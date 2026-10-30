package com.dukaan.app.feature.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import com.dukaan.app.core.navigation.Screen
import com.dukaan.app.core.util.CurrencyFormatter
import com.dukaan.app.core.util.formatQuantity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(
    navController: NavController,
    productId: String,
    viewModel: ProductDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(productId) {
        viewModel.loadProduct(productId)
    }

    LaunchedEffect(state.isDeleted) {
        if (state.isDeleted) {
            navController.popBackStack()
        }
    }

    var showAdjustDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            DukaanTopBar(
                title = state.product?.name ?: "Product Details",
                onBackClick = { navController.popBackStack() },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.EditProduct.createRoute(productId)) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = SurfaceWhite)
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = SurfaceWhite)
                    }
                }
            )
        },
        containerColor = WarmCream
    ) { paddingValues ->
        if (state.isLoading) {
            LoadingState(modifier = Modifier.padding(paddingValues))
        } else if (state.product == null) {
            ErrorState(
                message = "Product not found",
                onRetry = { navController.popBackStack() },
                modifier = Modifier.padding(paddingValues)
            )
        } else {
            val product = state.product!!
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Stock Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("CURRENT STOCK", style = MaterialTheme.typography.labelSmall, color = SecondaryText)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "${product.currentStock.formatQuantity()} ${product.unit.symbol}",
                                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                                    color = when {
                                        product.isOutOfStock -> ErrorRed
                                        product.isLowStock -> WarningOrange
                                        else -> ForestGreen
                                    }
                                )
                            }
                            when {
                                product.isOutOfStock -> StatusBadge("Out of Stock", ErrorRed)
                                product.isLowStock -> StatusBadge("Low Stock Alert", WarningOrange)
                                else -> StatusBadge("In Stock", SuccessGreen)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            PrimaryButton(
                                text = "Adjust Stock",
                                onClick = { showAdjustDialog = true },
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.Tune
                            )
                            SecondaryButton(
                                text = "Movements",
                                onClick = { navController.navigate(Screen.StockMovement.createRoute(product.id)) },
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.History
                            )
                        }
                    }
                }

                // Financial Information
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Price & Margin", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = ForestGreen)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Selling Price", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                                Text(
                                    CurrencyFormatter.formatPaise(product.sellingPricePaise),
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = Charcoal
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Purchase / Cost Price", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                                Text(
                                    CurrencyFormatter.formatPaise(product.purchasePricePaise),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = SecondaryText
                                )
                            }
                        }

                        val marginPaise = product.sellingPricePaise - product.purchasePricePaise
                        val marginPercent = if (product.purchasePricePaise > 0) {
                            (marginPaise.toDouble() / product.purchasePricePaise * 100).toInt()
                        } else 0

                        HorizontalDivider(color = DividerColor)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Estimated Profit Margin", style = MaterialTheme.typography.bodyMedium, color = Charcoal)
                            Text(
                                "${CurrencyFormatter.formatPaise(marginPaise)} ($marginPercent%)",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (marginPaise >= 0) SuccessGreen else ErrorRed
                            )
                        }
                    }
                }

                // Details Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Additional Info", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = ForestGreen)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Low Stock Threshold", style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
                            Text("${product.minStockLevel} ${product.unit.symbol}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        }

                        if (!product.barcode.isNullOrBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Barcode", style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
                                Text(product.barcode ?: "-", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            }
                        }
                    }
                }
            }
        }
    }

    // Adjust Stock Dialog
    if (showAdjustDialog) {
        var quantityInput by remember { mutableStateOf("") }
        var isAddition by remember { mutableStateOf(true) }
        var reasonInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAdjustDialog = false },
            title = { Text("Adjust Stock Quantity") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = isAddition,
                            onClick = { isAddition = true },
                            label = { Text("+ Add Stock") }
                        )
                        FilterChip(
                            selected = !isAddition,
                            onClick = { isAddition = false },
                            label = { Text("- Deduct / Damage") }
                        )
                    }

                    OutlinedTextField(
                        value = quantityInput,
                        onValueChange = { quantityInput = it },
                        label = { Text("Quantity (${state.product?.unit?.symbol ?: "Unit"})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = reasonInput,
                        onValueChange = { reasonInput = it },
                        label = { Text("Reason (e.g. New stock, Broken/Expired)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val qty = quantityInput.toDoubleOrNull() ?: 0.0
                        if (qty > 0) {
                            val delta = if (isAddition) qty else -qty
                            viewModel.adjustStock(delta, reasonInput)
                            showAdjustDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showAdjustDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Confirm Delete Dialog
    if (showDeleteDialog) {
        ConfirmationDialog(
            title = "Delete Product?",
            message = "Are you sure you want to remove '${state.product?.name}'? Historical sales and bills will remain preserved.",
            confirmText = "Delete",
            isDestructive = true,
            onConfirm = { viewModel.deleteProduct() },
            onDismiss = { showDeleteDialog = false }
        )
    }
}