package com.dukaan.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.dukaan.app.data.local.dao.*
import com.dukaan.app.data.local.entity.*

@Database(
    entities = [
        ShopProfileEntity::class,
        ProductCategoryEntity::class,
        ProductEntity::class,
        SupplierEntity::class,
        InvoiceEntity::class,
        InvoiceItemEntity::class,
        CustomerEntity::class,
        KhataTransactionEntity::class,
        SaleEntity::class,
        SaleItemEntity::class,
        SalePaymentEntity::class,
        StockMovementEntity::class,
        ExpenseEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class DukaanDatabase : RoomDatabase() {
    abstract fun shopProfileDao(): ShopProfileDao
    abstract fun productCategoryDao(): ProductCategoryDao
    abstract fun productDao(): ProductDao
    abstract fun supplierDao(): SupplierDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun invoiceItemDao(): InvoiceItemDao
    abstract fun customerDao(): CustomerDao
    abstract fun khataTransactionDao(): KhataTransactionDao
    abstract fun saleDao(): SaleDao
    abstract fun saleItemDao(): SaleItemDao
    abstract fun salePaymentDao(): SalePaymentDao
    abstract fun stockMovementDao(): StockMovementDao
    abstract fun expenseDao(): ExpenseDao

    companion object {
        const val DATABASE_NAME = "dukaan_db"
    }
}