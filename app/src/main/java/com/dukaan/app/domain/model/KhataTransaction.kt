package com.dukaan.app.domain.model

data class KhataTransaction(
    val id: String,
    val customerId: String,
    val customerName: String = "",
    val type: KhataTransactionType,
    val amountPaise: Long,
    val paymentMethod: PaymentMethod? = null,
    val referenceId: String? = null,   // saleId or paymentId
    val notes: String? = null,
    val isReversed: Boolean = false,
    val reversalId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class KhataTransactionType {
    CREDIT,         // Customer bought on credit
    PAYMENT,        // Customer paid
    ADJUSTMENT,     // Manual correction
    REVERSAL        // Reverses another transaction
}
