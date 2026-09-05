package com.supermarket.inventory.ui.others

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

data class OthersUiState(
    val summary: DashboardSummaryDto? = null,
)

// The hub's tiles all draw from one place: the dashboard summary already
// carries every figure they show, so the landing page costs a single call
// rather than one per section.
@HiltViewModel
class OthersViewModel @Inject constructor(
    private val dashboardRepository: DashboardRepository,
) : ViewModel() {

    var uiState by mutableStateOf(OthersUiState())
        private set

    init { load() }

    fun load() {
        viewModelScope.launch {
            when (val result = dashboardRepository.getSummary()) {
                is ApiResult.Success -> uiState = uiState.copy(summary = result.data)
                // A tile without its figure still navigates, so a failed
                // summary is not worth an error state of its own here.
                is ApiResult.Error -> Unit
            }
        }
    }
}
