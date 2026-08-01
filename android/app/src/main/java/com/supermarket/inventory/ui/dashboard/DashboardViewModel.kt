package com.supermarket.inventory.ui.dashboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.supermarket.inventory.data.ApiResult
import com.supermarket.inventory.data.remote.dto.DashboardSummaryDto
import com.supermarket.inventory.data.repository.DashboardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val isLoading: Boolean = true,
    val summary: DashboardSummaryDto? = null,
    val error: String? = null,
    val showWorkingDayPrompt: Boolean = false,
    val todayIsWorkingDay: Boolean? = null, // null = not yet known
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: DashboardRepository,
) : ViewModel() {

    var uiState by mutableStateOf(DashboardUiState())
        private set

    init {
        refresh()
        checkWorkingDay()
    }

    fun refresh() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = uiState.summary == null, error = null)
            when (val result = repository.getSummary()) {
                is ApiResult.Success -> uiState = uiState.copy(isLoading = false, summary = result.data)
                is ApiResult.Error -> uiState = uiState.copy(isLoading = false, error = result.message)
            }
        }
    }

    // Only checked once per app session (not on every periodic refresh) -
    // whether the shop was open today only needs answering once a day, and
    // the server remembers the answer once given.
    private fun checkWorkingDay() {
        viewModelScope.launch {
            when (val result = repository.getTodayWorkingDay()) {
                is ApiResult.Success -> uiState = uiState.copy(
                    showWorkingDayPrompt = !result.data.answered,
                    todayIsWorkingDay = result.data.isWorking,
                )
                is ApiResult.Error -> Unit
            }
        }
    }

    /** Re-opens the prompt so the owner can change today's answer if they tapped the wrong one. */
    fun reopenWorkingDayPrompt() {
        uiState = uiState.copy(showWorkingDayPrompt = true)
    }

    fun answerWorkingDay(isWorking: Boolean) {
        viewModelScope.launch {
            uiState = uiState.copy(showWorkingDayPrompt = false, todayIsWorkingDay = isWorking)
            repository.setTodayWorkingDay(isWorking)
            refresh()
        }
    }
}
