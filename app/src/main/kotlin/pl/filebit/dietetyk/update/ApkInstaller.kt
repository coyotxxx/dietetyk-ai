package pl.filebit.dietetyk.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/** Pobiera APK nowej wersji i uruchamia systemowy instalator. */
object ApkInstaller {
    private val http = OkHttpClient()

    suspend fun downloadAndInstall(context: Context, url: String): Boolean {
        val file = withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                val f = File(dir, "dietetyk-update.apk")
                val req = Request.Builder().url(url).header("User-Agent", "DietetykAI").build()
                http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@runCatching null
                    resp.body?.byteStream()?.use { input -> f.outputStream().use { input.copyTo(it) } }
                }
                f
            }.getOrNull()
        } ?: return false

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent); true }.getOrDefault(false)
    }
}
