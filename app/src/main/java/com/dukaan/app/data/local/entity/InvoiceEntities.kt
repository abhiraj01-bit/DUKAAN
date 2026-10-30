package com.dukaan.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "suppliers")
data class SupplierEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "contact_person")
    val contactPerson: String? = null,

    @ColumnInfo(name = "phone_number")
    val phoneNumber: String? = null,

    @ColumnInfo(name = "email")
    val email: String? = null,

    @ColumnInfo(name = "address")
    val address: String? = null,

    @ColumnInfo(name = "gst_number")
    val gstNumber: String? = null,

    @ColumnInfo(name = "notes")
    val notes: String? = null,

    @ColumnInfo(name = "total_purchases_paise")
    val totalPurchasesPaise: Long = 0L,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "invoices",
    indices = [Index(value = ["supplier_id"])]
)
data class InvoiceEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "invoice_number")
    val invoiceNumber: String? = null,

    @ColumnInfo(name = "supplier_id")
    val supplierId: String? = null,

    @ColumnInfo(name = "supplier_name")
    val supplierName: String? = null,

    @ColumnInfo(name = "invoice_date")
    val invoiceDate: Long? = null,

    @ColumnInfo(name = "due_date")
    val dueDate: Long? = null,

    @ColumnInfo(name = "subtotal_paise")
    val subtotalPaise: Long = 0L,

    @ColumnInfo(name = "discount_paise")
    val discountPaise: Long = 0L,

    @ColumnInfo(name = "tax_paise")
    val taxPaise: Long = 0L,

    @ColumnInfo(name = "grand_total_paise")
    val grandTotalPaise: Long = 0L,

    @ColumnInfo(name = "status")
    val status: String = "SCANNED",

    @ColumnInfo(name = "image_path")
    val imagePath: String? = null,

    @ColumnInfo(name = "raw_ocr_text")
    val rawOcrText: String? = null,

    @ColumnInfo(name = "notes")
    val notes: String? = null,

    @ColumnInfo(name = "scanned_at")
    val scannedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "confirmed_at")
    val confirmedAt: Long? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "invoice_items",
    foreignKeys = [
        ForeignKey(
            entity = InvoiceEntity::class,
            parentColumns = ["id"],
            childColumns = ["invoice_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["invoice_id"]), Index(value = ["product_id"])]
)
data class InvoiceItemEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "invoice_id")
    val invoiceId: String,

    @ColumnInfo(name = "product_id")
    val productId: String? = null,

    @ColumnInfo(name = "product_name")
    val productName: String,

    @ColumnInfo(name = "quantity")
    val quantity: Double,

    @ColumnInfo(name = "unit")
    val unit: String? = null,

    @ColumnInfo(name = "purchase_price_paise")
    val purchasePricePaise: Long = 0L,

    @ColumnInfo(name = "discount_paise")
    val discountPaise: Long = 0L,

    @ColumnInfo(name = "tax_paise")
    val taxPaise: Long = 0L,

    @ColumnInfo(name = "line_total_paise")
    val lineTotalPaise: Long = 0L
)