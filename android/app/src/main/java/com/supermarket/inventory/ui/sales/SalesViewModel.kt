package com.supermarket.inventory.ui.sales

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.supermarket.inventory.data.ApiResult
import com.supermarket.inventory.data.remote.dto.ProductDto
import com.supermarket.inventory.data.repository.ProductRepository
import com.supermarket.inventory.data.repository.SalesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class CartItem(val product: ProductDto, val quantity: Double) {
    val subtotal: BigDecimal get() = BigDecimal(product.sellingPrice).multiply(BigDecimal(quantity))
}

data class SalesUiState(
    val barcodeInput: String = "",
    val cart: List<CartItem> = emptyList(),
    val isLookingUp: Boolean = false,
    val isCheckingOut: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
) {
    val total: BigDecimal get() = cart.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.subtotal) }
}

@HiltViewModel
class SalesViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val salesRepository: SalesRepository,
) : ViewModel() {

    var uiState by mutableStateOf(SalesUiState())
        private set

    fun onBarcodeInputChange(value: String) {
        uiState = uiState.copy(barcodeInput = value, error = null)
    }

    fun submitBarcode(barcode: String = uiState.barcodeInput) {
        if (barcode.isBlank()) return
        viewModelScope.launch {
            uiState = uiState.copy(isLookingUp = true, error = null, barcodeInput = "")
            when (val result = productRepository.getByBarcode(barcode)) {
                is ApiResult.Success -> addToCart(result.data)
                is ApiResult.Error -> uiState = uiState.copy(isLookingUp = false, error = "NOT_FOUND:$barcode")
            }
        }
    }

    private fun addToCart(product: ProductDto) {
        val existing = uiState.cart.find { it.product.id == product.id }
        val newCart = if (existing != null) {
            uiState.cart.map { if (it.product.id == product.id) it.copy(quantity = it.quantity + 1) else it }
        } else {
            uiState.cart + CartItem(product, 1.0)
        }
        uiState = uiState.copy(cart = newCart, isLookingUp = false)
    }

    fun updateQuantity(productId: String, quantity: Double) {
        if (quantity <= 0) {
            removeItem(productId)
            return
        }
        uiState = uiState.copy(cart = uiState.cart.map { if (it.product.id == productId) it.copy(quantity = quantity) else it })
    }

    fun removeItem(productId: String) {
        uiState = uiState.copy(cart = uiState.cart.filterNot { it.product.id == productId })
    }

    fun dismissMessages() {
        uiState = uiState.copy(error = null, successMessage = null)
    }

    fun checkout() {
        if (uiState.cart.isEmpty()) return
        viewModelScope.launch {
            uiState = uiState.copy(isCheckingOut = true, error = null)
            val items = uiState.cart.map { it.product.id to it.quantity }
            when (val result = salesRepository.createSale(items)) {
                is ApiResult.Success -> uiState = SalesUiState(successMessage = "Sale completed")
                is ApiResult.Error -> uiState = uiState.copy(isCheckingOut = false, error = result.message)
            }
        }
    }
}
