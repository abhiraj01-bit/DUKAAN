package com.dukaan.app.feature.khata

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dukaan.app.domain.model.Customer
import com.dukaan.app.domain.repository.CustomerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class KhataUiState(
    val customersWithDue: List<Customer> = emptyList(),
    val allCustomers: List<Customer> = emptyList(),
    val totalOutstandingPaise: Long = 0L,
    val searchQuery: String = "",
    val showOnlyPending: Boolean = true,
    val isLoading: Boolean = true
)

@HiltViewModel
class KhataViewModel @Inject constructor(
    private val customerRepository: CustomerRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _showOnlyPending = MutableStateFlow(true)
    private val _uiState = MutableStateFlow(KhataUiState())
    val uiState: StateFlow<KhataUiState> = _uiState.asStateFlow()

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
            combine(_searchQuery, _showOnlyPending) { query, onlyPending ->
                Pair(query, onlyPending)
            }.flatMapLatest { (query, onlyPending) ->
                if (onlyPending) customerRepository.getCustomersWithOutstanding()
                else if (query.isNotBlank()) customerRepository.searchCustomers(query)
                else customerRepository.getAllActiveCustomers()
            }.collect { list ->
                _uiState.update {
                    it.copy(
                        customersWithDue = list,
                        searchQuery = _searchQuery.value,
                        showOnlyPending = _showOnlyPending.value,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun onSearchChange(query: String) { _searchQuery.value = query }
    fun onFilterChange(onlyPending: Boolean) { _showOnlyPending.value = onlyPending }
}