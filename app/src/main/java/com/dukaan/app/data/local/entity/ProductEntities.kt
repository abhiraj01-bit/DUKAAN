package com.dukaan.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "shop_profile")
data class ShopProfileEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "shop_name")
    val shopName: String,

    @ColumnInfo(name = "owner_name")
    val ownerName: String,

    @ColumnInfo(name = "business_type")
    val businessType: String,

    @ColumnInfo(name = "address")
    val address: String = "",

    @ColumnInfo(name = "phone_number")
    val phoneNumber: String = "",

    @ColumnInfo(name = "email")
    val email: String = "",

    @ColumnInfo(name = "logo_image_path")
    val logoImagePath: String? = null,

    @ColumnInfo(name = "currency_code")
    val currencyCode: String = "INR",

    @ColumnInfo(name = "language_code")
    val languageCode: String = "en",

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "product_categories",
    indices = [Index(value = ["name"], unique = true)]
)
data class ProductCategoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "description")
    val description: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "products",
    indices = [
        Index(value = ["category_id"]),
        Index(value = ["supplier_id"]),
        Index(value = ["barcode"]),
        Index(value = ["sku"])
    ]
)
data class ProductEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "description")
    val description: String? = null,

    @ColumnInfo(name = "category_id")
    val categoryId: String? = null,

    @ColumnInfo(name = "sku")
    val sku: String? = null,

    @ColumnInfo(name = "barcode")
    val barcode: String? = null,

    @ColumnInfo(name = "purchase_price_paise")
    val purchasePricePaise: Long = 0L,

    @ColumnInfo(name = "selling_price_paise")
    val sellingPricePaise: Long = 0L,

    @ColumnInfo(name = "current_stock")
    val currentStock: Double = 0.0,

    @ColumnInfo(name = "min_stock_level")
    val minStockLevel: Double = 0.0,

    @ColumnInfo(name = "unit")
    val unit: String = "PIECES",

    @ColumnInfo(name = "supplier_id")
    val supplierId: String? = null,

    @ColumnInfo(name = "image_path")
    val imagePath: String? = null,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,

    @ColumnInfo(name = "weighted_avg_cost_paise")
    val weightedAvgCostPaise: Long = 0L,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)