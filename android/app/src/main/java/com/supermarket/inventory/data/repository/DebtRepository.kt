package com.supermarket.inventory.data.repository

import com.supermarket.inventory.data.ApiResult
import com.supermarket.inventory.data.apiCall
import com.supermarket.inventory.data.remote.ApiService
import com.supermarket.inventory.data.remote.dto.DebtDto
import com.supermarket.inventory.data.remote.dto.DebtInput
import com.supermarket.inventory.data.remote.dto.DebtRepayPartialRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DebtRepository @Inject constructor(private val api: ApiService) {

    suspend fun getDebts(status: String? = null): ApiResult<List<DebtDto>> = apiCall { api.getDebts(status) }

    // Distinct worker names already used on a debt, for the autocomplete
    // when recording a new one - same convention as SalesRepository's
    // getCustomerNames.
    suspend fun getWorkerNames(): ApiResult<List<String>> = apiCall { api.getWorkerNames() }

    suspend fun createDebt(workerName: String, amount: Double, notes: String? = null): ApiResult<DebtDto> =
        apiCall { api.createDebt(DebtInput(workerName, amount, notes)) }

    suspend fun repayDebt(id: String): ApiResult<DebtDto> = apiCall { api.repayDebt(id) }

    suspend fun repayDebtPartial(id: String, amount: Double): ApiResult<DebtDto> =
        apiCall { api.repayDebtPartial(id, DebtRepayPartialRequest(amount)) }

    suspend fun deleteDebt(id: String): ApiResult<Unit> = apiCall { api.deleteDebt(id) }
}
