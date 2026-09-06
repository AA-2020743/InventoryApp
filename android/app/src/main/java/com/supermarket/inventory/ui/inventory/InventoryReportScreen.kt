package com.supermarket.inventory.ui.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.supermarket.inventory.R
import com.supermarket.inventory.data.ApiResult
import com.supermarket.inventory.data.remote.dto.ProductDto
import com.supermarket.inventory.data.repository.ProductRepository
import com.supermarket.inventory.ui.common.PieChart
import com.supermarket.inventory.ui.common.categoryColor
import com.supermarket.inventory.ui.common.formatAmount
import com.supermarket.inventory.ui.common.formatPercent
import com.supermarket.inventory.ui.common.formatQuantity
import com.supermarket.inventory.ui.common.topSlicesWithOther
import com.supermarket.inventory.ui.theme.warningColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.compose.material.icons.filled.BarChart
import com.supermarket.inventory.ui.common.EmptyState

enum class InventoryReportSort { VALUE, NAME, CATEGORY, QUANTITY }

data class InventoryValueItem(
    val product: ProductDto,
    val quantity: Double,
    val unitCost: Double,
    val value: Double,
)

data class InventoryReportUiState(
    val isLoading: Boolean = true,
    val items: List<InventoryValueItem> = emptyList(),
    val sortBy: InventoryReportSort = InventoryReportSort.VALUE,
    val error: String? = null,
) {
    val totalValue: Double get() = items.sumOf { it.value }
}

@HiltViewModel
class InventoryReportViewModel @Inject constructor(
    private val repository: ProductRepository,
) : ViewModel() {

    var uiState by mutableStateOf(InventoryReportUiState())
        private set

    init { load() }

    // Deliberately fetches the full unfiltered active-product list fresh
    // (no search/category/lowStock args) rather than reusing
    // InventoryViewModel's state, which reflects whatever the owner last
    // filtered the list by on the Inventory screen - a valuation report
    // needs every active product regardless of that.
    fun load() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null)
            when (val result = repository.getProducts()) {
                is ApiResult.Success -> {
                    val items = result.data.map { product ->
                        val quantity = product.quantity.toDoubleOrNull() ?: 0.0
                        val unitCost = product.purchaseCost.toDoubleOrNull() ?: 0.0
                        InventoryValueItem(product, quantity, unitCost, quantity * unitCost)
                    }
                    uiState = uiState.copy(isLoading = false, items = items)
                }
                is ApiResult.Error -> uiState = uiState.copy(isLoading = false, error = result.message)
            }
        }
    }

    fun onSortChange(sort: InventoryReportSort) {
        uiState = uiState.copy(sortBy = sort)
    }
}

// A category subtotal header, interleaved with InventoryValueItem rows in
// the display list when sorted by category - kept as its own type so the
// LazyColumn can tell the two apart with a single `when`.
private data class CategoryHeader(val category: String, val total: Double)

// Total inventory value (at purchase cost, same figure the dashboard's
// inventoryValue reflects) plus a full per-product breakdown - a category
// pie chart for the shape of where that value sits, and a sortable list
// with each row's own value bar so the biggest contributors are visually
// obvious at a glance, not just numerically.
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun InventoryReportScreen(
    onBack: () -> Unit,
    viewModel: InventoryReportViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState
    val uncategorizedLabel = stringResource(R.string.stats_uncategorized)
    val otherLabel = stringResource(R.string.stats_other)

    val categoryData = remember(state.items, uncategorizedLabel) {
        state.items
            .groupBy { it.product.category?.takeIf { c -> c.isNotBlank() } ?: uncategorizedLabel }
            .map { (category, items) -> category to items.sumOf { it.value } }
    }
    val categoryTotals = remember(categoryData) { categoryData.toMap() }
    // Position by value, so a category's colour is the same one the pie and
    // the card's strip give it.
    val categoryRank = remember(categoryData) {
        categoryData.sortedByDescending { it.second }.mapIndexed { index, (name, _) -> name to index }.toMap()
    }
    val maxValue = remember(state.items) { state.items.maxOfOrNull { it.value } ?: 0.0 }

    val displayRows: List<Any> = remember(state.items, state.sortBy, uncategorizedLabel) {
        when (state.sortBy) {
            InventoryReportSort.VALUE -> state.items.sortedByDescending { it.value }
            InventoryReportSort.NAME -> state.items.sortedBy { it.product.name.lowercase() }
            InventoryReportSort.QUANTITY -> state.items.sortedByDescending { it.quantity }
            InventoryReportSort.CATEGORY -> {
                val grouped = state.items.groupBy { it.product.category?.takeIf { c -> c.isNotBlank() } ?: uncategorizedLabel }
                grouped.entries
                    .sortedByDescending { (_, items) -> items.sumOf { it.value } }
                    .flatMap { (category, items) ->
                        listOf(CategoryHeader(category, items.sumOf { it.value })) + items.sortedByDescending { it.value }
                    }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.inventory_report_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = null) }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.items.isEmpty() -> EmptyState(
                icon = Icons.Filled.BarChart,
                title = stringResource(R.string.inventory_report_empty),
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
            ) {
                item {
                    TotalValueCard(
                        total = state.totalValue,
                        itemCount = state.items.size,
                        unitCount = state.items.sumOf { it.quantity },
                        categoryShares = categoryData.sortedByDescending { it.second },
                        lowStockCount = state.items.count {
                            val threshold = it.product.lowStockThreshold.toDoubleOrNull() ?: 0.0
                            threshold > 0 && it.quantity <= threshold
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                }
                if (categoryTotals.size > 1) {
                    item {
                        Card(
                            Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.PieChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.stats_by_category), style = MaterialTheme.typography.titleMedium)
                                }
                                Spacer(Modifier.height(12.dp))
                                PieChart(topSlicesWithOther(categoryData, 6, otherLabel), modifier = Modifier.fillMaxWidth())
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
                item {
                    SortRow(sortBy = state.sortBy, onSortChange = viewModel::onSortChange)
                    Spacer(Modifier.height(8.dp))
                }
                items(
                    displayRows,
                    key = {
                        when (it) {
                            is CategoryHeader -> "header_${it.category}"
                            is InventoryValueItem -> it.product.id
                            else -> it.toString()
                        }
                    },
                ) { row ->
                    when (row) {
                        is CategoryHeader -> CategoryHeaderRow(
                            category = row.category,
                            total = row.total,
                            share = if (state.totalValue > 0) (row.total / state.totalValue) * 100 else 0.0,
                            // The same colour the category carries in the pie
                            // and in the strip on the card above.
                            color = categoryColor(categoryRank[row.category] ?: 0),
                        )
                        is InventoryValueItem -> ProductValueRow(row, maxValue, state.totalValue)
                    }
                }
            }
        }
    }
}

// What the shelves are worth, and the shape of it: how many lines make it
// up, how many units that is, how many categories it spans, and how much of
// the value sits in each - the strip is the pie's information in the space
// of a line, so the headline figure answers "where is it?" too.
@Composable
private fun TotalValueCard(
    total: Double,
    itemCount: Int,
    unitCount: Double,
    categoryShares: List<Pair<String, Double>>,
    lowStockCount: Int,
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                stringResource(R.string.inventory_report_total_value),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                formatAmount(total.toString()),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.inventory_report_item_count, itemCount) + " · " +
                    stringResource(R.string.inventory_report_units, formatQuantity(unitCount.toString())) +
                    if (categoryShares.isNotEmpty()) {
                        " · " + stringResource(R.string.inventory_report_categories, categoryShares.size)
                    } else {
                        ""
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (categoryShares.isNotEmpty() && total > 0) {
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    categoryShares.forEachIndexed { index, (_, value) ->
                        if (value <= 0.0) return@forEachIndexed
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .weight((value / total).toFloat())
                                .background(categoryColor(index)),
                        )
                    }
                }
            }

            // Worth knowing while looking at a valuation: some of this value
            // is about to need replacing.
            if (lowStockCount > 0) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = warningColor(),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.inventory_report_low_stock_count, lowStockCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = warningColor(),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SortRow(sortBy: InventoryReportSort, onSortChange: (InventoryReportSort) -> Unit) {
    // Wraps rather than scrolling sideways, like every other chip row in
    // the app - a sort you cannot see is a sort nobody uses.
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FilterChip(
            selected = sortBy == InventoryReportSort.VALUE,
            onClick = { onSortChange(InventoryReportSort.VALUE) },
            label = { Text(stringResource(R.string.inventory_report_sort_value)) },
        )
        FilterChip(
            selected = sortBy == InventoryReportSort.CATEGORY,
            onClick = { onSortChange(InventoryReportSort.CATEGORY) },
            label = { Text(stringResource(R.string.inventory_report_sort_category)) },
        )
        FilterChip(
            selected = sortBy == InventoryReportSort.NAME,
            onClick = { onSortChange(InventoryReportSort.NAME) },
            label = { Text(stringResource(R.string.inventory_report_sort_name)) },
        )
        FilterChip(
            selected = sortBy == InventoryReportSort.QUANTITY,
            onClick = { onSortChange(InventoryReportSort.QUANTITY) },
            label = { Text(stringResource(R.string.inventory_report_sort_quantity)) },
        )
    }
}

@Composable
private fun CategoryHeaderRow(category: String, total: Double, share: Double, color: Color) {
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(
            category,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            "${formatPercent(share.toString())}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Text(
            formatAmount(total.toString()),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
    }
    Divider(modifier = Modifier.padding(bottom = 4.dp))
}

// Each row carries its own proportional background bar (value relative to
// the single most valuable item in stock) so the breakdown reads like an
// embedded bar chart, not just a plain list of numbers.
@Composable
private fun ProductValueRow(item: InventoryValueItem, maxValue: Double, totalValue: Double) {
    val fraction = if (maxValue > 0) (item.value / maxValue).toFloat().coerceIn(0f, 1f) else 0f
    val share = if (totalValue > 0) (item.value / totalValue) * 100 else 0.0
    val threshold = item.product.lowStockThreshold.toDoubleOrNull() ?: 0.0
    val isLow = threshold > 0 && item.quantity <= threshold

    Card(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
            )
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // A line that is nearly out is worth flagging on a
                        // valuation: this is value about to need spending.
                        if (isLow) {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = stringResource(R.string.inventory_low_stock_badge),
                                tint = warningColor(),
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            item.product.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        stringResource(
                            R.string.inventory_report_row_detail,
                            formatQuantity(item.product.quantity),
                            item.product.unit,
                            formatAmount(item.product.purchaseCost),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        formatAmount(item.value.toString()),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    // What slice of the whole valuation this one line is.
                    Text(
                        "${formatPercent(share.toString())}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
