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
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

data class DeferredSalesUiState(
    val isLoading: Boolean = true,
    val sales: List<SaleDto> = emptyList(),
    // Deferred sales that have since been collected in full. They used to
    // vanish on collection, leaving no way to see who settled up and when.
    val collected: List<SaleDto> = emptyList(),
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
            when (val result = salesRepository.getSales(paymentStatus = "DEFERRED")) {
                is ApiResult.Success -> uiState = uiState.copy(isLoading = false, sales = result.data)
                is ApiResult.Error -> uiState = uiState.copy(isLoading = false, error = result.message)
            }
            // A collectedAt is what separates a tab that was settled from an
            // ordinary sale paid at the counter. The server does that
            // filtering: asked for "the last 200 paid sales" and then
            // filtered here, a shop that rings up mostly counter sales
            // would see an almost empty history however many tabs it had
            // actually collected.
            when (val collectedResult = salesRepository.getSales(paymentStatus = "PAID", collected = true)) {
                is ApiResult.Success -> uiState = uiState.copy(collected = collectedResult.data)
                is ApiResult.Error -> Unit
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
    //
    // Split in exact money: done in Double, 100.30 across tabs of 50.10 and
    // 50.20 sent the second one 50.199999999999996, which never settled it.
    suspend fun collectPartialGroup(sales: List<SaleDto>, amount: Double): ApiResult<Unit> {
        var remaining = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP)
        for (sale in sales.sortedBy { it.createdAt }) {
            if (remaining.signum() <= 0) break
            val owed = sale.totalAmount.toMoney().subtract(sale.amountCollected.toMoney())
            if (owed.signum() <= 0) continue
            val toApply = remaining.min(owed)
            val result = salesRepository.collectSalePartial(sale.id, toApply.toDouble())
            if (result is ApiResult.Error) return result
            remaining = remaining.subtract(toApply)
        }
        load()
        return ApiResult.Success(Unit)
    }

    suspend fun addManualDeferredSale(amount: Double, customerName: String?) =
        salesRepository.createManualDeferredSale(amount, customerName)
}

private fun String.toMoney(): BigDecimal = (toBigDecimalOrNull() ?: BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)
