package pl.filebit.dietetyk

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Kopia zapasowa — pełny ZIP (baza + ustawienia) do udostępnienia i przywrócenia.
 * Wsteczna zgodność: przywraca też stare kopie w formie gołego pliku `.db`.
 */
object Backup {

    const val DB_VERSION = 17
    private const val BACKUP_VERSION = 1
    private const val PREFS_NAME = "dietetyk_settings"
    private const val API_KEY = "claude_api_key"

    sealed class RestoreResult {
        object Success : RestoreResult()
        data class Error(val message: String) : RestoreResult()
    }

    // === EKSPORT (ZIP: manifest.json + dietetyk.db + prefs.json) ===

    /** Zapisuje ZIP (manifest+db+prefs) do wskazanego pliku. Wspólny rdzeń eksportu i auto-kopii. */
    private fun writeBackupZip(context: Context, app: DietetykApp, out: File, includeApiKey: Boolean) {
        val dbFile = context.getDatabasePath("dietetyk.db")
        require(dbFile.exists()) { "brak bazy" }
        // Spójny snapshot: VACUUM INTO czyta AKTUALNY stan bazy (łącznie z WAL) i pisze czystą kopię —
        // odporne na kontencję z aktywnymi czytnikami (Flow w Compose). checkpoint+copy potrafił zgubić
        // najświeższe zapisy, gdy jakiś reader trzymał WAL. Kopiujemy snapshot, nie żywy plik.
        val snapshot = File(context.cacheDir, "backup_snapshot_${System.currentTimeMillis()}.db")
        snapshot.delete()
        val escaped = snapshot.absolutePath.replace("'", "''")
        // VACUUM INTO musi biec na ODDZIELNYM połączeniu — na połączeniu Room rzuca (VACUUM w transakcji /
        // kontencja z aktywnymi Flow). Oddzielne połączenie widzi wszystkie zatwierdzone dane (łącznie z WAL).
        android.database.sqlite.SQLiteDatabase.openDatabase(
            dbFile.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
        ).use { raw -> raw.execSQL("VACUUM INTO '$escaped'") }
        out.parentFile?.mkdirs()
        try {
            ZipOutputStream(out.outputStream()).use { zos ->
                val manifest = JSONObject().apply {
                    put("backupVersion", BACKUP_VERSION)
                    put("dbVersion", DB_VERSION)
                    put("appVersion", BuildConfig.VERSION_NAME)
                    put("createdAt", System.currentTimeMillis())
                }
                zos.putNextEntry(ZipEntry("manifest.json")); zos.write(manifest.toString().toByteArray()); zos.closeEntry()
                zos.putNextEntry(ZipEntry("dietetyk.db")); snapshot.inputStream().use { it.copyTo(zos) }; zos.closeEntry()
                zos.putNextEntry(ZipEntry("prefs.json")); zos.write(prefsToJson(context, includeApiKey).toString().toByteArray()); zos.closeEntry()
            }
        } finally {
            snapshot.delete()
        }
    }

    fun exportShareIntent(context: Context, app: DietetykApp, includeApiKey: Boolean = true): Intent? = runCatching {
        val dir = File(context.cacheDir, "backup").apply { mkdirs() }
        val out = File(dir, "dietetyk_backup_${System.currentTimeMillis()}.zip")
        writeBackupZip(context, app, out, includeApiKey)
        markBackupDone(context)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", out)
        Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Kopia zapasowa Dietetyk AI")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }.getOrNull()

    /** Auto-kopia: zapis do zewn. katalogu aplikacji (widoczny w menedżerze plików, przeżywa reinstalację przez restore). */
    fun writeLocalBackup(context: Context, app: DietetykApp): Boolean = runCatching {
        val dir = context.getExternalFilesDir("backups") ?: File(context.filesDir, "backups")
        val out = File(dir, "dietetyk_auto_backup.zip")
        writeBackupZip(context, app, out, includeApiKey = true)
        markBackupDone(context)
        true
    }.getOrDefault(false)

    /** Zapamiętaj czas ostatniej kopii (do ekranu „Ostatnia kopia"). */
    private fun markBackupDone(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putLong("last_backup_at", System.currentTimeMillis()).apply()
    }

    fun lastBackupAt(context: Context): Long =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getLong("last_backup_at", 0L)

    private fun prefsToJson(context: Context, includeApiKey: Boolean): JSONObject {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = JSONObject()
        for ((k, v) in prefs.all) {
            if (k == API_KEY && !includeApiKey) continue
            val o = JSONObject()
            when (v) {
                is String -> { o.put("type", "string"); o.put("value", v) }
                is Int -> { o.put("type", "int"); o.put("value", v) }
                is Boolean -> { o.put("type", "bool"); o.put("value", v) }
                is Float -> { o.put("type", "float"); o.put("value", v.toDouble()) }
                is Long -> { o.put("type", "long"); o.put("value", v) }
                is Set<*> -> { o.put("type", "stringset"); o.put("value", JSONArray(v.filterIsInstance<String>())) }
                else -> continue
            }
            json.put(k, o)
        }
        return json
    }

    // === PRZYWRACANIE ===

    fun restoreFromUri(context: Context, uri: Uri): RestoreResult = runCatching {
        val raw = File(context.cacheDir, "restore_raw").apply { parentFile?.mkdirs() }
        context.contentResolver.openInputStream(uri)?.use { input -> raw.outputStream().use { input.copyTo(it) } }
            ?: return RestoreResult.Error("Nie udało się odczytać pliku.")

        // Rozpoznaj format: ZIP (nowy) vs goły plik .db (stara kopia).
        val magic = raw.inputStream().use { ByteArray(4).also { b -> it.read(b) } }
        val isZip = magic[0] == 'P'.code.toByte() && magic[1] == 'K'.code.toByte()

        val dbTmp = File(context.cacheDir, "restore_db.db")
        var prefsJson: String? = null

        if (isZip) {
            ZipInputStream(raw.inputStream()).use { zis ->
                var e: ZipEntry? = zis.nextEntry
                while (e != null) {
                    when (e.name) {
                        "dietetyk.db" -> dbTmp.outputStream().use { zis.copyTo(it) }
                        "prefs.json" -> prefsJson = zis.readBytes().toString(Charsets.UTF_8)
                        // manifest.json — pomijamy (walidację robimy na samej bazie)
                    }
                    zis.closeEntry(); e = zis.nextEntry
                }
            }
            if (!dbTmp.exists()) return RestoreResult.Error("Kopia jest niekompletna (brak bazy).")
        } else {
            raw.copyTo(dbTmp, overwrite = true)   // stary format: goły .db
        }

        // Walidacja bazy na osobnej read-only instancji.
        val verdict = runCatching {
            android.database.sqlite.SQLiteDatabase.openDatabase(dbTmp.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use { db ->
                val integrity = db.rawQuery("PRAGMA integrity_check", null).use { if (it.moveToFirst()) it.getString(0) else "" }
                if (integrity != "ok") return@runCatching "invalid"
                val ver = db.rawQuery("PRAGMA user_version", null).use { if (it.moveToFirst()) it.getInt(0) else -1 }
                if (ver > DB_VERSION) return@runCatching "newer"
                val hasTables = listOf("profile", "weight_samples").all { t ->
                    db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(t)).use { it.moveToFirst() }
                }
                if (!hasTables) return@runCatching "invalid"
                "ok"
            }
        }.getOrDefault("invalid")

        when (verdict) {
            "ok" -> Unit
            "newer" -> return RestoreResult.Error("Kopia pochodzi z nowszej wersji aplikacji. Zaktualizuj Dietetyk AI i spróbuj ponownie.")
            else -> return RestoreResult.Error("To nie jest prawidłowa kopia Dietetyka AI.")
        }

        // Podmiana bazy: backup obecnej → usuń WAL/SHM → kopiuj.
        val dbFile = context.getDatabasePath("dietetyk.db")
        if (dbFile.exists()) dbFile.copyTo(File(dbFile.parentFile, "dietetyk.db.pre-restore"), overwrite = true)
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-shm").delete()
        dbTmp.copyTo(dbFile, overwrite = true)

        // Ustawienia z kopii (jeśli są). commit() — synchronicznie, przed restartem procesu.
        prefsJson?.let { restorePrefs(context, it) }

        dbTmp.delete(); raw.delete()
        RestoreResult.Success
    }.getOrElse { RestoreResult.Error("Błąd przywracania: ${it.message}") }

    private fun restorePrefs(context: Context, jsonText: String) {
        val obj = runCatching { JSONObject(jsonText) }.getOrNull() ?: return
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        for (k in obj.keys()) {
            val o = obj.optJSONObject(k) ?: continue
            when (o.optString("type")) {
                "string" -> editor.putString(k, o.optString("value"))
                "int" -> editor.putInt(k, o.optInt("value"))
                "bool" -> editor.putBoolean(k, o.optBoolean("value"))
                "float" -> editor.putFloat(k, o.optDouble("value").toFloat())
                "long" -> editor.putLong(k, o.optLong("value"))
                "stringset" -> {
                    val arr = o.optJSONArray("value") ?: JSONArray()
                    editor.putStringSet(k, (0 until arr.length()).map { arr.getString(it) }.toSet())
                }
            }
        }
        editor.commit()
    }

    /** Twardy restart procesu — jedyny czysty sposób na przeładowanie bazy Room po podmianie pliku. */
    fun restartApp(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }
}
