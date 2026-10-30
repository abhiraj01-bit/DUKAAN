package com.dukaan.app.domain.repository

import com.dukaan.app.domain.model.*
import kotlinx.coroutines.flow.Flow

interface ProductRepository {
    fun getAllActiveProducts(): Flow<List<Product>>
    fun getAllProducts(): Flow<List<Product>>
    fun getProductById(id: String): Flow<Product?>
    suspend fun getProductByIdOnce(id: String): Product?
    fun searchProducts(query: String): Flow<List<Product>>
    fun getLowStockProducts(): Flow<List<Product>>
    fun getOutOfStockProducts(): Flow<List<Product>>
    fun getProductsByCategory(categoryId: String): Flow<List<Product>>
    fun getActiveProductCount(): Flow<Int>
    fun getTotalInventoryValuePaise(): Flow<Long?>
    fun getLowStockCount(): Flow<Int>
    fun getOutOfStockCount(): Flow<Int>
    suspend fun saveProduct(product: Product)
    suspend fun updateProduct(product: Product)
    suspend fun archiveProduct(productId: String)
    suspend fun updateStock(productId: String, newStock: Double, newWeightedCost: Long)
}

interface CategoryRepository {
    fun getAllCategories(): Flow<List<ProductCategory>>
    suspend fun getCategoryById(id: String): ProductCategory?
    suspend fun saveCategory(category: ProductCategory)
    suspend fun deleteCategory(category: ProductCategory)
}

interface SupplierRepository {
    fun getAllActiveSuppliers(): Flow<List<Supplier>>
    suspend fun getSupplierById(id: String): Supplier?
    fun searchSuppliers(query: String): Flow<List<Supplier>>
    suspend fun saveSupplier(supplier: Supplier)
    suspend fun archiveSupplier(id: String)
    suspend fun addToPurchasesTotal(id: String, amount: Long)
}

interface InvoiceRepository {
    fun getAllInvoices(): Flow<List<Invoice>>
    suspend fun getInvoiceById(id: String): Invoice?
    fun getPendingInvoices(): Flow<List<Invoice>>
    fun getInvoicesBySupplier(supplierId: String): Flow<List<Invoice>>
    suspend fun saveInvoice(invoice: Invoice)
    suspend fun updateInvoiceStatus(id: String, status: InvoiceStatus, confirmedAt: Long? = null)
    suspend fun findDuplicate(invoiceNumber: String, supplierId: String): Invoice?
}

interface CustomerRepository {
    fun getAllActiveCustomers(): Flow<List<Customer>>
    fun getCustomerById(id: String): Flow<Customer?>
    suspend fun getCustomerByIdOnce(id: String): Customer?
    fun searchCustomers(query: String): Flow<List<Customer>>
    fun getCustomersWithOutstanding(): Flow<List<Customer>>
    fun getTotalOutstandingPaise(): Flow<Long?>
    suspend fun saveCustomer(customer: Customer)
    suspend fun archiveCustomer(id: String)
    suspend fun updateFinancials(customerId: String, delta: Long, creditDelta: Long, paymentDelta: Long)
}

interface KhataRepository {
    fun getTransactionsByCustomer(customerId: String): Flow<List<KhataTransaction>>
    suspend fun getTransactionById(id: String): KhataTransaction?
    suspend fun insertTransaction(transaction: KhataTransaction)
    suspend fun markAsReversed(id: String, reversalId: String)
    fun getTransactionCount(customerId: String): Flow<Int>
}

interface SaleRepository {
    fun getAllSales(): Flow<List<Sale>>
    suspend fun getSaleById(id: String): Sale?
    fun getSalesByCustomer(customerId: String): Flow<List<Sale>>
    fun getSalesByDateRange(start: Long, end: Long): Flow<List<Sale>>
    fun getTotalSalesPaiseForPeriod(start: Long, end: Long): Flow<Long?>
    fun getSalesCountForPeriod(start: Long, end: Long): Flow<Int>
    fun getTodaysSalesPaise(date: Long): Flow<Long?>
    suspend fun saveSaleWithDetails(sale: Sale)
    suspend fun updateSaleStatus(id: String, status: SaleStatus)
}

interface StockMovementRepository {
    fun getMovementsByProduct(productId: String): Flow<List<StockMovement>>
    fun getRecentMovements(limit: Int): Flow<List<StockMovement>>
    suspend fun insertMovement(movement: StockMovement)
    suspend fun insertMovements(movements: List<StockMovement>)
}

interface ExpenseRepository {
    fun getAllExpenses(): Flow<List<Expense>>
    suspend fun getExpenseById(id: String): Expense?
    fun getExpensesByDateRange(start: Long, end: Long): Flow<List<Expense>>
    fun getTotalExpensesPaiseForPeriod(start: Long, end: Long): Flow<Long?>
    suspend fun saveExpense(expense: Expense)
    suspend fun deleteExpense(expense: Expense)
}

interface ShopRepository {
    fun getShopProfile(): Flow<ShopProfile?>
    suspend fun getShopProfileOnce(): ShopProfile?
    suspend fun saveShopProfile(profile: ShopProfile)
}