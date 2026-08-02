package com.supermarket.inventory.ui.invoices

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.supermarket.inventory.data.ApiResult
import com.supermarket.inventory.data.remote.dto.SupplierInvoiceDto
import com.supermarket.inventory.data.repository.InvoiceRepository
import com.supermarket.inventory.data.repository.SupplierRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
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
) : ViewModel() {

    var uiState by mutableStateOf(InvoicesUiState())
        private set

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

    fun markPaid(id: String, payFromCashRegister: Boolean = false) {
        viewModelScope.launch {
            when (invoiceRepository.markPaid(id, payFromCashRegister)) {
                is ApiResult.Success -> load()
                is ApiResult.Error -> Unit
            }
        }
    }

    suspend fun loadSuppliers() = supplierRepository.getSuppliers()

    suspend fun createInvoice(supplierId: String, invoiceNumber: String?, amount: Double, dueDateIso: String, notes: String?) =
        invoiceRepository.createInvoice(supplierId, invoiceNumber, amount, dueDateIso, notes)

    suspend fun updateInvoice(id: String, supplierId: String, invoiceNumber: String?, amount: Double, dueDateIso: String, notes: String?) =
        invoiceRepository.updateInvoice(id, supplierId, invoiceNumber, amount, dueDateIso, notes)

    fun deleteInvoice(id: String) {
        viewModelScope.launch {
            when (invoiceRepository.deleteInvoice(id)) {
                is ApiResult.Success -> load()
                is ApiResult.Error -> Unit
            }
        }
    }
}
