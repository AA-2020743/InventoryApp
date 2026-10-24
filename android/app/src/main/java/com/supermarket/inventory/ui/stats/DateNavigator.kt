package com.supermarket.inventory.ui.stats

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.supermarket.inventory.R
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

// One line: previous / next around the date, the date itself as the way
// into the full picker, and - once you've moved away from today - a button
// straight back. It used to take three rows and a strip of neighbouring
// days; the figures it navigates are what the screen is for, so they now
// start right under it.
//
// The date slides in from the side you moved toward, as a page would, so
// stepping reads as movement through time rather than a label changing.
// Swiping across the content does the same (see swipeToStep).
//
// The arrows use auto-mirrored icons in a Row, so in Arabic "previous" sits
// on the right pointing right, which is backwards in that reading direction.
// "Next" stops at today: nothing after it has anything in it.
@OptIn(ExperimentalAnimationApi::class)
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
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // Relative names where they're more useful than a date.
    val relative = when {
        period == StatsPeriod.DAY && date == today -> stringResource(R.string.stats_nav_today)
        period == StatsPeriod.DAY && date == today.minusDays(1) -> stringResource(R.string.stats_nav_yesterday)
        period == StatsPeriod.MONTH && YearMonth.from(date) == YearMonth.from(today) -> stringResource(R.string.stats_nav_this_month)
        period == StatsPeriod.MONTH && YearMonth.from(date) == YearMonth.from(today).minusMonths(1) -> stringResource(R.string.stats_nav_last_month)
        else -> null
    }
    val pattern = when {
        period == StatsPeriod.MONTH -> "MMMM yyyy"
        date.year == today.year -> "EEE d MMM"
        else -> "EEE d MMM yyyy"
    }
    val label = date.format(DateTimeFormatter.ofPattern(pattern, locale)) + (relative?.let { " · $it" } ?: "")
    val unit = if (period == StatsPeriod.DAY) R.string.stats_nav_day else R.string.stats_nav_month

    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.stats_nav_previous, stringResource(unit)),
            )
        }
        Row(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = onPick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            AnimatedContent(
                targetState = date to label,
                transitionSpec = {
                    // Later dates come in from the end of the line (the
                    // right in English, the left in Arabic), earlier ones
                    // from the start.
                    val forward = targetState.first > initialState.first
                    val towardEnd = if (rtl) -1 else 1
                    val from = if (forward) towardEnd else -towardEnd
                    (slideInHorizontally { width -> from * width / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { width -> -from * width / 3 } + fadeOut())
                },
                label = "date",
            ) { (_, text) ->
                Text(
                    text,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onNext, enabled = canGoForward) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.stats_nav_next, stringResource(unit)),
            )
        }
        if (!isCurrent) {
            IconButton(onClick = onJumpToNow) {
                Icon(
                    Icons.Filled.Today,
                    contentDescription = stringResource(
                        if (period == StatsPeriod.DAY) R.string.stats_nav_back_to_today else R.string.stats_nav_back_to_this_month
                    ),
                    tint = MaterialTheme.colorScheme.primary,
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

