package com.dukaan.app.feature.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.dukaan.app.core.designsystem.components.*
import com.dukaan.app.core.designsystem.theme.*
import com.dukaan.app.core.util.CurrencyFormatter
import com.dukaan.app.core.util.DateFormatter
import com.dukaan.app.domain.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AnalyticsUiState(
    val monthlySalesPaise: Long = 0L,
    val monthlyExpensesPaise: Long = 0L,
    val totalOutstandingPaise: Long = 0L,
    val inventoryValuePaise: Long = 0L,
    val totalSalesCount: Int = 0,
    val isLoading: Boolean = true
) {
    val estimatedNetProfitPaise: Long get() = (monthlySalesPaise * 0.20).toLong() - monthlyExpensesPaise
}

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val saleRepository: SaleRepository,
    private val expenseRepository: ExpenseRepository,
    private val customerRepository: CustomerRepository,
    private val productRepository: ProductRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        val now = System.currentTimeMillis()
        val startMonth = DateFormatter.startOfMonth(now)

        viewModelScope.launch {
            combine(
                saleRepository.getTotalSalesPaiseForPeriod(startMonth, now),
                saleRepository.getSalesCountForPeriod(startMonth, now),
                expenseRepository.getTotalExpensesPaiseForPeriod(startMonth, now),
                customerRepository.getTotalOutstandingPaise(),
                productRepository.getTotalInventoryValuePaise()
            ) { sales, count, expenses, out, invVal ->
                AnalyticsUiState(
                    monthlySalesPaise = sales ?: 0L,
                    totalSalesCount = count,
                    monthlyExpensesPaise = expenses ?: 0L,
                    totalOutstandingPaise = out ?: 0L,
                    inventoryValuePaise = invVal ?: 0L,
                    isLoading = false
                )
            }.collect { combined ->
                _uiState.value = combined
            }
        }
    }
}

@Composable
fun AnalyticsScreen(
    navController: NavController,
    onMenuClick: () -> Unit,
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            DukaanTopBar(
                title = "Business Analytics",
                onMenuClick = onMenuClick
            )
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        "Monthly Performance Overview",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Charcoal
                    )
                }

                // Profit & Revenue Cards
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SummaryCard(
                            title = "This Month Sales",
                            value = CurrencyFormatter.formatPaise(state.monthlySalesPaise),
                            icon = Icons.Default.TrendingUp,
                            valueColor = ForestGreen,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryCard(
                            title = "Bills Created",
                            value = "${state.totalSalesCount}",
                            icon = Icons.Default.Receipt,
                            valueColor = ForestGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SummaryCard(
                            title = "Expenses Logged",
                            value = CurrencyFormatter.formatPaise(state.monthlyExpensesPaise),
                            icon = Icons.Default.TrendingDown,
                            valueColor = ErrorRed,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryCard(
                            title = "Estimated Net",
                            value = CurrencyFormatter.formatPaise(state.estimatedNetProfitPaise),
                            icon = Icons.Default.MonetizationOn,
                            valueColor = if (state.estimatedNetProfitPaise >= 0) SuccessGreen else ErrorRed,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Balance Sheet Snapshot
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text("Shop Assets & Receivables", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = ForestGreen)

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Current Inventory Stock Valuation", style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
                                Text(CurrencyFormatter.formatPaise(state.inventoryValuePaise), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            }

                            HorizontalDivider(color = DividerColor)

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total Customer Udhar to Collect", style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
                                Text(CurrencyFormatter.formatPaise(state.totalOutstandingPaise), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = WarningOrange)
                            }
                        }
                    }
                }

                // AI Insight Note
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SuccessGreenLight),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lightbulb, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text("Smart Shop Insight", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = ForestGreen)
                                Text(
                                    "Your sales are on track! Remember to collect outstanding payments from customers before ordering new wholesale stock.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Charcoal
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}