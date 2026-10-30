package com.dukaan.app.domain.model

data class Invoice(
    val id: String,
    val invoiceNumber: String = "",
    val supplierId: String? = null,
    val supplierName: String = "",
    val invoiceDate: Long = System.currentTimeMillis(),
    val dueDate: Long? = null,
    val subtotalPaise: Long = 0L,
    val discountPaise: Long = 0L,
    val taxPaise: Long = 0L,
    val grandTotalPaise: Long = 0L,
    val status: InvoiceStatus = InvoiceStatus.DRAFT,
    val imagePath: String? = null,
    val rawOcrText: String? = null,
    val notes: String? = null,
    val items: List<InvoiceItem> = emptyList(),
    val scannedAt: Long = System.currentTimeMillis(),
    val confirmedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class InvoiceItem(
    val id: String,
    val invoiceId: String,
    val productId: String? = null,
    val productName: String,
    val quantity: Double,
    val unit: String? = "pcs",
    val purchasePricePaise: Long = 0L,
    val discountPaise: Long = 0L,
    val taxPaise: Long = 0L,
    val lineTotalPaise: Long = (quantity * purchasePricePaise).toLong()
)

enum class InvoiceStatus {
    DRAFT,
    SCANNED,
    REVIEWED,
    CONFIRMED,
    FAILED
}
