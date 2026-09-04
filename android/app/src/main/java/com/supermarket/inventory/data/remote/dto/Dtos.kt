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

// Details for creating a brand-new supplier invoice inline while restocking
// on deferred payment, instead of picking an existing pending one - the
// backend creates it in the same transaction and links it to the restock.
@JsonClass(generateAdapter = true)
data class NewInvoiceForRestockInput(
    val supplierId: String,
    val invoiceNumber: String? = null,
    val dueDate: String,
    val notes: String? = null,
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
    // Only meaningful when quantity > 0 (there's initial stock to pay for).
    // "CASH" deducts from the till immediately (rejected if it can't cover
    // the cost); "DEFERRED" skips the till and links the stock to a
    // supplier invoice via supplierInvoiceId or newInvoice instead.
    val financing: String? = null,
    val supplierInvoiceId: String? = null,
    val newInvoice: NewInvoiceForRestockInput? = null,
)

@JsonClass(generateAdapter = true)
data class RestockRequest(
    val quantity: Double,
    val unitCost: Double?,
    val note: String?,
    val financing: String? = null,
    val supplierInvoiceId: String? = null,
    val newInvoice: NewInvoiceForRestockInput? = null,
)

@JsonClass(generateAdapter = true)
data class AdjustRequest(val quantityChange: Double, val note: String)

@JsonClass(generateAdapter = true)
data class SpoilRequest(val quantity: Double, val notes: String?)

@JsonClass(generateAdapter = true)
data class SpoilResponse(val product: ProductDto, val expense: ExpenseDto)

@JsonClass(generateAdapter = true)
data class InventoryTransactionDto(
    val id: String,
    val productId: String,
    val type: String,
    val quantityChange: String,
    val unitCost: String?,
    val note: String?,
    val createdAt: String,
    // Null means this stock was paid for in cash; set means it sits on a
    // supplier invoice. Drives the financing badge and the correction.
    val supplierInvoiceId: String? = null,
    val supplierInvoice: SupplierInvoiceDto? = null,
)

// Result of putting a mistaken spoilage back. expenseReversed is false when
// the write-off couldn't be identified unambiguously (an old spoilage from
// before write-offs were linked), meaning the stock returned but the expense
// still needs removing by hand.
@JsonClass(generateAdapter = true)
data class UndoSpoilageResponse(
    val product: ProductDto,
    val quantityRestored: String,
    val expenseReversed: Boolean,
)

// Corrects how an already-recorded restock was paid for. The goods are not
// in question - only the cash/invoice side is undone and re-applied.
@JsonClass(generateAdapter = true)
data class RefinanceRequest(
    val financing: String,
    val supplierInvoiceId: String? = null,
    val newInvoice: NewInvoiceForRestockInput? = null,
)

@JsonClass(generateAdapter = true)
data class RefinanceResponse(
    val transaction: InventoryTransactionDto,
    val previousFinancing: String,
    val financing: String,
    // Set when the correction left a pending invoice covering no stock at
    // all, so the app can point the owner at it to review.
    val orphanedInvoiceId: String? = null,
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
    val imageUrl: String?,
    val createdAt: String,
    // True when the amount is the sum of the stock lines booked against
    // this invoice, rather than a figure typed on an older invoice.
    val amountFromLines: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class InvoiceInput(
    val supplierId: String,
    val invoiceNumber: String?,
    val amount: Double,
    val dueDate: String,
    val notes: String?,
    val imageUrl: String? = null,
)

// One product being purchased on a new invoice.
@JsonClass(generateAdapter = true)
data class InvoiceLineInput(
    val productId: String,
    val quantity: Double,
    val unitCost: Double,
)

// Recording a purchase: the invoice is the document and its lines are the
// stock it brought in, so the total is their sum rather than a typed figure.
// paymentMethod decides where the money comes from - CASH settles it against
// the till immediately, DEFERRED leaves it owed until marked paid.
@JsonClass(generateAdapter = true)
data class InvoicePurchaseInput(
    val supplierId: String,
    val invoiceNumber: String?,
    val dueDate: String,
    val paymentMethod: String,
    val lines: List<InvoiceLineInput>,
    // Only for an invoice that isn't for stock at all - with lines present
    // the server ignores it and uses their sum.
    val amount: Double? = null,
    val notes: String? = null,
    val imageUrl: String? = null,
)

// One RESTOCK booked against a supplier invoice, with the product it added
// stock to. lineCost (quantity x unitCost) is computed client-side.
@JsonClass(generateAdapter = true)
data class InvoiceInventoryItemDto(
    val id: String,
    val productId: String,
    val product: ProductDto?,
    val quantityChange: String,
    val unitCost: String?,
    val note: String?,
    val createdAt: String,
)

// The stock a supplier invoice paid for. linkedTotal is the summed cost of
// the lines below, so the app can flag when it doesn't match the invoice's
// own amount (stock booked but not fully accounted for, or vice versa).
@JsonClass(generateAdapter = true)
data class InvoiceInventoryResponse(
    val invoiceId: String,
    val invoiceAmount: String,
    val items: List<InvoiceInventoryItemDto>,
    val linkedTotal: String,
)

// Corrects a stock line already booked against an invoice. Quantity is the
// new total for the line, not a delta. No cash moves either way - stock on
// an invoice was never paid for from the till.
@JsonClass(generateAdapter = true)
data class LinkedStockInput(
    val quantity: Double,
    val unitCost: Double? = null,
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
    val amountCollected: String = "0",
)

@JsonClass(generateAdapter = true)
data class SalesForRangeResponse(
    val period: String,
    val date: String,
    val items: List<SaleDto>,
)

@JsonClass(generateAdapter = true)
data class CollectPartialRequest(val amount: Double)

@JsonClass(generateAdapter = true)
data class ExpenseDto(
    val id: String,
    val name: String,
    val amount: String,
    val category: String?,
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

// One category's share of a period's expenses or other sales. `category` is
// null for entries that were never given one - the client renders that as a
// localized "Uncategorized" label rather than the server picking English.
@JsonClass(generateAdapter = true)
data class CategoryTotalDto(
    val category: String?,
    val total: String,
    val count: Int,
)

@JsonClass(generateAdapter = true)
data class ExpensesForRangeResponse(
    val period: String,
    val date: String,
    val items: List<ExpenseDto>,
    val total: String,
    val deficit: String,
    val byCategory: List<CategoryTotalDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class DashboardPeriodDto(
    val revenue: String,
    val cost: String,
    val profit: String,
    val expenses: String,
    val deficit: String,
    // Share of `revenue` that came from miscellaneous income entries rather
    // than checkout sales. Defaulted so an older server that doesn't send it
    // still parses.
    val otherSales: String = "0",
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
    val debtReceivableTotal: String,
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
data class DebtDto(
    val id: String,
    val workerName: String,
    val amount: String,
    val amountRepaid: String,
    val status: String,
    val deficitAmount: String,
    val notes: String?,
    val repaidAt: String?,
    val createdAt: String,
)

@JsonClass(generateAdapter = true)
data class DebtInput(
    val workerName: String,
    val amount: Double,
    val notes: String? = null,
)

@JsonClass(generateAdapter = true)
data class DebtRepayPartialRequest(val amount: Double)

@JsonClass(generateAdapter = true)
data class CashRegisterEntryDto(
    val id: String,
    val amount: String,
    val note: String?,
    val invoiceId: String?,
    val createdAt: String,
    // An entry created for another record (an expense, sale, invoice,
    // restock, other sale or debt) is that record's business - it can only
    // be corrected through it. Only a hand-made entry can be removed here,
    // which is what these let the app work out.
    val expenseId: String? = null,
    val saleId: String? = null,
    val inventoryTransactionId: String? = null,
    val otherSaleId: String? = null,
    val debtId: String? = null,
) {
    val isManual: Boolean
        get() = invoiceId == null && expenseId == null && saleId == null &&
            inventoryTransactionId == null && otherSaleId == null && debtId == null
}

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

@JsonClass(generateAdapter = true)
data class OtherSaleDto(
    val id: String,
    val amount: String,
    val category: String?,
    val notes: String?,
    val date: String,
    val createdAt: String,
)

@JsonClass(generateAdapter = true)
data class OtherSaleInput(
    val amount: Double,
    val category: String?,
    val notes: String?,
    val date: String?,
)

@JsonClass(generateAdapter = true)
data class OtherSalesForRangeResponse(
    val period: String,
    val date: String,
    val items: List<OtherSaleDto>,
    val total: String,
    val byCategory: List<CategoryTotalDto> = emptyList(),
)
