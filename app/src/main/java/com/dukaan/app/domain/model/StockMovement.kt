package com.dukaan.app.domain.model

data class StockMovement(
    val id: String,
    val productId: String,
    val productName: String,
    val type: StockMovementType,
    val quantityChange: Double,
    val quantityAfter: Double,
    val referenceId: String? = null,
    val referenceType: String? = null,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

enum class StockMovementType(val displayName: String) {
    PURCHASE("Purchase"),
    INVOICE_PURCHASE("Purchase"),
    SALE("Sale"),
    SALE_RETURN("Sale Return"),
    PURCHASE_RETURN("Purchase Return"),
    DAMAGE("Damaged"),
    DAMAGE_OR_LOSS("Damaged"),
    ADJUSTMENT("Manual Adjustment"),
    MANUAL_ADD("Manual Addition"),
    RECONCILIATION("Reconciliation"),
    OPENING_STOCK("Opening Stock"),
    INITIAL_STOCK("Opening Stock")
}
