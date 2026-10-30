package com.dukaan.app.feature.khata

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.dukaan.app.core.designsystem.components.*
import com.dukaan.app.core.designsystem.theme.*
import com.dukaan.app.core.navigation.Screen
import com.dukaan.app.core.util.CurrencyFormatter
import com.dukaan.app.domain.model.Customer

@Composable
fun KhataScreen(
    navController: NavController,
    onMenuClick: () -> Unit,
    viewModel: KhataViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            DukaanTopBar(
                title = "Digital Khata (Udhar)",
                onMenuClick = onMenuClick
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Screen.AddCustomer.route) },
                containerColor = ForestGreen,
                contentColor = SurfaceWhite
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = "Add Customer")
            }
        },
        containerColor = WarmCream
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Outstanding Banner
            Surface(
                color = SurfaceWhite,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (state.totalOutstandingPaise > 0) WarningOrangeLight else SuccessGreenLight),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("TOTAL AMOUNT TO RECEIVE (UDHAR)", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Charcoal)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    CurrencyFormatter.formatPaise(state.totalOutstandingPaise),
                                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
                                    color = if (state.totalOutstandingPaise > 0) WarningOrange else ForestGreen
                                )
                            }
                            Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(36.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    DukaanSearchBar(
                        query = state.searchQuery,
                        onQueryChange = viewModel::onSearchChange,
                        placeholder = "Search customer by name or phone..."
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.showOnlyPending,
                            onClick = { viewModel.onFilterChange(true) },
                            label = { Text("Pending Due Only") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ForestGreen,
                                selectedLabelColor = SurfaceWhite
                            )
                        )
                        FilterChip(
                            selected = !state.showOnlyPending,
                            onClick = { viewModel.onFilterChange(false) },
                            label = { Text("All Customers") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ForestGreen,
                                selectedLabelColor = SurfaceWhite
                            )
                        )
                    }
                }
            }

            // Customer List
            if (state.isLoading) {
                LoadingState()
            } else if (state.customersWithDue.isEmpty()) {
                EmptyState(
                    title = if (state.showOnlyPending) "No pending customer dues!" else "No customers found",
                    subtitle = if (state.showOnlyPending) "All customer accounts are settled and paid up." else "Add customers to start maintaining credit balances.",
                    icon = Icons.Default.CheckCircle,
                    actionText = "Add New Customer",
                    onAction = { navController.navigate(Screen.AddCustomer.route) }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.customersWithDue, key = { it.id }) { customer ->
                        KhataCustomerCard(
                            customer = customer,
                            onClick = { navController.navigate(Screen.CustomerDetail.createRoute(customer.id)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun KhataCustomerCard(
    customer: Customer,
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
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = customer.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Charcoal
                )
                if (!customer.phoneNumber.isNullOrBlank()) {
                    Text(
                        text = customer.phoneNumber!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = SecondaryText
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = CurrencyFormatter.formatPaise(customer.outstandingPaise),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (customer.outstandingPaise > 0) ErrorRed else SuccessGreen
                )
                Text(
                    text = if (customer.outstandingPaise > 0) "Due" else "Settled",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (customer.outstandingPaise > 0) ErrorRed else SuccessGreen
                )
            }
        }
    }
}