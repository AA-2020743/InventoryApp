package com.supermarket.inventory.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.supermarket.inventory.R
import com.supermarket.inventory.data.remote.dto.PulseDto
import com.supermarket.inventory.ui.common.formatAmount
import com.supermarket.inventory.ui.common.formatPercent
import com.supermarket.inventory.ui.common.formatQuantity
import com.supermarket.inventory.ui.theme.lossColor
import com.supermarket.inventory.ui.theme.profitColor
import com.supermarket.inventory.ui.theme.warningColor
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// Rows of the rhythm grid, in the order an Egyptian week runs: Saturday
// first. Server weekdays are 0 = Sunday ... 6 = Saturday.
private val WEEK_ORDER = listOf(6, 0, 1, 2, 3, 4, 5)
private fun dayOfWeek(serverIndex: Int): DayOfWeek = DayOfWeek.of(if (serverIndex == 0) 7 else serverIndex)

@Composable
fun PulseTab(report: ReportState<PulseDto>, onRetry: () -> Unit) {
    val data = report.data
    when {
        data == null && report.loading -> ReportLoading()
        data == null && report.error != null -> ReportError(report.error, onRetry)
        data != null -> PulseContent(data)
    }
}

@Composable
private fun PulseContent(p: PulseDto) {
    val locale = Locale.getDefault()
    LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp)) {
        // ---- the month so far, against the same stretch of last month ----
        item {
            ReportCard(
                title = stringResource(if (p.inProgress) R.string.pulse_month_so_far else R.string.pulse_month_total),
                subtitle = stringResource(if (p.inProgress) R.string.pulse_vs_same_stretch else R.string.pulse_vs_last_month),
                icon = Icons.Filled.Insights,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KpiTile(
                        label = stringResource(R.string.pulse_revenue),
                        value = formatAmount(p.current.revenue),
                        change = percentChange(p.current.revenue.money(), p.previous.revenue.money()),
                        footnote = stringResource(R.string.pulse_was, formatAmount(p.previous.revenue)),
                        modifier = Modifier.weight(1f),
                    )
                    KpiTile(
                        label = stringResource(R.string.pulse_profit),
                        value = formatAmount(p.current.profit),
                        accent = if (p.current.profit.money() >= 0) profitColor() else lossColor(),
                        change = percentChange(p.current.profit.money(), p.previous.profit.money()),
                        footnote = stringResource(R.string.pulse_was, formatAmount(p.previous.profit)),
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.pulse_sales_count, p.current.sales, p.previous.sales),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                p.projection?.let { proj ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.pulse_month_elapsed, formatPercent(proj.elapsedPercent.toString())),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    MeterBar((proj.elapsedPercent / 100).toFloat(), MaterialTheme.colorScheme.primary, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KpiTile(
                            label = stringResource(R.string.pulse_heading_for_revenue),
                            value = formatAmount(proj.revenue),
                            accent = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        KpiTile(
                            label = stringResource(R.string.pulse_heading_for_profit),
                            value = formatAmount(proj.profit),
                            accent = if (proj.profit.money() >= 0) profitColor() else lossColor(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        stringResource(R.string.pulse_projection_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }

        // ---- basket ----
        item {
            ReportCard(title = stringResource(R.string.pulse_basket), icon = Icons.Filled.ShoppingBasket, accent = MaterialTheme.colorScheme.tertiary) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KpiTile(
                        label = stringResource(R.string.pulse_average_sale),
                        value = p.basket.averageSale?.let { formatAmount(it) } ?: "—",
                        change = p.basket.averageSale?.let { cur ->
                            p.basket.previousAverageSale?.let { prev -> percentChange(cur.money(), prev.money()) }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    KpiTile(
                        label = stringResource(R.string.pulse_lines_per_sale),
                        value = p.basket.averageLines?.let { formatQuantity(it.toString()) } ?: "—",
                        modifier = Modifier.weight(1f),
                    )
                    KpiTile(
                        label = stringResource(R.string.pulse_on_credit),
                        value = "${formatPercent(p.creditSharePercent.toString())}%",
                        accent = if (p.creditSharePercent > 20) warningColor() else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (p.bestDay != null && p.quietestDay != null && p.bestDay.date != p.quietestDay.date) {
                    Spacer(Modifier.height(8.dp))
                    val fmt = DateTimeFormatter.ofPattern("EEEE d MMM", locale)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KpiTile(
                            label = stringResource(R.string.pulse_best_day),
                            value = formatAmount(p.bestDay.revenue),
                            accent = profitColor(),
                            footnote = LocalDate.parse(p.bestDay.date).format(fmt),
                            modifier = Modifier.weight(1f),
                        )
                        KpiTile(
                            label = stringResource(R.string.pulse_quietest_day),
                            value = formatAmount(p.quietestDay.revenue),
                            footnote = LocalDate.parse(p.quietestDay.date).format(fmt),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // ---- rhythm ----
        item { RhythmCard(p) }

        // ---- concentration ----
        item {
            val pareto = p.pareto
            ReportCard(
                title = stringResource(R.string.pulse_concentration),
                subtitle = stringResource(R.string.pulse_concentration_subtitle),
                icon = Icons.Filled.PieChart,
                accent = profitColor(),
            ) {
                if (pareto.productsSold == 0) {
                    Text(stringResource(R.string.pulse_no_sales_yet), style = MaterialTheme.typography.bodyMedium)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${pareto.productsForShare}",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = profitColor(),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(
                                R.string.pulse_concentration_headline,
                                pareto.productsSold,
                                formatPercent(pareto.sharePercent.toString()),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    val maxProfit = pareto.products.maxOfOrNull { it.profit.money() }?.takeIf { it > 0 } ?: 1.0
                    pareto.products.take(pareto.productsForShare).forEachIndexed { i, prod ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(22.dp))
                            Column(Modifier.weight(1f)) {
                                Row {
                                    Text(prod.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Text(formatAmount(prod.profit), style = MaterialTheme.typography.bodySmall, color = profitColor())
                                }
                                MeterBar((prod.profit.money() / maxProfit).toFloat(), profitColor(), Modifier.fillMaxWidth().padding(top = 3.dp))
                                Text(
                                    stringResource(R.string.pulse_running_share, formatPercent(prod.cumulativeSharePercent.toString())),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    // Products that lost money this month - sold below cost.
                    val losers = pareto.products.filter { it.profit.money() < 0 }
                    if (losers.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.pulse_sold_at_loss), style = MaterialTheme.typography.labelMedium, color = lossColor(), fontWeight = FontWeight.SemiBold)
                        losers.forEach { prod ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                Text(prod.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(formatAmount(prod.profit), style = MaterialTheme.typography.bodySmall, color = lossColor())
                            }
                        }
                    }
                }
            }
        }
    }
}

// Revenue by weekday and hour, darker where the shop takes more - when to
// have staff on and stock out, and when it's quiet enough for a delivery.
@Composable
private fun RhythmCard(p: PulseDto) {
    val locale = Locale.getDefault()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val cells = p.heatmap.cells
    val values = cells.map { row -> row.map { it.revenue.money() } }
    val max = values.flatten().maxOrNull()?.takeIf { it > 0 } ?: 1.0
    // Only the hours the shop actually trades in, so the grid isn't mostly
    // empty night.
    val usedHours = (0 until 24).filter { h -> values.any { it[h] > 0 } }
    val firstHour = usedHours.minOrNull() ?: 8
    val lastHour = usedHours.maxOrNull() ?: 22
    val hours = (firstHour..lastHour).toList()
    var busiest: Pair<Int, Int>? = null
    var busiestValue = 0.0
    values.forEachIndexed { d, row -> row.forEachIndexed { h, v -> if (v > busiestValue) { busiestValue = v; busiest = d to h } } }
    val base = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceVariant

    ReportCard(
        title = stringResource(R.string.pulse_rhythm),
        subtitle = stringResource(R.string.pulse_rhythm_subtitle, p.heatmap.days),
        icon = Icons.Filled.LocalFireDepartment,
        accent = warningColor(),
    ) {
        busiest?.let { (d, h) ->
            Text(
                stringResource(
                    R.string.pulse_busiest,
                    dayOfWeek(d).getDisplayName(TextStyle.FULL, locale),
                    String.format(locale, "%02d:00", h),
                    String.format(locale, "%02d:00", (h + 1) % 24),
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
        }
        WEEK_ORDER.forEach { d ->
            Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    dayOfWeek(d).getDisplayName(TextStyle.SHORT, locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(40.dp),
                    maxLines = 1,
                )
                Canvas(Modifier.weight(1f).height(16.dp)) {
                    val n = hours.size
                    val gap = 2.dp.toPx()
                    val w = (size.width - gap * (n - 1)) / n
                    hours.forEachIndexed { i, h ->
                        val slot = if (rtl) n - 1 - i else i
                        val v = values.getOrNull(d)?.getOrNull(h) ?: 0.0
                        val c = if (v <= 0) empty else base.copy(alpha = (0.15f + 0.85f * (v / max).toFloat()).coerceIn(0.15f, 1f))
                        drawRoundRect(c, Offset(slot * (w + gap), 0f), Size(w, size.height), CornerRadius(3.dp.toPx()))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 40.dp, top = 4.dp)) {
            Text(String.format(locale, "%02d", firstHour), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.weight(1f))
            Text(String.format(locale, "%02d", lastHour), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
