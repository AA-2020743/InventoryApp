package com.supermarket.inventory.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Shelving a running list by month.
//
// Every screen that records something as it happens - expenses, other
// sales, the cash ledger - grows one entry at a time and never shrinks, so
// after a season the thing you actually want (what has been spent this
// month) is buried under everything ever spent. These split the list in
// two: the month in progress, laid out as before, and everything older
// folded into one collapsed card per month showing its count and its total.
//
// The fold is not only tidying. A month you can't see the rows of still
// tells you what it came to, which the flat list never did.

// One month's worth of rows, with what they add up to.
data class MonthBucket<T>(
    val yearMonth: YearMonth,
    val items: List<T>,
    val total: Double,
)

// Buckets rows by the month of their own date - the day the money moved,
// not the day the row was typed - newest month first.
fun <T> groupByMonth(
    items: List<T>,
    dateOf: (T) -> String,
    amountOf: (T) -> Double,
): List<MonthBucket<T>> {
    val zone = ZoneId.systemDefault()
    // A row whose date can't be read still has to land somewhere it will be
    // seen rather than vanish into a month that doesn't exist.
    val fallback = YearMonth.now(zone)
    return items
        .groupBy { item ->
            runCatching { YearMonth.from(Instant.parse(dateOf(item)).atZone(zone)) }.getOrDefault(fallback)
        }
        .map { (month, rows) -> MonthBucket(month, rows, rows.sumOf { amountOf(it) }) }
        .sortedByDescending { it.yearMonth }
}

private val monthFormatter = DateTimeFormatter.ofPattern("LLLL yyyy")

// "September 2026" / "سبتمبر 2026" - the standalone month name, so Arabic
// reads as a heading rather than as part of a date.
fun formatMonth(month: YearMonth, locale: Locale = Locale.getDefault()): String =
    month.atDay(1).format(monthFormatter.withLocale(locale))

// The switch between the month in progress and everything before it.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodTabs(
    currentText: String,
    historyText: String,
    showHistory: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        SegmentedButton(
            selected = !showHistory,
            onClick = { onChange(false) },
            shape = SegmentedButtonDefaults.itemShape(0, 2),
        ) { Text(currentText, maxLines = 1) }
        SegmentedButton(
            selected = showHistory,
            onClick = { onChange(true) },
            shape = SegmentedButtonDefaults.itemShape(1, 2),
        ) { Text(historyText, maxLines = 1) }
    }
}

// The one figure that says where the open side stands: this month's total,
// or everything the history holds.
@Composable
fun PeriodSummaryCard(
    label: String,
    detail: String,
    total: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(total, style = MaterialTheme.typography.titleLarge, color = accent, maxLines = 1)
        }
    }
}

// One folded month. Tapping it opens the rows underneath; closed, it still
// carries the month's count and total.
@Composable
fun MonthGroupHeader(
    label: String,
    detail: String,
    total: String,
    expanded: Boolean,
    accent: Color,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(
            containerColor = if (expanded) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(total, style = MaterialTheme.typography.titleSmall, color = accent, maxLines = 1)
        }
    }
}
