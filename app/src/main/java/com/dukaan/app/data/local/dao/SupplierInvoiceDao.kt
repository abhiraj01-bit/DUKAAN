package com.dukaan.app.data.local.dao

import androidx.room.*
import com.dukaan.app.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SupplierDao {
    @Query("SELECT * FROM suppliers WHERE is_active = 1 ORDER BY name ASC")
    fun getAllActiveSuppliers(): Flow<List<SupplierEntity>>

    @Query("SELECT * FROM suppliers WHERE id = :id")
    suspend fun getSupplierById(id: String): SupplierEntity?

    @Query("SELECT * FROM suppliers WHERE is_active = 1 AND name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchSuppliers(query: String): Flow<List<SupplierEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(supplier: SupplierEntity)

    @Update
    suspend fun update(supplier: SupplierEntity)

    @Query("UPDATE suppliers SET is_active = 0 WHERE id = :id")
    suspend fun archiveSupplier(id: String)

    @Query("UPDATE suppliers SET total_purchases_paise = total_purchases_paise + :amount WHERE id = :id")
    suspend fun addToPurchasesTotal(id: String, amount: Long)
}

@Dao
interface InvoiceDao {
    @Query("SELECT * FROM invoices ORDER BY created_at DESC")
    fun getAllInvoices(): Flow<List<InvoiceEntity>>

    @Query("SELECT * FROM invoices WHERE id = :id")
    suspend fun getInvoiceById(id: String): InvoiceEntity?

    @Query("SELECT * FROM invoices WHERE status = 'SCANNED' OR status = 'REVIEWED' ORDER BY created_at DESC")
    fun getPendingInvoices(): Flow<List<InvoiceEntity>>

    @Query("SELECT * FROM invoices WHERE supplier_id = :supplierId ORDER BY created_at DESC")
    fun getInvoicesBySupplier(supplierId: String): Flow<List<InvoiceEntity>>

    @Query("SELECT * FROM invoices WHERE invoice_number = :invoiceNumber AND supplier_id = :supplierId AND status = 'CONFIRMED'")
    suspend fun findDuplicateInvoice(invoiceNumber: String, supplierId: String): InvoiceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(invoice: InvoiceEntity)

    @Update
    suspend fun update(invoice: InvoiceEntity)

    @Query("UPDATE invoices SET status = :status, confirmed_at = :confirmedAt, updated_at = :now WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, confirmedAt: Long? = null, now: Long = System.currentTimeMillis())
}

@Dao
interface InvoiceItemDao {
    @Query("SELECT * FROM invoice_items WHERE invoice_id = :invoiceId")
    suspend fun getItemsByInvoiceId(invoiceId: String): List<InvoiceItemEntity>

    @Query("SELECT * FROM invoice_items WHERE invoice_id = :invoiceId")
    fun getItemsByInvoiceIdFlow(invoiceId: String): Flow<List<InvoiceItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<InvoiceItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: InvoiceItemEntity)

    @Update
    suspend fun update(item: InvoiceItemEntity)

    @Delete
    suspend fun delete(item: InvoiceItemEntity)

    @Query("DELETE FROM invoice_items WHERE invoice_id = :invoiceId")
    suspend fun deleteByInvoiceId(invoiceId: String)
}