package com.supermarket.inventory.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.supermarket.inventory.R
import com.supermarket.inventory.data.remote.dto.StatsPeriodDto
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// Previous / next around the date, the date itself as the way into the full
// picker, and - once you've moved away from today - a way straight back.
//
// The arrows use auto-mirrored icons in a Row, so in Arabic "previous" sits
// on the right pointing right, which is backwards in that reading direction.
// "Next" stops at today: nothing after it has anything in it.
@Composable
fun DateNavigator(
    period: StatsPeriod,
    date: LocalDate,
    canGoForward: Boolean,
    isCurrent: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPick: () -> Unit,
    onJumpToNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = Locale.getDefault()
    val today = businessToday()
    val label = if (period == StatsPeriod.DAY) {
        date.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", locale))
    } else {
        date.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
    }
    // Relative names where they're more useful than a date.
    val relative = when {
        period == StatsPeriod.DAY && date == today -> stringResource(R.string.stats_nav_today)
        period == StatsPeriod.DAY && date == today.minusDays(1) -> stringResource(R.string.stats_nav_yesterday)
        period == StatsPeriod.MONTH && YearMonth.from(date) == YearMonth.from(today) -> stringResource(R.string.stats_nav_this_month)
        period == StatsPeriod.MONTH && YearMonth.from(date) == YearMonth.from(today).minusMonths(1) -> stringResource(R.string.stats_nav_last_month)
        else -> null
    }
    val unit = if (period == StatsPeriod.DAY) R.string.stats_nav_day else R.string.stats_nav_month

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = onPrevious) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.stats_nav_previous, stringResource(unit)),
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onPick)
                    .padding(vertical = 6.dp, horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
                if (relative != null) {
                    Text(relative, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            FilledTonalIconButton(onClick = onNext, enabled = canGoForward) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.stats_nav_next, stringResource(unit)),
                )
            }
        }
        if (!isCurrent) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                AssistChip(
                    onClick = onJumpToNow,
                    label = {
                        Text(stringResource(if (period == StatsPeriod.DAY) R.string.stats_nav_back_to_today else R.string.stats_nav_back_to_this_month))
                    },
                    leadingIcon = { Icon(Icons.Filled.Today, contentDescription = null, modifier = Modifier.size(16.dp)) },
                )
            }
        }
    }
}

// The selected day (or month) among its neighbours, each a tappable column
// with a bar for its revenue - quiet and busy days stand out at a glance,
// and any of them is one tap away. Today is marked with a dot.
@Composable
fun PeriodStrip(
    period: StatsPeriod,
    periods: List<StatsPeriodDto>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (periods.isEmpty()) return
    val locale = Locale.getDefault()
    val today = businessToday()
    val todayKey = if (period == StatsPeriod.DAY) today.toString() else YearMonth.from(today).toString()
    val max = periods.maxOf { it.revenue.money() }.takeIf { it > 0 } ?: 1.0
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        periods.forEach { p ->
            val selected = p.key == selectedKey
            val (top, bottom) = if (period == StatsPeriod.DAY) {
                val d = LocalDate.parse(p.key)
                d.dayOfWeek.getDisplayName(TextStyle.SHORT, locale) to d.dayOfMonth.toString()
            } else {
                val m = YearMonth.parse(p.key)
                m.month.getDisplayName(TextStyle.SHORT, locale) to (m.year % 100).toString().padStart(2, '0')
            }
            val fraction = (p.revenue.money() / max).toFloat()
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                    .clickable { onSelect(p.key) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(top, style = MaterialTheme.typography.labelSmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(Modifier.height(30.dp).width(10.dp), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier
                            .width(10.dp)
                            .height((30 * fraction).coerceAtLeast(if (p.revenue.money() > 0) 3f else 0f).dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                    )
                }
                Text(
                    bottom,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                )
                Box(
                    Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(if (p.key == todayKey) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent),
                )
            }
        }
    }
}

// Swiping across the content turns the page: toward the reading direction's
// start for the previous day, toward its end for the next. A deliberate
// swipe only - a short drag while scrolling doesn't count.
// `rtl` comes from the caller's LocalLayoutDirection: the gesture scope
// doesn't know the reading direction itself.
fun Modifier.swipeToStep(rtl: Boolean, onPrevious: () -> Unit, onNext: () -> Unit): Modifier = this.then(
    Modifier.pointerInput(rtl, onPrevious, onNext) {
        val threshold = 72.dp.toPx()
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = {
                // Finger moving right in LTR (left in RTL) pulls the previous
                // page in, as with paper.
                val towardStart = if (rtl) total < -threshold else total > threshold
                val towardEnd = if (rtl) total > threshold else total < -threshold
                if (towardStart) onPrevious() else if (towardEnd) onNext()
            },
            onHorizontalDrag = { _, amount -> total += amount },
        )
    }
)

