package com.dukaan.app.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.dukaan.app.core.designsystem.components.*
import com.dukaan.app.core.designsystem.theme.*
import com.dukaan.app.core.navigation.Screen
import com.dukaan.app.core.util.CurrencyFormatter
import com.dukaan.app.core.util.DateFormatter

// =================== SCREEN 10: HOME DASHBOARD ===================

@Composable
fun DashboardScreen(
    navController: NavController,
    onMenuClick: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = WarmCream
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // Top Bar: Profile avatar + Shop name + Notification bell
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(onClick = onMenuClick)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(LightGreenSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Storefront, contentDescription = "Menu", tint = ForestGreen, modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = state.shopProfile?.shopName ?: "My Shop",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = PrimaryText
                        )
                    }

                    // Notification Bell with unread dot
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(SurfaceWhite)
                            .clickable { navController.navigate(Screen.LowStockAlerts.route) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.NotificationsNone, contentDescription = "Notifications", tint = PrimaryText, modifier = Modifier.size(22.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .align(Alignment.TopEnd)
                                .offset(x = (-6).dp, y = 6.dp)
                                .clip(CircleShape)
                                .background(SaffronOrange)
                        )
                    }
                }
            }

            // Greeting Section: "Good Morning!" / "नमस्ते जी!" / "Here's your shop's overview"
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                    Text(
                        text = "Good Morning!",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 26.sp
                        ),
                        color = PrimaryText
                    )
                    Text(
                        text = if (!state.shopProfile?.ownerName.isNullOrBlank()) "नमस्ते, ${state.shopProfile?.ownerName}!" else "नमस्ते जी!",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = SecondaryGreen
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Here's your shop's overview",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryText
                    )
                }
            }

            // 2x2 Summary Cards Grid (All live Room DB queries!)
            item {
                Spacer(modifier = Modifier.height(14.dp))
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Row 1: Today's Sales & Profit
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DashboardMetricCard(
                            title = "Today's Sales",
                            value = CurrencyFormatter.formatPaise(state.todaySalesPaise),
                            badgeText = null,
                            badgeColor = SecondaryGreen,
                            backgroundColor = LightGreenSurface,
                            modifier = Modifier.weight(1f),
                            onClick = { navController.navigate(Screen.SaleHistory.route) }
                        )

                        DashboardMetricCard(
                            title = "Inventory Value",
                            value = CurrencyFormatter.formatPaise(state.inventoryValuePaise),
                            badgeText = null,
                            badgeColor = SecondaryGreen,
                            backgroundColor = LightOrangeSurface,
                            modifier = Modifier.weight(1f),
                            onClick = { navController.navigate(Screen.AnalyticsDashboard.route) }
                        )
                    }

                    // Row 2: Udhaar & Low Stock
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DashboardMetricCard(
                            title = "Udhaar",
                            value = CurrencyFormatter.formatPaise(state.totalKhataOutstandingPaise),
                            badgeText = null,
                            badgeColor = ErrorRed,
                            backgroundColor = LightRedSurface,
                            modifier = Modifier.weight(1f),
                            onClick = { navController.navigate(Screen.CustomerList.route) }
                        )

                        DashboardMetricCard(
                            title = "Low Stock",
                            value = "${state.lowStockCount} items",
                            badgeText = null,
                            badgeColor = SaffronOrange,
                            backgroundColor = LightOrangeSurface,
                            modifier = Modifier.weight(1f),
                            onClick = { navController.navigate(Screen.LowStockAlerts.route) }
                        )
                    }
                }
            }

            // Quick Actions Section (Scan Bill, Add Sale, Add Customer)
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    Text(
                        text = "Quick Actions",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = PrimaryText
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        QuickActionItem(
                            label = "Scan Bill",
                            icon = Icons.Default.DocumentScanner,
                            color = ForestGreen,
                            onClick = { navController.navigate(Screen.InvoiceCamera.route) }
                        )

                        QuickActionItem(
                            label = "Add Sale",
                            icon = Icons.Default.ShoppingCart,
                            color = SaffronOrange,
                            onClick = { navController.navigate(Screen.NewSale.route) }
                        )

                        QuickActionItem(
                            label = "Add Customer",
                            icon = Icons.Default.PersonAdd,
                            color = SecondaryGreen,
                            onClick = { navController.navigate(Screen.AddCustomer.route) }
                        )
                    }
                }
            }

            // Recent Business Activity Card
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent Activity",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = PrimaryText
                        )
                        Text(
                            text = "View All",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = SecondaryGreen,
                            modifier = Modifier.clickable { navController.navigate(Screen.SaleHistory.route) }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (state.recentSales.isEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No recent activity yet",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SecondaryText
                                )
                            }
                        }
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                state.recentSales.forEachIndexed { index, sale ->
                                    ActivityRowItem(
                                        title = "Sale - ${sale.customerName ?: "Cash Customer"}",
                                        time = DateFormatter.formatFull(sale.saleDate),
                                        amount = CurrencyFormatter.formatPaise(sale.grandTotalPaise),
                                        isPositive = true
                                    )
                                    if (index < state.recentSales.lastIndex) {
                                        HorizontalDivider(color = BorderColor, modifier = Modifier.padding(vertical = 10.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardMetricCard(
    title: String,
    value: String,
    badgeText: String?,
    badgeColor: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(106.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = SecondaryText
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp
                    ),
                    color = PrimaryText
                )

                if (badgeText != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(badgeColor.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = badgeColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionItem(
    label: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = PrimaryText
        )
    }
}

@Composable
private fun ActivityRowItem(
    title: String,
    time: String,
    amount: String,
    isPositive: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isPositive) LightGreenSurface else LightRedSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPositive) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                    contentDescription = null,
                    tint = if (isPositive) SuccessGreen else ErrorRed,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = PrimaryText
                )
                Text(
                    text = time,
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryText
                )
            }
        }

        Text(
            text = amount,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = if (isPositive) SuccessGreen else ErrorRed
        )
    }
}

