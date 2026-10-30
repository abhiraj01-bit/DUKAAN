package com.dukaan.app.domain.model

data class ShopProfile(
    val id: String,
    val shopName: String,
    val ownerName: String,
    val businessType: String,
    val address: String = "",
    val phoneNumber: String = "",
    val email: String? = null,
    val logoImagePath: String? = null,
    val currencyCode: String = "INR",
    val languageCode: String = "en",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
