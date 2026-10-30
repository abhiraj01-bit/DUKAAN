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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.dukaan.app.core.designsystem.components.*
import com.dukaan.app.core.designsystem.theme.*
import com.dukaan.app.core.navigation.Screen
import com.dukaan.app.core.util.CurrencyFormatter
import com.dukaan.app.core.util.DateFormatter
import com.dukaan.app.domain.model.Invoice
import com.dukaan.app.domain.repository.InvoiceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── Scan processing states ──────────────────────────────────────────────────

enum class ScanStage {
    IDLE,
    IMAGE_PROCESSING,
    API_REQUEST_IN_PROGRESS,
    RESPONSE_RECEIVED,
    PARSING_RESPONSE,
    SUCCESS,
    API_ERROR,
    INVALID_RESPONSE,
    NETWORK_ERROR,
    NO_API_KEY,
    OCR_FALLBACK,
    OCR_EMPTY
}

data class ScannerUiState(
    val stage: ScanStage = ScanStage.IDLE,
    val errorMessage: String? = null,
    val pendingInvoices: List<Invoice> = emptyList(),
    // Holds the last bitmap so Retry can re-use it
    val lastBitmap: Bitmap? = null,
    val lastContext: Context? = null
)

@HiltViewModel
class InvoiceScannerViewModel @Inject constructor(
    private val invoiceRepository: InvoiceRepository,
    private val geminiInvoiceService: GeminiInvoiceService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState = _uiState.asStateFlow()

    // Keep for backward compat with CameraScreen which reads isProcessing
    val isProcessing get() = _uiState.value.stage.let {
        it == ScanStage.IMAGE_PROCESSING ||
        it == ScanStage.API_REQUEST_IN_PROGRESS ||
        it == ScanStage.RESPONSE_RECEIVED ||
        it == ScanStage.PARSING_RESPONSE
    }

    init {
        viewModelScope.launch {
            invoiceRepository.getPendingInvoices().collect { list ->
                _uiState.update { it.copy(pendingInvoices = list) }
            }
        }
    }

    fun processCapturedBitmap(bitmap: Bitmap, context: Context, onReady: (String) -> Unit) {
        _uiState.update { it.copy(lastBitmap = bitmap, lastContext = context) }
        doScan(bitmap, context, onReady)
    }

    fun processGalleryImage(uri: Uri, context: Context, onReady: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(stage = ScanStage.IMAGE_PROCESSING, errorMessage = null) }
            val bitmap = try {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            } catch (e: Exception) {
                _uiState.update { it.copy(stage = ScanStage.NETWORK_ERROR, errorMessage = "Could not read image: ${e.message}") }
                return@launch
            }
            if (bitmap == null) {
                _uiState.update { it.copy(stage = ScanStage.INVALID_RESPONSE, errorMessage = "Could not decode selected image") }
                return@launch
            }
            _uiState.update { it.copy(lastBitmap = bitmap, lastContext = context) }
            doScan(bitmap, context, onReady)
        }
    }

    fun retry(onReady: (String) -> Unit) {
        val bitmap = _uiState.value.lastBitmap ?: return
        val context = _uiState.value.lastContext ?: return
        doScan(bitmap, context, onReady)
    }

    fun resetError() {
        _uiState.update { it.copy(stage = ScanStage.IDLE, errorMessage = null) }
    }

    private fun doScan(bitmap: Bitmap, context: Context, onReady: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(stage = ScanStage.API_REQUEST_IN_PROGRESS, errorMessage = null) }

            val result = geminiInvoiceService.processInvoiceImage(context, bitmap)

            _uiState.update { it.copy(stage = ScanStage.RESPONSE_RECEIVED) }

            when (result) {
                is ScanResult.Success -> {
                    _uiState.update { it.copy(stage = ScanStage.PARSING_RESPONSE) }
                    invoiceRepository.saveInvoice(result.invoice)
                    _uiState.update { it.copy(stage = ScanStage.SUCCESS, errorMessage = null) }
                    onReady(result.invoice.id)
                }

                is ScanResult.OcrFallback -> {
                    // Gemini failed but ML Kit got some text — save what we have, let user edit
                    invoiceRepository.saveInvoice(result.invoice)
                    _uiState.update {
                        it.copy(
                            stage = ScanStage.OCR_FALLBACK,
                            errorMessage = "AI scan unavailable. Showing on-device OCR results — please review and correct."
                        )
                    }
                    onReady(result.invoice.id)
                }

                is ScanResult.OcrEmpty -> {
                    // Nothing extracted at all — navigate to a blank review screen so user can type manually
                    val now = System.currentTimeMillis()
                    val blankInvoice = com.dukaan.app.domain.model.Invoice(
                        id = result.invoiceId,
                        invoiceNumber = "",
                        supplierName = "",
                        invoiceDate = now,
                        grandTotalPaise = 0L,
                        subtotalPaise = 0L,
                        status = com.dukaan.app.domain.model.InvoiceStatus.DRAFT,
                        imagePath = result.imagePath,
                        rawOcrText = "",
                        items = emptyList(),
                        scannedAt = now,
                        createdAt = now,
                        updatedAt = now
                    )
                    invoiceRepository.saveInvoice(blankInvoice)
                    _uiState.update {
                        it.copy(
                            stage = ScanStage.OCR_EMPTY,
                            errorMessage = "Could not extract text from this image. Please enter invoice details manually."
                        )
                    }
                    onReady(result.invoiceId)
                }

                is ScanResult.NoApiKey -> {
                    _uiState.update {
                        it.copy(
                            stage = ScanStage.NO_API_KEY,
                            errorMessage = "Gemini API key not configured. Go to Settings → AI Bill Scanner to add your key."
                        )
                    }
                }

                is ScanResult.ApiError -> {
                    _uiState.update {
                        it.copy(stage = ScanStage.API_ERROR, errorMessage = result.message)
                    }
                }

                is ScanResult.NetworkError -> {
                    _uiState.update {
                        it.copy(stage = ScanStage.NETWORK_ERROR, errorMessage = result.message)
                    }
                }

                is ScanResult.ParseError -> {
                    _uiState.update {
                        it.copy(stage = ScanStage.INVALID_RESPONSE, errorMessage = "Could not parse AI response: ${result.message}")
                    }
                }
            }
        }
    }
}

// ── Scanner Screen ──────────────────────────────────────────────────────────

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

    val isProcessing = uiState.stage in listOf(
        ScanStage.IMAGE_PROCESSING,
        ScanStage.API_REQUEST_IN_PROGRESS,
        ScanStage.RESPONSE_RECEIVED,
        ScanStage.PARSING_RESPONSE
    )

    val hasError = uiState.stage in listOf(
        ScanStage.API_ERROR,
        ScanStage.NETWORK_ERROR,
        ScanStage.INVALID_RESPONSE,
        ScanStage.NO_API_KEY
    )

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
                // Error / status banner
                if (hasError && uiState.errorMessage != null) {
                    item {
                        ScanErrorBanner(
                            message = uiState.errorMessage!!,
                            stage = uiState.stage,
                            canRetry = uiState.lastBitmap != null &&
                                uiState.stage != ScanStage.NO_API_KEY,
                            onRetry = {
                                viewModel.retry { invoiceId ->
                                    navController.navigate(Screen.ExtractedInvoicePreview.createRoute(invoiceId))
                                }
                            },
                            onDismiss = { viewModel.resetError() },
                            onGoToSettings = { navController.navigate(Screen.Settings.route) }
                        )
                    }
                }

                // OCR fallback info banner (non-blocking)
                if (uiState.stage == ScanStage.OCR_FALLBACK && uiState.errorMessage != null) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = LightOrangeSurface),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = SaffronOrange)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(uiState.errorMessage!!, style = MaterialTheme.typography.bodySmall, color = PrimaryText)
                            }
                        }
                    }
                }

                // OCR empty info banner
                if (uiState.stage == ScanStage.OCR_EMPTY && uiState.errorMessage != null) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = LightOrangeSurface),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.WarningAmber, contentDescription = null, tint = SaffronOrange)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(uiState.errorMessage!!, style = MaterialTheme.typography.bodySmall, color = PrimaryText)
                            }
                        }
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
                                        inv.supplierName.ifBlank { "Unknown Supplier" },
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

            // Processing overlay
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
                                    when (uiState.stage) {
                                        ScanStage.IMAGE_PROCESSING -> "Preparing image..."
                                        ScanStage.API_REQUEST_IN_PROGRESS -> "Sending to Gemini AI..."
                                        ScanStage.RESPONSE_RECEIVED -> "Response received..."
                                        ScanStage.PARSING_RESPONSE -> "Parsing invoice data..."
                                        else -> "Processing..."
                                    },
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryText
                                )
                                Text(
                                    "Extracting items, rates & totals",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SecondaryText
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanErrorBanner(
    message: String,
    stage: ScanStage,
    canRetry: Boolean,
    onRetry: () -> Unit,
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
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    when (stage) {
                        ScanStage.NO_API_KEY -> "API Key Required"
                        ScanStage.API_ERROR -> "Gemini API Error"
                        ScanStage.NETWORK_ERROR -> "Network Error"
                        ScanStage.INVALID_RESPONSE -> "Invalid Response"
                        else -> "Scan Failed"
                    },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = ErrorRed
                )
            }
            Text(message, style = MaterialTheme.typography.bodySmall, color = PrimaryText)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (stage == ScanStage.NO_API_KEY) {
                    Button(
                        onClick = onGoToSettings,
                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) { Text("Go to Settings", style = MaterialTheme.typography.labelMedium) }
                }
                if (canRetry) {
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = SaffronOrange),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) { Text("Retry", style = MaterialTheme.typography.labelMedium) }
                }
                TextButton(onClick = onDismiss) {
                    Text("Dismiss", style = MaterialTheme.typography.labelMedium, color = SecondaryText)
                }
            }
        }
    }
}
