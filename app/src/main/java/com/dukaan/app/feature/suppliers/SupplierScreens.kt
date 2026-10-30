package com.dukaan.app.feature.suppliers

import androidx.compose.foundation.clickable
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
import com.dukaan.app.core.util.UuidGenerator
import com.dukaan.app.domain.model.Supplier
import com.dukaan.app.domain.repository.SupplierRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SupplierViewModel @Inject constructor(
    private val supplierRepository: SupplierRepository
) : ViewModel() {
    private val _suppliers = MutableStateFlow<List<Supplier>>(emptyList())
    val suppliers = _suppliers.asStateFlow()

    init {
        viewModelScope.launch {
            supplierRepository.getAllActiveSuppliers().collect { list ->
                _suppliers.value = list
            }
        }
    }

    fun saveSupplier(name: String, contact: String?, phone: String?, gst: String?, onDone: () -> Unit) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val supplier = Supplier(
                id = UuidGenerator.generate(),
                name = name.trim(),
                contactPerson = contact?.trim()?.ifEmpty { null },
                phoneNumber = phone?.trim()?.ifEmpty { null },
                gstNumber = gst?.trim()?.ifEmpty { null },
                createdAt = now,
                updatedAt = now
            )
            supplierRepository.saveSupplier(supplier)
            onDone()
        }
    }
}

@Composable
fun SupplierListScreen(
    navController: NavController,
    viewModel: SupplierViewModel = hiltViewModel()
) {
    val suppliers by viewModel.suppliers.collectAsState()

    Scaffold(
        topBar = {
            DukaanTopBar(
                title = "Suppliers & Distributors",
                onBackClick = { navController.popBackStack() }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Screen.AddSupplier.route) },
                containerColor = ForestGreen,
                contentColor = SurfaceWhite
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Supplier")
            }
        },
        containerColor = WarmCream
    ) { paddingValues ->
        if (suppliers.isEmpty()) {
            EmptyState(
                title = "No suppliers added",
                subtitle = "Manage wholesalers and distributors you buy inventory from.",
                icon = Icons.Default.LocalShipping,
                actionText = "Add Supplier",
                onAction = { navController.navigate(Screen.AddSupplier.route) },
                modifier = Modifier.padding(paddingValues)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(suppliers) { sup ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { navController.navigate(Screen.SupplierDetail.createRoute(sup.id)) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(sup.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                                if (!sup.phoneNumber.isNullOrBlank()) {
                                    Text(sup.phoneNumber!!, style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                                }
                            }
                            Text(
                                "Total: ${CurrencyFormatter.formatPaise(sup.totalPurchasesPaise)}",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = ForestGreen
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddEditSupplierScreen(
    navController: NavController,
    supplierId: String? = null,
    viewModel: SupplierViewModel = hiltViewModel()
) {
    var name by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var gst by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            DukaanTopBar(
                title = if (supplierId == null) "Add Supplier" else "Edit Supplier",
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
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Supplier / Company Name *") },
                placeholder = { Text("e.g. Balaji Traders") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = contact,
                onValueChange = { contact = it },
                label = { Text("Contact Person Name") },
                placeholder = { Text("e.g. Suresh Bhai") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Phone Number") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = gst,
                onValueChange = { gst = it },
                label = { Text("GST Number (Optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            PrimaryButton(
                text = "Save Supplier",
                onClick = {
                    viewModel.saveSupplier(name, contact, phone, gst) {
                        navController.popBackStack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Default.Check
            )
        }
    }
}

@Composable
fun SupplierDetailScreen(
    navController: NavController,
    supplierId: String
) {
    Scaffold(
        topBar = {
            DukaanTopBar(
                title = "Supplier Details",
                onBackClick = { navController.popBackStack() }
            )
        },
        containerColor = WarmCream
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            Text("Supplier ID: $supplierId", style = MaterialTheme.typography.bodyLarge)
        }
    }
}