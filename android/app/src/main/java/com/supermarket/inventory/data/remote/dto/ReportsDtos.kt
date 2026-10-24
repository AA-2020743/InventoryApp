package com.supermarket.inventory.data.remote.dto

import com.squareup.moshi.JsonClass

// Money arrives as decimal strings, exactly as the server summed it; counts
// and day figures as plain numbers.

// ---- /api/stats/periods: consecutive days or months, cut in Cairo time ----

@JsonClass(generateAdapter = true)
data class StatsPeriodDto(
    // "YYYY-MM-DD" for a day, "YYYY-MM" for a month.
    val key: String,
    val revenue: String,
    val cost: String,
    val profit: String,
    val salesCount: Int,
)

@JsonClass(generateAdapter = true)
data class StatsPeriodsResponse(val period: String, val periods: List<StatsPeriodDto>)

// ---- /api/reports/pulse ----

@JsonClass(generateAdapter = true)
data class PulseTotalsDto(val revenue: String, val profit: String, val sales: Int)

// The month's expected total: what's been sold, plus the average full day
// so far for each day left. The breakdown fields are null from a server
// older than that.
@JsonClass(generateAdapter = true)
data class PulseProjectionDto(
    val revenue: String,
    val profit: String,
    val elapsedPercent: Double,
    // On top of what's already been sold.
    val remainingRevenue: String? = null,
    val remainingProfit: String? = null,
    val averageDayRevenue: String? = null,
    val averageDayProfit: String? = null,
    val dayOfMonth: Int? = null,
    val daysInMonth: Int? = null,
    val daysCompleted: Int? = null,
    // Today included.
    val daysLeft: Int? = null,
)

@JsonClass(generateAdapter = true)
data class PulseBasketDto(val averageSale: String?, val averageLines: Double?, val previousAverageSale: String?)

@JsonClass(generateAdapter = true)
data class PulseDayDto(val date: String, val revenue: String)

@JsonClass(generateAdapter = true)
data class HeatCellDto(val revenue: String, val sales: Int)

// cells[weekday][hour]: weekday 0 = Sunday ... 6 = Saturday, on the shop's clock.
@JsonClass(generateAdapter = true)
data class PulseHeatmapDto(val days: Int, val cells: List<List<HeatCellDto>>)

@JsonClass(generateAdapter = true)
data class ParetoProductDto(
    val productId: String,
    val name: String,
    val revenue: String,
    val profit: String,
    val quantity: String,
    val cumulativeSharePercent: Double,
)

@JsonClass(generateAdapter = true)
data class PulseParetoDto(
    val sharePercent: Double,
    val productsForShare: Int,
    val productsSold: Int,
    val products: List<ParetoProductDto>,
)

@JsonClass(generateAdapter = true)
data class PulseDto(
    val month: String,
    val inProgress: Boolean,
    val current: PulseTotalsDto,
    // The same stretch of the month before (all of it, once this month is over).
    val previous: PulseTotalsDto,
    val projection: PulseProjectionDto?,
    val basket: PulseBasketDto,
    val creditSharePercent: Double,
    val bestDay: PulseDayDto?,
    val quietestDay: PulseDayDto?,
    val heatmap: PulseHeatmapDto,
    val pareto: PulseParetoDto,
)

// ---- /api/reports/cost-trend ----

@JsonClass(generateAdapter = true)
data class CostMonthDto(
    val month: String,
    val active: Boolean,
    val inProgress: Boolean,
    val recurring: String,
    val variable: String,
    val spoilage: String,
    val total: String,
    val revenue: String,
    val costOfGoods: String,
    val grossProfit: String,
    val breakEvenRevenue: String?,
)

@JsonClass(generateAdapter = true)
data class CostNameDto(
    val name: String?,
    // One amount per month of the window, oldest first.
    val series: List<String>,
    val total: String,
    val monthsPresent: Int,
    val average: String,
    val recurring: Boolean,
)

@JsonClass(generateAdapter = true)
data class CostTrendDto(
    val months: List<String>,
    val activeMonths: Int,
    val recurringThreshold: Int,
    val typicalRecurring: String?,
    val monthRows: List<CostMonthDto>,
    val names: List<CostNameDto>,
)

// ---- /api/reports/suppliers ----

@JsonClass(generateAdapter = true)
data class SupplierMonthDto(val month: String, val suppliers: String, val unassigned: String)

@JsonClass(generateAdapter = true)
data class SupplierProductDto(val productId: String, val name: String, val unit: String, val quantity: String, val value: String)

@JsonClass(generateAdapter = true)
data class SupplierReportDto(
    val supplierId: String,
    val name: String,
    val series: List<String>,
    val total: String,
    val invoices: Int,
    val activeMonths: Int,
    val averagePerActiveMonth: String,
    val sharePercent: Double,
    // Percent per month over months that are over; null under three of them.
    val trendPercent: Double?,
    val owed: String,
    val products: List<SupplierProductDto>,
)

@JsonClass(generateAdapter = true)
data class SuppliersReportDto(
    val months: List<String>,
    val total: String,
    val trendPercent: Double?,
    val currentMonth: String,
    val monthTotals: List<SupplierMonthDto>,
    val suppliers: List<SupplierReportDto>,
)

// ---- /api/reports/deferred-customers ----

@JsonClass(generateAdapter = true)
data class CreditTotalsDto(
    val totalDeferred: String,
    val outstanding: String,
    val collected: String,
    val salesOnCredit: Int,
    val customers: Int,
    val allSalesRevenue: String,
)

@JsonClass(generateAdapter = true)
data class AgingBucketDto(val bucket: String, val outstanding: String, val count: Int)

@JsonClass(generateAdapter = true)
data class CreditCustomerDto(
    // null for sales recorded without a customer name.
    val name: String?,
    val total: String,
    val count: Int,
    val outstanding: String,
    val collected: String,
    val openCount: Int,
    val settledCount: Int,
    val avgDaysToSettle: Double?,
    val oldestOpenAt: String?,
    val firstAt: String,
    val lastAt: String,
    val monthly: List<String>,
)

@JsonClass(generateAdapter = true)
data class CreditReportDto(
    val totals: CreditTotalsDto,
    val aging: List<AgingBucketDto>,
    val sparkMonths: List<String>,
    val customers: List<CreditCustomerDto>,
)

// ---- /api/reports/sell-through ----

@JsonClass(generateAdapter = true)
data class SellThroughSummaryDto(
    val products: Int,
    val stockValue: String,
    val stagnantValue: String,
    val slowValue: String,
    val counts: Map<String, Int>,
)

@JsonClass(generateAdapter = true)
data class SellThroughProductDto(
    val productId: String,
    val name: String,
    val category: String?,
    val unit: String,
    val imageUrl: String?,
    val quantity: String,
    val stockValue: String,
    val unitsSold: String,
    val revenue: String,
    val measuredDays: Double,
    val perDay: String,
    val daysOfCover: Double?,
    val runsOutOn: String?,
    val lastSoldAt: String?,
    val batchesCleared: Int,
    val avgDaysToClear: Double?,
    val fastestClear: Double?,
    val slowestClear: Double?,
    val oldestStockDays: Double?,
    // OUT, STAGNANT, FAST, STEADY or SLOW.
    val status: String,
)

@JsonClass(generateAdapter = true)
data class SellThroughDto(val days: Int, val summary: SellThroughSummaryDto, val products: List<SellThroughProductDto>)

@JsonClass(generateAdapter = true)
data class StockBatchDto(
    val receivedAt: String,
    val quantity: String,
    val remaining: String,
    val clearedAt: String?,
    val daysToClear: Double?,
    val daysOnShelf: Double?,
    val opening: Boolean,
)

@JsonClass(generateAdapter = true)
data class StockBatchesDto(val unit: String, val batches: List<StockBatchDto>)
