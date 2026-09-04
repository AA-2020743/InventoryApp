package com.supermarket.inventory.ui.inventory

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.supermarket.inventory.R
import com.supermarket.inventory.data.remote.dto.SupplierDto
import com.supermarket.inventory.data.remote.dto.SupplierInvoiceDto
import com.supermarket.inventory.ui.common.copyUriToCacheFile
import com.supermarket.inventory.ui.common.formatAmount
import com.supermarket.inventory.ui.common.formatIsoDate
import java.time.Instant
import java.time.ZoneOffset
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Divider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import com.supermarket.inventory.ui.common.formatQuantity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductFormScreen(
    onScanBarcode: () -> Unit,
    scannedBarcode: String?,
    onScannedBarcodeConsumed: () -> Unit,
    onDone: () -> Unit,
    viewModel: ProductFormViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState
    val context = LocalContext.current

    LaunchedEffect(scannedBarcode) {
        if (scannedBarcode != null) {
            viewModel.onBarcodeScanned(scannedBarcode)
            onScannedBarcodeConsumed()
        }
    }

    LaunchedEffect(state.saved) {
        if (state.saved) onDone()
    }

    var showRestockDialog by remember { mutableStateOf(false) }
    var showAdjustDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val file = copyUriToCacheFile(context, uri)
            if (file != null) viewModel.uploadImage(file)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.productId != null) stringResource(R.string.action_edit) else stringResource(R.string.inventory_add_product)) },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.Filled.ArrowBack, contentDescription = null) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            if (state.isLoading) {
                CircularProgressIndicator()
                return@Column
            }

            if (state.imageUrl != null) {
                AsyncImage(
                    model = viewModel.fullImageUrl(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)),
                )
                Spacer(Modifier.height(8.dp))
            }
            OutlinedButton(onClick = { imagePicker.launch("image/*") }, enabled = !state.isUploadingImage) {
                if (state.isUploadingImage) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                } else {
                    Text(stringResource(R.string.product_pick_photo))
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.barcode,
                    onValueChange = viewModel::onBarcodeChange,
                    label = { Text(stringResource(R.string.product_barcode)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onScanBarcode) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = stringResource(R.string.product_scan_barcode))
                }
            }

            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text(stringResource(R.string.product_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            var categoryExpanded by remember { mutableStateOf(false) }
            val filteredCategories = state.availableCategories.filter {
                state.category.isBlank() || it.contains(state.category, ignoreCase = true)
            }
            androidx.compose.material3.ExposedDropdownMenuBox(
                expanded = categoryExpanded && filteredCategories.isNotEmpty(),
                onExpandedChange = { categoryExpanded = it },
                modifier = Modifier.padding(top = 8.dp),
            ) {
                OutlinedTextField(
                    value = state.category,
                    onValueChange = {
                        viewModel.onCategoryChange(it)
                        categoryExpanded = true
                    },
                    label = { Text(stringResource(R.string.product_category)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                )
                androidx.compose.material3.DropdownMenu(
                    expanded = categoryExpanded && filteredCategories.isNotEmpty(),
                    onDismissRequest = { categoryExpanded = false },
                ) {
                    filteredCategories.forEach { existingCategory ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(existingCategory) },
                            onClick = {
                                viewModel.onCategoryChange(existingCategory)
                                categoryExpanded = false
                            },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = state.unit,
                onValueChange = viewModel::onUnitChange,
                label = { Text(stringResource(R.string.product_unit)) },
                singleLine = true,
                readOnly = state.soldByWeight,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Row(modifier = Modifier.padding(top = 8.dp)) {
                OutlinedTextField(
                    value = state.purchaseCost,
                    onValueChange = viewModel::onPurchaseCostChange,
                    label = { Text(stringResource(if (state.soldByWeight) R.string.product_purchase_cost_per_kg else R.string.product_purchase_cost)) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = state.sellingPrice,
                    onValueChange = viewModel::onSellingPriceChange,
                    label = { Text(stringResource(if (state.soldByWeight) R.string.product_selling_price_per_kg else R.string.product_selling_price)) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.product_sold_by_weight), modifier = Modifier.weight(1f))
                androidx.compose.material3.Switch(checked = state.soldByWeight, onCheckedChange = viewModel::onSoldByWeightChange)
            }

            if (!state.soldByWeight) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.product_is_packaged), modifier = Modifier.weight(1f))
                    androidx.compose.material3.Switch(checked = state.isPackaged, onCheckedChange = viewModel::onIsPackagedChange)
                }
            }

            if (state.isPackaged && !state.soldByWeight) {
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = state.unitsPerPackage,
                        onValueChange = viewModel::onUnitsPerPackageChange,
                        label = { Text(stringResource(R.string.product_units_per_package)) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.productId == null) {
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = state.packagesOnHand,
                            onValueChange = viewModel::onPackagesOnHandChange,
                            label = { Text(stringResource(R.string.product_packages_on_hand)) },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Row(modifier = Modifier.padding(top = 8.dp)) {
                OutlinedTextField(
                    value = state.quantity,
                    onValueChange = viewModel::onQuantityChange,
                    label = { Text(stringResource(R.string.product_quantity)) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                if (state.isPackaged && state.thresholdMode == ThresholdMode.PACKAGES) {
                    OutlinedTextField(
                        value = state.thresholdPackages,
                        onValueChange = viewModel::onThresholdPackagesChange,
                        label = { Text(stringResource(R.string.product_low_stock_threshold)) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    OutlinedTextField(
                        value = state.lowStockThreshold,
                        onValueChange = viewModel::onLowStockThresholdChange,
                        label = { Text(stringResource(R.string.product_low_stock_threshold)) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (state.isPackaged) {
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.product_threshold_by), modifier = Modifier.align(androidx.compose.ui.Alignment.CenterVertically))
                    androidx.compose.material3.FilterChip(
                        selected = state.thresholdMode == ThresholdMode.UNITS,
                        onClick = { viewModel.onThresholdModeChange(ThresholdMode.UNITS) },
                        label = { Text(stringResource(R.string.product_threshold_units)) },
                    )
                    androidx.compose.material3.FilterChip(
                        selected = state.thresholdMode == ThresholdMode.PACKAGES,
                        onClick = { viewModel.onThresholdModeChange(ThresholdMode.PACKAGES) },
                        label = { Text(stringResource(R.string.product_threshold_packages)) },
                    )
                }
            }

            // Only a brand-new product with real initial stock needs to say
            // how it's paid for - editing an existing product never
            // re-finances its stock (quantity edits go through adjust(),
            // which doesn't touch cash or invoices).
            if (state.productId == null && (state.quantity.toDoubleOrNull() ?: 0.0) > 0) {
                FinancingChoiceFields(
                    financing = state.financing,
                    onFinancingChange = viewModel::onFinancingChange,
                    suppliers = state.suppliers,
                    pendingInvoices = state.pendingInvoices,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }

            Spacer(Modifier.height(16.dp))
            Button(onClick = viewModel::save, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) {
                if (state.isSaving) CircularProgressIndicator(modifier = Modifier.size(18.dp)) else Text(stringResource(R.string.action_save))
            }

            if (state.productId != null) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showRestockDialog = true }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.restock_title))
                    }
                    OutlinedButton(onClick = { showAdjustDialog = true }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.adjust_title))
                    }
                }
                Spacer(Modifier.height(8.dp))
                // Correcting a purchase that was entered with the wrong
                // financing lives here, next to the actions that created it.
                OutlinedButton(
                    onClick = { showHistoryDialog = true; viewModel.loadPurchaseHistory() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.purchase_history_title))
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.product_delete))
                }
            }
        }
    }

    if (showHistoryDialog) {
        PurchaseHistoryDialog(
            state = state,
            onDismiss = { showHistoryDialog = false },
            onRefinance = { txnId, financing -> viewModel.refinancePurchase(txnId, financing) },
            onUndoSpoilage = { txnId -> viewModel.undoSpoilage(txnId) },
        )
    }

    if (showRestockDialog) {
        RestockDialog(
            isPackaged = state.isPackaged,
            unitsPerPackage = state.unitsPerPackageValue,
            suppliers = state.suppliers,
            pendingInvoices = state.pendingInvoices,
            onDismiss = { showRestockDialog = false },
            onConfirm = { qty, cost, financing ->
                viewModel.restock(qty, cost, financing)
                showRestockDialog = false
            },
        )
    }
    if (showAdjustDialog) {
        AdjustDialog(
            purchaseCost = state.purchaseCost,
            onDismiss = { showAdjustDialog = false },
            onConfirm = { change, reason ->
                viewModel.adjust(change, reason)
                showAdjustDialog = false
            },
        )
    }
    if (showDeleteConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.product_delete)) },
            text = { Text(stringResource(R.string.product_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.delete()
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}


// Past stock purchases for this product, each showing how it was paid for,
// with a way to correct that choice after the fact. Entering a delivery as
// cash when it was really on the supplier's invoice (or the reverse) is an
// easy slip at the counter, and until it's corrected the till and the
// invoice both carry the wrong figure.
@Composable
private fun PurchaseHistoryDialog(
    state: ProductFormUiState,
    onDismiss: () -> Unit,
    onRefinance: suspend (String, RestockFinancing) -> String?,
    onUndoSpoilage: suspend (String) -> String?,
) {
    val scope = rememberCoroutineScope()
    // Which row's correction controls are open; only one at a time.
    var editingId by remember { mutableStateOf<String?>(null) }
    var choice by remember { mutableStateOf<RestockFinancing>(RestockFinancing.Cash) }
    var rowError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.purchase_history_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                when {
                    state.historyLoading -> CircularProgressIndicator()
                    state.historyError != null -> Text(
                        state.historyError,
                        color = MaterialTheme.colorScheme.error,
                    )
                    state.purchaseHistory.isEmpty() -> Text(
                        stringResource(R.string.purchase_history_empty),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    else -> state.purchaseHistory.forEach { txn ->
                        if (txn.type == "SPOILAGE") {
                            SpoilageHistoryRow(
                                txn = txn,
                                busy = busy,
                                onUndo = {
                                    busy = true
                                    rowError = null
                                    scope.launch {
                                        val failure = onUndoSpoilage(txn.id)
                                        busy = false
                                        rowError = failure
                                    }
                                },
                            )
                            return@forEach
                        }
                        // A restock of zero units is a revaluation, not a
                        // delivery: what the product was said to be worth,
                        // either when it was created or when the cost was
                        // corrected by hand. Nothing was bought, so there is
                        // no payment to correct.
                        if ((txn.quantityChange.toDoubleOrNull() ?: 0.0) == 0.0) {
                            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Text(
                                    stringResource(
                                        R.string.purchase_history_revaluation,
                                        formatAmount(txn.unitCost ?: "0"),
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    formatIsoDate(txn.createdAt),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            return@forEach
                        }
                        val onInvoice = txn.supplierInvoiceId != null
                        val financingLabel = if (onInvoice) {
                            val inv = txn.supplierInvoice
                            stringResource(
                                R.string.purchase_financing_invoice,
                                inv?.supplier?.name ?: inv?.invoiceNumber ?: "",
                            )
                        } else {
                            stringResource(R.string.purchase_financing_cash)
                        }

                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        stringResource(
                                            R.string.purchase_history_line,
                                            formatQuantity(txn.quantityChange),
                                            formatAmount(txn.unitCost ?: "0"),
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        formatIsoDate(txn.createdAt),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        financingLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (onInvoice) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                                    )
                                }
                                TextButton(onClick = {
                                    rowError = null
                                    if (editingId == txn.id) {
                                        editingId = null
                                    } else {
                                        editingId = txn.id
                                        // Default to the opposite of what it is
                                        // now - that's the correction the owner
                                        // opened this row to make.
                                        choice = if (onInvoice) {
                                            RestockFinancing.Cash
                                        } else {
                                            state.pendingInvoices.firstOrNull()
                                                ?.let { RestockFinancing.ExistingInvoice(it.id) }
                                                ?: RestockFinancing.Cash
                                        }
                                    }
                                }) { Text(stringResource(R.string.purchase_correct)) }
                            }

                            if (editingId == txn.id) {
                                Column(Modifier.padding(start = 8.dp, top = 4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(
                                            selected = choice is RestockFinancing.Cash,
                                            onClick = { choice = RestockFinancing.Cash },
                                        )
                                        Text(stringResource(R.string.restock_financing_cash))
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(
                                            selected = choice is RestockFinancing.ExistingInvoice,
                                            onClick = {
                                                choice = state.pendingInvoices.firstOrNull()
                                                    ?.let { RestockFinancing.ExistingInvoice(it.id) }
                                                    ?: choice
                                            },
                                            enabled = state.pendingInvoices.isNotEmpty(),
                                        )
                                        Text(stringResource(R.string.restock_financing_deferred))
                                    }
                                    if (choice is RestockFinancing.ExistingInvoice) {
                                        ExistingInvoicePicker(
                                            pendingInvoices = state.pendingInvoices,
                                            selectedId = (choice as RestockFinancing.ExistingInvoice).invoiceId,
                                            onSelect = { choice = RestockFinancing.ExistingInvoice(it) },
                                        )
                                    }
                                    if (state.pendingInvoices.isEmpty()) {
                                        Text(
                                            stringResource(R.string.purchase_correct_no_invoices),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    rowError?.let {
                                        Text(
                                            it,
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(top = 4.dp),
                                        )
                                    }
                                    TextButton(
                                        enabled = !busy,
                                        onClick = {
                                            busy = true
                                            rowError = null
                                            scope.launch {
                                                val failure = onRefinance(txn.id, choice)
                                                busy = false
                                                if (failure == null) editingId = null else rowError = failure
                                            }
                                        },
                                    ) { Text(stringResource(R.string.purchase_correct_apply)) }
                                }
                            }
                            Divider(Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

// A spoilage has no cash-or-invoice side to correct - the only thing that
// can be wrong is that it happened at all, so the single action is to put
// the stock back and drop the write-off.
@Composable
private fun SpoilageHistoryRow(
    txn: com.supermarket.inventory.data.remote.dto.InventoryTransactionDto,
    busy: Boolean,
    onUndo: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(
                        R.string.spoilage_history_line,
                        formatQuantity(txn.quantityChange.removePrefix("-")),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    formatIsoDate(txn.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(enabled = !busy, onClick = onUndo) {
                Text(stringResource(R.string.spoilage_undo))
            }
        }
        Divider(Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun RestockDialog(
    isPackaged: Boolean,
    unitsPerPackage: Double,
    suppliers: List<SupplierDto>,
    pendingInvoices: List<SupplierInvoiceDto>,
    onDismiss: () -> Unit,
    onConfirm: (Double, Double?, RestockFinancing) -> Unit,
) {
    var quantity by remember { mutableStateOf("") }
    var unitCost by remember { mutableStateOf("") }
    var financing by remember { mutableStateOf<RestockFinancing>(RestockFinancing.Cash) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.restock_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text(stringResource(if (isPackaged) R.string.restock_packages_received else R.string.restock_quantity)) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = unitCost,
                    onValueChange = { unitCost = it },
                    label = { Text(stringResource(R.string.restock_unit_cost)) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.padding(top = 8.dp),
                )
                FinancingChoiceFields(
                    financing = financing,
                    onFinancingChange = { financing = it },
                    suppliers = suppliers,
                    pendingInvoices = pendingInvoices,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val entered = quantity.toDoubleOrNull()
                if (entered != null && entered > 0) {
                    val units = if (isPackaged) entered * unitsPerPackage else entered
                    onConfirm(units, unitCost.toDoubleOrNull(), financing)
                }
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

// Which supplier invoice this stock arrives on - either one already pending,
// picked from a dropdown, or a brand new one described inline (supplier +
// due date).
//
// There is deliberately no cash option here anymore: stock bought with cash
// is recorded as a supplier invoice from Others -> Supplier invoices, so
// every cash purchase has a document behind it that can be reviewed,
// corrected, or undone. Cash stays reachable as a correction (Purchase
// history -> Correct), which is what it's for - fixing a purchase that was
// entered against the wrong side.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FinancingChoiceFields(
    financing: RestockFinancing,
    onFinancingChange: (RestockFinancing) -> Unit,
    suppliers: List<SupplierDto>,
    pendingInvoices: List<SupplierInvoiceDto>,
    modifier: Modifier = Modifier,
) {
    // The state still starts on the old cash default; move it onto an
    // invoice as soon as this section appears, so nothing can be saved as a
    // cash purchase just because the owner never touched the picker.
    LaunchedEffect(financing, pendingInvoices, suppliers) {
        if (financing is RestockFinancing.Cash) {
            onFinancingChange(
                pendingInvoices.firstOrNull()?.let { RestockFinancing.ExistingInvoice(it.id) }
                    ?: RestockFinancing.NewInvoice(supplierId = suppliers.firstOrNull()?.id ?: "")
            )
        }
    }

    Column(modifier) {
        Text(stringResource(R.string.restock_financing_label), style = MaterialTheme.typography.labelMedium)
        Text(
            stringResource(R.string.restock_financing_invoice_only),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (pendingInvoices.isNotEmpty()) {
                FilterChip(
                    selected = financing is RestockFinancing.ExistingInvoice,
                    onClick = { onFinancingChange(RestockFinancing.ExistingInvoice(pendingInvoices.first().id)) },
                    label = { Text(stringResource(R.string.restock_financing_existing_invoice)) },
                )
            }
            FilterChip(
                selected = financing is RestockFinancing.NewInvoice,
                onClick = { onFinancingChange(RestockFinancing.NewInvoice(supplierId = suppliers.firstOrNull()?.id ?: "")) },
                label = { Text(stringResource(R.string.restock_financing_new_invoice)) },
            )
        }

        when (financing) {
            is RestockFinancing.ExistingInvoice -> ExistingInvoicePicker(
                pendingInvoices = pendingInvoices,
                selectedId = financing.invoiceId,
                onSelect = { onFinancingChange(RestockFinancing.ExistingInvoice(it)) },
            )
            is RestockFinancing.NewInvoice -> NewInvoiceFields(
                value = financing,
                suppliers = suppliers,
                onChange = onFinancingChange,
            )
            // Only for the instant before the effect above moves it off cash.
            is RestockFinancing.Cash -> Unit
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExistingInvoicePicker(
    pendingInvoices: List<SupplierInvoiceDto>,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = pendingInvoices.find { it.id == selectedId }
    val label = selected?.let {
        "${it.supplier?.name ?: it.supplierId} — ${formatAmount(it.amount)}"
    } ?: ""

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.padding(top = 8.dp),
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.restock_financing_existing_invoice)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            pendingInvoices.forEach { invoice ->
                DropdownMenuItem(
                    text = { Text("${invoice.supplier?.name ?: invoice.supplierId} — ${formatAmount(invoice.amount)}") },
                    onClick = { onSelect(invoice.id); expanded = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewInvoiceFields(
    value: RestockFinancing.NewInvoice,
    suppliers: List<SupplierDto>,
    onChange: (RestockFinancing.NewInvoice) -> Unit,
) {
    var supplierExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()
    val selectedSupplier = suppliers.find { it.id == value.supplierId }

    Column(Modifier.padding(top = 8.dp)) {
        ExposedDropdownMenuBox(expanded = supplierExpanded, onExpandedChange = { supplierExpanded = it }) {
            OutlinedTextField(
                value = selectedSupplier?.name ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.invoice_supplier)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = supplierExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
            )
            DropdownMenu(expanded = supplierExpanded, onDismissRequest = { supplierExpanded = false }) {
                suppliers.forEach { supplier ->
                    DropdownMenuItem(
                        text = { Text(supplier.name) },
                        onClick = { onChange(value.copy(supplierId = supplier.id)); supplierExpanded = false },
                    )
                }
            }
        }
        OutlinedTextField(
            value = value.invoiceNumber,
            onValueChange = { onChange(value.copy(invoiceNumber = it)) },
            label = { Text(stringResource(R.string.invoice_number)) },
            singleLine = true,
            modifier = Modifier.padding(top = 8.dp),
        )
        OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.padding(top = 8.dp)) {
            Text(
                value.dueDateIso?.let { formatIsoDate(it) } ?: stringResource(R.string.invoice_due_date)
            )
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = datePickerState.selectedDateMillis
                    if (millis != null) {
                        val iso = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toInstant().toString()
                        onChange(value.copy(dueDateIso = iso))
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun AdjustDialog(
    purchaseCost: String,
    onDismiss: () -> Unit,
    onConfirm: (Double, String) -> Unit,
) {
    var change by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    // Taking stock out returns what it cost to the till, so say so before
    // it happens rather than leaving the balance to move unannounced.
    val removing = (change.toDoubleOrNull() ?: 0.0) < 0
    val refund = (change.toDoubleOrNull() ?: 0.0) * -1 * (purchaseCost.toDoubleOrNull() ?: 0.0)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.adjust_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = change,
                    onValueChange = { change = it },
                    label = { Text(stringResource(R.string.adjust_quantity)) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text(stringResource(R.string.adjust_reason)) },
                    singleLine = true,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (removing && refund > 0) {
                    Text(
                        stringResource(R.string.adjust_refund_notice, formatAmount(refund.toString())),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val c = change.toDoubleOrNull()
                if (c != null && c != 0.0 && reason.isNotBlank()) onConfirm(c, reason)
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
