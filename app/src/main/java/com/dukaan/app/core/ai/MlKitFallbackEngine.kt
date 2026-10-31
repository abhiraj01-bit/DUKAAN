package com.dukaan.app.core.ai

import android.util.Log
import com.dukaan.app.core.ai.contracts.*
import com.dukaan.app.core.ocr.MlKitOcrEngine
import com.dukaan.app.core.ocr.OcrLanguage
import com.dukaan.app.core.ocr.OcrResult
import com.dukaan.app.feature.invoice.InvoiceParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MlKitFallbackEngine"

/**
 * Instant zero-download on-device OCR engine powered by Google ML Kit (bundled Latin + Devanagari).
 *
 * Runs instantaneously without requiring internet or multi-gigabyte model weights.
 * Serves as the primary engine for low-RAM devices (<4GB RAM) or when the shopkeeper has not downloaded Gemma 3n.
 */
@Singleton
class MlKitFallbackEngine @Inject constructor(
    private val mlKitOcrEngine: MlKitOcrEngine,
    private val validator: InvoiceValidator
) : VLMInferenceEngine {

    override val engineId: String = "mlkit-offline-bundled"
    override val isZeroDownload: Boolean = true

    override suspend fun isReady(): Boolean = true

    override suspend fun processInvoice(request: OCRRequest): OCRResponse = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        Log.i(TAG, "Starting zero-download ML Kit recognition for invoice: ${request.invoiceId}")

        // 1. Run local ML Kit OCR across Latin + Devanagari scripts
        val ocrResult = mlKitOcrEngine.recognize(
            image = request.bitmap,
            requestedLanguages = setOf(OcrLanguage.ENGLISH, OcrLanguage.HINDI)
        )

        val rawText = when (ocrResult) {
            is OcrResult.Success -> ocrResult.fullText
            is OcrResult.Partial -> ocrResult.fullText
            is OcrResult.Failure -> ""
        }

        // 2. Parse structured financial fields using heuristic Indian invoice parser
        val parsed = InvoiceParser.parse(rawText, request.invoiceId)

        // 3. Transform parsed items into OCRProduct contracts
        val items = parsed.items.mapIndexed { idx, item ->
            OCRProduct(
                id = item.id,
                productName = OCRField(
                    value = item.productName,
                    rawText = item.productName,
                    confidence = 0.85f,
                    isUncertain = item.productName.isBlank(),
                    source = ExtractionSource.ON_DEVICE_MLKIT
                ),
                hsnSac = OCRField(null, source = ExtractionSource.ON_DEVICE_MLKIT),
                quantity = OCRField(
                    value = item.quantity,
                    rawText = item.quantity.toString(),
                    confidence = 0.90f,
                    source = ExtractionSource.ON_DEVICE_MLKIT
                ),
                unit = OCRField(item.unit ?: "pcs", source = ExtractionSource.ON_DEVICE_MLKIT),
                unitPricePaise = OCRField(
                    value = item.purchasePricePaise,
                    rawText = (item.purchasePricePaise / 100.0).toString(),
                    confidence = 0.88f,
                    source = ExtractionSource.ON_DEVICE_MLKIT
                ),
                discountPaise = OCRField(item.discountPaise, source = ExtractionSource.ON_DEVICE_MLKIT),
                gstRatePercent = OCRField(null, source = ExtractionSource.ON_DEVICE_MLKIT),
                lineTotalPaise = OCRField(
                    value = item.lineTotalPaise,
                    rawText = (item.lineTotalPaise / 100.0).toString(),
                    confidence = 0.90f,
                    source = ExtractionSource.ON_DEVICE_MLKIT
                )
            )
        }

        // 4. Form preliminary draft to run strict arithmetic validation
        val draft = InvoiceDraft(
            id = request.invoiceId,
            supplierName = parsed.supplierName,
            supplierGstin = parsed.supplierGstin.ifBlank { null },
            supplierPhone = parsed.supplierPhone.ifBlank { null },
            invoiceNumber = parsed.invoiceNumber,
            invoiceDate = parsed.invoiceDate,
            dueDate = parsed.dueDate,
            items = items,
            subtotalPaise = parsed.subtotalPaise,
            discountPaise = parsed.discountPaise,
            taxPaise = parsed.taxPaise,
            grandTotalPaise = parsed.grandTotalPaise,
            imagePath = request.imagePath,
            rawOcrText = rawText,
            extractionSource = ExtractionSource.ON_DEVICE_MLKIT,
            validationResult = InvoiceValidationResult.VALID
        )

        val validation = validator.validateDraft(draft)
        val durationMs = System.currentTimeMillis() - startTime

        Log.i(TAG, "ML Kit OCR completed in ${durationMs}ms with ${items.size} items. Valid=${validation.isValid}")

        OCRResponse(
            invoiceId = request.invoiceId,
            supplierName = OCRField(
                value = parsed.supplierName.ifBlank { null },
                rawText = parsed.supplierName,
                confidence = if (parsed.supplierName.isNotBlank()) 0.85f else null,
                isUncertain = parsed.supplierName.isBlank(),
                source = ExtractionSource.ON_DEVICE_MLKIT
            ),
            supplierGstin = OCRField(
                value = parsed.supplierGstin.ifBlank { null },
                rawText = parsed.supplierGstin,
                confidence = if (parsed.supplierGstin.isNotBlank()) 0.95f else null,
                source = ExtractionSource.ON_DEVICE_MLKIT
            ),
            supplierPhone = OCRField(
                value = parsed.supplierPhone.ifBlank { null },
                rawText = parsed.supplierPhone,
                confidence = if (parsed.supplierPhone.isNotBlank()) 0.90f else null,
                source = ExtractionSource.ON_DEVICE_MLKIT
            ),
            supplierAddress = OCRField(
                value = parsed.supplierAddress.ifBlank { null },
                rawText = parsed.supplierAddress,
                source = ExtractionSource.ON_DEVICE_MLKIT
            ),
            invoiceNumber = OCRField(
                value = parsed.invoiceNumber.ifBlank { null },
                rawText = parsed.invoiceNumber,
                confidence = if (parsed.invoiceNumber.isNotBlank()) 0.88f else null,
                isUncertain = parsed.invoiceNumber.isBlank(),
                source = ExtractionSource.ON_DEVICE_MLKIT
            ),
            invoiceDate = OCRField(
                value = parsed.invoiceDate,
                rawText = parsed.invoiceDate.toString(),
                confidence = 0.90f,
                source = ExtractionSource.ON_DEVICE_MLKIT
            ),
            dueDate = OCRField(
                value = parsed.dueDate,
                rawText = parsed.dueDate?.toString() ?: "",
                source = ExtractionSource.ON_DEVICE_MLKIT
            ),
            items = items,
            subtotalPaise = OCRField(
                value = parsed.subtotalPaise,
                rawText = parsed.subtotalPaise.toString(),
                source = ExtractionSource.ON_DEVICE_MLKIT
            ),
            discountPaise = OCRField(
                value = parsed.discountPaise,
                rawText = parsed.discountPaise.toString(),
                source = ExtractionSource.ON_DEVICE_MLKIT
            ),
            taxPaise = OCRField(
                value = parsed.taxPaise,
                rawText = parsed.taxPaise.toString(),
                source = ExtractionSource.ON_DEVICE_MLKIT
            ),
            grandTotalPaise = OCRField(
                value = parsed.grandTotalPaise,
                rawText = parsed.grandTotalPaise.toString(),
                confidence = if (parsed.grandTotalPaise > 0L) 0.92f else null,
                isUncertain = parsed.grandTotalPaise <= 0L,
                source = ExtractionSource.ON_DEVICE_MLKIT
            ),
            rawText = rawText,
            source = ExtractionSource.ON_DEVICE_MLKIT,
            modelId = engineId,
            processingDurationMs = durationMs,
            validationResult = validation
        )
    }
}

