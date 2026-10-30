package com.dukaan.app.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.dukaan.app.core.datastore.DukaanPreferences
import com.dukaan.app.core.designsystem.components.*
import com.dukaan.app.core.designsystem.theme.*
import com.dukaan.app.core.navigation.Screen
import com.dukaan.app.domain.model.ShopProfile
import com.dukaan.app.domain.repository.ShopRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@Composable
fun SettingsScreen(navController: NavController) {
    Scaffold(
        topBar = {
            DukaanTopBar(
                title = "Settings & Support",
                onBackClick = { navController.popBackStack() }
            )
        },
        containerColor = WarmCream
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text("BUSINESS PROFILE", style = MaterialTheme.typography.labelSmall, color = SecondaryText, modifier = Modifier.padding(start = 8.dp))
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Storefront,
                    title = "Shop Profile",
                    subtitle = "Store name, owner, contact & address",
                    onClick = { navController.navigate(Screen.ShopProfile.route) }
                )
            }

            item {
                Text("PREFERENCES", style = MaterialTheme.typography.labelSmall, color = SecondaryText, modifier = Modifier.padding(start = 8.dp, top = 12.dp))
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Language,
                    title = "App Language",
                    subtitle = "English, ?????? (Hindi), Hinglish",
                    onClick = { navController.navigate(Screen.LanguageSettings.route) }
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Notifications,
                    title = "Notifications & Reminders",
                    subtitle = "Low stock alerts, daily summaries",
                    onClick = { navController.navigate(Screen.NotificationSettings.route) }
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Backup,
                    title = "Backup & Restore",
                    subtitle = "Export or restore offline database",
                    onClick = { navController.navigate(Screen.BackupRestore.route) }
                )
            }

            item {
                Text("SUPPORT & LEGAL", style = MaterialTheme.typography.labelSmall, color = SecondaryText, modifier = Modifier.padding(start = 8.dp, top = 12.dp))
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Help,
                    title = "Help Center & FAQs",
                    subtitle = "Guides on scanning bills and khata",
                    onClick = { navController.navigate(Screen.HelpCenter.route) }
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "About DUKAAN",
                    subtitle = "Version 1.0.0 � Offline Kirana Assistant",
                    onClick = { navController.navigate(Screen.About.route) }
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.PrivacyTip,
                    title = "Privacy Policy",
                    subtitle = "Zero cloud data collection guarantee",
                    onClick = { navController.navigate(Screen.PrivacyPolicy.route) }
                )
            }
        }
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = Charcoal)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = SecondaryText)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = SecondaryText)
        }
    }
}

@HiltViewModel
class ShopProfileViewModel @Inject constructor(
    private val shopRepository: ShopRepository,
    private val preferences: DukaanPreferences
) : ViewModel() {
    private val _profile = MutableStateFlow<ShopProfile?>(null)
    val profile = _profile.asStateFlow()

    init {
        viewModelScope.launch {
            shopRepository.getShopProfile().collect { _profile.value = it }
        }
    }

    fun save(updated: ShopProfile) {
        viewModelScope.launch { shopRepository.saveShopProfile(updated) }
    }
}

@Composable
fun ShopProfileScreen(navController: NavController, viewModel: ShopProfileViewModel = hiltViewModel()) {
    val profile by viewModel.profile.collectAsState()
    val p = profile

    var shopName by remember(p) { mutableStateOf(p?.shopName ?: "") }
    var ownerName by remember(p) { mutableStateOf(p?.ownerName ?: "") }
    var phone by remember(p) { mutableStateOf(p?.phoneNumber ?: "") }
    var address by remember(p) { mutableStateOf(p?.address ?: "") }

    Scaffold(
        topBar = { DukaanTopBar("Shop Profile", onBackClick = { navController.popBackStack() }) },
        containerColor = WarmCream
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = shopName, onValueChange = { shopName = it },
                label = { Text("Shop Name") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ForestGreen, unfocusedBorderColor = BorderColor, focusedContainerColor = SurfaceWhite, unfocusedContainerColor = SurfaceWhite)
            )
            OutlinedTextField(
                value = ownerName, onValueChange = { ownerName = it },
                label = { Text("Owner Name") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ForestGreen, unfocusedBorderColor = BorderColor, focusedContainerColor = SurfaceWhite, unfocusedContainerColor = SurfaceWhite)
            )
            OutlinedTextField(
                value = phone, onValueChange = { phone = it },
                label = { Text("Phone Number") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ForestGreen, unfocusedBorderColor = BorderColor, focusedContainerColor = SurfaceWhite, unfocusedContainerColor = SurfaceWhite)
            )
            OutlinedTextField(
                value = address, onValueChange = { address = it },
                label = { Text("Address") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ForestGreen, unfocusedBorderColor = BorderColor, focusedContainerColor = SurfaceWhite, unfocusedContainerColor = SurfaceWhite)
            )
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = {
                    if (p != null && shopName.isNotBlank()) {
                        viewModel.save(p.copy(shopName = shopName.trim(), ownerName = ownerName.trim(), phoneNumber = phone.trim(), address = address.trim()))
                        navController.popBackStack()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("Save Changes", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
fun LanguageSettingsScreen(navController: NavController) {
    Scaffold(
        topBar = { DukaanTopBar("Language Settings", onBackClick = { navController.popBackStack() }) },
        containerColor = WarmCream
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceWhite), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("English (Default)", style = MaterialTheme.typography.titleMedium)
                    Icon(Icons.Default.Check, contentDescription = null, tint = ForestGreen)
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceWhite), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("?????? (Hindi)", style = MaterialTheme.typography.titleMedium)
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceWhite), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Hinglish", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val preferences: DukaanPreferences
) : ViewModel() {
    val notifLowStock = preferences.notifLowStock
    val notifPaymentReminder = preferences.notifPaymentReminder

    fun setLowStock(v: Boolean) { viewModelScope.launch { preferences.setNotifLowStock(v) } }
    fun setPaymentReminder(v: Boolean) { viewModelScope.launch { preferences.setNotifPaymentReminder(v) } }
}

@Composable
fun NotificationSettingsScreen(navController: NavController, viewModel: NotificationSettingsViewModel = hiltViewModel()) {
    val lowStock by viewModel.notifLowStock.collectAsState(initial = true)
    val paymentReminders by viewModel.notifPaymentReminder.collectAsState(initial = true)

    Scaffold(
        topBar = { DukaanTopBar("Notification Settings", onBackClick = { navController.popBackStack() }) },
        containerColor = WarmCream
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceWhite), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Low Stock Alerts", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                        Text("Alert when item stock dips below minimum threshold", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                    }
                    Switch(checked = lowStock, onCheckedChange = { viewModel.setLowStock(it) })
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceWhite), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Customer Payment Follow-ups", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                        Text("Remind to follow up on pending udhar weekly", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                    }
                    Switch(checked = paymentReminders, onCheckedChange = { viewModel.setPaymentReminder(it) })
                }
            }
        }
    }
}

@Composable
fun HelpCenterScreen(navController: NavController) {
    Scaffold(
        topBar = { DukaanTopBar("Help & Tutorials", onBackClick = { navController.popBackStack() }) },
        containerColor = WarmCream
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = SurfaceWhite), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("How to scan supplier invoices?", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = ForestGreen)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Place your wholesaler bill flat under good lighting. Tap 'Scan Bill' on Home, take a clear photo. DUKAAN automatically reads the items and asks you to confirm them into inventory.", style = MaterialTheme.typography.bodyMedium, color = Charcoal)
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = SurfaceWhite), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("How to maintain Udhar Khata?", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = ForestGreen)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Select customer name during New Sale or visit Khata tab to record Give Udhar (+) or Got Payment (-). You can share WhatsApp payment reminders with one click.", style = MaterialTheme.typography.bodyMedium, color = Charcoal)
                    }
                }
            }
        }
    }
}

@Composable
fun AboutScreen(navController: NavController) {
    Scaffold(
        topBar = { DukaanTopBar("About DUKAAN", onBackClick = { navController.popBackStack() }) },
        containerColor = WarmCream
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Storefront, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(72.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text("DUKAAN", style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold), color = ForestGreen)
            Text("Version 1.0.0", style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
            Spacer(modifier = Modifier.height(16.dp))
            Text("A simple, 100% offline digital business companion built for Indian Kirana & Retail Shopkeepers.", textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.bodyLarge, color = Charcoal)
        }
    }
}

@Composable
fun PrivacyPolicyScreen(navController: NavController) {
    Scaffold(
        topBar = { DukaanTopBar("Privacy Policy", onBackClick = { navController.popBackStack() }) },
        containerColor = WarmCream
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
            Text("Your Data Belongs to You", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = ForestGreen)
            Spacer(modifier = Modifier.height(12.dp))
            Text("DUKAAN is built on a 100% offline, privacy-first architecture. None of your sales records, customer balances, supplier bills, or shop accounts are transmitted to external cloud servers. All computations and OCR run directly on your phone.", style = MaterialTheme.typography.bodyMedium, color = Charcoal)
        }
    }
}