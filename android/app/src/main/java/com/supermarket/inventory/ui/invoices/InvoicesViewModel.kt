package com.supermarket.inventory.ui.invoices

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.supermarket.inventory.data.ApiResult
import com.supermarket.inventory.data.SessionManager
import com.supermarket.inventory.data.remote.dto.SupplierInvoiceDto
import com.supermarket.inventory.data.remote.dto.UploadImageResponse
import com.supermarket.inventory.data.repository.InvoiceRepository
import com.supermarket.inventory.data.repository.SupplierRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class InvoicesUiState(
    val isLoading: Boolean = true,
    val invoices: List<SupplierInvoiceDto> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class InvoicesViewModel @Inject constructor(
    private val invoiceRepository: InvoiceRepository,
    private val supplierRepository: SupplierRepository,
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
        }
    }

    fun markPaid(id: String) {
        viewModelScope.launch {
            when (invoiceRepository.markPaid(id)) {
                is ApiResult.Success -> load()
                is ApiResult.Error -> Unit
            }
        }
    }

    suspend fun loadSuppliers() = supplierRepository.getSuppliers()

    suspend fun createSupplier(name: String, contactInfo: String?) = supplierRepository.createSupplier(name, contactInfo)

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
