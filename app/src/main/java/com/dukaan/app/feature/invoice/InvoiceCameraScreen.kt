package com.dukaan.app.feature.invoice

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.dukaan.app.core.ai.contracts.InvoiceScanMode
import com.dukaan.app.core.designsystem.theme.ForestGreen
import com.dukaan.app.core.designsystem.theme.PrimaryText
import com.dukaan.app.core.designsystem.theme.SecondaryText
import com.dukaan.app.core.designsystem.theme.SurfaceWhite
import com.dukaan.app.core.navigation.Screen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

// =================== SCREEN 12: INVOICE SCANNER (CAMERA) ===================

@Composable
fun InvoiceCameraScreen(
    navController: NavController,
    viewModel: InvoiceScannerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        CameraCaptureContent(navController = navController, viewModel = viewModel)
    } else {
        Scaffold { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Camera permission is needed to scan invoices", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Grant Permission")
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraCaptureContent(
    navController: NavController,
    viewModel: InvoiceScannerViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()
    val isProcessing = viewModel.isProcessing
    val coroutineScope = rememberCoroutineScope()

    var flashEnabled by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var isCapturingPhoto by remember { mutableStateOf(false) }

    // Tap to focus state
    var focusPoint by remember { mutableStateOf<Offset?>(null) }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.processGalleryImage(uri, context, uiState.selectedScanMode) { invoiceId ->
                navController.navigate(Screen.ExtractedInvoicePreview.createRoute(invoiceId)) {
                    popUpTo(Screen.InvoiceCamera.route) { inclusive = true }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    focusPoint = offset
                    val pView = previewViewRef
                    if (pView != null && cameraControl != null) {
                        val factory = pView.meteringPointFactory
                        val point = factory.createPoint(offset.x, offset.y)
                        val action = FocusMeteringAction.Builder(point).build()
                        cameraControl?.startFocusAndMetering(action)
                    }
                    coroutineScope.launch {
                        delay(1200)
                        if (focusPoint == offset) {
                            focusPoint = null
                        }
                    }
                }
            }
    ) {
        // Camera Viewfinder Preview
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                previewViewRef = previewView
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val cameraSelector = if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                            CameraSelector.DEFAULT_BACK_CAMERA
                        } else if (cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        } else {
                            null
                        }

                        if (cameraSelector != null) {
                            cameraProvider.unbindAll()
                            val camera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageCapture
                            )
                            cameraControl = camera.cameraControl
                        }
                    } catch (t: Throwable) {
                        android.util.Log.e("InvoiceCamera", "Failed to bind camera", t)
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            }
        )

        // Targeting frame overlay with clear center cutout and white corner reticles
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 120.dp),
            contentAlignment = Alignment.Center
        ) {
            // White corner brackets reticle (with coordinates bug FIXED at line 198)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val cornerLength = 48.dp.toPx()
                val strokeW = 4.dp.toPx()
                val cornerColor = Color.White

                // Top-Left Bracket
                drawLine(cornerColor, Offset(0f, 0f), Offset(cornerLength, 0f), strokeW)
                drawLine(cornerColor, Offset(0f, 0f), Offset(0f, cornerLength), strokeW)

                // Top-Right Bracket
                drawLine(cornerColor, Offset(w, 0f), Offset(w - cornerLength, 0f), strokeW)
                drawLine(cornerColor, Offset(w, 0f), Offset(w, cornerLength), strokeW)

                // Bottom-Left Bracket
                drawLine(cornerColor, Offset(0f, h), Offset(cornerLength, h), strokeW)
                drawLine(cornerColor, Offset(0f, h), Offset(0f, h - cornerLength), strokeW)

                // Bottom-Right Bracket (FIXED: Offset(w - cornerLength, h) instead of 0f)
                drawLine(cornerColor, Offset(w, h), Offset(w - cornerLength, h), strokeW)
                drawLine(cornerColor, Offset(w, h), Offset(w, h - cornerLength), strokeW)
            }
        }

        // Tap-to-focus ring indicator
        focusPoint?.let { pos ->
            Box(
                modifier = Modifier
                    .offset(
                        x = (pos.x / LocalContext.current.resources.displayMetrics.density).dp - 28.dp,
                        y = (pos.y / LocalContext.current.resources.displayMetrics.density).dp - 28.dp
                    )
                    .size(56.dp)
                    .border(2.dp, Color(0xFFF3A342), RoundedCornerShape(8.dp))
            )
        }

        // Top Control Bar: Back, AI Mode Badge, and Flash
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = SurfaceWhite)
            }

            // On-Device AI Readiness Pill
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.55f),
                border = androidx.compose.foundation.BorderStroke(1.dp, ForestGreen.copy(alpha = 0.7f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(ForestGreen)
                    )
                    Text(
                        "On-Device AI",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SurfaceWhite
                    )
                }
            }

            IconButton(
                onClick = {
                    flashEnabled = !flashEnabled
                    cameraControl?.enableTorch(flashEnabled)
                },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(
                    if (flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = "Flash",
                    tint = if (flashEnabled) Color(0xFFF3A342) else SurfaceWhite
                )
            }
        }

        // Bottom Controls: Scan Mode Pills + Shutter Row
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Scan Mode Selector Pills (Auto, Handwritten, Printed, Thermal Receipt)
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InvoiceScanMode.entries.forEach { mode ->
                    val isSelected = uiState.selectedScanMode == mode
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) ForestGreen else Color.Black.copy(alpha = 0.55f),
                        border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                        modifier = Modifier.clickable { viewModel.setScanMode(mode) }
                    ) {
                        Text(
                            text = mode.displayName,
                            color = SurfaceWhite,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Capture Controls Row: Gallery Button (Left) + Big Shutter (Center) + Spacer (Right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gallery shortcut button
                IconButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                ) {
                    Icon(
                        Icons.Default.PhotoLibrary,
                        contentDescription = "Gallery",
                        tint = SurfaceWhite,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Primary Shutter Button with Debounce Protection
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .border(4.dp, Color.White, CircleShape)
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(enabled = !isProcessing && !isCapturingPhoto) {
                            isCapturingPhoto = true
                            imageCapture.takePicture(
                                cameraExecutor,
                                object : ImageCapture.OnImageCapturedCallback() {
                                    override fun onCaptureSuccess(imageProxy: ImageProxy) {
                                        try {
                                            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                                            val buffer = imageProxy.planes[0].buffer
                                            val bytes = ByteArray(buffer.remaining())
                                            buffer.get(bytes)
                                            imageProxy.close()

                                            var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                            if (bitmap != null && rotationDegrees != 0) {
                                                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                                                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                                                if (rotated != bitmap) {
                                                    bitmap.recycle()
                                                    bitmap = rotated
                                                }
                                            }

                                            if (bitmap != null) {
                                                ContextCompat.getMainExecutor(context).execute {
                                                    isCapturingPhoto = false
                                                    viewModel.processCapturedBitmap(bitmap, context, uiState.selectedScanMode) { invoiceId ->
                                                        navController.navigate(Screen.ExtractedInvoicePreview.createRoute(invoiceId)) {
                                                            popUpTo(Screen.InvoiceCamera.route) { inclusive = true }
                                                        }
                                                    }
                                                }
                                            } else {
                                                ContextCompat.getMainExecutor(context).execute {
                                                    isCapturingPhoto = false
                                                }
                                            }
                                        } catch (t: Throwable) {
                                            try { imageProxy.close() } catch (_: Throwable) {}
                                            android.util.Log.e("InvoiceCamera", "Error capturing invoice picture", t)
                                            ContextCompat.getMainExecutor(context).execute {
                                                isCapturingPhoto = false
                                            }
                                        }
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        android.util.Log.e("InvoiceCamera", "ImageCaptureException", exception)
                                        ContextCompat.getMainExecutor(context).execute {
                                            isCapturingPhoto = false
                                        }
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .clip(CircleShape)
                            .background(ForestGreen)
                    )
                }

                // Balance spacer on right for symmetrical layout
                Box(modifier = Modifier.size(52.dp))
            }
        }

        // Processing Overlay with detailed real-time state
        if (isProcessing) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black.copy(alpha = 0.7f)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(color = ForestGreen, strokeWidth = 3.dp)
                            Text(
                                text = when (val state = uiState.scannerState) {
                                    is InvoiceScannerState.ProcessingImage -> "Reading your bill..."
                                    is InvoiceScannerState.SendingToGemini -> "AI Model Active (${state.modelName})..."
                                    is InvoiceScannerState.Retrying -> "Retrying connection (attempt ${state.retryNumber})..."
                                    is InvoiceScannerState.SwitchingModel -> "Optimizing AI model..."
                                    is InvoiceScannerState.ExtractingData -> "Extracting items & totals..."
                                    else -> "Scanning Bill with AI..."
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = PrimaryText
                            )
                            Text(
                                "Extracting items, quantities & prices",
                                fontSize = 14.sp,
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

        // Error Dialog when Scanning Fails (NEVER silences errors)
        val errorState = uiState.scannerState as? InvoiceScannerState.Error
        if (errorState != null) {
            AlertDialog(
                onDismissRequest = { viewModel.resetError() },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Invoice Scan Incomplete", fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(errorState.error.userMessage, style = MaterialTheme.typography.bodyMedium)
                        if (errorState.canUseOfflineOcr) {
                            Text(
                                "On-device OCR was able to read text from this invoice. You can proceed with offline OCR and verify the items.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SecondaryText
                            )
                        }
                    }
                },
                confirmButton = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (errorState.error is GeminiError.NoApiKey || errorState.error is GeminiError.InvalidApiKey || errorState.error is GeminiError.ModelNotFound) {
                            Button(
                                onClick = {
                                    viewModel.resetError()
                                    navController.navigate(Screen.Settings.route)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Configure Settings")
                            }
                        }

                        if (errorState.canRetry) {
                            Button(
                                onClick = {
                                    viewModel.retry { invoiceId ->
                                        navController.navigate(Screen.ExtractedInvoicePreview.createRoute(invoiceId)) {
                                            popUpTo(Screen.InvoiceCamera.route) { inclusive = true }
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Try Again")
                            }
                        }

                        if (errorState.canUseOfflineOcr) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.useOfflineOcr { invoiceId ->
                                        navController.navigate(Screen.ExtractedInvoicePreview.createRoute(invoiceId)) {
                                            popUpTo(Screen.InvoiceCamera.route) { inclusive = true }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Use Offline OCR", color = ForestGreen)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.enterManually { invoiceId ->
                                    navController.navigate(Screen.ExtractedInvoicePreview.createRoute(invoiceId)) {
                                        popUpTo(Screen.InvoiceCamera.route) { inclusive = true }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Enter Manually", color = PrimaryText)
                        }

                        TextButton(
                            onClick = { viewModel.resetError() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Dismiss", color = SecondaryText)
                        }
                    }
                }
            )
        }
    }
}
