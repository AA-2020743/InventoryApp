package com.supermarket.inventory.data.remote

import com.supermarket.inventory.data.remote.dto.*
import okhttp3.MultipartBody
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
    suspend fun markInvoicePaid(@Path("id") id: String): SupplierInvoiceDto

    // Sales
    @GET("api/sales")
    suspend fun getSales(
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("limit") limit: Int? = null,
    ): List<SaleDto>

    @POST("api/sales")
    suspend fun createSale(@Body input: SaleInput): SaleDto

    // Expenses
    @GET("api/expenses")
    suspend fun getExpenses(@Query("activeOnly") activeOnly: Boolean? = null): List<ExpenseDto>

    @POST("api/expenses")
    suspend fun createExpense(@Body input: ExpenseInput): ExpenseDto

    @PUT("api/expenses/{id}")
    suspend fun updateExpense(@Path("id") id: String, @Body input: ExpenseInput): ExpenseDto

    @DELETE("api/expenses/{id}")
    suspend fun deleteExpense(@Path("id") id: String)

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
}
