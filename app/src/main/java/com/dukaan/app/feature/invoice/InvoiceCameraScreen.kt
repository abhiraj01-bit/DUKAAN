package com.dukaan.app.feature.invoice

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.dukaan.app.core.designsystem.theme.ForestGreen
import com.dukaan.app.core.designsystem.theme.PrimaryText
import com.dukaan.app.core.designsystem.theme.SecondaryText
import com.dukaan.app.core.designsystem.theme.SurfaceWhite
import com.dukaan.app.core.navigation.Screen
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
    val isProcessing = uiState.stage in listOf(
        ScanStage.IMAGE_PROCESSING,
        ScanStage.API_REQUEST_IN_PROGRESS,
        ScanStage.RESPONSE_RECEIVED,
        ScanStage.PARSING_RESPONSE
    )

    var selectedMode by remember { mutableStateOf("Photo") }
    var flashEnabled by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }

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
            viewModel.processGalleryImage(uri, context) { invoiceId ->
                navController.navigate(Screen.ExtractedInvoicePreview.createRoute(invoiceId)) {
                    popUpTo(Screen.InvoiceCamera.route) { inclusive = true }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Camera Viewfinder Preview
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
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

        // Targeting frame overlay (Screen 12 reference visual)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 110.dp),
            contentAlignment = Alignment.Center
        ) {
            // White corner brackets reticle
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val cornerLength = 48.dp.toPx()
                val strokeW = 4.dp.toPx()
                val cornerColor = Color.White

                // Top-Left
                drawLine(cornerColor, Offset(0f, 0f), Offset(cornerLength, 0f), strokeW)
                drawLine(cornerColor, Offset(0f, 0f), Offset(0f, cornerLength), strokeW)

                // Top-Right
                drawLine(cornerColor, Offset(w, 0f), Offset(w - cornerLength, 0f), strokeW)
                drawLine(cornerColor, Offset(w, 0f), Offset(w, cornerLength), strokeW)

                // Bottom-Left
                drawLine(cornerColor, Offset(0f, h), Offset(cornerLength, h), strokeW)
                drawLine(cornerColor, Offset(0f, h), Offset(0f, h - cornerLength), strokeW)

                // Bottom-Right
                drawLine(cornerColor, Offset(w, h), Offset(w - cornerLength, h), strokeW)
                drawLine(cornerColor, Offset(w, h), Offset(w, h - cornerLength), strokeW)
            }
        }

        // Top Control Bar: Back & Flash
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
                    .background(Color.Black.copy(alpha = 0.4f))
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = SurfaceWhite)
            }

            IconButton(
                onClick = {
                    flashEnabled = !flashEnabled
                    cameraControl?.enableTorch(flashEnabled)
                },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f))
            ) {
                Icon(
                    if (flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = "Flash",
                    tint = if (flashEnabled) Color(0xFFF3A342) else SurfaceWhite
                )
            }
        }

        // Bottom Controls: Shutter Button + Mode Tabs
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Big Circular Shutter Button
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .border(4.dp, Color.White, CircleShape)
                    .padding(5.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(enabled = !isProcessing) {
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

                                        var bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
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
                                                viewModel.processCapturedBitmap(bitmap, context) { invoiceId ->
                                                    navController.navigate(Screen.ExtractedInvoicePreview.createRoute(invoiceId)) {
                                                        popUpTo(Screen.InvoiceCamera.route) { inclusive = true }
                                                    }
                                                }
                                            }
                                        }
                                    } catch (t: Throwable) {
                                        try { imageProxy.close() } catch (_: Throwable) {}
                                        android.util.Log.e("InvoiceCamera", "Error capturing invoice picture", t)
                                    }
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    android.util.Log.e("InvoiceCamera", "ImageCaptureException", exception)
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

            // Photo / Gallery Pill Switcher (Screen 12 reference visual)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selectedMode == "Photo") Color.White else Color.Transparent)
                        .clickable { selectedMode = "Photo" }
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                ) {
                    Text(
                        "Photo",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (selectedMode == "Photo") ForestGreen else SurfaceWhite
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selectedMode == "Gallery") Color.White else Color.Transparent)
                        .clickable {
                            selectedMode = "Gallery"
                            galleryLauncher.launch("image/*")
                        }
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                ) {
                    Text(
                        "Gallery",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (selectedMode == "Gallery") ForestGreen else SurfaceWhite
                    )
                }
            }
        }

        // Processing Overlay with Gemini AI indicator
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
                                "Scanning Bill with AI...",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = PrimaryText
                            )
                            Text(
                                "Extracting items, quantities & prices",
                                fontSize = 14.sp,
                                color = SecondaryText
                            )
                        }
                    }
                }
            }
        }
    }
}
