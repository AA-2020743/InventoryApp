package com.supermarket.inventory.ui.sales

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.supermarket.inventory.data.ApiResult
import com.supermarket.inventory.data.remote.dto.SaleDto
import com.supermarket.inventory.data.repository.SalesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeferredSalesUiState(
    val isLoading: Boolean = true,
    val sales: List<SaleDto> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class DeferredSalesViewModel @Inject constructor(
    private val salesRepository: SalesRepository,
) : ViewModel() {

    var uiState by mutableStateOf(DeferredSalesUiState())
        private set

    init { load() }

    fun load() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null)
            when (val result = salesRepository.getSales(paymentStatus = "DEFERRED", limit = 200)) {
                is ApiResult.Success -> uiState = uiState.copy(isLoading = false, sales = result.data)
                is ApiResult.Error -> uiState = uiState.copy(isLoading = false, error = result.message)
            }
        }
    }

    fun collect(id: String) {
        viewModelScope.launch {
            when (salesRepository.collectSale(id)) {
                is ApiResult.Success -> load()
                is ApiResult.Error -> Unit
            }
        }
    }

    suspend fun collectPartial(id: String, amount: Double): ApiResult<SaleDto> {
        val result = salesRepository.collectSalePartial(id, amount)
        if (result is ApiResult.Success) load()
        return result
    }

    suspend fun addManualDeferredSale(amount: Double, customerName: String?) =
        salesRepository.createManualDeferredSale(amount, customerName)
}
