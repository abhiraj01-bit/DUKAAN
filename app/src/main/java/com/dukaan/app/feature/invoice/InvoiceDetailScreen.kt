package com.dukaan.app.feature.invoice

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.dukaan.app.core.util.CurrencyFormatter
import com.dukaan.app.core.util.DateFormatter
import com.dukaan.app.core.util.formatQuantity

@Composable
fun InvoiceDetailScreen(
    navController: NavController,
    invoiceId: String,
    viewModel: InvoiceReviewViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(invoiceId) {
        viewModel.loadInvoice(invoiceId)
    }

    Scaffold(
        topBar = {
            DukaanTopBar(
                title = "Invoice Details",
                onBackClick = { navController.popBackStack() }
            )
        },
        containerColor = WarmCream
    ) { paddingValues ->
        if (state.isLoading) {
            LoadingState(modifier = Modifier.padding(paddingValues))
        } else if (state.invoice == null) {
            ErrorState(message = "Invoice not found", onRetry = { navController.popBackStack() })
        } else {
            val inv = state.invoice!!
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(inv.supplierName, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = ForestGreen)
                            Text("Invoice Number: ${inv.invoiceNumber}", style = MaterialTheme.typography.bodyMedium, color = Charcoal)
                            Text("Date: ${DateFormatter.formatDate(inv.invoiceDate)}", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                            Text("Status: ${inv.status.name}", style = MaterialTheme.typography.bodySmall, color = SuccessGreen)
                        }
                    }
                }

                item {
                    Text("Purchased Items (${inv.items.size})", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Charcoal)
                }

                items(inv.items) { item ->
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
                            Column {
                                Text(item.productName, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                                Text(
                                    "${item.quantity.formatQuantity()} pcs × ${CurrencyFormatter.formatPaise(item.purchasePricePaise)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SecondaryText
                                )
                            }
                            Text(
                                CurrencyFormatter.formatPaise(item.lineTotalPaise),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Charcoal
                            )
                        }
                    }
                }

                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SuccessGreenLight),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Total Invoice Amount", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = ForestGreen)
                            Text(
                                CurrencyFormatter.formatPaise(inv.grandTotalPaise),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = ForestGreen
                            )
                        }
                    }
                }
            }
        }
    }
}