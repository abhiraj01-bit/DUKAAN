package com.dukaan.app.core.ocr

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "OcrLanguageRouter"

/**
 * Decides which OCR engine(s) to use based on requested languages and availability.
 *
 * Routing rules (as specified in requirements):
 * ┌──────────────────────────┬───────────────────────────────────────────────┐
 * │ Language(s)              │ Engine                                        │
 * ├──────────────────────────┼───────────────────────────────────────────────┤
 * │ English                  │ MLKit Latin (offline)                         │
 * │ Hindi                    │ MLKit Devanagari (offline)                    │
 * │ Punjabi (Gurmukhi)       │ Gemini when online; offline -> unsupported     │
 * │ English + Hindi          │ MLKit Latin + MLKit Devanagari (merged)       │
 * │ English + Punjabi        │ MLKit Latin (offline) + Gemini (Gurmukhi)     │
 * └──────────────────────────┴───────────────────────────────────────────────┘
 *
 * IMPORTANT: We never claim Devanagari models recognize Gurmukhi. That is
 * explicitly prohibited in the requirements.
 */
@Singleton
class OcrLanguageRouter @Inject constructor(
    private val mlKitEngine: MlKitOcrEngine
) {
    /**
     * Execute offline-only OCR and return the result.
     * For any languages not supported offline, they are noted in [OcrResult.Success.unsupportedOfflineLanguages].
     *
     * @param image Preprocessed Bitmap.
     * @param requestedLanguages User/detection hint; defaults to all when empty (auto-detect).
     */
    suspend fun runOfflineOcr(
        image: Bitmap,
        requestedLanguages: Set<OcrLanguage> = OcrLanguage.entries.toSet()
    ): OcrResult = withContext(Dispatchers.IO) {
        try {
            val offlineLanguages = requestedLanguages.filter { it.supportsOffline }.toSet()
            val onlineOnlyLanguages = requestedLanguages.filter { !it.supportsOffline }.toSet()

            if (offlineLanguages.isEmpty()) {
                Log.w(TAG, "All requested languages ($requestedLanguages) require network – no offline OCR possible")
                return@withContext OcrResult.Failure(
                    reason = "Selected language(s) require Gemini AI for recognition. " +
                             "Please connect to internet or select a different language.",
                    isRecoverable = false
                )
            }

            val result = mlKitEngine.recognize(image, offlineLanguages)

            // Merge in the unsupported offline note
            return@withContext when (result) {
                is OcrResult.Success -> result.copy(
                    unsupportedOfflineLanguages = result.unsupportedOfflineLanguages + onlineOnlyLanguages
                )
                is OcrResult.Partial -> result.copy(
                    failedLanguages = result.failedLanguages + onlineOnlyLanguages
                )
                is OcrResult.Failure -> result
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Log.e(TAG, "Offline OCR routing failed: ${t.message}", t)
            OcrResult.Failure(
                reason = "Offline OCR error: ${t.message}",
                isRecoverable = true
            )
        }
    }

    /**
     * Returns the set of languages in [requestedLanguages] that require network-based recognition.
     */
    fun getNetworkRequiredLanguages(requestedLanguages: Set<OcrLanguage>): Set<OcrLanguage> =
        requestedLanguages.filter { !it.supportsOffline }.toSet()

    /**
     * Returns whether any of the requested languages require Gemini for offline images.
     */
    fun requiresGeminiForAnyLanguage(requestedLanguages: Set<OcrLanguage>): Boolean =
        requestedLanguages.any { !it.supportsOffline }

    /**
     * Instance delegator for [detectScriptsInText].
     */
    fun detectScriptsInText(text: String): Set<OcrLanguage> = Companion.detectScriptsInText(text)

    companion object {
        /**
         * Detects probable script regions from raw text output.
         * Used to refine [OcrLanguage] sets for subsequent engine passes.
         */
        fun detectScriptsInText(text: String): Set<OcrLanguage> {
            val detected = mutableSetOf<OcrLanguage>()

            // Devanagari Unicode block: U+0900–U+097F
            if (text.any { it.code in 0x0900..0x097F }) {
                detected.add(OcrLanguage.HINDI)
            }

            // Gurmukhi Unicode block: U+0A00–U+0A7F
            if (text.any { it.code in 0x0A00..0x0A7F }) {
                detected.add(OcrLanguage.PUNJABI)
            }

            // Latin / ASCII text: alphanumeric in ASCII range suggests English/Latin
            if (text.any { it.isLetter() && it.code in 0x0041..0x007A }) {
                detected.add(OcrLanguage.ENGLISH)
            }

            return detected.ifEmpty { setOf(OcrLanguage.ENGLISH) }
        }
    }
}
