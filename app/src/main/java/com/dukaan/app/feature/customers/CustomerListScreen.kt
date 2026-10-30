package com.dukaan.app.feature.customers

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
fun CustomerListScreen(
    navController: NavController,
    viewModel: CustomerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            DukaanTopBar(
                title = "Customers",
                onBackClick = { navController.popBackStack() }
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
            // Search Bar
            Box(modifier = Modifier.padding(16.dp)) {
                DukaanSearchBar(
                    query = state.searchQuery,
                    onQueryChange = viewModel::onSearchChange,
                    placeholder = "Search customers by name or phone..."
                )
            }

            if (state.isLoading) {
                LoadingState()
            } else if (state.customers.isEmpty()) {
                EmptyState(
                    title = "No customers added yet",
                    subtitle = "Add regular customers to record credit sales and maintain khata books.",
                    icon = Icons.Default.People,
                    actionText = "Add First Customer",
                    onAction = { navController.navigate(Screen.AddCustomer.route) }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.customers, key = { it.id }) { customer ->
                        CustomerItemCard(
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
fun CustomerItemCard(
    customer: Customer,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
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
                if (customer.hasOutstanding) {
                    Text(
                        text = "Due: ${CurrencyFormatter.formatPaise(customer.outstandingPaise)}",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = ErrorRed
                    )
                    StatusBadge("Udhar Pending", ErrorRed)
                } else {
                    Text(
                        text = "All Cleared",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = SuccessGreen
                    )
                    StatusBadge("No Due", SuccessGreen)
                }
            }
        }
    }
}