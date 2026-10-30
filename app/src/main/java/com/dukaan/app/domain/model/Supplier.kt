package com.dukaan.app.domain.model

data class Supplier(
    val id: String,
    val name: String,
    val contactPerson: String? = null,
    val phoneNumber: String? = null,
    val email: String? = null,
    val address: String? = null,
    val gstNumber: String? = null,
    val notes: String? = null,
    val totalPurchasesPaise: Long = 0L,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
