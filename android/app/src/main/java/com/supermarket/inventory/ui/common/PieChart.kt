package com.supermarket.inventory.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp

// Shared so a category keeps the same colour whether it is drawn as a pie
// slice or as a rank dot beside a bar - the same category reading as two
// different colours on one screen is worse than no colour at all.
fun categoryColor(index: Int): Color = PieSliceColors[index % PieSliceColors.size]

private val PieSliceColors = listOf(
    Color(0xFF3B82F6), Color(0xFFEF4444), Color(0xFF10B981), Color(0xFFF59E0B),
    Color(0xFF8B5CF6), Color(0xFFEC4899), Color(0xFF14B8A6), Color(0xFF6366F1),
)

/**
 * Groups the smallest slices beyond [maxSlices] into a single [otherLabel]
 * bucket so the legend stays readable no matter how many distinct
 * categories/items a period contains.
 */
fun topSlicesWithOther(data: List<Pair<String, Double>>, maxSlices: Int, otherLabel: String): List<Pair<String, Double>> {
    val sorted = data.sortedByDescending { it.second }
    if (sorted.size <= maxSlices) return sorted
    val head = sorted.take(maxSlices - 1)
    val otherTotal = sorted.drop(maxSlices - 1).sumOf { it.second }
    return head + (otherLabel to otherTotal)
}

/**
 * A simple pie chart drawn directly with Canvas (no charting library
 * dependency) with a color-keyed legend showing each slice's share.
 */
@Composable
fun PieChart(data: List<Pair<String, Double>>, modifier: Modifier = Modifier) {
    val total = data.sumOf { it.second }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(120.dp)) {
            if (total <= 0.0) return@Canvas
            var startAngle = -90f
            data.forEachIndexed { index, (_, value) ->
                val sweep = (value / total * 360.0).toFloat()
                drawArc(
                    color = PieSliceColors[index % PieSliceColors.size],
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = true,
                )
                startAngle += sweep
            }
        }
        Spacer(Modifier.width(16.dp))
        Column {
            data.forEachIndexed { index, (label, value) ->
                val percent = if (total > 0) value / total * 100 else 0.0
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(PieSliceColors[index % PieSliceColors.size], CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "$label (${"%.1f".format(percent)}%)",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

/**
 * The same data as [PieChart], drawn as a ring so the middle can carry the
 * figure the slices add up to. A total sitting inside its own breakdown
 * says "this is what it is, and this is what it is made of" in one shape,
 * where a pie plus a separate headline says it twice.
 *
 * [center] is drawn in the hole - keep it to a line or two.
 */
@Composable
fun DonutChart(
    data: List<Pair<String, Double>>,
    modifier: Modifier = Modifier,
    diameter: Dp = 176.dp,
    thickness: Dp = 24.dp,
    center: @Composable () -> Unit = {},
) {
    val total = data.sumOf { it.second }
    val emptyRingColor = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val strokePx = thickness.toPx()
            val inset = strokePx / 2
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            val topLeft = Offset(inset, inset)
            // A ring even with nothing in it, so the shape doesn't vanish
            // on a shop that has just opened.
            drawArc(
                color = emptyRingColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx),
            )
            if (total <= 0.0) return@Canvas
            var startAngle = -90f
            data.forEachIndexed { index, (_, value) ->
                if (value <= 0.0) return@forEachIndexed
                val sweep = (value / total * 360.0).toFloat()
                drawArc(
                    color = categoryColor(index),
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx),
                )
                startAngle += sweep
            }
        }
        center()
    }
}

/**
 * The key to a [DonutChart] or [PieChart], one row per slice: its colour,
 * its name, its share and what it is worth. Laid out as rows rather than
 * beside the chart so the names have room to be read.
 */
@Composable
fun CategoryLegend(data: List<Pair<String, Double>>, modifier: Modifier = Modifier) {
    val total = data.sumOf { it.second }
    Column(modifier) {
        data.forEachIndexed { index, (label, value) ->
            val percent = if (total > 0) value / total * 100 else 0.0
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(categoryColor(index)))
                Spacer(Modifier.width(8.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${formatPercent(percent.toString())}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                Text(formatAmount(value.toString()), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
