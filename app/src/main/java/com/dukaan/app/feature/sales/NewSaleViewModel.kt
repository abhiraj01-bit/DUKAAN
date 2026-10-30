package com.dukaan.app.feature.sales

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dukaan.app.core.util.UuidGenerator
import com.dukaan.app.domain.model.*
import com.dukaan.app.domain.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CartItem(
    val product: Product,
    val quantity: Double,
    val sellingPricePaise: Long
) {
    val lineTotalPaise: Long get() = (quantity * sellingPricePaise).toLong()
}

data class NewSaleUiState(
    val availableProducts: List<Product> = emptyList(),
    val customers: List<Customer> = emptyList(),
    val selectedCustomer: Customer? = null,
    val cartItems: List<CartItem> = emptyList(),
    val searchQuery: String = "",
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val isCreditSale: Boolean = false,
    val isLoading: Boolean = false,
    val isCompleted: Boolean = false,
    val error: String? = null
) {
    val totalAmountPaise: Long get() = cartItems.sumOf { it.lineTotalPaise }
}

@HiltViewModel
class NewSaleViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val customerRepository: CustomerRepository,
    private val saleRepository: SaleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewSaleUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            productRepository.getAllActiveProducts().collect { prods ->
                _uiState.update { it.copy(availableProducts = prods) }
            }
        }
        viewModelScope.launch {
            customerRepository.getAllActiveCustomers().collect { custs ->
                _uiState.update { it.copy(customers = custs) }
            }
        }
    }

    fun onSearchChange(q: String) = _uiState.update { it.copy(searchQuery = q) }
    fun selectCustomer(c: Customer?) = _uiState.update { it.copy(selectedCustomer = c) }
    fun setPaymentMethod(m: PaymentMethod) = _uiState.update { it.copy(paymentMethod = m, isCreditSale = false) }
    fun setCreditSale(isCredit: Boolean) = _uiState.update { it.copy(isCreditSale = isCredit) }

    fun addToCart(product: Product) {
        val current = _uiState.value.cartItems.toMutableList()
        val index = current.indexOfFirst { it.product.id == product.id }
        if (index >= 0) {
            current[index] = current[index].copy(quantity = current[index].quantity + 1.0)
        } else {
            current.add(CartItem(product, 1.0, product.sellingPricePaise))
        }
        _uiState.update { it.copy(cartItems = current) }
    }

    fun updateCartQuantity(productId: String, delta: Double) {
        val current = _uiState.value.cartItems.toMutableList()
        val index = current.indexOfFirst { it.product.id == productId }
        if (index >= 0) {
            val newQty = current[index].quantity + delta
            if (newQty <= 0) {
                current.removeAt(index)
            } else {
                current[index] = current[index].copy(quantity = newQty)
            }
            _uiState.update { it.copy(cartItems = current) }
        }
    }

    fun completeSale() {
        val state = _uiState.value
        if (state.cartItems.isEmpty()) {
            _uiState.update { it.copy(error = "Cart is empty") }
            return
        }

        if (state.isCreditSale && state.selectedCustomer == null) {
            _uiState.update { it.copy(error = "Please select a customer for credit (udhar) sale") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val saleId = UuidGenerator.generate()
                val now = System.currentTimeMillis()
                val totalPaise = state.totalAmountPaise

                val saleItems = state.cartItems.map { ci ->
                    SaleItem(
                        id = UuidGenerator.generate(),
                        saleId = saleId,
                        productId = ci.product.id,
                        productName = ci.product.name,
                        quantity = ci.quantity,
                        unit = ci.product.unit,
                        sellingPricePaise = ci.sellingPricePaise,
                        purchasePricePaise = ci.product.purchasePricePaise,
                        lineTotalPaise = ci.lineTotalPaise
                    )
                }

                val payments = if (!state.isCreditSale) {
                    listOf(
                        SalePayment(
                            id = UuidGenerator.generate(),
                            saleId = saleId,
                            method = state.paymentMethod,
                            amountPaise = totalPaise
                        )
                    )
                } else emptyList()

                val sale = Sale(
                    id = saleId,
                    customerId = state.selectedCustomer?.id,
                    customerName = state.selectedCustomer?.name ?: "Cash Customer",
                    saleDate = now,
                    subtotalPaise = totalPaise,
                    grandTotalPaise = totalPaise,
                    status = SaleStatus.COMPLETED,
                    items = saleItems,
                    payments = payments,
                    createdAt = now,
                    updatedAt = now
                )

                saleRepository.saveSaleWithDetails(sale)
                _uiState.update { it.copy(isLoading = false, isCompleted = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage ?: "Failed to save sale") }
            }
        }
    }
}