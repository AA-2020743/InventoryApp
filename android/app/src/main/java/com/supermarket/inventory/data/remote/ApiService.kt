package com.supermarket.inventory.data.remote

import com.supermarket.inventory.data.remote.dto.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.*

interface ApiService {

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @POST("api/auth/change-password")
    suspend fun changePassword(@Body request: ChangePasswordRequest)

    // Products
    @GET("api/products")
    suspend fun getProducts(
        @Query("search") search: String? = null,
        @Query("category") category: String? = null,
        @Query("lowStockOnly") lowStockOnly: Boolean? = null,
    ): List<ProductDto>

    @GET("api/products/barcode/{barcode}")
    suspend fun getProductByBarcode(@Path("barcode") barcode: String): ProductDto

    @GET("api/products/categories")
    suspend fun getCategories(): List<String>

    @GET("api/products/{id}")
    suspend fun getProduct(@Path("id") id: String): ProductDto

    @POST("api/products")
    suspend fun createProduct(@Body input: ProductInput): ProductDto

    @PUT("api/products/{id}")
    suspend fun updateProduct(@Path("id") id: String, @Body input: ProductInput): ProductDto

    @DELETE("api/products/{id}")
    suspend fun deleteProduct(@Path("id") id: String)

    @POST("api/products/{id}/restock")
    suspend fun restockProduct(@Path("id") id: String, @Body request: RestockRequest): ProductDto

    @POST("api/products/{id}/adjust")
    suspend fun adjustProduct(@Path("id") id: String, @Body request: AdjustRequest): ProductDto

    @GET("api/products/{id}/transactions")
    suspend fun getProductTransactions(@Path("id") id: String): List<InventoryTransactionDto>

    @Multipart
    @POST("api/uploads/image")
    suspend fun uploadImage(@Part image: MultipartBody.Part): UploadImageResponse

    // Suppliers
    @GET("api/suppliers")
    suspend fun getSuppliers(): List<SupplierDto>

    @POST("api/suppliers")
    suspend fun createSupplier(@Body input: SupplierInput): SupplierDto

    // Invoices
    @GET("api/invoices")
    suspend fun getInvoices(@Query("status") status: String? = null): List<SupplierInvoiceDto>

    @GET("api/invoices/upcoming")
    suspend fun getUpcomingInvoices(@Query("days") days: Int? = null): UpcomingInvoicesResponse

    @POST("api/invoices")
    suspend fun createInvoice(@Body input: InvoiceInput): SupplierInvoiceDto

    @POST("api/invoices/{id}/pay")
    suspend fun markInvoicePaid(@Path("id") id: String, @Body request: PayInvoiceRequest = PayInvoiceRequest()): SupplierInvoiceDto

    // Sales
    @GET("api/sales")
    suspend fun getSales(
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("paymentStatus") paymentStatus: String? = null,
    ): List<SaleDto>

    @GET("api/sales/{id}")
    suspend fun getSale(@Path("id") id: String): SaleDto

    @POST("api/sales")
    suspend fun createSale(@Body input: SaleInput): SaleDto

    @PUT("api/sales/{id}")
    suspend fun updateSale(@Path("id") id: String, @Body input: SaleEditInput): SaleDto

    @DELETE("api/sales/{id}")
    suspend fun deleteSale(@Path("id") id: String)

    @POST("api/sales/{id}/collect")
    suspend fun collectSale(@Path("id") id: String): SaleDto

    // Expenses
    @GET("api/expenses")
    suspend fun getExpenses(@Query("activeOnly") activeOnly: Boolean? = null): List<ExpenseDto>

    @POST("api/expenses")
    suspend fun createExpense(@Body input: ExpenseInput): ExpenseDto

    @PUT("api/expenses/{id}")
    suspend fun updateExpense(@Path("id") id: String, @Body input: ExpenseInput): ExpenseDto

    @DELETE("api/expenses/{id}")
    suspend fun deleteExpense(@Path("id") id: String)

    // Assets
    @GET("api/assets")
    suspend fun getAssets(): List<AssetDto>

    @POST("api/assets")
    suspend fun createAsset(@Body input: AssetInput): AssetDto

    @PUT("api/assets/{id}")
    suspend fun updateAsset(@Path("id") id: String, @Body input: AssetInput): AssetDto

    @DELETE("api/assets/{id}")
    suspend fun deleteAsset(@Path("id") id: String)

    // Cash register
    @GET("api/cash-register")
    suspend fun getCashRegister(): CashRegisterResponse

    @POST("api/cash-register/set")
    suspend fun setCashRegister(@Body request: SetCashRegisterRequest): CashRegisterEntryResponse

    @POST("api/cash-register/entries")
    suspend fun addCashRegisterEntry(@Body request: CashRegisterEntryRequest): CashRegisterEntryResponse

    // Dashboard / stats / alerts
    @GET("api/dashboard/summary")
    suspend fun getDashboardSummary(): DashboardSummaryDto

    @GET("api/stats/top-products")
    suspend fun getTopProducts(
        @Query("period") period: String,
        @Query("date") date: String? = null,
        @Query("sortBy") sortBy: String? = null,
        @Query("limit") limit: Int? = null,
    ): TopProductsResponse

    @GET("api/stats/margins")
    suspend fun getMargins(@Query("limit") limit: Int? = null): MarginsResponse

    @GET("api/stats/revenue")
    suspend fun getRevenueSeries(
        @Query("period") period: String,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
    ): RevenueSeriesResponse

    @GET("api/alerts")
    suspend fun getAlerts(@Query("days") days: Int? = null): AlertsResponse

    @GET("api/workdays/today")
    suspend fun getTodayWorkingDay(): WorkingDayDto

    @POST("api/workdays/today")
    suspend fun setTodayWorkingDay(@Body request: SetWorkingDayRequest): WorkingDayDto

    // Backup / restore
    @Streaming
    @GET("api/backup/export")
    suspend fun exportBackup(): ResponseBody

    @POST("api/backup/restore")
    suspend fun restoreBackup(@Body body: RequestBody): RestoreResponse
}
