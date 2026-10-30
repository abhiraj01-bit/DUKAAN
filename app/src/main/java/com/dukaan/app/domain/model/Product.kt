package com.dukaan.app.domain.model

data class Product(
    val id: String,
    val name: String,
    val description: String? = null,
    val categoryId: String? = null,
    val categoryName: String? = null,
    val sku: String? = null,
    val barcode: String? = null,
    val purchasePricePaise: Long = 0L,
    val sellingPricePaise: Long = 0L,
    val currentStock: Double = 0.0,
    val minStockLevel: Double = 5.0,
    val unit: StockUnit = StockUnit.PCS,
    val supplierId: String? = null,
    val supplierName: String? = null,
    val imagePath: String? = null,
    val weightedAvgCostPaise: Long = purchasePricePaise,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val isLowStock: Boolean get() = currentStock in 0.01..minStockLevel
    val isOutOfStock: Boolean get() = currentStock <= 0.0
    val profitMarginPaise: Long get() = sellingPricePaise - purchasePricePaise
    val profitMarginPercent: Double get() =
        if (purchasePricePaise > 0) (profitMarginPaise.toDouble() / purchasePricePaise) * 100 else 0.0
}

data class ProductCategory(
    val id: String,
    val name: String,
    val description: String? = null,
    val productCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class StockUnit(val displayName: String, val shortName: String) {
    PCS("Pieces", "pcs"),
    PIECES("Pieces", "pcs"),
    KG("Kilograms", "kg"),
    KILOGRAMS("Kilograms", "kg"),
    GM("Grams", "g"),
    GRAMS("Grams", "g"),
    LTR("Litres", "L"),
    LITRES("Litres", "L"),
    ML("Millilitres", "mL"),
    MILLILITRES("Millilitres", "mL"),
    PKT("Packets", "pkt"),
    PACKETS("Packets", "pkt"),
    BOX("Boxes", "box"),
    BOXES("Boxes", "box"),
    DOZ("Dozens", "doz"),
    DOZENS("Dozens", "doz"),
    MTR("Metres", "m"),
    METRES("Metres", "m"),
    OTHER("Other", "unit");

    val symbol: String get() = shortName
    val label: String get() = displayName
}
