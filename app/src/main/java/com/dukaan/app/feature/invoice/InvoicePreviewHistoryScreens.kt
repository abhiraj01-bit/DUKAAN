package com.dukaan.app.feature.invoice

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
import com.dukaan.app.domain.model.Invoice
import com.dukaan.app.domain.model.InvoiceStatus
import com.dukaan.app.domain.repository.InvoiceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class InvoiceHistoryViewModel @Inject constructor(
    private val invoiceRepository: InvoiceRepository
) : ViewModel() {
    private val _invoices = MutableStateFlow<List<Invoice>>(emptyList())
    val invoices = _invoices.asStateFlow()

    init {
        viewModelScope.launch {
            invoiceRepository.getAllInvoices().collect { list ->
                _invoices.value = list
            }
        }
    }
}

// =================== SCREEN 13: EXTRACTED INVOICE PREVIEW ===================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtractedInvoicePreviewScreen(
    navController: NavController,
    invoiceId: String,
    viewModel: InvoiceReviewViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(invoiceId) {
        viewModel.loadInvoice(invoiceId)
    }

    LaunchedEffect(state.isConfirmed) {
        if (state.isConfirmed) {
            navController.navigate(Screen.InvoiceHistory.route) {
                popUpTo(Screen.HomeDashboard.route)
            }
        }
    }

    var showAddItemDialog by remember { mutableStateOf(false) }
    var showEditDetailsDialog by remember { mutableStateOf(false) }
    var editSupplierName by remember { mutableStateOf("") }
    var editInvoiceNumber by remember { mutableStateOf("") }
    var newItemName by remember { mutableStateOf("") }
    var newItemQty by remember { mutableStateOf("") }
    var newItemPrice by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Review Extracted Bill",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = PrimaryText
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PrimaryText)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WarmCream)
            )
        },
        bottomBar = {
            Surface(
                color = SurfaceWhite,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    PrimaryButton(
                        text = "Confirm & Save",
                        onClick = viewModel::confirmInvoice,
                        loading = state.isLoading,
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    )
                }
            }
        },
        containerColor = WarmCream
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Bill photo preview card (Screen 13 reference visual)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EFE6))
                ) {
                    val imagePath = state.invoice?.imagePath
                    val imageFile = remember(imagePath) { imagePath?.let { File(it) } }
                    val bitmap = remember(imageFile) {
                        if (imageFile != null && imageFile.exists()) {
                            BitmapFactory.decodeFile(imageFile.absolutePath)
                        } else null
                    }

                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Scanned bill image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                                val w = size.width
                                val h = size.height
                                listOf(0.25f, 0.5f, 0.75f).forEach { fraction ->
                                    drawLine(
                                        color = Color(0xFFC7BFB0),
                                        start = Offset(20f, h * fraction),
                                        end = Offset(w - 20f, h * fraction),
                                        strokeWidth = 2f
                                    )
                                }
                            }
                            Text(
                                text = "[Captured Bill: ${if (state.supplierName.isNotBlank()) state.supplierName else "Unknown Supplier"} #${if (state.invoiceNumber.isNotBlank()) state.invoiceNumber else "—"}]",
                                style = MaterialTheme.typography.labelMedium,
                                color = SecondaryText
                            )
                        }
                    }
                }
            }

            // Supplier Name Card with Change link
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Supplier Name", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                                Text(
                                    text = state.supplierName.ifBlank { "(tap to edit)" },
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (state.supplierName.isNotBlank()) PrimaryText else SecondaryText
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val isAiProcessed = state.invoice?.rawOcrText?.trim()?.startsWith("{") == true
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isAiProcessed) ForestGreen.copy(alpha = 0.12f) else SaffronOrange.copy(alpha = 0.12f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            if (isAiProcessed) Icons.Default.AutoAwesome else Icons.Default.DocumentScanner,
                                            contentDescription = null,
                                            tint = if (isAiProcessed) ForestGreen else SaffronOrange,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isAiProcessed) "AI Scanned" else "On-Device OCR",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isAiProcessed) ForestGreen else SaffronOrange,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Change",
                                    modifier = Modifier.clickable {
                                        editSupplierName = state.supplierName
                                        editInvoiceNumber = state.invoiceNumber
                                        showEditDetailsDialog = true
                                    },
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color(0xFF1E88E5)
                                )
                            }
                        }

                        HorizontalDivider(color = BorderColor)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Invoice No.", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                                Text(
                                    text = state.invoiceNumber.ifBlank { "—" },
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Date", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                                Text(
                                    text = DateFormatter.formatDate(state.invoice?.invoiceDate ?: System.currentTimeMillis()),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }
                        }
                    }
                }
            }

            // Extracted Items Table
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(LightGreenSurface, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Item", modifier = Modifier.weight(1.8f), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = ForestGreen)
                            Text("Qty", modifier = Modifier.weight(0.6f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = ForestGreen)
                            Text("Rate", modifier = Modifier.weight(0.8f), textAlign = TextAlign.End, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = ForestGreen)
                            Text("Amount", modifier = Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = ForestGreen)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (state.items.isEmpty()) {
                            Text(
                                "No items extracted yet.",
                                modifier = Modifier.padding(16.dp),
                                color = SecondaryText,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            state.items.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(item.productName, modifier = Modifier.weight(1.8f), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = PrimaryText)
                                    Text("${item.quantity.toInt()}", modifier = Modifier.weight(0.6f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                                    Text("${item.purchasePricePaise / 100}", modifier = Modifier.weight(0.8f), textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium)
                                    Text("${item.lineTotalPaise / 100}", modifier = Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                }
                                HorizontalDivider(color = BorderColor.copy(alpha = 0.5f))
                            }
                        }

                        // + Add Item Button
                        TextButton(
                            onClick = { showAddItemDialog = true },
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("+ Add Item", color = ForestGreen, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Total Amount Display
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Total Amount",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = PrimaryText
                    )
                    Text(
                        text = CurrencyFormatter.formatPaise(state.grandTotalPaise),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 24.sp
                        ),
                        color = ForestGreen
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }

    if (showEditDetailsDialog) {
        AlertDialog(
            onDismissRequest = { showEditDetailsDialog = false },
            title = { Text("Edit Invoice Details", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editSupplierName,
                        onValueChange = { editSupplierName = it },
                        label = { Text("Supplier Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editInvoiceNumber,
                        onValueChange = { editInvoiceNumber = it },
                        label = { Text("Invoice Number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.onSupplierNameChange(editSupplierName.trim())
                        viewModel.onInvoiceNumberChange(editInvoiceNumber.trim())
                        showEditDetailsDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDetailsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddItemDialog) {
        AlertDialog(
            onDismissRequest = { showAddItemDialog = false },
            title = { Text("Add Item to Invoice", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newItemName,
                        onValueChange = { newItemName = it },
                        label = { Text("Item Name") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newItemQty,
                        onValueChange = { newItemQty = it },
                        label = { Text("Quantity") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newItemPrice,
                        onValueChange = { newItemPrice = it },
                        label = { Text("Price (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val qty = newItemQty.toDoubleOrNull() ?: 1.0
                        val price = (newItemPrice.toDoubleOrNull() ?: 0.0) * 100
                        if (newItemName.isNotBlank()) {
                            viewModel.addItem(newItemName, qty, price.toLong())
                            newItemName = ""
                            newItemQty = ""
                            newItemPrice = ""
                            showAddItemDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddItemDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// =================== SCREEN 14: INVOICE HISTORY (VAULT) ===================

@Composable
fun InvoiceHistoryScreen(
    navController: NavController,
    viewModel: InvoiceHistoryViewModel = hiltViewModel()
) {
    val invoices by viewModel.invoices.collectAsState()
    var selectedFilter by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }

    val filteredInvoices = remember(invoices, selectedFilter, searchQuery) {
        invoices.filter { inv ->
            val matchesFilter = when (selectedFilter) {
                "Purchase" -> inv.status == InvoiceStatus.CONFIRMED
                "Pending" -> inv.status != InvoiceStatus.CONFIRMED
                else -> true
            }
            val matchesSearch = searchQuery.isBlank() ||
                    inv.supplierName.contains(searchQuery, ignoreCase = true) ||
                    inv.invoiceNumber.contains(searchQuery, ignoreCase = true)
            matchesFilter && matchesSearch
        }
    }

    Scaffold(
        topBar = {
            DukaanTopBar(
                title = "Invoice History",
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.CalendarToday, contentDescription = "Filter Date", tint = SurfaceWhite)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Screen.InvoiceCamera.route) },
                containerColor = ForestGreen,
                contentColor = SurfaceWhite
            ) {
                Icon(Icons.Default.Add, contentDescription = "Scan New Invoice")
            }
        },
        containerColor = WarmCream
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Filter Tabs: All, Purchase, Pending
            Surface(color = SurfaceWhite, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("All", "Purchase", "Pending").forEach { tab ->
                        val isSelected = selectedFilter == tab
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedFilter = tab },
                            label = { Text(tab) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ForestGreen,
                                selectedLabelColor = SurfaceWhite
                            )
                        )
                    }
                }
            }

            if (filteredInvoices.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.ReceiptLong,
                            contentDescription = null,
                            tint = SecondaryText,
                            modifier = Modifier.size(56.dp)
                        )
                        Text(
                            "No invoices found",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = PrimaryText
                        )
                        Text(
                            "Tap the + button to scan your first purchase bill",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SecondaryText,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredInvoices) { inv ->
                        InvoiceHistoryItemCard(
                            supplierName = inv.supplierName.ifBlank { "Supplier" },
                            date = DateFormatter.formatDate(inv.invoiceDate),
                            amount = CurrencyFormatter.formatPaise(inv.grandTotalPaise),
                            status = if (inv.status == InvoiceStatus.CONFIRMED) "Confirmed" else "Pending",
                            statusColor = if (inv.status == InvoiceStatus.CONFIRMED) SuccessGreen else SaffronOrange,
                            imagePath = inv.imagePath,
                            onClick = {
                                navController.navigate(Screen.ExtractedInvoicePreview.createRoute(inv.id))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InvoiceHistoryItemCard(
    supplierName: String,
    date: String,
    amount: String,
    status: String,
    statusColor: Color,
    imagePath: String? = null,
    onClick: () -> Unit
) {
    val imageFile = remember(imagePath) { imagePath?.let { File(it) } }
    val bitmap = remember(imageFile) {
        if (imageFile != null && imageFile.exists()) {
            BitmapFactory.decodeFile(imageFile.absolutePath)
        } else null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Receipt Paper Thumbnail (or actual photo if captured!)
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF3EFE6)),
                    contentAlignment = Alignment.Center
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            Icons.Default.ReceiptLong,
                            contentDescription = null,
                            tint = Color(0xFF8D6E63),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = supplierName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = PrimaryText
                    )
                    Text(
                        text = date,
                        style = MaterialTheme.typography.bodySmall,
                        color = SecondaryText
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = amount,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = PrimaryText
                )
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (status == "Confirmed") LightGreenSurface else LightOrangeSurface)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = statusColor
                    )
                }
            }
        }
    }
}

