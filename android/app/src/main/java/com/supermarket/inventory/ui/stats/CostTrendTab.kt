package com.supermarket.inventory.ui.stats

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.supermarket.inventory.R
import com.supermarket.inventory.data.remote.dto.CostNameDto
import com.supermarket.inventory.data.remote.dto.CostTrendDto
import com.supermarket.inventory.ui.common.expenseDisplayName
import com.supermarket.inventory.ui.common.formatAmount
import com.supermarket.inventory.ui.common.formatPercent
import com.supermarket.inventory.ui.theme.lossColor
import com.supermarket.inventory.ui.theme.profitColor
import com.supermarket.inventory.ui.theme.warningColor
import java.math.BigDecimal
import java.math.RoundingMode

@Composable
fun CostTrendTab(
    report: ReportState<CostTrendDto>,
    window: Int,
    onWindowChange: (Int) -> Unit,
    onRetry: () -> Unit,
) {
    val data = report.data
    when {
        data == null && report.loading -> ReportLoading()
        data == null && report.error != null -> ReportError(report.error, onRetry)
        data != null -> CostTrendContent(data, window, onWindowChange)
    }
}

@Composable
private fun CostTrendContent(r: CostTrendDto, window: Int, onWindowChange: (Int) -> Unit) {
    val fixedColor = MaterialTheme.colorScheme.primary
    val variableColor = MaterialTheme.colorScheme.tertiary
    val spoilColor = warningColor()
    val sel = r.monthRows.last()
    val prev = r.monthRows.getOrNull(r.monthRows.size - 2)
    val labels = r.months.map { shortMonth(it) }

    LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp)) {
        item {
            ChoiceChips(
                options = listOf(6 to stringResource(R.string.reports_months_n, 6), 12 to stringResource(R.string.reports_months_n, 12)),
                selected = window,
                onSelect = onWindowChange,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        // ---- fixed costs this month ----
        item {
            ReportCard(
                title = stringResource(R.string.costs_fixed_this_month),
                subtitle = stringResource(R.string.costs_fixed_explained, r.recurringThreshold, r.activeMonths),
                icon = Icons.Filled.Balance,
                accent = fixedColor,
            ) {
                val typicalText = r.typicalRecurring
                val typical = typicalText?.money()
                if (sel.inProgress && typicalText != null && typical != null && typical > 0) {
                    Text(
                        stringResource(R.string.costs_paid_so_far_of_typical, formatAmount(sel.recurring), formatAmount(typicalText)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    MeterBar((sel.recurring.money() / typical).toFloat(), fixedColor, Modifier.fillMaxWidth())
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KpiTile(
                            label = stringResource(R.string.costs_fixed),
                            value = formatAmount(sel.recurring),
                            accent = fixedColor,
                            change = prev?.let { percentChange(sel.recurring.money(), it.recurring.money()) },
                            higherIsGood = false,
                            footnote = prev?.let { stringResource(R.string.costs_vs_month, shortMonth(it.month)) },
                            modifier = Modifier.weight(1f),
                        )
                        KpiTile(
                            label = stringResource(R.string.costs_typical_month),
                            value = r.typicalRecurring?.let { formatAmount(it) } ?: "—",
                            change = typical?.takeIf { it > 0 }?.let { percentChange(sel.recurring.money(), it) },
                            higherIsGood = false,
                            footnote = stringResource(R.string.costs_this_month_vs_typical),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KpiTile(stringResource(R.string.costs_variable), formatAmount(sel.variable), Modifier.weight(1f), accent = variableColor)
                    KpiTile(stringResource(R.string.costs_spoilage), formatAmount(sel.spoilage), Modifier.weight(1f), accent = spoilColor)
                }
            }
        }

        // ---- break-even ----
        item { BreakEvenCard(r) }

        // ---- the trend ----
        item {
            ReportCard(title = stringResource(R.string.costs_month_by_month), icon = Icons.Filled.ShowChart, accent = fixedColor) {
                StackedMonthBars(
                    labels = labels,
                    segments = r.monthRows.map { m ->
                        listOf(m.recurring.money() to fixedColor, m.variable.money() to variableColor, m.spoilage.money() to spoilColor)
                    },
                    reference = r.typicalRecurring?.money(),
                    highlight = r.monthRows.lastIndex,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LegendDot(fixedColor, stringResource(R.string.costs_fixed))
                    LegendDot(variableColor, stringResource(R.string.costs_variable))
                    LegendDot(spoilColor, stringResource(R.string.costs_spoilage))
                }
                if (r.typicalRecurring != null) {
                    Text(
                        stringResource(R.string.costs_dashed_line, formatAmount(r.typicalRecurring)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                // How much of each month's gross profit its fixed costs ate.
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.costs_share_of_gross_profit), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth()) {
                    r.monthRows.forEachIndexed { i, m ->
                        val gp = m.grossProfit.money()
                        val share = if (gp > 0) m.recurring.money() / gp * 100 else null
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                share?.let { "${formatPercent(it.toString())}%" } ?: "—",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (i == r.monthRows.lastIndex) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    share == null -> MaterialTheme.colorScheme.onSurfaceVariant
                                    share > 100 -> lossColor()
                                    share > 70 -> warningColor()
                                    else -> profitColor()
                                },
                                maxLines = 1,
                            )
                            Text(labels[i], style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
                Text(
                    stringResource(R.string.costs_over_100_means),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        // ---- biggest movers since last month ----
        if (prev != null) {
            val movers = r.names
                .map { n -> n to (n.series.last().money() - n.series[n.series.size - 2].money()) }
                .filter { it.second != 0.0 }
                .sortedByDescending { kotlin.math.abs(it.second) }
            if (movers.isNotEmpty()) {
                item {
                    ReportCard(
                        title = stringResource(R.string.costs_movers),
                        subtitle = stringResource(R.string.costs_movers_subtitle, shortMonth(prev.month), shortMonth(sel.month)),
                        icon = Icons.Filled.SwapVert,
                        accent = MaterialTheme.colorScheme.secondary,
                    ) {
                        movers.forEach { (n, delta) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(nameLabel(n), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${formatAmount(n.series[n.series.size - 2])} → ${formatAmount(n.series.last())}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    (if (delta > 0) "+" else "−") + formatAmount(kotlin.math.abs(delta).toString()),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (delta > 0) lossColor() else profitColor(),
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---- every expense name ----
        item {
            ReportCard(title = stringResource(R.string.costs_every_expense), icon = Icons.Filled.Tune, accent = MaterialTheme.colorScheme.outline) {
                val fixed = r.names.filter { it.recurring }
                val variable = r.names.filter { !it.recurring }
                if (fixed.isNotEmpty()) {
                    Text(stringResource(R.string.costs_fixed), style = MaterialTheme.typography.labelMedium, color = fixedColor, fontWeight = FontWeight.SemiBold)
                    fixed.forEach { NameRow(it, r.months.size, fixedColor) }
                }
                if (variable.isNotEmpty()) {
                    if (fixed.isNotEmpty()) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text(stringResource(R.string.costs_variable), style = MaterialTheme.typography.labelMedium, color = variableColor, fontWeight = FontWeight.SemiBold)
                    variable.forEach { NameRow(it, r.months.size, variableColor) }
                }
                val spoilSeries = r.monthRows.map { it.spoilage.money() }
                if (spoilSeries.any { it > 0 }) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.costs_spoilage), style = MaterialTheme.typography.bodyMedium, color = spoilColor)
                            Text(
                                stringResource(R.string.costs_window_total, formatAmount(spoilSeries.sum().toString())),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Sparkline(spoilSeries, spoilColor, Modifier.width(110.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun nameLabel(n: CostNameDto): String =
    n.name?.let { expenseDisplayName(it) } ?: stringResource(R.string.stats_uncategorized)

@Composable
private fun NameRow(n: CostNameDto, months: Int, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(nameLabel(n), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(R.string.costs_name_detail, formatAmount(n.average), n.monthsPresent, months),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Sparkline(n.series.map { it.money() }, color, Modifier.width(110.dp))
    }
}

// The revenue the month's gross margin needed to pay for its fixed costs,
// and how the month's actual revenue compares. For the month under way, the
// fixed costs are a typical month's rather than the part paid so far - the
// question is whether this month will cover its bills, not the ones already
// in. Worked in BigDecimal from the server's exact figures.
@Composable
private fun BreakEvenCard(r: CostTrendDto) {
    val sel = r.monthRows.last()
    val revenue = BigDecimal(sel.revenue)
    val gross = BigDecimal(sel.grossProfit)
    val fixed = if (sel.inProgress && r.typicalRecurring != null) BigDecimal(r.typicalRecurring) else BigDecimal(sel.recurring)
    val breakEven = if (gross.signum() > 0 && revenue.signum() > 0) {
        fixed.multiply(revenue).divide(gross, 2, RoundingMode.HALF_UP)
    } else {
        null
    }
    ReportCard(
        title = stringResource(R.string.costs_break_even),
        subtitle = stringResource(if (sel.inProgress) R.string.costs_break_even_typical else R.string.costs_break_even_actual),
        icon = Icons.Filled.Balance,
        accent = profitColor(),
    ) {
        if (breakEven == null) {
            Text(stringResource(R.string.costs_break_even_unknown), style = MaterialTheme.typography.bodyMedium)
            return@ReportCard
        }
        val margin = gross.multiply(BigDecimal(100)).divide(revenue, 1, RoundingMode.HALF_UP)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KpiTile(stringResource(R.string.costs_needed_revenue), formatAmount(breakEven.toPlainString()), Modifier.weight(1f), accent = profitColor())
            KpiTile(
                stringResource(if (sel.inProgress) R.string.costs_revenue_so_far else R.string.costs_actual_revenue),
                formatAmount(sel.revenue),
                Modifier.weight(1f),
                footnote = stringResource(R.string.costs_at_margin, formatPercent(margin.toPlainString())),
            )
        }
        Spacer(Modifier.height(8.dp))
        val covered = revenue >= breakEven
        MeterBar((revenue.toDouble() / breakEven.toDouble()).toFloat(), if (covered) profitColor() else warningColor(), Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp))
        Text(
            if (covered) {
                stringResource(R.string.costs_covered_by, formatAmount(revenue.subtract(breakEven).toPlainString()))
            } else {
                stringResource(R.string.costs_short_by, formatAmount(breakEven.subtract(revenue).toPlainString()))
            },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = if (covered) profitColor() else warningColor(),
        )
    }
}
