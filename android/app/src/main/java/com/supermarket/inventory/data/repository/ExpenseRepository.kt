package com.supermarket.inventory.data.repository

import com.supermarket.inventory.data.ApiResult
import com.supermarket.inventory.data.apiCall
import com.supermarket.inventory.data.remote.ApiService
import com.supermarket.inventory.data.remote.dto.ExpenseDto
import com.supermarket.inventory.data.remote.dto.ExpenseInput
import com.supermarket.inventory.data.remote.dto.ExpensesForDayResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpenseRepository @Inject constructor(private val api: ApiService) {
    suspend fun getExpenses(activeOnly: Boolean = false): ApiResult<List<ExpenseDto>> =
        apiCall { api.getExpenses(if (activeOnly) true else null) }

    suspend fun getExpensesForDay(dateIso: String? = null): ApiResult<ExpensesForDayResponse> =
        apiCall { api.getExpensesForDay(dateIso) }

    suspend fun createExpense(
        name: String,
        amount: Double,
        frequency: String,
        startDate: String?,
        notes: String?,
        paymentDayOfMonth: Int?,
        fromCashRegister: Boolean,
    ): ApiResult<ExpenseDto> =
        apiCall { api.createExpense(ExpenseInput(name, amount, frequency, startDate, null, true, notes, paymentDayOfMonth, fromCashRegister)) }

    suspend fun updateExpense(
        id: String,
        name: String,
        amount: Double,
        frequency: String,
        startDate: String?,
        notes: String?,
        paymentDayOfMonth: Int?,
        fromCashRegister: Boolean,
    ): ApiResult<ExpenseDto> =
        apiCall { api.updateExpense(id, ExpenseInput(name, amount, frequency, startDate, null, null, notes, paymentDayOfMonth, fromCashRegister)) }

    suspend fun deleteExpense(id: String): ApiResult<Unit> = apiCall { api.deleteExpense(id) }
}
