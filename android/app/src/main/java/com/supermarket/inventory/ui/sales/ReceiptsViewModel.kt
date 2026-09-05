package com.supermarket.inventory.ui.sales

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.supermarket.inventory.data.ApiResult
import com.supermarket.inventory.data.remote.dto.SaleDto
import com.supermarket.inventory.data.remote.dto.SalesMonthDto
import com.supermarket.inventory.data.repository.SalesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

data class ReceiptsUiState(
    val isLoading: Boolean = true,
    // Every month that has sales, with its count and takings.
    val months: List<SalesMonthDto> = emptyList(),
    val currentMonthSales: List<SaleDto> = emptyList(),
    // Older months whose receipts have actually been fetched, keyed
    // "YYYY-MM" - a month is only loaded when it's opened.
    val monthSales: Map<String, List<SaleDto>> = emptyMap(),
    val loadingMonth: String? = null,
    val error: String? = null,
)

@HiltViewModel
class ReceiptsViewModel @Inject constructor(
    private val salesRepository: SalesRepository,
) : ViewModel() {

    var uiState by mutableStateOf(ReceiptsUiState())
        private set

    init { load() }

    // A month is asked for by any date inside it; the server cuts the
    // boundaries on the shop's own clock, so a sale rung up just after
    // midnight lands in the month the shop would say it did.
    private fun anyDayIn(month: String): String = "$month-15T12:00:00.000Z"

    fun load() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null)
            when (val monthsResult = salesRepository.getMonths()) {
                is ApiResult.Success -> uiState = uiState.copy(months = monthsResult.data)
                is ApiResult.Error -> uiState = uiState.copy(error = monthsResult.message)
            }
            val current = YearMonth.now().toString()
            when (val result = salesRepository.getSalesForRange("month", anyDayIn(current), limit = 500)) {
                is ApiResult.Success ->
                    uiState = uiState.copy(isLoading = false, currentMonthSales = result.data.items)
                is ApiResult.Error -> uiState = uiState.copy(isLoading = false, error = result.message)
            }
            // Anything already open is refreshed too, so a receipt corrected
            // in an open month doesn't leave a stale copy on screen.
            uiState.monthSales.keys.toList().forEach { loadMonth(it, force = true) }
        }
    }

    fun loadMonth(month: String, force: Boolean = false) {
        if (!force && uiState.monthSales.containsKey(month)) return
        viewModelScope.launch {
            uiState = uiState.copy(loadingMonth = month)
            when (val result = salesRepository.getSalesForRange("month", anyDayIn(month), limit = 500)) {
                is ApiResult.Success ->
                    uiState = uiState.copy(
                        loadingMonth = null,
                        monthSales = uiState.monthSales + (month to result.data.items),
                    )
                is ApiResult.Error -> uiState = uiState.copy(loadingMonth = null, error = result.message)
            }
        }
    }

    fun forgetMonth(month: String) {
        uiState = uiState.copy(monthSales = uiState.monthSales - month)
    }
}
