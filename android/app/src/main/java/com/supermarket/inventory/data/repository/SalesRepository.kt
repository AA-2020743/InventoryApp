package com.supermarket.inventory.data.repository

import com.supermarket.inventory.data.ApiResult
import com.supermarket.inventory.data.apiCall
import com.supermarket.inventory.data.remote.ApiService
import com.supermarket.inventory.data.remote.dto.SaleDto
import com.supermarket.inventory.data.remote.dto.SaleEditInput
import com.supermarket.inventory.data.remote.dto.SaleInput
import com.supermarket.inventory.data.remote.dto.SaleItemInput
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
}
