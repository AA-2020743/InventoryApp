package com.supermarket.inventory.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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
