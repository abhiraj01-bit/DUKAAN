package com.dukaan.app.feature.invoice

import android.util.Log
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Gemini model selection, validation, and smart fallback ordering.
 */
@Singleton
class GeminiModelManager @Inject constructor() {

    companion object {
        private const val TAG = "GeminiModelManager"

        // Production-tested currently available Gemini models
        val SUPPORTED_FALLBACK_MODELS = listOf(
            "gemini-1.5-flash",
            "gemini-2.0-flash",
            "gemini-1.5-pro"
        )

        private val MODEL_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_.-]+$")
    }

    /**
     * Validates whether a given model name string has valid characters and structure.
     */
    fun validateModelName(model: String): Boolean {
        val trimmed = model.trim()
        if (trimmed.isBlank() || trimmed.length < 3 || trimmed.length > 64) return false
        return MODEL_NAME_PATTERN.matcher(trimmed).matches()
    }

    /**
     * Resolves the list of models to try in sequence.
     * Primary configured model is always first.
     * If fallback is disabled, only returns the primary model.
     */
    fun getCandidateModels(primaryModel: String, fallbackEnabled: Boolean = true): List<String> {
        val cleanPrimary = primaryModel.trim().ifBlank { "gemini-1.5-flash" }
        if (!fallbackEnabled) {
            return listOf(cleanPrimary)
        }

        val candidates = mutableListOf<String>()
        candidates.add(cleanPrimary)

        for (fallback in SUPPORTED_FALLBACK_MODELS) {
            if (!candidates.contains(fallback)) {
                candidates.add(fallback)
            }
        }
        return candidates
    }

    /**
     * Determines whether an error is suitable for attempting a fallback model.
     */
    fun shouldAttemptFallback(error: GeminiError): Boolean {
        return error.isRecoverableWithFallback
    }
}

