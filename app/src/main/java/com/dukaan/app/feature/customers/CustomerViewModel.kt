package com.dukaan.app.feature.customers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dukaan.app.core.util.UuidGenerator
import com.dukaan.app.domain.model.Customer
import com.dukaan.app.domain.repository.CustomerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CustomersUiState(
    val customers: List<Customer> = emptyList(),
    val searchQuery: String = "",
    val totalOutstandingPaise: Long = 0L,
    val isLoading: Boolean = true
)

@HiltViewModel
class CustomerViewModel @Inject constructor(
    private val customerRepository: CustomerRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _uiState = MutableStateFlow(CustomersUiState())
    val uiState: StateFlow<CustomersUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            customerRepository.getTotalOutstandingPaise().collect { total ->
                _uiState.update { it.copy(totalOutstandingPaise = total ?: 0L) }
            }
        }

        viewModelScope.launch {
            _searchQuery.flatMapLatest { q ->
                if (q.isBlank()) customerRepository.getAllActiveCustomers()
                else customerRepository.searchCustomers(q)
            }.collect { list ->
                _uiState.update { it.copy(customers = list, searchQuery = _searchQuery.value, isLoading = false) }
            }
        }
    }

    fun onSearchChange(query: String) {
        _searchQuery.value = query
    }
}