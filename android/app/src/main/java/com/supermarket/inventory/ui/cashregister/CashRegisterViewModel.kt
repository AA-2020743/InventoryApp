package com.supermarket.inventory.ui.cashregister

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.supermarket.inventory.data.ApiResult
import com.supermarket.inventory.data.remote.dto.CashMonthDto
import com.supermarket.inventory.data.remote.dto.CashRegisterEntryDto
import com.supermarket.inventory.data.repository.CashRegisterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

data class CashRegisterUiState(
    val isLoading: Boolean = true,
    val balance: String = "0",
    // The month in progress, in full.
    val entries: List<CashRegisterEntryDto> = emptyList(),
    // Every month the register has movements in, with its totals - enough to
    // draw the folded history cards without loading the months themselves.
    val months: List<CashMonthDto> = emptyList(),
    // Months whose rows have actually been fetched, keyed "YYYY-MM". A month
    // is only loaded when it's opened, so a long history costs nothing until
    // someone looks into it.
    val monthEntries: Map<String, List<CashRegisterEntryDto>> = emptyMap(),
    val loadingMonth: String? = null,
    val error: String? = null,
)

@HiltViewModel
class CashRegisterViewModel @Inject constructor(
    private val repository: CashRegisterRepository,
) : ViewModel() {

    var uiState by mutableStateOf(CashRegisterUiState())
        private set

    // The ledger is asked for one month at a time rather than "the last
    // hundred rows": a hundred rows is an arbitrary window that can cut a
    // month in half, and a half-month's figures are worse than none.
    private fun currentMonth(): String = YearMonth.now().toString()

    init { load() }

    fun load() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null)
            when (val result = repository.getRegister(month = currentMonth())) {
                is ApiResult.Success ->
                    uiState = uiState.copy(isLoading = false, balance = result.data.balance, entries = result.data.entries)
                is ApiResult.Error -> uiState = uiState.copy(isLoading = false, error = result.message)
            }
            when (val monthsResult = repository.getMonths()) {
                // Anything already open is reloaded too, so a new entry or a
                // deletion shows up in an expanded month instead of leaving
                // a stale copy on screen.
                is ApiResult.Success -> {
                    uiState = uiState.copy(months = monthsResult.data)
                    uiState.monthEntries.keys.toList().forEach { loadMonth(it, force = true) }
                }
                is ApiResult.Error -> Unit
            }
        }
    }

    fun loadMonth(month: String, force: Boolean = false) {
        if (!force && uiState.monthEntries.containsKey(month)) return
        viewModelScope.launch {
            uiState = uiState.copy(loadingMonth = month)
            when (val result = repository.getRegister(month = month)) {
                is ApiResult.Success ->
                    uiState = uiState.copy(
                        loadingMonth = null,
                        monthEntries = uiState.monthEntries + (month to result.data.entries),
                    )
                is ApiResult.Error -> uiState = uiState.copy(loadingMonth = null, error = result.message)
            }
        }
    }

    fun forgetMonth(month: String) {
        uiState = uiState.copy(monthEntries = uiState.monthEntries - month)
    }

    fun dismissError() {
        uiState = uiState.copy(error = null)
    }

    fun setBalance(value: Double, note: String?) {
        viewModelScope.launch {
            when (repository.setBalance(value, note)) {
                is ApiResult.Success -> load()
                is ApiResult.Error -> Unit
            }
        }
    }

    fun deleteEntry(id: String) {
        viewModelScope.launch {
            when (val result = repository.deleteEntry(id)) {
                is ApiResult.Success -> load()
                is ApiResult.Error -> uiState = uiState.copy(error = result.message)
            }
        }
    }

    fun addEntry(amount: Double, note: String?) {
        viewModelScope.launch {
            when (repository.addEntry(amount, note)) {
                is ApiResult.Success -> load()
                is ApiResult.Error -> Unit
            }
        }
    }
}
