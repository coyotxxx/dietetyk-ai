package pl.filebit.dietetyk

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.sqlite.db.SimpleSQLiteQuery
import java.io.File

/** Kopia zapasowa — eksport pliku bazy do udostępnienia (Drive/mail/pliki). */
object Backup {

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
