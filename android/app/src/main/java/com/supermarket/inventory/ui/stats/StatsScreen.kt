package com.supermarket.inventory.ui.stats

import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.supermarket.inventory.data.remote.dto.CategorySubTotalDto
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.supermarket.inventory.R
import com.supermarket.inventory.data.remote.dto.CategoryTotalDto
import com.supermarket.inventory.data.remote.dto.SaleDto
import com.supermarket.inventory.ui.common.expenseDisplayName
import com.supermarket.inventory.ui.common.categoryColor
import com.supermarket.inventory.ui.common.formatAmount
import com.supermarket.inventory.ui.common.formatMonth
import com.supermarket.inventory.ui.common.formatIsoDateTime
import com.supermarket.inventory.ui.common.formatPercent
import com.supermarket.inventory.ui.common.formatQuantity
import com.supermarket.inventory.ui.theme.lossColor
import com.supermarket.inventory.ui.theme.profitColor
import com.supermarket.inventory.ui.theme.warningColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.YearMonth
import java.util.Locale
import java.time.format.DateTimeFormatter

// How a tab moves through time: by the Overview's day/month toggle, a month
// at a time (the reports that only make sense over a month), or not at all -
// a picture of right now.
private enum class Stepping { TOGGLE, MONTH, NONE }

// Top products and margins are their own sub-tabs (kept out of the main
// scroll) so the day/month's sales and expenses - what the owner checks
// most often - are visible right away instead of below two long rankings.
// The longer-range reports follow them, each in a tab of its own.
private enum class StatsTab(@StringRes val title: Int, val stepping: Stepping) {
    OVERVIEW(R.string.stats_tab_overview, Stepping.TOGGLE),
    PULSE(R.string.stats_tab_pulse, Stepping.MONTH),
    // Best sellers and margins - once two tabs of their own - as one report.
    PRODUCTS(R.string.stats_tab_products, Stepping.MONTH),
    COSTS(R.string.stats_tab_costs, Stepping.MONTH),
    SUPPLIERS(R.string.stats_tab_suppliers, Stepping.MONTH),
    CREDIT(R.string.stats_tab_credit, Stepping.NONE),
    SELL_THROUGH(R.string.stats_tab_sell_through, Stepping.NONE),
}

// Picking a month should be picking a month.
//
// The calendar picker made you navigate to some arbitrary day inside the
// month you wanted and tap it - three interactions to express one choice,
// and it left the impression that the day mattered when everything
// downstream only ever reads the month.
private const val MONTHS_OFFERED = 36

@Composable
private fun MonthPickerDialog(
    selected: YearMonth,
    onDismiss: () -> Unit,
    onSelect: (YearMonth) -> Unit,
) {
    val locale = Locale.getDefault()
    // Newest first. A month later than the current one has nothing in it
    // to look at, unless one is somehow already selected.
    val months = remember(selected) {
        val newest = maxOf(YearMonth.from(businessToday()), selected)
        (0 until MONTHS_OFFERED).map { newest.minusMonths(it.toLong()) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.stats_pick_month)) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                months.forEach { month ->
                    val isSelected = month == selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                                else Color.Transparent
                            )
                            .clickable { onSelect(month) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Text(
                            formatMonth(month, locale),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        if (isSelected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        },
        // No confirm step: tapping a month is the choice.
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onEditSale: (String) -> Unit,
    viewModel: StatsViewModel = hiltViewModel(),
    reports: ReportsViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState
    var showDatePicker by remember { mutableStateOf(false) }
    var showMonthPicker by remember { mutableStateOf(false) }
    var saleToDelete by remember { mutableStateOf<SaleDto?>(null) }
    var selectedTab by rememberSaveable { mutableStateOf(StatsTab.OVERVIEW) }
    val uncategorizedLabel = stringResource(R.string.stats_uncategorized)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // What the arrows step by here: the toggle on the Overview, a month on
    // the monthly reports.
    val stepBy = if (selectedTab.stepping == Stepping.TOGGLE) state.period else StatsPeriod.MONTH
    val month = YearMonth.from(state.selectedDate)

    // A report loads when its tab is opened, and again when the month in
    // view changes - not before: most visits are for the day's figures.
    LaunchedEffect(selectedTab, month) {
        when (selectedTab) {
            StatsTab.PULSE -> reports.loadPulse(month)
            StatsTab.COSTS -> reports.loadCosts(month)
            StatsTab.SUPPLIERS -> reports.loadSuppliers(month)
            StatsTab.CREDIT -> reports.loadCredit()
            StatsTab.SELL_THROUGH -> reports.loadSellThrough()
            else -> Unit
        }
    }

    // Reloading keeps what's on screen, dimmed under a thin bar, rather than
    // blanking it: stepping through days with the arrows would otherwise
    // flash a spinner on every tap.
    val reloading = when (selectedTab) {
        StatsTab.OVERVIEW, StatsTab.PRODUCTS -> state.isLoading && state.hasLoaded
        StatsTab.PULSE -> reports.pulse.loading && reports.pulse.data != null
        StatsTab.COSTS -> reports.costs.loading && reports.costs.data != null
        StatsTab.SUPPLIERS -> reports.suppliers.loading && reports.suppliers.data != null
        StatsTab.CREDIT -> reports.credit.loading && reports.credit.data != null
        StatsTab.SELL_THROUGH -> reports.sellThrough.loading && reports.sellThrough.data != null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_title)) },
                // Day or month rides in the top bar rather than a row of its
                // own, so the figures start that much higher up the screen.
                actions = {
                    if (selectedTab.stepping == Stepping.TOGGLE) {
                        SingleChoiceSegmentedButtonRow(Modifier.width(150.dp).padding(end = 8.dp)) {
                            SegmentedButton(
                                selected = state.period == StatsPeriod.DAY,
                                onClick = { viewModel.onPeriodChange(StatsPeriod.DAY) },
                                shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(0, 2),
                                icon = {},
                            ) { Text(stringResource(R.string.stats_period_day), maxLines = 1) }
                            SegmentedButton(
                                selected = state.period == StatsPeriod.MONTH,
                                onClick = { viewModel.onPeriodChange(StatsPeriod.MONTH) },
                                shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(1, 2),
                                icon = {},
                            ) { Text(stringResource(R.string.stats_period_month), maxLines = 1) }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            ScrollableTabRow(selectedTabIndex = selectedTab.ordinal, edgePadding = 8.dp) {
                StatsTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(stringResource(tab.title), maxLines = 1) },
                    )
                }
            }

            if (selectedTab.stepping != Stepping.NONE) {
                // A day view asks for a day, a month view asks for a month -
                // picking a month by tapping a day inside it was always a
                // detour through information nothing needed.
                DateNavigator(
                    period = stepBy,
                    date = state.selectedDate,
                    canGoForward = state.canGoForward(stepBy),
                    isCurrent = state.isCurrent(stepBy),
                    onPrevious = { viewModel.step(forward = false, stepBy = stepBy) },
                    onNext = { viewModel.step(forward = true, stepBy = stepBy) },
                    onPick = { if (stepBy == StatsPeriod.MONTH) showMonthPicker = true else showDatePicker = true },
                    onJumpToNow = viewModel::jumpToNow,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }

            if (reloading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                // Same height as the bar, so the content doesn't jump when
                // it comes and goes.
                Spacer(Modifier.height(4.dp))
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .alpha(if (reloading) 0.55f else 1f)
                    .then(
                        if (selectedTab.stepping != Stepping.NONE) {
                            Modifier.swipeToStep(
                                rtl = rtl,
                                onPrevious = { viewModel.step(forward = false, stepBy = stepBy) },
                                onNext = { viewModel.step(forward = true, stepBy = stepBy) },
                            )
                        } else {
                            Modifier
                        }
                    ),
            ) {
                when (selectedTab) {
                    StatsTab.OVERVIEW, StatsTab.PRODUCTS ->
                        if (state.isLoading && !state.hasLoaded) {
                            Column(Modifier.fillMaxSize(), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                CircularProgressIndicator()
                            }
                        } else if (selectedTab == StatsTab.PRODUCTS) {
                            ProductsTab(
                                month = month,
                                topProducts = state.topProducts,
                                margins = state.margins,
                                uncategorizedLabel = uncategorizedLabel,
                            )
                        } else {
                            OverviewTab(
                                state = state,
                                onEditSale = onEditSale,
                                onDeleteSaleRequest = { saleToDelete = it },
                            )
                        }
                    StatsTab.PULSE -> PulseTab(reports.pulse, onRetry = { reports.loadPulse(month, force = true) })
                    StatsTab.COSTS -> CostTrendTab(
                        report = reports.costs,
                        window = reports.trendWindow,
                        onWindowChange = { reports.chooseTrendWindow(it, month) },
                        onRetry = { reports.loadCosts(month, force = true) },
                    )
                    StatsTab.SUPPLIERS -> SuppliersTab(
                        report = reports.suppliers,
                        window = reports.trendWindow,
                        onWindowChange = { reports.chooseTrendWindow(it, month) },
                        onRetry = { reports.loadSuppliers(month, force = true) },
                    )
                    StatsTab.CREDIT -> CreditTab(reports.credit, onRetry = { reports.loadCredit(force = true) })
                    StatsTab.SELL_THROUGH -> SellThroughTab(
                        report = reports.sellThrough,
                        paceDays = reports.paceDays,
                        onPaceChange = reports::choosePaceDays,
                        batches = reports.batches,
                        onOpenProduct = reports::loadBatches,
                        onRetry = { reports.loadSellThrough(force = true) },
                    )
                }
            }
        }
    }

    if (showMonthPicker) {
        MonthPickerDialog(
            selected = YearMonth.from(state.selectedDate),
            onDismiss = { showMonthPicker = false },
            onSelect = { picked ->
                viewModel.onMonthSelected(picked)
                showMonthPicker = false
            },
        )
    }

    if (showDatePicker) {
        // The picker works in UTC milliseconds: the selected day's UTC
        // midnight is that day to it, whatever the phone's own zone. (The
        // phone's local midnight, east of Greenwich, is the day before.)
        val initialMillis = state.selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        viewModel.onDateSelected(minOf(date, businessToday()))
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
internal fun OverviewTab(
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
                    hint = if (expenses.items.isEmpty()) stringResource(R.string.stats_no_expenses_this_period) else null,
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
        }
        state.otherSalesForRange?.let { otherSales ->
            item { Spacer(Modifier.height(16.dp)) }
            item {
                SectionTotalCard(
                    icon = Icons.Filled.Savings,
                    label = stringResource(R.string.stats_other_sales_title),
                    total = otherSales.total,
                    accent = profitColor(),
                    hint = if (otherSales.items.isEmpty()) stringResource(R.string.stats_no_other_sales_this_period) else null,
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
        }
        if (state.period == StatsPeriod.DAY) {
            item { Spacer(Modifier.height(16.dp)) }
            item {
                SectionTotalCard(
                    icon = Icons.Filled.ReceiptLong,
                    label = stringResource(R.string.sales_title),
                    total = state.periodRevenue,
                    accent = MaterialTheme.colorScheme.primary,
                    hint = if (state.salesForDay.isEmpty()) stringResource(R.string.stats_no_sales_this_day) else null,
                )
            }
            item { Spacer(Modifier.height(4.dp)) }
            items(state.salesForDay, key = { it.id }) { sale ->
                SaleRow(sale, onEdit = { onEditSale(sale.id) }, onDelete = { onDeleteSaleRequest(sale) })
            }
        }
    }
}

// A section's headline: an icon in a tinted disc, what the section is, and
// its total for the period. The same shape for expenses and for other
// income, so the two read as a pair rather than as two unrelated blocks.
// `hint` says the section is empty inside the card, rather than as loose
// text under it.
@Composable
private fun SectionTotalCard(
    icon: ImageVector,
    label: String,
    total: String,
    accent: Color,
    note: String? = null,
    hint: String? = null,
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
                if (hint != null) {
                    Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
//
// A group that has a breakdown - a catch-all name like "Other" with notes on
// its entries, or the spoilage group with its products - fills its bar in
// segments, one per line, so what it's made of shows before it's opened; a
// tap opens the lines themselves.
@Composable
private fun CategoryBreakdownCard(
    title: String,
    breakdown: List<CategoryTotalDto>,
    accent: Color,
) {
    val maxTotal = breakdown.maxOfOrNull { it.total.toDoubleOrNull() ?: 0.0 } ?: 0.0
    val grandTotal = breakdown.sumOf { it.total.toDoubleOrNull() ?: 0.0 }
    // Which groups are open. Keyed by kind and name so it survives the list
    // reloading for the same period.
    var expanded by remember(breakdown) { mutableStateOf(emptySet<String>()) }

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
                val key = "${row.kind}|${row.category}"
                CategoryGroupRow(
                    row = row,
                    color = categoryColor(index),
                    maxTotal = maxTotal,
                    grandTotal = grandTotal,
                    expanded = key in expanded,
                    onToggle = { expanded = if (key in expanded) expanded - key else expanded + key },
                )
            }
        }
    }
}

@Composable
private fun groupLabel(row: CategoryTotalDto): String = when {
    row.kind == "spoilage" -> stringResource(R.string.stats_spoiled_group)
    row.category.isNullOrBlank() -> stringResource(R.string.stats_uncategorized)
    else -> expenseDisplayName(row.category)
}

// The lines of a group, each with its shade of the group's colour - fully
// strong for the largest, fading down the list - and the "no note" remainder
// in a neutral grey, so it reads as what's left over rather than a category.
private data class ShadedLine(val line: CategorySubTotalDto, val value: Double, val color: Color)

private fun shadeLines(row: CategoryTotalDto, base: Color, neutral: Color): List<ShadedLine> {
    var rank = 0
    return row.breakdown.map { line ->
        val color = if (line.label == null) {
            neutral
        } else {
            base.copy(alpha = (1f - 0.18f * rank++).coerceAtLeast(0.3f))
        }
        ShadedLine(line, line.total.toDoubleOrNull() ?: 0.0, color)
    }
}

@Composable
private fun CategoryGroupRow(
    row: CategoryTotalDto,
    color: Color,
    maxTotal: Double,
    grandTotal: Double,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val value = row.total.toDoubleOrNull() ?: 0.0
    val fraction = if (maxTotal > 0) (value / maxTotal).toFloat().coerceIn(0f, 1f) else 0f
    val share = if (grandTotal > 0) (value / grandTotal) * 100 else 0.0
    val hasLines = row.breakdown.isNotEmpty()
    val lines = shadeLines(row, color, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
    val label = groupLabel(row)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .then(if (hasLines) Modifier.clickable(onClick = onToggle) else Modifier)
            .padding(vertical = 5.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (hasLines) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(formatAmount(row.total), style = MaterialTheme.typography.bodyMedium)
            if (hasLines) {
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
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
                // Only lines with an amount get a segment: a weight of zero
                // isn't allowed, and a zero-width segment would show nothing.
                val segments = lines.filter { it.value > 0 }
                if (segments.isEmpty()) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction)
                            .clip(RoundedCornerShape(3.dp))
                            .background(color),
                    )
                } else {
                    Row(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction)
                            .clip(RoundedCornerShape(3.dp)),
                        horizontalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        segments.forEach { segment ->
                            Box(
                                Modifier
                                    .weight(segment.value.toFloat())
                                    .fillMaxHeight()
                                    .background(segment.color),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            // How many entries made this category up, and what slice of the
            // period it is. Both were asked for; the individual entries
            // deliberately are not here.
            Text(
                "${formatPercent(share.toString())}% · " +
                    stringResource(R.string.stats_category_count, row.count),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        AnimatedVisibility(
            visible = expanded && hasLines,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            BreakdownLines(lines = lines, groupTotal = value, groupLabel = label, spoilage = row.kind == "spoilage")
        }
    }
}

// The opened group: its lines hanging off a guide in the group's colour, each
// with its amount, its share of the group (not of the period - the question
// here is what this group was made of), how many entries, and for spoilage
// how much of the product was lost.
@Composable
private fun BreakdownLines(lines: List<ShadedLine>, groupTotal: Double, groupLabel: String, spoilage: Boolean) {
    Row(Modifier.fillMaxWidth().padding(start = 3.dp, top = 6.dp).height(IntrinsicSize.Min)) {
        Box(
            Modifier
                .width(2.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(1.dp))
                .background(lines.firstOrNull()?.color ?: MaterialTheme.colorScheme.outlineVariant),
        )
        Column(Modifier.padding(start = 11.dp)) {
            lines.forEach { shaded ->
                val line = shaded.line
                val share = if (groupTotal > 0) (shaded.value / groupTotal) * 100 else 0.0
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(shaded.color))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            line.label ?: stringResource(R.string.stats_no_note),
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = if (line.label == null) FontStyle.Italic else FontStyle.Normal,
                            color = if (line.label == null) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(formatAmount(line.total), style = MaterialTheme.typography.bodySmall)
                    }
                    val details = buildList {
                        add(stringResource(R.string.stats_share_of_group, formatPercent(share.toString()), groupLabel))
                        add(stringResource(R.string.stats_category_count, line.count))
                        if (spoilage && line.quantity != null) {
                            add(
                                stringResource(
                                    R.string.stats_spoiled_quantity,
                                    "${formatQuantity(line.quantity)} ${line.unit.orEmpty()}".trim(),
                                )
                            )
                        }
                    }
                    Text(
                        details.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 15.dp),
                    )
                }
            }
        }
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
                    // How many checkouts made the period, and what one came
                    // to on average - a busy day of small baskets and a quiet
                    // day of big ones can take the same money.
                    if (state.periodSalesCount > 0) {
                        Text(
                            stringResource(
                                R.string.stats_sales_count_average,
                                state.periodSalesCount,
                                formatAmount(
                                    (state.periodRevenue.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO)
                                        .divide(state.periodSalesCount.toBigDecimal(), 2, java.math.RoundingMode.HALF_UP)
                                        .toPlainString()
                                ),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // Revenue against the day (or month) before - what the
                    // strip of neighbouring days used to show, in one line.
                    // Not for today or this month: half a day measured
                    // against a whole one only ever reads as a fall.
                    val previous = state.previousPeriod
                    val change = previous?.revenue?.toDoubleOrNull()?.let { percentChange(revenue, it) }
                    if (previous != null && change != null && !state.isCurrent()) {
                        Row(
                            Modifier.padding(top = 4.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            ChangeChip(change)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(
                                    if (state.period == StatsPeriod.DAY) R.string.stats_revenue_vs_day_before
                                    else R.string.stats_revenue_vs_month_before,
                                    formatAmount(previous.revenue),
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
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
            // cost, what was spent running the place, and - when there is
            // one - the surplus left over. Comparing three figures line by
            // line never showed which one was eating the takings.
            //
            // The bar's length is whichever is bigger, what came in or what
            // went out. So a period that spent more than it took is a bar
            // filled entirely with cost and expenses, with no green left,
            // which is the shortfall shown as the headline above.
            //
            // That remainder is deliberately not in the legend: it is the
            // headline figure, and printing it twice on one card only
            // invites the reader to check whether the two agree.
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

// One sale of the day: when it was rung up, what it came to and how many
// items, whether it went on credit (and to whom), then each line with its
// quantity, unit price and amount. Edit and delete stay one tap away.
@Composable
private fun SaleRow(sale: SaleDto, onEdit: () -> Unit, onDelete: () -> Unit) {
    val deferred = sale.paymentStatus == "DEFERRED"
    val time = runCatching {
        Instant.parse(sale.createdAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
    }.getOrDefault(formatIsoDateTime(sale.createdAt))
    Card(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 10.dp, end = 4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    time,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(formatAmount(sale.totalAmount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.stats_sale_items, sale.items.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (deferred) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                listOfNotNull(stringResource(R.string.deferred_sales_title), sale.customerName?.trim()?.takeIf { it.isNotEmpty() })
                                    .joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = warningColor(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(warningColor().copy(alpha = 0.14f))
                                    .padding(horizontal = 7.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (sale.items.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(end = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    sale.items.forEach { item ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(
                                item.product?.name ?: item.productId,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                stringResource(R.string.stats_sale_line_qty, formatQuantity(item.quantity), formatAmount(item.unitPrice)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(formatAmount(item.subtotal), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
