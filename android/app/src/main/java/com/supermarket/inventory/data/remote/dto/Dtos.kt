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
    val soldByWeight: Boolean,
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
    val soldByWeight: Boolean,
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
    val deficitAmount: String,
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
data class SaleInput(
    val clientId: String?,
    val items: List<SaleItemInput>,
    val paymentStatus: String = "PAID",
    val customerName: String? = null,
    // Set only for a manually-entered deferred sale (items empty) - a total
    // owed by a customer that isn't tied to specific inventory.
    val manualAmount: Double? = null,
)

@JsonClass(generateAdapter = true)
data class SaleEditInput(
    val items: List<SaleItemInput>,
    val customerName: String?,
    val paymentStatus: String?,
)

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
    val paymentStatus: String,
    val customerName: String?,
    val collectedAt: String?,
    val createdAt: String,
    val items: List<SaleItemDto>,
)

@JsonClass(generateAdapter = true)
data class ExpenseDto(
    val id: String,
    val name: String,
    val amount: String,
    val date: String,
    val deficitAmount: String,
    val notes: String?,
    val createdAt: String,
)

@JsonClass(generateAdapter = true)
data class ExpenseInput(
    val name: String,
    val amount: Double,
    val date: String?,
    val notes: String?,
)

@JsonClass(generateAdapter = true)
data class ExpensesForRangeResponse(
    val period: String,
    val date: String,
    val items: List<ExpenseDto>,
    val total: String,
    val deficit: String,
)

@JsonClass(generateAdapter = true)
data class DashboardPeriodDto(
    val revenue: String,
    val cost: String,
    val profit: String,
    val expenses: String,
    val deficit: String,
)

@JsonClass(generateAdapter = true)
data class DashboardAlertsDto(
    val lowStockCount: Int,
    val overdueInvoicesCount: Int,
    val dueSoonInvoicesCount: Int,
)

@JsonClass(generateAdapter = true)
data class DashboardSummaryDto(
    val inventoryValue: String,
    val assetsValue: String,
    val deferredReceivablesTotal: String,
    val cashRegisterBalance: String,
    val pendingInvoicesTotal: String,
    val allTimeDeficitTotal: String,
    val netValuation: String,
    val today: DashboardPeriodDto,
    val month: DashboardPeriodDto,
    val alerts: DashboardAlertsDto,
)

@JsonClass(generateAdapter = true)
data class TopProductItemDto(
    val productId: String,
    val name: String,
    val category: String?,
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
data class AssetDto(
    val id: String,
    val name: String,
    val value: String,
    val category: String?,
    val notes: String?,
    val createdAt: String,
    val updatedAt: String,
)

@JsonClass(generateAdapter = true)
data class AssetInput(
    val name: String,
    val value: Double,
    val category: String?,
    val notes: String?,
)

@JsonClass(generateAdapter = true)
data class CashRegisterEntryDto(
    val id: String,
    val amount: String,
    val note: String?,
    val invoiceId: String?,
    val createdAt: String,
)

@JsonClass(generateAdapter = true)
data class CashRegisterResponse(
    val balance: String,
    val entries: List<CashRegisterEntryDto>,
)

@JsonClass(generateAdapter = true)
data class CashRegisterEntryResponse(
    val balance: String,
    val entry: CashRegisterEntryDto,
)

@JsonClass(generateAdapter = true)
data class SetCashRegisterRequest(val value: Double, val note: String?)

@JsonClass(generateAdapter = true)
data class CashRegisterEntryRequest(val amount: Double, val note: String?)

@JsonClass(generateAdapter = true)
data class RestoreResponse(val success: Boolean, val restoredAt: String)

@JsonClass(generateAdapter = true)
data class SettingsDto(val startingValue: String)

@JsonClass(generateAdapter = true)
data class UpdateSettingsRequest(val startingValue: Double)
