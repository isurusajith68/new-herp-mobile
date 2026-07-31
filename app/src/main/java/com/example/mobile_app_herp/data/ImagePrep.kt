package com.example.mobile_app_herp.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Gets a phone photo ready to be read by the OCR.
 *
 * A modern handset shoots 8–12 MP, which is 4–12 MB of JPEG. Pushing that up a
 * hotel's uplink takes long enough that the server's 40s Gemini budget is spent
 * before the bill even arrives, and the upload dies with nothing to show for it.
 * Downscaling to [MAX_EDGE] keeps printed bill text comfortably legible — a
 * thermal receipt's characters are large relative to the page — while cutting a
 * typical photo to a few hundred kilobytes.
 *
 * Rotation matters as much as size: phones record orientation in EXIF rather
 * than rotating the pixels, so a bill shot in portrait arrives sideways. The
 * model can read sideways text, but far less reliably, and it is a silent
 * accuracy loss rather than a visible failure.
 */
object ImagePrep {

    /** Long edge in pixels. Above this adds upload time without adding legibility. */
    private const val MAX_EDGE = 2000

    /** High enough that thermal-print dots survive; low enough to stay small. */
    private const val JPEG_QUALITY = 88

    const val MIME_TYPE = "image/jpeg"

    /**
     * Reads [source], corrects its orientation, scales it down and writes a JPEG
     * into the cache. Returns null when the image cannot be decoded at all.
     */
    suspend fun prepare(context: Context, source: Uri): File? = withContext(Dispatchers.IO) {
        // First pass reads the dimensions only. decodeStream ALWAYS returns null
        // in this mode — it reports through outWidth/outHeight instead — so the
        // stream must be null-checked on its own. Folding the two together
        // (`openInputStream(...)?.use { decode } ?: return null`) rejects every
        // image ever passed in, which is exactly the bug this replaces.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val header = context.contentResolver.openInputStream(source) ?: return@withContext null
        header.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

        // Sub-sampling during decode, so a 12 MP photo never fully enters memory —
        // decoding one at full size is a real OutOfMemoryError on a cheap handset.
        val decode = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
        }
        val pixels = context.contentResolver.openInputStream(source) ?: return@withContext null
        var bitmap = pixels.use {
            BitmapFactory.decodeStream(it, null, decode)
        } ?: return@withContext null

        bitmap = applyExifRotation(context, source, bitmap)
        bitmap = scaleWithin(bitmap, MAX_EDGE)

        val out = File(context.cacheDir, "bill-${System.currentTimeMillis()}.jpg")
        FileOutputStream(out).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
        }
        bitmap.recycle()
        if (out.length() == 0L) {
            out.delete()
            null
        } else {
            out
        }
    }

    /** Largest power-of-two reduction that still leaves us above the target size. */
    private fun sampleSizeFor(width: Int, height: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (maxOf(w, h) / 2 >= MAX_EDGE) {
            w /= 2
            h /= 2
            sample *= 2
        }
        return sample
    }

    private fun applyExifRotation(context: Context, source: Uri, bitmap: Bitmap): Bitmap {
        val degrees = runCatching {
            context.contentResolver.openInputStream(source)?.use { stream ->
                when (ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)

        if (degrees == 0f) return bitmap
        val rotated = Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height,
            Matrix().apply { postRotate(degrees) },
            true,
        )
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }

    private fun scaleWithin(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxEdge) return bitmap
        val ratio = maxEdge.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled != bitmap) bitmap.recycle()
        return scaled
    }
}
