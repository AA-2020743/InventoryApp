package com.supermarket.inventory.ui.cashregister

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.supermarket.inventory.data.ApiResult
import com.supermarket.inventory.data.remote.dto.CashRegisterEntryDto
import com.supermarket.inventory.data.repository.CashRegisterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CashRegisterUiState(
    val isLoading: Boolean = true,
    val balance: String = "0",
    val entries: List<CashRegisterEntryDto> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class CashRegisterViewModel @Inject constructor(
    private val repository: CashRegisterRepository,
) : ViewModel() {

    var uiState by mutableStateOf(CashRegisterUiState())
        private set

    init { load() }

    fun load() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null)
            when (val result = repository.getRegister()) {
                is ApiResult.Success -> uiState = uiState.copy(isLoading = false, balance = result.data.balance, entries = result.data.entries)
                is ApiResult.Error -> uiState = uiState.copy(isLoading = false, error = result.message)
            }
        }
    }

    fun setBalance(value: Double, note: String?) {
        viewModelScope.launch {
            when (repository.setBalance(value, note)) {
                is ApiResult.Success -> load()
                is ApiResult.Error -> Unit
            }
        }
    }

    fun deleteEntry(id: String) {
        viewModelScope.launch {
            when (val result = repository.deleteEntry(id)) {
                is ApiResult.Success -> load()
                is ApiResult.Error -> uiState = uiState.copy(error = result.message)
            }
        }
    }

    fun addEntry(amount: Double, note: String?) {
        viewModelScope.launch {
            when (repository.addEntry(amount, note)) {
                is ApiResult.Success -> load()
                is ApiResult.Error -> Unit
            }
        }
    }
}
