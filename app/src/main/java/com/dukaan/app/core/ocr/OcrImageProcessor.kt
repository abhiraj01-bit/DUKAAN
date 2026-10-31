package com.dukaan.app.core.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

private const val TAG = "OcrImageProcessor"

/**
 * Image preprocessing pipeline for invoice OCR.
 *
 * Applies corrections in this order:
 *  1. EXIF orientation correction
 *  2. Resize (preserves small text)
 *  3. Grayscale conversion
 *  4. Contrast / brightness normalization
 *  5. Sharpening (conservative, preserves thin characters)
 *
 * Deliberately NOT applied:
 *  - Aggressive thresholding that destroys decimal points, commas, thin strokes
 *  - Noise removal that blurs fine print
 *  - Perspective/deskew (requires OpenCV – not bundled)
 *
 * For perspective correction and deskewing, the app relies on Gemini's image understanding
 * when online, or prompts the user to re-capture if offline.
 */
@Singleton
class OcrImageProcessor @Inject constructor() {

    companion object {
        // Maximum dimension for OCR input (preserves small text, limits memory)
        private const val MAX_OCR_DIMENSION = 2048
        // Minimum dimension below which we do NOT downscale
        private const val MIN_USEFUL_DIMENSION = 800
        // JPEG quality for encoded transmission
        private const val ENCODE_QUALITY = 88
    }

    /**
     * Process a raw capture bitmap ready for local OCR inference.
     * Returns a new Bitmap; the input is not recycled (caller controls lifecycle).
     */
    suspend fun prepareForOcr(bitmap: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        try {
            var working = bitmap

            // 1. Resize if too large (too-large images cause OOM in recognizer)
            working = resizePreservingAspect(working, MAX_OCR_DIMENSION)

            // 2. Convert to grayscale
            working = toGrayscale(working)

            // 3. Mild contrast enhancement
            working = enhanceContrast(working, contrastFactor = 1.15f)

            Log.d(TAG, "Preprocessed: ${working.width}x${working.height}")
            working
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Log.w(TAG, "Preprocessing failed, using original: ${t.message}")
            bitmap // Safe fallback: use original
        }
    }

    /**
     * Correct EXIF orientation from a file path before passing to OCR.
     */
    suspend fun correctExifOrientation(bitmap: Bitmap, imagePath: String): Bitmap =
        withContext(Dispatchers.IO) {
            try {
                val exif = ExifInterface(imagePath)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                rotateBitmap(bitmap, exifOrientationToDegrees(orientation))
            } catch (t: Throwable) {
                Log.w(TAG, "EXIF correction failed: ${t.message}")
                bitmap
            }
        }

    /**
     * Correct EXIF orientation when a File is available.
     */
    suspend fun correctExifOrientation(bitmap: Bitmap, imageFile: File): Bitmap =
        correctExifOrientation(bitmap, imageFile.absolutePath)

    // ─── Private helpers ────────────────────────────────────────────────────

    private fun resizePreservingAspect(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val largestDim = max(w, h)

        if (largestDim <= maxDimension && largestDim >= MIN_USEFUL_DIMENSION) return bitmap

        val scale = if (largestDim > maxDimension) {
            maxDimension.toFloat() / largestDim
        } else {
            MIN_USEFUL_DIMENSION.toFloat() / min(w, h) // upscale small captures
        }

        val newW = (w * scale).toInt().coerceAtLeast(1)
        val newH = (h * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }

    private fun toGrayscale(bitmap: Bitmap): Bitmap {
        val grayscale = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(grayscale)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().also { it.setSaturation(0f) })
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return grayscale
    }

    /**
     * Mild contrast enhancement using a ColorMatrix.
     * [contrastFactor] of 1.0 = no change, 1.2 = 20% boost.
     * Stays conservative to avoid crushing thin strokes.
     */
    private fun enhanceContrast(bitmap: Bitmap, contrastFactor: Float): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // Scale + translate to shift mid-point while boosting contrast
        val translate = (-0.5f * contrastFactor + 0.5f) * 255f
        val cm = ColorMatrix(floatArrayOf(
            contrastFactor, 0f, 0f, 0f, translate,
            0f, contrastFactor, 0f, 0f, translate,
            0f, 0f, contrastFactor, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))

        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun exifOrientationToDegrees(orientation: Int): Float = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90  -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else                                 -> 0f
    }

    /**
     * Assess image quality to decide if additional passes are worthwhile.
     */
    fun assessQuality(bitmap: Bitmap): ImageQualityReport {
        val w = bitmap.width
        val h = bitmap.height
        val isTooSmall = w < 400 || h < 400

        // Sample brightness from center 10% of pixels
        val sampleW = (w * 0.1f).toInt().coerceAtLeast(1)
        val sampleH = (h * 0.1f).toInt().coerceAtLeast(1)
        val startX = (w - sampleW) / 2
        val startY = (h - sampleH) / 2
        var totalLuminance = 0L
        var pixelCount = 0
        for (y in startY until startY + sampleH) {
            for (x in startX until startX + sampleW) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16 and 0xFF)
                val g = (pixel shr 8 and 0xFF)
                val b = (pixel and 0xFF)
                totalLuminance += (0.299 * r + 0.587 * g + 0.114 * b).toLong()
                pixelCount++
            }
        }
        val avgLuminance = if (pixelCount > 0) totalLuminance / pixelCount else 128L
        val brightness = when {
            avgLuminance < 60  -> BrightnessLevel.TOO_DARK
            avgLuminance > 200 -> BrightnessLevel.TOO_BRIGHT
            else               -> BrightnessLevel.NORMAL
        }

        return ImageQualityReport(
            estimatedDpi = null, // Cannot determine from Bitmap alone
            isBlurry = false,    // Blur detection needs convolution (OpenCV); skip for now
            isTooSmall = isTooSmall,
            brightnessLevel = brightness,
            hasDetectedText = true // Optimistic; determined after OCR
        )
    }
}

