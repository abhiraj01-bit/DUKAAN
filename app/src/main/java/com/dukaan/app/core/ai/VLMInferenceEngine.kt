package com.dukaan.app.core.ai

import com.dukaan.app.core.ai.contracts.OCRRequest
import com.dukaan.app.core.ai.contracts.OCRResponse

/**
 * Common contract for all invoice extraction engines (VLM and Fallback OCR).
 */
interface VLMInferenceEngine {
    val engineId: String
    val isZeroDownload: Boolean

    suspend fun isReady(): Boolean
    suspend fun processInvoice(request: OCRRequest): OCRResponse
}

