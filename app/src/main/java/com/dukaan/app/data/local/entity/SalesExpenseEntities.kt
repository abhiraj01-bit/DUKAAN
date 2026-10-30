package com.dukaan.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sales",
    indices = [Index(value = ["customer_id"]), Index(value = ["sale_date"])]
)
data class SaleEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "customer_id")
    val customerId: String? = null,

    @ColumnInfo(name = "customer_name")
    val customerName: String? = null,

    @ColumnInfo(name = "sale_date")
    val saleDate: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "subtotal_paise")
    val subtotalPaise: Long = 0L,

    @ColumnInfo(name = "discount_paise")
    val discountPaise: Long = 0L,

    @ColumnInfo(name = "tax_paise")
    val taxPaise: Long = 0L,

    @ColumnInfo(name = "grand_total_paise")
    val grandTotalPaise: Long = 0L,

    @ColumnInfo(name = "status")
    val status: String = "CONFIRMED",

    @ColumnInfo(name = "notes")
    val notes: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "sale_items",
    foreignKeys = [
        ForeignKey(
            entity = SaleEntity::class,
            parentColumns = ["id"],
            childColumns = ["sale_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sale_id"]), Index(value = ["product_id"])]
)
data class SaleItemEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "sale_id")
    val saleId: String,

    @ColumnInfo(name = "product_id")
    val productId: String,

    @ColumnInfo(name = "product_name")
    val productName: String,

    @ColumnInfo(name = "quantity")
    val quantity: Double,

    @ColumnInfo(name = "unit")
    val unit: String = "PIECES",

    @ColumnInfo(name = "selling_price_paise")
    val sellingPricePaise: Long,

    @ColumnInfo(name = "purchase_price_paise")
    val purchasePricePaise: Long,

    @ColumnInfo(name = "discount_paise")
    val discountPaise: Long = 0L,

    @ColumnInfo(name = "tax_paise")
    val taxPaise: Long = 0L,

    @ColumnInfo(name = "line_total_paise")
    val lineTotalPaise: Long
)

@Entity(
    tableName = "sale_payments",
    foreignKeys = [
        ForeignKey(
            entity = SaleEntity::class,
            parentColumns = ["id"],
            childColumns = ["sale_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sale_id"])]
)
data class SalePaymentEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "sale_id")
    val saleId: String,

    @ColumnInfo(name = "method")
    val method: String,

    @ColumnInfo(name = "amount_paise")
    val amountPaise: Long,

    @ColumnInfo(name = "reference")
    val reference: String? = null
)

@Entity(
    tableName = "stock_movements",
    indices = [
        Index(value = ["product_id"]),
        Index(value = ["reference_id"]),
        Index(value = ["created_at"])
    ]
)
data class StockMovementEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "product_id")
    val productId: String,

    @ColumnInfo(name = "product_name")
    val productName: String,

    @ColumnInfo(name = "type")
    val type: String,

    @ColumnInfo(name = "quantity_change")
    val quantityChange: Double,

    @ColumnInfo(name = "quantity_after")
    val quantityAfter: Double,

    @ColumnInfo(name = "reference_id")
    val referenceId: String? = null,

    @ColumnInfo(name = "reference_type")
    val referenceType: String? = null,

    @ColumnInfo(name = "notes")
    val notes: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "expenses", indices = [Index(value = ["date"])])
data class ExpenseEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "category")
    val category: String,

    @ColumnInfo(name = "amount_paise")
    val amountPaise: Long,

    @ColumnInfo(name = "description")
    val description: String? = null,

    @ColumnInfo(name = "date")
    val date: Long,

    @ColumnInfo(name = "receipt_image_path")
    val receiptImagePath: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)