package com.dukaan.app.data.local.dao

import androidx.room.*
import com.dukaan.app.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SaleDao {
    @Query("SELECT * FROM sales ORDER BY sale_date DESC")
    fun getAllSales(): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE id = :id")
    suspend fun getSaleById(id: String): SaleEntity?

    @Query("SELECT * FROM sales WHERE customer_id = :customerId ORDER BY sale_date DESC")
    fun getSalesByCustomer(customerId: String): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE sale_date >= :startDate AND sale_date <= :endDate ORDER BY sale_date DESC")
    fun getSalesByDateRange(startDate: Long, endDate: Long): Flow<List<SaleEntity>>

    @Query("SELECT SUM(grand_total_paise) FROM sales WHERE status = 'CONFIRMED' AND sale_date >= :startDate AND sale_date <= :endDate")
    fun getTotalSalesPaiseForPeriod(startDate: Long, endDate: Long): Flow<Long?>

    @Query("SELECT COUNT(*) FROM sales WHERE status = 'CONFIRMED' AND sale_date >= :startDate AND sale_date <= :endDate")
    fun getSalesCountForPeriod(startDate: Long, endDate: Long): Flow<Int>

    @Query("SELECT SUM(grand_total_paise) FROM sales WHERE status = 'CONFIRMED' AND DATE(sale_date/1000, 'unixepoch', 'localtime') = DATE(:date/1000, 'unixepoch', 'localtime')")
    fun getTodaysSalesPaise(date: Long): Flow<Long?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sale: SaleEntity)

    @Update
    suspend fun update(sale: SaleEntity)

    @Query("UPDATE sales SET status = :status, updated_at = :now WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, now: Long = System.currentTimeMillis())
}

@Dao
interface SaleItemDao {
    @Query("SELECT * FROM sale_items WHERE sale_id = :saleId")
    suspend fun getItemsBySaleId(saleId: String): List<SaleItemEntity>

    @Query("SELECT * FROM sale_items WHERE product_id = :productId ORDER BY sale_id DESC")
    fun getSaleItemsByProduct(productId: String): Flow<List<SaleItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<SaleItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: SaleItemEntity)
}

@Dao
interface SalePaymentDao {
    @Query("SELECT * FROM sale_payments WHERE sale_id = :saleId")
    suspend fun getPaymentsBySaleId(saleId: String): List<SalePaymentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(payments: List<SalePaymentEntity>)
}

@Dao
interface StockMovementDao {
    @Query("SELECT * FROM stock_movements WHERE product_id = :productId ORDER BY created_at DESC")
    fun getMovementsByProduct(productId: String): Flow<List<StockMovementEntity>>

    @Query("SELECT * FROM stock_movements ORDER BY created_at DESC LIMIT :limit")
    fun getRecentMovements(limit: Int = 50): Flow<List<StockMovementEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(movement: StockMovementEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(movements: List<StockMovementEntity>)
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY date DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getExpenseById(id: String): ExpenseEntity?

    @Query("SELECT * FROM expenses WHERE date >= :startDate AND date <= :endDate ORDER BY date DESC")
    fun getExpensesByDateRange(startDate: Long, endDate: Long): Flow<List<ExpenseEntity>>

    @Query("SELECT SUM(amount_paise) FROM expenses WHERE date >= :startDate AND date <= :endDate")
    fun getTotalExpensesPaiseForPeriod(startDate: Long, endDate: Long): Flow<Long?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(expense: ExpenseEntity)

    @Update
    suspend fun update(expense: ExpenseEntity)

    @Delete
    suspend fun delete(expense: ExpenseEntity)
}