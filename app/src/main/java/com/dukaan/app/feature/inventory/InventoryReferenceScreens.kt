package com.dukaan.app.feature.inventory

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
import com.dukaan.app.domain.model.Product
import com.dukaan.app.domain.model.StockMovement
import com.dukaan.app.domain.model.StockMovementType
import com.dukaan.app.domain.model.StockUnit
import com.dukaan.app.domain.repository.ProductRepository
import com.dukaan.app.domain.repository.StockMovementRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InventoryListViewModel @Inject constructor(
    private val productRepository: ProductRepository
) : ViewModel() {
    private val _products = MutableStateFlow<List<Product>>(emptyList())
    val products = _products.asStateFlow()

    init {
        viewModelScope.launch {
            productRepository.getAllActiveProducts().collect { list ->
                _products.value = list
            }
        }
    }
}

@HiltViewModel
class ProductDetailReferenceViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val stockMovementRepository: StockMovementRepository
) : ViewModel() {
    private val _product = MutableStateFlow<Product?>(null)
    val product = _product.asStateFlow()

    private val _movements = MutableStateFlow<List<StockMovement>>(emptyList())
    val movements = _movements.asStateFlow()

    fun load(productId: String) {
        viewModelScope.launch {
            productRepository.getProductById(productId).collect {
                _product.value = it
            }
        }
        viewModelScope.launch {
            stockMovementRepository.getMovementsByProduct(productId).collect {
                _movements.value = it
            }
        }
    }
}

@HiltViewModel
class AddProductReferenceViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val stockMovementRepository: StockMovementRepository
) : ViewModel() {
    fun saveProduct(
        name: String,
        category: String,
        unit: String,
        gstRatePercent: Double,
        purchasePricePaise: Long,
        sellingPricePaise: Long,
        minStock: Double,
        initialStock: Double,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val id = UuidGenerator.generate()
            val stockUnit = try { StockUnit.valueOf(unit.uppercase()) } catch (_: Exception) { StockUnit.PCS }
            val product = Product(
                id = id,
                name = name,
                categoryName = category,
                unit = stockUnit,
                purchasePricePaise = purchasePricePaise,
                sellingPricePaise = sellingPricePaise,
                currentStock = initialStock,
                minStockLevel = minStock,
                isActive = true,
                createdAt = now,
                updatedAt = now
            )
            productRepository.saveProduct(product)

            if (initialStock > 0) {
                stockMovementRepository.insertMovement(
                    StockMovement(
                        id = UuidGenerator.generate(),
                        productId = id,
                        productName = name,
                        type = StockMovementType.INITIAL_STOCK,
                        quantityChange = initialStock,
                        quantityAfter = initialStock,
                        notes = "Initial stock entry",
                        createdAt = now
                    )
                )
            }
            onSuccess()
        }
    }
}

// =================== SCREEN 15: ADD PRODUCT ===================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferenceAddProductScreen(
    navController: NavController,
    viewModel: AddProductReferenceViewModel = hiltViewModel()
) {
    var productName by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Atta, Rice & Grains") }
    var unit by remember { mutableStateOf("kg") }
    var gstRate by remember { mutableStateOf("5%") }
    var purchasePrice by remember { mutableStateOf("") }
    var sellingPrice by remember { mutableStateOf("") }
    var minStock by remember { mutableStateOf("10") }
    var initialStock by remember { mutableStateOf("20") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Product", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = PrimaryText) },
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
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Dashed photo upload area
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp)
                        .border(1.dp, BorderColor, RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = SecondaryText, modifier = Modifier.size(26.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Add Photo", style = MaterialTheme.typography.labelMedium, color = SecondaryText)
                    }
                }

                // Product Name
                OutlinedTextField(
                    value = productName,
                    onValueChange = { productName = it },
                    label = { Text("Product Name") },
                    placeholder = { Text("e.g. Aashirvaad Atta") },
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

                // Category & Unit Row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Category") },
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ForestGreen,
                            unfocusedBorderColor = BorderColor,
                            focusedContainerColor = SurfaceWhite,
                            unfocusedContainerColor = SurfaceWhite
                        )
                    )

                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Unit") },
                        modifier = Modifier.weight(0.8f),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ForestGreen,
                            unfocusedBorderColor = BorderColor,
                            focusedContainerColor = SurfaceWhite,
                            unfocusedContainerColor = SurfaceWhite
                        )
                    )
                }

                // Purchase & Selling Price Row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = purchasePrice,
                        onValueChange = { purchasePrice = it },
                        label = { Text("Purchase Price") },
                        placeholder = { Text("320") },
                        prefix = { Text("₹ ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ForestGreen,
                            unfocusedBorderColor = BorderColor,
                            focusedContainerColor = SurfaceWhite,
                            unfocusedContainerColor = SurfaceWhite
                        )
                    )

                    OutlinedTextField(
                        value = sellingPrice,
                        onValueChange = { sellingPrice = it },
                        label = { Text("Selling Price") },
                        placeholder = { Text("360") },
                        prefix = { Text("₹ ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ForestGreen,
                            unfocusedBorderColor = BorderColor,
                            focusedContainerColor = SurfaceWhite,
                            unfocusedContainerColor = SurfaceWhite
                        )
                    )
                }

                // Minimum Stock & Initial Stock
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = initialStock,
                        onValueChange = { initialStock = it },
                        label = { Text("Initial Stock") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ForestGreen,
                            unfocusedBorderColor = BorderColor,
                            focusedContainerColor = SurfaceWhite,
                            unfocusedContainerColor = SurfaceWhite
                        )
                    )

                    OutlinedTextField(
                        value = minStock,
                        onValueChange = { minStock = it },
                        label = { Text("Minimum Stock") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ForestGreen,
                            unfocusedBorderColor = BorderColor,
                            focusedContainerColor = SurfaceWhite,
                            unfocusedContainerColor = SurfaceWhite
                        )
                    )
                }
            }

            // Save Product Button
            PrimaryButton(
                text = "Save Product",
                onClick = {
                    if (productName.isNotBlank()) {
                        val purchase = (purchasePrice.toDoubleOrNull() ?: 0.0) * 100
                        val selling = (sellingPrice.toDoubleOrNull() ?: 0.0) * 100
                        val min = minStock.toDoubleOrNull() ?: 10.0
                        val init = initialStock.toDoubleOrNull() ?: 0.0

                        viewModel.saveProduct(
                            name = productName,
                            category = category,
                            unit = unit,
                            gstRatePercent = 5.0,
                            purchasePricePaise = purchase.toLong(),
                            sellingPricePaise = selling.toLong(),
                            minStock = min,
                            initialStock = init,
                            onSuccess = { navController.popBackStack() }
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp).padding(bottom = 16.dp)
            )
        }
    }
}

// =================== SCREEN 16: INVENTORY LIST ===================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferenceInventoryListScreen(
    navController: NavController,
    onMenuClick: () -> Unit = {},
    viewModel: InventoryListViewModel = hiltViewModel()
) {
    var selectedTab by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    val products by viewModel.products.collectAsState()

    val filteredProducts = remember(products, selectedTab, searchQuery) {
        products.filter { prod ->
            val matchesTab = when (selectedTab) {
                "Low Stock" -> prod.currentStock > 0 && prod.currentStock <= prod.minStockLevel
                "Out of Stock" -> prod.currentStock <= 0
                else -> true
            }
            val matchesSearch = searchQuery.isBlank() || prod.name.contains(searchQuery, ignoreCase = true)
            matchesTab && matchesSearch
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inventory", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = PrimaryText) },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.AddProduct.route) }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Product", tint = PrimaryText)
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
        ) {
            // Search Input
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                DukaanSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "Search products..."
                )
            }

            // Filter Tabs: All, Low Stock, Out of Stock
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All", "Low Stock", "Out of Stock").forEach { tab ->
                    val isSelected = selectedTab == tab
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedTab = tab },
                        label = { Text(tab) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ForestGreen,
                            selectedLabelColor = SurfaceWhite
                        )
                    )
                }
            }

            if (filteredProducts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Inventory2, contentDescription = null, tint = SecondaryText, modifier = Modifier.size(56.dp))
                        Text("No products found", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = PrimaryText)
                        Text("Tap + to add a product or scan a purchase invoice", style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredProducts) { product ->
                        val isLow = product.currentStock <= product.minStockLevel
                        val statusText = when {
                            product.currentStock <= 0 -> "Out of Stock"
                            isLow -> "Low Stock"
                            else -> "In Stock"
                        }

                        InventoryRowItem(
                            name = product.name,
                            qty = "${product.currentStock.toInt()} ${product.unit.name.lowercase()}",
                            price = CurrencyFormatter.formatPaise(product.sellingPricePaise),
                            status = statusText,
                            isLowStock = isLow,
                            onClick = { navController.navigate(Screen.ProductDetails.createRoute(product.id)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InventoryRowItem(
    name: String,
    qty: String,
    price: String,
    status: String,
    isLowStock: Boolean,
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
                GroceryProductThumbnail(productName = name)
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = PrimaryText)
                    Text(qty, style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(price, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = PrimaryText)
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isLowStock) LightRedSurface else LightGreenSurface)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        status,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isLowStock) ErrorRed else SuccessGreen
                    )
                }
            }
        }
    }
}

// =================== SCREEN 17: PRODUCT DETAILS ===================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferenceProductDetailsScreen(
    navController: NavController,
    productId: String,
    viewModel: ProductDetailReferenceViewModel = hiltViewModel()
) {
    val product by viewModel.product.collectAsState()
    val movements by viewModel.movements.collectAsState()

    LaunchedEffect(productId) {
        viewModel.load(productId)
    }

    val prod = product

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Product Details", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = PrimaryText) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PrimaryText)
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = PrimaryText)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WarmCream)
            )
        },
        bottomBar = {
            Surface(
                color = SurfaceWhite,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    PrimaryButton(
                        text = "Edit Product",
                        onClick = { navController.navigate(Screen.AddProduct.route) },
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    )
                }
            }
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
            // Product Hero Card
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
                        GroceryProductThumbnail(productName = prod?.name ?: "Product", modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                prod?.name ?: "Loading...",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                                color = PrimaryText
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "${CurrencyFormatter.formatPaise(prod?.sellingPricePaise ?: 0L)} (Selling)",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = SecondaryGreen
                            )
                        }
                    }
                }
            }

            // 4 Metric Chips (2x2)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ProductMetricBox(
                            value = "${prod?.currentStock?.toInt() ?: 0} ${prod?.unit?.name?.lowercase() ?: "kg"}",
                            label = "Current Stock",
                            bgColor = LightGreenSurface,
                            modifier = Modifier.weight(1f)
                        )
                        ProductMetricBox(
                            value = "${prod?.minStockLevel?.toInt() ?: 10} ${prod?.unit?.name?.lowercase() ?: "kg"}",
                            label = "Minimum Stock",
                            bgColor = LightOrangeSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ProductMetricBox(
                            value = CurrencyFormatter.formatPaise(prod?.purchasePricePaise ?: 0L),
                            label = "Purchase Price",
                            bgColor = SurfaceWhite,
                            modifier = Modifier.weight(1f)
                        )
                        ProductMetricBox(
                            value = "5%",
                            label = "GST Rate",
                            bgColor = SurfaceWhite,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Stock Movement Section with View All link
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Stock Movement",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = PrimaryText
                            )
                            Text(
                                "View All",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = Color(0xFF1E88E5)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        if (movements.isEmpty()) {
                            Text("No stock movements recorded yet", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                        } else {
                            movements.take(4).forEach { move ->
                                val isPositive = move.quantityChange > 0
                                StockMovementRow(
                                    date = DateFormatter.formatDate(move.createdAt),
                                    delta = "${if (isPositive) "+" else ""}${move.quantityChange.toInt()}",
                                    type = if (isPositive) "Purchase" else "Sale",
                                    isPositive = isPositive
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ProductMetricBox(
    value: String,
    label: String,
    bgColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = PrimaryText
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = SecondaryText)
        }
    }
}

@Composable
private fun StockMovementRow(
    date: String,
    delta: String,
    type: String,
    isPositive: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isPositive) SuccessGreen else ErrorRed)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(date, style = MaterialTheme.typography.bodySmall, color = SecondaryText)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                delta,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = if (isPositive) SuccessGreen else ErrorRed
            )
            Spacer(modifier = Modifier.width(14.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isPositive) LightGreenSurface else LightRedSurface)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    type,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (isPositive) SuccessGreen else ErrorRed
                )
            }
        }
    }
}

