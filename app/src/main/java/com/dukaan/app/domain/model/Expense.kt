package com.dukaan.app.domain.model

data class Expense(
    val id: String,
    val title: String,
    val category: ExpenseCategory,
    val amountPaise: Long,
    val description: String? = null,
    val date: Long = System.currentTimeMillis(),
    val receiptImagePath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class ExpenseCategory(val displayName: String) {
    SHOP_RENT("Rent"),
    RENT("Rent"),
    ELECTRICITY_AND_UTILITIES("Electricity & Utilities"),
    ELECTRICITY("Electricity"),
    TRANSPORT_AND_FREIGHT("Transport & Freight"),
    TRANSPORT("Transport"),
    PACKAGING("Packaging"),
    TEA_AND_REFRESHMENTS("Tea & Refreshments"),
    SALARIES("Salaries"),
    MAINTENANCE("Maintenance"),
    MARKETING("Marketing"),
    TAXES("Taxes & Fees"),
    INSURANCE("Insurance"),
    OTHER("Other")
}
