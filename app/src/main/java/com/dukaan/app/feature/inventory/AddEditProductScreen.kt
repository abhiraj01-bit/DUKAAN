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
import com.dukaan.app.domain.model.StockUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditProductScreen(
    navController: NavController,
    productId: String? = null,
    viewModel: AddEditProductViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(productId) {
        viewModel.initProduct(productId)
    }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {
            navController.popBackStack()
        }
    }

    var expandedCategoryDropdown by remember { mutableStateOf(false) }
    var expandedUnitDropdown by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            DukaanTopBar(
                title = if (productId == null) "Add New Product" else "Edit Product",
                onBackClick = { navController.popBackStack() }
            )
        },
        containerColor = WarmCream
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Product Name
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("Product / Item Name *") },
                placeholder = { Text("e.g. Fortune Mustard Oil 1L") },
                leadingIcon = { Icon(Icons.Default.Inventory2, contentDescription = null, tint = ForestGreen) },
                isError = state.error != null && state.name.isBlank(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ForestGreen,
                    unfocusedBorderColor = BorderColor,
                    focusedContainerColor = SurfaceWhite,
                    unfocusedContainerColor = SurfaceWhite
                )
            )

            // Category & Unit Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Category
                ExposedDropdownMenuBox(
                    expanded = expandedCategoryDropdown,
                    onExpandedChange = { expandedCategoryDropdown = !expandedCategoryDropdown },
                    modifier = Modifier.weight(1.2f)
                ) {
                    val categoryName = state.categories.find { it.id == state.categoryId }?.name ?: "None"
                    OutlinedTextField(
                        value = categoryName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCategoryDropdown) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ForestGreen,
                            unfocusedBorderColor = BorderColor,
                            focusedContainerColor = SurfaceWhite,
                            unfocusedContainerColor = SurfaceWhite
                        )
                    )

                    ExposedDropdownMenu(
                        expanded = expandedCategoryDropdown,
                        onDismissRequest = { expandedCategoryDropdown = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("None") },
                            onClick = {
                                viewModel.onCategoryChange(null)
                                expandedCategoryDropdown = false
                            }
                        )
                        state.categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name) },
                                onClick = {
                                    viewModel.onCategoryChange(cat.id)
                                    expandedCategoryDropdown = false
                                }
                            )
                        }
                    }
                }

                // Stock Unit
                ExposedDropdownMenuBox(
                    expanded = expandedUnitDropdown,
                    onExpandedChange = { expandedUnitDropdown = !expandedUnitDropdown },
                    modifier = Modifier.weight(0.8f)
                ) {
                    OutlinedTextField(
                        value = state.unit.symbol,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Unit") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedUnitDropdown) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ForestGreen,
                            unfocusedBorderColor = BorderColor,
                            focusedContainerColor = SurfaceWhite,
                            unfocusedContainerColor = SurfaceWhite
                        )
                    )

                    ExposedDropdownMenu(
                        expanded = expandedUnitDropdown,
                        onDismissRequest = { expandedUnitDropdown = false }
                    ) {
                        StockUnit.values().forEach { u ->
                            DropdownMenuItem(
                                text = { Text("${u.symbol} (${u.label})") },
                                onClick = {
                                    viewModel.onUnitChange(u)
                                    expandedUnitDropdown = false
                                }
                            )
                        }
                    }
                }
            }

            // Prices Card
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Pricing", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = ForestGreen)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = state.purchasePriceRupees,
                            onValueChange = viewModel::onPurchasePriceChange,
                            label = { Text("Cost Price (?)") },
                            placeholder = { Text("0.00") },
                            leadingIcon = { Icon(Icons.Default.CurrencyRupee, contentDescription = null, tint = ForestGreen) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = state.sellingPriceRupees,
                            onValueChange = viewModel::onSellingPriceChange,
                            label = { Text("Selling MRP (?) *") },
                            placeholder = { Text("0.00") },
                            leadingIcon = { Icon(Icons.Default.CurrencyRupee, contentDescription = null, tint = ForestGreen) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            // Stock Details
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Stock & Inventory", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = ForestGreen)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = state.openingStock,
                            onValueChange = viewModel::onStockChange,
                            label = { Text(if (productId == null) "Current Stock" else "Stock Quantity") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = state.minStockLevel,
                            onValueChange = viewModel::onMinStockChange,
                            label = { Text("Low Stock Alert At") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            // Barcode
            OutlinedTextField(
                value = state.barcode,
                onValueChange = viewModel::onBarcodeChange,
                label = { Text("Barcode / Product Code (Optional)") },
                placeholder = { Text("Scan or enter barcode") },
                leadingIcon = { Icon(Icons.Default.QrCode, contentDescription = null, tint = ForestGreen) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ForestGreen,
                    unfocusedBorderColor = BorderColor,
                    focusedContainerColor = SurfaceWhite,
                    unfocusedContainerColor = SurfaceWhite
                )
            )

            // Error
            if (state.error != null) {
                Text(state.error!!, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
            }

            // Save Button
            PrimaryButton(
                text = if (productId == null) "Save Product" else "Update Product",
                onClick = viewModel::saveProduct,
                loading = state.isLoading,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Default.Check
            )
        }
    }
}