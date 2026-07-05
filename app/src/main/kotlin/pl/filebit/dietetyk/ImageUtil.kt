package pl.filebit.dietetyk

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.min

/** Zdjęcia posiłków dla Claude vision — przygotowanie URI i kodowanie base64 JPEG. */
object ImageUtil {

    fun newPhotoUri(context: Context): Uri {
        val dir = File(context.cacheDir, "photos").apply { mkdirs() }
        val f = File(dir, "meal_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
    }

    /** Wczytaj, przeskaluj (max 1024 px) i zwróć base64 JPEG (albo null przy błędzie). */
    fun toBase64Jpeg(context: Context, uri: Uri, maxDim: Int = 1024, quality: Int = 80): String? =
        runCatching {
            val src = context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
                ?: return null
            val scale = min(1f, maxDim.toFloat() / max(src.width, src.height))
            val bmp = if (scale < 1f)
                Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true)
            else src
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }.getOrNull()

    /** Zapisz zdjęcie (przeskalowane) trwale w filesDir/chat_images i zwróć ścieżkę pliku (do miniatury w czacie). */
    fun persistChatImage(context: Context, srcUri: Uri, maxDim: Int = 1280, quality: Int = 85): String? =
        runCatching {
            val src = context.contentResolver.openInputStream(srcUri).use { BitmapFactory.decodeStream(it) }
                ?: return null
            val scale = min(1f, maxDim.toFloat() / max(src.width, src.height))
            val bmp = if (scale < 1f)
                Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true)
            else src
            val dir = File(context.filesDir, "chat_images").apply { mkdirs() }
            val f = File(dir, "chat_${System.currentTimeMillis()}.jpg")
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }
            f.absolutePath
        }.getOrNull()

    /** Base64 JPEG z zapisanego pliku (do API). */
    fun base64FromFile(path: String, quality: Int = 80): String? =
        runCatching {
            val bmp = BitmapFactory.decodeFile(path) ?: return null
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }.getOrNull()

    /** Zdekoduj miniaturę do wyświetlenia (inSampleSize dopasowany do reqWidth — chroni przed OOM). */
    fun decodeThumb(path: String, reqWidth: Int = 500): Bitmap? =
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0) return null
            var sample = 1
            while (bounds.outWidth / sample > reqWidth) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()

    /** Usuń wszystkie zapisane zdjęcia czatu (przy „Wyczyść rozmowę"). */
    fun clearChatImages(context: Context) {
        runCatching { File(context.filesDir, "chat_images").deleteRecursively() }
    }
}
