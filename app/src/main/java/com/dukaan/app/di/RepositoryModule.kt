package com.dukaan.app.di

import com.dukaan.app.data.repository.*
import com.dukaan.app.domain.repository.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton abstract fun bindShopRepository(impl: ShopRepositoryImpl): ShopRepository
    @Binds @Singleton abstract fun bindCategoryRepository(impl: CategoryRepositoryImpl): CategoryRepository
    @Binds @Singleton abstract fun bindProductRepository(impl: ProductRepositoryImpl): ProductRepository
    @Binds @Singleton abstract fun bindSupplierRepository(impl: SupplierRepositoryImpl): SupplierRepository
    @Binds @Singleton abstract fun bindInvoiceRepository(impl: InvoiceRepositoryImpl): InvoiceRepository
    @Binds @Singleton abstract fun bindCustomerRepository(impl: CustomerRepositoryImpl): CustomerRepository
    @Binds @Singleton abstract fun bindKhataRepository(impl: KhataRepositoryImpl): KhataRepository
    @Binds @Singleton abstract fun bindSaleRepository(impl: SaleRepositoryImpl): SaleRepository
    @Binds @Singleton abstract fun bindStockMovementRepository(impl: StockMovementRepositoryImpl): StockMovementRepository
    @Binds @Singleton abstract fun bindExpenseRepository(impl: ExpenseRepositoryImpl): ExpenseRepository
    @Binds @Singleton abstract fun bindKhataSmsRepository(impl: KhataSmsRepositoryImpl): KhataSmsRepository
}