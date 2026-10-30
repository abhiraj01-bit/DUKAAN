package com.dukaan.app.feature.invoice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dukaan.app.core.util.UuidGenerator
import com.dukaan.app.domain.model.*
import com.dukaan.app.domain.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InvoiceReviewUiState(
    val invoice: Invoice? = null,
    val supplierName: String = "",
    val invoiceNumber: String = "",
    val items: List<InvoiceItem> = emptyList(),
    val grandTotalPaise: Long = 0L,
    val isLoading: Boolean = true,
    val isConfirmed: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class InvoiceReviewViewModel @Inject constructor(
    private val invoiceRepository: InvoiceRepository,
    private val productRepository: ProductRepository,
    private val stockMovementRepository: StockMovementRepository,
    private val supplierRepository: SupplierRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(InvoiceReviewUiState())
    val uiState: StateFlow<InvoiceReviewUiState> = _uiState.asStateFlow()

    fun loadInvoice(invoiceId: String) {
        viewModelScope.launch {
            val inv = invoiceRepository.getInvoiceById(invoiceId)
            if (inv != null) {
                _uiState.update {
                    it.copy(
                        invoice = inv,
                        supplierName = inv.supplierName,
                        invoiceNumber = inv.invoiceNumber,
                        items = inv.items,
                        grandTotalPaise = inv.grandTotalPaise,
                        isLoading = false
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false, error = "Invoice not found") }
            }
        }
    }

    fun onSupplierNameChange(name: String) = _uiState.update { it.copy(supplierName = name) }
    fun onInvoiceNumberChange(num: String) = _uiState.update { it.copy(invoiceNumber = num) }

    fun updateItem(index: Int, name: String, qty: Double, pricePaise: Long) {
        val list = _uiState.value.items.toMutableList()
        if (index in list.indices) {
            val item = list[index]
            list[index] = item.copy(
                productName = name,
                quantity = qty,
                purchasePricePaise = pricePaise,
                lineTotalPaise = (qty * pricePaise).toLong()
            )
            val newTotal = list.sumOf { it.lineTotalPaise }
            _uiState.update { it.copy(items = list, grandTotalPaise = newTotal) }
        }
    }

    fun addItem(name: String, qty: Double, pricePaise: Long) {
        val inv = _uiState.value.invoice ?: return
        val list = _uiState.value.items.toMutableList()
        list.add(
            InvoiceItem(
                id = UuidGenerator.generate(),
                invoiceId = inv.id,
                productName = name,
                quantity = qty,
                purchasePricePaise = pricePaise,
                lineTotalPaise = (qty * pricePaise).toLong()
            )
        )
        val newTotal = list.sumOf { it.lineTotalPaise }
        _uiState.update { it.copy(items = list, grandTotalPaise = newTotal) }
    }

    fun removeItem(index: Int) {
        val list = _uiState.value.items.toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            val newTotal = list.sumOf { it.lineTotalPaise }
            _uiState.update { it.copy(items = list, grandTotalPaise = newTotal) }
        }
    }

    fun confirmInvoice() {
        val state = _uiState.value
        val inv = state.invoice ?: return
        val now = System.currentTimeMillis()

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // 1. For each item, update or create product and record stock movement
                val allProducts = productRepository.getAllProducts().first()

                for (item in state.items) {
                    val existing = allProducts.find { it.name.equals(item.productName.trim(), ignoreCase = true) }
                    val productId = if (existing != null) {
                        val newStock = existing.currentStock + item.quantity
                        productRepository.updateStock(existing.id, newStock, item.purchasePricePaise)
                        existing.id
                    } else {
                        val newId = UuidGenerator.generate()
                        // 15% estimated selling markup for Kirana stores if not set
                        val markup = (item.purchasePricePaise * 1.15).toLong()
                        val newProduct = Product(
                            id = newId,
                            name = item.productName.trim(),
                            purchasePricePaise = item.purchasePricePaise,
                            sellingPricePaise = markup,
                            currentStock = item.quantity,
                            unit = StockUnit.PCS,
                            isActive = true,
                            createdAt = now,
                            updatedAt = now
                        )
                        productRepository.saveProduct(newProduct)
                        newId
                    }

                    stockMovementRepository.insertMovement(
                        StockMovement(
                            id = UuidGenerator.generate(),
                            productId = productId,
                            productName = item.productName,
                            type = StockMovementType.INVOICE_PURCHASE,
                            quantityChange = item.quantity,
                            quantityAfter = (existing?.currentStock ?: 0.0) + item.quantity,
                            referenceId = inv.id,
                            referenceType = "INVOICE",
                            notes = "Invoice #${state.invoiceNumber}",
                            createdAt = now
                        )
                    )
                }

                // 2. Save confirmed invoice
                val confirmedInvoice = inv.copy(
                    supplierName = state.supplierName,
                    invoiceNumber = state.invoiceNumber,
                    items = state.items,
                    grandTotalPaise = state.grandTotalPaise,
                    status = InvoiceStatus.CONFIRMED,
                    confirmedAt = now,
                    updatedAt = now
                )
                invoiceRepository.saveInvoice(confirmedInvoice)

                _uiState.update { it.copy(isLoading = false, isConfirmed = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage ?: "Failed to confirm invoice") }
            }
        }
    }
}