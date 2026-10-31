package com.dukaan.app.core.ocr

import android.graphics.Bitmap

/**
 * Contract for all OCR engines.
 * Implementations must run inference off the main thread (use Dispatchers.IO internally).
 * Must never return fabricated text.
 * Must support coroutine cancellation.
 */
interface OcrEngine {
    /**
     * Unique identifier for this engine / model combination.
     */
    val engineId: String

    /**
     * Languages this engine can recognize.
     */
    val supportedLanguages: Set<OcrLanguage>

    /**
     * Whether this engine requires an internet connection.
     */
    val requiresNetwork: Boolean

    /**
     * Run text recognition on the given bitmap.
     *
     * @param image Source image (may have been preprocessed).
     * @param requestedLanguages Hint about which scripts to focus on.
     *                           The engine may ignore hints it cannot fulfil.
     * @return [OcrResult.Success], [OcrResult.Partial], or [OcrResult.Failure].
     *         Never throws (exception is wrapped in [OcrResult.Failure]).
     */
    suspend fun recognize(
        image: Bitmap,
        requestedLanguages: Set<OcrLanguage> = OcrLanguage.entries.toSet()
    ): OcrResult
}

