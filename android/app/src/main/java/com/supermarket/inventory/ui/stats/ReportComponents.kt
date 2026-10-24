package com.supermarket.inventory.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.supermarket.inventory.R
import com.supermarket.inventory.ui.common.formatPercent
import com.supermarket.inventory.ui.theme.lossColor
import com.supermarket.inventory.ui.theme.profitColor
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

fun String?.money(): Double = this?.toDoubleOrNull() ?: 0.0

// Percent change from `previous` to `current`; null when there's nothing to
// compare against, rather than an infinite or meaningless figure.
fun percentChange(current: Double, previous: Double): Double? =
    if (previous == 0.0) null else (current - previous) / previous * 100

fun shortMonth(key: String, locale: Locale = Locale.getDefault()): String =
    runCatching { YearMonth.parse(key).month.getDisplayName(TextStyle.SHORT, locale) }.getOrDefault(key)

// A report section: a coloured tick, a title, and what's inside.
@Composable
fun ReportCard(
    title: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    icon: ImageVector? = null,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier.fillMaxWidth().padding(top = 10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(17.dp)) }
                } else {
                    Box(Modifier.width(3.dp).height(16.dp).clip(RoundedCornerShape(2.dp)).background(accent))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (subtitle != null) {
                        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

// One headline figure with what it is and, optionally, what it's measured
// against.
@Composable
fun KpiTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.onSurface,
    footnote: String? = null,
    change: Double? = null,
    higherIsGood: Boolean = true,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (change != null) {
            Spacer(Modifier.height(4.dp))
            ChangeChip(change, higherIsGood)
        }
        if (footnote != null) {
            Spacer(Modifier.height(2.dp))
            Text(footnote, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ▲ 12% / ▼ 4%, green when the move is good and red when it isn't - a rise
// in revenue is good, a rise in costs isn't.
//
// `neutral` for moves that are neither good nor bad in themselves - buying
// more from a supplier, say - shown in one colour either way. `suffix` goes
// after the figure ("/month" for a trend).
@Composable
fun ChangeChip(percent: Double, higherIsGood: Boolean = true, neutral: Boolean = false, suffix: String = "") {
    val up = percent > 0.05
    val down = percent < -0.05
    val good = if (higherIsGood) up else down
    val color = when {
        !up && !down -> MaterialTheme.colorScheme.onSurfaceVariant
        neutral -> MaterialTheme.colorScheme.secondary
        good -> profitColor()
        else -> lossColor()
    }
    val arrow = when {
        up -> "▲"
        down -> "▼"
        else -> "●"
    }
    Text(
        "$arrow ${formatPercent(kotlin.math.abs(percent).toString())}%$suffix",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

// A row of small bars, one per month, oldest at the start. The last bar can
// be set apart (the month in view). Drawn in reading direction, so in Arabic
// the newest month is on the left.
@Composable
fun Sparkline(
    values: List<Double>,
    color: Color,
    modifier: Modifier = Modifier,
    highlightLast: Boolean = true,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val muted = color.copy(alpha = 0.35f)
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier.height(28.dp)) {
        if (values.isEmpty()) return@Canvas
        val max = values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
        val gap = 3.dp.toPx()
        val barW = ((size.width - gap * (values.size - 1)) / values.size).coerceAtLeast(1f)
        values.forEachIndexed { i, v ->
            val slot = if (rtl) values.size - 1 - i else i
            val x = slot * (barW + gap)
            val h = (v / max).toFloat() * size.height
            drawRoundRect(track, Offset(x, size.height - 2.dp.toPx()), Size(barW, 2.dp.toPx()), CornerRadius(1.dp.toPx()))
            if (h > 0f) {
                val c = if (!highlightLast || i == values.lastIndex) color else muted
                drawRoundRect(c, Offset(x, size.height - h), Size(barW, h), CornerRadius(2.dp.toPx()))
            }
        }
    }
}

// Monthly columns built from coloured segments (fixed / variable / spoilage,
// say), with an optional dashed reference line - a typical month, a
// break-even point. Labels run under the columns. Reading direction as above.
@Composable
fun StackedMonthBars(
    labels: List<String>,
    segments: List<List<Pair<Double, Color>>>,
    modifier: Modifier = Modifier,
    reference: Double? = null,
    referenceColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    highlight: Int? = null,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(130.dp)) {
            val totals = segments.map { col -> col.sumOf { it.first } }
            val max = maxOf(totals.maxOrNull() ?: 0.0, reference ?: 0.0).takeIf { it > 0 } ?: 1.0
            val n = segments.size.coerceAtLeast(1)
            val slotW = size.width / n
            val barW = slotW * 0.62f
            segments.forEachIndexed { i, col ->
                val slot = if (rtl) n - 1 - i else i
                val left = slot * slotW + (slotW - barW) / 2
                if (i == highlight) {
                    drawRoundRect(highlightColor, Offset(slot * slotW + 1, 0f), Size(slotW - 2, size.height), CornerRadius(6.dp.toPx()))
                }
                var top = size.height
                col.forEach { (value, c) ->
                    val h = (value / max).toFloat() * size.height
                    if (h > 0f) {
                        drawRect(c, Offset(left, top - h), Size(barW, h))
                        top -= h
                    }
                }
            }
            if (reference != null && reference > 0) {
                val y = size.height - (reference / max).toFloat() * size.height
                drawLine(
                    referenceColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx())),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            labels.forEachIndexed { i, label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (i == highlight) FontWeight.Bold else FontWeight.Normal,
                    color = if (i == highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun LegendDot(color: Color, label: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// A row of choices for a report's window (6 / 12 months, 30 / 90 days...).
@Composable
fun <T> ChoiceChips(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) })
        }
    }
}

// A thin horizontal bar: `fraction` of the track filled.
@Composable
fun MeterBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color),
        )
    }
}

@Composable
fun ReportLoading() {
    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun ReportError(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onRetry) { Text(stringResource(R.string.reports_retry)) }
    }
}

@Composable
fun ReportEmpty(message: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
