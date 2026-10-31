package com.dukaan.app.data.local.dao

import androidx.room.*
import com.dukaan.app.data.local.entity.KhataSmsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KhataSmsDao {

    @Query("SELECT * FROM khata_sms_reminders WHERE customer_id = :customerId ORDER BY created_at DESC")
    fun getSmsHistoryByCustomer(customerId: String): Flow<List<KhataSmsEntity>>

    @Query("SELECT * FROM khata_sms_reminders WHERE id = :id")
    suspend fun getSmsById(id: String): KhataSmsEntity?

    @Query("SELECT * FROM khata_sms_reminders WHERE unique_key = :uniqueKey LIMIT 1")
    suspend fun findByUniqueKey(uniqueKey: String): KhataSmsEntity?

    @Query("SELECT * FROM khata_sms_reminders ORDER BY created_at DESC LIMIT 50")
    fun getRecentSms(): Flow<List<KhataSmsEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(sms: KhataSmsEntity)

    @Query("UPDATE khata_sms_reminders SET status = :status, sent_at = :sentAt, error_code = :errorCode, error_message = :errorMessage WHERE id = :id")
    suspend fun updateSentStatus(id: String, status: String, sentAt: Long? = null, errorCode: Int? = null, errorMessage: String? = null)

    @Query("UPDATE khata_sms_reminders SET status = :status, delivered_at = :deliveredAt WHERE id = :id")
    suspend fun updateDeliveredStatus(id: String, status: String, deliveredAt: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM khata_sms_reminders WHERE month_year = :monthYear AND reminder_type = 'MONTHLY_AUTO' AND (status = 'SENT' OR status = 'DELIVERED')")
    suspend fun countSentMonthlyForMonth(monthYear: String): Int
}

