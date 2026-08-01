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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import java.io.File

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
            OutlinedTextField(
                value = state.category,
                onValueChange = viewModel::onCategoryChange,
                label = { Text(stringResource(R.string.product_category)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = state.unit,
                onValueChange = viewModel::onUnitChange,
                label = { Text(stringResource(R.string.product_unit)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Row(modifier = Modifier.padding(top = 8.dp)) {
                OutlinedTextField(
                    value = state.purchaseCost,
                    onValueChange = viewModel::onPurchaseCostChange,
                    label = { Text(stringResource(R.string.product_purchase_cost)) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = state.sellingPrice,
                    onValueChange = viewModel::onSellingPriceChange,
                    label = { Text(stringResource(R.string.product_selling_price)) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = state.packageLabel,
                onValueChange = viewModel::onPackageLabelChange,
                label = { Text(stringResource(R.string.product_package_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )

            if (state.isPackaged) {
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
                    enabled = state.productId == null && !state.isPackaged,
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
            }
        }
    }

    if (showRestockDialog) {
        RestockDialog(
            isPackaged = state.isPackaged,
            unitsPerPackage = state.unitsPerPackageValue,
            onDismiss = { showRestockDialog = false },
            onConfirm = { qty, cost ->
                viewModel.restock(qty, cost)
                showRestockDialog = false
            },
        )
    }
    if (showAdjustDialog) {
        AdjustDialog(
            onDismiss = { showAdjustDialog = false },
            onConfirm = { change, reason ->
                viewModel.adjust(change, reason)
                showAdjustDialog = false
            },
        )
    }
}

@Composable
private fun RestockDialog(
    isPackaged: Boolean,
    unitsPerPackage: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double, Double?) -> Unit,
) {
    var quantity by remember { mutableStateOf("") }
    var unitCost by remember { mutableStateOf("") }
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
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val entered = quantity.toDoubleOrNull()
                if (entered != null && entered > 0) {
                    val units = if (isPackaged) entered * unitsPerPackage else entered
                    onConfirm(units, unitCost.toDoubleOrNull())
                }
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun AdjustDialog(onDismiss: () -> Unit, onConfirm: (Double, String) -> Unit) {
    var change by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
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

private fun copyUriToCacheFile(context: android.content.Context, uri: Uri): File? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val file = File(context.cacheDir, "upload_${System.currentTimeMillis()}.jpg")
        inputStream.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
        file
    } catch (_: Exception) {
        null
    }
}
