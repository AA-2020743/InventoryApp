package com.supermarket.inventory.data.repository

import com.supermarket.inventory.data.ApiResult
import com.supermarket.inventory.data.apiCall
import com.supermarket.inventory.data.remote.ApiService
import com.supermarket.inventory.data.remote.dto.ExpenseDto
import com.supermarket.inventory.data.remote.dto.ExpenseInput
import com.supermarket.inventory.data.remote.dto.ExpensesForRangeResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpenseRepository @Inject constructor(private val api: ApiService) {
    suspend fun getExpenses(): ApiResult<List<ExpenseDto>> = apiCall { api.getExpenses() }

    suspend fun getCategories(): ApiResult<List<String>> = apiCall { api.getExpenseCategories() }

    suspend fun getExpensesForRange(period: String, dateIso: String? = null): ApiResult<ExpensesForRangeResponse> =
        apiCall { api.getExpensesForRange(period, dateIso) }

    suspend fun createExpense(name: String, amount: Double, category: String?, date: String?, notes: String?): ApiResult<ExpenseDto> =
        apiCall { api.createExpense(ExpenseInput(name, amount, category, date, notes)) }

    suspend fun updateExpense(id: String, name: String, amount: Double, category: String?, date: String?, notes: String?): ApiResult<ExpenseDto> =
        apiCall { api.updateExpense(id, ExpenseInput(name, amount, category, date, notes)) }

    suspend fun deleteExpense(id: String): ApiResult<Unit> = apiCall { api.deleteExpense(id) }
}
