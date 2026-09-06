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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
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
import com.supermarket.inventory.ui.common.categoryColor
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
            item {
                val deficit = expenses.deficit.toDoubleOrNull() ?: 0.0
                SectionTotalCard(
                    icon = Icons.Filled.Payments,
                    label = stringResource(R.string.stats_expenses_title),
                    total = expenses.total,
                    accent = MaterialTheme.colorScheme.error,
                    note = if (deficit > 0) {
                        stringResource(R.string.stats_expenses_deficit, formatAmount(expenses.deficit))
                    } else {
                        null
                    },
                )
            }
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
            item {
                SectionTotalCard(
                    icon = Icons.Filled.Savings,
                    label = stringResource(R.string.stats_other_sales_title),
                    total = otherSales.total,
                    accent = profitColor(),
                )
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
            item {
                SectionTotalCard(
                    icon = Icons.Filled.ReceiptLong,
                    label = stringResource(R.string.sales_title),
                    total = state.periodRevenue,
                    accent = MaterialTheme.colorScheme.primary,
                )
            }
            item { Spacer(Modifier.height(4.dp)) }
            if (state.salesForDay.isEmpty()) {
                item { Text(stringResource(R.string.stats_no_sales_this_day), style = MaterialTheme.typography.bodyMedium) }
            }
            items(state.salesForDay, key = { it.id }) { sale ->
                SaleRow(sale, onEdit = { onEditSale(sale.id) }, onDelete = { onDeleteSaleRequest(sale) })
            }
        }
    }
}

// A section's headline: an icon in a tinted disc, what the section is, and
// its total for the period. The same shape for expenses and for other
// income, so the two read as a pair rather than as two unrelated blocks.
@Composable
private fun SectionTotalCard(
    icon: ImageVector,
    label: String,
    total: String,
    accent: Color,
    note: String? = null,
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (note != null) {
                    Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
            Text(formatAmount(total), style = MaterialTheme.typography.titleMedium, color = accent)
        }
    }
}

// Per-category totals for the selected day/month, biggest first (the server
// already sorts them that way). Each row carries its colour, its share of
// the period, how many entries made it up, and a bar sized against the
// biggest category - so which categories dominate reads at a glance
// instead of having to compare numbers line by line.
//
// The colour is the same one the category would get as a pie slice, so a
// category looks like itself wherever it appears.
@Composable
private fun CategoryBreakdownCard(
    title: String,
    breakdown: List<CategoryTotalDto>,
    accent: Color,
) {
    val uncategorizedLabel = stringResource(R.string.stats_uncategorized)
    val maxTotal = breakdown.maxOfOrNull { it.total.toDoubleOrNull() ?: 0.0 } ?: 0.0
    val grandTotal = breakdown.sumOf { it.total.toDoubleOrNull() ?: 0.0 }

    Card(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Box(
                    Modifier
                        .width(3.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(accent),
                )
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(4.dp))
            breakdown.forEachIndexed { index, row ->
                val value = row.total.toDoubleOrNull() ?: 0.0
                val fraction = if (maxTotal > 0) (value / maxTotal).toFloat().coerceIn(0f, 1f) else 0f
                val share = if (grandTotal > 0) (value / grandTotal) * 100 else 0.0
                val color = categoryColor(index)
                Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            row.category?.takeIf { it.isNotBlank() }?.let { expenseDisplayName(it) }
                                ?: uncategorizedLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(formatAmount(row.total), style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, top = 3.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fraction)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(color),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        // How many entries made this category up, and what
                        // slice of the period it is. Both were asked for;
                        // the individual entries deliberately are not here.
                        Text(
                            "${formatPercent(share.toString())}% · " +
                                stringResource(R.string.stats_category_count, row.count),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
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
    val revenue = state.periodRevenue.toDoubleOrNull() ?: 0.0
    val cost = state.periodCost.toDoubleOrNull() ?: 0.0
    val grossProfit = state.periodProfit.toDoubleOrNull() ?: 0.0
    val expenses = state.expensesForRange?.total?.toDoubleOrNull() ?: 0.0
    val otherSales = state.otherSalesForRange?.total?.toDoubleOrNull() ?: 0.0

    // Revenue here is checkout sales only - the server's revenue series and
    // the day's sale list both exclude miscellaneous income (see
    // stats.routes.ts), which is why other sales are added in separately
    // rather than assumed to be inside it.
    val net = grossProfit + otherSales - expenses
    // Margin stays what it always was: profit on goods against what they
    // sold for. Folding expenses into it would quietly change the meaning
    // of a number that is also shown per-product on the Margins tab.
    val marginPercent = if (revenue > 0) (grossProfit / revenue) * 100 else 0.0

    val costColor = MaterialTheme.colorScheme.tertiary
    val expenseColor = MaterialTheme.colorScheme.error
    val netColor = if (net < 0) lossColor() else profitColor()

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(
                            if (state.period == StatsPeriod.DAY) R.string.stats_net_day
                            else R.string.stats_net_month
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        formatAmount(net.toString()),
                        style = MaterialTheme.typography.headlineMedium,
                        color = netColor,
                    )
                }
                // The margin rides in the corner as a chip rather than a
                // third column, so the headline figure gets the width.
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            (if (marginPercent < 0) lossColor() else profitColor()).copy(alpha = 0.16f)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        Text(
                            stringResource(R.string.stats_average_margin),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "${formatPercent(marginPercent.toString())}%",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (marginPercent < 0) lossColor() else profitColor(),
                        )
                    }
                }
            }

            // Where the period's money went, as one bar: what the goods
            // cost, what was spent running the place, and what is left.
            // Comparing three figures line by line never showed which one
            // was eating the takings.
            Spacer(Modifier.height(14.dp))
            FlowBar(
                segments = listOf(
                    costColor to cost,
                    expenseColor to expenses,
                    netColor to net.coerceAtLeast(0.0),
                ),
            )
            Spacer(Modifier.height(10.dp))
            FlowLegend(
                entries = listOf(
                    Triple(costColor, stringResource(R.string.stats_flow_cost), cost),
                    Triple(expenseColor, stringResource(R.string.stats_expenses_title), expenses),
                    Triple(netColor, stringResource(R.string.stats_flow_left), net),
                ),
            )

            Spacer(Modifier.height(14.dp))
            Divider()
            Spacer(Modifier.height(10.dp))

            // Revenue, profit on goods and miscellaneous income - the three
            // figures this card carried before, kept in full.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatFigure(
                    label = stringResource(R.string.dashboard_revenue),
                    value = formatAmount(state.periodRevenue),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                StatFigure(
                    label = stringResource(R.string.dashboard_profit),
                    value = formatAmount(state.periodProfit),
                    color = if (grossProfit < 0) lossColor() else profitColor(),
                    modifier = Modifier.weight(1f),
                )
                StatFigure(
                    label = stringResource(R.string.stats_other_sales_title),
                    value = formatAmount(otherSales.toString()),
                    color = if (otherSales > 0) profitColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StatFigure(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(value, style = MaterialTheme.typography.titleSmall, color = color, maxLines = 1)
    }
}

// One bar split by value. Segments are sized against their own sum, so the
// bar always fills - it shows proportion, not an absolute scale.
@Composable
private fun FlowBar(segments: List<Pair<Color, Double>>) {
    val total = segments.sumOf { it.second }
    Row(
        Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (total <= 0.0) return@Row
        segments.forEach { (color, value) ->
            if (value <= 0.0) return@forEach
            Box(
                Modifier
                    .fillMaxHeight()
                    .weight((value / total).toFloat())
                    .background(color),
            )
        }
    }
}

@Composable
private fun FlowLegend(entries: List<Triple<Color, String, Double>>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        entries.forEach { (color, label, value) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(8.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(formatAmount(value.toString()), style = MaterialTheme.typography.bodySmall)
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
