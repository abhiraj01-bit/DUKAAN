package com.dukaan.app.core.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.dukaan.app.core.ai.contracts.*
import com.dukaan.app.domain.model.Invoice
import com.dukaan.app.domain.model.InvoiceItem
import com.dukaan.app.domain.model.InvoiceStatus
import com.dukaan.app.domain.repository.InvoiceRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "InvoiceOcrPipeline"

/**
 * Production-grade On-Device AI OCR & VLM pipeline orchestrator.
 *
 * Directs raw invoice captures through:
 * 1. Image preprocessing (scan mode tuning for Handwritten, Printed, Thermal Receipt).
 * 2. Device memory and capability checks.
 * 3. Engine routing: Gemma 3n VLM if downloaded and compatible; bundled ML Kit OCR otherwise.
 * 4. Strict arithmetic validation.
 * 5. Room database draft storage.
 */
@Singleton
class InvoiceOcrPipeline @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageProcessor: InvoiceImageProcessor,
    private val capabilityManager: DeviceCapabilityManager,
    private val vlmEngine: LiteRTVlmEngine,
    private val mlKitEngine: MlKitFallbackEngine,
    private val invoiceRepository: InvoiceRepository
) {

    private val _processingStatus = MutableStateFlow<OCRProcessingStatus>(OCRProcessingStatus.Idle)
    val processingStatus: StateFlow<OCRProcessingStatus> = _processingStatus.asStateFlow()

    suspend fun processInvoice(
        bitmap: Bitmap,
        invoiceId: String,
        scanMode: InvoiceScanMode = InvoiceScanMode.AUTO,
        onStatusUpdate: ((OCRProcessingStatus) -> Unit)? = null
    ): Result<OCRResponse> = withContext(Dispatchers.IO) {
        fun updateStatus(status: OCRProcessingStatus) {
            _processingStatus.value = status
            onStatusUpdate?.invoke(status)
        }

        try {
            // Step 1: Preprocessing
            updateStatus(OCRProcessingStatus.Preprocessing(scanMode))
            val preprocessed = imageProcessor.preprocess(bitmap, scanMode)

            // Step 2: Cache image to disk
            val imageFile = File(context.cacheDir, "invoice_$invoiceId.jpg")
            FileOutputStream(imageFile).use { out ->
                preprocessed.bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            val request = OCRRequest(
                bitmap = preprocessed.bitmap,
                scanMode = preprocessed.appliedMode,
                invoiceId = invoiceId,
                imagePath = imageFile.absolutePath
            )

            // Step 3: Inspect memory & choose engine
            updateStatus(OCRProcessingStatus.InspectingDeviceMemory)
            val useVlm = vlmEngine.isReady()

            val response = if (useVlm) {
                updateStatus(OCRProcessingStatus.RunningVlmInference("Gemma 3n E2B"))
                vlmEngine.processInvoice(request)
            } else {
                updateStatus(OCRProcessingStatus.RunningMlKitFallback("Offline ML Kit (Bundled)"))
                mlKitEngine.processInvoice(request)
            }

            // Step 4: Validate arithmetic
            updateStatus(OCRProcessingStatus.ValidatingArithmetic)

            // Step 5: Save draft to Room database
            val domainInvoice = Invoice(
                id = invoiceId,
                invoiceNumber = response.invoiceNumber.effectiveValue ?: "",
                supplierName = response.supplierName.effectiveValue ?: "",
                invoiceDate = response.invoiceDate.effectiveValue ?: System.currentTimeMillis(),
                dueDate = response.dueDate.effectiveValue,
                subtotalPaise = response.subtotalPaise.effectiveValue ?: 0L,
                discountPaise = response.discountPaise.effectiveValue ?: 0L,
                taxPaise = response.taxPaise.effectiveValue ?: 0L,
                grandTotalPaise = response.grandTotalPaise.effectiveValue ?: 0L,
                status = InvoiceStatus.DRAFT,
                imagePath = imageFile.absolutePath,
                rawOcrText = response.rawText,
                items = response.items.map { prod ->
                    InvoiceItem(
                        id = prod.id,
                        invoiceId = invoiceId,
                        productName = prod.productName.effectiveValue ?: "Item",
                        quantity = prod.quantity.effectiveValue ?: 1.0,
                        unit = prod.unit.effectiveValue ?: "pcs",
                        purchasePricePaise = prod.unitPricePaise.effectiveValue ?: 0L,
                        discountPaise = prod.discountPaise.effectiveValue ?: 0L,
                        taxPaise = 0L,
                        lineTotalPaise = prod.lineTotalPaise.effectiveValue ?: 0L
                    )
                }
            )

            invoiceRepository.saveInvoice(domainInvoice)

            val draft = InvoiceDraft(
                id = invoiceId,
                supplierName = domainInvoice.supplierName,
                supplierGstin = response.supplierGstin.effectiveValue,
                supplierPhone = response.supplierPhone.effectiveValue,
                invoiceNumber = domainInvoice.invoiceNumber,
                invoiceDate = domainInvoice.invoiceDate,
                dueDate = domainInvoice.dueDate,
                items = response.items,
                subtotalPaise = domainInvoice.subtotalPaise,
                discountPaise = domainInvoice.discountPaise,
                taxPaise = domainInvoice.taxPaise,
                grandTotalPaise = domainInvoice.grandTotalPaise,
                imagePath = imageFile.absolutePath,
                rawOcrText = response.rawText,
                extractionSource = response.source,
                validationResult = response.validationResult
            )

            updateStatus(OCRProcessingStatus.ReadyForReview(draft))
            Result.success(response)
        } catch (t: Throwable) {
            Log.e(TAG, "Invoice OCR pipeline failed", t)
            val error = OCRProcessingError.Unknown(t.localizedMessage ?: "OCR pipeline failed")
            updateStatus(OCRProcessingStatus.Error(error))
            Result.failure(t)
        }
    }
}

