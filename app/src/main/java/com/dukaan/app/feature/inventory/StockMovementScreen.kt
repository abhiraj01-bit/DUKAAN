package com.dukaan.app.feature.inventory

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.dukaan.app.core.designsystem.components.*
import com.dukaan.app.core.designsystem.theme.*
import com.dukaan.app.core.util.DateFormatter
import com.dukaan.app.core.util.formatQuantity
import com.dukaan.app.domain.model.StockMovementType

@Composable
fun StockMovementScreen(
    navController: NavController,
    productId: String,
    viewModel: ProductDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(productId) {
        viewModel.loadProduct(productId)
    }

    Scaffold(
        topBar = {
            DukaanTopBar(
                title = "Stock History",
                onBackClick = { navController.popBackStack() }
            )
        },
        containerColor = WarmCream
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Product Subhead
            if (state.product != null) {
                Surface(
                    color = SurfaceWhite,
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
                            Text(state.product!!.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            Text("Current: ${state.product!!.currentStock.formatQuantity()} ${state.product!!.unit.symbol}", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                        }
                    }
                }
            }

            if (state.movements.isEmpty()) {
                EmptyState(
                    title = "No movement records",
                    subtitle = "Stock adjustments, sales, and invoice purchases will appear here.",
                    icon = Icons.Default.History
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.movements) { move ->
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
                                    val title = when (move.type) {
                                        StockMovementType.INITIAL_STOCK -> "Opening Stock"
                                        StockMovementType.INVOICE_PURCHASE -> "Invoice Receipt"
                                        StockMovementType.SALE -> "Customer Sale"
                                        StockMovementType.SALE_RETURN -> "Return from Customer"
                                        StockMovementType.MANUAL_ADD -> "Manual Addition"
                                        StockMovementType.DAMAGE_OR_LOSS -> "Damage / Wastage"
                                        StockMovementType.RECONCILIATION -> "Stock Count Correction"
                                        else -> move.type.displayName
                                    }
                                    Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                                    Text(
                                        "${DateFormatter.formatFull(move.createdAt)}${if (!move.notes.isNullOrBlank()) " • ${move.notes}" else ""}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SecondaryText
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    val sign = if (move.quantityChange > 0) "+" else ""
                                    Text(
                                        "$sign${move.quantityChange.formatQuantity()}",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (move.quantityChange >= 0) SuccessGreen else ErrorRed
                                    )
                                    Text(
                                        "Bal: ${move.quantityAfter.formatQuantity()}",
                                        style = MaterialTheme.typography.labelSmall,
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
}