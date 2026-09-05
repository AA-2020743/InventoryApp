package com.supermarket.inventory.data.repository

import com.supermarket.inventory.data.ApiResult
import com.supermarket.inventory.data.apiCall
import com.supermarket.inventory.data.remote.ApiService
import com.supermarket.inventory.data.remote.dto.CollectPartialRequest
import com.supermarket.inventory.data.remote.dto.SaleDto
import com.supermarket.inventory.data.remote.dto.SaleEditInput
import com.supermarket.inventory.data.remote.dto.SaleInput
import com.supermarket.inventory.data.remote.dto.SaleItemInput
import com.supermarket.inventory.data.remote.dto.SalesForRangeResponse
import com.supermarket.inventory.data.remote.dto.SalesMonthDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SalesRepository @Inject constructor(private val api: ApiService) {

    suspend fun getSales(
        from: String? = null,
        to: String? = null,
        limit: Int? = null,
        paymentStatus: String? = null,
    ): ApiResult<List<SaleDto>> = apiCall { api.getSales(from, to, limit, paymentStatus) }

    suspend fun getSale(id: String): ApiResult<SaleDto> = apiCall { api.getSale(id) }

    // Distinct customer names already used on a deferred sale, for the
    // autocomplete when entering a new one - same convention as
    // ProductRepository's category suggestions.
    suspend fun getCustomerNames(): ApiResult<List<String>> = apiCall { api.getCustomerNames() }

    // Sales list for a calendar day/month, boundaries resolved server-side
    // against the business's Egypt timezone (see backend utils/dates.ts) -
    // pass a plain "yyyy-MM-dd" date so no client timezone assumption is
    // baked into the request at all.
    suspend fun getSalesForRange(period: String, date: String, limit: Int? = null): ApiResult<SalesForRangeResponse> =
        apiCall { api.getSalesForRange(period, date, limit) }

    suspend fun getMonths(): ApiResult<List<SalesMonthDto>> = apiCall { api.getSalesMonths() }

    suspend fun createSale(
        items: List<Pair<String, Double>>,
        clientId: String? = null,
        isDeferred: Boolean = false,
        customerName: String? = null,
    ): ApiResult<SaleDto> = apiCall {
        api.createSale(
            SaleInput(
                clientId = clientId,
                items = items.map { (productId, quantity) -> SaleItemInput(productId, quantity) },
                paymentStatus = if (isDeferred) "DEFERRED" else "PAID",
                customerName = customerName,
            )
        )
    }

    // Records a deferred sale directly as a total owed by a customer,
    // without going through the checkout flow or tying it to specific
    // inventory - e.g. an existing customer tab being entered into the app.
    suspend fun createManualDeferredSale(amount: Double, customerName: String? = null): ApiResult<SaleDto> = apiCall {
        api.createSale(
            SaleInput(
                clientId = null,
                items = emptyList(),
                paymentStatus = "DEFERRED",
                customerName = customerName,
                manualAmount = amount,
            )
        )
    }

    suspend fun updateSale(
        id: String,
        items: List<Pair<String, Double>>,
        customerName: String? = null,
        paymentStatus: String? = null,
    ): ApiResult<SaleDto> = apiCall {
        api.updateSale(
            id,
            SaleEditInput(
                items = items.map { (productId, quantity) -> SaleItemInput(productId, quantity) },
                customerName = customerName,
                paymentStatus = paymentStatus,
            ),
        )
    }

    suspend fun deleteSale(id: String): ApiResult<Unit> = apiCall { api.deleteSale(id) }

    suspend fun collectSale(id: String): ApiResult<SaleDto> = apiCall { api.collectSale(id) }

    suspend fun collectSalePartial(id: String, amount: Double): ApiResult<SaleDto> =
        apiCall { api.collectSalePartial(id, CollectPartialRequest(amount)) }
}
