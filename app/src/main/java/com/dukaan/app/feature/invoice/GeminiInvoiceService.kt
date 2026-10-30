package com.dukaan.app.feature.invoice

import android.content.Context
import android.graphics.Bitmap
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

private const val TAG = "GeminiInvoiceService"

// Sealed class representing every possible outcome of a scan attempt
sealed class ScanResult {
    data class Success(val invoice: Invoice) : ScanResult()
    object NoApiKey : ScanResult()
    data class ApiError(val httpCode: Int, val message: String) : ScanResult()
    data class NetworkError(val message: String) : ScanResult()
    data class ParseError(val message: String) : ScanResult()
    data class OcrFallback(val invoice: Invoice, val ocrText: String) : ScanResult()
    data class OcrEmpty(val invoiceId: String, val imagePath: String) : ScanResult()
}

@Singleton
class GeminiInvoiceService @Inject constructor(
    private val preferences: DukaanPreferences
) {

    /**
     * Main entry point. Returns a ScanResult — never throws, never returns hardcoded data.
     */
    suspend fun processInvoiceImage(
        context: Context,
        bitmap: Bitmap,
        invoiceId: String = UuidGenerator.generate()
    ): ScanResult = withContext(Dispatchers.IO) {

        // 1. Save image
        val imagePath = saveImage(context, bitmap, invoiceId)

        // 2. Read config
        val apiKey = preferences.geminiApiKey.first()
        val model = preferences.geminiModel.first().trim().ifBlank { "gemini-1.5-flash" }

        Log.d(TAG, "API key configured: ${apiKey.isNotBlank()}, model: $model")

        if (apiKey.isBlank()) {
            Log.w(TAG, "No Gemini API key configured — skipping AI scan")
            return@withContext ScanResult.NoApiKey
        }

        // 3. Try Gemini with image
        val startMs = System.currentTimeMillis()
        Log.d(TAG, "Starting Gemini request with model=$model")

        val geminiResult = callGeminiApi(bitmap, apiKey, model, invoiceId, imagePath)
        val durationMs = System.currentTimeMillis() - startMs
        Log.d(TAG, "Gemini request completed in ${durationMs}ms, result=${geminiResult::class.simpleName}")

        when (geminiResult) {
            is ScanResult.Success -> return@withContext geminiResult
            is ScanResult.ApiError, is ScanResult.NetworkError, is ScanResult.ParseError -> {
                // On Gemini failure, try ML Kit OCR as a transparent fallback
                Log.w(TAG, "Gemini failed ($geminiResult), attempting ML Kit OCR fallback")
                val ocrText = runMlKitOcr(bitmap)
                if (ocrText.isBlank()) {
                    Log.w(TAG, "ML Kit OCR returned empty text")
                    return@withContext ScanResult.OcrEmpty(invoiceId, imagePath)
                }
                val parsed = InvoiceParser.parse(ocrText, invoiceId)
                val now = System.currentTimeMillis()
                val invoice = Invoice(
                    id = invoiceId,
                    invoiceNumber = parsed.invoiceNumber.ifBlank { "" },
                    supplierName = parsed.supplierName.ifBlank { "" },
                    invoiceDate = if (parsed.invoiceDate > 0) parsed.invoiceDate else now,
                    grandTotalPaise = parsed.grandTotalPaise,
                    subtotalPaise = parsed.grandTotalPaise,
                    status = InvoiceStatus.DRAFT,
                    imagePath = imagePath,
                    rawOcrText = ocrText,
                    items = parsed.items,
                    scannedAt = now,
                    createdAt = now,
                    updatedAt = now
                )
                return@withContext ScanResult.OcrFallback(invoice, ocrText)
            }
            else -> return@withContext geminiResult
        }
    }

    private suspend fun callGeminiApi(
        bitmap: Bitmap,
        apiKey: String,
        model: String,
        invoiceId: String,
        imagePath: String
    ): ScanResult = withContext(Dispatchers.IO) {

        // Resize to max 1600px on longest side for network efficiency
        val scaled = scaleBitmap(bitmap, 1600)
        val stream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        val base64Image = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)

        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        Log.d(TAG, "POST $endpoint")

        val conn: HttpURLConnection
        try {
            conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                // API key in header — NOT query param
                setRequestProperty("x-goog-api-key", apiKey)
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 30_000
                readTimeout = 30_000
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open connection: ${e.message}")
            return@withContext ScanResult.NetworkError("Failed to connect: ${e.message}")
        }

        val prompt = """
You are an expert invoice extraction assistant for Indian kirana and retail shops.
Analyze the supplied invoice image carefully.
Extract all available information accurately.
Return valid JSON only — no markdown, no code fences, no explanation.

Required JSON structure:
{
  "supplier_name": null,
  "invoice_number": null,
  "invoice_date": null,
  "grand_total": null,
  "items": [
    {
      "product_name": "",
      "quantity": null,
      "unit": null,
      "unit_price": null,
      "line_total": null
    }
  ],
  "warnings": []
}

Rules:
- Use null for any field you cannot read clearly.
- Never invent or guess values.
- Preserve actual values from the invoice exactly.
- Include every line item visible on the invoice.
- Return only the JSON object, nothing else.
        """.trimIndent()

        val requestBody = JSONObject().apply {
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

        try {
            conn.outputStream.bufferedWriter().use { it.write(requestBody.toString()) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write request body: ${e.message}")
            return@withContext ScanResult.NetworkError("Request write failed: ${e.message}")
        }

        val httpCode: Int
        try {
            httpCode = conn.responseCode
        } catch (e: Exception) {
            Log.e(TAG, "Network error reading response code: ${e.message}")
            return@withContext ScanResult.NetworkError("Network error: ${e.message}")
        }

        Log.d(TAG, "HTTP response code: $httpCode")

        if (httpCode != HttpURLConnection.HTTP_OK) {
            val errorBody = try {
                conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            } catch (_: Exception) { "" }

            val userMessage = when (httpCode) {
                400 -> "Invalid request — check model name and image format (HTTP 400)"
                401, 403 -> "Authentication failed — check your Gemini API key (HTTP $httpCode)"
                404 -> "Model '$model' not found — check model name in Settings (HTTP 404)"
                429 -> "API quota exceeded — try again later (HTTP 429)"
                500, 503 -> "Gemini server error — try again later (HTTP $httpCode)"
                else -> "API error (HTTP $httpCode)"
            }
            Log.w(TAG, "API error $httpCode: $errorBody")
            return@withContext ScanResult.ApiError(httpCode, userMessage)
        }

        val responseText = try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read response: ${e.message}")
            return@withContext ScanResult.NetworkError("Failed to read response: ${e.message}")
        }

        Log.d(TAG, "Response received, parsing...")

        // Extract the text content from the Gemini response envelope
        val extractedJson = try {
            val root = JSONObject(responseText)
            val candidates = root.optJSONArray("candidates")
                ?: return@withContext ScanResult.ParseError("No candidates in response")
            if (candidates.length() == 0)
                return@withContext ScanResult.ParseError("Empty candidates array")
            val content = candidates.getJSONObject(0).optJSONObject("content")
                ?: return@withContext ScanResult.ParseError("No content in candidate")
            val parts = content.optJSONArray("parts")
                ?: return@withContext ScanResult.ParseError("No parts in content")
            parts.getJSONObject(0).optString("text", "")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Gemini envelope: ${e.message}")
            return@withContext ScanResult.ParseError("Response envelope parse failed: ${e.message}")
        }

        if (extractedJson.isBlank()) {
            Log.w(TAG, "Gemini returned empty text content")
            return@withContext ScanResult.ParseError("Gemini returned empty response")
        }

        parseGeminiJson(extractedJson, invoiceId, imagePath)
    }

    private fun parseGeminiJson(rawJson: String, invoiceId: String, imagePath: String): ScanResult {
        // Strip any accidental markdown fences
        val cleanJson = rawJson.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()

        val obj = try {
            JSONObject(cleanJson)
        } catch (e: Exception) {
            Log.e(TAG, "JSON parse failed: ${e.message}")
            return ScanResult.ParseError("Invalid JSON from Gemini: ${e.message}")
        }

        val supplierName = obj.optString("supplier_name", "").let { if (it == "null") "" else it }
        val invoiceNumber = obj.optString("invoice_number", "").let { if (it == "null") "" else it }
        val grandTotalDouble = if (obj.isNull("grand_total")) 0.0 else obj.optDouble("grand_total", 0.0)
        val grandTotalPaise = (grandTotalDouble * 100).toLong()

        val itemsArray = obj.optJSONArray("items") ?: JSONArray()
        val items = mutableListOf<InvoiceItem>()

        for (i in 0 until itemsArray.length()) {
            val itemObj = itemsArray.optJSONObject(i) ?: continue
            val name = itemObj.optString("product_name", "").let { if (it == "null" || it.isBlank()) "Item ${i + 1}" else it }
            val qty = if (itemObj.isNull("quantity")) 1.0 else itemObj.optDouble("quantity", 1.0)
            val unit = itemObj.optString("unit", "pcs").let { if (it == "null") "pcs" else it }
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

        Log.d(TAG, "Parsed invoice: supplier='$supplierName', items=${items.size}, total=$calculatedTotal paise")

        val invoice = Invoice(
            id = invoiceId,
            invoiceNumber = invoiceNumber,
            supplierName = supplierName,
            invoiceDate = now,
            subtotalPaise = calculatedTotal,
            grandTotalPaise = calculatedTotal,
            status = InvoiceStatus.DRAFT,
            imagePath = imagePath,
            rawOcrText = rawJson,
            items = items,
            scannedAt = now,
            createdAt = now,
            updatedAt = now
        )

        return ScanResult.Success(invoice)
    }

    private suspend fun runMlKitOcr(bitmap: Bitmap): String {
        return try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val visionText = suspendCancellableCoroutine<com.google.mlkit.vision.text.Text?> { cont ->
                recognizer.process(inputImage)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resume(null) }
            }
            visionText?.text ?: ""
        } catch (e: Throwable) {
            Log.w(TAG, "ML Kit OCR failed: ${e.message}")
            ""
        }
    }

    private fun saveImage(context: Context, bitmap: Bitmap, invoiceId: String): String {
        val invoiceDir = File(context.filesDir, "invoices").apply { mkdirs() }
        val imageFile = File(invoiceDir, "inv_$invoiceId.jpg")
        FileOutputStream(imageFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
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
