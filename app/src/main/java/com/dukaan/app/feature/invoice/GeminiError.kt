package com.dukaan.app.feature.invoice

/**
 * Smart classification of all possible Gemini API outcomes and failure modes.
 */
sealed class GeminiError(
    open val userMessage: String,
    val canRetry: Boolean,
    val isRecoverableWithFallback: Boolean
) {
    data class InvalidApiKey(
        override val userMessage: String = "Invalid or expired Gemini API key. Please check your key in Settings."
    ) : GeminiError(userMessage, canRetry = false, isRecoverableWithFallback = false)

    data class PermissionDenied(
        override val userMessage: String = "Permission denied for this API key or model. Ensure your key has access."
    ) : GeminiError(userMessage, canRetry = false, isRecoverableWithFallback = false)

    data class ModelNotFound(
        val model: String,
        override val userMessage: String = "Model '$model' was not found or is discontinued. Try another model."
    ) : GeminiError(userMessage, canRetry = false, isRecoverableWithFallback = true)

    data class QuotaExceeded(
        val retryAfterSeconds: Long? = null,
        override val userMessage: String = "Gemini API quota exceeded for current model. Quota limit reached."
    ) : GeminiError(userMessage, canRetry = true, isRecoverableWithFallback = true)

    data class RateLimited(
        val retryAfterSeconds: Long? = null,
        override val userMessage: String = "Request rate limit reached. Waiting for server..."
    ) : GeminiError(userMessage, canRetry = true, isRecoverableWithFallback = true)

    data class ServerError(
        val code: Int,
        override val userMessage: String = "Google Gemini server error ($code). Server temporarily unavailable."
    ) : GeminiError(userMessage, canRetry = true, isRecoverableWithFallback = true)

    data class NetworkError(
        val details: String,
        override val userMessage: String = "Network connection failed. Please verify your internet connection."
    ) : GeminiError(userMessage, canRetry = true, isRecoverableWithFallback = false)

    data class Timeout(
        override val userMessage: String = "Connection to Gemini timed out. Please try again."
    ) : GeminiError(userMessage, canRetry = true, isRecoverableWithFallback = true)

    data class InvalidImage(
        val details: String,
        override val userMessage: String = "Invoice image could not be processed: $details"
    ) : GeminiError(userMessage, canRetry = false, isRecoverableWithFallback = false)

    data class ImageTooLarge(
        override val userMessage: String = "Invoice image size exceeds Gemini payload limits."
    ) : GeminiError(userMessage, canRetry = false, isRecoverableWithFallback = false)

    data class InvalidRequest(
        val details: String,
        override val userMessage: String = "Malformed request payload: $details"
    ) : GeminiError(userMessage, canRetry = false, isRecoverableWithFallback = false)

    data class ContentBlocked(
        val reason: String,
        override val userMessage: String = "Invoice extraction was flagged by safety filters: $reason"
    ) : GeminiError(userMessage, canRetry = false, isRecoverableWithFallback = false)

    data class InvalidJson(
        val details: String,
        override val userMessage: String = "Could not parse AI invoice response."
    ) : GeminiError(userMessage, canRetry = true, isRecoverableWithFallback = true)

    data class EmptyResponse(
        override val userMessage: String = "Gemini returned an empty response. Please re-scan."
    ) : GeminiError(userMessage, canRetry = true, isRecoverableWithFallback = true)

    data class NoApiKey(
        override val userMessage: String = "No Gemini API key configured. Tap Settings to enter your key."
    ) : GeminiError(userMessage, canRetry = false, isRecoverableWithFallback = false)

    data class Unknown(
        val details: String,
        override val userMessage: String = "Unexpected error occurred: $details"
    ) : GeminiError(userMessage, canRetry = true, isRecoverableWithFallback = false)
}

