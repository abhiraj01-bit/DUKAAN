package com.dukaan.app.feature.customers

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
import com.dukaan.app.core.navigation.Screen
import com.dukaan.app.core.util.CurrencyFormatter
import com.dukaan.app.core.util.DateFormatter
import com.dukaan.app.core.util.UuidGenerator
import com.dukaan.app.domain.model.Customer
import com.dukaan.app.domain.model.KhataTransaction
import com.dukaan.app.domain.model.KhataTransactionType
import com.dukaan.app.domain.repository.CustomerRepository
import com.dukaan.app.domain.repository.KhataRepository
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.dukaan.app.data.local.entity.KhataSmsEntity
import com.dukaan.app.domain.repository.KhataSmsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CustomerListViewModel @Inject constructor(
    private val customerRepository: CustomerRepository
) : ViewModel() {
    private val _customers = MutableStateFlow<List<Customer>>(emptyList())
    val customers = _customers.asStateFlow()

    init {
        viewModelScope.launch {
            customerRepository.getAllActiveCustomers().collect { list ->
                _customers.value = list
            }
        }
    }
}

sealed interface SmsReminderUiState {
    object Idle : SmsReminderUiState
    object Sending : SmsReminderUiState
    data class Success(val message: String) : SmsReminderUiState
    data class Error(val errorMessage: String) : SmsReminderUiState
}

@HiltViewModel
class CustomerLedgerViewModel @Inject constructor(
    private val customerRepository: CustomerRepository,
    private val khataRepository: KhataRepository,
    private val khataSmsRepository: KhataSmsRepository
) : ViewModel() {
    private val _customer = MutableStateFlow<Customer?>(null)
    val customer = _customer.asStateFlow()

    private val _transactions = MutableStateFlow<List<KhataTransaction>>(emptyList())
    val transactions = _transactions.asStateFlow()

    private val _smsState = MutableStateFlow<SmsReminderUiState>(SmsReminderUiState.Idle)
    val smsState = _smsState.asStateFlow()

    private val _smsHistory = MutableStateFlow<List<KhataSmsEntity>>(emptyList())
    val smsHistory = _smsHistory.asStateFlow()

    fun load(customerId: String) {
        viewModelScope.launch {
            customerRepository.getCustomerById(customerId).collect {
                _customer.value = it
            }
        }
        viewModelScope.launch {
            khataRepository.getTransactionsByCustomer(customerId).collect {
                _transactions.value = it
            }
        }
        viewModelScope.launch {
            khataSmsRepository.getSmsHistoryByCustomer(customerId).collect {
                _smsHistory.value = it
            }
        }
    }

    fun sendSmsReminder(customerId: String) {
        val cust = _customer.value ?: return
        val phone = cust.phoneNumber?.trim() ?: ""
        if (phone.isBlank()) {
            _smsState.value = SmsReminderUiState.Error("Customer has no phone number on record")
            return
        }
        if (cust.outstandingPaise <= 0L) {
            _smsState.value = SmsReminderUiState.Error("Customer has zero balance (?0 udhaar)")
            return
        }

        viewModelScope.launch {
            _smsState.value = SmsReminderUiState.Sending
            val result = khataSmsRepository.sendManualReminder(customerId)
            if (result.isSuccess) {
                _smsState.value = SmsReminderUiState.Success("SMS sent directly to  () via SIM")
            } else {
                _smsState.value = SmsReminderUiState.Error(result.exceptionOrNull()?.message ?: "Failed to send SMS")
            }
        }
    }

    fun resetSmsState() {
        _smsState.value = SmsReminderUiState.Idle
    }
}

@HiltViewModel
class AddTransactionViewModel @Inject constructor(
    private val customerRepository: CustomerRepository,
    private val khataRepository: KhataRepository
) : ViewModel() {
    private val _customer = MutableStateFlow<Customer?>(null)
    val customer = _customer.asStateFlow()

    fun load(customerId: String) {
        viewModelScope.launch {
            customerRepository.getCustomerById(customerId).collect {
                _customer.value = it
            }
        }
    }

    fun saveTransaction(
        customerId: String,
        isCredit: Boolean,
        amountPaise: Long,
        note: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val txn = KhataTransaction(
                id = UuidGenerator.generate(),
                customerId = customerId,
                type = if (isCredit) KhataTransactionType.CREDIT else KhataTransactionType.PAYMENT,
                amountPaise = amountPaise,
                notes = note,
                createdAt = now,
                updatedAt = now
            )
            khataRepository.insertTransaction(txn)

            // Update customer balance
            val delta = if (isCredit) amountPaise else -amountPaise
            val creditDelta = if (isCredit) amountPaise else 0L
            val paymentDelta = if (!isCredit) amountPaise else 0L
            customerRepository.updateFinancials(customerId, delta, creditDelta, paymentDelta)

            onSuccess()
        }
    }
}

// =================== SCREEN 18: ADD CUSTOMER ===================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferenceAddCustomerScreen(
    navController: NavController,
    viewModel: AddEditCustomerViewModel = hiltViewModel()
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Customer", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = PrimaryText) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Profile avatar with camera badge
                Box(
                    modifier = Modifier
                        .size(86.dp)
                        .clip(CircleShape)
                        .background(LightGreenSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(48.dp))
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .align(Alignment.BottomEnd)
                            .clip(CircleShape)
                            .background(ForestGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = SurfaceWhite, modifier = Modifier.size(14.dp))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Customer Name") },
                    placeholder = { Text("e.g. Ramesh Kumar") },
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

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    placeholder = { Text("e.g. 9876543210") },
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

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address (Optional)") },
                    placeholder = { Text("e.g. Sector 21, Chandigarh") },
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
            }

            PrimaryButton(
                text = "Save Customer",
                onClick = {
                    if (name.isNotBlank()) {
                        viewModel.onNameChange(name)
                        viewModel.onPhoneChange(phone)
                        viewModel.onAddressChange(address)
                        viewModel.saveCustomer()
                        navController.popBackStack()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp).padding(bottom = 12.dp)
            )
        }
    }
}

// =================== SCREEN 19: CUSTOMER LIST ===================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferenceCustomerListScreen(
    navController: NavController,
    viewModel: CustomerListViewModel = hiltViewModel()
) {
    val customers by viewModel.customers.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    val filteredCustomers = remember(customers, searchQuery) {
        if (searchQuery.isBlank()) customers
        else customers.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    (it.phoneNumber?.contains(searchQuery) == true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Customers", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = PrimaryText) },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = PrimaryText)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WarmCream)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Screen.AddCustomer.route) },
                containerColor = ForestGreen,
                contentColor = SurfaceWhite
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Customer")
            }
        },
        containerColor = WarmCream
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                DukaanSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "Search customers..."
                )
            }

            if (filteredCustomers.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.PeopleOutline, contentDescription = null, tint = SecondaryText, modifier = Modifier.size(56.dp))
                        Text("No customers found", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = PrimaryText)
                        Text("Tap + to add your first customer to Khata", style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredCustomers) { customer ->
                        val initials = getInitials(customer.name)
                        val (bg, fg) = getAvatarColors(initials)

                        CustomerListItemRow(
                            initials = initials,
                            name = customer.name,
                            phone = customer.phoneNumber ?: "",
                            amount = CurrencyFormatter.formatPaise(customer.outstandingPaise),
                            initialsBg = bg,
                            initialsColor = fg,
                            onClick = { navController.navigate(Screen.CustomerLedger.createRoute(customer.id)) }
                        )
                    }
                }
            }
        }
    }
}

private fun getInitials(name: String): String {
    val parts = name.trim().split(" ")
    return when {
        parts.size >= 2 -> "${parts[0].firstOrNull() ?: ' '}${parts[1].firstOrNull() ?: ' '}".uppercase()
        parts.isNotEmpty() && parts[0].isNotEmpty() -> parts[0].take(2).uppercase()
        else -> "C"
    }
}

private fun getAvatarColors(initials: String): Pair<Color, Color> {
    return when (initials.firstOrNull()) {
        'R' -> Pair(Color(0xFFFFE0B2), Color(0xFFE65100))
        'S' -> Pair(Color(0xFFE1F5FE), Color(0xFF0288D1))
        'A' -> Pair(Color(0xFFFFCDD2), Color(0xFFC62828))
        'V' -> Pair(Color(0xFFE8F5E9), Color(0xFF2E7D32))
        'P' -> Pair(Color(0xFFEDE7F6), Color(0xFF512DA8))
        else -> Pair(LightGreenSurface, ForestGreen)
    }
}

@Composable
private fun CustomerListItemRow(
    initials: String,
    name: String,
    phone: String,
    amount: String,
    initialsBg: Color,
    initialsColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(initialsBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = initialsColor
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = PrimaryText)
                    Text(phone, style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                }
            }

            Text(
                text = amount,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = PrimaryText
            )
        }
    }
}

// =================== SCREEN 20: CUSTOMER LEDGER ===================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferenceCustomerLedgerScreen(
    navController: NavController,
    customerId: String,
    viewModel: CustomerLedgerViewModel = hiltViewModel()
) {
    var selectedTab by remember { mutableStateOf("Transactions") }
    val customer by viewModel.customer.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val smsState by viewModel.smsState.collectAsState()
    val smsHistory by viewModel.smsHistory.collectAsState()
    val context = LocalContext.current

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.sendSmsReminder(customerId)
        } else {
            // Permission denied
        }
    }

    LaunchedEffect(customerId) {
        viewModel.load(customerId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(LightGreenSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = customer?.name ?: "Customer",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = PrimaryText
                            )
                            Text(
                                text = customer?.phoneNumber ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = SecondaryText
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PrimaryText)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                            viewModel.sendSmsReminder(customerId)
                        } else {
                            smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                        }
                    }) {
                        Icon(Icons.Default.Send, contentDescription = "Send SMS Reminder", tint = ForestGreen)
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Segmented Tabs: Transactions & Summary
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceWhite, RoundedCornerShape(12.dp))
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedTab == "Transactions") LightGreenSurface else Color.Transparent)
                            .clickable { selectedTab = "Transactions" },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Transactions",
                            fontWeight = FontWeight.Bold,
                            color = if (selectedTab == "Transactions") ForestGreen else SecondaryText
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedTab == "Summary") LightGreenSurface else Color.Transparent)
                            .clickable { selectedTab = "Summary" },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Summary",
                            fontWeight = FontWeight.Bold,
                            color = if (selectedTab == "Summary") ForestGreen else SecondaryText
                        )
                    }
                }
            }

            // Outstanding Balance Hero Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Outstanding Balance", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = CurrencyFormatter.formatPaise(customer?.outstandingPaise ?: 0L),
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 30.sp
                            ),
                            color = PrimaryText
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Two action buttons: Orange "Add Credit" and Green "Record Payment"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { navController.navigate(Screen.AddTransaction.createRoute(customerId)) },
                                colors = ButtonDefaults.buttonColors(containerColor = SaffronOrange),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(46.dp)
                            ) {
                                Text("Add Credit", fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { navController.navigate(Screen.AddTransaction.createRoute(customerId)) },
                                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(46.dp)
                            ) {
                                Text("Record Payment", fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // FEATURE 1: Send SMS Reminder Button
                        Button(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                                    viewModel.sendSmsReminder(customerId)
                                } else {
                                    smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                                }
                            },
                            enabled = smsState !is SmsReminderUiState.Sending,
                            colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            if (smsState is SmsReminderUiState.Sending) {
                                CircularProgressIndicator(color = SurfaceWhite, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sending SMS from SIM...", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Send SMS Reminder", fontWeight = FontWeight.Bold)
                            }
                        }

                        // Live SMS Feedback Banners
                        when (val state = smsState) {
                            is SmsReminderUiState.Success -> {
                                Spacer(modifier = Modifier.height(8.dp))
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = LightGreenSurface),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(state.message, style = MaterialTheme.typography.bodySmall, color = PrimaryText, modifier = Modifier.weight(1f))
                                        IconButton(onClick = viewModel::resetSmsState, modifier = Modifier.size(20.dp)) {
                                            Icon(Icons.Default.Close, contentDescription = "Close", tint = SecondaryText, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                            is SmsReminderUiState.Error -> {
                                Spacer(modifier = Modifier.height(8.dp))
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = LightRedSurface),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(state.errorMessage, style = MaterialTheme.typography.bodySmall, color = PrimaryText, modifier = Modifier.weight(1f))
                                        IconButton(onClick = viewModel::resetSmsState, modifier = Modifier.size(20.dp)) {
                                            Icon(Icons.Default.Close, contentDescription = "Close", tint = SecondaryText, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                            else -> {}
                        }

                        // Last reminder date indicator
                        val lastSms = smsHistory.firstOrNull()
                        if (lastSms != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Last reminder:  • Status: ",
                                style = MaterialTheme.typography.labelSmall,
                                color = SecondaryText
                            )
                        }
                    }
                }
            }

            // Filter row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = true,
                        onClick = {},
                        label = { Text("All") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ForestGreen,
                            selectedLabelColor = SurfaceWhite
                        )
                    )
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.CalendarToday, contentDescription = "Date", tint = PrimaryText)
                    }
                }
            }

            // Real Ledger Rows from Room DB
            if (transactions.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No transactions yet for this customer", color = SecondaryText)
                    }
                }
            } else {
                items(transactions) { txn ->
                    val isCredit = txn.type == KhataTransactionType.CREDIT
                    LedgerTransactionItemCard(
                        date = DateFormatter.formatDate(txn.createdAt),
                        desc = if (!txn.notes.isNullOrBlank()) txn.notes else if (isCredit) "Purchase" else "Payment",
                        amount = "${if (isCredit) "+" else "-"}${CurrencyFormatter.formatPaise(txn.amountPaise)}",
                        isCredit = isCredit
                    )
                }
            }
        }
    }
}

@Composable
private fun LedgerTransactionItemCard(
    date: String,
    desc: String,
    amount: String,
    isCredit: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(date, style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                Text(desc, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = PrimaryText)
            }

            Text(
                text = amount,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = if (isCredit) Color(0xFFD32F2F) else SuccessGreen
            )
        }
    }
}

// =================== SCREEN 21: ADD CREDIT / PAYMENT (ADD TRANSACTION) ===================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferenceAddTransactionScreen(
    navController: NavController,
    customerId: String,
    viewModel: AddTransactionViewModel = hiltViewModel()
) {
    var isCreditSelected by remember { mutableStateOf(true) }
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val customer by viewModel.customer.collectAsState()

    LaunchedEffect(customerId) {
        viewModel.load(customerId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Add Transaction", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = PrimaryText)
                        Text(customer?.name ?: "Customer", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                    }
                },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Segmented Toggle: Credit vs Payment
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceWhite, RoundedCornerShape(12.dp))
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isCreditSelected) ForestGreen else Color.Transparent)
                            .clickable { isCreditSelected = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Credit",
                            fontWeight = FontWeight.Bold,
                            color = if (isCreditSelected) SurfaceWhite else PrimaryText
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (!isCreditSelected) ForestGreen else Color.Transparent)
                            .clickable { isCreditSelected = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Payment",
                            fontWeight = FontWeight.Bold,
                            color = if (!isCreditSelected) SurfaceWhite else PrimaryText
                        )
                    }
                }

                // Amount
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    prefix = { Text("â‚¹ ") },
                    placeholder = { Text("0") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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

                // Date Field (Displays today)
                OutlinedTextField(
                    value = DateFormatter.formatDate(System.currentTimeMillis()),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Date") },
                    trailingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
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

                // Description (Optional)
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (Optional)") },
                    placeholder = { Text(if (isCreditSelected) "e.g. Groceries" else "e.g. Cash received") },
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
            }

            PrimaryButton(
                text = "Save",
                onClick = {
                    val amountNum = amount.toDoubleOrNull() ?: 0.0
                    if (amountNum > 0) {
                        val paise = (amountNum * 100).toLong()
                        viewModel.saveTransaction(
                            customerId = customerId,
                            isCredit = isCreditSelected,
                            amountPaise = paise,
                            note = description.ifBlank { if (isCreditSelected) "Purchase" else "Payment" },
                            onSuccess = { navController.popBackStack() }
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp).padding(bottom = 12.dp)
            )
        }
    }
}

