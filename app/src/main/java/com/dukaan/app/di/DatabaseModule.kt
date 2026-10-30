package com.dukaan.app.di

import android.content.Context
import androidx.room.Room
import com.dukaan.app.core.database.DukaanDatabase
import com.dukaan.app.data.local.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDukaanDatabase(@ApplicationContext context: Context): DukaanDatabase =
        Room.databaseBuilder(
            context,
            DukaanDatabase::class.java,
            DukaanDatabase.DATABASE_NAME
        )
        .fallbackToDestructiveMigration()
        .build()

    @Provides fun provideShopProfileDao(db: DukaanDatabase) = db.shopProfileDao()
    @Provides fun provideProductCategoryDao(db: DukaanDatabase) = db.productCategoryDao()
    @Provides fun provideProductDao(db: DukaanDatabase) = db.productDao()
    @Provides fun provideSupplierDao(db: DukaanDatabase) = db.supplierDao()
    @Provides fun provideInvoiceDao(db: DukaanDatabase) = db.invoiceDao()
    @Provides fun provideInvoiceItemDao(db: DukaanDatabase) = db.invoiceItemDao()
    @Provides fun provideCustomerDao(db: DukaanDatabase) = db.customerDao()
    @Provides fun provideKhataTransactionDao(db: DukaanDatabase) = db.khataTransactionDao()
    @Provides fun provideSaleDao(db: DukaanDatabase) = db.saleDao()
    @Provides fun provideSaleItemDao(db: DukaanDatabase) = db.saleItemDao()
    @Provides fun provideSalePaymentDao(db: DukaanDatabase) = db.salePaymentDao()
    @Provides fun provideStockMovementDao(db: DukaanDatabase) = db.stockMovementDao()
    @Provides fun provideExpenseDao(db: DukaanDatabase) = db.expenseDao()
}