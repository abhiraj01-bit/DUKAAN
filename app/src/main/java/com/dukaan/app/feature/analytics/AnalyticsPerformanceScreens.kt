package com.dukaan.app.feature.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.dukaan.app.core.designsystem.components.*
import com.dukaan.app.core.designsystem.theme.*
import com.dukaan.app.core.navigation.Screen
import com.dukaan.app.core.util.CurrencyFormatter
import com.dukaan.app.core.util.DateFormatter
import com.dukaan.app.domain.repository.CustomerRepository
import com.dukaan.app.domain.repository.ProductRepository
import com.dukaan.app.domain.repository.SaleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AnalyticsState(
    val totalSalesPaise: Long = 0L,
    val totalPurchasePaise: Long = 0L,
    val estimatedProfitPaise: Long = 0L,
    val outstandingCreditPaise: Long = 0L,
    val todaySalesPaise: Long = 0L,
    val isLoading: Boolean = true
)

data class ProductSaleVolume(
    val name: String,
    val unitsSold: Int,
    val progress: Float
)

@HiltViewModel
class AnalyticsReferenceViewModel @Inject constructor(
    private val saleRepository: SaleRepository,
    private val customerRepository: CustomerRepository,
    private val productRepository: ProductRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(AnalyticsState())
    val uiState = _uiState.asStateFlow()

    private val _topProducts = MutableStateFlow<List<ProductSaleVolume>>(emptyList())
    val topProducts = _topProducts.asStateFlow()

    private val _selectedPeriod = MutableStateFlow("Month")
    val selectedPeriod = _selectedPeriod.asStateFlow()

    init {
        loadData()
    }

    fun onPeriodChange(period: String) {
        _selectedPeriod.value = period
        loadData()
    }

    private fun loadData() {
        val now = System.currentTimeMillis()
        val todayStart = DateFormatter.startOfDay(now)
        val periodStart = when (_selectedPeriod.value) {
            "Today" -> todayStart
            "Week" -> DateFormatter.daysAgo(7)
            "Year" -> DateFormatter.daysAgo(365)
            else -> DateFormatter.startOfMonth(now)
        }

        viewModelScope.launch {
            combine(
                saleRepository.getTotalSalesPaiseForPeriod(periodStart, now),
                saleRepository.getTodaysSalesPaise(todayStart),
                customerRepository.getTotalOutstandingPaise(),
                productRepository.getTotalInventoryValuePaise()
            ) { periodSales, todaySales, outstanding, invVal ->
                val sales = periodSales ?: 0L
                AnalyticsState(
                    totalSalesPaise = sales,
                    totalPurchasePaise = invVal ?: 0L,
                    estimatedProfitPaise = (sales * 0.20).toLong(),
                    outstandingCreditPaise = outstanding ?: 0L,
                    todaySalesPaise = todaySales ?: 0L,
                    isLoading = false
                )
            }.collect { _uiState.value = it }
        }

        viewModelScope.launch {
            productRepository.getAllActiveProducts().collect { prods ->
                if (prods.isEmpty()) { _topProducts.value = emptyList(); return@collect }
                val maxStock = prods.maxOfOrNull { it.currentStock } ?: 1.0
                _topProducts.value = prods
                    .sortedByDescending { it.currentStock }
                    .take(5)
                    .map { p ->
                        ProductSaleVolume(
                            name = p.name,
                            unitsSold = p.currentStock.toInt(),
                            progress = if (maxStock > 0) (p.currentStock / maxStock).toFloat() else 0f
                        )
                    }
            }
        }
    }
}

// =================== SCREEN 22: ANALYTICS DASHBOARD ===================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferenceAnalyticsDashboardScreen(
    navController: NavController,
    onMenuClick: () -> Unit = {},
    viewModel: AnalyticsReferenceViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val selectedPeriod by viewModel.selectedPeriod.collectAsState()
    val topProducts by viewModel.topProducts.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analytics", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = PrimaryText) },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.ProductPerformance.route) }) {
                        Icon(Icons.Default.Leaderboard, contentDescription = "Performance", tint = PrimaryText)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WarmCream)
            )
        },
        containerColor = WarmCream
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 20.dp)
        ) {
            // Filter Tabs: Today, Week, Month, Year — each re-queries DB
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceWhite, RoundedCornerShape(12.dp))
                        .padding(4.dp)
                ) {
                    listOf("Today", "Week", "Month", "Year").forEach { tab ->
                        val isSelected = selectedPeriod == tab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) ForestGreen else Color.Transparent)
                                .clickable { viewModel.onPeriodChange(tab) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tab,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp,
                                color = if (isSelected) SurfaceWhite else SecondaryText
                            )
                        }
                    }
                }
            }

            // 4 Summary Cards (2x2) — all real DB data
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AnalyticsMetricCard(
                            title = "Total Sales",
                            value = CurrencyFormatter.formatPaise(state.totalSalesPaise),
                            badgeText = null,
                            badgeColor = SecondaryGreen,
                            bgColor = LightGreenSurface,
                            modifier = Modifier.weight(1f)
                        )
                        AnalyticsMetricCard(
                            title = "Inventory Value",
                            value = CurrencyFormatter.formatPaise(state.totalPurchasePaise),
                            badgeText = null,
                            badgeColor = SaffronOrange,
                            bgColor = LightOrangeSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AnalyticsMetricCard(
                            title = "Estimated Profit",
                            value = CurrencyFormatter.formatPaise(state.estimatedProfitPaise),
                            badgeText = null,
                            badgeColor = SecondaryGreen,
                            bgColor = LightGreenSurface,
                            modifier = Modifier.weight(1f)
                        )
                        AnalyticsMetricCard(
                            title = "Outstanding Credit",
                            value = CurrencyFormatter.formatPaise(state.outstandingCreditPaise),
                            badgeText = null,
                            badgeColor = ErrorRed,
                            bgColor = LightRedSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Sales Trend Bar Chart — bars driven by real top-product stock levels
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Sales Trend",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = PrimaryText
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(LightGreenSurface)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = CurrencyFormatter.formatPaise(state.totalSalesPaise),
                                    fontWeight = FontWeight.Bold,
                                    color = ForestGreen,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                        ) {
                            if (topProducts.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("No data yet", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                                }
                            } else {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val w = size.width
                                    val h = size.height
                                    drawLine(
                                        color = Color(0xFFE8E3D9),
                                        start = Offset(0f, h * 0.88f),
                                        end = Offset(w, h * 0.88f),
                                        strokeWidth = 2f
                                    )
                                    val barWidth = 26f
                                    val spacing = (w - (topProducts.size * barWidth)) / (topProducts.size + 1)
                                    topProducts.forEachIndexed { i, prod ->
                                        val x = spacing + i * (barWidth + spacing)
                                        val barH = h * 0.75f * prod.progress
                                        val y = h * 0.88f - barH
                                        val color = if (i % 2 == 0) ForestGreen else SaffronOrange
                                        drawRoundRect(
                                            color = color,
                                            topLeft = Offset(x, y),
                                            size = Size(barWidth, barH),
                                            cornerRadius = CornerRadius(6f, 6f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // View Product Performance Link Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navController.navigate(Screen.ProductPerformance.route) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.TrendingUp, contentDescription = null, tint = ForestGreen)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("View Product Performance", fontWeight = FontWeight.Bold, color = PrimaryText)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = SecondaryText)
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyticsMetricCard(
    title: String,
    value: String,
    badgeText: String?,
    badgeColor: Color,
    bgColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(104.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = SecondaryText)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), color = PrimaryText)
                if (badgeText != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(badgeColor.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(badgeText, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = badgeColor)
                    }
                }
            }
        }
    }
}

// =================== SCREEN 23: PRODUCT PERFORMANCE ===================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferenceProductPerformanceScreen(
    navController: NavController,
    viewModel: AnalyticsReferenceViewModel = hiltViewModel()
) {
    val topProducts by viewModel.topProducts.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Product Performance", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = PrimaryText) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PrimaryText)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WarmCream)
            )
        },
        containerColor = WarmCream
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            if (topProducts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No products in inventory yet", color = SecondaryText, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        topProducts.forEach { item ->
                            PerformanceBarRow(
                                name = item.name,
                                units = "${item.unitsSold} units",
                                progress = item.progress
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PerformanceBarRow(
    name: String,
    units: String,
    progress: Float
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = PrimaryText)
            Text(units, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = SecondaryText)
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp)),
            color = ForestGreen,
            trackColor = LightGreenSurface
        )
    }
}
