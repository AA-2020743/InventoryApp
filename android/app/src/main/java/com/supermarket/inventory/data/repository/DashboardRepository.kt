package com.supermarket.inventory.data.repository

import com.supermarket.inventory.data.ApiResult
import com.supermarket.inventory.data.apiCall
import com.supermarket.inventory.data.remote.ApiService
import com.supermarket.inventory.data.remote.dto.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DashboardRepository @Inject constructor(private val api: ApiService) {
    suspend fun getSummary(): ApiResult<DashboardSummaryDto> = apiCall { api.getDashboardSummary() }
    suspend fun getAlerts(days: Int? = null): ApiResult<AlertsResponse> = apiCall { api.getAlerts(days) }
    suspend fun getTodayWorkingDay(): ApiResult<WorkingDayDto> = apiCall { api.getTodayWorkingDay() }
    suspend fun setTodayWorkingDay(isWorking: Boolean): ApiResult<WorkingDayDto> =
        apiCall { api.setTodayWorkingDay(SetWorkingDayRequest(isWorking)) }
}

@Singleton
class StatsRepository @Inject constructor(private val api: ApiService) {
    suspend fun getTopProducts(
        period: String,
        date: String? = null,
        sortBy: String? = null,
        limit: Int? = null,
    ): ApiResult<TopProductsResponse> = apiCall { api.getTopProducts(period, date, sortBy, limit) }

    suspend fun getMargins(limit: Int? = null): ApiResult<MarginsResponse> = apiCall { api.getMargins(limit) }

    suspend fun getRevenueSeries(period: String, from: String? = null, to: String? = null): ApiResult<RevenueSeriesResponse> =
        apiCall { api.getRevenueSeries(period, from, to) }
}
