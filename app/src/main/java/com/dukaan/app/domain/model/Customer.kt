package com.dukaan.app.domain.model

data class Customer(
    val id: String,
    val name: String,
    val phoneNumber: String? = null,
    val address: String? = null,
    val notes: String? = null,
    val outstandingPaise: Long = 0L,
    val totalCreditPaise: Long = 0L,
    val totalPaymentsPaise: Long = 0L,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val hasOutstanding: Boolean get() = outstandingPaise > 0
}
