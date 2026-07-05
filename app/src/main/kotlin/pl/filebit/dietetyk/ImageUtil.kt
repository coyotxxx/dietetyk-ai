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
}
