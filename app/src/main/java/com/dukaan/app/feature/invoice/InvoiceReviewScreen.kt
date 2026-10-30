package com.dukaan.app.feature.invoice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.dukaan.app.core.designsystem.components.*
import com.dukaan.app.core.designsystem.theme.*
import com.dukaan.app.core.util.CurrencyFormatter
import com.dukaan.app.core.util.DateFormatter
import com.dukaan.app.core.util.formatQuantity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceReviewScreen(
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
            navController.popBackStack()
        }
    }

    var showAddItemDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            DukaanTopBar(
                title = "Review Scanned Invoice",
                onBackClick = { navController.popBackStack() }
            )
        },
        bottomBar = {
            Surface(
                color = SurfaceWhite,
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("TOTAL AMOUNT", style = MaterialTheme.typography.labelSmall, color = SecondaryText)
                        Text(
                            CurrencyFormatter.formatPaise(state.grandTotalPaise),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = ForestGreen
                        )
                    }

                    PrimaryButton(
                        text = "Confirm & Add Stock",
                        onClick = viewModel::confirmInvoice,
                        loading = state.isLoading,
                        icon = Icons.Default.Check
                    )
                }
            }
        },
        containerColor = WarmCream
    ) { paddingValues ->
        if (state.isLoading) {
            LoadingState(modifier = Modifier.padding(paddingValues))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Card
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("Invoice Details", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = ForestGreen)

                            OutlinedTextField(
                                value = state.supplierName,
                                onValueChange = viewModel::onSupplierNameChange,
                                label = { Text("Supplier / Distributor Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )

                            OutlinedTextField(
                                value = state.invoiceNumber,
                                onValueChange = viewModel::onInvoiceNumberChange,
                                label = { Text("Bill / Invoice Number") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }

                // Items Section Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Recognized Items (${state.items.size})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Charcoal
                        )

                        TextButton(onClick = { showAddItemDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Item", color = ForestGreen)
                        }
                    }
                }

                if (state.items.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("No items parsed automatically", style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
                                Spacer(modifier = Modifier.height(10.dp))
                                SecondaryButton(text = "Add Item Manually", onClick = { showAddItemDialog = true })
                            }
                        }
                    }
                } else {
                    itemsIndexed(state.items) { index, item ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.productName, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                                    Text(
                                        "${item.quantity.formatQuantity()} pcs × ${CurrencyFormatter.formatPaise(item.purchasePricePaise)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SecondaryText
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        CurrencyFormatter.formatPaise(item.lineTotalPaise),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Charcoal
                                    )
                                    IconButton(onClick = { viewModel.removeItem(index) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove", tint = SecondaryText, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Item Dialog
    if (showAddItemDialog) {
        var nameInput by remember { mutableStateOf("") }
        var qtyInput by remember { mutableStateOf("1") }
        var priceInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddItemDialog = false },
            title = { Text("Add Invoice Item") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Product Name *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = qtyInput,
                        onValueChange = { qtyInput = it },
                        label = { Text("Quantity") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = priceInput,
                        onValueChange = { priceInput = it },
                        label = { Text("Cost Price Per Unit (?)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val qty = qtyInput.toDoubleOrNull() ?: 1.0
                        val pricePaise = CurrencyFormatter.rupeesToPaise(priceInput)
                        if (nameInput.isNotBlank()) {
                            viewModel.addItem(nameInput, qty, pricePaise)
                            showAddItemDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddItemDialog = false }) { Text("Cancel") }
            }
        )
    }
}