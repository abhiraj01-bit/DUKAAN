package com.dukaan.app.feature.expenses

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import com.dukaan.app.core.navigation.Screen
import com.dukaan.app.core.util.CurrencyFormatter
import com.dukaan.app.core.util.DateFormatter
import com.dukaan.app.core.util.UuidGenerator
import com.dukaan.app.domain.model.Expense
import com.dukaan.app.domain.model.ExpenseCategory
import com.dukaan.app.domain.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExpenseViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository
) : ViewModel() {
    private val _expenses = MutableStateFlow<List<Expense>>(emptyList())
    val expenses = _expenses.asStateFlow()

    init {
        viewModelScope.launch {
            expenseRepository.getAllExpenses().collect { list ->
                _expenses.value = list
            }
        }
    }

    fun addExpense(title: String, category: ExpenseCategory, amountRupees: String, onDone: () -> Unit) {
        val paise = CurrencyFormatter.rupeesToPaise(amountRupees)
        if (title.isBlank() || paise <= 0) return

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val expense = Expense(
                id = UuidGenerator.generate(),
                title = title.trim(),
                category = category,
                amountPaise = paise,
                date = now,
                createdAt = now,
                updatedAt = now
            )
            expenseRepository.saveExpense(expense)
            onDone()
        }
    }
}

@Composable
fun ExpenseListScreen(
    navController: NavController,
    viewModel: ExpenseViewModel = hiltViewModel()
) {
    val expenses by viewModel.expenses.collectAsState()
    val totalExpensePaise = expenses.sumOf { it.amountPaise }

    Scaffold(
        topBar = {
            DukaanTopBar(
                title = "Business Expenses",
                onBackClick = { navController.popBackStack() }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Screen.AddExpense.route) },
                containerColor = ForestGreen,
                contentColor = SurfaceWhite
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Expense")
            }
        },
        containerColor = WarmCream
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Surface(
                color = SurfaceWhite,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Total Expenses Logged", style = MaterialTheme.typography.titleMedium, color = Charcoal)
                    Text(
                        CurrencyFormatter.formatPaise(totalExpensePaise),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = ErrorRed
                    )
                }
            }

            if (expenses.isEmpty()) {
                EmptyState(
                    title = "No expenses recorded",
                    subtitle = "Track rent, electricity, transport, packaging, and tea expenses.",
                    icon = Icons.Default.AccountBalanceWallet,
                    actionText = "Add Expense",
                    onAction = { navController.navigate(Screen.AddExpense.route) }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(expenses) { exp ->
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
                                    Text(exp.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                                    Text(
                                        "${exp.category.name} • ${DateFormatter.formatShortDate(exp.date)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SecondaryText
                                    )
                                }
                                Text(
                                    CurrencyFormatter.formatPaise(exp.amountPaise),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = ErrorRed
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddEditExpenseScreen(
    navController: NavController,
    expenseId: String? = null,
    viewModel: ExpenseViewModel = hiltViewModel()
) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(ExpenseCategory.SHOP_RENT) }

    Scaffold(
        topBar = {
            DukaanTopBar(
                title = "Add Expense",
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
                value = title,
                onValueChange = { title = it },
                label = { Text("Expense Title / Note *") },
                placeholder = { Text("e.g. Electricity Bill, Shop Rent, Packaging") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Amount (₹) *") },
                placeholder = { Text("0.00") },
                leadingIcon = { Icon(Icons.Default.CurrencyRupee, contentDescription = null, tint = ErrorRed) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Text("Category", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Charcoal)

            val categoryList = listOf(
                Pair(ExpenseCategory.SHOP_RENT, "Rent"),
                Pair(ExpenseCategory.ELECTRICITY_AND_UTILITIES, "Utilities / Electricity"),
                Pair(ExpenseCategory.TRANSPORT_AND_FREIGHT, "Transport / Auto"),
                Pair(ExpenseCategory.PACKAGING, "Packaging / Polythene"),
                Pair(ExpenseCategory.TEA_AND_REFRESHMENTS, "Tea & Refreshments"),
                Pair(ExpenseCategory.OTHER, "Other")
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                categoryList.chunked(2).forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { item ->
                            val cat = item.first
                            val label = item.second
                            FilterChip(
                                selected = category == cat,
                                onClick = { category = cat },
                                label = { Text(label) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ForestGreen,
                                    selectedLabelColor = SurfaceWhite
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            PrimaryButton(
                text = "Save Expense",
                onClick = {
                    viewModel.addExpense(title, category, amount) {
                        navController.popBackStack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Default.Check
            )
        }
    }
}