package com.dukaan.app.feature.customers

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.dukaan.app.core.designsystem.components.*
import com.dukaan.app.core.designsystem.theme.*

@Composable
fun AddEditCustomerScreen(
    navController: NavController,
    customerId: String? = null,
    viewModel: AddEditCustomerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(customerId) {
        viewModel.initCustomer(customerId)
    }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) navController.popBackStack()
    }

    Scaffold(
        topBar = {
            DukaanTopBar(
                title = if (customerId == null) "Add Customer" else "Edit Customer",
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
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("Customer Name *") },
                placeholder = { Text("e.g. Ramesh Sharma") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = ForestGreen) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = state.phoneNumber,
                onValueChange = viewModel::onPhoneChange,
                label = { Text("Mobile / Phone Number") },
                placeholder = { Text("e.g. 9876543210") },
                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = ForestGreen) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = state.address,
                onValueChange = viewModel::onAddressChange,
                label = { Text("Customer Address / Colony (Optional)") },
                placeholder = { Text("e.g. House #14, Lane 2") },
                leadingIcon = { Icon(Icons.Default.Home, contentDescription = null, tint = ForestGreen) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::onNotesChange,
                label = { Text("Notes (Optional)") },
                leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null, tint = ForestGreen) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            if (state.error != null) {
                Text(state.error!!, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(8.dp))

            PrimaryButton(
                text = if (customerId == null) "Save Customer" else "Update Customer",
                onClick = viewModel::saveCustomer,
                loading = state.isLoading,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Default.Check
            )
        }
    }
}