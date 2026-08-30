package com.supermarket.inventory.ui.invoices

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.supermarket.inventory.data.ApiResult
import com.supermarket.inventory.data.remote.dto.SupplierDto
import com.supermarket.inventory.data.remote.dto.ProductDto
import com.supermarket.inventory.data.remote.dto.SupplierInvoiceDto
import com.supermarket.inventory.ui.common.copyUriToCacheFile
import com.supermarket.inventory.ui.common.formatAmount
import com.supermarket.inventory.ui.common.formatIsoDate
import com.supermarket.inventory.ui.common.formatQuantity
import com.supermarket.inventory.ui.theme.LossRed
import com.supermarket.inventory.ui.theme.ProfitGreen
import com.supermarket.inventory.ui.theme.WarningAmber
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoicesTabContent(viewModel: InvoicesViewModel = hiltViewModel()) {
    val state = viewModel.uiState
    var showAddDialog by remember { mutableStateOf(false) }
    var showManageSuppliers by remember { mutableStateOf(false) }
    var invoiceToEdit by remember { mutableStateOf<SupplierInvoiceDto?>(null) }
    var invoiceToDelete by remember { mutableStateOf<SupplierInvoiceDto?>(null) }
    var invoiceImageToView by remember { mutableStateOf<String?>(null) }
    // Id of an invoice just created, held so we can offer to book the stock
    // it paid for right away instead of the owner having to remember later.
    var justCreatedInvoiceId by remember { mutableStateOf<String?>(null) }
    // Invoice whose linked-stock sheet is open.
    var linkedStockInvoice by remember { mutableStateOf<SupplierInvoiceDto?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                TextButton(onClick = { showManageSuppliers = true }) { Text(stringResource(R.string.suppliers_title)) }
            }
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.invoices.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.inventory_empty))
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                ) {
                    items(state.invoices.sortedBy { it.dueDate }, key = { it.id }) { invoice ->
                        InvoiceRow(
                            invoice = invoice,
                            onMarkPaid = { viewModel.markPaid(invoice.id) },
                            onEdit = { invoiceToEdit = invoice },
                            onDelete = { invoiceToDelete = invoice },
                            onViewImage = { invoice.imageUrl?.let { invoiceImageToView = viewModel.fullImageUrl(it) } },
                            onViewLinkedStock = {
                                linkedStockInvoice = invoice
                                viewModel.openLinkedInventory(invoice.id)
                            },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.invoices_add))
        }
    }

    if (showManageSuppliers) {
        ManageSuppliersDialog(onDismiss = { showManageSuppliers = false })
    }

    if (showAddDialog) {
        InvoiceDialog(
            viewModel = viewModel,
            title = stringResource(R.string.invoices_add),
            invoiceToEdit = null,
            onDismiss = { showAddDialog = false },
            onDone = { createdId ->
                showAddDialog = false
                viewModel.load()
                // A supplier invoice is by definition stock taken on credit,
                // so offer to record what it covers while it's fresh.
                justCreatedInvoiceId = createdId
            },
        )
    }

    // Offered right after an invoice is created: a supplier invoice is stock
    // bought on credit, so this is the moment to record which products it
    // covers. Declining just closes it - the same view is reachable later
    // from the invoice's "Linked stock" button.
    justCreatedInvoiceId?.let { createdId ->
        val created = state.invoices.firstOrNull { it.id == createdId }
        AlertDialog(
            onDismissRequest = { justCreatedInvoiceId = null },
            title = { Text(stringResource(R.string.invoice_link_stock_prompt_title)) },
            text = { Text(stringResource(R.string.invoice_link_stock_prompt_message)) },
            confirmButton = {
                TextButton(onClick = {
                    justCreatedInvoiceId = null
                    if (created != null) {
                        linkedStockInvoice = created
                        viewModel.openLinkedInventory(created.id)
                    }
                }) { Text(stringResource(R.string.invoice_link_stock_now)) }
            },
            dismissButton = {
                TextButton(onClick = { justCreatedInvoiceId = null }) { Text(stringResource(R.string.invoice_link_stock_later)) }
            },
        )
    }

    linkedStockInvoice?.let { invoice ->
        LinkedStockDialog(
            viewModel = viewModel,
            invoice = invoice,
            onDismiss = { linkedStockInvoice = null; viewModel.closeLinkedInventory() },
        )
    }

    invoiceToEdit?.let { invoice ->
        InvoiceDialog(
            viewModel = viewModel,
            title = stringResource(R.string.invoice_edit),
            invoiceToEdit = invoice,
            onDismiss = { invoiceToEdit = null },
            onDone = { _ -> invoiceToEdit = null; viewModel.load() },
        )
    }

    invoiceToDelete?.let { invoice ->
        AlertDialog(
            onDismissRequest = { invoiceToDelete = null },
            title = { Text(stringResource(R.string.confirm_delete_named_title, invoice.supplier?.name ?: invoice.supplierId)) },
            text = { Text(stringResource(R.string.confirm_delete_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteInvoice(invoice.id); invoiceToDelete = null }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = { TextButton(onClick = { invoiceToDelete = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    invoiceImageToView?.let { url ->
        AlertDialog(
            onDismissRequest = { invoiceImageToView = null },
            title = { Text(stringResource(R.string.invoice_attachment)) },
            text = {
                AsyncImage(
                    model = url,
                    contentDescription = stringResource(R.string.invoice_attachment),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = { invoiceImageToView = null }) { Text(stringResource(R.string.action_close)) }
            },
        )
    }

    // markPaid() is a one-tap action on the row with no dialog of its own
    // to show a failure in (e.g. the till can't cover it) - surfaced here
    // instead.
    state.error?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text(stringResource(R.string.action_error)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissError) { Text(stringResource(R.string.action_close)) }
            },
        )
    }
}

// What an invoice actually paid for: every restock booked against it, plus
// an inline way to book more. Adding stock here is always a DEFERRED
// restock - the till isn't touched, because the invoice's own PENDING ->
// PAID lifecycle is what moves the cash.
//
// Only existing products can be topped up from here; a product that doesn't
// exist yet is created from Inventory -> add product, whose own financing
// section can point at this same invoice.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LinkedStockDialog(
    viewModel: InvoicesViewModel,
    invoice: SupplierInvoiceDto,
    onDismiss: () -> Unit,
) {
    val state = viewModel.uiState
    val scope = rememberCoroutineScope()
    var productQuery by remember { mutableStateOf("") }
    var selectedProduct by remember { mutableStateOf<ProductDto?>(null) }
    var productExpanded by remember { mutableStateOf(false) }
    var quantity by remember { mutableStateOf("") }
    var unitCost by remember { mutableStateOf("") }
    var addError by remember { mutableStateOf<String?>(null) }
    var isAdding by remember { mutableStateOf(false) }

    val matches = state.products.filter {
        productQuery.isBlank() || it.name.contains(productQuery, ignoreCase = true) ||
            (it.barcode?.contains(productQuery, ignoreCase = true) == true)
    }.take(20)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.invoice_linked_stock)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val linked = state.linkedInventory
                Text(
                    stringResource(R.string.invoice_amount_label, formatAmount(invoice.amount)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (linked != null) {
                    val invoiceAmount = linked.invoiceAmount.toDoubleOrNull() ?: 0.0
                    val bookedTotal = linked.linkedTotal.toDoubleOrNull() ?: 0.0
                    Text(
                        stringResource(R.string.invoice_linked_total_label, formatAmount(linked.linkedTotal)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    // Rounding on decimal money can leave a sub-cent gap that
                    // isn't a real discrepancy, so only flag a visible one.
                    if (kotlin.math.abs(invoiceAmount - bookedTotal) >= 0.01) {
                        Text(
                            stringResource(
                                R.string.invoice_linked_mismatch,
                                formatAmount((invoiceAmount - bookedTotal).toString()),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = WarningAmber,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Divider()
                Spacer(Modifier.height(8.dp))

                when {
                    state.linkedInventoryLoading -> CircularProgressIndicator()
                    state.linkedInventoryError != null -> Text(
                        state.linkedInventoryError,
                        color = MaterialTheme.colorScheme.error,
                    )
                    linked == null || linked.items.isEmpty() -> Text(
                        stringResource(R.string.invoice_no_linked_stock),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    else -> linked.items.forEach { line ->
                        val qty = line.quantityChange.toDoubleOrNull() ?: 0.0
                        val cost = line.unitCost?.toDoubleOrNull() ?: 0.0
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(line.product?.name ?: line.productId, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    stringResource(
                                        R.string.invoice_linked_line_detail,
                                        formatQuantity(line.quantityChange),
                                        formatAmount(line.unitCost ?: "0"),
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(formatAmount((qty * cost).toString()), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Divider()
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.invoice_add_stock), style = MaterialTheme.typography.titleSmall)

                ExposedDropdownMenuBox(
                    expanded = productExpanded && matches.isNotEmpty(),
                    onExpandedChange = { productExpanded = it },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    OutlinedTextField(
                        value = selectedProduct?.name ?: productQuery,
                        onValueChange = { productQuery = it; selectedProduct = null; productExpanded = true },
                        label = { Text(stringResource(R.string.invoice_pick_product)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    DropdownMenu(
                        expanded = productExpanded && matches.isNotEmpty(),
                        onDismissRequest = { productExpanded = false },
                    ) {
                        matches.forEach { product ->
                            DropdownMenuItem(
                                text = { Text(product.name) },
                                onClick = {
                                    selectedProduct = product
                                    productQuery = product.name
                                    // Seed with the product's known cost; the
                                    // owner overrides it when this invoice
                                    // charged a different price.
                                    if (unitCost.isBlank()) unitCost = product.purchaseCost
                                    productExpanded = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text(stringResource(R.string.restock_quantity)) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = unitCost,
                    onValueChange = { unitCost = it },
                    label = { Text(stringResource(R.string.restock_unit_cost)) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                addError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
                TextButton(
                    enabled = !isAdding,
                    onClick = {
                        val product = selectedProduct
                        val qty = quantity.toDoubleOrNull()
                        if (product == null || qty == null || qty <= 0) {
                            addError = null
                            return@TextButton
                        }
                        isAdding = true
                        addError = null
                        scope.launch {
                            val failure = viewModel.addStockToInvoice(
                                invoiceId = invoice.id,
                                productId = product.id,
                                quantity = qty,
                                unitCost = unitCost.toDoubleOrNull(),
                            )
                            isAdding = false
                            if (failure == null) {
                                selectedProduct = null
                                productQuery = ""
                                quantity = ""
                                unitCost = ""
                            } else {
                                addError = failure
                            }
                        }
                    },
                    modifier = Modifier.padding(top = 4.dp),
                ) { Text(stringResource(R.string.invoice_add_stock)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

@Composable
private fun InvoiceRow(
    invoice: SupplierInvoiceDto,
    onMarkPaid: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onViewImage: () -> Unit,
    onViewLinkedStock: () -> Unit,
) {
    val now = Instant.now()
    val dueInstant = try { Instant.parse(invoice.dueDate) } catch (_: Exception) { now }
    val isOverdue = invoice.status == "PENDING" && dueInstant.isBefore(now)
    val statusColor = when {
        invoice.status == "PAID" -> ProfitGreen
        isOverdue -> LossRed
        else -> WarningAmber
    }

    val deficit = invoice.deficitAmount.toDoubleOrNull() ?: 0.0

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(invoice.supplier?.name ?: invoice.supplierId, style = MaterialTheme.typography.titleMedium)
                    if (deficit > 0) {
                        Text(
                            stringResource(R.string.expense_deficit_badge, formatAmount(invoice.deficitAmount)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Text(formatAmount(invoice.amount), style = MaterialTheme.typography.titleMedium)
                if (invoice.imageUrl != null) {
                    IconButton(onClick = onViewImage) { Icon(Icons.Filled.Image, contentDescription = stringResource(R.string.invoice_attachment)) }
                }
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit)) }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete)) }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${stringResource(R.string.invoice_due_date)}: ${formatIsoDate(invoice.dueDate)}",
                color = statusColor,
                style = MaterialTheme.typography.bodySmall,
            )
            // The actions get a row to themselves rather than sharing one
            // with the due date: sharing left them whatever width the date
            // didn't take, which in Arabic (longer labels, longer month
            // names) squeezed the paid/mark-paid label down to a single
            // character per line.
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onViewLinkedStock) {
                    Icon(Icons.Filled.Inventory2, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.invoice_linked_stock), maxLines = 1)
                }
                // No "pay from cash register?" choice anymore - marking paid
                // always tries to pay from the till, recording a deficit if
                // it comes up short, so this is a direct one-tap action.
                if (invoice.status == "PENDING") {
                    TextButton(onClick = onMarkPaid) {
                        Text(stringResource(R.string.invoice_mark_paid), maxLines = 1)
                    }
                } else {
                    Text(
                        stringResource(R.string.invoice_status_paid),
                        color = ProfitGreen,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 8.dp, end = 12.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InvoiceDialog(
    viewModel: InvoicesViewModel,
    title: String,
    invoiceToEdit: SupplierInvoiceDto?,
    onDismiss: () -> Unit,
    onDone: (String?) -> Unit,
) {
    var suppliers by remember { mutableStateOf<List<SupplierDto>>(emptyList()) }
    var selectedSupplier by remember { mutableStateOf<SupplierDto?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf(invoiceToEdit?.amount ?: "") }
    var invoiceNumber by remember { mutableStateOf(invoiceToEdit?.invoiceNumber ?: "") }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()
    var dueDateMillis by remember {
        mutableStateOf(invoiceToEdit?.dueDate?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() })
    }
    var error by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var showQuickAddSupplier by remember { mutableStateOf(false) }
    var imageUrl by remember { mutableStateOf(invoiceToEdit?.imageUrl) }
    var isUploadingImage by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val file = copyUriToCacheFile(context, uri)
            if (file != null) {
                isUploadingImage = true
                scope.launch {
                    when (val result = viewModel.uploadImage(file)) {
                        is ApiResult.Success -> { imageUrl = result.data.url; isUploadingImage = false }
                        is ApiResult.Error -> { error = result.message; isUploadingImage = false }
                    }
                }
            }
        }
    }

    suspend fun reloadSuppliers(selectId: String? = null) {
        when (val result = viewModel.loadSuppliers()) {
            is com.supermarket.inventory.data.ApiResult.Success -> {
                suppliers = result.data
                if (selectId != null) {
                    selectedSupplier = result.data.find { it.id == selectId }
                } else if (invoiceToEdit != null && selectedSupplier == null) {
                    selectedSupplier = result.data.find { it.id == invoiceToEdit.supplierId }
                }
            }
            is com.supermarket.inventory.data.ApiResult.Error -> error = result.message
        }
    }

    LaunchedEffect(Unit) { reloadSuppliers() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = selectedSupplier?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.invoice_supplier)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        suppliers.forEach { supplier ->
                            DropdownMenuItem(text = { Text(supplier.name) }, onClick = { selectedSupplier = supplier; expanded = false })
                        }
                        if (suppliers.isNotEmpty()) Divider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.supplier_quick_add)) },
                            onClick = { expanded = false; showQuickAddSupplier = true },
                        )
                    }
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text(stringResource(R.string.invoice_amount)) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = invoiceNumber,
                    onValueChange = { invoiceNumber = it },
                    label = { Text(stringResource(R.string.invoice_number)) },
                    singleLine = true,
                    modifier = Modifier.padding(top = 8.dp),
                )
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        dueDateMillis?.let { formatIsoDate(Instant.ofEpochMilli(it).toString()) }
                            ?: stringResource(R.string.invoice_due_date),
                    )
                }
                Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val currentImageUrl = imageUrl
                    if (currentImageUrl != null) {
                        AsyncImage(
                            model = viewModel.fullImageUrl(currentImageUrl),
                            contentDescription = stringResource(R.string.invoice_attachment),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = { imageUrl = null }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.invoice_remove_attachment))
                        }
                    } else if (isUploadingImage) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        OutlinedButton(onClick = { imagePicker.launch("image/*") }) {
                            Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.invoice_attach_photo))
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSaving,
                onClick = {
                    val supplier = selectedSupplier
                    val amountValue = amount.toDoubleOrNull()
                    val due = dueDateMillis
                    if (supplier == null || amountValue == null || due == null) {
                        error = "All fields are required"
                        return@TextButton
                    }
                    isSaving = true
                    scope.launch {
                        val dueIso = Instant.ofEpochMilli(due).atZone(ZoneOffset.UTC).toInstant().toString()
                        val result = if (invoiceToEdit != null) {
                            viewModel.updateInvoice(invoiceToEdit.id, supplier.id, invoiceNumber.ifBlank { null }, amountValue, dueIso, null, imageUrl)
                        } else {
                            viewModel.createInvoice(supplier.id, invoiceNumber.ifBlank { null }, amountValue, dueIso, null, imageUrl)
                        }
                        when (result) {
                            is com.supermarket.inventory.data.ApiResult.Success ->
                                onDone(if (invoiceToEdit == null) result.data.id else null)
                            is com.supermarket.inventory.data.ApiResult.Error -> { isSaving = false; error = result.message }
                        }
                    }
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dueDateMillis = datePickerState.selectedDateMillis
                    showDatePicker = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showQuickAddSupplier) {
        QuickAddSupplierDialog(
            onDismiss = { showQuickAddSupplier = false },
            onSave = { name, contact ->
                scope.launch {
                    when (val result = viewModel.createSupplier(name, contact)) {
                        is com.supermarket.inventory.data.ApiResult.Success -> {
                            reloadSuppliers(selectId = result.data.id)
                            showQuickAddSupplier = false
                        }
                        is com.supermarket.inventory.data.ApiResult.Error -> error = result.message
                    }
                }
            },
        )
    }
}

@Composable
private fun QuickAddSupplierDialog(onDismiss: () -> Unit, onSave: (String, String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.supplier_add)) },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.supplier_name)) }, singleLine = true)
                OutlinedTextField(
                    value = contact,
                    onValueChange = { contact = it },
                    label = { Text(stringResource(R.string.supplier_contact)) },
                    singleLine = true,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) onSave(name, contact.ifBlank { null })
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
