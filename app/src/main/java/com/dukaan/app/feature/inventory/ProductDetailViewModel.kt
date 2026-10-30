package com.dukaan.app.feature.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dukaan.app.core.util.UuidGenerator
import com.dukaan.app.domain.model.*
import com.dukaan.app.domain.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProductDetailUiState(
    val product: Product? = null,
    val movements: List<StockMovement> = emptyList(),
    val isLoading: Boolean = true,
    val isDeleted: Boolean = false
)

@HiltViewModel
class ProductDetailViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val stockMovementRepository: StockMovementRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductDetailUiState())
    val uiState: StateFlow<ProductDetailUiState> = _uiState.asStateFlow()

    fun loadProduct(productId: String) {
        viewModelScope.launch {
            productRepository.getProductById(productId).collect { p ->
                _uiState.update { it.copy(product = p, isLoading = false) }
            }
        }
        viewModelScope.launch {
            stockMovementRepository.getMovementsByProduct(productId).collect { moves ->
                _uiState.update { it.copy(movements = moves) }
            }
        }
    }

    fun adjustStock(quantityDelta: Double, reason: String) {
        val product = _uiState.value.product ?: return
        val newStock = product.currentStock + quantityDelta
        val now = System.currentTimeMillis()

        viewModelScope.launch {
            productRepository.updateStock(product.id, newStock, product.weightedAvgCostPaise)
            stockMovementRepository.insertMovement(
                StockMovement(
                    id = UuidGenerator.generate(),
                    productId = product.id,
                    productName = product.name,
                    type = if (quantityDelta >= 0) StockMovementType.MANUAL_ADD else StockMovementType.DAMAGE_OR_LOSS,
                    quantityChange = quantityDelta,
                    quantityAfter = newStock,
                    notes = reason.ifBlank { "Manual adjustment" },
                    createdAt = now
                )
            )
        }
    }

    fun deleteProduct() {
        val product = _uiState.value.product ?: return
        viewModelScope.launch {
            productRepository.archiveProduct(product.id)
            _uiState.update { it.copy(isDeleted = true) }
        }
    }
}