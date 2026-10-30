package com.dukaan.app.core.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.dukaan.app.core.designsystem.theme.*
import com.dukaan.app.domain.model.ShopProfile
import com.dukaan.app.domain.repository.ShopRepository
import com.dukaan.app.feature.analytics.AnalyticsScreen
import com.dukaan.app.feature.dashboard.DashboardScreen
import com.dukaan.app.feature.inventory.InventoryScreen
import com.dukaan.app.feature.invoice.InvoiceHistoryScreen
import com.dukaan.app.feature.khata.KhataScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ViewModel for side drawer to load real shop profile
@HiltViewModel
class MainScreenViewModel @Inject constructor(
    private val shopRepository: ShopRepository
) : ViewModel() {
    private val _shopProfile = MutableStateFlow<ShopProfile?>(null)
    val shopProfile = _shopProfile.asStateFlow()

    init {
        viewModelScope.launch {
            shopRepository.getShopProfile().collect { _shopProfile.value = it }
        }
    }
}

// 5 Bottom Navigation items matching reference
data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: String
)

val bottomNavItems = listOf(
    BottomNavItem("Home", Icons.Default.Home, Screen.HomeDashboard.route),
    BottomNavItem("Invoice", Icons.Default.Receipt, Screen.InvoiceHistory.route),
    BottomNavItem("Inventory", Icons.Default.Inventory2, Screen.InventoryList.route),
    BottomNavItem("Khata", Icons.Default.Book, Screen.CustomerList.route),
    BottomNavItem("More", Icons.Default.MoreHoriz, Screen.AnalyticsDashboard.route)
)

// =================== SCREEN 10 & 11: MAIN SCAFFOLD WITH DRAWER ===================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    navController: NavController,
    startTab: Int = 0,
    viewModel: MainScreenViewModel = hiltViewModel()
) {
    val shopProfile by viewModel.shopProfile.collectAsState()
    var selectedTab by remember { mutableStateOf(startTab) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ReferenceSideMenuDrawer(
                navController = navController,
                shopProfile = shopProfile,
                onClose = { coroutineScope.launch { drawerState.close() } }
            )
        }
    ) {
        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = SurfaceWhite,
                    tonalElevation = 8.dp,
                    modifier = Modifier.height(68.dp)
                ) {
                    bottomNavItems.forEachIndexed { index, item ->
                        val isSelected = selectedTab == index
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { selectedTab = index },
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = item.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = ForestGreen,
                                selectedTextColor = ForestGreen,
                                indicatorColor = LightGreenSurface,
                                unselectedIconColor = SecondaryText,
                                unselectedTextColor = SecondaryText
                            )
                        )
                    }
                }
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                when (selectedTab) {
                    0 -> DashboardScreen(
                        navController = navController,
                        onMenuClick = { coroutineScope.launch { drawerState.open() } }
                    )
                    1 -> InvoiceHistoryScreen(navController = navController)
                    2 -> InventoryScreen(
                        navController = navController,
                        onMenuClick = { coroutineScope.launch { drawerState.open() } }
                    )
                    3 -> KhataScreen(
                        navController = navController,
                        onMenuClick = { coroutineScope.launch { drawerState.open() } }
                    )
                    4 -> AnalyticsScreen(
                        navController = navController,
                        onMenuClick = { coroutineScope.launch { drawerState.open() } }
                    )
                }
            }
        }
    }
}

// =================== SCREEN 11: SIDE NAVIGATION DRAWER ===================

@Composable
fun ReferenceSideMenuDrawer(
    navController: NavController,
    shopProfile: ShopProfile?,
    onClose: () -> Unit
) {
    ModalDrawerSheet(
        drawerContainerColor = SurfaceWhite,
        modifier = Modifier.width(300.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 20.dp, horizontal = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // User Avatar Header — real shop data
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(LightGreenSurface),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(32.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = shopProfile?.shopName ?: "Your Shop",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = PrimaryText
                        )
                        Text(
                            text = shopProfile?.ownerName?.takeIf { it.isNotBlank() } ?: shopProfile?.phoneNumber ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondaryText
                        )
                    }
                }

                HorizontalDivider(color = BorderColor, modifier = Modifier.padding(vertical = 12.dp))

                // Navigation Items
                DrawerRowItem(label = "Home", icon = Icons.Default.Home, isSelected = true) {
                    onClose()
                }
                DrawerRowItem(label = "Invoice Scanner", icon = Icons.Default.DocumentScanner) {
                    navController.navigate(Screen.InvoiceCamera.route); onClose()
                }
                DrawerRowItem(label = "Inventory", icon = Icons.Default.Inventory2) {
                    navController.navigate(Screen.InventoryList.route); onClose()
                }
                DrawerRowItem(label = "Khata (Customers)", icon = Icons.Default.People) {
                    navController.navigate(Screen.CustomerList.route); onClose()
                }
                DrawerRowItem(label = "Analytics", icon = Icons.Default.BarChart) {
                    navController.navigate(Screen.AnalyticsDashboard.route); onClose()
                }
                DrawerRowItem(label = "Sales", icon = Icons.Default.ShoppingCart) {
                    navController.navigate(Screen.SaleHistory.route); onClose()
                }
                DrawerRowItem(label = "Expenses", icon = Icons.Default.AccountBalanceWallet) {
                    navController.navigate(Screen.ExpenseList.route); onClose()
                }
                DrawerRowItem(label = "Suppliers", icon = Icons.Default.LocalShipping) {
                    navController.navigate(Screen.SupplierList.route); onClose()
                }
                DrawerRowItem(label = "Products", icon = Icons.Default.Category) {
                    navController.navigate(Screen.InventoryList.route); onClose()
                }
                DrawerRowItem(label = "Settings", icon = Icons.Default.Settings) {
                    navController.navigate(Screen.Settings.route); onClose()
                }
                DrawerRowItem(label = "Help & Support", icon = Icons.Default.HelpOutline) {
                    navController.navigate(Screen.HelpCenter.route); onClose()
                }
            }

            // Logout at bottom (red)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0)
                        }
                    }
                    .padding(12.dp)
            ) {
                Icon(Icons.Default.Logout, contentDescription = "Logout", tint = ErrorRed)
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Logout",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = ErrorRed
                )
            }
        }
    }
}

@Composable
private fun DrawerRowItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        color = if (isSelected) LightGreenSurface else Color.Transparent
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) ForestGreen else SecondaryText,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (isSelected) ForestGreen else PrimaryText
            )
        }
    }
}

// end of file
