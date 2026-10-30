package com.dukaan.app.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dukaan.app.core.designsystem.components.PrimaryButton
import com.dukaan.app.core.designsystem.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopSetupScreen(
    onSetupComplete: () -> Unit,
    viewModel: ShopSetupViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.isSuccess) {
        if (state.isSuccess) {
            onSetupComplete()
        }
    }

    val businessTypes = listOf(
        "Kirana / Grocery Store",
        "General / Retail Store",
        "Medical / Pharmacy",
        "Clothing / Garments",
        "Hardware / Electrical",
        "Fruits & Vegetables",
        "Wholesale Trader",
        "Other"
    )
    var expandedTypeDropdown by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Set Up Your Shop",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = SurfaceWhite
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ForestGreen)
            )
        },
        containerColor = WarmCream
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Intro banner
            Card(
                colors = CardDefaults.cardColors(containerColor = SuccessGreenLight),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Storefront,
                        contentDescription = null,
                        tint = ForestGreen,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Welcome to DUKAAN!",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = ForestGreen
                        )
                        Text(
                            text = "Enter basic shop details to personalize your invoices and bills.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Charcoal
                        )
                    }
                }
            }

            // Shop Name
            OutlinedTextField(
                value = state.shopName,
                onValueChange = viewModel::onShopNameChange,
                label = { Text("Shop / Business Name *") },
                placeholder = { Text("e.g. Gupta General Store") },
                leadingIcon = { Icon(Icons.Default.Store, contentDescription = null, tint = ForestGreen) },
                isError = state.error != null && state.shopName.isBlank(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ForestGreen,
                    unfocusedBorderColor = BorderColor,
                    focusedContainerColor = SurfaceWhite,
                    unfocusedContainerColor = SurfaceWhite
                )
            )

            // Owner Name
            OutlinedTextField(
                value = state.ownerName,
                onValueChange = viewModel::onOwnerNameChange,
                label = { Text("Owner / Shopkeeper Name") },
                placeholder = { Text("e.g. Ramesh Gupta") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = ForestGreen) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ForestGreen,
                    unfocusedBorderColor = BorderColor,
                    focusedContainerColor = SurfaceWhite,
                    unfocusedContainerColor = SurfaceWhite
                )
            )

            // Business Type Dropdown
            ExposedDropdownMenuBox(
                expanded = expandedTypeDropdown,
                onExpandedChange = { expandedTypeDropdown = !expandedTypeDropdown }
            ) {
                OutlinedTextField(
                    value = state.businessType,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category / Business Type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedTypeDropdown) },
                    leadingIcon = { Icon(Icons.Default.Category, contentDescription = null, tint = ForestGreen) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ForestGreen,
                        unfocusedBorderColor = BorderColor,
                        focusedContainerColor = SurfaceWhite,
                        unfocusedContainerColor = SurfaceWhite
                    )
                )

                ExposedDropdownMenu(
                    expanded = expandedTypeDropdown,
                    onDismissRequest = { expandedTypeDropdown = false }
                ) {
                    businessTypes.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type) },
                            onClick = {
                                viewModel.onBusinessTypeChange(type)
                                expandedTypeDropdown = false
                            }
                        )
                    }
                }
            }

            // Phone Number
            OutlinedTextField(
                value = state.phoneNumber,
                onValueChange = viewModel::onPhoneNumberChange,
                label = { Text("Shop Phone / Mobile") },
                placeholder = { Text("e.g. 9876543210") },
                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = ForestGreen) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ForestGreen,
                    unfocusedBorderColor = BorderColor,
                    focusedContainerColor = SurfaceWhite,
                    unfocusedContainerColor = SurfaceWhite
                )
            )

            // Shop Address / City
            OutlinedTextField(
                value = state.address,
                onValueChange = viewModel::onAddressChange,
                label = { Text("Shop Address / Market Location") },
                placeholder = { Text("e.g. Main Market, Station Road") },
                leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = ForestGreen) },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ForestGreen,
                    unfocusedBorderColor = BorderColor,
                    focusedContainerColor = SurfaceWhite,
                    unfocusedContainerColor = SurfaceWhite
                )
            )

            // Error display
            if (state.error != null) {
                Text(
                    text = state.error!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = ErrorRed
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Save & Enter
            PrimaryButton(
                text = "Save & Open DUKAAN",
                onClick = viewModel::completeSetup,
                loading = state.isLoading,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Default.Check
            )
        }
    }
}