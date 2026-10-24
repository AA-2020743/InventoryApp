package com.supermarket.inventory.ui.stats

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.supermarket.inventory.data.ApiResult
import com.supermarket.inventory.data.remote.dto.ExpensesForRangeResponse
import com.supermarket.inventory.data.remote.dto.MarginItemDto
import com.supermarket.inventory.data.remote.dto.OtherSalesForRangeResponse
import com.supermarket.inventory.data.remote.dto.SaleDto
import com.supermarket.inventory.data.remote.dto.StatsPeriodDto
import com.supermarket.inventory.data.remote.dto.TopProductItemDto
import com.supermarket.inventory.data.repository.ExpenseRepository
import com.supermarket.inventory.data.repository.OtherSaleRepository
import com.supermarket.inventory.data.repository.SalesRepository
import com.supermarket.inventory.data.repository.StatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

enum class StatsPeriod { DAY, MONTH }

private fun <T> ApiResult<T>.dataOrNull(): T? = when (this) {
    is ApiResult.Success -> data
    is ApiResult.Error -> null
}

// The shop's own clock. "Today" and "this month" are Cairo's, whatever time
// zone the phone happens to be set to, so the screen agrees with how the
// server cuts days and months - summer time included.
val BUSINESS_ZONE: ZoneId = ZoneId.of("Africa/Cairo")
fun businessToday(): LocalDate = LocalDate.now(BUSINESS_ZONE)

enum class StatsSort { QUANTITY, PROFIT }

data class StatsUiState(
    val period: StatsPeriod = StatsPeriod.DAY,
    val selectedDate: LocalDate = businessToday(),
    val sortBy: StatsSort = StatsSort.QUANTITY,
    val isLoading: Boolean = true,
    val topProducts: List<TopProductItemDto> = emptyList(),
    val margins: List<MarginItemDto> = emptyList(),
    val salesForDay: List<SaleDto> = emptyList(),
    val expensesForRange: ExpensesForRangeResponse? = null,
    val otherSalesForRange: OtherSalesForRangeResponse? = null,
    val periodRevenue: String = "0",
    val periodCost: String = "0",
    val periodProfit: String = "0",
    val periodSalesCount: Int = 0,
    // The day or month before the selected one, to measure it against.
    val previousPeriod: StatsPeriodDto? = null,
    // Something has been shown once. After that a reload keeps the figures
    // up (dimmed) instead of blanking the screen on every step.
    val hasLoaded: Boolean = false,
    val error: String? = null,
) {
    // The key the server uses for the selected period.
    val selectedKey: String
        get() = if (period == StatsPeriod.DAY) selectedDate.toString() else YearMonth.from(selectedDate).toString()

    // Nothing after today has anything in it. `stepBy` is the unit being
    // stepped through: the month-long reports step by month whatever the toggle says.
    fun canGoForward(stepBy: StatsPeriod = period): Boolean =
        if (stepBy == StatsPeriod.DAY) selectedDate < businessToday()
        else YearMonth.from(selectedDate) < YearMonth.from(businessToday())

    fun isCurrent(stepBy: StatsPeriod = period): Boolean =
        if (stepBy == StatsPeriod.DAY) selectedDate == businessToday()
        else YearMonth.from(selectedDate) == YearMonth.from(businessToday())
}

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val statsRepository: StatsRepository,
    private val salesRepository: SalesRepository,
    private val expenseRepository: ExpenseRepository,
    private val otherSaleRepository: OtherSaleRepository,
) : ViewModel() {

    var uiState by mutableStateOf(StatsUiState())
        private set

    init { load() }

    // Switching between day and month is a change of what's shown, not a
    // step through it: the figures on screen are for the other kind of
    // period, so they go rather than staying up, dimmed, under the new
    // heading.
    fun onPeriodChange(period: StatsPeriod) {
        if (period == uiState.period) return
        uiState = uiState.copy(period = period, hasLoaded = false)
        load()
    }

    fun onDateSelected(date: LocalDate) {
        uiState = uiState.copy(selectedDate = date)
        load()
    }

    // One day or one month back or forward - the arrows, and a swipe.
    //
    // A month step keeps the day of the month (the 24th of one month to the
    // 24th of the one before), so stepping back and forward again from a
    // report tab lands on the day the Overview was showing. It stops at
    // today: there's nothing to show after it.
    fun step(forward: Boolean, stepBy: StatsPeriod = uiState.period) {
        val state = uiState
        if (forward && !state.canGoForward(stepBy)) return
        val delta = if (forward) 1L else -1L
        val next = if (stepBy == StatsPeriod.DAY) state.selectedDate.plusDays(delta)
        else state.selectedDate.plusMonths(delta)
        onDateSelected(minOf(next, businessToday()))
    }

    // A month picked from the list, on the same day of it as the one in view
    // where it has one (and no later than today).
    fun onMonthSelected(month: YearMonth) {
        val day = minOf(uiState.selectedDate.dayOfMonth, month.lengthOfMonth())
        onDateSelected(minOf(month.atDay(day), businessToday()))
    }

    fun jumpToNow() = onDateSelected(businessToday())

    fun onSortChange(sort: StatsSort) {
        uiState = uiState.copy(sortBy = sort)
        load()
    }

    // Stepping through days quickly starts a load per step. Only the newest
    // may write its result: an older one finishing last would otherwise put
    // the wrong day's figures under the right date.
    private var loadGeneration = 0
    private var loadJob: Job? = null

    fun load() {
        val generation = ++loadGeneration
        loadJob?.cancel()
        // What this load is for, read once: the selection can move on while
        // it's in flight.
        val request = uiState
        uiState = request.copy(isLoading = true, error = null)
        loadJob = viewModelScope.launch {
            val day = request.period == StatsPeriod.DAY
            val sortParam = if (request.sortBy == StatsSort.QUANTITY) "quantity" else "profit"
            // Midday on the shop's clock names the month whatever time zone
            // the phone is set to. (The phone's own midnight on the 1st could
            // still be the last day of the month before in Cairo.)
            val dateIso = request.selectedDate.atTime(12, 0).atZone(BUSINESS_ZONE).toInstant().toString()

            // The headline figures come from the selected period and the one
            // before it, cut on the server in Cairo time. (They used to come
            // from a UTC-midnight window, which left the first hours after
            // the shop's midnight out of every day.)
            val periodName = if (day) "day" else "month"
            // A bare "yyyy-MM-dd" carries no timezone of its own - the
            // backend resolves the actual day boundaries against the
            // business's Egypt timezone (see utils/dates.ts server-side), so
            // the day's lists can't drift with the phone's own clock/tz. For
            // a month, any instant inside it names it; the server finds the
            // month's real boundaries in Cairo time.
            val rangeDate = if (day) request.selectedDate.toString() else dateIso

            // All at once rather than one after another: stepping through
            // days with the arrows waits on the slowest of these, not on
            // their sum.
            coroutineScope {
                // Top products is always a monthly view (regardless of the
                // Overview tab's day/month toggle) - it's the month containing
                // whichever date is currently selected, since a single day's
                // worth of sales is too thin a sample to be a useful ranking.
                // No limits: the charts on those tabs are drawn from these
                // lists, so a capped list would make them describe the top few
                // rows rather than the period or the catalogue.
                val topProductsCall = async { statsRepository.getTopProducts("month", dateIso, sortParam) }
                val marginsCall = async { statsRepository.getMargins() }
                val periodsCall = async { statsRepository.getPeriods(periodName, request.selectedDate.toString(), 2) }
                val salesCall = if (day) async { salesRepository.getSalesForRange(period = "day", date = rangeDate) } else null
                val expensesCall = async { expenseRepository.getExpensesForRange(periodName, rangeDate) }
                val otherSalesCall = async { otherSaleRepository.getForRange(periodName, rangeDate) }

                val periods = periodsCall.await().dataOrNull()?.periods ?: emptyList()
                val selected = periods.firstOrNull { it.key == request.selectedKey }
                val previous = periods.firstOrNull { it.key != request.selectedKey }
                val salesForDay = salesCall?.await()?.dataOrNull()?.items ?: emptyList()
                val expensesForRange = expensesCall.await().dataOrNull()
                val otherSalesForRange = otherSalesCall.await().dataOrNull()
                val topProductsResult = topProductsCall.await()
                val topProducts = topProductsResult.dataOrNull()?.items ?: emptyList()
                val margins = marginsCall.await().dataOrNull()?.items ?: emptyList()

                if (generation != loadGeneration) return@coroutineScope
                uiState = uiState.copy(
                    isLoading = false,
                    hasLoaded = true,
                    error = (topProductsResult as? ApiResult.Error)?.message,
                    topProducts = topProducts,
                    margins = margins,
                    salesForDay = salesForDay,
                    expensesForRange = expensesForRange,
                    otherSalesForRange = otherSalesForRange,
                    periodRevenue = selected?.revenue ?: "0",
                    periodCost = selected?.cost ?: "0",
                    periodProfit = selected?.profit ?: "0",
                    periodSalesCount = selected?.salesCount ?: 0,
                    previousPeriod = previous,
                )
            }
        }
    }

    fun deleteSale(id: String) {
        viewModelScope.launch {
            when (salesRepository.deleteSale(id)) {
                is ApiResult.Success -> load()
                is ApiResult.Error -> Unit
            }
        }
    }

}
