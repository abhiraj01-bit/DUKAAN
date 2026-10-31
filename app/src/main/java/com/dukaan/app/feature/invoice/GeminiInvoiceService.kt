package com.dukaan.app.feature.invoice

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.dukaan.app.core.datastore.DukaanPreferences
import com.dukaan.app.core.util.UuidGenerator
import com.dukaan.app.domain.model.Invoice
import com.dukaan.app.domain.model.InvoiceItem
import com.dukaan.app.domain.model.InvoiceStatus
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
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
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

private const val TAG = "GeminiInvoiceService"

/**
 * Result of the invoice processing pipeline.
 */
sealed class ScanResult {
    data class Success(val invoice: Invoice, val modelUsed: String) : ScanResult()
    data class Failure(val error: GeminiError, val rawOcrText: String = "", val imagePath: String = "") : ScanResult()
    data class LocalOcrSuccess(val invoice: Invoice, val rawOcrText: String) : ScanResult()
}

@Singleton
class GeminiInvoiceService @Inject constructor(
    private val preferences: DukaanPreferences,
    private val modelManager: GeminiModelManager
) {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Complete invoice image processing pipeline:
     * 1. Safe image persistence & downscaling.
     * 2. Background local OCR execution.
     * 3. Config validation (API key, model).
     * 4. Multi-model fallback execution with exponential backoff.
     * 5. Robust JSON parsing.
     * 6. Diagnostic tracking.
     */
    suspend fun processInvoiceImage(
        context: Context,
        bitmap: Bitmap,
        invoiceId: String = UuidGenerator.generate(),
        onStateUpdate: suspend (InvoiceScannerState) -> Unit = {}
    ): ScanResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        // 1. Memory-safe downscaling and disk persistence
        val imagePath = try {
            saveImageToInternalStorage(context, bitmap, invoiceId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save invoice image: ${e.message}")
            return@withContext ScanResult.Failure(
                GeminiError.InvalidImage("Failed to save image: ${e.message}"),
                "",
                ""
            )
        }

        // 2. Run local ML Kit OCR in parallel for instant fallback readiness
        val localOcrDeferred = async {
            runMlKitOcr(bitmap)
        }

        // 3. Read dynamic configuration
        val apiKey = preferences.geminiApiKey.first().trim()
        val primaryModel = preferences.geminiModel.first().trim().ifBlank { "gemini-1.5-flash" }
        val fallbackEnabled = preferences.geminiFallbackEnabled.first()

        // Safe diagnostic logging (NEVER logs API key or auth headers)
        Log.d(TAG, "Scan session started: apiKeyConfigured=${apiKey.isNotBlank()}, primaryModel=$primaryModel, fallbackEnabled=$fallbackEnabled")

        if (apiKey.isBlank()) {
            val localOcr = localOcrDeferred.await()
            preferences.updateScanDiagnostics(primaryModel, "No API Key", System.currentTimeMillis() - startTime, "NO_API_KEY")
            return@withContext ScanResult.Failure(GeminiError.NoApiKey(), localOcr, imagePath)
        }

        if (!modelManager.validateModelName(primaryModel)) {
            val localOcr = localOcrDeferred.await()
            val error = GeminiError.ModelNotFound(primaryModel, "Invalid model name '$primaryModel' in Settings. Please correct it.")
            preferences.updateScanDiagnostics(primaryModel, "Invalid Model", System.currentTimeMillis() - startTime, "INVALID_MODEL_NAME")
            return@withContext ScanResult.Failure(error, localOcr, imagePath)
        }

        // 4. Encode image to Base64 safely
        val base64Image = try {
            encodeBitmapToBase64(bitmap)
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "OOM while encoding image")
            val localOcr = localOcrDeferred.await()
            return@withContext ScanResult.Failure(GeminiError.ImageTooLarge(), localOcr, imagePath)
        } catch (e: Exception) {
            Log.e(TAG, "Error encoding image: ${e.message}")
            val localOcr = localOcrDeferred.await()
            return@withContext ScanResult.Failure(GeminiError.InvalidImage(e.message ?: "Encoding error"), localOcr, imagePath)
        }

        // 5. Build candidate models list
        val candidateModels = modelManager.getCandidateModels(primaryModel, fallbackEnabled)
        Log.d(TAG, "Candidate models for scan: $candidateModels")

        val retryPolicy = RetryPolicy(maxRetries = 2)
        var lastError: GeminiError = GeminiError.Unknown("No attempts executed")
        var successfullyUsedModel: String? = null
        var extractedInvoice: Invoice? = null

        // 6. Iterate through candidate models
        for ((index, model) in candidateModels.withIndex()) {
            val isFallback = index > 0
            if (isFallback) {
                Log.d(TAG, "Switching to fallback model: $model (previous model failed)")
                onStateUpdate(InvoiceScannerState.SwitchingModel(candidateModels[index - 1], model))
            }

            onStateUpdate(InvoiceScannerState.SendingToGemini(model, attemptNumber = 1))

            val modelResult = retryPolicy.execute(
                onRetry = { attemptNum, delayMs, error ->
                    Log.d(TAG, "Retrying model $model (attempt $attemptNum after ${delayMs}ms) due to: ${error::class.simpleName}")
                    onStateUpdate(InvoiceScannerState.Retrying(model, attemptNum))
                }
            ) { attempt ->
                executeGeminiRequest(model, apiKey, base64Image)
            }

            if (modelResult.isSuccess) {
                val rawResponseJson = modelResult.getOrThrow()
                onStateUpdate(InvoiceScannerState.ExtractingData)

                val parseResult = parseGeminiResponse(rawResponseJson, invoiceId, imagePath)
                if (parseResult.isSuccess) {
                    extractedInvoice = parseResult.getOrThrow()
                    successfullyUsedModel = model
                    break
                } else {
                    val parseEx = parseResult.exceptionOrNull()
                    Log.w(TAG, "Model $model returned unparseable content: ${parseEx?.message}")
                    lastError = GeminiError.InvalidJson(parseEx?.message ?: "JSON parse error")
                }
            } else {
                val ex = modelResult.exceptionOrNull()
                lastError = (ex as? GeminiException)?.error ?: GeminiError.Unknown(ex?.message ?: "Network error")
                Log.w(TAG, "Model $model failed with: ${lastError::class.simpleName} (${lastError.userMessage})")

                // If error is NOT recoverable with fallback (e.g. Invalid API key, Image too large), stop immediately!
                if (!modelManager.shouldAttemptFallback(lastError)) {
                    Log.d(TAG, "Halting fallback attempts due to non-recoverable error (${lastError::class.simpleName})")
                    break
                }
            }
        }

        val totalDuration = System.currentTimeMillis() - startTime
        val localOcr = localOcrDeferred.await()

        // 7. Complete and return
        if (extractedInvoice != null && successfullyUsedModel != null) {
            Log.d(TAG, "Invoice scan succeeded using model $successfullyUsedModel in ${totalDuration}ms (${extractedInvoice.items.size} items)")
            preferences.updateScanDiagnostics(successfullyUsedModel, "SUCCESS", totalDuration, "")
            ScanResult.Success(extractedInvoice, successfullyUsedModel)
        } else {
            Log.e(TAG, "All model attempts failed in ${totalDuration}ms. Last error: ${lastError::class.simpleName}")
            preferences.updateScanDiagnostics(
                candidateModels.firstOrNull() ?: primaryModel,
                "FAILED",
                totalDuration,
                lastError::class.simpleName ?: "ERROR"
            )
            ScanResult.Failure(lastError, localOcr, imagePath)
        }
    }

    /**
     * Executes HTTP POST call to Gemini REST API via OkHttp.
     * Uses dynamic model and sets API key in `x-goog-api-key` header (never query param).
     */
    private suspend fun executeGeminiRequest(
        model: String,
        apiKey: String,
        base64Image: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        val requestStartTime = System.currentTimeMillis()

        val prompt = """
You are an expert invoice extraction assistant for Indian retail and grocery (kirana) stores.
Analyze the supplied invoice image carefully.
Extract all available invoice fields accurately.
Return valid JSON only.

Required structure:
{
  "supplier_name": null,
  "invoice_number": null,
  "invoice_date": null,
  "due_date": null,
  "subtotal": null,
  "discount": null,
  "tax": null,
  "grand_total": null,
  "items": [
    {
      "product_name": "",
      "quantity": null,
      "unit": null,
      "unit_price": null,
      "tax_percentage": null,
      "line_total": null
    }
  ],
  "warnings": []
}

Rules:
- Never invent unreadable values.
- Use null for missing information.
- Preserve actual invoice values.
- Do not calculate missing values by guessing.
- Include all identifiable invoice items.
- Return JSON without Markdown code fences.
        """.trimIndent()

        val requestPayload = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                        put(JSONObject().apply {
                            put("inline_data", JSONObject().apply {
                                put("mime_type", "image/jpeg")
                                put("data", base64Image)
                            })
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("response_mime_type", "application/json")
                put("temperature", 0.1)
            })
        }

        val requestBody = requestPayload.toString().toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(endpoint)
            .addHeader("x-goog-api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val duration = System.currentTimeMillis() - requestStartTime
                val code = response.code
                Log.d(TAG, "Gemini response HTTP $code received in ${duration}ms for model=$model")

                val bodyString = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    if (bodyString.isBlank()) {
                        return@withContext Result.failure(GeminiException(GeminiError.EmptyResponse()))
                    }
                    Result.success(bodyString)
                } else {
                    val retryAfterSeconds = response.header("Retry-After")?.toLongOrNull()
                    val error = classifyHttpError(code, bodyString, model, retryAfterSeconds)
                    Result.failure(GeminiException(error))
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (te: SocketTimeoutException) {
            Log.w(TAG, "Gemini request timed out: ${te.message}")
            Result.failure(GeminiException(GeminiError.Timeout()))
        } catch (ioe: IOException) {
            Log.w(TAG, "Gemini network failure: ${ioe.message}")
            Result.failure(GeminiException(GeminiError.NetworkError(ioe.localizedMessage ?: "Connection error")))
        } catch (t: Throwable) {
            Log.e(TAG, "Unexpected HTTP exception: ${t.message}")
            Result.failure(GeminiException(GeminiError.Unknown(t.message ?: "Unknown error")))
        }
    }

    /**
     * Maps HTTP status codes and response error JSON to typed GeminiError.
     */
    private fun classifyHttpError(
        code: Int,
        body: String,
        model: String,
        retryAfter: Long?
    ): GeminiError {
        val lowerBody = body.lowercase()
        return when (code) {
            400 -> {
                when {
                    lowerBody.contains("image") || lowerBody.contains("payload") ->
                        GeminiError.InvalidImage("Invalid image encoding or format")
                    lowerBody.contains("model") ->
                        GeminiError.ModelNotFound(model, "Model '$model' rejected by server (HTTP 400)")
                    else ->
                        GeminiError.InvalidRequest("Malformed request payload (HTTP 400)")
                }
            }
            401 -> GeminiError.InvalidApiKey("Invalid Gemini API key (HTTP 401)")
            403 -> {
                if (lowerBody.contains("quota") || lowerBody.contains("rate")) {
                    GeminiError.QuotaExceeded(retryAfter, "Quota exceeded for model '$model' (HTTP 403)")
                } else {
                    GeminiError.PermissionDenied("Access denied or API not enabled for key (HTTP 403)")
                }
            }
            404 -> GeminiError.ModelNotFound(model, "Model '$model' not found or discontinued (HTTP 404)")
            429 -> {
                if (lowerBody.contains("quota") || lowerBody.contains("resource_exhausted")) {
                    GeminiError.QuotaExceeded(retryAfter, "Daily or project quota exceeded for model '$model' (HTTP 429)")
                } else {
                    GeminiError.RateLimited(retryAfter, "Rate limit reached for model '$model' (HTTP 429)")
                }
            }
            500, 502, 503, 504 -> GeminiError.ServerError(code, "Google Gemini server error ($code)")
            else -> GeminiError.Unknown("Gemini API error (HTTP $code)")
        }
    }

    /**
     * Parses the Gemini envelope and maps valid JSON to domain Invoice & items.
     * Guaranteed never to invent fake or hardcoded default items.
     */
    private fun parseGeminiResponse(
        rawEnvelopeJson: String,
        invoiceId: String,
        imagePath: String
    ): Result<Invoice> {
        return try {
            val root = JSONObject(rawEnvelopeJson)
            val candidates = root.optJSONArray("candidates")
                ?: return Result.failure(IllegalArgumentException("No candidates returned by Gemini"))

            if (candidates.length() == 0) {
                return Result.failure(IllegalArgumentException("Empty candidates array in response"))
            }

            val firstCandidate = candidates.getJSONObject(0)
            val finishReason = firstCandidate.optString("finishReason", "")
            if (finishReason == "SAFETY" || finishReason == "BLOCKLIST") {
                return Result.failure(GeminiException(GeminiError.ContentBlocked(finishReason)))
            }

            val content = firstCandidate.optJSONObject("content")
                ?: return Result.failure(IllegalArgumentException("No content object in candidate"))
            val parts = content.optJSONArray("parts")
                ?: return Result.failure(IllegalArgumentException("No parts array in content"))
            if (parts.length() == 0) {
                return Result.failure(IllegalArgumentException("Empty parts array"))
            }

            val rawContentText = parts.getJSONObject(0).optString("text", "")
            if (rawContentText.isBlank()) {
                return Result.failure(IllegalArgumentException("Empty text in Gemini content part"))
            }

            // Clean markdown code fences if Gemini added them
            val cleanJson = rawContentText.trim()
                .removePrefix("```json")
                .removePrefix("```JSON")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val obj = JSONObject(cleanJson)

            val supplierName = obj.optString("supplier_name", "").let { if (it == "null") "" else it.trim() }
            val invoiceNumber = obj.optString("invoice_number", "").let { if (it == "null") "" else it.trim() }
            val grandTotalDouble = if (obj.isNull("grand_total")) 0.0 else obj.optDouble("grand_total", 0.0)
            val grandTotalPaise = (grandTotalDouble * 100).toLong()

            val itemsArray = obj.optJSONArray("items") ?: JSONArray()
            val items = mutableListOf<InvoiceItem>()

            for (i in 0 until itemsArray.length()) {
                val itemObj = itemsArray.optJSONObject(i) ?: continue
                val rawName = itemObj.optString("product_name", "")
                val name = if (rawName.isBlank() || rawName == "null") "Item ${i + 1}" else rawName.trim()
                val qty = if (itemObj.isNull("quantity")) 1.0 else itemObj.optDouble("quantity", 1.0)
                val unit = itemObj.optString("unit", "pcs").let { if (it == "null" || it.isBlank()) "pcs" else it.trim() }
                val unitPrice = if (itemObj.isNull("unit_price")) 0.0 else itemObj.optDouble("unit_price", 0.0)
                val unitPricePaise = (unitPrice * 100).toLong()
                val lineTotal = if (itemObj.isNull("line_total")) qty * unitPrice else itemObj.optDouble("line_total", qty * unitPrice)
                val lineTotalPaise = (lineTotal * 100).toLong()

                items.add(
                    InvoiceItem(
                        id = UuidGenerator.generate(),
                        invoiceId = invoiceId,
                        productName = name,
                        quantity = qty,
                        unit = unit,
                        purchasePricePaise = unitPricePaise,
                        lineTotalPaise = lineTotalPaise
                    )
                )
            }

            val calculatedTotal = if (grandTotalPaise > 0) grandTotalPaise else items.sumOf { it.lineTotalPaise }
            val now = System.currentTimeMillis()

            val invoice = Invoice(
                id = invoiceId,
                invoiceNumber = invoiceNumber,
                supplierName = supplierName,
                invoiceDate = now,
                subtotalPaise = calculatedTotal,
                grandTotalPaise = calculatedTotal,
                status = InvoiceStatus.DRAFT,
                imagePath = imagePath,
                rawOcrText = rawContentText,
                items = items,
                scannedAt = now,
                createdAt = now,
                updatedAt = now
            )

            Result.success(invoice)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /**
     * On-device ML Kit OCR helper using bundled model. Never throws.
     */
    suspend fun runMlKitOcr(bitmap: Bitmap): String {
        return try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val visionText = suspendCancellableCoroutine<com.google.mlkit.vision.text.Text?> { cont ->
                recognizer.process(inputImage)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener {
                        Log.w(TAG, "ML Kit OCR failed: ${it.message}")
                        cont.resume(null)
                    }
            }
            visionText?.text ?: ""
        } catch (t: Throwable) {
            Log.w(TAG, "ML Kit text recognition caught error: ${t.message}")
            ""
        }
    }

    /**
     * Creates an invoice from pure offline OCR text when the user selects 'Use Offline OCR'.
     */
    fun createInvoiceFromOcr(ocrText: String, invoiceId: String, imagePath: String): Invoice {
        val parsed = InvoiceParser.parse(ocrText, invoiceId)
        val now = System.currentTimeMillis()
        return Invoice(
            id = invoiceId,
            invoiceNumber = parsed.invoiceNumber,
            supplierName = parsed.supplierName,
            invoiceDate = if (parsed.invoiceDate > 0) parsed.invoiceDate else now,
            subtotalPaise = parsed.grandTotalPaise,
            grandTotalPaise = parsed.grandTotalPaise,
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
     * Creates a blank manual invoice for the captured image when user chooses 'Enter Manually'.
     */
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
}
