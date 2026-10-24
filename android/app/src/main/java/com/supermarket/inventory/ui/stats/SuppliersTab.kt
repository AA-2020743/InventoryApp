package com.supermarket.inventory.ui.stats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.supermarket.inventory.R
import com.supermarket.inventory.data.remote.dto.SupplierReportDto
import com.supermarket.inventory.data.remote.dto.SuppliersReportDto
import com.supermarket.inventory.ui.common.categoryColor
import com.supermarket.inventory.ui.common.formatAmount
import com.supermarket.inventory.ui.common.formatPercent
import com.supermarket.inventory.ui.common.formatQuantity
import com.supermarket.inventory.ui.theme.warningColor

@Composable
fun SuppliersTab(
    report: ReportState<SuppliersReportDto>,
    window: Int,
    onWindowChange: (Int) -> Unit,
    onRetry: () -> Unit,
) {
    val data = report.data
    when {
        data == null && report.loading -> ReportLoading()
        data == null && report.error != null -> ReportError(report.error, onRetry)
        data != null -> SuppliersContent(data, window, onWindowChange)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuppliersContent(r: SuppliersReportDto, window: Int, onWindowChange: (Int) -> Unit) {
    val unassignedColor = MaterialTheme.colorScheme.outline
    var expanded by remember(r) { mutableStateOf(emptySet<String>()) }
    val labels = r.months.map { shortMonth(it) }
    val colors = r.suppliers.indices.map { categoryColor(it) }
    val selTotal = r.monthTotals.last().suppliers.money()
    val prevTotal = r.monthTotals.getOrNull(r.monthTotals.size - 2)?.suppliers?.money()
    val owedTotal = r.suppliers.sumOf { it.owed.money() }
    val inProgress = r.months.last() == r.currentMonth

    LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp)) {
        item {
            ChoiceChips(
                options = listOf(6 to stringResource(R.string.reports_months_n, 6), 12 to stringResource(R.string.reports_months_n, 12)),
                selected = window,
                onSelect = onWindowChange,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (r.suppliers.isEmpty() && r.monthTotals.all { it.unassigned.money() == 0.0 }) {
            item { ReportEmpty(stringResource(R.string.suppliers_none)) }
            return@LazyColumn
        }

        // ---- headline ----
        item {
            ReportCard(
                title = stringResource(R.string.suppliers_buying),
                subtitle = stringResource(R.string.suppliers_window_total, formatAmount(r.total)),
                icon = Icons.Filled.LocalShipping,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KpiTile(
                        label = stringResource(if (inProgress) R.string.suppliers_this_month_so_far else R.string.suppliers_this_month),
                        value = formatAmount(r.monthTotals.last().suppliers),
                        change = if (inProgress) null else prevTotal?.let { percentChange(selTotal, it) },
                        footnote = prevTotal?.let { stringResource(R.string.suppliers_last_month, formatAmount(it.toString())) },
                        modifier = Modifier.weight(1f),
                    )
                    KpiTile(
                        label = stringResource(R.string.suppliers_owed),
                        value = formatAmount(owedTotal.toString()),
                        accent = if (owedTotal > 0) warningColor() else MaterialTheme.colorScheme.onSurface,
                        footnote = stringResource(R.string.suppliers_owed_note),
                        modifier = Modifier.weight(1f),
                    )
                }
                r.trendPercent?.let { t ->
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.suppliers_overall_trend), style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.width(6.dp))
                        ChangeChip(t, neutral = true, suffix = stringResource(R.string.reports_per_month_suffix))
                    }
                }
            }
        }

        // ---- month by month, by supplier ----
        item {
            ReportCard(title = stringResource(R.string.suppliers_month_by_month), icon = Icons.Filled.ShowChart, accent = MaterialTheme.colorScheme.secondary) {
                StackedMonthBars(
                    labels = labels,
                    segments = r.months.indices.map { i ->
                        r.suppliers.mapIndexed { s, sup -> sup.series[i].money() to colors[s] } +
                            listOf(r.monthTotals[i].unassigned.money() to unassignedColor)
                    },
                    highlight = r.months.lastIndex,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    r.suppliers.forEachIndexed { s, sup -> LegendDot(colors[s], sup.name) }
                    if (r.monthTotals.any { it.unassigned.money() > 0 }) LegendDot(unassignedColor, stringResource(R.string.suppliers_unassigned))
                }
                if (r.monthTotals.any { it.unassigned.money() > 0 }) {
                    Text(
                        stringResource(R.string.suppliers_unassigned_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        // ---- how much depends on one supplier ----
        if (r.suppliers.size > 1) {
            item {
                ReportCard(title = stringResource(R.string.suppliers_dependence), accent = MaterialTheme.colorScheme.tertiary) {
                    Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp))) {
                        r.suppliers.forEachIndexed { s, sup ->
                            if (sup.sharePercent > 0) {
                                Box(Modifier.weight(sup.sharePercent.toFloat()).fillMaxHeight().background(colors[s]))
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    val top = r.suppliers.first()
                    Text(
                        stringResource(R.string.suppliers_top_share, top.name, formatPercent(top.sharePercent.toString())),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (top.sharePercent >= 60) warningColor() else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        // ---- each supplier ----
        items(r.suppliers, key = { it.supplierId }) { sup ->
            val i = r.suppliers.indexOf(sup)
            SupplierCard(
                sup = sup,
                color = colors[i],
                expanded = sup.supplierId in expanded,
                onToggle = {
                    expanded = if (sup.supplierId in expanded) expanded - sup.supplierId else expanded + sup.supplierId
                },
            )
        }
    }
}

@Composable
private fun SupplierCard(sup: SupplierReportDto, color: Color, expanded: Boolean, onToggle: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(top = 10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.clickable(onClick = onToggle).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(8.dp))
                Text(sup.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatAmount(sup.total), style = MaterialTheme.typography.titleSmall)
                Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.suppliers_card_detail, formatPercent(sup.sharePercent.toString()), sup.invoices, formatAmount(sup.averagePerActiveMonth)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        if (sup.trendPercent != null) {
                            ChangeChip(sup.trendPercent, neutral = true, suffix = stringResource(R.string.reports_per_month_suffix))
                        } else {
                            Text(stringResource(R.string.suppliers_trend_needs_history), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (sup.owed.money() > 0) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.suppliers_owed_amount, formatAmount(sup.owed)),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = warningColor(),
                            )
                        }
                    }
                }
                Sparkline(sup.series.map { it.money() }, color, Modifier.width(110.dp))
            }
            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(top = 10.dp)) {
                    if (sup.products.isEmpty()) {
                        Text(stringResource(R.string.suppliers_no_lines), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text(stringResource(R.string.suppliers_products_bought), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        val maxValue = sup.products.maxOf { it.value.money() }.takeIf { it > 0 } ?: 1.0
                        sup.products.forEach { p ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Row {
                                    Text(p.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${formatQuantity(p.quantity)} ${p.unit} · ${formatAmount(p.value)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                MeterBar((p.value.money() / maxValue).toFloat(), color, Modifier.fillMaxWidth().padding(top = 2.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
