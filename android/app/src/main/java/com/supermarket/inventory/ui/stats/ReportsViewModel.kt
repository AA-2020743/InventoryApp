package com.supermarket.inventory.ui.stats

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.supermarket.inventory.data.ApiResult
import com.supermarket.inventory.data.remote.dto.CostTrendDto
import com.supermarket.inventory.data.remote.dto.CreditReportDto
import com.supermarket.inventory.data.remote.dto.PulseDto
import com.supermarket.inventory.data.remote.dto.SellThroughDto
import com.supermarket.inventory.data.remote.dto.StockBatchesDto
import com.supermarket.inventory.data.remote.dto.SuppliersReportDto
import com.supermarket.inventory.data.repository.ReportsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject
import kotlin.reflect.KMutableProperty0

// One report's state. `key` is what it was loaded for (a month, a window), so
// opening its tab again for the same thing shows what's there instead of
// fetching it again.
data class ReportState<T>(
    val loading: Boolean = false,
    val data: T? = null,
    val error: String? = null,
    val key: String? = null,
)

// The Statistics section's longer-range reports. Each loads only when its tab
// is opened - most visits are to the day's figures - and reloads when what
// it's for changes.
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val repository: ReportsRepository,
) : ViewModel() {

    var pulse by mutableStateOf(ReportState<PulseDto>())
        private set
    var costs by mutableStateOf(ReportState<CostTrendDto>())
        private set
    var suppliers by mutableStateOf(ReportState<SuppliersReportDto>())
        private set
    var credit by mutableStateOf(ReportState<CreditReportDto>())
        private set
    var sellThrough by mutableStateOf(ReportState<SellThroughDto>())
        private set
    var batches by mutableStateOf(ReportState<StockBatchesDto>())
        private set

    // How many months the cost and supplier trends look back over.
    var trendWindow by mutableStateOf(6)
        private set
    // The days sell-through measures pace over.
    var paceDays by mutableStateOf(30)
        private set

    // Keeps what's on screen while the new one loads, and drops an answer
    // that arrives after the question changed - stepping through months
    // quickly would otherwise let a slow, older reply land last and show
    // the wrong month under the right heading.
    private fun <T> load(
        state: KMutableProperty0<ReportState<T>>,
        key: String,
        force: Boolean,
        fetch: suspend () -> ApiResult<T>,
    ) {
        val current = state.get()
        if (!force && current.key == key && (current.data != null || current.loading)) return
        // What's on screen is kept on a failed refresh of the same thing, but
        // not when it was for something else: last month's figures shouldn't
        // stay up under this month's heading because this month didn't load.
        val sameKey = current.key == key
        state.set(current.copy(loading = true, error = null, key = key))
        viewModelScope.launch {
            val result = fetch()
            if (state.get().key != key) return@launch
            state.set(
                when (result) {
                    is ApiResult.Success -> ReportState(data = result.data, key = key)
                    is ApiResult.Error -> ReportState(data = if (sameKey) state.get().data else null, error = result.message, key = key)
                }
            )
        }
    }

    fun loadPulse(month: YearMonth, force: Boolean = false) =
        load(::pulse, month.toString(), force) { repository.getPulse(month.toString()) }

    fun loadCosts(month: YearMonth, force: Boolean = false) =
        load(::costs, "$month/$trendWindow", force) { repository.getCostTrend(month.toString(), trendWindow) }

    fun loadSuppliers(month: YearMonth, force: Boolean = false) =
        load(::suppliers, "$month/$trendWindow", force) { repository.getSuppliers(month.toString(), trendWindow) }

    fun loadCredit(force: Boolean = false) =
        load(::credit, "all", force) { repository.getCredit() }

    fun loadSellThrough(force: Boolean = false) =
        load(::sellThrough, "$paceDays", force) { repository.getSellThrough(paceDays) }

    fun loadBatches(productId: String) =
        load(::batches, productId, false) { repository.getBatches(productId) }

    fun setTrendWindow(months: Int, month: YearMonth) {
        trendWindow = months
        loadCosts(month)
        loadSuppliers(month)
    }

    fun setPaceDays(days: Int) {
        paceDays = days
        loadSellThrough()
    }
}
