package com.supermarket.inventory.ui.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.supermarket.inventory.R
import com.supermarket.inventory.data.remote.dto.ProductDto
import com.supermarket.inventory.ui.common.formatAmount
import com.supermarket.inventory.ui.common.formatQuantity
import com.supermarket.inventory.ui.theme.WarningAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryListScreen(
    onAddProduct: () -> Unit,
    onEditProduct: (String) -> Unit,
    onAddProductWithBarcode: (String) -> Unit,
    onScanToAdd: () -> Unit,
    onOpenReport: () -> Unit,
    scannedBarcode: String?,
    onScannedBarcodeConsumed: () -> Unit,
    viewModel: InventoryViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState

    // Refresh whenever this screen (re-)enters composition - e.g. returning
    // here after saving a new product on the form - since the ViewModel
    // otherwise only loads once when first created and survives (via
    // saved nav state) across tab switches without refetching on its own.
    LaunchedEffect(Unit) {
        viewModel.load()
        viewModel.loadCategories()
    }

    LaunchedEffect(scannedBarcode) {
        if (scannedBarcode != null) {
            when (val resolution = viewModel.resolveScannedBarcode(scannedBarcode)) {
                is BarcodeResolution.ExistingProduct -> onEditProduct(resolution.productId)
                is BarcodeResolution.NewProduct -> onAddProductWithBarcode(resolution.barcode)
            }
            onScannedBarcodeConsumed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.inventory_title)) },
                actions = {
                    TextButton(onClick = onOpenReport) {
                        Icon(Icons.Filled.BarChart, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.inventory_report_button))
                    }
                },
            )
        },
        floatingActionButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FloatingActionButton(onClick = onScanToAdd) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = stringResource(R.string.product_scan_barcode))
                }
                ExtendedFloatingActionButton(onClick = onAddProduct, icon = { Icon(Icons.Filled.Add, null) }, text = { Text(stringResource(R.string.inventory_add_product)) })
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = state.search,
                    onValueChange = viewModel::onSearchChange,
                    label = { Text(stringResource(R.string.inventory_search_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(Modifier.padding(horizontal = 12.dp)) {
                FilterChip(
                    selected = state.lowStockOnly,
                    onClick = viewModel::onToggleLowStockOnly,
                    label = { Text(stringResource(R.string.inventory_low_stock_badge)) },
                )
            }
            if (state.availableCategories.isNotEmpty()) {
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = state.selectedCategory == null,
                            onClick = { viewModel.onCategorySelected(null) },
                            label = { Text(stringResource(R.string.inventory_all_categories)) },
                        )
                    }
                    items(state.availableCategories) { category ->
                        FilterChip(
                            selected = state.selectedCategory == category,
                            onClick = { viewModel.onCategorySelected(category) },
                            label = { Text(category) },
                        )
                    }
                }
            }
            Spacer(Modifier.padding(4.dp))

            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.products.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.inventory_empty))
                }
                else -> LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                    items(state.products, key = { it.id }) { product ->
                        ProductRow(
                            product = product,
                            imageUrl = viewModel.fullImageUrl(product.imageUrl),
                            onClick = { onEditProduct(product.id) },
                        )
                        Spacer(Modifier.padding(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductRow(product: ProductDto, imageUrl: String?, onClick: () -> Unit) {
    val isLowStock = (product.quantity.toDoubleOrNull() ?: 0.0) <= (product.lowStockThreshold.toDoubleOrNull() ?: 0.0)

    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                )
            } else {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(product.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    product.barcode ?: product.category.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatAmount(product.sellingPrice), style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${formatQuantity(product.quantity)} ${product.unit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isLowStock) WarningAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
