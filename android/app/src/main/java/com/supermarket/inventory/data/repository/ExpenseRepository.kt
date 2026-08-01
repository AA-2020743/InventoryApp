package com.supermarket.inventory.data.repository

import com.supermarket.inventory.data.ApiResult
import com.supermarket.inventory.data.apiCall
import com.supermarket.inventory.data.remote.ApiService
import com.supermarket.inventory.data.remote.dto.ExpenseDto
import com.supermarket.inventory.data.remote.dto.ExpenseInput
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpenseRepository @Inject constructor(private val api: ApiService) {
    suspend fun getExpenses(activeOnly: Boolean = false): ApiResult<List<ExpenseDto>> =
        apiCall { api.getExpenses(if (activeOnly) true else null) }

    suspend fun createExpense(name: String, amount: Double, frequency: String, notes: String?): ApiResult<ExpenseDto> =
        apiCall { api.createExpense(ExpenseInput(name, amount, frequency, null, null, true, notes)) }

    suspend fun deleteExpense(id: String): ApiResult<Unit> = apiCall { api.deleteExpense(id) }
}
