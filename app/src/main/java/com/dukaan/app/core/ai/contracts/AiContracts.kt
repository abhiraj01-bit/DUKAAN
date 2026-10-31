package com.dukaan.app.core.ai.contracts

import android.graphics.Bitmap
import android.graphics.RectF

/**
 * Strict data contracts for the DukaanAI on-device AI VLM & OCR pipeline.
 *
 * Rules:
 * - Every monetary value is stored as Long paise (1 INR = 100 paise) to prevent floating-point drift.
 * - Missing or unreadable fields are strictly null. Never fabricate values or convert unknown to zero.
 * - Every field maintains extraction provenance, confidence/uncertainty, and user-correction state.
 */

// ─── Pipeline Inputs ────────────────────────────────────────────────────────

enum class InvoiceScanMode(val displayName: String) {
    AUTO("Auto Detect"),
    HANDWRITTEN("Handwritten"),
    PRINTED("Standard Printed"),
    THERMAL_RECEIPT("Thermal Receipt")
}

data class InvoiceInput(
    val image: InvoiceImage,
    val scanMode: InvoiceScanMode = InvoiceScanMode.AUTO,
    val preferredLanguageHints: Set<String> = setOf("en", "hi"),
    val correlationId: String
)

data class InvoiceImage(
    val sourcePath: String,
    val width: Int,
    val height: Int,
    val orientationDegrees: Int = 0,
    val fileSizeCompressedBytes: Long = 0L,
    val mimeType: String = "image/jpeg"
)

// ─── Extraction Provenance & Field Envelope ────────────────────────────────

enum class ExtractionSource(val displayLabel: String) {
    ON_DEVICE_VLM("On-Device AI (Gemma 3n)"),
    ON_DEVICE_MLKIT("Offline OCR (ML Kit)"),
    CLOUD_GEMINI("Cloud AI (Gemini)"),
    MANUAL_ENTRY("Manual Entry")
}

data class OCRField<T>(
    /** Extracted and parsed value (null if missing or unreadable) */
    val value: T?,
    /** Raw recognized string prior to parsing / normalization */
    val rawText: String = "",
    /** Normalized confidence in [0.0..1.0] if calibrated by model, null if not calibrated */
    val confidence: Float? = null,
    /** Whether the value is flagged as uncertain and requires human confirmation */
    val isUncertain: Boolean = false,
    /** Origin engine that extracted this value */
    val source: ExtractionSource = ExtractionSource.ON_DEVICE_MLKIT,
    /** Whether the shopkeeper edited this value on the review screen */
    val isUserModified: Boolean = false,
    /** User-corrected value if modified */
    val userCorrectedValue: T? = null,
    /** Visual bounding box if provided by engine */
    val boundingBox: RectF? = null
) {
    /** Returns the definitive value: user correction takes priority, then extracted value. */
    val effectiveValue: T? get() = if (isUserModified) userCorrectedValue else value
}

// ─── Extracted Product Line Item ───────────────────────────────────────────

data class OCRProduct(
    val id: String,
    val productName: OCRField<String>,
    val hsnSac: OCRField<String?> = OCRField(null),
    val quantity: OCRField<Double>,
    val unit: OCRField<String> = OCRField("pcs"),
    val unitPricePaise: OCRField<Long>,
    val discountPaise: OCRField<Long> = OCRField(0L),
    val gstRatePercent: OCRField<Double?> = OCRField(null),
    val lineTotalPaise: OCRField<Long>
) {
    /**
     * Checks mathematical consistency for this line item:
     * (Qty × UnitPrice) - Discount ≈ LineTotal (within 200 paise rounding tolerance).
     */
    fun isArithmeticConsistent(tolerancePaise: Long = 200L): Boolean {
        val qty = quantity.effectiveValue ?: return false
        val price = unitPricePaise.effectiveValue ?: return false
        val total = lineTotalPaise.effectiveValue ?: return false
        val disc = discountPaise.effectiveValue ?: 0L

        val expected = (qty * price).toLong() - disc
        return kotlin.math.abs(expected - total) <= tolerancePaise
    }
}

// ─── Complete OCR Response ─────────────────────────────────────────────────

data class OCRRequest(
    val bitmap: Bitmap,
    val scanMode: InvoiceScanMode,
    val invoiceId: String,
    val imagePath: String
)

data class OCRResponse(
    val invoiceId: String,
    val supplierName: OCRField<String>,
    val supplierGstin: OCRField<String?>,
    val supplierPhone: OCRField<String?>,
    val supplierAddress: OCRField<String?>,
    val invoiceNumber: OCRField<String>,
    val invoiceDate: OCRField<Long>,
    val dueDate: OCRField<Long?>,
    val items: List<OCRProduct>,
    val subtotalPaise: OCRField<Long>,
    val discountPaise: OCRField<Long>,
    val taxPaise: OCRField<Long>,
    val grandTotalPaise: OCRField<Long>,
    val rawText: String,
    val source: ExtractionSource,
    val modelId: String,
    val processingDurationMs: Long,
    val validationResult: InvoiceValidationResult
)

// ─── Editable Invoice Draft for UI Review ─────────────────────────────────

data class InvoiceDraft(
    val id: String,
    val supplierName: String,
    val supplierGstin: String?,
    val supplierPhone: String?,
    val invoiceNumber: String,
    val invoiceDate: Long,
    val dueDate: Long?,
    val items: List<OCRProduct>,
    val subtotalPaise: Long,
    val discountPaise: Long,
    val taxPaise: Long,
    val grandTotalPaise: Long,
    val imagePath: String,
    val rawOcrText: String,
    val extractionSource: ExtractionSource,
    val validationResult: InvoiceValidationResult,
    val isConfirmed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

// ─── Financial Validation & Discrepancy Reporting ──────────────────────────

data class InvoiceValidationResult(
    val isValid: Boolean,
    val warnings: List<String> = emptyList(),
    val hasCriticalMismatch: Boolean = false,
    val arithmeticChecked: Boolean = false
) {
    companion object {
        val VALID = InvoiceValidationResult(isValid = true, arithmeticChecked = true)
    }
}

// ─── Engine & Model Lifecycle Status ───────────────────────────────────────

sealed class ModelStatus {
    object NotDownloaded : ModelStatus()
    data class Downloading(val progressPercent: Int, val bytesDownloaded: Long, val totalBytes: Long) : ModelStatus()
    object Downloaded : ModelStatus()
    object Loaded : ModelStatus()
    data class Failed(val reason: String) : ModelStatus()
    object InsufficientStorage : ModelStatus()
    data class InsufficientMemory(val availableMb: Long, val requiredMb: Long) : ModelStatus()
}

sealed class OCRProcessingStatus {
    object Idle : OCRProcessingStatus()
    object ValidatingImage : OCRProcessingStatus()
    data class Preprocessing(val mode: InvoiceScanMode) : OCRProcessingStatus()
    object InspectingDeviceMemory : OCRProcessingStatus()
    data class RunningVlmInference(val modelName: String) : OCRProcessingStatus()
    data class RunningMlKitFallback(val reason: String) : OCRProcessingStatus()
    object ValidatingArithmetic : OCRProcessingStatus()
    data class ReadyForReview(val draft: InvoiceDraft) : OCRProcessingStatus()
    data class Error(val error: OCRProcessingError) : OCRProcessingStatus()
}

// ─── Centralized Error Hierarchy ───────────────────────────────────────────

sealed class OCRProcessingError(
    val internalCode: String,
    val userMessage: String,
    val canRetry: Boolean = true,
    val canFallbackToMlKit: Boolean = true
) {
    data class InvalidImage(val details: String) : OCRProcessingError(
        "IMG_INVALID", "The selected invoice image could not be loaded or is corrupted: $details", canRetry = false
    )
    data class ImageTooBlurry(val details: String) : OCRProcessingError(
        "IMG_BLURRY", "The image appears too blurry to read invoice text clearly. Please retake the photo with steady focus."
    )
    data class InsufficientDeviceMemory(val availableMb: Long, val requiredMb: Long) : OCRProcessingError(
        "MEM_INSUFFICIENT", "Device available memory ($availableMb MB) is insufficient for on-device AI model (requires $requiredMb MB). Using offline ML Kit instead.",
        canRetry = false
    )
    data class ModelNotAvailable(val modelName: String) : OCRProcessingError(
        "MODEL_NOT_FOUND", "AI model '$modelName' is not downloaded. You can download it in Settings or proceed with instant offline OCR."
    )
    data class ModelCorrupted(val details: String) : OCRProcessingError(
        "MODEL_CORRUPT", "Model checksum verification failed. Please re-download the model in Settings.", canRetry = false
    )
    data class InferenceTimeout(val timeoutSeconds: Long) : OCRProcessingError(
        "INF_TIMEOUT", "AI processing timed out after $timeoutSeconds seconds. Falling back to offline OCR."
    )
    data class InferenceCancelled(val reason: String) : OCRProcessingError(
        "INF_CANCELLED", "Scan operation was cancelled.", canRetry = false, canFallbackToMlKit = false
    )
    data class MalformedModelOutput(val details: String) : OCRProcessingError(
        "OUTPUT_MALFORMED", "The AI model returned an unparseable response. Falling back to offline OCR."
    )
    data class DatabaseTransactionFailed(val details: String) : OCRProcessingError(
        "DB_TX_FAIL", "Failed to save invoice draft to database: $details", canRetry = true, canFallbackToMlKit = false
    )
    data class Unknown(val details: String) : OCRProcessingError(
        "GENERIC_ERROR", "An unexpected error occurred during processing: $details"
    )
}

