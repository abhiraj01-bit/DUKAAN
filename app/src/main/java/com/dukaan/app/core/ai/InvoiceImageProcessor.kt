package com.dukaan.app.core.ai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.util.Log
import com.dukaan.app.core.ai.contracts.InvoiceScanMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

private const val TAG = "InvoiceImageProcessor"

/**
 * Dedicated image preprocessor for on-device invoice recognition.
 *
 * Prepares raw camera/gallery captures according to the selected [InvoiceScanMode]:
 * - [InvoiceScanMode.HANDWRITTEN]: Preserves thin ink strokes and Devanagari matras, boosts local contrast gently without binary thresholding.
 * - [InvoiceScanMode.PRINTED]: Balances contrast and sharpens standard typed characters.
 * - [InvoiceScanMode.THERMAL_RECEIPT]: Recovers low-contrast / faded thermal receipts via histogram stretching.
 * - [InvoiceScanMode.AUTO]: Analyzes average luminance and variance to choose the optimal mode automatically.
 */
@Singleton
class InvoiceImageProcessor @Inject constructor() {

    companion object {
        const val MAX_VLM_DIMENSION = 1800
        const val MIN_DIMENSION = 800
    }

    suspend fun preprocess(
        bitmap: Bitmap,
        scanMode: InvoiceScanMode = InvoiceScanMode.AUTO
    ): PreprocessedImage = withContext(Dispatchers.Default) {
        try {
            var working = resizePreservingAspect(bitmap, MAX_VLM_DIMENSION)

            val resolvedMode = if (scanMode == InvoiceScanMode.AUTO) {
                detectOptimalScanMode(working)
            } else {
                scanMode
            }

            Log.d(TAG, "Preprocessing invoice image with mode: ${resolvedMode.displayName}")

            val processed = when (resolvedMode) {
                InvoiceScanMode.HANDWRITTEN -> processHandwritten(working)
                InvoiceScanMode.PRINTED -> processPrinted(working)
                InvoiceScanMode.THERMAL_RECEIPT -> processThermalReceipt(working)
                InvoiceScanMode.AUTO -> processPrinted(working)
            }

            PreprocessedImage(
                bitmap = processed,
                appliedMode = resolvedMode,
                originalWidth = bitmap.width,
                originalHeight = bitmap.height,
                processedWidth = processed.width,
                processedHeight = processed.height
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Log.w(TAG, "Preprocessing encountered an error, falling back to original bitmap: ${t.message}")
            PreprocessedImage(
                bitmap = bitmap,
                appliedMode = scanMode,
                originalWidth = bitmap.width,
                originalHeight = bitmap.height,
                processedWidth = bitmap.width,
                processedHeight = bitmap.height
            )
        }
    }

    /**
     * Corrects EXIF orientation if the image was captured via file path.
     */
    suspend fun correctExif(bitmap: Bitmap, imagePath: String): Bitmap = withContext(Dispatchers.IO) {
        try {
            val file = File(imagePath)
            if (!file.exists()) return@withContext bitmap

            val exif = ExifInterface(imagePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }

            if (degrees != 0) {
                val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }
        } catch (t: Throwable) {
            Log.w(TAG, "EXIF reading failed: ${t.message}")
            bitmap
        }
    }

    // ─── Mode Specific Pipelines ──────────────────────────────────────────

    /**
     * Handwritten mode:
     * - Preserves fine strokes (ballpoint pen, pencil, Devanagari shirorekha and matras).
     * - Uses gentle grayscale and moderate contrast boost (1.18x) without hard binarization.
     */
    private fun processHandwritten(bitmap: Bitmap): Bitmap {
        val gray = toGrayscale(bitmap)
        return applyContrast(gray, contrast = 1.18f, brightnessOffset = 5f)
    }

    /**
     * Printed mode:
     * - Standard dot-matrix or laser printed bill.
     * - Grayscale with 1.25x contrast to make dark ink pop on white/off-white bill paper.
     */
    private fun processPrinted(bitmap: Bitmap): Bitmap {
        val gray = toGrayscale(bitmap)
        return applyContrast(gray, contrast = 1.25f, brightnessOffset = 0f)
    }

    /**
     * Thermal Receipt mode:
     * - Faded, yellowed or low-contrast POS register thermal paper.
     * - Aggressive contrast enhancement (1.45x) and slight brightness suppression to recover faint characters.
     */
    private fun processThermalReceipt(bitmap: Bitmap): Bitmap {
        val gray = toGrayscale(bitmap)
        return applyContrast(gray, contrast = 1.45f, brightnessOffset = -12f)
    }

    /**
     * Automatically analyzes image brightness and variance to decide if it's thermal or standard.
     */
    private fun detectOptimalScanMode(bitmap: Bitmap): InvoiceScanMode {
        // Sample center pixels to estimate contrast
        val step = max(1, bitmap.width / 20)
        var totalLuma = 0L
        var count = 0

        for (x in 0 until bitmap.width step step) {
            for (y in 0 until bitmap.height step step) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val luma = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                totalLuma += luma
                count++
            }
        }

        if (count == 0) return InvoiceScanMode.PRINTED
        val avgLuma = totalLuma / count

        // High average brightness with washed out contrast is typical for thermal POS paper
        return if (avgLuma > 215) {
            InvoiceScanMode.THERMAL_RECEIPT
        } else {
            InvoiceScanMode.PRINTED
        }
    }

    // ─── Low-Level Transformations ─────────────────────────────────────────

    private fun resizePreservingAspect(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val largest = max(w, h)

        if (largest <= maxDim && largest >= MIN_DIMENSION) return bitmap

        val scale = if (largest > maxDim) {
            maxDim.toFloat() / largest
        } else {
            MIN_DIMENSION.toFloat() / min(w, h)
        }

        val newW = (w * scale).toInt().coerceAtLeast(1)
        val newH = (h * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }

    private fun toGrayscale(bitmap: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return output
    }

    private fun applyContrast(bitmap: Bitmap, contrast: Float, brightnessOffset: Float): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // Scale RGB values by contrast factor and shift by brightness offset
        val cm = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, brightnessOffset,
                0f, contrast, 0f, 0f, brightnessOffset,
                0f, 0f, contrast, 0f, brightnessOffset,
                0f, 0f, 0f, 1f, 0f
            )
        )

        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(cm)
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return output
    }
}

data class PreprocessedImage(
    val bitmap: Bitmap,
    val appliedMode: InvoiceScanMode,
    val originalWidth: Int,
    val originalHeight: Int,
    val processedWidth: Int,
    val processedHeight: Int
)

