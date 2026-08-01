package com.supermarket.inventory.data.repository

import com.supermarket.inventory.data.ApiResult
import com.supermarket.inventory.data.apiCall
import com.supermarket.inventory.data.remote.ApiService
import com.supermarket.inventory.data.remote.dto.SaleDto
import com.supermarket.inventory.data.remote.dto.SaleInput
import com.supermarket.inventory.data.remote.dto.SaleItemInput
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SalesRepository @Inject constructor(private val api: ApiService) {

    suspend fun getSales(from: String? = null, to: String? = null, limit: Int? = null): ApiResult<List<SaleDto>> =
        apiCall { api.getSales(from, to, limit) }

    suspend fun createSale(items: List<Pair<String, Double>>): ApiResult<SaleDto> = apiCall {
        api.createSale(SaleInput(items.map { (productId, quantity) -> SaleItemInput(productId, quantity) }))
    }
}
