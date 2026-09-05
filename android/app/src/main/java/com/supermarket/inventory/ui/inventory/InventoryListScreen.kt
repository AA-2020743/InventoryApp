package com.supermarket.inventory.ui.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.supermarket.inventory.ui.theme.warningColor
import androidx.compose.material.icons.filled.Inventory2
import com.supermarket.inventory.ui.common.EmptyState

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
    // The category picker starts closed so the products have the screen.
    var categoriesExpanded by remember { mutableStateOf(false) }

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
            // The filters ride along with the list rather than sitting in a
            // fixed band above it. A shop with twenty categories wraps to
            // eight rows of chips, and a fixed band that tall left the
            // products themselves a sliver at the bottom of the screen.
            //
            // Bottom padding clears the add/scan buttons, which otherwise
            // sit on top of the last product's row.
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 12.dp, top = 4.dp, end = 12.dp, bottom = 96.dp,
                ),
            ) {
                item(key = "filters") {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilterChip(
                            selected = state.lowStockOnly,
                            onClick = viewModel::onToggleLowStockOnly,
                            label = { Text(stringResource(R.string.inventory_low_stock_badge)) },
                        )
                        if (state.availableCategories.isNotEmpty()) {
                            // Closed, this is one line that names the filter
                            // in force. Open, every category is on screen at
                            // once - no sideways drag, no band to scroll
                            // inside, and the list is a scroll away rather
                            // than pushed off the bottom.
                            FilterChip(
                                selected = state.selectedCategory != null,
                                onClick = { categoriesExpanded = !categoriesExpanded },
                                label = {
                                    Text(
                                        state.selectedCategory
                                            ?: stringResource(R.string.inventory_all_categories),
                                        maxLines = 1,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.FilterList,
                                        contentDescription = stringResource(R.string.inventory_filter_categories),
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        if (categoriesExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                            )
                        }
                    }
                }

                if (categoriesExpanded && state.availableCategories.isNotEmpty()) {
                    item(key = "categories") {
                        androidx.compose.foundation.layout.FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            FilterChip(
                                selected = state.selectedCategory == null,
                                onClick = { viewModel.onCategorySelected(null); categoriesExpanded = false },
                                label = { Text(stringResource(R.string.inventory_all_categories)) },
                            )
                            state.availableCategories.forEach { category ->
                                FilterChip(
                                    selected = state.selectedCategory == category,
                                    // Choosing one closes the picker: the
                                    // choice is made, and the products are
                                    // what you wanted to see.
                                    onClick = { viewModel.onCategorySelected(category); categoriesExpanded = false },
                                    label = { Text(category) },
                                )
                            }
                        }
                    }
                }

                item(key = "filters-gap") { Spacer(Modifier.height(8.dp)) }

                when {
                    // Fixed heights: a lazy item is measured with unbounded
                    // height, so anything asking to fill it has nothing to
                    // fill.
                    state.isLoading -> item(key = "loading") {
                        Box(
                            Modifier.fillMaxWidth().height(240.dp),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }
                    }
                    state.products.isEmpty() -> item(key = "empty") {
                        EmptyState(
                            icon = Icons.Filled.Inventory2,
                            title = stringResource(R.string.inventory_empty),
                            hint = stringResource(R.string.inventory_empty_hint),
                            modifier = Modifier.height(320.dp),
                        )
                    }
                    else -> items(state.products, key = { it.id }) { product ->
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
                    color = if (isLowStock) warningColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
