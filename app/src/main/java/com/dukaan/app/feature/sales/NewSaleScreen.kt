package com.dukaan.app.feature.sales

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.dukaan.app.core.util.CurrencyFormatter
import com.dukaan.app.core.util.formatQuantity
import com.dukaan.app.domain.model.PaymentMethod

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewSaleScreen(
    navController: NavController,
    viewModel: NewSaleViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.isCompleted) {
        if (state.isCompleted) navController.popBackStack()
    }

    var showCustomerPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            DukaanTopBar(
                title = "New Sale / Billing",
                onBackClick = { navController.popBackStack() }
            )
        },
        bottomBar = {
            Surface(
                color = SurfaceWhite,
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("${state.cartItems.size} items in cart", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                            Text(
                                CurrencyFormatter.formatPaise(state.totalAmountPaise),
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                color = ForestGreen
                            )
                        }

                        Button(
                            onClick = viewModel::completeSale,
                            enabled = state.cartItems.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(50.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (state.isCreditSale) "Charge Udhar" else "Finish Sale")
                        }
                    }

                    if (state.error != null) {
                        Text(state.error!!, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        containerColor = WarmCream
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Customer Selector & Mode
            Surface(
                color = SurfaceWhite,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { showCustomerPicker = true }
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = ForestGreen)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = state.selectedCustomer?.name ?: "Cash Customer (Tap to select)",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Charcoal
                            )
                        }

                        if (state.selectedCustomer != null) {
                            IconButton(onClick = { viewModel.selectCustomer(null) }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = SecondaryText)
                            }
                        }
                    }

                    // Payment Method Chips
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !state.isCreditSale && state.paymentMethod == PaymentMethod.CASH,
                            onClick = { viewModel.setPaymentMethod(PaymentMethod.CASH) },
                            label = { Text("Cash") }
                        )
                        FilterChip(
                            selected = !state.isCreditSale && state.paymentMethod == PaymentMethod.UPI,
                            onClick = { viewModel.setPaymentMethod(PaymentMethod.UPI) },
                            label = { Text("UPI / Online") }
                        )
                        FilterChip(
                            selected = state.isCreditSale,
                            onClick = { viewModel.setCreditSale(true) },
                            label = { Text("Udhar / Credit") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ErrorRed,
                                selectedLabelColor = SurfaceWhite
                            )
                        )
                    }
                }
            }

            // Products to pick & Cart
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Cart Items Section
                if (state.cartItems.isNotEmpty()) {
                    item {
                        Text("Cart Items", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Charcoal)
                    }

                    items(state.cartItems) { ci ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(ci.product.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                                    Text(CurrencyFormatter.formatPaise(ci.sellingPricePaise), style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { viewModel.updateCartQuantity(ci.product.id, -1.0) }) {
                                        Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Decrease", tint = ForestGreen)
                                    }
                                    Text(
                                        "${ci.quantity.formatQuantity()} ${ci.product.unit.symbol}",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                    IconButton(onClick = { viewModel.updateCartQuantity(ci.product.id, 1.0) }) {
                                        Icon(Icons.Default.AddCircleOutline, contentDescription = "Increase", tint = ForestGreen)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        CurrencyFormatter.formatPaise(ci.lineTotalPaise),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = ForestGreen
                                    )
                                }
                            }
                        }
                    }
                }

                // Inventory Products Quick Add
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Select from Inventory", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Charcoal)
                }

                items(state.availableProducts) { prod ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.addToCart(prod) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(prod.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                                Text(
                                    "Stock: ${prod.currentStock.formatQuantity()} • ${CurrencyFormatter.formatPaise(prod.sellingPricePaise)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SecondaryText
                                )
                            }
                            IconButton(
                                onClick = { viewModel.addToCart(prod) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(SuccessGreenLight)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add", tint = ForestGreen)
                            }
                        }
                    }
                }
            }
        }
    }

    // Select Customer Dialog
    if (showCustomerPicker) {
        AlertDialog(
            onDismissRequest = { showCustomerPicker = false },
            title = { Text("Select Customer") },
            text = {
                LazyColumn(modifier = Modifier.height(300.dp)) {
                    items(state.customers) { c ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.selectCustomer(c)
                                    showCustomerPicker = false
                                }
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(c.name, style = MaterialTheme.typography.bodyLarge)
                            if (c.hasOutstanding) {
                                Text("Due: ${CurrencyFormatter.formatPaise(c.outstandingPaise)}", color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        HorizontalDivider(color = DividerColor)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCustomerPicker = false }) { Text("Close") }
            }
        )
    }
}