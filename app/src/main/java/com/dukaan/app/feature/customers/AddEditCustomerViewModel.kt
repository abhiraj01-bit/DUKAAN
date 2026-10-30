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

data class AddEditCustomerUiState(
    val customerId: String? = null,
    val name: String = "",
    val phoneNumber: String = "",
    val address: String = "",
    val notes: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSaved: Boolean = false
)

@HiltViewModel
class AddEditCustomerViewModel @Inject constructor(
    private val customerRepository: CustomerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddEditCustomerUiState())
    val uiState: StateFlow<AddEditCustomerUiState> = _uiState.asStateFlow()

    fun initCustomer(customerId: String?) {
        if (customerId == null) return
        viewModelScope.launch {
            val customer = customerRepository.getCustomerByIdOnce(customerId) ?: return@launch
            _uiState.update {
                it.copy(
                    customerId = customer.id,
                    name = customer.name,
                    phoneNumber = customer.phoneNumber ?: "",
                    address = customer.address ?: "",
                    notes = customer.notes ?: ""
                )
            }
        }
    }

    fun onNameChange(v: String) = _uiState.update { it.copy(name = v, error = null) }
    fun onPhoneChange(v: String) = _uiState.update { it.copy(phoneNumber = v) }
    fun onAddressChange(v: String) = _uiState.update { it.copy(address = v) }
    fun onNotesChange(v: String) = _uiState.update { it.copy(notes = v) }

    fun saveCustomer() {
        val state = _uiState.value
        if (state.name.isBlank()) {
            _uiState.update { it.copy(error = "Customer name is required") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val now = System.currentTimeMillis()
                val id = state.customerId ?: UuidGenerator.generate()
                val existing = if (state.customerId != null) customerRepository.getCustomerByIdOnce(state.customerId) else null

                val customer = Customer(
                    id = id,
                    name = state.name.trim(),
                    phoneNumber = state.phoneNumber.trim().ifEmpty { null },
                    address = state.address.trim().ifEmpty { null },
                    notes = state.notes.trim().ifEmpty { null },
                    outstandingPaise = existing?.outstandingPaise ?: 0L,
                    totalCreditPaise = existing?.totalCreditPaise ?: 0L,
                    totalPaymentsPaise = existing?.totalPaymentsPaise ?: 0L,
                    isActive = true,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now
                )
                customerRepository.saveCustomer(customer)
                _uiState.update { it.copy(isLoading = false, isSaved = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage ?: "Failed to save customer") }
            }
        }
    }
}