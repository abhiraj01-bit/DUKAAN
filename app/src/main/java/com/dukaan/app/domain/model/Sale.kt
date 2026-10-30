package com.dukaan.app.domain.model

data class Sale(
    val id: String,
    val customerId: String? = null,
    val customerName: String? = null,
    val saleDate: Long = System.currentTimeMillis(),
    val subtotalPaise: Long = 0L,
    val discountPaise: Long = 0L,
    val taxPaise: Long = 0L,
    val grandTotalPaise: Long = 0L,
    val status: SaleStatus = SaleStatus.COMPLETED,
    val notes: String? = null,
    val items: List<SaleItem> = emptyList(),
    val payments: List<SalePayment> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val cashAmountPaise: Long get() = payments.filter { it.method == PaymentMethod.CASH }.sumOf { it.amountPaise }
    val upiAmountPaise: Long get() = payments.filter { it.method == PaymentMethod.UPI }.sumOf { it.amountPaise }
    val creditAmountPaise: Long get() = grandTotalPaise - cashAmountPaise - upiAmountPaise -
        payments.filter { it.method != PaymentMethod.CASH && it.method != PaymentMethod.UPI }.sumOf { it.amountPaise }
}

data class SaleItem(
    val id: String,
    val saleId: String,
    val productId: String,
    val productName: String,
    val quantity: Double,
    val unit: StockUnit = StockUnit.PCS,
    val sellingPricePaise: Long,
    val purchasePricePaise: Long = 0L,
    val discountPaise: Long = 0L,
    val taxPaise: Long = 0L,
    val lineTotalPaise: Long
)

data class SalePayment(
    val id: String,
    val saleId: String,
    val method: PaymentMethod,
    val amountPaise: Long,
    val reference: String? = null
)

enum class SaleStatus {
    PENDING,
    CONFIRMED,
    COMPLETED,
    RETURNED,
    CANCELLED
}

enum class PaymentMethod(val displayName: String) {
    CASH("Cash"),
    UPI("UPI"),
    CARD("Card"),
    CHEQUE("Cheque"),
    BANK_TRANSFER("Bank Transfer"),
    OTHER("Other")
}
