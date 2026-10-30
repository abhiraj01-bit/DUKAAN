package com.dukaan.app.data.local.mapper

import com.dukaan.app.data.local.entity.*
import com.dukaan.app.domain.model.*

// ====== SHOP PROFILE ======
fun ShopProfileEntity.toDomain() = ShopProfile(
    id = id, shopName = shopName, ownerName = ownerName, businessType = businessType,
    address = address, phoneNumber = phoneNumber, email = email, logoImagePath = logoImagePath,
    currencyCode = currencyCode, languageCode = languageCode, createdAt = createdAt, updatedAt = updatedAt
)
fun ShopProfile.toEntity() = ShopProfileEntity(
    id = id, shopName = shopName, ownerName = ownerName, businessType = businessType,
    address = address, phoneNumber = phoneNumber, email = email ?: "", logoImagePath = logoImagePath,
    currencyCode = currencyCode, languageCode = languageCode, createdAt = createdAt, updatedAt = updatedAt
)

// ====== PRODUCT CATEGORY ======
fun ProductCategoryEntity.toDomain() = ProductCategory(
    id = id, name = name, description = description, createdAt = createdAt, updatedAt = updatedAt
)
fun ProductCategory.toEntity() = ProductCategoryEntity(
    id = id, name = name, description = description, createdAt = createdAt, updatedAt = updatedAt
)

// ====== PRODUCT ======
fun ProductEntity.toDomain(categoryName: String? = null, supplierName: String? = null) = Product(
    id = id, name = name, description = description, categoryId = categoryId,
    categoryName = categoryName, sku = sku, barcode = barcode,
    purchasePricePaise = purchasePricePaise, sellingPricePaise = sellingPricePaise,
    currentStock = currentStock, minStockLevel = minStockLevel,
    unit = StockUnit.valueOf(unit), supplierId = supplierId, supplierName = supplierName,
    imagePath = imagePath, isActive = isActive, createdAt = createdAt, updatedAt = updatedAt
)
fun Product.toEntity() = ProductEntity(
    id = id, name = name, description = description, categoryId = categoryId,
    sku = sku, barcode = barcode, purchasePricePaise = purchasePricePaise,
    sellingPricePaise = sellingPricePaise, currentStock = currentStock,
    minStockLevel = minStockLevel, unit = unit.name, supplierId = supplierId,
    imagePath = imagePath, isActive = isActive, weightedAvgCostPaise = purchasePricePaise,
    createdAt = createdAt, updatedAt = updatedAt
)

// ====== SUPPLIER ======
fun SupplierEntity.toDomain() = Supplier(
    id = id, name = name, contactPerson = contactPerson, phoneNumber = phoneNumber,
    email = email, address = address, gstNumber = gstNumber, notes = notes,
    totalPurchasesPaise = totalPurchasesPaise, isActive = isActive,
    createdAt = createdAt, updatedAt = updatedAt
)
fun Supplier.toEntity() = SupplierEntity(
    id = id, name = name, contactPerson = contactPerson, phoneNumber = phoneNumber,
    email = email, address = address, gstNumber = gstNumber, notes = notes,
    totalPurchasesPaise = totalPurchasesPaise, isActive = isActive,
    createdAt = createdAt, updatedAt = updatedAt
)

// ====== INVOICE ======
fun InvoiceEntity.toDomain(items: List<InvoiceItem> = emptyList()) = Invoice(
    id = id, invoiceNumber = invoiceNumber ?: "", supplierId = supplierId, supplierName = supplierName ?: "",
    invoiceDate = invoiceDate ?: 0L, dueDate = dueDate, subtotalPaise = subtotalPaise,
    discountPaise = discountPaise, taxPaise = taxPaise, grandTotalPaise = grandTotalPaise,
    status = try { InvoiceStatus.valueOf(status) } catch (_: Exception) { InvoiceStatus.DRAFT }, imagePath = imagePath, rawOcrText = rawOcrText,
    notes = notes, items = items, scannedAt = scannedAt, confirmedAt = confirmedAt,
    createdAt = createdAt, updatedAt = updatedAt
)
fun Invoice.toEntity() = InvoiceEntity(
    id = id, invoiceNumber = invoiceNumber, supplierId = supplierId, supplierName = supplierName,
    invoiceDate = invoiceDate, dueDate = dueDate, subtotalPaise = subtotalPaise,
    discountPaise = discountPaise, taxPaise = taxPaise, grandTotalPaise = grandTotalPaise,
    status = status.name, imagePath = imagePath, rawOcrText = rawOcrText, notes = notes,
    scannedAt = scannedAt, confirmedAt = confirmedAt, createdAt = createdAt, updatedAt = updatedAt
)
fun InvoiceItemEntity.toDomain() = InvoiceItem(
    id = id, invoiceId = invoiceId, productId = productId, productName = productName,
    quantity = quantity, unit = unit, purchasePricePaise = purchasePricePaise,
    discountPaise = discountPaise, taxPaise = taxPaise, lineTotalPaise = lineTotalPaise
)
fun InvoiceItem.toEntity() = InvoiceItemEntity(
    id = id, invoiceId = invoiceId, productId = productId, productName = productName,
    quantity = quantity, unit = unit, purchasePricePaise = purchasePricePaise,
    discountPaise = discountPaise, taxPaise = taxPaise, lineTotalPaise = lineTotalPaise
)

// ====== CUSTOMER ======
fun CustomerEntity.toDomain() = Customer(
    id = id, name = name, phoneNumber = phoneNumber, address = address, notes = notes,
    outstandingPaise = outstandingPaise, totalCreditPaise = totalCreditPaise,
    totalPaymentsPaise = totalPaymentsPaise, isActive = isActive,
    createdAt = createdAt, updatedAt = updatedAt
)
fun Customer.toEntity() = CustomerEntity(
    id = id, name = name, phoneNumber = phoneNumber, address = address, notes = notes,
    outstandingPaise = outstandingPaise, totalCreditPaise = totalCreditPaise,
    totalPaymentsPaise = totalPaymentsPaise, isActive = isActive,
    createdAt = createdAt, updatedAt = updatedAt
)

// ====== KHATA TRANSACTION ======
fun KhataTransactionEntity.toDomain(customerName: String = "") = KhataTransaction(
    id = id, customerId = customerId, customerName = customerName,
    type = KhataTransactionType.valueOf(type),
    amountPaise = amountPaise,
    paymentMethod = paymentMethod?.let { PaymentMethod.valueOf(it) },
    referenceId = referenceId, notes = notes, isReversed = isReversed,
    reversalId = reversalId, createdAt = createdAt, updatedAt = updatedAt
)
fun KhataTransaction.toEntity() = KhataTransactionEntity(
    id = id, customerId = customerId, type = type.name, amountPaise = amountPaise,
    paymentMethod = paymentMethod?.name, referenceId = referenceId,
    referenceType = null, notes = notes, isReversed = isReversed,
    reversalId = reversalId, createdAt = createdAt, updatedAt = updatedAt
)

// ====== STOCK MOVEMENT ======
fun StockMovementEntity.toDomain() = StockMovement(
    id = id, productId = productId, productName = productName,
    type = StockMovementType.valueOf(type), quantityChange = quantityChange,
    quantityAfter = quantityAfter, referenceId = referenceId,
    referenceType = referenceType, notes = notes, createdAt = createdAt
)
fun StockMovement.toEntity() = StockMovementEntity(
    id = id, productId = productId, productName = productName,
    type = type.name, quantityChange = quantityChange, quantityAfter = quantityAfter,
    referenceId = referenceId, referenceType = referenceType, notes = notes, createdAt = createdAt
)

// ====== EXPENSE ======
fun ExpenseEntity.toDomain() = Expense(
    id = id, title = title, category = ExpenseCategory.valueOf(category),
    amountPaise = amountPaise, description = description, date = date,
    receiptImagePath = receiptImagePath, createdAt = createdAt, updatedAt = updatedAt
)
fun Expense.toEntity() = ExpenseEntity(
    id = id, title = title, category = category.name, amountPaise = amountPaise,
    description = description, date = date, receiptImagePath = receiptImagePath,
    createdAt = createdAt, updatedAt = updatedAt
)

// ====== SALE ======
fun SaleItemEntity.toDomain() = SaleItem(
    id = id, saleId = saleId, productId = productId, productName = productName,
    quantity = quantity, unit = StockUnit.valueOf(unit), sellingPricePaise = sellingPricePaise,
    purchasePricePaise = purchasePricePaise, discountPaise = discountPaise,
    taxPaise = taxPaise, lineTotalPaise = lineTotalPaise
)
fun SalePaymentEntity.toDomain() = SalePayment(
    id = id, saleId = saleId, method = PaymentMethod.valueOf(method),
    amountPaise = amountPaise, reference = reference
)
fun SaleEntity.toDomain(items: List<SaleItem> = emptyList(), payments: List<SalePayment> = emptyList()) = Sale(
    id = id, customerId = customerId, customerName = customerName, saleDate = saleDate,
    subtotalPaise = subtotalPaise, discountPaise = discountPaise, taxPaise = taxPaise,
    grandTotalPaise = grandTotalPaise, status = SaleStatus.valueOf(status), notes = notes,
    items = items, payments = payments, createdAt = createdAt, updatedAt = updatedAt
)