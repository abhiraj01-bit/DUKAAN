package com.dukaan.app.domain.repository

import com.dukaan.app.data.local.entity.CustomerEntity
import com.dukaan.app.data.local.entity.KhataSmsEntity
import kotlinx.coroutines.flow.Flow

interface KhataSmsRepository {
    fun getSmsHistoryByCustomer(customerId: String): Flow<List<KhataSmsEntity>>
    suspend fun sendManualReminder(customerId: String): Result<KhataSmsEntity>
    suspend fun sendMonthlyReminderToCustomer(customer: CustomerEntity, shopName: String, monthYear: String): Result<KhataSmsEntity>
    suspend fun getCustomersWithOutstanding(): List<CustomerEntity>
}

