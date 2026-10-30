package com.dukaan.app.feature.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dukaan.app.core.util.CurrencyFormatter
import com.dukaan.app.core.util.UuidGenerator
import com.dukaan.app.domain.model.*
import com.dukaan.app.domain.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddEditProductUiState(
    val productId: String? = null,
    val name: String = "",
    val categoryId: String? = null,
    val unit: StockUnit = StockUnit.PCS,
    val purchasePriceRupees: String = "",
    val sellingPriceRupees: String = "",
    val openingStock: String = "0",
    val minStockLevel: String = "5",
    val barcode: String = "",
    val description: String = "",
    val categories: List<ProductCategory> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSaved: Boolean = false
)

@HiltViewModel
class AddEditProductViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val categoryRepository: CategoryRepository,
    private val stockMovementRepository: StockMovementRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddEditProductUiState())
    val uiState: StateFlow<AddEditProductUiState> = _uiState.asStateFlow()

    init {
        loadCategories()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            categoryRepository.getAllCategories().collect { cats ->
                _uiState.update { it.copy(categories = cats) }
            }
        }
    }

    fun initProduct(productId: String?) {
        if (productId == null) return
        viewModelScope.launch {
            val product = productRepository.getProductByIdOnce(productId) ?: return@launch
            _uiState.update {
                it.copy(
                    productId = product.id,
                    name = product.name,
                    categoryId = product.categoryId,
                    unit = product.unit,
                    purchasePriceRupees = (product.purchasePricePaise / 100.0).toString(),
                    sellingPriceRupees = (product.sellingPricePaise / 100.0).toString(),
                    openingStock = product.currentStock.toString(),
                    minStockLevel = product.minStockLevel.toString(),
                    barcode = product.barcode ?: "",
                    description = product.description ?: ""
                )
            }
        }
    }

    fun onNameChange(v: String) = _uiState.update { it.copy(name = v, error = null) }
    fun onCategoryChange(v: String?) = _uiState.update { it.copy(categoryId = v) }
    fun onUnitChange(v: StockUnit) = _uiState.update { it.copy(unit = v) }
    fun onPurchasePriceChange(v: String) = _uiState.update { it.copy(purchasePriceRupees = v, error = null) }
    fun onSellingPriceChange(v: String) = _uiState.update { it.copy(sellingPriceRupees = v, error = null) }
    fun onStockChange(v: String) = _uiState.update { it.copy(openingStock = v) }
    fun onMinStockChange(v: String) = _uiState.update { it.copy(minStockLevel = v) }
    fun onBarcodeChange(v: String) = _uiState.update { it.copy(barcode = v) }
    fun onDescriptionChange(v: String) = _uiState.update { it.copy(description = v) }

    fun saveProduct() {
        val state = _uiState.value
        if (state.name.isBlank()) {
            _uiState.update { it.copy(error = "Product name cannot be empty") }
            return
        }

        val purchasePaise = CurrencyFormatter.rupeesToPaise(state.purchasePriceRupees)
        val sellingPaise = CurrencyFormatter.rupeesToPaise(state.sellingPriceRupees)
        val stock = state.openingStock.toDoubleOrNull() ?: 0.0
        val minStock = state.minStockLevel.toDoubleOrNull() ?: 5.0

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val isNew = state.productId == null
                val id = state.productId ?: UuidGenerator.generate()
                val now = System.currentTimeMillis()

                val product = Product(
                    id = id,
                    name = state.name.trim(),
                    categoryId = state.categoryId,
                    unit = state.unit,
                    purchasePricePaise = purchasePaise,
                    sellingPricePaise = sellingPaise,
                    currentStock = stock,
                    minStockLevel = minStock,
                    barcode = state.barcode.trim().ifEmpty { null },
                    description = state.description.trim().ifEmpty { null },
                    isActive = true,
                    createdAt = now,
                    updatedAt = now
                )

                if (isNew) {
                    productRepository.saveProduct(product)
                    if (stock > 0) {
                        stockMovementRepository.insertMovement(
                            StockMovement(
                                id = UuidGenerator.generate(),
                                productId = id,
                                productName = product.name,
                                type = StockMovementType.INITIAL_STOCK,
                                quantityChange = stock,
                                quantityAfter = stock,
                                notes = "Opening balance",
                                createdAt = now
                            )
                        )
                    }
                } else {
                    productRepository.updateProduct(product)
                }

                _uiState.update { it.copy(isLoading = false, isSaved = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage ?: "Failed to save product") }
            }
        }
    }
}