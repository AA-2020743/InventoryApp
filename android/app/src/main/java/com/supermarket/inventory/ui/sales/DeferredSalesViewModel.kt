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
    val customerSuggestions: List<String> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class DeferredSalesViewModel @Inject constructor(
    private val salesRepository: SalesRepository,
) : ViewModel() {

    var uiState by mutableStateOf(DeferredSalesUiState())
        private set

    init {
        load()
        loadCustomerSuggestions()
    }

    fun load() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null)
            when (val result = salesRepository.getSales(paymentStatus = "DEFERRED", limit = 200)) {
                is ApiResult.Success -> uiState = uiState.copy(isLoading = false, sales = result.data)
                is ApiResult.Error -> uiState = uiState.copy(isLoading = false, error = result.message)
            }
        }
    }

    private fun loadCustomerSuggestions() {
        viewModelScope.launch {
            when (val result = salesRepository.getCustomerNames()) {
                is ApiResult.Success -> uiState = uiState.copy(customerSuggestions = result.data)
                is ApiResult.Error -> Unit
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

    // Same customer can have several separate deferred sales (each its own
    // checkout or manual entry) that the screen displays merged into one
    // running tab - "mark collected" on that merged row clears every
    // underlying sale that still has a balance.
    suspend fun collectGroup(saleIds: List<String>): ApiResult<Unit> {
        for (id in saleIds) {
            val result = salesRepository.collectSale(id)
            if (result is ApiResult.Error) return result
        }
        load()
        return ApiResult.Success(Unit)
    }

    // Applies one entered amount across a customer's merged tab, oldest
    // sale first (paying down what's been owed longest before newer debt),
    // spilling into the next sale once the current one is fully covered.
    suspend fun collectPartialGroup(sales: List<SaleDto>, amount: Double): ApiResult<Unit> {
        var remaining = amount
        for (sale in sales.sortedBy { it.createdAt }) {
            if (remaining <= 0) break
            val owed = (sale.totalAmount.toDoubleOrNull() ?: 0.0) - (sale.amountCollected.toDoubleOrNull() ?: 0.0)
            if (owed <= 0) continue
            val toApply = minOf(remaining, owed)
            val result = salesRepository.collectSalePartial(sale.id, toApply)
            if (result is ApiResult.Error) return result
            remaining -= toApply
        }
        load()
        return ApiResult.Success(Unit)
    }

    suspend fun addManualDeferredSale(amount: Double, customerName: String?) =
        salesRepository.createManualDeferredSale(amount, customerName)
}
