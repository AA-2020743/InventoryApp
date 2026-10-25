package com.supermarket.inventory.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.supermarket.inventory.R
import com.supermarket.inventory.data.remote.dto.MarginItemDto
import com.supermarket.inventory.data.remote.dto.TopProductItemDto
import com.supermarket.inventory.ui.common.categoryColor
import com.supermarket.inventory.ui.common.formatAmount
import com.supermarket.inventory.ui.common.formatAmountChange
import com.supermarket.inventory.ui.common.formatMonth
import com.supermarket.inventory.ui.common.formatPercent
import com.supermarket.inventory.ui.common.formatQuantity
import com.supermarket.inventory.ui.theme.lossColor
import com.supermarket.inventory.ui.theme.profitColor
import com.supermarket.inventory.ui.theme.warningColor
import java.math.BigDecimal
import java.time.YearMonth
import java.util.Locale

// What the best-seller ranking is ranked by. Each answers a different
// question: what brings in the money, what earns it, and what moves.
private enum class ProductMetric { REVENUE, PROFIT, QUANTITY }

private enum class MarginOrder { LOWEST, HIGHEST }

// Thin margins are the ones worth spotting: under a tenth is a warning,
// under a quarter worth a look, above that healthy. The same judgement the
// margins list has always coloured by.
private const val THIN_MARGIN = 10.0
private const val FAIR_MARGIN = 25.0

@Composable
private fun marginColor(percent: Double): Color = when {
    percent < THIN_MARGIN -> lossColor()
    percent < FAIR_MARGIN -> warningColor()
    else -> profitColor()
}

private fun TopProductItemDto.metric(m: ProductMetric): Double = when (m) {
    ProductMetric.REVENUE -> revenue.money()
    ProductMetric.PROFIT -> profit.money()
    ProductMetric.QUANTITY -> quantitySold
}

// The month's products and the catalogue's margins - what used to be the
// Top products and Margins tabs - as one report in the same shape as the
// others: what sold, what it's made of by category, and what each line
// earns on a sale.
@Composable
fun ProductsTab(
    month: YearMonth,
    topProducts: List<TopProductItemDto>,
    margins: List<MarginItemDto>,
    uncategorizedLabel: String,
) {
    var metric by rememberSaveable { mutableStateOf(ProductMetric.REVENUE) }
    var marginOrder by rememberSaveable { mutableStateOf(MarginOrder.LOWEST) }
    val ranked = remember(topProducts, metric) { topProducts.sortedByDescending { it.metric(metric) } }
    val top = ranked.firstOrNull()?.metric(metric)?.takeIf { it > 0 } ?: 1.0
    val totalRevenue = remember(topProducts) { topProducts.fold(BigDecimal.ZERO) { a, p -> a.add(p.revenue.toBigDecimalOrNull() ?: BigDecimal.ZERO) } }
    val totalProfit = remember(topProducts) { topProducts.fold(BigDecimal.ZERO) { a, p -> a.add(p.profit.toBigDecimalOrNull() ?: BigDecimal.ZERO) } }

    LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp)) {
        // ---- best sellers ----
        item {
            ReportCard(
                title = stringResource(R.string.products_best_sellers),
                subtitle = stringResource(R.string.products_best_sellers_subtitle, formatMonth(month, Locale.getDefault()), topProducts.size),
                icon = Icons.Filled.EmojiEvents,
                accent = warningColor(),
            ) {
                if (topProducts.isEmpty()) {
                    Text(stringResource(R.string.products_none_sold), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    return@ReportCard
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KpiTile(stringResource(R.string.pulse_revenue), formatAmount(totalRevenue.toPlainString()), Modifier.weight(1f))
                    KpiTile(
                        stringResource(R.string.pulse_profit),
                        formatAmount(totalProfit.toPlainString()),
                        Modifier.weight(1f),
                        accent = if (totalProfit.signum() >= 0) profitColor() else lossColor(),
                        footnote = if (totalRevenue.signum() > 0) {
                            stringResource(R.string.products_overall_margin, formatPercent((totalProfit.toDouble() / totalRevenue.toDouble() * 100).toString()))
                        } else null,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    ChoiceChips(
                        options = listOf(
                            ProductMetric.REVENUE to stringResource(R.string.products_by_revenue),
                            ProductMetric.PROFIT to stringResource(R.string.products_by_profit),
                            ProductMetric.QUANTITY to stringResource(R.string.products_by_units),
                        ),
                        selected = metric,
                        onSelect = { metric = it },
                    )
                }
                Spacer(Modifier.height(10.dp))
                Podium(ranked.take(3), metric)
            }
        }
        val rest = ranked.drop(3)
        itemsIndexed(rest, key = { _, p -> p.productId }) { i, p ->
            RankedProductRow(rank = i + 4, p = p, metric = metric, fraction = (p.metric(metric) / top).toFloat(), shape = groupedRowShape(i, rest.size), first = i == 0)
        }

        // ---- by category ----
        if (topProducts.isNotEmpty()) {
            item { CategoryCard(topProducts, totalRevenue, uncategorizedLabel) }
        }

        // ---- margins ----
        if (margins.isNotEmpty()) {
            item { MarginSummaryCard(margins, marginOrder, onOrder = { marginOrder = it }) }
            val ordered = margins.sortedBy { it.marginPercent.money() }.let { if (marginOrder == MarginOrder.HIGHEST) it.reversed() else it }
            itemsIndexed(ordered, key = { _, m -> "m-" + m.productId }) { i, m -> MarginRow(m, shape = groupedRowShape(i, ordered.size), first = i == 0) }
        }
    }
}

private fun metricValue(p: TopProductItemDto, m: ProductMetric): String = when (m) {
    ProductMetric.REVENUE -> formatAmount(p.revenue)
    ProductMetric.PROFIT -> formatAmount(p.profit)
    ProductMetric.QUANTITY -> formatQuantity(p.quantitySold.toString())
}

// The first three, side by side: first in the middle and raised, second and
// third either side - the ranking reads at a glance before any list does.
@Composable
private fun Podium(top: List<TopProductItemDto>, metric: ProductMetric) {
    if (top.isEmpty()) return
    val gold = Color(0xFFE0A526)
    val silver = Color(0xFF9EA7AD)
    val bronze = Color(0xFFB07A4F)
    // Order on screen: second, first, third.
    val slots = listOfNotNull(top.getOrNull(1)?.let { Triple(2, it, silver) }, Triple(1, top[0], gold), top.getOrNull(2)?.let { Triple(3, it, bronze) })
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        slots.forEach { (rank, p, medal) ->
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(horizontal = 8.dp, vertical = if (rank == 1) 14.dp else 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(if (rank == 1) 34.dp else 28.dp).clip(CircleShape).background(medal.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                    Text("$rank", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = medal)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    p.name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.heightIn(min = 32.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    metricValue(p, metric),
                    style = if (rank == 1) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (metric == ProductMetric.PROFIT) profitColor() else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                if (metric == ProductMetric.QUANTITY) {
                    Text(stringResource(R.string.products_units_sold), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// Fourth place down: the bar is sized against first place on the measure
// the list is ranked by, so the bar and the order always agree.
@Composable
private fun RankedProductRow(rank: Int, p: TopProductItemDto, metric: ProductMetric, fraction: Float, shape: Shape, first: Boolean) {
    val revenue = p.revenue.money()
    val margin = if (revenue > 0) p.profit.money() / revenue * 100 else null
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = if (first) 10.dp else 2.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$rank", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(26.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        p.category?.takeIf { it.isNotBlank() },
                        stringResource(R.string.stats_quantity_sold, formatQuantity(p.quantitySold.toString())),
                        margin?.let { stringResource(R.string.stats_margin_percent, formatPercent(it.toString())) },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                metricValue(p, metric),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (metric == ProductMetric.PROFIT) profitColor() else MaterialTheme.colorScheme.onSurface,
            )
        }
        MeterBar(fraction, MaterialTheme.colorScheme.primary.copy(alpha = 0.75f), Modifier.fillMaxWidth().padding(top = 6.dp, start = 26.dp))
    }
}

// Revenue by category as one bar split by share, with a line per category:
// easier to compare than slices of a pie once there are more than a few.
@Composable
private fun CategoryCard(products: List<TopProductItemDto>, total: BigDecimal, uncategorizedLabel: String) {
    val groups = remember(products) {
        products.groupBy { it.category?.takeIf { c -> c.isNotBlank() } ?: uncategorizedLabel }
            .map { (name, items) -> Triple(name, items.fold(BigDecimal.ZERO) { a, p -> a.add(p.revenue.toBigDecimalOrNull() ?: BigDecimal.ZERO) }, items.size) }
            .sortedByDescending { it.second }
    }
    val totalValue = total.toDouble().takeIf { it > 0 } ?: 1.0
    ReportCard(title = stringResource(R.string.stats_by_category), icon = Icons.Filled.Category, accent = MaterialTheme.colorScheme.tertiary) {
        Row(Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(8.dp))) {
            groups.forEachIndexed { i, (_, value, _) ->
                val v = value.toDouble()
                if (v > 0) Box(Modifier.weight(v.toFloat()).fillMaxHeight().background(categoryColor(i)))
            }
        }
        Spacer(Modifier.height(8.dp))
        groups.forEachIndexed { i, (name, value, count) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(categoryColor(i)))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(R.string.products_count, count), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatAmount(value.toPlainString()), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${formatPercent((value.toDouble() / totalValue * 100).toString())}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// The catalogue's margins at a glance - highest, average and lowest, and how
// many lines are thin, fair or healthy - above the line-by-line list.
@Composable
private fun MarginSummaryCard(margins: List<MarginItemDto>, order: MarginOrder, onOrder: (MarginOrder) -> Unit) {
    val percents = margins.map { it.marginPercent.money() }
    val highest = margins.maxByOrNull { it.marginPercent.money() }
    val lowest = margins.minByOrNull { it.marginPercent.money() }
    val thin = percents.count { it < THIN_MARGIN }
    val fair = percents.count { it >= THIN_MARGIN && it < FAIR_MARGIN }
    val healthy = percents.size - thin - fair
    ReportCard(
        title = stringResource(R.string.products_margins),
        subtitle = stringResource(R.string.products_margins_subtitle, margins.size),
        icon = Icons.Filled.Percent,
        accent = profitColor(),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KpiTile(
                stringResource(R.string.stats_margin_highest),
                "${formatPercent((highest?.marginPercent ?: "0").money().toString())}%",
                Modifier.weight(1f),
                accent = profitColor(),
                footnote = highest?.name,
            )
            KpiTile(stringResource(R.string.stats_average_margin), "${formatPercent(percents.average().toString())}%", Modifier.weight(1f))
            KpiTile(
                stringResource(R.string.stats_margin_lowest),
                "${formatPercent((lowest?.marginPercent ?: "0").money().toString())}%",
                Modifier.weight(1f),
                accent = marginColor((lowest?.marginPercent ?: "0").money()),
                footnote = lowest?.name,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp))) {
            listOf(thin to lossColor(), fair to warningColor(), healthy to profitColor()).forEach { (n, c) ->
                if (n > 0) Box(Modifier.weight(n.toFloat()).fillMaxHeight().background(c))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            LegendDot(lossColor(), stringResource(R.string.products_margin_thin, thin))
            LegendDot(warningColor(), stringResource(R.string.products_margin_fair, fair))
            LegendDot(profitColor(), stringResource(R.string.products_margin_healthy, healthy))
        }
        Spacer(Modifier.height(12.dp))
        ChoiceChips(
            options = listOf(
                MarginOrder.LOWEST to stringResource(R.string.products_margin_lowest_first),
                MarginOrder.HIGHEST to stringResource(R.string.products_margin_highest_first),
            ),
            selected = order,
            onSelect = onOrder,
        )
    }
}

@Composable
private fun MarginRow(m: MarginItemDto, shape: Shape, first: Boolean) {
    val percent = m.marginPercent.money()
    val color = marginColor(percent)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = if (first) 10.dp else 2.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(m.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                // What it costs and what it sells for, and what that leaves
                // per unit - the percentage alone never said whether a thin
                // margin is thin money.
                Text(
                    stringResource(R.string.products_margin_line, formatAmountChange(m.purchaseCost, m.sellingPrice), formatAmount(m.marginAmount)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "${formatPercent(percent.toString())}%",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = color,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(color.copy(alpha = 0.14f))
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
        MeterBar((percent / 100.0).toFloat(), color.copy(alpha = 0.8f), Modifier.fillMaxWidth().padding(top = 6.dp))
    }
}
