package com.supermarket.inventory.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.supermarket.inventory.R
import com.supermarket.inventory.data.remote.dto.CategoryTotalDto
import com.supermarket.inventory.data.remote.dto.MarginItemDto
import com.supermarket.inventory.data.remote.dto.SaleDto
import com.supermarket.inventory.data.remote.dto.TopProductItemDto
import com.supermarket.inventory.ui.common.PieChart
import com.supermarket.inventory.ui.common.expenseDisplayName
import com.supermarket.inventory.ui.common.formatAmount
import com.supermarket.inventory.ui.common.formatIsoDateTime
import com.supermarket.inventory.ui.common.formatPercent
import com.supermarket.inventory.ui.common.formatQuantity
import com.supermarket.inventory.ui.common.topSlicesWithOther
import com.supermarket.inventory.ui.theme.lossColor
import com.supermarket.inventory.ui.theme.profitColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Top products and margins are their own sub-tabs (kept out of the main
// scroll) so the day/month's sales and expenses - what the owner checks
// most often - are visible right away instead of below two long rankings.
private enum class StatsTab { OVERVIEW, TOP_PRODUCTS, MARGINS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(onEditSale: (String) -> Unit, viewModel: StatsViewModel = hiltViewModel()) {
    val state = viewModel.uiState
    var showDatePicker by remember { mutableStateOf(false) }
    var saleToDelete by remember { mutableStateOf<SaleDto?>(null) }
    var selectedTab by remember { mutableStateOf(StatsTab.OVERVIEW) }
    val uncategorizedLabel = stringResource(R.string.stats_uncategorized)
    val otherLabel = stringResource(R.string.stats_other)

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.stats_title)) }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(12.dp)) {
                SegmentedButton(
                    selected = state.period == StatsPeriod.DAY,
                    onClick = { viewModel.onPeriodChange(StatsPeriod.DAY) },
                    shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text(stringResource(R.string.stats_period_day)) }
                SegmentedButton(
                    selected = state.period == StatsPeriod.MONTH,
                    onClick = { viewModel.onPeriodChange(StatsPeriod.MONTH) },
                    shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text(stringResource(R.string.stats_period_month)) }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = { showDatePicker = true }) {
                    val pattern = if (state.period == StatsPeriod.MONTH) "MMMM yyyy" else "dd MMM yyyy"
                    Text(state.selectedDate.format(DateTimeFormatter.ofPattern(pattern)))
                }
            }

            Spacer(Modifier.height(8.dp))

            TabRow(selectedTabIndex = selectedTab.ordinal) {
                Tab(
                    selected = selectedTab == StatsTab.OVERVIEW,
                    onClick = { selectedTab = StatsTab.OVERVIEW },
                    text = { Text(stringResource(R.string.stats_tab_overview)) },
                )
                Tab(
                    selected = selectedTab == StatsTab.TOP_PRODUCTS,
                    onClick = { selectedTab = StatsTab.TOP_PRODUCTS },
                    text = { Text(stringResource(R.string.stats_tab_top_products)) },
                )
                Tab(
                    selected = selectedTab == StatsTab.MARGINS,
                    onClick = { selectedTab = StatsTab.MARGINS },
                    text = { Text(stringResource(R.string.stats_tab_margins)) },
                )
            }

            if (state.isLoading) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
            } else {
                when (selectedTab) {
                    StatsTab.OVERVIEW -> OverviewTab(
                        state = state,
                        onEditSale = onEditSale,
                        onDeleteSaleRequest = { saleToDelete = it },
                    )
                    StatsTab.TOP_PRODUCTS -> TopProductsTab(
                        state = state,
                        viewModel = viewModel,
                        uncategorizedLabel = uncategorizedLabel,
                        otherLabel = otherLabel,
                    )
                    StatsTab.MARGINS -> MarginsTab(state)
                }
            }
        }
    }

    if (showDatePicker) {
        val initialMillis = state.selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                        viewModel.onDateSelected(date)
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    saleToDelete?.let { sale ->
        AlertDialog(
            onDismissRequest = { saleToDelete = null },
            title = { Text(stringResource(R.string.stats_delete_sale_title)) },
            text = { Text(stringResource(R.string.stats_delete_sale_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteSale(sale.id); saleToDelete = null }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = { TextButton(onClick = { saleToDelete = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

// Expenses and other sales are summarized here as totals plus a
// per-category breakdown only - deliberately not as a row-per-entry list.
// The category breakdown already carries an entry count per category,
// which is what the owner needs from this screen; the individual records
// (and editing them) live on their own Expenses / Other Sales tabs.
@Composable
private fun OverviewTab(
    state: StatsUiState,
    onEditSale: (String) -> Unit,
    onDeleteSaleRequest: (SaleDto) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(12.dp)) {
        item { PeriodSummaryCard(state) }
        state.expensesForRange?.let { expenses ->
            item { Spacer(Modifier.height(16.dp)) }
            item { Text(stringResource(R.string.stats_expenses_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp)) }
            item { ExpensesSummaryRow(expenses.total, expenses.deficit) }
            if (expenses.byCategory.isNotEmpty()) {
                item {
                    CategoryBreakdownCard(
                        title = stringResource(R.string.stats_expenses_by_category),
                        breakdown = expenses.byCategory,
                        accent = MaterialTheme.colorScheme.error,
                    )
                }
            }
            if (expenses.items.isEmpty()) {
                item { Text(stringResource(R.string.stats_no_expenses_this_period), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp)) }
            }
        }
        state.otherSalesForRange?.let { otherSales ->
            item { Spacer(Modifier.height(16.dp)) }
            item { Text(stringResource(R.string.stats_other_sales_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp)) }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.stats_other_sales_total, formatAmount(otherSales.total)),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            if (otherSales.byCategory.isNotEmpty()) {
                item {
                    CategoryBreakdownCard(
                        title = stringResource(R.string.stats_other_sales_by_category),
                        breakdown = otherSales.byCategory,
                        accent = profitColor(),
                    )
                }
            }
            if (otherSales.items.isEmpty()) {
                item { Text(stringResource(R.string.stats_no_other_sales_this_period), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp)) }
            }
        }
        if (state.period == StatsPeriod.DAY) {
            item { Spacer(Modifier.height(16.dp)) }
            item { Text(stringResource(R.string.sales_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp)) }
            if (state.salesForDay.isEmpty()) {
                item { Text(stringResource(R.string.stats_no_sales_this_day), style = MaterialTheme.typography.bodyMedium) }
            }
            items(state.salesForDay, key = { it.id }) { sale ->
                SaleRow(sale, onEdit = { onEditSale(sale.id) }, onDelete = { onDeleteSaleRequest(sale) })
            }
        }
    }
}

@Composable
private fun ExpensesSummaryRow(total: String, deficit: String) {
    val hasDeficit = (deficit.toDoubleOrNull() ?: 0.0) > 0
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.stats_expenses_total, formatAmount(total)), style = MaterialTheme.typography.bodyMedium)
            if (hasDeficit) {
                Text(
                    stringResource(R.string.stats_expenses_deficit, formatAmount(deficit)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

// Per-category totals for the selected day/month, biggest first (the server
// already sorts them that way). Each row carries a proportional bar sized
// against the largest category, so which categories dominate the period
// reads at a glance instead of having to compare numbers line by line.
@Composable
private fun CategoryBreakdownCard(
    title: String,
    breakdown: List<CategoryTotalDto>,
    accent: androidx.compose.ui.graphics.Color,
) {
    val uncategorizedLabel = stringResource(R.string.stats_uncategorized)
    val maxTotal = breakdown.maxOfOrNull { it.total.toDoubleOrNull() ?: 0.0 } ?: 0.0

    Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
            breakdown.forEach { row ->
                val value = row.total.toDoubleOrNull() ?: 0.0
                val fraction = if (maxTotal > 0) (value / maxTotal).toFloat().coerceIn(0f, 1f) else 0f
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            row.category?.takeIf { it.isNotBlank() }?.let { expenseDisplayName(it) }
                                ?: uncategorizedLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            stringResource(R.string.stats_category_count, row.count),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                        Text(formatAmount(row.total), style = MaterialTheme.typography.bodyMedium)
                    }
                    Box(
                        Modifier
                            .padding(top = 4.dp)
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction)
                                .clip(RoundedCornerShape(3.dp))
                                .background(accent),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopProductsTab(
    state: StatsUiState,
    viewModel: StatsViewModel,
    uncategorizedLabel: String,
    otherLabel: String,
) {
    LazyColumn(contentPadding = PaddingValues(12.dp)) {
        item {
            val monthLabel = state.selectedDate.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
            Text(
                stringResource(R.string.stats_top_products_month_label, monthLabel),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        if (state.topProducts.isNotEmpty()) {
            item {
                Column {
                    val categoryData = state.topProducts
                        .groupBy { it.category?.takeIf { c -> c.isNotBlank() } ?: uncategorizedLabel }
                        .map { (category, items) -> category to items.sumOf { it.revenue.toDoubleOrNull() ?: 0.0 } }
                    Text(stringResource(R.string.stats_by_category), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                    PieChart(topSlicesWithOther(categoryData, 6, otherLabel), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(20.dp))

                    val itemData = state.topProducts.map { it.name to (it.revenue.toDoubleOrNull() ?: 0.0) }
                    Text(stringResource(R.string.stats_by_item), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                    PieChart(topSlicesWithOther(itemData, 6, otherLabel), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
        item { SortRow(state, viewModel) }
        item { Text(stringResource(R.string.stats_top_products), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp)) }
        items(state.topProducts) { TopProductRow(it) }
    }
}

@Composable
private fun MarginsTab(state: StatsUiState) {
    LazyColumn(contentPadding = PaddingValues(12.dp)) {
        item { Text(stringResource(R.string.stats_top_margins), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp)) }
        items(state.margins) { MarginRow(it) }
    }
}

@Composable
private fun PeriodSummaryCard(state: StatsUiState) {
    val profit = state.periodProfit.toDoubleOrNull() ?: 0.0
    val revenue = state.periodRevenue.toDoubleOrNull() ?: 0.0
    // Overall margin for the period - profit relative to revenue, the same
    // ratio as each product's static marginPercent on the Margins tab, but
    // computed from what was actually sold this day/month rather than a
    // per-product list price.
    val marginPercent = if (revenue > 0) (profit / revenue) * 100 else 0.0
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(stringResource(R.string.dashboard_revenue), style = MaterialTheme.typography.bodySmall)
                    Text(formatAmount(state.periodRevenue), style = MaterialTheme.typography.titleMedium)
                }
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.dashboard_profit), style = MaterialTheme.typography.bodySmall)
                    Text(
                        formatAmount(state.periodProfit),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (profit < 0) lossColor() else profitColor(),
                    )
                }
                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                    Text(stringResource(R.string.stats_average_margin), style = MaterialTheme.typography.bodySmall)
                    Text(
                        "${formatPercent(marginPercent.toString())}%",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (marginPercent < 0) lossColor() else profitColor(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SortRow(state: StatsUiState, viewModel: StatsViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = state.sortBy == StatsSort.QUANTITY,
            onClick = { viewModel.onSortChange(StatsSort.QUANTITY) },
            label = { Text(stringResource(R.string.stats_sort_quantity)) },
        )
        FilterChip(
            selected = state.sortBy == StatsSort.PROFIT,
            onClick = { viewModel.onSortChange(StatsSort.PROFIT) },
            label = { Text(stringResource(R.string.stats_sort_profit)) },
        )
    }
}

@Composable
private fun TopProductRow(item: TopProductItemDto) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(item.name, style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.stats_quantity_sold, formatQuantity(item.quantitySold.toString())), style = MaterialTheme.typography.bodySmall)
            }
            Text(formatAmount(item.profit), style = MaterialTheme.typography.bodyLarge, color = profitColor())
        }
    }
}

@Composable
private fun MarginRow(item: MarginItemDto) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.stats_margin_percent, formatPercent(item.marginPercent)),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun SaleRow(sale: SaleDto, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column {
                    Text(formatIsoDateTime(sale.createdAt), style = MaterialTheme.typography.bodyMedium)
                    if (sale.paymentStatus == "DEFERRED") {
                        Text(
                            stringResource(R.string.deferred_sales_title),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(formatAmount(sale.totalAmount), style = MaterialTheme.typography.bodyMedium)
                    IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit)) }
                    IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete)) }
                }
            }
            Divider(Modifier.padding(vertical = 4.dp))
            sale.items.forEach { item ->
                Text(
                    "${item.product?.name ?: item.productId} x${formatQuantity(item.quantity)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
