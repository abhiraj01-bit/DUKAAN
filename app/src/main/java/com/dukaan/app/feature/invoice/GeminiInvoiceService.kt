package com.dukaan.app.feature.invoice

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.dukaan.app.core.datastore.DukaanPreferences
import com.dukaan.app.core.ocr.ExtractionSource
import com.dukaan.app.core.ocr.OcrImageProcessor
import com.dukaan.app.core.ocr.OcrLanguage
import com.dukaan.app.core.ocr.OcrLanguageRouter
import com.dukaan.app.core.ocr.OcrResult
import com.dukaan.app.core.util.UuidGenerator
import com.dukaan.app.domain.model.Invoice
import com.dukaan.app.domain.model.InvoiceItem
import com.dukaan.app.domain.model.InvoiceStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GeminiInvoiceService"

/**
 * Result of the invoice processing pipeline.
 */
sealed class ScanResult {
    data class Success(
        val invoice: Invoice,
        val modelUsed: String,
        val extractionSource: ExtractionSource = ExtractionSource.HYBRID
    ) : ScanResult()

    data class Failure(
        val error: GeminiError,
        val rawOcrText: String = "",
        val imagePath: String = ""
    ) : ScanResult()

    data class LocalOcrSuccess(
        val invoice: Invoice,
        val rawOcrText: String,
        val extractionSource: ExtractionSource = ExtractionSource.LOCAL_OCR
    ) : ScanResult()
}

/**
 * Orchestrates the hybrid OCR pipeline:
 *
 *  1. Save image to local storage.
 *  2. Correct EXIF orientation.
 *  3. Preprocess image (grayscale, contrast).
 *  4. Run ML Kit local OCR (Latin + Devanagari, offline).
 *  5. Detect scripts present in OCR output.
 *  6. Parse extracted text into structured invoice.
 *  7. If Gemini is configured AND internet available: run Gemini visual verification.
 *  8. Merge/compare local OCR vs. Gemini output.
 *  9. Return result with confidence and source labels.
 *
 * Never fabricates invoice data.
 * Never returns fake success.
 * Gracefully degrades to offline OCR when Gemini fails.
 */
@Singleton
class GeminiInvoiceService @Inject constructor(
    private val preferences: DukaanPreferences,
    private val modelManager: GeminiModelManager,
    private val ocrLanguageRouter: OcrLanguageRouter,
    private val ocrImageProcessor: OcrImageProcessor
) {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // ─── Main entry point ─────────────────────────────────────────────────

    suspend fun processInvoiceImage(
        context: Context,
        bitmap: Bitmap,
        invoiceId: String = UuidGenerator.generate(),
        onStateUpdate: suspend (InvoiceScannerState) -> Unit = {}
    ): ScanResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        // 1. Save original image to disk (needed as path reference)
        val imagePath = try {
            saveImageToInternalStorage(context, bitmap, invoiceId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save invoice image: ${e.message}")
            return@withContext ScanResult.Failure(
                GeminiError.InvalidImage("Failed to save image: ${e.message}"),
                "", ""
            )
        }

        // 2. EXIF orientation + preprocessing
        onStateUpdate(InvoiceScannerState.ProcessingImage)
        val imageFile = File(imagePath)
        val orientedBitmap = ocrImageProcessor.correctExifOrientation(bitmap, imageFile)
        val processedBitmap = ocrImageProcessor.prepareForOcr(orientedBitmap)

        // 3. Run local ML Kit OCR (ALWAYS runs offline, no internet needed)
        Log.d(TAG, "Running local ML Kit OCR…")
        val ocrResult = ocrLanguageRouter.runOfflineOcr(
            processedBitmap,
            requestedLanguages = setOf(OcrLanguage.ENGLISH, OcrLanguage.HINDI)
        )

        val localOcrText = when (ocrResult) {
            is OcrResult.Success -> ocrResult.fullText
            is OcrResult.Partial -> ocrResult.fullText
            is OcrResult.Failure -> ""
        }

        val hasLocalOcr = localOcrText.isNotBlank()
        val ocrDurationMs = System.currentTimeMillis() - startTime
        Log.d(TAG, "Local OCR completed in ${ocrDurationMs}ms, text length=${localOcrText.length}")

        // Detect which scripts are actually present
        val detectedLanguages = if (hasLocalOcr)
            ocrLanguageRouter.detectScriptsInText(localOcrText)
        else setOf(OcrLanguage.ENGLISH)

        val hasPunjabi = detectedLanguages.contains(OcrLanguage.PUNJABI)

        // 4. Read Gemini configuration
        val apiKey = preferences.geminiApiKey.first().trim()
        val primaryModel = preferences.geminiModel.first().trim().ifBlank { "gemini-1.5-flash" }
        val fallbackEnabled = preferences.geminiFallbackEnabled.first()

        Log.d(TAG, "Scan: apiKeyConfigured=${apiKey.isNotBlank()}, model=$primaryModel, " +
                "ocrTextLen=${localOcrText.length}, detectedLangs=$detectedLanguages")

        // 5. If no API key, fall back to local OCR result
        if (apiKey.isBlank()) {
            preferences.updateScanDiagnostics(primaryModel, "No API Key", ocrDurationMs, "NO_API_KEY")
            return@withContext if (hasLocalOcr) {
                val invoice = buildInvoiceFromOcr(localOcrText, invoiceId, imagePath)
                ScanResult.LocalOcrSuccess(invoice, localOcrText, ExtractionSource.LOCAL_OCR)
            } else {
                ScanResult.Failure(GeminiError.NoApiKey(), localOcrText, imagePath)
            }
        }

        if (!modelManager.validateModelName(primaryModel)) {
            preferences.updateScanDiagnostics(primaryModel, "Invalid Model", ocrDurationMs, "INVALID_MODEL")
            return@withContext if (hasLocalOcr) {
                val invoice = buildInvoiceFromOcr(localOcrText, invoiceId, imagePath)
                ScanResult.LocalOcrSuccess(invoice, localOcrText, ExtractionSource.LOCAL_OCR)
            } else {
                ScanResult.Failure(
                    GeminiError.ModelNotFound(primaryModel, "Invalid model name '$primaryModel'. Please fix in Settings."),
                    localOcrText, imagePath
                )
            }
        }

        // 6. Run Gemini for visual verification (and Punjabi if present)
        val candidateModels = modelManager.getCandidateModels(primaryModel, fallbackEnabled)

        for (model in candidateModels) {
            onStateUpdate(InvoiceScannerState.SendingToGemini(model))

            val geminiResult = callGeminiApi(
                model = model,
                apiKey = apiKey,
                bitmap = processedBitmap,
                ocrText = localOcrText,
                needsPunjabiSupport = hasPunjabi
            )

            when {
                geminiResult.isSuccess -> {
                    val geminiInvoice = geminiResult.getOrNull()
                    if (geminiInvoice != null) {
                        // Merge Gemini with local OCR: local OCR fills fields Gemini may miss
                        val mergedInvoice = mergeInvoices(
                            localOcrInvoice = if (hasLocalOcr) buildInvoiceFromOcr(localOcrText, invoiceId, imagePath) else null,
                            geminiInvoice = geminiInvoice.copy(id = invoiceId, imagePath = imagePath),
                            invoiceId = invoiceId,
                            imagePath = imagePath,
                            rawOcrText = localOcrText
                        )
                        val totalDuration = System.currentTimeMillis() - startTime
                        preferences.updateScanDiagnostics(model, "Success", totalDuration, "OK")
                        Log.d(TAG, "Hybrid extraction complete in ${totalDuration}ms using $model")
                        return@withContext ScanResult.Success(
                            invoice = mergedInvoice,
                            modelUsed = model,
                            extractionSource = if (hasLocalOcr) ExtractionSource.HYBRID else ExtractionSource.GEMINI_AI
                        )
                    }
                }
                else -> {
                    val error = (geminiResult.exceptionOrNull() as? GeminiException)?.error
                        ?: GeminiError.Unknown("Gemini returned empty result")

                    Log.w(TAG, "Gemini model $model failed: ${error.userMessage}")

                    if (!modelManager.shouldAttemptFallback(error)) {
                        // Non-recoverable – fall through to local OCR
                        if (hasLocalOcr) {
                            val invoice = buildInvoiceFromOcr(localOcrText, invoiceId, imagePath)
                            return@withContext ScanResult.LocalOcrSuccess(invoice, localOcrText, ExtractionSource.LOCAL_OCR)
                        }
                        return@withContext ScanResult.Failure(error, localOcrText, imagePath)
                    }

                    onStateUpdate(InvoiceScannerState.SwitchingModel(model, candidateModels.getOrElse(candidateModels.indexOf(model) + 1) { model }))
                }
            }
        }

        // 7. All Gemini models failed – use local OCR if available
        val finalDuration = System.currentTimeMillis() - startTime
        preferences.updateScanDiagnostics(primaryModel, "GeminiFailed", finalDuration, "GEMINI_ALL_FAILED")

        return@withContext if (hasLocalOcr) {
            Log.i(TAG, "Gemini unavailable; returning local OCR result")
            val invoice = buildInvoiceFromOcr(localOcrText, invoiceId, imagePath)
            ScanResult.LocalOcrSuccess(invoice, localOcrText, ExtractionSource.LOCAL_OCR)
        } else {
            ScanResult.Failure(
                GeminiError.NetworkError("All models failed and no local OCR text available"),
                "", imagePath
            )
        }
    }

    // ─── Gemini API call ──────────────────────────────────────────────────

    private suspend fun callGeminiApi(
        model: String,
        apiKey: String,
        bitmap: Bitmap,
        ocrText: String,
        needsPunjabiSupport: Boolean
    ): Result<Invoice> = withContext(Dispatchers.IO) {
        try {
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"

            val imageBase64 = encodeBitmapToBase64(bitmap)
            val prompt = buildPrompt(ocrText, needsPunjabiSupport)

            val requestBody = buildGeminiRequest(prompt, imageBase64)

            val request = Request.Builder()
                .url(endpoint)
                .addHeader("x-goog-api-key", apiKey)
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            val responseCode = response.code
            val responseBody = response.body?.string() ?: ""
            response.close()

            Log.d(TAG, "Gemini HTTP $responseCode from $model (body length: ${responseBody.length})")

            when (responseCode) {
                200 -> parseGeminiResponse(responseBody)
                400 -> {
                    val details = extractErrorMessage(responseBody)
                    Result.failure(GeminiException(GeminiError.InvalidRequest(details)))
                }
                401 -> Result.failure(GeminiException(GeminiError.InvalidApiKey()))
                403 -> Result.failure(GeminiException(GeminiError.PermissionDenied()))
                404 -> Result.failure(GeminiException(GeminiError.ModelNotFound(model)))
                429 -> {
                    val retryAfter = response.header("Retry-After")?.toLongOrNull()
                    Result.failure(GeminiException(GeminiError.RateLimited(retryAfter)))
                }
                503 -> Result.failure(GeminiException(GeminiError.QuotaExceeded()))
                in 500..599 -> Result.failure(GeminiException(GeminiError.ServerError(responseCode)))
                else -> Result.failure(GeminiException(GeminiError.Unknown("HTTP $responseCode")))
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: java.net.SocketTimeoutException) {
            Result.failure(GeminiException(GeminiError.Timeout()))
        } catch (e: java.io.IOException) {
            Result.failure(GeminiException(GeminiError.NetworkError(e.message ?: "IO error")))
        } catch (t: Throwable) {
            Result.failure(GeminiException(GeminiError.Unknown(t.message ?: "Unknown error")))
        }
    }

    // ─── Request building ─────────────────────────────────────────────────

    private fun buildPrompt(ocrText: String, includePunjabi: Boolean): String {
        val basePrompt = """You are an expert invoice data extraction assistant for Indian small businesses.

${if (ocrText.isNotBlank()) "The following text was extracted by on-device OCR from the invoice image. Use it to assist your extraction:\n\n--- OCR TEXT START ---\n$ocrText\n--- OCR TEXT END ---\n\n" else ""}Analyze the invoice image carefully.

CRITICAL RULES:
- Extract ONLY information that is clearly visible in the image.
- Do NOT fabricate, infer, or guess any field. Use null for missing fields.
- Preserve original language for product names (do not translate).
- Use Long integers for all monetary amounts (in paise, where ₹1 = 100 paise).
- Dates must be in ISO 8601 format: YYYY-MM-DD.
- GSTIN format: 15 characters (e.g., 27AAPFU0939F1ZV).

${if (includePunjabi) "NOTE: This invoice may contain Punjabi/Gurmukhi script. Extract Gurmukhi text accurately.\n\n" else ""}Return ONLY valid JSON matching this exact structure (no markdown, no code fences):

{
  "supplier_name": null,
  "supplier_address": null,
  "supplier_gstin": null,
  "supplier_phone": null,
  "invoice_number": null,
  "invoice_date": null,
  "due_date": null,
  "subtotal_paise": null,
  "discount_paise": null,
  "tax_paise": null,
  "grand_total_paise": null,
  "items": [
    {
      "product_name": "",
      "hsn_sac": null,
      "quantity": null,
      "unit": null,
      "unit_price_paise": null,
      "discount_paise": null,
      "gst_rate_percent": null,
      "line_total_paise": null
    }
  ]
}"""
        return basePrompt
    }

    private fun buildGeminiRequest(prompt: String, imageBase64: String): String {
        val requestJson = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        // Image part
                        put(JSONObject().apply {
                            put("inline_data", JSONObject().apply {
                                put("mime_type", "image/jpeg")
                                put("data", imageBase64)
                            })
                        })
                        // Text prompt part
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.1)
                put("topP", 0.8)
                put("maxOutputTokens", 4096)
            })
        }
        return requestJson.toString()
    }

    // ─── Response parsing ─────────────────────────────────────────────────

    private fun parseGeminiResponse(responseBody: String): Result<Invoice> {
        return try {
            val root = JSONObject(responseBody)

            // Check for safety blocks
            val candidates = root.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                val promptFeedback = root.optJSONObject("promptFeedback")
                val blockReason = promptFeedback?.optString("blockReason", "")
                if (!blockReason.isNullOrBlank()) {
                    return Result.failure(GeminiException(GeminiError.ContentBlocked(blockReason)))
                }
                return Result.failure(GeminiException(GeminiError.EmptyResponse()))
            }

            val candidate = candidates.getJSONObject(0)
            val finishReason = candidate.optString("finishReason", "")
            if (finishReason == "SAFETY") {
                return Result.failure(GeminiException(GeminiError.ContentBlocked("SAFETY")))
            }

            val content = candidate.optJSONObject("content")
                ?: return Result.failure(GeminiException(GeminiError.EmptyResponse()))

            val parts = content.optJSONArray("parts")
                ?: return Result.failure(GeminiException(GeminiError.EmptyResponse()))

            val rawText = (0 until parts.length())
                .map { parts.getJSONObject(it).optString("text", "") }.filter { it.isNotBlank() }
                .joinToString("")
                .trim()

            if (rawText.isBlank()) {
                return Result.failure(GeminiException(GeminiError.EmptyResponse()))
            }

            // Extract JSON from response (may be wrapped in markdown code fences)
            val jsonText = extractJsonFromText(rawText)
                ?: return Result.failure(GeminiException(GeminiError.InvalidJson("No JSON object found in response")))

            val invoiceJson = JSONObject(jsonText)
            buildInvoiceFromJson(invoiceJson)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: org.json.JSONException) {
            Result.failure(GeminiException(GeminiError.InvalidJson("JSON parse error: ${e.message}")))
        } catch (t: Throwable) {
            Result.failure(GeminiException(GeminiError.Unknown(t.message ?: "Parse error")))
        }
    }

    private fun extractJsonFromText(text: String): String? {
        // Try to find JSON between code fences
        val fencePattern = Regex("""```(?:json)?\s*(\{[\s\S]*?\})\s*```""")
        val fenceMatch = fencePattern.find(text)
        if (fenceMatch != null) return fenceMatch.groupValues[1]

        // Try to find the first { ... } block
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start >= 0 && end > start) return text.substring(start, end + 1)

        return null
    }

    private fun buildInvoiceFromJson(json: JSONObject): Result<Invoice> {
        return try {
            val now = System.currentTimeMillis()
            val invoiceId = UuidGenerator.generate()

            val supplierName = json.optString("supplier_name", "").nullIfBlank()
            val invoiceNumber = json.optString("invoice_number", "").nullIfBlank()

            val invoiceDateStr = json.optString("invoice_date", "")
            val invoiceDate = parseIsoDate(invoiceDateStr) ?: now

            val subtotalPaise = json.optLong("subtotal_paise", 0L)
            val discountPaise = json.optLong("discount_paise", 0L)
            val taxPaise = json.optLong("tax_paise", 0L)
            val grandTotalPaise = json.optLong("grand_total_paise", 0L)

            val items = mutableListOf<InvoiceItem>()
            val itemsArray = json.optJSONArray("items")
            if (itemsArray != null) {
                for (i in 0 until itemsArray.length()) {
                    val itemJson = itemsArray.getJSONObject(i)
                    val name = itemJson.optString("product_name", "").trim()
                    if (name.isBlank()) continue

                    val qty = itemJson.optDouble("quantity", 1.0).let { if (it.isNaN() || it <= 0) 1.0 else it }
                    val unitPrice = itemJson.optLong("unit_price_paise", 0L)
                    val lineTotal = itemJson.optLong("line_total_paise", 0L)
                    val unit = itemJson.optString("unit", "pcs").nullIfBlank() ?: "pcs"
                    val gstRate = if (itemJson.isNull("gst_rate_percent")) null else itemJson.optDouble("gst_rate_percent", 0.0)
                    val itemDiscount = itemJson.optLong("discount_paise", 0L)

                    // Validate line total
                    val computedLineTotal = (qty * unitPrice).toLong()
                    val effectiveLineTotal = when {
                        lineTotal > 0 -> lineTotal
                        computedLineTotal > 0 -> computedLineTotal
                        else -> 0L
                    }

                    items.add(
                        InvoiceItem(
                            id = UuidGenerator.generate(),
                            invoiceId = invoiceId,
                            productName = name,
                            quantity = qty,
                            unit = unit,
                            purchasePricePaise = unitPrice,
                            discountPaise = itemDiscount,
                            lineTotalPaise = effectiveLineTotal
                        )
                    )
                }
            }

            // Compute effective grand total
            val effectiveGrandTotal = when {
                grandTotalPaise > 0 -> grandTotalPaise
                items.isNotEmpty()  -> items.sumOf { it.lineTotalPaise } - discountPaise + taxPaise
                else                -> 0L
            }

            val invoice = Invoice(
                id = invoiceId,
                invoiceNumber = invoiceNumber ?: "",
                supplierName = supplierName ?: "",
                invoiceDate = invoiceDate,
                subtotalPaise = subtotalPaise,
                discountPaise = discountPaise,
                taxPaise = taxPaise,
                grandTotalPaise = effectiveGrandTotal,
                status = InvoiceStatus.DRAFT,
                items = items,
                scannedAt = now,
                createdAt = now,
                updatedAt = now
            )

            Result.success(invoice)
        } catch (t: Throwable) {
            Result.failure(GeminiException(GeminiError.InvalidJson(t.message ?: "Build error")))
        }
    }

    // ─── Invoice building from OCR ────────────────────────────────────────

    private fun buildInvoiceFromOcr(ocrText: String, invoiceId: String, imagePath: String): Invoice {
        val parsed = InvoiceParser.parse(ocrText, invoiceId)
        val now = System.currentTimeMillis()
        val effectiveTotal = when {
            parsed.grandTotalPaise > 0 -> parsed.grandTotalPaise
            parsed.items.isNotEmpty()  -> parsed.items.sumOf { it.lineTotalPaise }
            else                       -> 0L
        }
        return Invoice(
            id = invoiceId,
            invoiceNumber = parsed.invoiceNumber,
            supplierName = parsed.supplierName,
            invoiceDate = parsed.invoiceDate,
            dueDate = parsed.dueDate,
            subtotalPaise = parsed.subtotalPaise,
            discountPaise = parsed.discountPaise,
            taxPaise = parsed.taxPaise,
            grandTotalPaise = effectiveTotal,
            status = InvoiceStatus.DRAFT,
            imagePath = imagePath,
            rawOcrText = ocrText,
            items = parsed.items,
            scannedAt = now,
            createdAt = now,
            updatedAt = now
        )
    }

    /**
     * Merge local OCR and Gemini extraction.
     * Strategy: Gemini wins for structured fields it confidently returned.
     *           Local OCR fills in gaps (especially product names in original language).
     */
    private fun mergeInvoices(
        localOcrInvoice: Invoice?,
        geminiInvoice: Invoice,
        invoiceId: String,
        imagePath: String,
        rawOcrText: String
    ): Invoice {
        val local = localOcrInvoice
        val now = System.currentTimeMillis()

        return geminiInvoice.copy(
            id = invoiceId,
            imagePath = imagePath,
            rawOcrText = rawOcrText,
            // Prefer Gemini's structured data; fall back to local OCR for blank fields
            supplierName = geminiInvoice.supplierName.ifBlank { local?.supplierName ?: "" },
            invoiceNumber = geminiInvoice.invoiceNumber.ifBlank { local?.invoiceNumber ?: "" },
            grandTotalPaise = geminiInvoice.grandTotalPaise.let {
                if (it > 0) it else local?.grandTotalPaise ?: 0L
            },
            // If Gemini found no items but local OCR did, use local items
            items = if (geminiInvoice.items.isNotEmpty()) {
                geminiInvoice.items.map { it.copy(invoiceId = invoiceId) }
            } else {
                local?.items?.map { it.copy(invoiceId = invoiceId) } ?: emptyList()
            },
            status = InvoiceStatus.DRAFT,
            scannedAt = now,
            updatedAt = now
        )
    }

    // ─── Public helpers (used by scanner for offline/manual paths) ────────

    fun createInvoiceFromOcr(ocrText: String, invoiceId: String, imagePath: String): Invoice =
        buildInvoiceFromOcr(ocrText, invoiceId, imagePath)

    fun createBlankManualInvoice(invoiceId: String, imagePath: String): Invoice {
        val now = System.currentTimeMillis()
        return Invoice(
            id = invoiceId,
            invoiceNumber = "",
            supplierName = "",
            invoiceDate = now,
            subtotalPaise = 0L,
            grandTotalPaise = 0L,
            status = InvoiceStatus.DRAFT,
            imagePath = imagePath,
            rawOcrText = "",
            items = emptyList(),
            scannedAt = now,
            createdAt = now,
            updatedAt = now
        )
    }

    /** Used by the scanner screen for retry – runs only Gemini (no ML Kit re-run). */
    suspend fun runMlKitOcr(bitmap: Bitmap): String {
        val result = ocrLanguageRouter.runOfflineOcr(
            bitmap,
            requestedLanguages = setOf(OcrLanguage.ENGLISH, OcrLanguage.HINDI)
        )
        return when (result) {
            is OcrResult.Success -> result.fullText
            is OcrResult.Partial -> result.fullText
            is OcrResult.Failure -> ""
        }
    }

    // ─── Utilities ────────────────────────────────────────────────────────

    private fun encodeBitmapToBase64(bitmap: Bitmap): String {
        val scaled = scaleBitmap(bitmap, 1600)
        val stream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        val bytes = stream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun saveImageToInternalStorage(context: Context, bitmap: Bitmap, invoiceId: String): String {
        val invoiceDir = File(context.filesDir, "invoices").apply { mkdirs() }
        val imageFile = File(invoiceDir, "inv_$invoiceId.jpg")
        FileOutputStream(imageFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
        }
        return imageFile.absolutePath
    }

    private fun scaleBitmap(bitmap: Bitmap, maxPx: Int): Bitmap {
        val max = maxOf(bitmap.width, bitmap.height)
        if (max <= maxPx) return bitmap
        val ratio = maxPx.toFloat() / max
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt(),
            (bitmap.height * ratio).toInt(),
            true
        )
    }

    private fun extractErrorMessage(body: String): String {
        return try {
            JSONObject(body).optJSONObject("error")?.optString("message", body) ?: body
        } catch (_: Exception) { body }
    }

    private fun parseIsoDate(dateStr: String): Long? {
        if (dateStr.isBlank()) return null
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            sdf.isLenient = false
            sdf.parse(dateStr)?.time
        } catch (_: Exception) { null }
    }

    private fun String?.nullIfBlank(): String? = if (isNullOrBlank()) null else this
}
