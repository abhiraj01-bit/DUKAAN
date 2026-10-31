package com.dukaan.app.feature.invoice

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.dukaan.app.core.designsystem.components.*
import com.dukaan.app.core.designsystem.theme.*
import com.dukaan.app.core.navigation.Screen
import com.dukaan.app.core.util.CurrencyFormatter
import com.dukaan.app.core.util.DateFormatter
import com.dukaan.app.core.util.UuidGenerator
import com.dukaan.app.domain.model.Invoice
import com.dukaan.app.domain.repository.InvoiceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── State Model (Phase 11) ──────────────────────────────────────────────────

sealed interface InvoiceScannerState {
    object Idle : InvoiceScannerState
    data class ImageSelected(val uri: String) : InvoiceScannerState
    object ProcessingImage : InvoiceScannerState
    data class SendingToGemini(val modelName: String, val attemptNumber: Int = 1) : InvoiceScannerState
    data class Retrying(val modelName: String, val retryNumber: Int) : InvoiceScannerState
    data class SwitchingModel(val previousModel: String, val newModel: String) : InvoiceScannerState
    object ExtractingData : InvoiceScannerState
    data class Success(val invoice: Invoice, val modelUsed: String, val extractionSource: com.dukaan.app.core.ocr.ExtractionSource = com.dukaan.app.core.ocr.ExtractionSource.HYBRID) : InvoiceScannerState
    data class Error(
        val error: GeminiError,
        val canRetry: Boolean,
        val canUseOfflineOcr: Boolean,
        val rawOcrText: String? = null,
        val imagePath: String? = null
    ) : InvoiceScannerState
}

data class ScannerUiState(
    val scannerState: InvoiceScannerState = InvoiceScannerState.Idle,
    val pendingInvoices: List<Invoice> = emptyList(),
    val lastBitmap: Bitmap? = null,
    val lastContext: Context? = null,
    val lastInvoiceId: String = ""
)

@HiltViewModel
class InvoiceScannerViewModel @Inject constructor(
    private val invoiceRepository: InvoiceRepository,
    private val geminiInvoiceService: GeminiInvoiceService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState = _uiState.asStateFlow()

    private var scanJob: Job? = null

    val isProcessing: Boolean
        get() = when (_uiState.value.scannerState) {
            is InvoiceScannerState.ProcessingImage,
            is InvoiceScannerState.SendingToGemini,
            is InvoiceScannerState.Retrying,
            is InvoiceScannerState.SwitchingModel,
            is InvoiceScannerState.ExtractingData -> true
            else -> false
        }

    init {
        viewModelScope.launch {
            invoiceRepository.getPendingInvoices().collect { list ->
                _uiState.update { it.copy(pendingInvoices = list) }
            }
        }
    }

    /**
     * Entry point for camera captures. Cancels any ongoing scan to prevent duplicate calls.
     */
    fun processCapturedBitmap(bitmap: Bitmap, context: Context, onReady: (String) -> Unit) {
        val invoiceId = UuidGenerator.generate()
        _uiState.update {
            it.copy(
                lastBitmap = bitmap,
                lastContext = context,
                lastInvoiceId = invoiceId
            )
        }
        startScan(bitmap, context, invoiceId, onReady)
    }

    /**
     * Entry point for gallery images. Safely decodes uri with bounds checking.
     */
    fun processGalleryImage(uri: Uri, context: Context, onReady: (String) -> Unit) {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _uiState.update { it.copy(scannerState = InvoiceScannerState.ProcessingImage) }
            val invoiceId = UuidGenerator.generate()

            val bitmap = try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } catch (oom: OutOfMemoryError) {
                _uiState.update {
                    it.copy(
                        scannerState = InvoiceScannerState.Error(
                            error = GeminiError.ImageTooLarge(),
                            canRetry = false,
                            canUseOfflineOcr = false
                        )
                    )
                }
                return@launch
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        scannerState = InvoiceScannerState.Error(
                            error = GeminiError.InvalidImage(e.localizedMessage ?: "Failed to open image"),
                            canRetry = false,
                            canUseOfflineOcr = false
                        )
                    )
                }
                return@launch
            }

            if (bitmap == null) {
                _uiState.update {
                    it.copy(
                        scannerState = InvoiceScannerState.Error(
                            error = GeminiError.InvalidImage("Could not decode selected image file"),
                            canRetry = false,
                            canUseOfflineOcr = false
                        )
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    lastBitmap = bitmap,
                    lastContext = context,
                    lastInvoiceId = invoiceId
                )
            }
            startScan(bitmap, context, invoiceId, onReady)
        }
    }

    /**
     * Retries scanning the last captured invoice image with Gemini.
     */
    fun retry(onReady: (String) -> Unit) {
        val bitmap = _uiState.value.lastBitmap ?: return
        val context = _uiState.value.lastContext ?: return
        val invoiceId = _uiState.value.lastInvoiceId.ifBlank { UuidGenerator.generate() }
        startScan(bitmap, context, invoiceId, onReady)
    }

    /**
     * Uses local on-device OCR results when Gemini fails or is offline.
     */
    fun useOfflineOcr(onReady: (String) -> Unit) {
        val errorState = _uiState.value.scannerState as? InvoiceScannerState.Error ?: return
        val ocrText = errorState.rawOcrText ?: ""
        val imagePath = errorState.imagePath ?: ""
        val invoiceId = _uiState.value.lastInvoiceId.ifBlank { UuidGenerator.generate() }

        viewModelScope.launch {
            val invoice = geminiInvoiceService.createInvoiceFromOcr(ocrText, invoiceId, imagePath)
            invoiceRepository.saveInvoice(invoice)
            _uiState.update { it.copy(scannerState = InvoiceScannerState.Idle) }
            onReady(invoice.id)
        }
    }

    /**
     * Creates a blank invoice for the user to manually enter items.
     */
    fun enterManually(onReady: (String) -> Unit) {
        val errorState = _uiState.value.scannerState as? InvoiceScannerState.Error
        val imagePath = errorState?.imagePath ?: ""
        val invoiceId = _uiState.value.lastInvoiceId.ifBlank { UuidGenerator.generate() }

        viewModelScope.launch {
            val blankInvoice = geminiInvoiceService.createBlankManualInvoice(invoiceId, imagePath)
            invoiceRepository.saveInvoice(blankInvoice)
            _uiState.update { it.copy(scannerState = InvoiceScannerState.Idle) }
            onReady(invoiceId)
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        _uiState.update { it.copy(scannerState = InvoiceScannerState.Idle) }
    }

    fun resetError() {
        _uiState.update { it.copy(scannerState = InvoiceScannerState.Idle) }
    }

    private fun startScan(
        bitmap: Bitmap,
        context: Context,
        invoiceId: String,
        onReady: (String) -> Unit
    ) {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _uiState.update { it.copy(scannerState = InvoiceScannerState.ProcessingImage) }

            try {
                val result = geminiInvoiceService.processInvoiceImage(
                    context = context,
                    bitmap = bitmap,
                    invoiceId = invoiceId,
                    onStateUpdate = { state ->
                        _uiState.update { it.copy(scannerState = state) }
                    }
                )

                when (result) {
                    is ScanResult.Success -> {
                        invoiceRepository.saveInvoice(result.invoice)
                        _uiState.update {
                            it.copy(scannerState = InvoiceScannerState.Success(result.invoice, result.modelUsed, result.extractionSource))
                        }
                        onReady(result.invoice.id)
                    }

                    is ScanResult.Failure -> {
                        _uiState.update {
                            it.copy(
                                scannerState = InvoiceScannerState.Error(
                                    error = result.error,
                                    canRetry = result.error.canRetry,
                                    canUseOfflineOcr = result.rawOcrText.isNotBlank(),
                                    rawOcrText = result.rawOcrText,
                                    imagePath = result.imagePath
                                )
                            )
                        }
                    }

                    is ScanResult.LocalOcrSuccess -> {
                        invoiceRepository.saveInvoice(result.invoice)
                        _uiState.update {
                            it.copy(scannerState = InvoiceScannerState.Success(result.invoice, "Offline OCR", result.extractionSource))
                        }
                        onReady(result.invoice.id)
                    }
                }
            } catch (ce: CancellationException) {
                // Respect coroutine cancellation
                _uiState.update { it.copy(scannerState = InvoiceScannerState.Idle) }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        scannerState = InvoiceScannerState.Error(
                            error = GeminiError.Unknown(t.localizedMessage ?: "Unexpected error"),
                            canRetry = true,
                            canUseOfflineOcr = false
                        )
                    )
                }
            }
        }
    }
}

// ── Scanner UI ──────────────────────────────────────────────────────────────

@Composable
fun InvoiceScannerScreen(
    navController: NavController,
    viewModel: InvoiceScannerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.processGalleryImage(uri, context) { invoiceId ->
                navController.navigate(Screen.ExtractedInvoicePreview.createRoute(invoiceId))
            }
        }
    }

    val isProcessing = viewModel.isProcessing
    val errorState = uiState.scannerState as? InvoiceScannerState.Error

    Scaffold(
        topBar = { DukaanTopBar(title = "Invoice Scanner") },
        containerColor = WarmCream
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Error card with complete options (Phase 12)
                if (errorState != null) {
                    item {
                        ScanErrorCard(
                            error = errorState.error,
                            canRetry = errorState.canRetry,
                            canUseOfflineOcr = errorState.canUseOfflineOcr,
                            onRetry = {
                                viewModel.retry { invoiceId ->
                                    navController.navigate(Screen.ExtractedInvoicePreview.createRoute(invoiceId))
                                }
                            },
                            onUseOfflineOcr = {
                                viewModel.useOfflineOcr { invoiceId ->
                                    navController.navigate(Screen.ExtractedInvoicePreview.createRoute(invoiceId))
                                }
                            },
                            onEnterManually = {
                                viewModel.enterManually { invoiceId ->
                                    navController.navigate(Screen.ExtractedInvoicePreview.createRoute(invoiceId))
                                }
                            },
                            onDismiss = { viewModel.resetError() },
                            onGoToSettings = { navController.navigate(Screen.Settings.route) }
                        )
                    }
                }

                // Main scan card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                Icons.Default.DocumentScanner,
                                contentDescription = null,
                                tint = ForestGreen,
                                modifier = Modifier.size(56.dp)
                            )
                            Text(
                                "Scan Purchase Invoice",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = PrimaryText
                            )
                            Text(
                                "Take a picture of a handwritten or printed bill to automatically extract items and update inventory",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SecondaryText,
                                textAlign = TextAlign.Center
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                PrimaryButton(
                                    text = "Camera",
                                    onClick = { navController.navigate(Screen.InvoiceCamera.route) },
                                    modifier = Modifier.weight(1f)
                                )
                                SecondaryButton(
                                    text = "Gallery",
                                    onClick = { galleryLauncher.launch("image/*") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // Pending invoices list
                if (uiState.pendingInvoices.isNotEmpty()) {
                    item {
                        Text(
                            "Pending Invoices",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = PrimaryText
                        )
                    }
                    items(uiState.pendingInvoices) { inv ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { navController.navigate(Screen.ExtractedInvoicePreview.createRoute(inv.id)) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        inv.supplierName.ifBlank { "Unassigned Supplier" },
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryText
                                    )
                                    Text(
                                        DateFormatter.formatDate(inv.scannedAt),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SecondaryText
                                    )
                                }
                                Text(
                                    CurrencyFormatter.formatPaise(inv.grandTotalPaise),
                                    fontWeight = FontWeight.Bold,
                                    color = ForestGreen
                                )
                            }
                        }
                    }
                }
            }

            // Processing overlay with informative status messages
            if (isProcessing) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black.copy(alpha = 0.55f)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                            elevation = CardDefaults.cardElevation(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                CircularProgressIndicator(color = ForestGreen, strokeWidth = 3.dp)
                                Text(
                                    text = when (val state = uiState.scannerState) {
                                        is InvoiceScannerState.ProcessingImage -> "Reading your invoice..."
                                        is InvoiceScannerState.SendingToGemini -> "Connecting to AI (${state.modelName})..."
                                        is InvoiceScannerState.Retrying -> "Retrying connection (attempt ${state.retryNumber})..."
                                        is InvoiceScannerState.SwitchingModel -> "Trying another available model..."
                                        is InvoiceScannerState.ExtractingData -> "Extracting product details..."
                                        else -> "Almost done..."
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = PrimaryText,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    "Extracting items, quantities & prices",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SecondaryText
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                TextButton(onClick = viewModel::cancelScan) {
                                    Text("Cancel", color = SecondaryText)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScanErrorCard(
    error: GeminiError,
    canRetry: Boolean,
    canUseOfflineOcr: Boolean,
    onRetry: () -> Unit,
    onUseOfflineOcr: () -> Unit,
    onEnterManually: () -> Unit,
    onDismiss: () -> Unit,
    onGoToSettings: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = LightRedSurface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = when (error) {
                        is GeminiError.NoApiKey -> "API Key Required"
                        is GeminiError.InvalidApiKey -> "Authentication Failed"
                        is GeminiError.PermissionDenied -> "Permission Denied"
                        is GeminiError.ModelNotFound -> "Model Unavailable"
                        is GeminiError.QuotaExceeded -> "Quota Limit Reached"
                        is GeminiError.RateLimited -> "Rate Limit"
                        is GeminiError.ServerError -> "AI Server Unavailable"
                        is GeminiError.NetworkError -> "Network Disconnected"
                        is GeminiError.Timeout -> "Request Timeout"
                        is GeminiError.ContentBlocked -> "Content Flagged"
                        else -> "Invoice Scan Incomplete"
                    },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = ErrorRed
                )
            }

            Text(
                text = error.userMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = PrimaryText
            )

            // Action Buttons (Phase 12)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (error is GeminiError.NoApiKey || error is GeminiError.InvalidApiKey || error is GeminiError.ModelNotFound) {
                    Button(
                        onClick = onGoToSettings,
                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Configure Settings", style = MaterialTheme.typography.labelMedium)
                    }
                }

                if (canRetry) {
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Try Again", style = MaterialTheme.typography.labelMedium)
                    }
                }

                if (canUseOfflineOcr) {
                    OutlinedButton(
                        onClick = onUseOfflineOcr,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Use Offline OCR", style = MaterialTheme.typography.labelMedium, color = ForestGreen)
                    }
                }

                OutlinedButton(
                    onClick = onEnterManually,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Manual Entry", style = MaterialTheme.typography.labelMedium, color = PrimaryText)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text("Dismiss", color = SecondaryText)
                }
            }
        }
    }
}
