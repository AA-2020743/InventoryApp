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
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: DashboardRepository,
) : ViewModel() {

    var uiState by mutableStateOf(DashboardUiState())
        private set

    init {
        refresh()
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
}
