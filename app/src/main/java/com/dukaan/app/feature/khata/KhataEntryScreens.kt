package com.dukaan.app.feature.khata

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.dukaan.app.core.designsystem.components.*
import com.dukaan.app.core.designsystem.theme.*
import com.dukaan.app.core.util.CurrencyFormatter
import com.dukaan.app.core.util.UuidGenerator
import com.dukaan.app.domain.model.*
import com.dukaan.app.domain.repository.CustomerRepository
import com.dukaan.app.domain.repository.KhataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class KhataEntryUiState(
    val customer: Customer? = null,
    val amountRupees: String = "",
    val notes: String = "",
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class KhataEntryViewModel @Inject constructor(
    private val customerRepository: CustomerRepository,
    private val khataRepository: KhataRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(KhataEntryUiState())
    val uiState = _uiState.asStateFlow()

    fun loadCustomer(customerId: String) {
        viewModelScope.launch {
            val c = customerRepository.getCustomerByIdOnce(customerId)
            _uiState.update { it.copy(customer = c) }
        }
    }

    fun onAmountChange(v: String) = _uiState.update { it.copy(amountRupees = v, error = null) }
    fun onNotesChange(v: String) = _uiState.update { it.copy(notes = v) }
    fun onPaymentMethodChange(m: PaymentMethod) = _uiState.update { it.copy(paymentMethod = m) }

    fun addCredit(customerId: String) {
        val amountPaise = CurrencyFormatter.rupeesToPaise(_uiState.value.amountRupees)
        if (amountPaise <= 0) {
            _uiState.update { it.copy(error = "Please enter a valid amount") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val now = System.currentTimeMillis()
            val txn = KhataTransaction(
                id = UuidGenerator.generate(),
                customerId = customerId,
                type = KhataTransactionType.CREDIT,
                amountPaise = amountPaise,
                notes = _uiState.value.notes.trim().ifEmpty { "Gave Udhar" },
                createdAt = now,
                updatedAt = now
            )
            khataRepository.insertTransaction(txn)
            customerRepository.updateFinancials(customerId, delta = amountPaise, creditDelta = amountPaise, paymentDelta = 0L)
            _uiState.update { it.copy(isLoading = false, isSaved = true) }
        }
    }

    fun recordPayment(customerId: String) {
        val amountPaise = CurrencyFormatter.rupeesToPaise(_uiState.value.amountRupees)
        if (amountPaise <= 0) {
            _uiState.update { it.copy(error = "Please enter a valid amount") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val now = System.currentTimeMillis()
            val txn = KhataTransaction(
                id = UuidGenerator.generate(),
                customerId = customerId,
                type = KhataTransactionType.PAYMENT,
                amountPaise = amountPaise,
                paymentMethod = _uiState.value.paymentMethod,
                notes = _uiState.value.notes.trim().ifEmpty { "Payment received via ${_uiState.value.paymentMethod.name}" },
                createdAt = now,
                updatedAt = now
            )
            khataRepository.insertTransaction(txn)
            // Receiving payment decreases outstanding balance (-amountPaise)
            customerRepository.updateFinancials(customerId, delta = -amountPaise, creditDelta = 0L, paymentDelta = amountPaise)
            _uiState.update { it.copy(isLoading = false, isSaved = true) }
        }
    }
}

@Composable
fun AddKhataCreditScreen(
    navController: NavController,
    customerId: String,
    viewModel: KhataEntryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(customerId) {
        viewModel.loadCustomer(customerId)
    }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) navController.popBackStack()
    }

    Scaffold(
        topBar = {
            DukaanTopBar(
                title = "Give Udhar (+ Credit)",
                onBackClick = { navController.popBackStack() }
            )
        },
        containerColor = WarmCream
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Customer Header
            if (state.customer != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(state.customer!!.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Text("Current Balance: ${CurrencyFormatter.formatPaise(state.customer!!.outstandingPaise)}", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                    }
                }
            }

            OutlinedTextField(
                value = state.amountRupees,
                onValueChange = viewModel::onAmountChange,
                label = { Text("Credit Amount Given (?) *") },
                placeholder = { Text("0.00") },
                leadingIcon = { Icon(Icons.Default.CurrencyRupee, contentDescription = null, tint = ErrorRed) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::onNotesChange,
                label = { Text("Items / Description (Optional)") },
                placeholder = { Text("e.g. Rice 10kg, Sugar 2kg") },
                leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null, tint = ForestGreen) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            if (state.error != null) {
                Text(state.error!!, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = { viewModel.addCredit(customerId) },
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Confirm Udhar Entry", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}

@Composable
fun RecordPaymentScreen(
    navController: NavController,
    customerId: String,
    viewModel: KhataEntryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(customerId) {
        viewModel.loadCustomer(customerId)
    }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) navController.popBackStack()
    }

    Scaffold(
        topBar = {
            DukaanTopBar(
                title = "Got Payment (- Jama)",
                onBackClick = { navController.popBackStack() }
            )
        },
        containerColor = WarmCream
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Customer Header
            if (state.customer != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(state.customer!!.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Text("Current Balance Due: ${CurrencyFormatter.formatPaise(state.customer!!.outstandingPaise)}", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                    }
                }
            }

            OutlinedTextField(
                value = state.amountRupees,
                onValueChange = viewModel::onAmountChange,
                label = { Text("Amount Received (?) *") },
                placeholder = { Text("0.00") },
                leadingIcon = { Icon(Icons.Default.CurrencyRupee, contentDescription = null, tint = SuccessGreen) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Text("Payment Mode", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Charcoal)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(PaymentMethod.CASH, PaymentMethod.UPI, PaymentMethod.BANK_TRANSFER, PaymentMethod.CHEQUE).forEach { mode ->
                    FilterChip(
                        selected = state.paymentMethod == mode,
                        onClick = { viewModel.onPaymentMethodChange(mode) },
                        label = { Text(mode.name.replace("_", " ")) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ForestGreen,
                            selectedLabelColor = SurfaceWhite
                        )
                    )
                }
            }

            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::onNotesChange,
                label = { Text("Notes / UPI Reference (Optional)") },
                leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null, tint = ForestGreen) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            if (state.error != null) {
                Text(state.error!!, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = { viewModel.recordPayment(customerId) },
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Confirm Payment Received", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}