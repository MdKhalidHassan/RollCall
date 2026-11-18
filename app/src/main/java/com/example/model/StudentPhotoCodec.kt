package com.example.model

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

object StudentPhotoCodec {
    private const val MAX_DIMENSION_PX = 220
    private const val JPEG_QUALITY = 75
    private const val MAX_BASE64_LENGTH = 95_000

    suspend fun encodeUriToBase64(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }

            val srcWidth = boundsOptions.outWidth
            val srcHeight = boundsOptions.outHeight
            if (srcWidth <= 0 || srcHeight <= 0) return@withContext null

            var sampleSize = 1
            val longest = max(srcWidth, srcHeight)
            while (longest / sampleSize > MAX_DIMENSION_PX * 2) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return@withContext null

            val scale = MAX_DIMENSION_PX.toFloat() / max(decoded.width, decoded.height).toFloat()
            val scaled = if (scale < 1f) {
                val targetW = (decoded.width * scale).roundToInt().coerceAtLeast(1)
                val targetH = (decoded.height * scale).roundToInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(decoded, targetW, targetH, true).also {
                    if (it !== decoded) decoded.recycle()
                }
            } else {
                decoded
            }

            val out = ByteArrayOutputStream()
            var quality = JPEG_QUALITY
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            while (out.size() > 68_000 && quality > 40) {
                out.reset()
                quality -= 15
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            }
            scaled.recycle()

            val encoded = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            if (encoded.length <= MAX_BASE64_LENGTH) encoded else null
        } catch (_: Exception) {
            null
        }
    }

    fun decodeBase64ToImageBitmap(photoData: String): ImageBitmap? {
        if (photoData.isBlank()) return null
        return try {
            val bytes = Base64.decode(photoData, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
}
