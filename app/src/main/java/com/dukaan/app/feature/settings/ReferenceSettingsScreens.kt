package com.dukaan.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.dukaan.app.core.designsystem.components.*
import com.dukaan.app.core.designsystem.theme.*
import com.dukaan.app.core.datastore.DukaanPreferences
import com.dukaan.app.core.navigation.Screen
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.dukaan.app.core.util.DateFormatter
import com.dukaan.app.core.worker.MonthlyKhataSmsWorker
import com.dukaan.app.core.util.UuidGenerator
import com.dukaan.app.domain.model.Product
import com.dukaan.app.domain.model.ShopProfile
import com.dukaan.app.domain.model.StockMovement
import com.dukaan.app.domain.model.StockMovementType
import com.dukaan.app.domain.repository.ProductRepository
import com.dukaan.app.domain.repository.ShopRepository
import com.dukaan.app.domain.repository.StockMovementRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LowStockViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val stockMovementRepository: StockMovementRepository
) : ViewModel() {
    private val _lowStockProducts = MutableStateFlow<List<Product>>(emptyList())
    val lowStockProducts = _lowStockProducts.asStateFlow()

    init {
        viewModelScope.launch {
            productRepository.getLowStockProducts().collect { list ->
                _lowStockProducts.value = list
            }
        }
    }

    fun restock(product: Product, quantityToAdd: Double) {
        viewModelScope.launch {
            val newStock = product.currentStock + quantityToAdd
            val now = System.currentTimeMillis()
            productRepository.updateStock(product.id, newStock, product.purchasePricePaise)
            stockMovementRepository.insertMovement(
                StockMovement(
                    id = UuidGenerator.generate(),
                    productId = product.id,
                    productName = product.name,
                    type = StockMovementType.MANUAL_ADD,
                    quantityChange = quantityToAdd,
                    quantityAfter = newStock,
                    notes = "Restock alert action",
                    createdAt = now
                )
            )
        }
    }
}

@HiltViewModel
class SettingsReferenceViewModel @Inject constructor(
    private val preferences: DukaanPreferences,
    private val shopRepository: ShopRepository
) : ViewModel() {
    private val _shopProfile = MutableStateFlow<ShopProfile?>(null)
    val shopProfile = _shopProfile.asStateFlow()

    private val _geminiKey = MutableStateFlow("")
    val geminiKey = _geminiKey.asStateFlow()

    private val _geminiModel = MutableStateFlow("gemini-1.5-flash")
    val geminiModel = _geminiModel.asStateFlow()

    init {
        viewModelScope.launch {
            shopRepository.getShopProfile().collect {
                _shopProfile.value = it
            }
        }
        viewModelScope.launch {
            preferences.geminiApiKey.collect { _geminiKey.value = it }
        }
        viewModelScope.launch {
            preferences.geminiModel.collect { _geminiModel.value = it }
        }
    }

    val autoMonthlySms = preferences.autoMonthlySms
    val nextMonthlySmsDate = preferences.nextMonthlySmsDate

    fun setAutoMonthlySms(enabled: Boolean, context: Context) {
        viewModelScope.launch {
            preferences.setAutoMonthlySms(enabled)
            if (enabled) {
                MonthlyKhataSmsWorker.schedule(context, preferences)
            } else {
                MonthlyKhataSmsWorker.cancel(context)
            }
        }
    }

    fun saveGeminiConfig(key: String, model: String) {
        viewModelScope.launch {
            preferences.setGeminiApiKey(key)
            preferences.setGeminiModel(model)
        }
    }
}

// =================== SCREEN 24: LOW STOCK ALERTS ===================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferenceLowStockAlertsScreen(
    navController: NavController,
    viewModel: LowStockViewModel = hiltViewModel()
) {
    val lowStockList by viewModel.lowStockProducts.collectAsState()
    var selectedProductForRestock by remember { mutableStateOf<Product?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Low Stock Alerts", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = PrimaryText) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PrimaryText)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WarmCream)
            )
        },
        containerColor = WarmCream
    ) { paddingValues ->
        if (lowStockList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(56.dp))
                    Text("All Items Well Stocked", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = PrimaryText)
                    Text("No products are running low right now", style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(lowStockList) { prod ->
                    LowStockAlertItemCard(
                        name = prod.name,
                        remaining = "${prod.currentStock.toInt()} ${prod.unit.name.lowercase()} left",
                        minStock = "Min stock: ${prod.minStockLevel.toInt()} ${prod.unit.name.lowercase()}",
                        onRestock = { selectedProductForRestock = prod }
                    )
                }
            }
        }
    }

    if (selectedProductForRestock != null) {
        val prod = selectedProductForRestock!!
        var addQty by remember { mutableStateOf("10") }

        AlertDialog(
            onDismissRequest = { selectedProductForRestock = null },
            title = { Text("Restock ${prod.name}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Enter quantity to add to inventory:", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = addQty,
                        onValueChange = { addQty = it },
                        label = { Text("Quantity (${prod.unit.name.lowercase()})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val qty = addQty.toDoubleOrNull() ?: 10.0
                        viewModel.restock(prod, qty)
                        selectedProductForRestock = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
                ) { Text("Confirm Restock") }
            },
            dismissButton = {
                TextButton(onClick = { selectedProductForRestock = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun LowStockAlertItemCard(
    name: String,
    remaining: String,
    minStock: String,
    onRestock: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GroceryProductThumbnail(productName = name, modifier = Modifier.size(50.dp))
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = PrimaryText)
                    Text(remaining, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFFD32F2F))
                    Text(minStock, style = MaterialTheme.typography.labelSmall, color = SecondaryText)
                }
            }

            // Orange "Restock" Button matching reference
            Button(
                onClick = onRestock,
                colors = ButtonDefaults.buttonColors(containerColor = SaffronOrange),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Restock",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = SurfaceWhite
                )
            }
        }
    }
}

// =================== SCREEN 25: SETTINGS ===================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferenceSettingsScreen(
    navController: NavController,
    viewModel: SettingsReferenceViewModel = hiltViewModel()
) {
    val shopProfile by viewModel.shopProfile.collectAsState()
    val geminiKey by viewModel.geminiKey.collectAsState()
    val geminiModel by viewModel.geminiModel.collectAsState()
    var showGeminiDialog by remember { mutableStateOf(false) }
    var showMonthlySmsDialog by remember { mutableStateOf(false) }
    val autoMonthlySms by viewModel.autoMonthlySms.collectAsState(initial = false)
    val nextMonthlySmsDate by viewModel.nextMonthlySmsDate.collectAsState(initial = 0L)
    val context = androidx.compose.ui.platform.LocalContext.current

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.setAutoMonthlySms(true, context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = PrimaryText) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PrimaryText)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WarmCream)
            )
        },
        containerColor = WarmCream
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // Profile Card (Ramangee Singh - 9876543210)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(LightGreenSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(34.dp))
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column {
                            Text(
                                text = shopProfile?.ownerName?.takeIf { it.isNotBlank() } ?: shopProfile?.shopName?.takeIf { it.isNotBlank() } ?: "Shop Owner",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = PrimaryText
                            )
                            Text(
                                text = shopProfile?.phoneNumber?.takeIf { it.isNotBlank() } ?: shopProfile?.businessType?.takeIf { it.isNotBlank() } ?: "DukaanAI Store",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SecondaryText
                            )
                        }
                    }
                }
            }

            // Grouped Settings List matching reference
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        ReferenceSettingsRow(
                            icon = Icons.Default.Storefront,
                            title = "Shop Details",
                            badge = null,
                            onClick = { navController.navigate(Screen.ShopProfile.route) }
                        )
                        HorizontalDivider(color = BorderColor.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))

                        // AI Scanner Gemini API Configuration Option
                        ReferenceSettingsRow(
                            icon = Icons.Default.DocumentScanner,
                            title = "AI Bill Scanner (Gemini)",
                            badge = if (geminiKey.isNotBlank()) "Configured" else "Setup API Key",
                            onClick = { showGeminiDialog = true }
                        )
                        HorizontalDivider(color = BorderColor.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))

                        ReferenceSettingsRow(
                            icon = Icons.Default.Language,
                            title = "Language",
                            badge = "English",
                            onClick = { navController.navigate(Screen.LanguageSelection.route) }
                        )
                        HorizontalDivider(color = BorderColor.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))

                        ReferenceSettingsRow(
                            icon = Icons.Default.NotificationsNone,
                            title = "Notifications",
                            badge = null,
                            onClick = { navController.navigate(Screen.NotificationSettings.route) }
                        )
                        HorizontalDivider(color = BorderColor.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))

                        ReferenceSettingsRow(
                            icon = Icons.Default.CloudSync,
                            title = "Backup & Sync",
                            badge = null,
                            onClick = { navController.navigate(Screen.BackupRestore.route) }
                        )
                        HorizontalDivider(color = BorderColor.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))

                        ReferenceSettingsRow(
                            icon = Icons.Default.Lock,
                            title = "App Lock",
                            badge = "PIN enabled",
                            onClick = { navController.navigate(Screen.SetPin.route) }
                        )
                        HorizontalDivider(color = BorderColor.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))

                        ReferenceSettingsRow(
                            icon = Icons.Default.HelpOutline,
                            title = "Help & Support",
                            badge = null,
                            onClick = { navController.navigate(Screen.HelpCenter.route) }
                        )
                        HorizontalDivider(color = BorderColor.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))

                        ReferenceSettingsRow(
                            icon = Icons.Default.Info,
                            title = "About App",
                            badge = null,
                            onClick = { navController.navigate(Screen.About.route) }
                        )
                    }
                }
            }

            // Logout row at bottom (red)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            navController.navigate(Screen.Login.route) {
                                popUpTo(0)
                            }
                        },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = "Logout", tint = Color(0xFFD32F2F))
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            "Logout",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFD32F2F)
                        )
                    }
                }
            }
        }
    }

    if (showMonthlySmsDialog) {
        AlertDialog(
            onDismissRequest = { showMonthlySmsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Sms, contentDescription = null, tint = ForestGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Monthly SMS Reminders", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        "Automatically send payment reminders from your phone's SIM card to all customers who have pending balances at the end of every calendar month at 8:00 PM.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SecondaryText
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Automatic Monthly SMS", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Text(if (autoMonthlySms) "Reminders will send automatically" else "Disabled", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                        }
                        Switch(
                            checked = autoMonthlySms,
                            onCheckedChange = { isChecked ->
                                if (isChecked) {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                                        viewModel.setAutoMonthlySms(true, context)
                                    } else {
                                        smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                                    }
                                } else {
                                    viewModel.setAutoMonthlySms(false, context)
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = ForestGreen, checkedTrackColor = ForestGreen.copy(alpha = 0.5f))
                        )
                    }

                    if (autoMonthlySms) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = LightGreenSurface),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Schedule Status", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = PrimaryText)
                                val nextTimestamp = if (nextMonthlySmsDate > 0L) nextMonthlySmsDate else MonthlyKhataSmsWorker.calculateNextMonthEndTimestamp()
                                Text("Next Run:  at 8:00 PM", style = MaterialTheme.typography.bodySmall, color = ForestGreen, fontWeight = FontWeight.SemiBold)
                                Text("Applies to all customers with balance > ?0", style = MaterialTheme.typography.labelSmall, color = SecondaryText)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showMonthlySmsDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
                ) {
                    Text("Done")
                }
            }
        )
    }

    if (showGeminiDialog) {
        var keyInput by remember { mutableStateOf(geminiKey) }
        var modelInput by remember { mutableStateOf(geminiModel) }

        AlertDialog(
            onDismissRequest = { showGeminiDialog = false },
            title = { Text("Gemini API Setup", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Configure your Gemini API key and model for AI Invoice extraction.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SecondaryText
                    )
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = { Text("Gemini API Key") },
                        placeholder = { Text("AIzaSy...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = modelInput,
                        onValueChange = { modelInput = it },
                        label = { Text("Model Name") },
                        placeholder = { Text("gemini-1.5-flash") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveGeminiConfig(keyInput.trim(), modelInput.trim().ifBlank { "gemini-1.5-flash" })
                        showGeminiDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGeminiDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ReferenceSettingsRow(
    icon: ImageVector,
    title: String,
    badge: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = title, tint = ForestGreen, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                color = PrimaryText
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (badge != null) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryText
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = SecondaryText,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

