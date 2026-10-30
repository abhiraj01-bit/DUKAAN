package com.dukaan.app.data.local.dao

import androidx.room.*
import com.dukaan.app.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers WHERE is_active = 1 ORDER BY name ASC")
    fun getAllActiveCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE id = :id")
    fun getCustomerById(id: String): Flow<CustomerEntity?>

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun getCustomerByIdOnce(id: String): CustomerEntity?

    @Query("SELECT * FROM customers WHERE is_active = 1 AND (name LIKE '%' || :query || '%' OR phone_number LIKE '%' || :query || '%') ORDER BY name ASC")
    fun searchCustomers(query: String): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE is_active = 1 AND outstanding_paise > 0 ORDER BY outstanding_paise DESC")
    fun getCustomersWithOutstanding(): Flow<List<CustomerEntity>>

    @Query("SELECT SUM(outstanding_paise) FROM customers WHERE is_active = 1")
    fun getTotalOutstandingPaise(): Flow<Long?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(customer: CustomerEntity)

    @Update
    suspend fun update(customer: CustomerEntity)

    @Query("UPDATE customers SET outstanding_paise = outstanding_paise + :delta, total_credit_paise = total_credit_paise + :creditDelta, total_payments_paise = total_payments_paise + :paymentDelta, updated_at = :now WHERE id = :customerId")
    suspend fun updateFinancials(customerId: String, delta: Long, creditDelta: Long = 0L, paymentDelta: Long = 0L, now: Long = System.currentTimeMillis())

    @Query("UPDATE customers SET is_active = 0 WHERE id = :id")
    suspend fun archiveCustomer(id: String)
}

@Dao
interface KhataTransactionDao {
    @Query("SELECT * FROM khata_transactions WHERE customer_id = :customerId ORDER BY created_at DESC")
    fun getTransactionsByCustomer(customerId: String): Flow<List<KhataTransactionEntity>>

    @Query("SELECT * FROM khata_transactions WHERE customer_id = :customerId AND is_reversed = 0 ORDER BY created_at DESC")
    suspend fun getActiveTransactionsByCustomer(customerId: String): List<KhataTransactionEntity>

    @Query("SELECT * FROM khata_transactions WHERE id = :id")
    suspend fun getTransactionById(id: String): KhataTransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: KhataTransactionEntity)

    @Update
    suspend fun update(transaction: KhataTransactionEntity)

    @Query("UPDATE khata_transactions SET is_reversed = 1, reversal_id = :reversalId, updated_at = :now WHERE id = :id")
    suspend fun markAsReversed(id: String, reversalId: String, now: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM khata_transactions WHERE customer_id = :customerId AND is_reversed = 0")
    fun getTransactionCount(customerId: String): Flow<Int>
}