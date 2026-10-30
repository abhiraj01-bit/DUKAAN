package com.dukaan.app.feature.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.dukaan.app.core.designsystem.components.*
import com.dukaan.app.core.designsystem.theme.*
import com.dukaan.app.core.navigation.Screen
import com.dukaan.app.core.util.CurrencyFormatter
import com.dukaan.app.core.util.formatQuantity
import com.dukaan.app.domain.model.Product

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    navController: NavController,
    onMenuClick: () -> Unit,
    viewModel: InventoryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            DukaanTopBar(
                title = "Inventory & Stock",
                onMenuClick = onMenuClick,
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Categories.route) }) {
                        Icon(Icons.Default.Category, contentDescription = "Categories", tint = SurfaceWhite)
                    }
                    IconButton(onClick = { navController.navigate(Screen.LowStock.route) }) {
                        BadgedBox(
                            badge = {
                                if (state.lowStockCount > 0) {
                                    Badge(containerColor = ErrorRed) { Text("${state.lowStockCount}") }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = "Low Stock", tint = SurfaceWhite)
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Screen.AddProduct.route) },
                containerColor = ForestGreen,
                contentColor = SurfaceWhite
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Product")
            }
        },
        containerColor = WarmCream
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search Bar & Filter
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceWhite)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DukaanSearchBar(
                    query = state.searchQuery,
                    onQueryChange = viewModel::onSearchChange,
                    placeholder = "Search products by name or barcode..."
                )

                // Category Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = state.selectedCategoryId == null,
                            onClick = { viewModel.onSelectCategory(null) },
                            label = { Text("All (${state.totalCount})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ForestGreen,
                                selectedLabelColor = SurfaceWhite
                            )
                        )
                    }

                    items(state.categories) { cat ->
                        FilterChip(
                            selected = state.selectedCategoryId == cat.id,
                            onClick = { viewModel.onSelectCategory(cat.id) },
                            label = { Text(cat.name) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ForestGreen,
                                selectedLabelColor = SurfaceWhite
                            )
                        )
                    }
                }
            }

            // Valuation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SuccessGreenLight)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${state.products.size} Products shown",
                    style = MaterialTheme.typography.bodySmall,
                    color = ForestGreen
                )
                Text(
                    text = "Stock Valuation: ${CurrencyFormatter.formatPaise(state.totalValuePaise)}",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = ForestGreen
                )
            }

            // Products List
            if (state.isLoading) {
                LoadingState()
            } else if (state.products.isEmpty()) {
                EmptyState(
                    title = "No products found",
                    subtitle = if (state.searchQuery.isNotBlank()) "Try searching for a different keyword" else "Add your shop's first product to track stock and sell.",
                    icon = Icons.Default.Inventory2,
                    actionText = if (state.searchQuery.isBlank()) "Add First Product" else null,
                    onAction = { navController.navigate(Screen.AddProduct.route) }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.products, key = { it.id }) { product ->
                        ProductItemCard(
                            product = product,
                            onClick = { navController.navigate(Screen.ProductDetail.createRoute(product.id)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProductItemCard(
    product: Product,
    onClick: () -> Unit
) {
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
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Charcoal
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Sell: ${CurrencyFormatter.formatPaise(product.sellingPricePaise)}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = ForestGreen
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Cost: ${CurrencyFormatter.formatPaise(product.purchasePricePaise)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = SecondaryText
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                val stockText = "${product.currentStock.formatQuantity()} ${product.unit.symbol}"
                Text(
                    text = stockText,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = when {
                        product.isOutOfStock -> ErrorRed
                        product.isLowStock -> WarningOrange
                        else -> Charcoal
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
                when {
                    product.isOutOfStock -> StatusBadge(text = "Out of Stock", color = ErrorRed)
                    product.isLowStock -> StatusBadge(text = "Low Stock", color = WarningOrange)
                    else -> StatusBadge(text = "In Stock", color = SuccessGreen)
                }
            }
        }
    }
}