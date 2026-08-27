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
import com.supermarket.inventory.data.remote.dto.TopProductItemDto
import com.supermarket.inventory.data.repository.ExpenseRepository
import com.supermarket.inventory.data.repository.OtherSaleRepository
import com.supermarket.inventory.data.repository.SalesRepository
import com.supermarket.inventory.data.repository.StatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject

enum class StatsPeriod { DAY, MONTH }
enum class StatsSort { QUANTITY, PROFIT }

data class StatsUiState(
    val period: StatsPeriod = StatsPeriod.DAY,
    val selectedDate: LocalDate = LocalDate.now(),
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
    val error: String? = null,
)

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

    fun onPeriodChange(period: StatsPeriod) {
        uiState = uiState.copy(period = period)
        load()
    }

    fun onDateSelected(date: LocalDate) {
        uiState = uiState.copy(selectedDate = date)
        load()
    }

    fun onSortChange(sort: StatsSort) {
        uiState = uiState.copy(sortBy = sort)
        load()
    }

    fun load() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null)
            val sortParam = if (uiState.sortBy == StatsSort.QUANTITY) "quantity" else "profit"
            val dateIso = uiState.selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toString()

            // Top products is always a monthly view (regardless of the
            // Overview tab's day/month toggle) - it's the month containing
            // whichever date is currently selected, since a single day's
            // worth of sales is too thin a sample to be a useful ranking.
            val topProductsDeferred = statsRepository.getTopProducts("month", dateIso, sortParam, 20)
            val marginsDeferred = statsRepository.getMargins(20)

            var salesForDay: List<SaleDto> = emptyList()
            var expensesForRange: ExpensesForRangeResponse? = null
            var otherSalesForRange: OtherSalesForRangeResponse? = null
            var revenue = "0"
            var cost = "0"
            var profit = "0"

            if (uiState.period == StatsPeriod.DAY) {
                // A bare "yyyy-MM-dd" carries no timezone of its own - the
                // backend resolves the actual day boundaries against the
                // business's Egypt timezone (see utils/dates.ts server-side),
                // so this list can't drift with the phone's own clock/tz.
                val dayStart = uiState.selectedDate.toString()
                when (val salesResult = salesRepository.getSalesForRange(period = "day", date = dayStart, limit = 200)) {
                    is ApiResult.Success -> {
                        salesForDay = salesResult.data.items
                        val totalRevenue = salesResult.data.items.sumOf { it.totalAmount.toDoubleOrNull() ?: 0.0 }
                        val totalCost = salesResult.data.items.sumOf { it.totalCost.toDoubleOrNull() ?: 0.0 }
                        revenue = totalRevenue.toString()
                        cost = totalCost.toString()
                        profit = (totalRevenue - totalCost).toString()
                    }
                    is ApiResult.Error -> Unit
                }
                when (val expensesResult = expenseRepository.getExpensesForRange("day", dayStart)) {
                    is ApiResult.Success -> expensesForRange = expensesResult.data
                    is ApiResult.Error -> Unit
                }
                when (val otherSalesResult = otherSaleRepository.getForRange("day", dayStart)) {
                    is ApiResult.Success -> otherSalesForRange = otherSalesResult.data
                    is ApiResult.Error -> Unit
                }
            } else {
                val monthStart = uiState.selectedDate.withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant().toString()
                val monthEnd = uiState.selectedDate.withDayOfMonth(uiState.selectedDate.lengthOfMonth())
                    .plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toString()
                when (val revenueResult = statsRepository.getRevenueSeries("month", monthStart, monthEnd)) {
                    is ApiResult.Success -> {
                        val bucket = revenueResult.data.series.firstOrNull()
                        revenue = bucket?.revenue ?: "0"
                        cost = bucket?.cost ?: "0"
                        profit = bucket?.profit ?: "0"
                    }
                    is ApiResult.Error -> Unit
                }
                when (val expensesResult = expenseRepository.getExpensesForRange("month", monthStart)) {
                    is ApiResult.Success -> expensesForRange = expensesResult.data
                    is ApiResult.Error -> Unit
                }
                when (val otherSalesResult = otherSaleRepository.getForRange("month", monthStart)) {
                    is ApiResult.Success -> otherSalesForRange = otherSalesResult.data
                    is ApiResult.Error -> Unit
                }
            }

            val topProducts = when (val result = topProductsDeferred) {
                is ApiResult.Success -> result.data.items
                is ApiResult.Error -> { uiState = uiState.copy(error = result.message); emptyList() }
            }
            val margins = when (val result = marginsDeferred) {
                is ApiResult.Success -> result.data.items
                is ApiResult.Error -> emptyList()
            }
            uiState = uiState.copy(
                isLoading = false,
                topProducts = topProducts,
                margins = margins,
                salesForDay = salesForDay,
                expensesForRange = expensesForRange,
                otherSalesForRange = otherSalesForRange,
                periodRevenue = revenue,
                periodCost = cost,
                periodProfit = profit,
            )
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
