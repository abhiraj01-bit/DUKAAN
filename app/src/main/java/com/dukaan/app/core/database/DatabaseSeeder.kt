package com.dukaan.app.core.database

import com.dukaan.app.data.local.dao.*
import com.dukaan.app.data.local.entity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseSeeder @Inject constructor(
    private val db: DukaanDatabase,
    private val shopProfileDao: ShopProfileDao,
    private val productDao: ProductDao,
    private val customerDao: CustomerDao,
    private val khataDao: KhataTransactionDao,
    private val invoiceDao: InvoiceDao,
    private val invoiceItemDao: InvoiceItemDao,
    private val stockMovementDao: StockMovementDao,
    private val saleDao: SaleDao,
    private val saleItemDao: SaleItemDao,
    private val expenseDao: ExpenseDao
) {
    /**
     * Seeds sample data ONLY after the user has completed onboarding (shop profile exists)
     * and the products table is empty. This avoids clobbering the user's real onboarding.
     */
    suspend fun seedIfEmpty() = withContext(Dispatchers.IO) {
        // Clean out any legacy mock shop profile with "Sharma General Store"
        val existingShop = shopProfileDao.getShopProfileOnce()
        if (existingShop?.shopName == "Sharma General Store") {
            shopProfileDao.deleteAll()
            return@withContext
        }
        if (existingShop == null) return@withContext

        // Only seed products/customers if DB is truly empty
        val count = productDao.getProductCountOnce()
        if (count > 0) return@withContext

        val now = System.currentTimeMillis()
        val dayMs = 86400000L

        // 1. Products (demo data for new installs)
        val products = listOf(
            ProductEntity(
                id = "p_atta",
                name = "Aashirvaad Atta",
                categoryId = "cat_grains",
                unit = "KG",
                currentStock = 50.0,
                minStockLevel = 10.0,
                purchasePricePaise = 32000L,
                sellingPricePaise = 36000L,
                isActive = true,
                createdAt = now - (dayMs * 20),
                updatedAt = now
            ),
            ProductEntity(
                id = "p_oil",
                name = "Fortune Oil",
                categoryId = "cat_oil",
                unit = "LTR",
                currentStock = 20.0,
                minStockLevel = 5.0,
                purchasePricePaise = 15000L,
                sellingPricePaise = 18000L,
                isActive = true,
                createdAt = now - (dayMs * 20),
                updatedAt = now
            ),
            ProductEntity(
                id = "p_salt",
                name = "Tata Salt",
                categoryId = "cat_spices",
                unit = "KG",
                currentStock = 4.0,
                minStockLevel = 10.0,
                purchasePricePaise = 1600L,
                sellingPricePaise = 2000L,
                isActive = true,
                createdAt = now - (dayMs * 20),
                updatedAt = now
            ),
            ProductEntity(
                id = "p_milk",
                name = "Amul Milk",
                categoryId = "cat_dairy",
                unit = "PCS",
                currentStock = 2.0,
                minStockLevel = 10.0,
                purchasePricePaise = 2400L,
                sellingPricePaise = 2800L,
                isActive = true,
                createdAt = now - (dayMs * 20),
                updatedAt = now
            ),
            ProductEntity(
                id = "p_maggi",
                name = "Maggi Noodles",
                categoryId = "cat_food",
                unit = "PCS",
                currentStock = 40.0,
                minStockLevel = 15.0,
                purchasePricePaise = 1100L,
                sellingPricePaise = 1400L,
                isActive = true,
                createdAt = now - (dayMs * 20),
                updatedAt = now
            ),
            ProductEntity(
                id = "p_bread",
                name = "Britannia Bread",
                categoryId = "cat_dairy",
                unit = "PCS",
                currentStock = 2.0,
                minStockLevel = 8.0,
                purchasePricePaise = 3200L,
                sellingPricePaise = 4000L,
                isActive = true,
                createdAt = now - (dayMs * 20),
                updatedAt = now
            )
        )
        products.forEach { productDao.insertOrUpdate(it) }

        // 3. Stock Movements for Atta
        stockMovementDao.insert(
            StockMovementEntity(
                id = "sm_1",
                productId = "p_atta",
                productName = "Aashirvaad Atta",
                type = "INVOICE_PURCHASE",
                quantityChange = 10.0,
                quantityAfter = 50.0,
                notes = "Invoice #1128",
                createdAt = now - (dayMs * 5)
            )
        )
        stockMovementDao.insert(
            StockMovementEntity(
                id = "sm_2",
                productId = "p_atta",
                productName = "Aashirvaad Atta",
                type = "SALE",
                quantityChange = -5.0,
                quantityAfter = 40.0,
                notes = "Counter Sale",
                createdAt = now - (dayMs * 7)
            )
        )

        // 4. Customers (Total Udhaar = 2450 + 1200 + 860 + 3100 + 590 = ₹8,200!)
        val customers = listOf(
            CustomerEntity(
                id = "c1",
                name = "Ramesh Kumar",
                phoneNumber = "9876543210",
                address = "Sector 21, Chandigarh",
                outstandingPaise = 245000L,
                totalCreditPaise = 450000L,
                totalPaymentsPaise = 205000L,
                createdAt = now - (dayMs * 25)
            ),
            CustomerEntity(
                id = "c2",
                name = "Suresh Patil",
                phoneNumber = "9812345678",
                address = "Sector 22",
                outstandingPaise = 120000L,
                totalCreditPaise = 200000L,
                totalPaymentsPaise = 80000L,
                createdAt = now - (dayMs * 20)
            ),
            CustomerEntity(
                id = "c3",
                name = "Anita Sharma",
                phoneNumber = "9723456789",
                address = "Sector 15",
                outstandingPaise = 86000L,
                totalCreditPaise = 150000L,
                totalPaymentsPaise = 64000L,
                createdAt = now - (dayMs * 18)
            ),
            CustomerEntity(
                id = "c4",
                name = "Vikram Singh",
                phoneNumber = "9898989898",
                address = "Sector 34",
                outstandingPaise = 310000L,
                totalCreditPaise = 500000L,
                totalPaymentsPaise = 190000L,
                createdAt = now - (dayMs * 15)
            ),
            CustomerEntity(
                id = "c5",
                name = "Pooja Gupta",
                phoneNumber = "9711223344",
                address = "Sector 19",
                outstandingPaise = 59000L,
                totalCreditPaise = 100000L,
                totalPaymentsPaise = 41000L,
                createdAt = now - (dayMs * 12)
            )
        )
        customers.forEach { customerDao.insertOrUpdate(it) }

        // 5. Khata Transactions for Ramesh Kumar
        val khataList = listOf(
            KhataTransactionEntity(
                id = "kt_1",
                customerId = "c1",
                type = "CREDIT",
                amountPaise = 50000L,
                notes = "Groceries",
                createdAt = now - (dayMs * 5)
            ),
            KhataTransactionEntity(
                id = "kt_2",
                customerId = "c1",
                type = "PAYMENT",
                amountPaise = 30000L,
                notes = "Cash payment",
                paymentMethod = "CASH",
                createdAt = now - (dayMs * 9)
            ),
            KhataTransactionEntity(
                id = "kt_3",
                customerId = "c1",
                type = "CREDIT",
                amountPaise = 120000L,
                notes = "Monthly ration",
                createdAt = now - (dayMs * 15)
            ),
            KhataTransactionEntity(
                id = "kt_4",
                customerId = "c1",
                type = "CREDIT",
                amountPaise = 105000L,
                notes = "Snacks & oil",
                createdAt = now - (dayMs * 21)
            )
        )
        khataList.forEach { khataDao.insert(it) }

        // 6. Invoices in Vault
        val inv1 = InvoiceEntity(
            id = "inv_1128",
            invoiceNumber = "1128",
            supplierName = "Sharma Traders",
            invoiceDate = now - (dayMs * 5),
            grandTotalPaise = 418000L,
            subtotalPaise = 418000L,
            status = "CONFIRMED",
            createdAt = now - (dayMs * 5),
            confirmedAt = now - (dayMs * 5)
        )
        invoiceDao.insertOrUpdate(inv1)
        invoiceItemDao.insertAll(
            listOf(
                InvoiceItemEntity(
                    id = "ii_1",
                    invoiceId = "inv_1128",
                    productId = "p_atta",
                    productName = "Aashirvaad Atta",
                    quantity = 10.0,
                    purchasePricePaise = 32000L,
                    lineTotalPaise = 320000L
                ),
                InvoiceItemEntity(
                    id = "ii_2",
                    invoiceId = "inv_1128",
                    productId = "p_oil",
                    productName = "Fortune Oil",
                    quantity = 5.0,
                    purchasePricePaise = 18000L,
                    lineTotalPaise = 90000L
                ),
                InvoiceItemEntity(
                    id = "ii_3",
                    invoiceId = "inv_1128",
                    productId = "p_salt",
                    productName = "Tata Salt",
                    quantity = 4.0,
                    purchasePricePaise = 2000L,
                    lineTotalPaise = 8000L
                )
            )
        )

        invoiceDao.insertOrUpdate(
            InvoiceEntity(
                id = "inv_882",
                invoiceNumber = "882",
                supplierName = "Gupta Distributors",
                invoiceDate = now - (dayMs * 9),
                grandTotalPaise = 245000L,
                subtotalPaise = 245000L,
                status = "CONFIRMED",
                createdAt = now - (dayMs * 9),
                confirmedAt = now - (dayMs * 9)
            )
        )

        invoiceDao.insertOrUpdate(
            InvoiceEntity(
                id = "inv_512",
                invoiceNumber = "512",
                supplierName = "Ravi Kirana",
                invoiceDate = now - (dayMs * 13),
                grandTotalPaise = 132000L,
                subtotalPaise = 132000L,
                status = "DRAFT",
                createdAt = now - (dayMs * 13)
            )
        )

        invoiceDao.insertOrUpdate(
            InvoiceEntity(
                id = "inv_104",
                invoiceNumber = "104",
                supplierName = "Aggarwal Wholesale",
                invoiceDate = now - (dayMs * 16),
                grandTotalPaise = 560000L,
                subtotalPaise = 560000L,
                status = "CONFIRMED",
                createdAt = now - (dayMs * 16),
                confirmedAt = now - (dayMs * 16)
            )
        )

        // 7. Sales (Today: ₹12,450; Month: ₹1,24,500; Expenses: ₹90,300; Profit: ₹34,200)
        val todaySale = SaleEntity(
            id = "s_today_1",
            customerId = "c1",
            customerName = "Ramesh Kumar",
            saleDate = now - 3600000L,
            subtotalPaise = 1245000L,
            grandTotalPaise = 1245000L,
            status = "COMPLETED",
            createdAt = now - 3600000L,
            updatedAt = now
        )
        saleDao.insert(todaySale)
        saleItemDao.insert(
            SaleItemEntity(
                id = "si_1",
                saleId = "s_today_1",
                productId = "p_atta",
                productName = "Aashirvaad Atta",
                quantity = 25.0,
                unit = "KG",
                sellingPricePaise = 36000L,
                purchasePricePaise = 32000L,
                lineTotalPaise = 900000L
            )
        )
        saleItemDao.insert(
            SaleItemEntity(
                id = "si_2",
                saleId = "s_today_1",
                productId = "p_oil",
                productName = "Fortune Oil",
                quantity = 15.0,
                unit = "LTR",
                sellingPricePaise = 18000L,
                purchasePricePaise = 15000L,
                lineTotalPaise = 270000L
            )
        )
        saleItemDao.insert(
            SaleItemEntity(
                id = "si_3",
                saleId = "s_today_1",
                productId = "p_salt",
                productName = "Tata Salt",
                quantity = 37.0,
                unit = "KG",
                sellingPricePaise = 2000L,
                purchasePricePaise = 1600L,
                lineTotalPaise = 74000L
            )
        )

        // Additional sales across this month to match ₹1,24,500
        val monthSale = SaleEntity(
            id = "s_month_1",
            customerId = "c4",
            customerName = "Vikram Singh",
            saleDate = now - (dayMs * 10),
            subtotalPaise = 11205000L,
            grandTotalPaise = 11205000L,
            status = "COMPLETED",
            createdAt = now - (dayMs * 10),
            updatedAt = now
        )
        saleDao.insert(monthSale)
        saleItemDao.insert(
            SaleItemEntity(
                id = "si_4",
                saleId = "s_month_1",
                productId = "p_atta",
                productName = "Aashirvaad Atta",
                quantity = 95.0,
                unit = "KG",
                sellingPricePaise = 36000L,
                purchasePricePaise = 32000L,
                lineTotalPaise = 3420000L
            )
        )
        saleItemDao.insert(
            SaleItemEntity(
                id = "si_5",
                saleId = "s_month_1",
                productId = "p_oil",
                productName = "Fortune Oil",
                quantity = 70.0,
                unit = "LTR",
                sellingPricePaise = 18000L,
                purchasePricePaise = 15000L,
                lineTotalPaise = 1260000L
            )
        )
        saleItemDao.insert(
            SaleItemEntity(
                id = "si_6",
                saleId = "s_month_1",
                productId = "p_salt",
                productName = "Tata Salt",
                quantity = 25.0,
                unit = "KG",
                sellingPricePaise = 2000L,
                purchasePricePaise = 1600L,
                lineTotalPaise = 50000L
            )
        )
        saleItemDao.insert(
            SaleItemEntity(
                id = "si_7",
                saleId = "s_month_1",
                productId = "p_maggi",
                productName = "Maggi Noodles",
                quantity = 50.0,
                unit = "PCS",
                sellingPricePaise = 1400L,
                purchasePricePaise = 1100L,
                lineTotalPaise = 70000L
            )
        )
        saleItemDao.insert(
            SaleItemEntity(
                id = "si_8",
                saleId = "s_month_1",
                productId = "p_milk",
                productName = "Amul Milk",
                quantity = 42.0,
                unit = "PCS",
                sellingPricePaise = 2800L,
                purchasePricePaise = 2400L,
                lineTotalPaise = 117600L
            )
        )

        // Expenses matching purchase costs
        expenseDao.insertOrUpdate(
            ExpenseEntity(
                id = "exp_1",
                title = "Wholesale Grocery Purchases",
                category = "PURCHASE",
                amountPaise = 9030000L,
                date = now - (dayMs * 12),
                createdAt = now - (dayMs * 12)
            )
        )
    }
}

