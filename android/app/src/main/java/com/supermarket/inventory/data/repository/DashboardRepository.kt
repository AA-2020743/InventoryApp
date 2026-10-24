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

    suspend fun getPeriods(period: String, end: String, count: Int): ApiResult<StatsPeriodsResponse> =
        apiCall { api.getStatsPeriods(period, end, count) }
}

@Singleton
class ReportsRepository @Inject constructor(private val api: ApiService) {
    suspend fun getPulse(month: String): ApiResult<PulseDto> = apiCall { api.getPulse(month) }
    suspend fun getCostTrend(month: String, months: Int): ApiResult<CostTrendDto> = apiCall { api.getCostTrend(month, months) }
    suspend fun getSuppliers(month: String, months: Int): ApiResult<SuppliersReportDto> = apiCall { api.getSuppliersReport(month, months) }
    suspend fun getCredit(): ApiResult<CreditReportDto> = apiCall { api.getCreditReport() }
    suspend fun getSellThrough(days: Int): ApiResult<SellThroughDto> = apiCall { api.getSellThrough(days) }
    suspend fun getBatches(productId: String): ApiResult<StockBatchesDto> = apiCall { api.getStockBatches(productId) }
}
