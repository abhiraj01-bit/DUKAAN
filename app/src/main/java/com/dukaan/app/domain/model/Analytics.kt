package com.dukaan.app.domain.model

data class BusinessSummary(
    val periodLabel: String,
    val totalSalesPaise: Long,
    val totalPurchasesPaise: Long,
    val totalExpensesPaise: Long,
    val grossProfitPaise: Long,
    val estimatedNetProfitPaise: Long,
    val cashCollectedPaise: Long,
    val customerOutstandingPaise: Long,
    val inventoryValuePaise: Long,
    val totalTransactions: Int
)

data class DailySalesPoint(
    val date: Long,
    val salesPaise: Long,
    val transactionCount: Int
)

data class ProductSalesSummary(
    val productId: String,
    val productName: String,
    val quantitySold: Double,
    val unit: StockUnit,
    val revenuePaise: Long,
    val profitPaise: Long
)