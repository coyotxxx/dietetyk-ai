package pl.filebit.dietetyk

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.sqlite.db.SimpleSQLiteQuery
import java.io.File

/** Kopia zapasowa — eksport pliku bazy do udostępnienia (Drive/mail/pliki). */
object Backup {

    const val DB_VERSION = 11

    sealed class RestoreResult {
        object Success : RestoreResult()
        data class Error(val message: String) : RestoreResult()
    }

    /**
     * Przywraca bazę z wybranego pliku. Waliduje (integrity_check + user_version + kluczowe tabele),
     * robi kopię bezpieczeństwa obecnej bazy, usuwa WAL/SHM i podmienia plik. Po Success należy
     * ZRESTARTOWAĆ proces ([restartApp]) — Room otworzy świeży plik bez wiszących połączeń.
     */
    fun restoreFromUri(context: Context, uri: Uri): RestoreResult = runCatching {
        val tmp = File(context.cacheDir, "restore_tmp.db")
        context.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            ?: return RestoreResult.Error("Nie udało się odczytać pliku.")

        // Walidacja na osobnej, read-only instancji — nie dotykamy działającej bazy.
        val verdict = runCatching {
            android.database.sqlite.SQLiteDatabase.openDatabase(tmp.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use { db ->
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

        // Podmiana: backup obecnej bazy → usuń WAL/SHM (inaczej stary WAL nadpisze dane) → kopiuj.
        val dbFile = context.getDatabasePath("dietetyk.db")
        if (dbFile.exists()) dbFile.copyTo(File(dbFile.parentFile, "dietetyk.db.pre-restore"), overwrite = true)
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-shm").delete()
        tmp.copyTo(dbFile, overwrite = true)
        tmp.delete()
        RestoreResult.Success
    }.getOrElse { RestoreResult.Error("Błąd przywracania: ${it.message}") }

    /** Twardy restart procesu — jedyny czysty sposób na przeładowanie bazy Room po podmianie pliku. */
    fun restartApp(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }

    fun exportShareIntent(context: Context, app: DietetykApp): Intent? = runCatching {
        // Zrzuć WAL do głównego pliku, żeby kopia była kompletna.
        runCatching { app.database.query(SimpleSQLiteQuery("PRAGMA wal_checkpoint(TRUNCATE)")).close() }
        val dbFile = context.getDatabasePath("dietetyk.db")
        if (!dbFile.exists()) return null
        val dir = File(context.cacheDir, "backup").apply { mkdirs() }
        val out = File(dir, "dietetyk_backup_${System.currentTimeMillis()}.db")
        dbFile.copyTo(out, overwrite = true)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", out)
        Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Kopia zapasowa Dietetyk AI")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }.getOrNull()
}
