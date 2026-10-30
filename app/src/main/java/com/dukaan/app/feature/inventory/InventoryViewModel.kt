package com.dukaan.app.feature.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dukaan.app.domain.model.Product
import com.dukaan.app.domain.model.ProductCategory
import com.dukaan.app.domain.repository.CategoryRepository
import com.dukaan.app.domain.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InventoryUiState(
    val products: List<Product> = emptyList(),
    val categories: List<ProductCategory> = emptyList(),
    val selectedCategoryId: String? = null,
    val searchQuery: String = "",
    val totalCount: Int = 0,
    val totalValuePaise: Long = 0L,
    val lowStockCount: Int = 0,
    val isLoading: Boolean = true
)

@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedCategoryId = MutableStateFlow<String?>(null)

    private val _uiState = MutableStateFlow(InventoryUiState())
    val uiState: StateFlow<InventoryUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            categoryRepository.getAllCategories().collect { cats ->
                _uiState.update { it.copy(categories = cats) }
            }
        }

        viewModelScope.launch {
            productRepository.getActiveProductCount().collect { count ->
                _uiState.update { it.copy(totalCount = count) }
            }
        }

        viewModelScope.launch {
            productRepository.getTotalInventoryValuePaise().collect { value ->
                _uiState.update { it.copy(totalValuePaise = value ?: 0L) }
            }
        }

        viewModelScope.launch {
            productRepository.getLowStockCount().collect { count ->
                _uiState.update { it.copy(lowStockCount = count) }
            }
        }

        viewModelScope.launch {
            combine(_searchQuery, _selectedCategoryId) { query, catId ->
                Pair(query, catId)
            }.flatMapLatest { (query, catId) ->
                when {
                    query.isNotBlank() -> productRepository.searchProducts(query)
                    catId != null -> productRepository.getProductsByCategory(catId)
                    else -> productRepository.getAllActiveProducts()
                }
            }.collect { productList ->
                _uiState.update {
                    it.copy(
                        products = productList,
                        searchQuery = _searchQuery.value,
                        selectedCategoryId = _selectedCategoryId.value,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun onSearchChange(query: String) {
        _searchQuery.value = query
    }

    fun onSelectCategory(categoryId: String?) {
        _selectedCategoryId.value = if (_selectedCategoryId.value == categoryId) null else categoryId
    }
}