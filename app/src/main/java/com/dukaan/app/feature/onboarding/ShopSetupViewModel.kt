package com.dukaan.app.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dukaan.app.core.datastore.DukaanPreferences
import com.dukaan.app.core.util.UuidGenerator
import com.dukaan.app.domain.model.ShopProfile
import com.dukaan.app.domain.repository.ShopRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SetPinViewModel @Inject constructor(
    private val preferences: DukaanPreferences
) : ViewModel() {
    val savedPin: kotlinx.coroutines.flow.Flow<String> = preferences.appPin

    fun savePin(pin: String) {
        viewModelScope.launch {
            preferences.setAppPin(pin)
        }
    }
}

data class ShopSetupUiState(
    val shopName: String = "",
    val ownerName: String = "",
    val businessType: String = "Kirana / Grocery Store",
    val phoneNumber: String = "",
    val address: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false
)

@HiltViewModel
class ShopSetupViewModel @Inject constructor(
    private val shopRepository: ShopRepository,
    private val preferences: DukaanPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShopSetupUiState())
    val uiState: StateFlow<ShopSetupUiState> = _uiState.asStateFlow()

    fun onShopNameChange(value: String) { _uiState.value = _uiState.value.copy(shopName = value, error = null) }
    fun onOwnerNameChange(value: String) { _uiState.value = _uiState.value.copy(ownerName = value, error = null) }
    fun onBusinessTypeChange(value: String) { _uiState.value = _uiState.value.copy(businessType = value) }
    fun onPhoneNumberChange(value: String) { _uiState.value = _uiState.value.copy(phoneNumber = value) }
    fun onAddressChange(value: String) { _uiState.value = _uiState.value.copy(address = value) }

    fun completeSetup() {
        val state = _uiState.value
        if (state.shopName.isBlank()) {
            _uiState.value = state.copy(error = "Please enter your shop or business name")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val now = System.currentTimeMillis()
                val profile = ShopProfile(
                    id = "main_shop",
                    shopName = state.shopName.trim(),
                    ownerName = state.ownerName.trim(),
                    businessType = state.businessType,
                    phoneNumber = state.phoneNumber.trim(),
                    address = state.address.trim(),
                    createdAt = now,
                    updatedAt = now
                )
                shopRepository.saveShopProfile(profile)
                preferences.setOnboardingDone(true)
                _uiState.value = _uiState.value.copy(isLoading = false, isSuccess = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.localizedMessage ?: "Failed to save shop details")
            }
        }
    }
}