package com.dukaan.app.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dukaan.app.core.util.DateFormatter
import com.dukaan.app.domain.model.*
import com.dukaan.app.domain.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val shopProfile: ShopProfile? = null,
    val todaySalesPaise: Long = 0L,
    val totalKhataOutstandingPaise: Long = 0L,
    val lowStockCount: Int = 0,
    val activeProductCount: Int = 0,
    val inventoryValuePaise: Long = 0L,
    val recentSales: List<Sale> = emptyList(),
    val lowStockItems: List<Product> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val shopRepository: ShopRepository,
    private val saleRepository: SaleRepository,
    private val customerRepository: CustomerRepository,
    private val productRepository: ProductRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadDashboardData()
    }

    private fun loadDashboardData() {
        val todayStart = DateFormatter.startOfDay(System.currentTimeMillis())

        viewModelScope.launch {
            combine(
                listOf(
                    shopRepository.getShopProfile(),
                    saleRepository.getTodaysSalesPaise(todayStart),
                    customerRepository.getTotalOutstandingPaise(),
                    productRepository.getLowStockCount(),
                    productRepository.getActiveProductCount(),
                    productRepository.getTotalInventoryValuePaise()
                )
            ) { values ->
                val profile = values[0] as? ShopProfile
                val todaySales = values[1] as? Long
                val outstanding = values[2] as? Long
                val lowStock = (values[3] as? Int) ?: 0
                val totalProducts = (values[4] as? Int) ?: 0
                val invVal = values[5] as? Long

                DashboardUiState(
                    shopProfile = profile,
                    todaySalesPaise = todaySales ?: 0L,
                    totalKhataOutstandingPaise = outstanding ?: 0L,
                    lowStockCount = lowStock,
                    activeProductCount = totalProducts,
                    inventoryValuePaise = invVal ?: 0L,
                    isLoading = false
                )
            }.collect { combinedState ->
                _uiState.update { current ->
                    current.copy(
                        shopProfile = combinedState.shopProfile,
                        todaySalesPaise = combinedState.todaySalesPaise,
                        totalKhataOutstandingPaise = combinedState.totalKhataOutstandingPaise,
                        lowStockCount = combinedState.lowStockCount,
                        activeProductCount = combinedState.activeProductCount,
                        inventoryValuePaise = combinedState.inventoryValuePaise,
                        isLoading = false
                    )
                }
            }
        }

        viewModelScope.launch {
            saleRepository.getAllSales().collect { sales ->
                _uiState.update { it.copy(recentSales = sales.take(5)) }
            }
        }

        viewModelScope.launch {
            productRepository.getLowStockProducts().collect { lowItems ->
                _uiState.update { it.copy(lowStockItems = lowItems.take(4)) }
            }
        }
    }
}