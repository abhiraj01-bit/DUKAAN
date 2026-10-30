package com.dukaan.app.feature.inventory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.dukaan.app.core.designsystem.components.*
import com.dukaan.app.core.designsystem.theme.WarmCream
import com.dukaan.app.core.navigation.Screen
import com.dukaan.app.domain.model.Product
import com.dukaan.app.domain.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LowStockViewModel @Inject constructor(
    private val productRepository: ProductRepository
) : ViewModel() {
    private val _items = MutableStateFlow<List<Product>>(emptyList())
    val items = _items.asStateFlow()

    init {
        viewModelScope.launch {
            productRepository.getLowStockProducts().collect { list ->
                _items.value = list
            }
        }
    }
}

@Composable
fun LowStockScreen(
    navController: NavController,
    viewModel: LowStockViewModel = hiltViewModel()
) {
    val items by viewModel.items.collectAsState()

    Scaffold(
        topBar = {
            DukaanTopBar(
                title = "Low Stock Attention",
                onBackClick = { navController.popBackStack() }
            )
        },
        containerColor = WarmCream
    ) { paddingValues ->
        if (items.isEmpty()) {
            EmptyState(
                title = "All stocks healthy!",
                subtitle = "No products are currently running low or out of stock.",
                icon = Icons.Default.CheckCircle,
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
                items(items, key = { it.id }) { product ->
                    ProductItemCard(
                        product = product,
                        onClick = { navController.navigate(Screen.ProductDetail.createRoute(product.id)) }
                    )
                }
            }
        }
    }
}