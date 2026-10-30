package com.dukaan.app.core.navigation

sealed class Screen(val route: String) {
    // 01 to 09: Flow
    object Splash : Screen("screen_01_splash")
    object Onboarding1 : Screen("screen_02_onboarding1")
    object Onboarding2 : Screen("screen_03_onboarding2")
    object Onboarding3 : Screen("screen_04_onboarding3")
    object Onboarding4 : Screen("screen_05_onboarding4")
    object LanguageSelection : Screen("screen_06_language")
    object LanguageSettings : Screen("screen_06_language")
    object Login : Screen("screen_07_login")
    object ShopSetup : Screen("screen_08_shop_setup")
    object SetPin : Screen("screen_09_set_pin?unlock={unlock}") {
        fun createRoute(isUnlock: Boolean = false) = "screen_09_set_pin?unlock=$isUnlock"
    }

    // 10 to 11: Dashboard & Drawer
    object HomeDashboard : Screen("screen_10_dashboard")

    // 12 to 14: Invoices
    object InvoiceCamera : Screen("screen_12_invoice_camera")
    object InvoiceReview : Screen("screen_13_invoice_review/{invoiceId}") {
        fun createRoute(invoiceId: String) = "screen_13_invoice_review/$invoiceId"
    }
    object ExtractedInvoicePreview : Screen("screen_13_invoice_review/{invoiceId}") {
        fun createRoute(invoiceId: String) = "screen_13_invoice_review/$invoiceId"
    }
    object InvoiceHistory : Screen("screen_14_invoice_history")

    // 15 to 17: Inventory & Products
    object AddProduct : Screen("screen_15_add_product")
    object EditProduct : Screen("edit_product/{productId}") {
        fun createRoute(productId: String) = "edit_product/$productId"
    }
    object InventoryList : Screen("screen_16_inventory_list")
    object ProductDetails : Screen("screen_17_product_details/{productId}") {
        fun createRoute(productId: String) = "screen_17_product_details/$productId"
    }
    object ProductDetail : Screen("screen_17_product_details/{productId}") {
        fun createRoute(productId: String) = "screen_17_product_details/$productId"
    }

    // 18 to 21: Customers & Khata
    object AddCustomer : Screen("screen_18_add_customer")
    object EditCustomer : Screen("edit_customer/{customerId}") {
        fun createRoute(customerId: String) = "edit_customer/$customerId"
    }
    object CustomerList : Screen("screen_19_customer_list")
    object CustomerLedger : Screen("screen_20_customer_ledger/{customerId}") {
        fun createRoute(customerId: String) = "screen_20_customer_ledger/$customerId"
    }
    object AddTransaction : Screen("screen_21_add_transaction/{customerId}") {
        fun createRoute(customerId: String) = "screen_21_add_transaction/$customerId"
    }

    // 22 to 24: Analytics & Stock
    object AnalyticsDashboard : Screen("screen_22_analytics_dashboard")
    object ProductPerformance : Screen("screen_23_product_performance")
    object LowStockAlerts : Screen("screen_24_low_stock_alerts")

    // 25: Settings
    object Settings : Screen("screen_25_settings")
    object ShopProfile : Screen("shop_profile")
    object NotificationSettings : Screen("notification_settings")
    object BackupRestore : Screen("backup_restore")
    object HelpCenter : Screen("help_center")
    object About : Screen("about")
    object PrivacyPolicy : Screen("privacy_policy")

    // Legacy/Helper aliases
    object Dashboard : Screen("screen_10_dashboard")
    object Inventory : Screen("screen_16_inventory_list")
    object Khata : Screen("screen_19_customer_list")
    object Analytics : Screen("screen_22_analytics_dashboard")
    object InvoiceScanner : Screen("screen_12_invoice_camera")
    object NewSale : Screen("new_sale")
    object SaleHistory : Screen("sale_history")
    object SaleDetail : Screen("sale_detail/{saleId}") {
        fun createRoute(saleId: String) = "sale_detail/$saleId"
    }
    object SupplierList : Screen("supplier_list")
    object AddSupplier : Screen("add_supplier")
    object SupplierDetail : Screen("supplier_detail/{supplierId}") {
        fun createRoute(supplierId: String) = "supplier_detail/$supplierId"
    }
    object ExpenseList : Screen("expense_list")
    object AddExpense : Screen("add_expense")
    object StockMovement : Screen("stock_movement/{productId}") {
        fun createRoute(productId: String) = "stock_movement/$productId"
    }
    object Categories : Screen("categories")
    object LowStock : Screen("screen_24_low_stock_alerts")
    object InvoiceDetail : Screen("invoice_detail/{invoiceId}") {
        fun createRoute(invoiceId: String) = "invoice_detail/$invoiceId"
    }
    object CustomerDetail : Screen("screen_20_customer_ledger/{customerId}") {
        fun createRoute(customerId: String) = "screen_20_customer_ledger/$customerId"
    }
    object AddKhataCredit : Screen("screen_21_add_transaction/{customerId}") {
        fun createRoute(customerId: String) = "screen_21_add_transaction/$customerId"
    }
    object RecordPayment : Screen("screen_21_add_transaction/{customerId}") {
        fun createRoute(customerId: String) = "screen_21_add_transaction/$customerId"
    }
}