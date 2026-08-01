package com.supermarket.inventory.ui.inventory

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.supermarket.inventory.data.ApiResult
import com.supermarket.inventory.data.SessionManager
import com.supermarket.inventory.data.remote.dto.ProductDto
import com.supermarket.inventory.data.remote.dto.ProductInput
import com.supermarket.inventory.data.repository.OpenFoodFactsRepository
import com.supermarket.inventory.data.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class ThresholdMode { UNITS, PACKAGES }

data class ProductFormUiState(
    val productId: String? = null,
    val barcode: String = "",
    val name: String = "",
    val imageUrl: String? = null,
    val category: String = "",
    val availableCategories: List<String> = emptyList(),
    val unit: String = "pcs",
    val purchaseCost: String = "",
    val sellingPrice: String = "",
    // Packaging is just "how many units come in one package" - no name/type
    // is asked for, only whether it applies and the count.
    val isPackaged: Boolean = false,
    val unitsPerPackage: String = "1",
    val packagesOnHand: String = "", // used to seed the computed quantity while creating
    // Always user-editable (e.g. to correct for a partially-full pallet),
    // auto-filled from packages x unitsPerPackage as a starting point only
    // while creating a packaged product.
    val quantity: String = "",
    val originalQuantity: String = "", // snapshot at load time, to compute a delta on save when editing
    val thresholdMode: ThresholdMode = ThresholdMode.UNITS,
    val thresholdPackages: String = "",
    val lowStockThreshold: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isUploadingImage: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
) {
    val unitsPerPackageValue: Double get() = unitsPerPackage.toDoubleOrNull()?.takeIf { it > 0 } ?: 1.0
}

@HiltViewModel
class ProductFormViewModel @Inject constructor(
    private val repository: ProductRepository,
    private val sessionManager: SessionManager,
    private val openFoodFactsRepository: OpenFoodFactsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    var uiState by mutableStateOf(ProductFormUiState())
        private set

    init {
        loadCategories()
        val productId = savedStateHandle.get<String>("productId")
        val initialBarcode = savedStateHandle.get<String>("barcode")
        if (productId != null) {
            uiState = uiState.copy(productId = productId, isLoading = true)
            loadProduct(productId)
        } else if (!initialBarcode.isNullOrBlank()) {
            uiState = uiState.copy(barcode = initialBarcode)
            tryAutoFill(initialBarcode)
        }
    }

    private fun loadCategories() {
        viewModelScope.launch {
            when (val result = repository.getCategories()) {
                is ApiResult.Success -> uiState = uiState.copy(availableCategories = result.data)
                is ApiResult.Error -> Unit // non-essential; category field still works as free text
            }
        }
    }

    // Best-effort prefill from Open Food Facts for a brand-new product;
    // silently does nothing if the barcode isn't found there (common for
    // local/loose goods) - manual entry remains the primary path.
    private fun tryAutoFill(barcode: String) {
        viewModelScope.launch {
            val product = openFoodFactsRepository.lookup(barcode) ?: return@launch
            applyAutoFill(product.productName, product.imageFrontUrl, product.categories)
        }
    }

    fun fullImageUrl(path: String? = uiState.imageUrl): String? =
        path?.let { if (it.startsWith("http")) it else sessionManager.serverUrl.value + it }

    private fun loadProduct(id: String) {
        viewModelScope.launch {
            when (val result = repository.getProduct(id)) {
                is ApiResult.Success -> uiState = result.data.toUiState(uiState.availableCategories)
                is ApiResult.Error -> uiState = uiState.copy(isLoading = false, error = result.message)
            }
        }
    }

    private fun ProductDto.toUiState(availableCategories: List<String>): ProductFormUiState {
        val perPackage = unitsPerPackage.toDoubleOrNull()?.takeIf { it > 0 } ?: 1.0
        val thresholdUnits = lowStockThreshold.toDoubleOrNull() ?: 0.0
        val isPackaged = perPackage > 1.0
        return ProductFormUiState(
            productId = id,
            barcode = barcode.orEmpty(),
            name = name,
            imageUrl = imageUrl,
            category = category.orEmpty(),
            availableCategories = availableCategories,
            unit = unit,
            purchaseCost = purchaseCost,
            sellingPrice = sellingPrice,
            isPackaged = isPackaged,
            unitsPerPackage = unitsPerPackage,
            quantity = quantity,
            originalQuantity = quantity,
            lowStockThreshold = lowStockThreshold,
            thresholdPackages = if (isPackaged) (thresholdUnits / perPackage).toCleanString() else "",
            isLoading = false,
        )
    }

    fun onBarcodeChange(v: String) { uiState = uiState.copy(barcode = v, error = null) }
    fun onNameChange(v: String) { uiState = uiState.copy(name = v, error = null) }
    fun onCategoryChange(v: String) { uiState = uiState.copy(category = v) }
    fun onUnitChange(v: String) { uiState = uiState.copy(unit = v) }
    fun onIsPackagedChange(v: Boolean) {
        uiState = uiState.copy(isPackaged = v, error = null)
        recomputeQuantityPreview()
    }
    fun onUnitsPerPackageChange(v: String) {
        uiState = uiState.copy(unitsPerPackage = v, error = null)
        recomputeQuantityPreview()
    }
    fun onPackagesOnHandChange(v: String) {
        uiState = uiState.copy(packagesOnHand = v, error = null)
        recomputeQuantityPreview()
    }

    // Only a starting-point convenience while creating a new packaged
    // product; the quantity field always stays directly editable so the
    // owner can correct for a partially-full last pallet, etc.
    private fun recomputeQuantityPreview() {
        if (uiState.productId == null && uiState.isPackaged) {
            val packages = uiState.packagesOnHand.toDoubleOrNull() ?: 0.0
            uiState = uiState.copy(quantity = (packages * uiState.unitsPerPackageValue).toCleanString())
        }
    }
    fun onPurchaseCostChange(v: String) { uiState = uiState.copy(purchaseCost = v, error = null) }
    fun onSellingPriceChange(v: String) { uiState = uiState.copy(sellingPrice = v, error = null) }
    fun onQuantityChange(v: String) { uiState = uiState.copy(quantity = v, error = null) }
    fun onThresholdModeChange(mode: ThresholdMode) { uiState = uiState.copy(thresholdMode = mode) }
    fun onThresholdPackagesChange(v: String) { uiState = uiState.copy(thresholdPackages = v) }
    fun onLowStockThresholdChange(v: String) { uiState = uiState.copy(lowStockThreshold = v) }
    fun onBarcodeScanned(v: String) {
        uiState = uiState.copy(barcode = v, error = null)
        if (uiState.productId == null) tryAutoFill(v)
    }

    fun uploadImage(file: File) {
        viewModelScope.launch {
            uiState = uiState.copy(isUploadingImage = true)
            when (val result = repository.uploadImage(file)) {
                is ApiResult.Success -> uiState = uiState.copy(isUploadingImage = false, imageUrl = result.data.url)
                is ApiResult.Error -> uiState = uiState.copy(isUploadingImage = false, error = result.message)
            }
        }
    }

    /** Prefills name/photo/category from a best-effort external lookup (e.g. Open Food Facts). */
    fun applyAutoFill(name: String?, imageUrl: String?, category: String?) {
        uiState = uiState.copy(
            name = name?.takeIf { uiState.name.isBlank() } ?: uiState.name,
            imageUrl = imageUrl ?: uiState.imageUrl,
            category = category?.takeIf { uiState.category.isBlank() } ?: uiState.category,
        )
    }

    fun save() {
        val cost = uiState.purchaseCost.toDoubleOrNull()
        val price = uiState.sellingPrice.toDoubleOrNull()
        val perPackage = uiState.unitsPerPackageValue
        val isEditing = uiState.productId != null

        if (uiState.name.isBlank()) {
            uiState = uiState.copy(error = "Name is required"); return
        }
        if (cost == null || price == null) {
            uiState = uiState.copy(error = "Purchase cost and selling price must be numbers"); return
        }

        val enteredQuantity = uiState.quantity.toDoubleOrNull() ?: 0.0
        val originalQuantity = uiState.originalQuantity.toDoubleOrNull() ?: 0.0
        // While editing, the plain update never changes quantity directly -
        // any difference is applied afterwards via adjust() so it lands in
        // the inventory audit trail instead of silently overwriting stock.
        val quantityForUpdate = if (isEditing) originalQuantity else enteredQuantity

        val threshold = if (uiState.isPackaged && uiState.thresholdMode == ThresholdMode.PACKAGES) {
            (uiState.thresholdPackages.toDoubleOrNull() ?: 0.0) * perPackage
        } else {
            uiState.lowStockThreshold.toDoubleOrNull() ?: 0.0
        }

        viewModelScope.launch {
            uiState = uiState.copy(isSaving = true, error = null)
            val input = ProductInput(
                barcode = uiState.barcode.ifBlank { null },
                name = uiState.name,
                imageUrl = uiState.imageUrl,
                category = uiState.category.ifBlank { null },
                unit = uiState.unit.ifBlank { "pcs" },
                unitsPerPackage = perPackage,
                purchaseCost = cost,
                sellingPrice = price,
                quantity = quantityForUpdate,
                lowStockThreshold = threshold,
            )
            val result = if (isEditing) {
                repository.update(uiState.productId!!, input)
            } else {
                repository.create(input)
            }
            when (result) {
                is ApiResult.Success -> {
                    val delta = enteredQuantity - originalQuantity
                    if (isEditing && delta != 0.0) {
                        when (val adjustResult = repository.adjust(uiState.productId!!, delta, "Manual correction via edit form")) {
                            is ApiResult.Success -> uiState = uiState.copy(isSaving = false, saved = true)
                            is ApiResult.Error -> uiState = uiState.copy(isSaving = false, error = adjustResult.message)
                        }
                    } else {
                        uiState = uiState.copy(isSaving = false, saved = true)
                    }
                }
                is ApiResult.Error -> uiState = uiState.copy(isSaving = false, error = result.message)
            }
        }
    }

    fun restock(quantity: Double, unitCost: Double?) {
        val id = uiState.productId ?: return
        viewModelScope.launch {
            uiState = uiState.copy(isSaving = true)
            when (val result = repository.restock(id, quantity, unitCost, "Restock")) {
                is ApiResult.Success -> uiState = result.data.toUiState(uiState.availableCategories)
                is ApiResult.Error -> uiState = uiState.copy(isSaving = false, error = result.message)
            }
        }
    }

    fun adjust(quantityChange: Double, reason: String) {
        val id = uiState.productId ?: return
        viewModelScope.launch {
            uiState = uiState.copy(isSaving = true)
            when (val result = repository.adjust(id, quantityChange, reason)) {
                is ApiResult.Success -> uiState = result.data.toUiState(uiState.availableCategories)
                is ApiResult.Error -> uiState = uiState.copy(isSaving = false, error = result.message)
            }
        }
    }

    // Soft-deletes (marks inactive) rather than a hard delete - past sales
    // and inventory-transaction records still reference this product, and
    // removing the row outright would corrupt that history. From the
    // owner's point of view it disappears from inventory/search either way.
    fun delete() {
        val id = uiState.productId ?: return
        viewModelScope.launch {
            uiState = uiState.copy(isSaving = true, error = null)
            when (val result = repository.delete(id)) {
                is ApiResult.Success -> uiState = uiState.copy(isSaving = false, saved = true)
                is ApiResult.Error -> uiState = uiState.copy(isSaving = false, error = result.message)
            }
        }
    }
}

private fun Double.toCleanString(): String =
    if (this == this.toLong().toDouble()) this.toLong().toString() else this.toString()
