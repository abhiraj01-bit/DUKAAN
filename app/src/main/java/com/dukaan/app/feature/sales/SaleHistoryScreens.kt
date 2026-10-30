package com.dukaan.app.feature.sales

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.dukaan.app.core.designsystem.components.*
import com.dukaan.app.core.designsystem.theme.*
import com.dukaan.app.core.navigation.Screen
import com.dukaan.app.core.util.CurrencyFormatter
import com.dukaan.app.core.util.DateFormatter
import com.dukaan.app.core.util.formatQuantity
import com.dukaan.app.domain.model.Sale
import com.dukaan.app.domain.repository.SaleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SaleHistoryViewModel @Inject constructor(
    private val saleRepository: SaleRepository
) : ViewModel() {
    private val _sales = MutableStateFlow<List<Sale>>(emptyList())
    val sales = _sales.asStateFlow()

    init {
        viewModelScope.launch {
            saleRepository.getAllSales().collect { list ->
                _sales.value = list
            }
        }
    }
}

@Composable
fun SaleHistoryScreen(
    navController: NavController,
    viewModel: SaleHistoryViewModel = hiltViewModel()
) {
    val sales by viewModel.sales.collectAsState()

    Scaffold(
        topBar = {
            DukaanTopBar(
                title = "Sales History",
                onBackClick = { navController.popBackStack() }
            )
        },
        containerColor = WarmCream
    ) { paddingValues ->
        if (sales.isEmpty()) {
            EmptyState(
                title = "No sales recorded",
                subtitle = "Completed sales and bills will appear in this history.",
                icon = Icons.Default.Receipt,
                modifier = Modifier.padding(paddingValues)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(sales) { sale ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { navController.navigate(Screen.SaleDetail.createRoute(sale.id)) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(sale.customerName ?: "Cash Customer", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                                Text(
                                    DateFormatter.formatFull(sale.saleDate),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SecondaryText
                                )
                            }
                            Text(
                                CurrencyFormatter.formatPaise(sale.grandTotalPaise),
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

@HiltViewModel
class SaleDetailViewModel @Inject constructor(
    private val saleRepository: SaleRepository
) : ViewModel() {
    private val _sale = MutableStateFlow<Sale?>(null)
    val sale = _sale.asStateFlow()

    fun load(saleId: String) {
        viewModelScope.launch {
            _sale.value = saleRepository.getSaleById(saleId)
        }
    }
}

@Composable
fun SaleDetailScreen(
    navController: NavController,
    saleId: String,
    viewModel: SaleDetailViewModel = hiltViewModel()
) {
    val sale by viewModel.sale.collectAsState()

    LaunchedEffect(saleId) { viewModel.load(saleId) }

    Scaffold(
        topBar = {
            DukaanTopBar(
                title = "Sale Details",
                onBackClick = { navController.popBackStack() }
            )
        },
        containerColor = WarmCream
    ) { paddingValues ->
        val s = sale
        if (s == null) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ForestGreen)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Customer: ${s.customerName ?: "Cash Customer"}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            Text("Date: ${DateFormatter.formatFull(s.saleDate)}", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                            Text("Status: ${s.status.name}", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                            HorizontalDivider(color = BorderColor)
                            Text("Total: ${CurrencyFormatter.formatPaise(s.grandTotalPaise)}",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), color = ForestGreen)
                        }
                    }
                }
                if (s.items.isNotEmpty()) {
                    item {
                        Text("Items", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = PrimaryText)
                    }
                    items(s.items) { item ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.productName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                    Text("${item.quantity.toInt()} × ${CurrencyFormatter.formatPaise(item.sellingPricePaise)}",
                                        style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                                }
                                Text(CurrencyFormatter.formatPaise(item.lineTotalPaise),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = ForestGreen)
                            }
                        }
                    }
                }
            }
        }
    }
}