package com.dukaan.app.core.ocr

import android.graphics.RectF

// ─── Language / Script registry ────────────────────────────────────────────

/**
 * Supported OCR languages with their script families and offline capabilities.
 * New entries can be added without touching existing code – just add an enum value.
 */
enum class OcrLanguage(
    val displayName: String,
    val scriptFamily: ScriptFamily,
    /** Whether a real on-device model is available for this language */
    val supportsOffline: Boolean,
    /** ISO 639 language code */
    val isoCode: String
) {
    ENGLISH("English", ScriptFamily.LATIN, supportsOffline = true, "en"),
    HINDI("Hindi", ScriptFamily.DEVANAGARI, supportsOffline = true, "hi"),
    PUNJABI("Punjabi (Gurmukhi)", ScriptFamily.GURMUKHI, supportsOffline = false, "pa");

    companion object {
        fun forIsoCode(code: String) = entries.firstOrNull { it.isoCode == code }
    }
}

enum class ScriptFamily {
    LATIN,
    DEVANAGARI,
    GURMUKHI
}

// ─── OCR result types ───────────────────────────────────────────────────────

/**
 * Single recognized text block with spatial and confidence information.
 */
data class OcrTextBlock(
    /** Recognized text. Never fabricated – null when unrecognizable. */
    val text: String,
    /** Normalized bounding box in image coordinates (0..width, 0..height) */
    val boundingBox: RectF?,
    /** 0.0 = no confidence, 1.0 = fully confident */
    val confidence: Float,
    /** Detected language/script for this block, if determinable */
    val detectedLanguage: OcrLanguage?,
    /** Which model produced this block */
    val modelId: String
)

/**
 * Confidence breakdown for individual invoice fields.
 */
data class OcrFieldConfidence(
    val ocrText: Float = 0f,
    val supplierName: Float = 0f,
    val invoiceNumber: Float = 0f,
    val invoiceDate: Float = 0f,
    val productNames: Float = 0f,
    val quantities: Float = 0f,
    val prices: Float = 0f,
    val gst: Float = 0f,
    val grandTotal: Float = 0f
) {
    /** Overall confidence as a weighted average. */
    val overall: Float get() = listOf(
        ocrText * 0.15f,
        supplierName * 0.10f,
        invoiceNumber * 0.10f,
        invoiceDate * 0.10f,
        productNames * 0.15f,
        quantities * 0.15f,
        prices * 0.15f,
        grandTotal * 0.10f
    ).sum()

    fun label(): ConfidenceLabel = when {
        overall >= 0.80f -> ConfidenceLabel.HIGH
        overall >= 0.50f -> ConfidenceLabel.MEDIUM
        else             -> ConfidenceLabel.LOW
    }
}

enum class ConfidenceLabel { HIGH, MEDIUM, LOW }

/**
 * Source of extraction for an invoice or field.
 */
enum class ExtractionSource(val displayLabel: String) {
    LOCAL_OCR("Offline OCR"),
    GEMINI_AI("Gemini AI"),
    HYBRID("Hybrid (OCR + AI)"),
    MANUAL("Manual Entry")
}

/**
 * Complete result from the OCR pipeline for one image.
 */
sealed class OcrResult {
    /**
     * At least some text was recognized (may have empty blocks if parts were unreadable).
     */
    data class Success(
        val blocks: List<OcrTextBlock>,
        val fullText: String,
        /** Languages that were confidently detected in the image */
        val detectedLanguages: Set<OcrLanguage>,
        /** Languages detected but for which offline recognition is unavailable */
        val unsupportedOfflineLanguages: Set<OcrLanguage>,
        val confidence: OcrFieldConfidence,
        val source: ExtractionSource,
        val durationMs: Long,
        val modelIds: List<String>
    ) : OcrResult()

    /** Recognition failed – reason is provided, never crash-worthy */
    data class Failure(
        val reason: String,
        val isRecoverable: Boolean
    ) : OcrResult()

    /** Partial: some engines succeeded, others did not */
    data class Partial(
        val blocks: List<OcrTextBlock>,
        val fullText: String,
        val detectedLanguages: Set<OcrLanguage>,
        val failedLanguages: Set<OcrLanguage>,
        val failureReason: String,
        val source: ExtractionSource
    ) : OcrResult()
}

// ─── Image quality ──────────────────────────────────────────────────────────

data class ImageQualityReport(
    val estimatedDpi: Int?,
    val isBlurry: Boolean,
    val isTooSmall: Boolean,
    val brightnessLevel: BrightnessLevel,
    val hasDetectedText: Boolean
)

enum class BrightnessLevel { TOO_DARK, NORMAL, TOO_BRIGHT }

