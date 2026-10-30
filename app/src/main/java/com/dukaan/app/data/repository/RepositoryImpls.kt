package com.dukaan.app.data.repository

import com.dukaan.app.data.local.dao.*
import com.dukaan.app.data.local.mapper.*
import com.dukaan.app.domain.model.*
import com.dukaan.app.domain.repository.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShopRepositoryImpl @Inject constructor(
    private val shopProfileDao: ShopProfileDao
) : ShopRepository {
    override fun getShopProfile(): Flow<ShopProfile?> = shopProfileDao.getShopProfile().map { it?.toDomain() }
    override suspend fun getShopProfileOnce(): ShopProfile? = shopProfileDao.getShopProfileOnce()?.toDomain()
    override suspend fun saveShopProfile(profile: ShopProfile) {
        shopProfileDao.deleteAll()
        shopProfileDao.insertOrUpdate(profile.copy(id = "main_shop").toEntity())
    }
}

@Singleton
class CategoryRepositoryImpl @Inject constructor(
    private val categoryDao: ProductCategoryDao
) : CategoryRepository {
    override fun getAllCategories(): Flow<List<ProductCategory>> = categoryDao.getAllCategories().map { list -> list.map { it.toDomain() } }
    override suspend fun getCategoryById(id: String): ProductCategory? = categoryDao.getCategoryById(id)?.toDomain()
    override suspend fun saveCategory(category: ProductCategory) = categoryDao.insertOrUpdate(category.toEntity())
    override suspend fun deleteCategory(category: ProductCategory) = categoryDao.delete(category.toEntity())
}

@Singleton
class ProductRepositoryImpl @Inject constructor(
    private val productDao: ProductDao
) : ProductRepository {
    override fun getAllActiveProducts(): Flow<List<Product>> = productDao.getAllActiveProducts().map { it.map { e -> e.toDomain() } }
    override fun getAllProducts(): Flow<List<Product>> = productDao.getAllProducts().map { it.map { e -> e.toDomain() } }
    override fun getProductById(id: String): Flow<Product?> = productDao.getProductById(id).map { it?.toDomain() }
    override suspend fun getProductByIdOnce(id: String): Product? = productDao.getProductByIdOnce(id)?.toDomain()
    override fun searchProducts(query: String): Flow<List<Product>> = productDao.searchProducts(query).map { it.map { e -> e.toDomain() } }
    override fun getLowStockProducts(): Flow<List<Product>> = productDao.getLowStockProducts().map { it.map { e -> e.toDomain() } }
    override fun getOutOfStockProducts(): Flow<List<Product>> = productDao.getOutOfStockProducts().map { it.map { e -> e.toDomain() } }
    override fun getProductsByCategory(categoryId: String): Flow<List<Product>> = productDao.getProductsByCategory(categoryId).map { it.map { e -> e.toDomain() } }
    override fun getActiveProductCount(): Flow<Int> = productDao.getActiveProductCount()
    override fun getTotalInventoryValuePaise(): Flow<Long?> = productDao.getTotalInventoryValuePaise()
    override fun getLowStockCount(): Flow<Int> = productDao.getLowStockCount()
    override fun getOutOfStockCount(): Flow<Int> = productDao.getOutOfStockCount()
    override suspend fun saveProduct(product: Product) = productDao.insertOrUpdate(product.toEntity())
    override suspend fun updateProduct(product: Product) = productDao.update(product.toEntity())
    override suspend fun archiveProduct(productId: String) = productDao.archiveProduct(productId)
    override suspend fun updateStock(productId: String, newStock: Double, newWeightedCost: Long) =
        productDao.updateStockAndCost(productId, newStock, newWeightedCost)
}

@Singleton
class SupplierRepositoryImpl @Inject constructor(
    private val supplierDao: SupplierDao
) : SupplierRepository {
    override fun getAllActiveSuppliers(): Flow<List<Supplier>> = supplierDao.getAllActiveSuppliers().map { it.map { e -> e.toDomain() } }
    override suspend fun getSupplierById(id: String): Supplier? = supplierDao.getSupplierById(id)?.toDomain()
    override fun searchSuppliers(query: String): Flow<List<Supplier>> = supplierDao.searchSuppliers(query).map { it.map { e -> e.toDomain() } }
    override suspend fun saveSupplier(supplier: Supplier) = supplierDao.insertOrUpdate(supplier.toEntity())
    override suspend fun archiveSupplier(id: String) = supplierDao.archiveSupplier(id)
    override suspend fun addToPurchasesTotal(id: String, amount: Long) = supplierDao.addToPurchasesTotal(id, amount)
}

@Singleton
class CustomerRepositoryImpl @Inject constructor(
    private val customerDao: CustomerDao
) : CustomerRepository {
    override fun getAllActiveCustomers(): Flow<List<Customer>> = customerDao.getAllActiveCustomers().map { it.map { e -> e.toDomain() } }
    override fun getCustomerById(id: String): Flow<Customer?> = customerDao.getCustomerById(id).map { it?.toDomain() }
    override suspend fun getCustomerByIdOnce(id: String): Customer? = customerDao.getCustomerByIdOnce(id)?.toDomain()
    override fun searchCustomers(query: String): Flow<List<Customer>> = customerDao.searchCustomers(query).map { it.map { e -> e.toDomain() } }
    override fun getCustomersWithOutstanding(): Flow<List<Customer>> = customerDao.getCustomersWithOutstanding().map { it.map { e -> e.toDomain() } }
    override fun getTotalOutstandingPaise(): Flow<Long?> = customerDao.getTotalOutstandingPaise()
    override suspend fun saveCustomer(customer: Customer) = customerDao.insertOrUpdate(customer.toEntity())
    override suspend fun archiveCustomer(id: String) = customerDao.archiveCustomer(id)
    override suspend fun updateFinancials(customerId: String, delta: Long, creditDelta: Long, paymentDelta: Long) =
        customerDao.updateFinancials(customerId, delta, creditDelta, paymentDelta)
}

@Singleton
class KhataRepositoryImpl @Inject constructor(
    private val khataDao: KhataTransactionDao,
    private val customerDao: CustomerDao
) : KhataRepository {
    override fun getTransactionsByCustomer(customerId: String): Flow<List<KhataTransaction>> =
        khataDao.getTransactionsByCustomer(customerId).map { list ->
            list.map { it.toDomain() }
        }
    override suspend fun getTransactionById(id: String): KhataTransaction? = khataDao.getTransactionById(id)?.toDomain()
    override suspend fun insertTransaction(transaction: KhataTransaction) = khataDao.insert(transaction.toEntity())
    override suspend fun markAsReversed(id: String, reversalId: String) = khataDao.markAsReversed(id, reversalId)
    override fun getTransactionCount(customerId: String): Flow<Int> = khataDao.getTransactionCount(customerId)
}

@Singleton
class StockMovementRepositoryImpl @Inject constructor(
    private val stockMovementDao: StockMovementDao
) : StockMovementRepository {
    override fun getMovementsByProduct(productId: String): Flow<List<StockMovement>> =
        stockMovementDao.getMovementsByProduct(productId).map { it.map { e -> e.toDomain() } }
    override fun getRecentMovements(limit: Int): Flow<List<StockMovement>> =
        stockMovementDao.getRecentMovements(limit).map { it.map { e -> e.toDomain() } }
    override suspend fun insertMovement(movement: StockMovement) = stockMovementDao.insert(movement.toEntity())
    override suspend fun insertMovements(movements: List<StockMovement>) = stockMovementDao.insertAll(movements.map { it.toEntity() })
}

@Singleton
class ExpenseRepositoryImpl @Inject constructor(
    private val expenseDao: ExpenseDao
) : ExpenseRepository {
    override fun getAllExpenses(): Flow<List<Expense>> = expenseDao.getAllExpenses().map { it.map { e -> e.toDomain() } }
    override suspend fun getExpenseById(id: String): Expense? = expenseDao.getExpenseById(id)?.toDomain()
    override fun getExpensesByDateRange(start: Long, end: Long): Flow<List<Expense>> = expenseDao.getExpensesByDateRange(start, end).map { it.map { e -> e.toDomain() } }
    override fun getTotalExpensesPaiseForPeriod(start: Long, end: Long): Flow<Long?> = expenseDao.getTotalExpensesPaiseForPeriod(start, end)
    override suspend fun saveExpense(expense: Expense) = expenseDao.insertOrUpdate(expense.toEntity())
    override suspend fun deleteExpense(expense: Expense) = expenseDao.delete(expense.toEntity())
}