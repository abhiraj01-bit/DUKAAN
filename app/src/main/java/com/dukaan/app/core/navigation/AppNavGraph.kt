package com.dukaan.app.core.navigation

import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.*
import androidx.navigation.compose.*
import com.dukaan.app.core.datastore.DukaanPreferences
import com.dukaan.app.feature.analytics.*
import com.dukaan.app.feature.backup.BackupRestoreScreen
import com.dukaan.app.feature.customers.*
import com.dukaan.app.feature.dashboard.DashboardScreen
import com.dukaan.app.feature.expenses.*
import com.dukaan.app.feature.inventory.*
import com.dukaan.app.feature.invoice.*
import com.dukaan.app.feature.khata.*
import com.dukaan.app.feature.onboarding.*
import com.dukaan.app.feature.sales.*
import com.dukaan.app.feature.settings.*
import com.dukaan.app.feature.suppliers.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    prefs: DukaanPreferences
) : ViewModel() {
    val isOnboardingDone = prefs.isOnboardingDone.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), null as Boolean?
    )
    val appPin = prefs.appPin.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), ""
    )
}

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route
    ) {
        // =================== ROW 1: SCREENS 01 TO 09 ===================
        composable(Screen.Splash.route) {
            SplashScreen(navController = navController)
        }
        composable(Screen.Onboarding1.route) {
            Onboarding1Screen(navController = navController)
        }
        composable(Screen.Onboarding2.route) {
            Onboarding2Screen(navController = navController)
        }
        composable(Screen.Onboarding3.route) {
            Onboarding3Screen(navController = navController)
        }
        composable(Screen.Onboarding4.route) {
            Onboarding4Screen(navController = navController)
        }
        composable(Screen.LanguageSelection.route) {
            LanguageSelectionScreen(navController = navController)
        }
        composable(Screen.Login.route) {
            LoginScreen(navController = navController)
        }
        composable(Screen.ShopSetup.route) {
            ReferenceShopSetupScreen(navController = navController)
        }
        composable(
            route = Screen.SetPin.route,
            arguments = listOf(
                navArgument("unlock") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            val isUnlock = backStackEntry.arguments?.getBoolean("unlock") ?: false
            SetPinScreen(navController = navController, isUnlockMode = isUnlock)
        }

        // =================== ROW 2: SCREENS 10 TO 17 ===================
        // SCREEN 10 (Dashboard) & SCREEN 11 (Side Menu Drawer)
        composable(Screen.HomeDashboard.route) {
            MainScreen(navController = navController, startTab = 0)
        }

        // SCREEN 12: Invoice Scanner Camera
        composable(Screen.InvoiceCamera.route) {
            InvoiceCameraScreen(navController = navController)
        }

        // SCREEN 13: Extracted Invoice Preview
        composable(
            route = Screen.InvoiceReview.route,
            arguments = listOf(navArgument("invoiceId") { type = NavType.StringType })
        ) { backStackEntry ->
            val invoiceId = backStackEntry.arguments?.getString("invoiceId") ?: ""
            ExtractedInvoicePreviewScreen(navController = navController, invoiceId = invoiceId)
        }

        // SCREEN 14: Invoice History
        composable(Screen.InvoiceHistory.route) {
            InvoiceHistoryScreen(navController = navController)
        }

        // SCREEN 15: Add Product
        composable(Screen.AddProduct.route) {
            ReferenceAddProductScreen(navController = navController)
        }

        // SCREEN 16: Inventory List
        composable(Screen.InventoryList.route) {
            ReferenceInventoryListScreen(navController = navController)
        }

        // SCREEN 17: Product Details
        composable(
            route = Screen.ProductDetails.route,
            arguments = listOf(navArgument("productId") { type = NavType.StringType })
        ) { backStackEntry ->
            val productId = backStackEntry.arguments?.getString("productId") ?: "atta"
            ReferenceProductDetailsScreen(navController = navController, productId = productId)
        }

        // =================== ROW 3: SCREENS 18 TO 25 ===================
        // SCREEN 18: Add Customer
        composable(Screen.AddCustomer.route) {
            ReferenceAddCustomerScreen(navController = navController)
        }

        // SCREEN 19: Customer List
        composable(Screen.CustomerList.route) {
            ReferenceCustomerListScreen(navController = navController)
        }

        // SCREEN 20: Customer Ledger
        composable(
            route = Screen.CustomerLedger.route,
            arguments = listOf(navArgument("customerId") { type = NavType.StringType })
        ) { backStackEntry ->
            val customerId = backStackEntry.arguments?.getString("customerId") ?: "c1"
            ReferenceCustomerLedgerScreen(navController = navController, customerId = customerId)
        }

        // SCREEN 21: Add Credit / Payment (Add Transaction)
        composable(
            route = Screen.AddTransaction.route,
            arguments = listOf(navArgument("customerId") { type = NavType.StringType })
        ) { backStackEntry ->
            val customerId = backStackEntry.arguments?.getString("customerId") ?: "c1"
            ReferenceAddTransactionScreen(navController = navController, customerId = customerId)
        }

        // SCREEN 22: Analytics Dashboard
        composable(Screen.AnalyticsDashboard.route) {
            ReferenceAnalyticsDashboardScreen(navController = navController)
        }

        // SCREEN 23: Product Performance
        composable(Screen.ProductPerformance.route) {
            ReferenceProductPerformanceScreen(navController = navController)
        }

        // SCREEN 24: Low Stock Alerts
        composable(Screen.LowStockAlerts.route) {
            ReferenceLowStockAlertsScreen(navController = navController)
        }

        // SCREEN 25: Settings
        composable(Screen.Settings.route) {
            ReferenceSettingsScreen(navController = navController)
        }

        // =================== SUPPORTING & SYSTEM ROUTES ===================
        composable(Screen.NewSale.route) {
            NewSaleScreen(navController = navController)
        }
        composable(Screen.SaleHistory.route) {
            SaleHistoryScreen(navController = navController)
        }
        composable(
            route = Screen.SaleDetail.route,
            arguments = listOf(navArgument("saleId") { type = NavType.StringType })
        ) { backStackEntry ->
            val saleId = backStackEntry.arguments?.getString("saleId") ?: ""
            SaleDetailScreen(navController = navController, saleId = saleId)
        }
        composable(Screen.SupplierList.route) {
            SupplierListScreen(navController = navController)
        }
        composable(Screen.AddSupplier.route) {
            AddEditSupplierScreen(navController = navController)
        }
        composable(Screen.ExpenseList.route) {
            ExpenseListScreen(navController = navController)
        }
        composable(Screen.AddExpense.route) {
            AddEditExpenseScreen(navController = navController)
        }
        composable(Screen.BackupRestore.route) {
            BackupRestoreScreen(navController = navController)
        }
        composable(Screen.ShopProfile.route) {
            ShopProfileScreen(navController = navController)
        }
        composable(Screen.NotificationSettings.route) {
            NotificationSettingsScreen(navController = navController)
        }
        composable(Screen.HelpCenter.route) {
            HelpCenterScreen(navController = navController)
        }
        composable(Screen.About.route) {
            AboutScreen(navController = navController)
        }
        composable(Screen.PrivacyPolicy.route) {
            PrivacyPolicyScreen(navController = navController)
        }
        composable(
            route = Screen.StockMovement.route,
            arguments = listOf(navArgument("productId") { type = NavType.StringType })
        ) { backStackEntry ->
            val productId = backStackEntry.arguments?.getString("productId") ?: ""
            StockMovementScreen(navController = navController, productId = productId)
        }
    }
}
