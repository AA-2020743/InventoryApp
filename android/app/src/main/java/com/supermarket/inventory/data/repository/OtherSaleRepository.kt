package com.supermarket.inventory.data.repository

import com.supermarket.inventory.data.ApiResult
import com.supermarket.inventory.data.apiCall
import com.supermarket.inventory.data.remote.ApiService
import com.supermarket.inventory.data.remote.dto.OtherSaleDto
import com.supermarket.inventory.data.remote.dto.OtherSaleInput
import com.supermarket.inventory.data.remote.dto.OtherSalesForRangeResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OtherSaleRepository @Inject constructor(private val api: ApiService) {
    suspend fun getOtherSales(): ApiResult<List<OtherSaleDto>> = apiCall { api.getOtherSales() }

    suspend fun getCategories(): ApiResult<List<String>> = apiCall { api.getOtherSaleCategories() }

    suspend fun getForRange(period: String, dateIso: String? = null): ApiResult<OtherSalesForRangeResponse> =
        apiCall { api.getOtherSalesForRange(period, dateIso) }

    suspend fun create(amount: Double, category: String?, notes: String?, date: String?): ApiResult<OtherSaleDto> =
        apiCall { api.createOtherSale(OtherSaleInput(amount, category, notes, date)) }

    suspend fun update(id: String, amount: Double, category: String?, notes: String?, date: String?): ApiResult<OtherSaleDto> =
        apiCall { api.updateOtherSale(id, OtherSaleInput(amount, category, notes, date)) }

    suspend fun delete(id: String): ApiResult<Unit> = apiCall { api.deleteOtherSale(id) }
}
