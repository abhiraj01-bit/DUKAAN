package com.dukaan.app.data.repository

import androidx.room.withTransaction
import com.dukaan.app.core.database.DukaanDatabase
import com.dukaan.app.core.util.UuidGenerator
import com.dukaan.app.data.local.dao.*
import com.dukaan.app.data.local.entity.*
import com.dukaan.app.data.local.mapper.*
import com.dukaan.app.domain.model.*
import com.dukaan.app.domain.repository.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InvoiceRepositoryImpl @Inject constructor(
    private val db: DukaanDatabase,
    private val invoiceDao: InvoiceDao,
    private val invoiceItemDao: InvoiceItemDao
) : InvoiceRepository {

    override fun getAllInvoices(): Flow<List<Invoice>> =
        invoiceDao.getAllInvoices().map { list -> list.map { it.toDomain() } }

    override suspend fun getInvoiceById(id: String): Invoice? {
        val entity = invoiceDao.getInvoiceById(id) ?: return null
        val items = invoiceItemDao.getItemsByInvoiceId(id).map { it.toDomain() }
        return entity.toDomain(items)
    }

    override fun getPendingInvoices(): Flow<List<Invoice>> =
        invoiceDao.getPendingInvoices().map { it.map { e -> e.toDomain() } }

    override fun getInvoicesBySupplier(supplierId: String): Flow<List<Invoice>> =
        invoiceDao.getInvoicesBySupplier(supplierId).map { it.map { e -> e.toDomain() } }

    override suspend fun saveInvoice(invoice: Invoice) {
        db.withTransaction {
            invoiceDao.insertOrUpdate(invoice.toEntity())
            invoiceItemDao.deleteByInvoiceId(invoice.id)
            invoiceItemDao.insertAll(invoice.items.map { it.toEntity() })
        }
    }

    override suspend fun updateInvoiceStatus(id: String, status: InvoiceStatus, confirmedAt: Long?) =
        invoiceDao.updateStatus(id, status.name, confirmedAt)

    override suspend fun findDuplicate(invoiceNumber: String, supplierId: String): Invoice? =
        invoiceDao.findDuplicateInvoice(invoiceNumber, supplierId)?.toDomain()
}

@Singleton
class SaleRepositoryImpl @Inject constructor(
    private val db: DukaanDatabase,
    private val saleDao: SaleDao,
    private val saleItemDao: SaleItemDao,
    private val salePaymentDao: SalePaymentDao,
    private val productDao: ProductDao,
    private val stockMovementDao: StockMovementDao,
    private val khataDao: KhataTransactionDao,
    private val customerDao: CustomerDao
) : SaleRepository {

    override fun getAllSales(): Flow<List<Sale>> = saleDao.getAllSales().map { it.map { e -> e.toDomain() } }

    override suspend fun getSaleById(id: String): Sale? {
        val entity = saleDao.getSaleById(id) ?: return null
        val items = saleItemDao.getItemsBySaleId(id).map { it.toDomain() }
        val payments = salePaymentDao.getPaymentsBySaleId(id).map { it.toDomain() }
        return entity.toDomain(items, payments)
    }

    override fun getSalesByCustomer(customerId: String): Flow<List<Sale>> =
        saleDao.getSalesByCustomer(customerId).map { it.map { e -> e.toDomain() } }

    override fun getSalesByDateRange(start: Long, end: Long): Flow<List<Sale>> =
        saleDao.getSalesByDateRange(start, end).map { it.map { e -> e.toDomain() } }

    override fun getTotalSalesPaiseForPeriod(start: Long, end: Long): Flow<Long?> =
        saleDao.getTotalSalesPaiseForPeriod(start, end)

    override fun getSalesCountForPeriod(start: Long, end: Long): Flow<Int> =
        saleDao.getSalesCountForPeriod(start, end)

    override fun getTodaysSalesPaise(date: Long): Flow<Long?> = saleDao.getTodaysSalesPaise(date)

    override suspend fun saveSaleWithDetails(sale: Sale) {
        db.withTransaction {
            // Save sale
            saleDao.insert(SaleEntity(
                id = sale.id, customerId = sale.customerId, customerName = sale.customerName,
                saleDate = sale.saleDate, subtotalPaise = sale.subtotalPaise,
                discountPaise = sale.discountPaise, taxPaise = sale.taxPaise,
                grandTotalPaise = sale.grandTotalPaise, status = sale.status.name,
                notes = sale.notes, createdAt = sale.createdAt, updatedAt = sale.updatedAt
            ))
            // Save items
            saleItemDao.insertAll(sale.items.map { item ->
                SaleItemEntity(
                    id = item.id, saleId = item.saleId, productId = item.productId,
                    productName = item.productName, quantity = item.quantity, unit = item.unit.name,
                    sellingPricePaise = item.sellingPricePaise, purchasePricePaise = item.purchasePricePaise,
                    discountPaise = item.discountPaise, taxPaise = item.taxPaise, lineTotalPaise = item.lineTotalPaise
                )
            })
            // Save payments
            salePaymentDao.insertAll(sale.payments.map { p ->
                SalePaymentEntity(id = p.id, saleId = p.saleId, method = p.method.name, amountPaise = p.amountPaise, reference = p.reference)
            })
            // Deduct inventory + record stock movements
            sale.items.forEach { item ->
                val product = productDao.getProductByIdOnce(item.productId)
                if (product != null) {
                    val newStock = product.currentStock - item.quantity
                    productDao.updateStock(item.productId, newStock)
                    stockMovementDao.insert(StockMovementEntity(
                        id = UuidGenerator.generate(), productId = item.productId,
                        productName = item.productName, type = StockMovementType.SALE.name,
                        quantityChange = -item.quantity, quantityAfter = newStock,
                        referenceId = sale.id, referenceType = "SALE",
                        createdAt = sale.saleDate
                    ))
                }
            }
            // Create Khata entry if credit
            val creditAmount = sale.creditAmountPaise
            if (creditAmount > 0 && sale.customerId != null) {
                val khataId = UuidGenerator.generate()
                khataDao.insert(KhataTransactionEntity(
                    id = khataId, customerId = sale.customerId,
                    type = KhataTransactionType.CREDIT.name, amountPaise = creditAmount,
                    referenceId = sale.id, referenceType = "SALE", notes = "Credit sale",
                    createdAt = sale.saleDate
                ))
                customerDao.updateFinancials(sale.customerId, creditAmount, creditAmount, 0L)
            }
        }
    }

    override suspend fun updateSaleStatus(id: String, status: SaleStatus) =
        saleDao.updateStatus(id, status.name)
}