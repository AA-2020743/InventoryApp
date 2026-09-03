package com.supermarket.inventory.data.repository

import com.supermarket.inventory.data.ApiResult
import com.supermarket.inventory.data.apiCall
import com.supermarket.inventory.data.remote.ApiService
import com.supermarket.inventory.data.remote.dto.CashRegisterEntryRequest
import com.supermarket.inventory.data.remote.dto.CashRegisterEntryResponse
import com.supermarket.inventory.data.remote.dto.CashRegisterResponse
import com.supermarket.inventory.data.remote.dto.SetCashRegisterRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CashRegisterRepository @Inject constructor(private val api: ApiService) {
    suspend fun getRegister(): ApiResult<CashRegisterResponse> = apiCall { api.getCashRegister() }

    suspend fun setBalance(value: Double, note: String?): ApiResult<CashRegisterEntryResponse> =
        apiCall { api.setCashRegister(SetCashRegisterRequest(value, note)) }

    // Removes a hand-made till entry recorded by mistake (a mistyped top-up,
    // or a reconciliation that shouldn't have happened). The server refuses
    // entries that belong to another record.
    suspend fun deleteEntry(id: String): ApiResult<CashRegisterResponse> =
        apiCall { api.deleteCashRegisterEntry(id) }

    suspend fun addEntry(amount: Double, note: String?): ApiResult<CashRegisterEntryResponse> =
        apiCall { api.addCashRegisterEntry(CashRegisterEntryRequest(amount, note)) }
}
