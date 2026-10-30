package com.dukaan.app.feature.customers

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.dukaan.app.core.designsystem.components.*
import com.dukaan.app.core.designsystem.theme.*
import com.dukaan.app.core.navigation.Screen
import com.dukaan.app.core.util.CurrencyFormatter
import com.dukaan.app.core.util.DateFormatter
import com.dukaan.app.domain.model.Customer
import com.dukaan.app.domain.model.KhataTransaction
import com.dukaan.app.domain.model.KhataTransactionType
import com.dukaan.app.domain.repository.CustomerRepository
import com.dukaan.app.domain.repository.KhataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CustomerDetailUiState(
    val customer: Customer? = null,
    val transactions: List<KhataTransaction> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class CustomerDetailViewModel @Inject constructor(
    private val customerRepository: CustomerRepository,
    private val khataRepository: KhataRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CustomerDetailUiState())
    val uiState: StateFlow<CustomerDetailUiState> = _uiState.asStateFlow()

    fun loadCustomer(customerId: String) {
        viewModelScope.launch {
            customerRepository.getCustomerById(customerId).collect { c ->
                _uiState.update { it.copy(customer = c, isLoading = false) }
            }
        }
        viewModelScope.launch {
            khataRepository.getTransactionsByCustomer(customerId).collect { list ->
                _uiState.update { it.copy(transactions = list) }
            }
        }
    }
}

@Composable
fun CustomerDetailScreen(
    navController: NavController,
    customerId: String,
    viewModel: CustomerDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(customerId) {
        viewModel.loadCustomer(customerId)
    }

    Scaffold(
        topBar = {
            DukaanTopBar(
                title = state.customer?.name ?: "Customer Khata",
                onBackClick = { navController.popBackStack() },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.EditCustomer.createRoute(customerId)) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = SurfaceWhite)
                    }
                }
            )
        },
        containerColor = WarmCream
    ) { paddingValues ->
        if (state.isLoading) {
            LoadingState(modifier = Modifier.padding(paddingValues))
        } else if (state.customer == null) {
            ErrorState(message = "Customer not found", onRetry = { navController.popBackStack() })
        } else {
            val customer = state.customer!!

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Balance Card
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("CURRENT BALANCE DUE", style = MaterialTheme.typography.labelSmall, color = SecondaryText)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        CurrencyFormatter.formatPaise(customer.outstandingPaise),
                                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (customer.outstandingPaise > 0) ErrorRed else SuccessGreen
                                    )
                                }

                                if (!customer.phoneNumber.isNullOrBlank() && customer.outstandingPaise > 0) {
                                    Button(
                                        onClick = {
                                            val message = "Dear ${customer.name}, your balance due at our shop is ${CurrencyFormatter.formatPaise(customer.outstandingPaise)}. Please pay at your convenience. Thank you!"
                                            val uri = Uri.parse("https://api.whatsapp.com/send?phone=91${customer.phoneNumber}&text=${Uri.encode(message)}")
                                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Remind")
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = DividerColor)
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Total Credit Given", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                                    Text(CurrencyFormatter.formatPaise(customer.totalCreditPaise), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Total Received", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                                    Text(CurrencyFormatter.formatPaise(customer.totalPaymentsPaise), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = SuccessGreen)
                                }
                            }
                        }
                    }
                }

                // Quick Khata Actions
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { navController.navigate(Screen.AddKhataCredit.createRoute(customer.id)) },
                            colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Give Udhar (+ Due)")
                        }

                        Button(
                            onClick = { navController.navigate(Screen.RecordPayment.createRoute(customer.id)) },
                            colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Got Payment (- Due)")
                        }
                    }
                }

                // Customer info details
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Contact & Info", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = ForestGreen)
                            if (!customer.phoneNumber.isNullOrBlank()) {
                                Text("Phone: ${customer.phoneNumber}", style = MaterialTheme.typography.bodyMedium)
                            }
                            if (!customer.address.isNullOrBlank()) {
                                Text("Address: ${customer.address}", style = MaterialTheme.typography.bodyMedium)
                            }
                            if (!customer.notes.isNullOrBlank()) {
                                Text("Notes: ${customer.notes}", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                            }
                        }
                    }
                }

                // Transaction history
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Khata Transactions (${state.transactions.size})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Charcoal
                        )
                        TextButton(onClick = { navController.navigate(Screen.CustomerLedger.createRoute(customer.id)) }) {
                            Text("Full Ledger", color = ForestGreen)
                        }
                    }
                }

                if (state.transactions.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                                Text("No transactions recorded yet", color = SecondaryText, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                } else {
                    items(state.transactions.take(8)) { txn ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    val isCredit = txn.type == KhataTransactionType.CREDIT
                                    Text(
                                        if (isCredit) "Gave Credit (Udhar)" else "Payment Received (Jama)",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (isCredit) ErrorRed else SuccessGreen
                                    )
                                    Text(
                                        "${DateFormatter.formatFull(txn.createdAt)}${if (!txn.notes.isNullOrBlank()) " • ${txn.notes}" else ""}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SecondaryText
                                    )
                                }

                                Text(
                                    CurrencyFormatter.formatPaise(txn.amountPaise),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (txn.type == KhataTransactionType.CREDIT) ErrorRed else SuccessGreen
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}