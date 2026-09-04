package com.supermarket.inventory.ui.invoices

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.supermarket.inventory.data.ApiResult
import com.supermarket.inventory.data.SessionManager
import com.supermarket.inventory.data.remote.dto.InvoiceInventoryResponse
import com.supermarket.inventory.data.remote.dto.InvoiceLineInput
import com.supermarket.inventory.data.remote.dto.ProductDto
import com.supermarket.inventory.data.remote.dto.SupplierInvoiceDto
import com.supermarket.inventory.data.remote.dto.UploadImageResponse
import com.supermarket.inventory.data.repository.InvoiceRepository
import com.supermarket.inventory.data.repository.ProductRepository
import com.supermarket.inventory.data.repository.SupplierRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class InvoicesUiState(
    val isLoading: Boolean = true,
    val invoices: List<SupplierInvoiceDto> = emptyList(),
    val error: String? = null,
    // Set while the linked-stock sheet is open for one invoice.
    val linkedInventory: InvoiceInventoryResponse? = null,
    val linkedInventoryLoading: Boolean = false,
    val linkedInventoryError: String? = null,
    // Products offered in the "add stock to this invoice" picker.
    val products: List<ProductDto> = emptyList(),
)

@HiltViewModel
class InvoicesViewModel @Inject constructor(
    private val invoiceRepository: InvoiceRepository,
    private val supplierRepository: SupplierRepository,
    private val productRepository: ProductRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    var uiState by mutableStateOf(InvoicesUiState())
        private set

    fun fullImageUrl(path: String?): String? =
        path?.let { if (it.startsWith("http")) it else sessionManager.serverUrl.value + it }

    suspend fun uploadImage(file: File): ApiResult<UploadImageResponse> = invoiceRepository.uploadImage(file)

    init { load() }

    fun load() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null)
            when (val result = invoiceRepository.getInvoices()) {
                is ApiResult.Success -> uiState = uiState.copy(isLoading = false, invoices = result.data)
                is ApiResult.Error -> uiState = uiState.copy(isLoading = false, error = result.message)
            }
            // Needed by the purchase dialog's product picker, so it's ready
            // before the dialog opens rather than after.
            when (val productsResult = productRepository.getProducts()) {
                is ApiResult.Success -> uiState = uiState.copy(products = productsResult.data)
                is ApiResult.Error -> Unit
            }
        }
    }

    // Reverses a payment recorded by mistake - the till gets its money back
    // and the invoice returns to pending.
    fun markUnpaid(id: String) {
        viewModelScope.launch {
            when (val result = invoiceRepository.markUnpaid(id)) {
                is ApiResult.Success -> load()
                is ApiResult.Error -> uiState = uiState.copy(error = result.message)
            }
        }
    }

    fun markPaid(id: String) {
        viewModelScope.launch {
            when (val result = invoiceRepository.markPaid(id)) {
                is ApiResult.Success -> load()
                is ApiResult.Error -> uiState = uiState.copy(error = result.message)
            }
        }
    }

    // Corrects a stock line on this invoice: `quantity` is the line's new
    // total. Inventory follows the change; the till is untouched, since
    // invoiced stock was never paid for from it. Returns null on success.
    suspend fun updateLinkedStock(invoiceId: String, transactionId: String, quantity: Double): String? =
        when (val result = invoiceRepository.updateStockLine(invoiceId, transactionId, quantity)) {
            is ApiResult.Success -> { openLinkedInventory(invoiceId); load(); null }
            is ApiResult.Error -> result.message
        }

    // Takes the line off the invoice and its units back out of inventory.
    suspend fun removeLinkedStock(invoiceId: String, transactionId: String): String? =
        when (val result = invoiceRepository.deleteStockLine(invoiceId, transactionId)) {
            is ApiResult.Success -> { openLinkedInventory(invoiceId); load(); null }
            is ApiResult.Error -> result.message
        }

    fun dismissError() {
        uiState = uiState.copy(error = null)
    }

    // Opens the linked-stock view for one invoice: what this invoice
    // actually paid for. Also pulls the product list so stock can be added
    // to the invoice from the same place.
    fun openLinkedInventory(invoiceId: String) {
        viewModelScope.launch {
            uiState = uiState.copy(linkedInventoryLoading = true, linkedInventoryError = null, linkedInventory = null)
            when (val result = invoiceRepository.getInventory(invoiceId)) {
                is ApiResult.Success ->
                    uiState = uiState.copy(linkedInventoryLoading = false, linkedInventory = result.data)
                is ApiResult.Error ->
                    uiState = uiState.copy(linkedInventoryLoading = false, linkedInventoryError = result.message)
            }
            when (val productsResult = productRepository.getProducts()) {
                is ApiResult.Success -> uiState = uiState.copy(products = productsResult.data)
                is ApiResult.Error -> Unit
            }
        }
    }

    fun closeLinkedInventory() {
        uiState = uiState.copy(linkedInventory = null, linkedInventoryError = null, linkedInventoryLoading = false)
    }

    // Books stock against the invoice: a DEFERRED restock, so the till is
    // untouched - the invoice's own PENDING -> PAID lifecycle governs cash.
    // Returns the error message on failure, null on success.
    suspend fun addStockToInvoice(
        invoiceId: String,
        productId: String,
        quantity: Double,
        unitCost: Double?,
    ): String? {
        val result = productRepository.restock(
            id = productId,
            quantity = quantity,
            unitCost = unitCost,
            note = null,
            financing = "DEFERRED",
            supplierInvoiceId = invoiceId,
        )
        return when (result) {
            is ApiResult.Success -> {
                openLinkedInventory(invoiceId)
                load()
                null
            }
            is ApiResult.Error -> result.message
        }
    }

    suspend fun loadSuppliers() = supplierRepository.getSuppliers()

    suspend fun createSupplier(name: String, contactInfo: String?) = supplierRepository.createSupplier(name, contactInfo)

    // Records a purchase: the invoice and the stock it delivered, totalled
    // from its lines. "CASH" settles it from the till immediately.
    suspend fun createPurchase(
        supplierId: String,
        invoiceNumber: String?,
        dueDateIso: String,
        paymentMethod: String,
        lines: List<InvoiceLineInput>,
        amount: Double?,
        imageUrl: String?,
    ) = invoiceRepository.createPurchase(supplierId, invoiceNumber, dueDateIso, paymentMethod, lines, amount, imageUrl)

    suspend fun createInvoice(supplierId: String, invoiceNumber: String?, amount: Double, dueDateIso: String, notes: String?, imageUrl: String?) =
        invoiceRepository.createInvoice(supplierId, invoiceNumber, amount, dueDateIso, notes, imageUrl)

    suspend fun updateInvoice(id: String, supplierId: String, invoiceNumber: String?, amount: Double, dueDateIso: String, notes: String?, imageUrl: String?) =
        invoiceRepository.updateInvoice(id, supplierId, invoiceNumber, amount, dueDateIso, notes, imageUrl)

    fun deleteInvoice(id: String) {
        viewModelScope.launch {
            when (invoiceRepository.deleteInvoice(id)) {
                is ApiResult.Success -> load()
                is ApiResult.Error -> Unit
            }
        }
    }
}
