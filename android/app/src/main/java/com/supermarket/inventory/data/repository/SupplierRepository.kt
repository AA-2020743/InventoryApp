package com.supermarket.inventory.data.repository

import com.supermarket.inventory.data.ApiResult
import com.supermarket.inventory.data.apiCall
import com.supermarket.inventory.data.remote.ApiService
import com.supermarket.inventory.data.remote.dto.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupplierRepository @Inject constructor(private val api: ApiService) {
    suspend fun getSuppliers(): ApiResult<List<SupplierDto>> = apiCall { api.getSuppliers() }

    suspend fun createSupplier(name: String, contactInfo: String?): ApiResult<SupplierDto> =
        apiCall { api.createSupplier(SupplierInput(name, contactInfo)) }

    suspend fun updateSupplier(id: String, name: String, contactInfo: String?): ApiResult<SupplierDto> =
        apiCall { api.updateSupplier(id, SupplierInput(name, contactInfo)) }

    suspend fun deleteSupplier(id: String): ApiResult<Unit> = apiCall { api.deleteSupplier(id) }
}

@Singleton
class InvoiceRepository @Inject constructor(private val api: ApiService) {
    suspend fun getInvoices(status: String? = null): ApiResult<List<SupplierInvoiceDto>> =
        apiCall { api.getInvoices(status) }

    suspend fun getUpcoming(days: Int? = null): ApiResult<UpcomingInvoicesResponse> =
        apiCall { api.getUpcomingInvoices(days) }

    suspend fun createInvoice(
        supplierId: String,
        invoiceNumber: String?,
        amount: Double,
        dueDateIso: String,
        notes: String?,
        imageUrl: String? = null,
    ): ApiResult<SupplierInvoiceDto> =
        apiCall { api.createInvoice(InvoiceInput(supplierId, invoiceNumber, amount, dueDateIso, notes, imageUrl)) }

    suspend fun updateInvoice(
        id: String,
        supplierId: String,
        invoiceNumber: String?,
        amount: Double,
        dueDateIso: String,
        notes: String?,
        imageUrl: String? = null,
    ): ApiResult<SupplierInvoiceDto> =
        apiCall { api.updateInvoice(id, InvoiceInput(supplierId, invoiceNumber, amount, dueDateIso, notes, imageUrl)) }

    // The stock booked against this invoice - what it actually paid for.
    suspend fun getInventory(id: String): ApiResult<InvoiceInventoryResponse> =
        apiCall { api.getInvoiceInventory(id) }

    suspend fun markPaid(id: String): ApiResult<SupplierInvoiceDto> =
        apiCall { api.markInvoicePaid(id) }

    // Undoes a payment made by mistake: the money goes back in the till and
    // the invoice returns to pending.
    suspend fun markUnpaid(id: String): ApiResult<SupplierInvoiceDto> =
        apiCall { api.markInvoiceUnpaid(id) }

    suspend fun deleteInvoice(id: String): ApiResult<Unit> = apiCall { api.deleteInvoice(id) }

    suspend fun uploadImage(file: File): ApiResult<UploadImageResponse> = apiCall {
        val body = file.asRequestBody("image/*".toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("image", file.name, body)
        api.uploadImage(part)
    }
}
