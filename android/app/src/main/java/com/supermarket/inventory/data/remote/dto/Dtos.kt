package com.supermarket.inventory.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LoginRequest(val email: String, val password: String)

@JsonClass(generateAdapter = true)
data class LoginResponse(val token: String, val user: UserDto)

@JsonClass(generateAdapter = true)
data class UserDto(val id: String, val email: String, val name: String)

@JsonClass(generateAdapter = true)
data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)

@JsonClass(generateAdapter = true)
data class ProductDto(
    val id: String,
    val barcode: String?,
    val name: String,
    val imageUrl: String?,
    val category: String?,
    val unit: String,
    val unitsPerPackage: String,
    val purchaseCost: String,
    val sellingPrice: String,
    val quantity: String,
    val lowStockThreshold: String,
    val active: Boolean,
    val createdAt: String,
    val updatedAt: String,
)

@JsonClass(generateAdapter = true)
data class ProductInput(
    val barcode: String?,
    val name: String,
    val imageUrl: String?,
    val category: String?,
    val unit: String,
    val unitsPerPackage: Double,
    val purchaseCost: Double,
    val sellingPrice: Double,
    val quantity: Double,
    val lowStockThreshold: Double,
)

@JsonClass(generateAdapter = true)
data class RestockRequest(val quantity: Double, val unitCost: Double?, val note: String?)

@JsonClass(generateAdapter = true)
data class AdjustRequest(val quantityChange: Double, val note: String)

@JsonClass(generateAdapter = true)
data class InventoryTransactionDto(
    val id: String,
    val productId: String,
    val type: String,
    val quantityChange: String,
    val unitCost: String?,
    val note: String?,
    val createdAt: String,
)

@JsonClass(generateAdapter = true)
data class UploadImageResponse(val url: String)

@JsonClass(generateAdapter = true)
data class SupplierDto(
    val id: String,
    val name: String,
    val contactInfo: String?,
    val createdAt: String,
)

@JsonClass(generateAdapter = true)
data class SupplierInput(val name: String, val contactInfo: String?)

@JsonClass(generateAdapter = true)
data class SupplierInvoiceDto(
    val id: String,
    val supplierId: String,
    val supplier: SupplierDto?,
    val invoiceNumber: String?,
    val amount: String,
    val issueDate: String,
    val dueDate: String,
    val status: String,
    val paidAt: String?,
    val notes: String?,
    val createdAt: String,
)

@JsonClass(generateAdapter = true)
data class InvoiceInput(
    val supplierId: String,
    val invoiceNumber: String?,
    val amount: Double,
    val dueDate: String,
    val notes: String?,
)

@JsonClass(generateAdapter = true)
data class UpcomingInvoicesResponse(
    val overdue: List<SupplierInvoiceDto>,
    val dueSoon: List<SupplierInvoiceDto>,
)

@JsonClass(generateAdapter = true)
data class SaleItemInput(val productId: String, val quantity: Double)

@JsonClass(generateAdapter = true)
data class SaleInput(val items: List<SaleItemInput>)

@JsonClass(generateAdapter = true)
data class SaleItemDto(
    val id: String,
    val productId: String,
    val product: ProductDto?,
    val quantity: String,
    val unitPrice: String,
    val unitCost: String,
    val subtotal: String,
)

@JsonClass(generateAdapter = true)
data class SaleDto(
    val id: String,
    val totalAmount: String,
    val totalCost: String,
    val createdAt: String,
    val items: List<SaleItemDto>,
)

@JsonClass(generateAdapter = true)
data class ExpenseDto(
    val id: String,
    val name: String,
    val amount: String,
    val frequency: String,
    val startDate: String,
    val endDate: String?,
    val active: Boolean,
    val notes: String?,
    val createdAt: String,
)

@JsonClass(generateAdapter = true)
data class ExpenseInput(
    val name: String,
    val amount: Double,
    val frequency: String,
    val startDate: String?,
    val endDate: String?,
    val active: Boolean?,
    val notes: String?,
)

@JsonClass(generateAdapter = true)
data class DashboardPeriodDto(val revenue: String, val cost: String, val profit: String)

@JsonClass(generateAdapter = true)
data class RecurringExpensesDto(val dailyRate: String, val monthlyRate: String)

@JsonClass(generateAdapter = true)
data class DashboardAlertsDto(
    val lowStockCount: Int,
    val overdueInvoicesCount: Int,
    val dueSoonInvoicesCount: Int,
)

@JsonClass(generateAdapter = true)
data class DashboardSummaryDto(
    val inventoryValue: String,
    val pendingInvoicesTotal: String,
    val netValuation: String,
    val today: DashboardPeriodDto,
    val month: DashboardPeriodDto,
    val recurringExpenses: RecurringExpensesDto,
    val alerts: DashboardAlertsDto,
)

@JsonClass(generateAdapter = true)
data class TopProductItemDto(
    val productId: String,
    val name: String,
    val quantitySold: Double,
    val revenue: String,
    val cost: String,
    val profit: String,
)

@JsonClass(generateAdapter = true)
data class TopProductsResponse(
    val period: String,
    val from: String,
    val to: String,
    val sortBy: String,
    val items: List<TopProductItemDto>,
)

@JsonClass(generateAdapter = true)
data class MarginItemDto(
    val productId: String,
    val name: String,
    val purchaseCost: String,
    val sellingPrice: String,
    val marginAmount: String,
    @Json(name = "marginPercent") val marginPercent: String,
)

@JsonClass(generateAdapter = true)
data class MarginsResponse(val items: List<MarginItemDto>)

@JsonClass(generateAdapter = true)
data class RevenueBucketDto(val bucket: String, val revenue: String, val cost: String, val profit: String)

@JsonClass(generateAdapter = true)
data class RevenueSeriesResponse(
    val period: String,
    val from: String,
    val to: String,
    val series: List<RevenueBucketDto>,
)

@JsonClass(generateAdapter = true)
data class LowStockAlertDto(val productId: String, val name: String, val quantity: String, val lowStockThreshold: String)

@JsonClass(generateAdapter = true)
data class AlertsResponse(
    val lowStock: List<LowStockAlertDto>,
    val overdueInvoices: List<SupplierInvoiceDto>,
    val dueSoonInvoices: List<SupplierInvoiceDto>,
)

@JsonClass(generateAdapter = true)
data class WorkingDayDto(val date: String, val isWorking: Boolean, val answered: Boolean)

@JsonClass(generateAdapter = true)
data class SetWorkingDayRequest(val isWorking: Boolean)

@JsonClass(generateAdapter = true)
data class RestoreResponse(val success: Boolean, val restoredAt: String)
