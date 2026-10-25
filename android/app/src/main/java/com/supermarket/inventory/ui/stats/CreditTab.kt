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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.supermarket.inventory.R
import com.supermarket.inventory.data.remote.dto.CreditCustomerDto
import com.supermarket.inventory.data.remote.dto.CreditReportDto
import com.supermarket.inventory.ui.common.formatAmount
import com.supermarket.inventory.ui.common.formatPercent
import com.supermarket.inventory.ui.common.formatQuantity
import com.supermarket.inventory.ui.theme.lossColor
import com.supermarket.inventory.ui.theme.profitColor
import com.supermarket.inventory.ui.theme.warningColor
import java.time.Duration
import java.time.Instant

private enum class CreditSort { VOLUME, OWED, SLOWEST, COUNT }

@Composable
fun CreditTab(report: ReportState<CreditReportDto>, onRetry: () -> Unit) {
    val data = report.data
    when {
        data == null && report.loading -> ReportLoading()
        data == null && report.error != null -> ReportError(report.error, onRetry)
        data != null -> CreditContent(data)
    }
}

private fun daysSince(iso: String?): Long? =
    iso?.let { runCatching { Duration.between(Instant.parse(it), Instant.now()).toDays() }.getOrNull() }

@Composable
private fun CreditContent(r: CreditReportDto) {
    var sort by remember { mutableStateOf(CreditSort.VOLUME) }
    val t = r.totals
    if (t.salesOnCredit == 0) {
        ReportEmpty(stringResource(R.string.credit_none))
        return
    }
    val sorted = remember(r, sort) {
        when (sort) {
            CreditSort.VOLUME -> r.customers.sortedByDescending { it.total.money() }
            CreditSort.OWED -> r.customers.sortedByDescending { it.outstanding.money() }
            CreditSort.SLOWEST -> r.customers.sortedByDescending { it.avgDaysToSettle ?: -1.0 }
            CreditSort.COUNT -> r.customers.sortedByDescending { it.count }
        }
    }
    val maxTotal = r.customers.maxOfOrNull { it.total.money() }?.takeIf { it > 0 } ?: 1.0
    val agingColors = listOf(profitColor(), MaterialTheme.colorScheme.primary, warningColor(), lossColor())

    LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp)) {
        item {
            ReportCard(title = stringResource(R.string.credit_overview), icon = Icons.Filled.People, accent = warningColor()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KpiTile(
                        stringResource(R.string.credit_owed_now),
                        formatAmount(t.outstanding),
                        Modifier.weight(1f),
                        accent = if (t.outstanding.money() > 0) warningColor() else profitColor(),
                    )
                    KpiTile(
                        stringResource(R.string.credit_sold_on_credit),
                        formatAmount(t.totalDeferred),
                        Modifier.weight(1f),
                        footnote = stringResource(R.string.credit_sales_count, t.salesOnCredit),
                    )
                }
                Spacer(Modifier.height(8.dp))
                val share = if (t.allSalesRevenue.money() > 0) t.totalDeferred.money() / t.allSalesRevenue.money() * 100 else 0.0
                Text(
                    stringResource(R.string.credit_share_of_sales, formatPercent(share.toString()), t.customers),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val collectedShare = if (t.totalDeferred.money() > 0) (t.collected.money() / t.totalDeferred.money()).toFloat() else 0f
                Spacer(Modifier.height(8.dp))
                MeterBar(collectedShare, profitColor(), Modifier.fillMaxWidth())
                Text(
                    stringResource(R.string.credit_collected_share, formatAmount(t.collected), formatPercent((collectedShare * 100).toString())),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        // ---- how old is what's owed ----
        if (t.outstanding.money() > 0) {
            item {
                ReportCard(
                    title = stringResource(R.string.credit_aging),
                    subtitle = stringResource(R.string.credit_aging_subtitle),
                    icon = Icons.Filled.HourglassBottom,
                    accent = lossColor(),
                ) {
                    Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp))) {
                        r.aging.forEachIndexed { i, a ->
                            val v = a.outstanding.money()
                            if (v > 0) Box(Modifier.weight(v.toFloat()).fillMaxHeight().background(agingColors[i]))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    r.aging.forEachIndexed { i, a ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            LegendDot(agingColors[i], stringResource(R.string.credit_aging_bucket, a.bucket), Modifier.weight(1f))
                            // The count and the amount as two pieces: joined in one
                            // string, the Arabic word for "sales" drifted away from
                            // its number to the far side of the amount.
                            Text(
                                stringResource(R.string.credit_aging_count, a.count),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                formatAmount(a.outstanding),
                                textAlign = TextAlign.End,
                                modifier = Modifier.widthIn(min = 104.dp),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (i == r.aging.lastIndex && a.count > 0) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (i == r.aging.lastIndex && a.count > 0) lossColor() else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }

        // ---- customers ----
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp).horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChoiceChips(
                    options = listOf(
                        CreditSort.VOLUME to stringResource(R.string.credit_sort_volume),
                        CreditSort.OWED to stringResource(R.string.credit_sort_owed),
                        CreditSort.SLOWEST to stringResource(R.string.credit_sort_slowest),
                        CreditSort.COUNT to stringResource(R.string.credit_sort_count),
                    ),
                    selected = sort,
                    onSelect = { sort = it },
                )
            }
        }
        itemsIndexed(sorted, key = { _, c -> c.name ?: "\u0000unnamed" }) { i, c ->
            CustomerRow(rank = i + 1, c = c, share = (c.total.money() / maxTotal).toFloat())
        }
    }
}

@Composable
private fun CustomerRow(rank: Int, c: CreditCustomerDto, share: Float) {
    val owes = c.outstanding.money() > 0
    val oldestDays = daysSince(c.oldestOpenAt)
    val accent: Color = when {
        oldestDays != null && oldestDays > 60 -> lossColor()
        owes -> warningColor()
        else -> profitColor()
    }
    Card(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$rank", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(26.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        c.name ?: stringResource(R.string.credit_no_name),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(R.string.credit_customer_volume, formatAmount(c.total), c.count),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Sparkline(c.monthly.map { it.money() }, accent, Modifier.width(84.dp))
            }
            MeterBar(share, accent.copy(alpha = 0.8f), Modifier.fillMaxWidth().padding(top = 8.dp, start = 26.dp))
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, start = 26.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (owes) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.credit_owes, formatAmount(c.outstanding)),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = accent,
                        )
                        if (oldestDays != null) {
                            Text(
                                stringResource(R.string.credit_oldest_unpaid, oldestDays.toInt(), c.openCount),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    Text(stringResource(R.string.credit_all_settled), style = MaterialTheme.typography.bodySmall, color = profitColor(), modifier = Modifier.weight(1f))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(stringResource(R.string.credit_pays_in), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        c.avgDaysToSettle?.let { stringResource(R.string.credit_days, formatQuantity(it.toString())) } ?: "—",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            c.avgDaysToSettle == null -> MaterialTheme.colorScheme.onSurfaceVariant
                            c.avgDaysToSettle > 30 -> lossColor()
                            c.avgDaysToSettle > 14 -> warningColor()
                            else -> profitColor()
                        },
                    )
                }
            }
        }
    }
}
