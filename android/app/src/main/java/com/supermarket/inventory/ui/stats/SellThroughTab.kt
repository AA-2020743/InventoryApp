package com.supermarket.inventory.ui.stats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.supermarket.inventory.R
import com.supermarket.inventory.data.remote.dto.SellThroughDto
import com.supermarket.inventory.data.remote.dto.SellThroughProductDto
import com.supermarket.inventory.data.remote.dto.StockBatchDto
import com.supermarket.inventory.data.remote.dto.StockBatchesDto
import com.supermarket.inventory.ui.common.formatAmount
import com.supermarket.inventory.ui.common.formatPercent
import com.supermarket.inventory.ui.common.formatQuantity
import com.supermarket.inventory.ui.theme.lossColor
import com.supermarket.inventory.ui.theme.profitColor
import com.supermarket.inventory.ui.theme.warningColor
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

// The server's five verdicts, in the order they're offered as filters:
// what needs reordering first, what's tying money up last.
private val STATUSES = listOf("FAST", "STEADY", "SLOW", "STAGNANT", "OUT")

// Days of stock beyond this read as "a long way off"; the meter under each
// product is full at this many days.
private const val COVER_SCALE_DAYS = 60.0

private enum class PaceSort { RUNS_OUT, CAPITAL, SLOWEST, BEST }

@Composable
private fun statusColor(status: String): Color = when (status) {
    "FAST" -> profitColor()
    "STEADY" -> MaterialTheme.colorScheme.primary
    "SLOW" -> warningColor()
    "STAGNANT" -> lossColor()
    else -> MaterialTheme.colorScheme.outline
}

@Composable
private fun statusLabel(status: String): String = stringResource(
    when (status) {
        "FAST" -> R.string.sell_status_fast
        "STEADY" -> R.string.sell_status_steady
        "SLOW" -> R.string.sell_status_slow
        "STAGNANT" -> R.string.sell_status_stagnant
        else -> R.string.sell_status_out
    }
)

// Days to one decimal place, and whole days once there are ten or more -
// "12.4 days" of stock is more precision than the question has.
private fun formatDays(days: Double): String =
    formatPercent((if (days >= 10) Math.round(days).toDouble() else days).toString())

private fun businessDate(iso: String?): LocalDate? =
    iso?.let { runCatching { Instant.parse(it).atZone(BUSINESS_ZONE).toLocalDate() }.getOrNull() }

private fun shortDate(date: LocalDate, locale: Locale = Locale.getDefault()): String {
    val pattern = if (date.year == businessToday().year) "d MMM" else "d MMM yyyy"
    return date.format(DateTimeFormatter.ofPattern(pattern, locale))
}

private fun decimalSum(values: List<String>): BigDecimal =
    values.fold(BigDecimal.ZERO) { acc, v -> acc + (v.toBigDecimalOrNull() ?: BigDecimal.ZERO) }

@Composable
fun SellThroughTab(
    report: ReportState<SellThroughDto>,
    paceDays: Int,
    onPaceChange: (Int) -> Unit,
    batches: ReportState<StockBatchesDto>,
    onOpenProduct: (String) -> Unit,
    onRetry: () -> Unit,
) {
    val data = report.data
    when {
        data == null && report.loading -> ReportLoading()
        data == null && report.error != null -> ReportError(report.error, onRetry)
        data != null -> SellThroughContent(data, paceDays, onPaceChange, batches, onOpenProduct)
    }
}

@Composable
private fun SellThroughContent(
    r: SellThroughDto,
    paceDays: Int,
    onPaceChange: (Int) -> Unit,
    batches: ReportState<StockBatchesDto>,
    onOpenProduct: (String) -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf(PaceSort.RUNS_OUT) }
    var query by rememberSaveable { mutableStateOf("") }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }

    val shown = remember(r, filter, sort, query) {
        val q = query.trim()
        r.products
            .filter { filter == null || it.status == filter }
            .filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) || it.category.orEmpty().contains(q, ignoreCase = true) }
            .let { list ->
                when (sort) {
                    // Soonest to run out first; nothing selling, and nothing
                    // in stock, have no date and go after everything that does.
                    PaceSort.RUNS_OUT -> list.sortedWith(compareBy<SellThroughProductDto, Double?>(nullsLast()) { it.daysOfCover })
                    PaceSort.CAPITAL -> list.sortedByDescending { it.stockValue.money() }
                    // Stock that isn't selling at all is the slowest of all.
                    PaceSort.SLOWEST -> list.sortedWith(
                        compareByDescending<SellThroughProductDto> { it.status == "STAGNANT" }
                            .thenByDescending { it.daysOfCover ?: -1.0 }
                            .thenByDescending { it.stockValue.money() }
                    )
                    PaceSort.BEST -> list.sortedByDescending { it.revenue.money() }
                }
            }
    }

    LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp).horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.sell_pace_over),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                ChoiceChips(
                    options = listOf(30, 60, 90).map { it to stringResource(R.string.reports_days_n, it) },
                    selected = paceDays,
                    onSelect = onPaceChange,
                )
            }
        }
        if (r.products.isEmpty()) {
            item { ReportEmpty(stringResource(R.string.sell_none)) }
            return@LazyColumn
        }

        item { ShelfCard(r, onFilter = { filter = if (filter == it) null else it }) }

        // ---- find, filter, order ----
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.sell_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.sell_search_clear))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            )
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = filter == null,
                    onClick = { filter = null },
                    label = { Text(stringResource(R.string.sell_filter_count, stringResource(R.string.sell_filter_all), r.summary.products)) },
                )
                STATUSES.forEach { s ->
                    val count = r.summary.counts[s] ?: 0
                    if (count > 0) {
                        FilterChip(
                            selected = filter == s,
                            onClick = { filter = if (filter == s) null else s },
                            label = { Text(stringResource(R.string.sell_filter_count, statusLabel(s), count)) },
                            leadingIcon = { Box(Modifier.size(8.dp).clip(CircleShape).background(statusColor(s))) },
                        )
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp).horizontalScroll(rememberScrollState())) {
                ChoiceChips(
                    options = listOf(
                        PaceSort.RUNS_OUT to stringResource(R.string.sell_sort_runs_out),
                        PaceSort.CAPITAL to stringResource(R.string.sell_sort_capital),
                        PaceSort.SLOWEST to stringResource(R.string.sell_sort_slowest),
                        PaceSort.BEST to stringResource(R.string.sell_sort_best),
                    ),
                    selected = sort,
                    onSelect = { sort = it },
                )
            }
        }
        if (shown.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.sell_no_match),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
            }
        }
        items(shown, key = { it.productId }) { p ->
            val open = openId == p.productId
            ProductPaceCard(
                p = p,
                paceDays = r.days,
                open = open,
                batches = batches.takeIf { it.key == p.productId },
                onToggle = {
                    openId = if (open) null else p.productId
                    if (!open) onOpenProduct(p.productId)
                },
            )
        }
    }
}

// What's on the shelf, and how much of that money is moving. The bar splits
// the stock's value by verdict, so "a third of what we hold isn't selling"
// is visible before any product is read; each verdict narrows the list below.
@Composable
private fun ShelfCard(r: SellThroughDto, onFilter: (String) -> Unit) {
    val s = r.summary
    val total = s.stockValue.money()
    val byStatus = remember(r) {
        STATUSES.associateWith { st -> decimalSum(r.products.filter { it.status == st }.map { it.stockValue }) }
    }
    ReportCard(
        title = stringResource(R.string.sell_shelf_title),
        subtitle = stringResource(R.string.sell_shelf_subtitle, s.products),
        icon = Icons.Filled.Inventory2,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KpiTile(stringResource(R.string.sell_stock_value), formatAmount(s.stockValue), Modifier.weight(1f))
            KpiTile(
                stringResource(R.string.sell_status_stagnant),
                formatAmount(s.stagnantValue),
                Modifier.weight(1f),
                accent = if (s.stagnantValue.money() > 0) lossColor() else MaterialTheme.colorScheme.onSurface,
                footnote = stringResource(R.string.sell_stagnant_note, s.counts["STAGNANT"] ?: 0, r.days),
            )
        }
        Spacer(Modifier.height(8.dp))
        KpiTile(
            stringResource(R.string.sell_slow_value),
            formatAmount(s.slowValue),
            Modifier.fillMaxWidth(),
            accent = if (s.slowValue.money() > 0) warningColor() else MaterialTheme.colorScheme.onSurface,
            footnote = stringResource(R.string.sell_slow_note, s.counts["SLOW"] ?: 0),
        )
        if (total > 0) {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.sell_where_money_sits), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp))) {
                STATUSES.forEach { st ->
                    val v = byStatus[st]?.toDouble() ?: 0.0
                    if (v > 0) Box(Modifier.weight(v.toFloat()).fillMaxHeight().background(statusColor(st)))
                }
            }
            Spacer(Modifier.height(6.dp))
            STATUSES.forEach { st ->
                val v = byStatus[st] ?: BigDecimal.ZERO
                val count = s.counts[st] ?: 0
                if (count > 0 && st != "OUT") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onFilter(st) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LegendDot(statusColor(st), stringResource(R.string.sell_filter_count, statusLabel(st), count), Modifier.weight(1f))
                        Text(
                            stringResource(
                                R.string.sell_share_row,
                                formatAmount(v.toPlainString()),
                                formatPercent((v.toDouble() / total * 100).toString()),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            val out = s.counts["OUT"] ?: 0
            if (out > 0) {
                Text(
                    stringResource(R.string.sell_out_note, out),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onFilter("OUT") }
                        .padding(vertical = 4.dp),
                )
            }
        }
    }
}

// One product: how long what's on the shelf will last at the current pace,
// and - from its history - how long a delivery of it usually takes to go.
// Opening it shows each delivery on a timeline.
@Composable
private fun ProductPaceCard(
    p: SellThroughProductDto,
    paceDays: Int,
    open: Boolean,
    batches: ReportState<StockBatchesDto>?,
    onToggle: () -> Unit,
) {
    val color = statusColor(p.status)
    val cover = p.daysOfCover
    Card(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.clickable(onClick = onToggle).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            statusLabel(p.status),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = color,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(color.copy(alpha = 0.12f))
                                .padding(horizontal = 7.dp, vertical = 1.dp),
                        )
                        if (!p.category.isNullOrBlank()) {
                            Spacer(Modifier.width(6.dp))
                            Text(p.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                // The headline: days of stock left, or why there's no figure.
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        when {
                            p.status == "OUT" -> "—"
                            cover == null -> "∞"
                            cover < 1 -> "<1"
                            else -> formatDays(cover)
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = color,
                    )
                    Text(
                        stringResource(if (p.status == "OUT") R.string.sell_status_out else R.string.sell_days_left),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp).size(20.dp),
                )
            }

            if (p.status != "OUT") {
                MeterBar(
                    fraction = if (cover == null) 1f else (cover / COVER_SCALE_DAYS).toFloat(),
                    color = color.copy(alpha = 0.85f),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            }

            Spacer(Modifier.height(8.dp))
            val details = buildList {
                if (p.unitsSold.money() > 0) {
                    add(stringResource(R.string.sell_pace_line, formatQuantity(p.perDay), p.unit))
                } else {
                    add(stringResource(R.string.sell_no_sales_in, paceDays))
                }
                if (p.status != "OUT") {
                    add(stringResource(R.string.sell_in_stock, formatQuantity(p.quantity), p.unit, formatAmount(p.stockValue)))
                }
            }
            Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall)

            businessDate(p.runsOutOn)?.let { date ->
                Text(
                    stringResource(R.string.sell_runs_out, shortDate(date)),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (p.status == "FAST") FontWeight.SemiBold else FontWeight.Normal,
                    color = if (p.status == "FAST") warningColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (p.measuredDays < paceDays) {
                Text(
                    stringResource(R.string.sell_new_product, formatDays(p.measuredDays)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // From the whole history, not just the window.
            val history = buildList {
                p.avgDaysToClear?.let { avg ->
                    add(
                        if (p.batchesCleared > 1 && p.fastestClear != null && p.slowestClear != null) {
                            stringResource(
                                R.string.sell_clear_range,
                                formatDays(avg),
                                formatDays(p.fastestClear),
                                formatDays(p.slowestClear),
                                p.batchesCleared,
                            )
                        } else {
                            stringResource(R.string.sell_clear_once, formatDays(avg))
                        }
                    )
                }
                p.oldestStockDays?.let { add(stringResource(R.string.sell_oldest_stock, formatDays(it))) }
                val lastSold = businessDate(p.lastSoldAt)
                when {
                    lastSold == null -> add(stringResource(R.string.sell_never_sold))
                    p.unitsSold.money() <= 0 -> add(
                        stringResource(R.string.sell_last_sold, ChronoUnit.DAYS.between(lastSold, businessToday()).toInt())
                    )
                }
            }
            if (history.isNotEmpty()) {
                Text(
                    history.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            AnimatedVisibility(
                visible = open,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                BatchTimeline(batches, p.unit)
            }
        }
    }
}

// Each delivery as a stop on a line, newest at the top: when it came, how
// much, and either how many days it took to sell through - with a bar
// against the slowest one, so a delivery that dragged stands out - or how
// much of it is still on the shelf and for how long.
@Composable
private fun BatchTimeline(state: ReportState<StockBatchesDto>?, unit: String) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(stringResource(R.string.sell_batches_title), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        val data = state?.data
        val error = state?.error
        when {
            data == null && error != null -> Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            data == null -> Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            }
            data.batches.isEmpty() -> Text(
                stringResource(R.string.sell_batches_none),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> {
                val slowest = data.batches.mapNotNull { it.daysToClear ?: it.daysOnShelf }.maxOrNull()?.takeIf { it > 0 } ?: 1.0
                data.batches.forEachIndexed { i, b ->
                    BatchStop(b, unit, slowest, first = i == 0, last = i == data.batches.lastIndex)
                }
            }
        }
    }
}

@Composable
private fun BatchStop(b: StockBatchDto, unit: String, slowest: Double, first: Boolean, last: Boolean) {
    val cleared = b.clearedAt != null
    val dotColor = if (cleared) profitColor() else MaterialTheme.colorScheme.primary
    val line = MaterialTheme.colorScheme.outlineVariant
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(Modifier.width(16.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.width(2.dp).height(8.dp).background(if (first) Color.Transparent else line))
            Box(Modifier.size(10.dp).clip(CircleShape).background(dotColor))
            Box(Modifier.width(2.dp).weight(1f).background(if (last) Color.Transparent else line))
        }
        Column(Modifier.weight(1f).padding(start = 8.dp, top = 4.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (b.opening) stringResource(R.string.sell_batch_opening)
                    else businessDate(b.receivedAt)?.let { shortDate(it) } ?: b.receivedAt,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${formatQuantity(b.quantity)} $unit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (cleared && b.daysToClear != null) {
                MeterBar((b.daysToClear / slowest).toFloat(), profitColor().copy(alpha = 0.7f), Modifier.fillMaxWidth().padding(top = 4.dp))
                Text(
                    stringResource(
                        R.string.sell_batch_cleared,
                        formatDays(b.daysToClear),
                        businessDate(b.clearedAt)?.let { shortDate(it) } ?: "",
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            } else {
                val quantity = b.quantity.money()
                val sold = if (quantity > 0) ((quantity - b.remaining.money()) / quantity).toFloat() else 0f
                MeterBar(sold, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), Modifier.fillMaxWidth().padding(top = 4.dp))
                Text(
                    stringResource(
                        R.string.sell_batch_open,
                        formatQuantity(b.remaining),
                        unit,
                        formatDays(b.daysOnShelf ?: 0.0),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
