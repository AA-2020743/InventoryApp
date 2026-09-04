package com.supermarket.inventory.ui.invoices

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import com.supermarket.inventory.data.remote.dto.InvoiceLineInput
import com.supermarket.inventory.data.remote.dto.SupplierDto
import com.supermarket.inventory.data.remote.dto.ProductDto
import com.supermarket.inventory.data.remote.dto.SupplierInvoiceDto
import com.supermarket.inventory.ui.common.copyUriToCacheFile
import com.supermarket.inventory.ui.common.formatAmount
import com.supermarket.inventory.ui.common.formatIsoDate
import com.supermarket.inventory.ui.common.formatQuantity
import com.supermarket.inventory.ui.theme.lossColor
import com.supermarket.inventory.ui.theme.profitColor
import com.supermarket.inventory.ui.theme.warningColor
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset
import androidx.compose.material.icons.filled.Receipt
import com.supermarket.inventory.ui.common.EmptyState

private const val PAYMENT_CASH = "CASH"
private const val PAYMENT_DEFERRED = "DEFERRED"

// One product about to be booked onto a new purchase invoice. It lives in
// the dialog until the invoice is saved, because the invoice and the stock
// it delivered are created together in one call.
private data class PurchaseLine(
    val product: ProductDto,
    val quantity: Double,
    val unitCost: Double,
) {
    val total: Double get() = quantity * unitCost
}

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
    // Settled invoices get their own shelf: what's still owed is the thing
    // you act on day to day, and a growing pile of paid paperwork buried it.
    var showSettled by remember { mutableStateOf(false) }

    val now = remember { Instant.now() }
    val openInvoices = state.invoices.filter { it.status != "PAID" }.sortedBy { it.dueDate }
    val settledInvoices = state.invoices.filter { it.status == "PAID" }
        .sortedByDescending { it.paidAt ?: it.dueDate }
    val outstanding = openInvoices.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
    val settledTotal = settledInvoices.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
    val overdueCount = openInvoices.count { invoice ->
        runCatching { Instant.parse(invoice.dueDate).isBefore(now) }.getOrDefault(false)
    }
    val shown = if (showSettled) settledInvoices else openInvoices

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { showManageSuppliers = true }) { Text(stringResource(R.string.suppliers_title)) }
            }

            if (!state.isLoading) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    SegmentedButton(
                        selected = !showSettled,
                        onClick = { showSettled = false },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) { Text(stringResource(R.string.invoices_filter_open, openInvoices.size), maxLines = 1) }
                    SegmentedButton(
                        selected = showSettled,
                        onClick = { showSettled = true },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) { Text(stringResource(R.string.invoices_filter_settled, settledInvoices.size), maxLines = 1) }
                }

                // One number that says where you stand on whichever shelf is
                // open: what's still owed, or what's already been settled.
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(
                                    if (showSettled) R.string.invoices_settled_total_label
                                    else R.string.invoices_outstanding_label
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                formatAmount((if (showSettled) settledTotal else outstanding).toString()),
                                style = MaterialTheme.typography.titleLarge,
                                color = if (showSettled) profitColor() else warningColor(),
                            )
                        }
                        if (!showSettled && overdueCount > 0) {
                            Text(
                                stringResource(R.string.invoices_overdue_count, overdueCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = lossColor(),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                shown.isEmpty() -> EmptyState(
                    icon = if (showSettled) Icons.Filled.CheckCircle else Icons.Filled.Receipt,
                    title = stringResource(
                        if (showSettled) R.string.invoices_empty_settled else R.string.invoices_empty_open
                    ),
                    hint = stringResource(
                        if (showSettled) R.string.invoices_empty_settled_hint else R.string.invoices_empty_open_hint
                    ),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                ) {
                    items(shown, key = { it.id }) { invoice ->
                        InvoiceRow(
                            invoice = invoice,
                            onMarkPaid = { viewModel.markPaid(invoice.id) },
                            onMarkUnpaid = { viewModel.markUnpaid(invoice.id) },
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
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.invoice_purchase_title))
        }
    }

    if (showManageSuppliers) {
        ManageSuppliersDialog(onDismiss = { showManageSuppliers = false })
    }

    if (showAddDialog) {
        PurchaseInvoiceDialog(
            viewModel = viewModel,
            onDismiss = { showAddDialog = false },
            onDone = { createdId, hasLines, paidNow ->
                showAddDialog = false
                viewModel.load()
                // A cash purchase is settled the moment it's recorded, so it
                // lands on the other shelf - follow it there rather than
                // leaving the owner staring at a list it isn't in.
                if (paidNow) showSettled = true
                // Lines booked their own stock. An invoice recorded as a bare
                // total still has nothing behind it, so offer to fill that in.
                if (!hasLines) justCreatedInvoiceId = createdId
            },
        )
    }

    // Offered right after an amount-only invoice is created: nothing was
    // booked against it yet, so this is the moment to record which products
    // it covers. Declining just closes it - the same view is reachable later
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
        // Re-read from the list so the sheet shows the invoice's current
        // total: editing a line changes it, and a stale copy would keep
        // showing the figure from before the correction.
        val current = state.invoices.firstOrNull { it.id == invoice.id } ?: invoice
        LinkedStockDialog(
            viewModel = viewModel,
            invoice = current,
            onDismiss = { linkedStockInvoice = null; viewModel.closeLinkedInventory() },
        )
    }

    invoiceToEdit?.let { invoice ->
        InvoiceDialog(
            viewModel = viewModel,
            invoice = invoice,
            onDismiss = { invoiceToEdit = null },
            onDone = { invoiceToEdit = null; viewModel.load() },
        )
    }

    invoiceToDelete?.let { invoice ->
        val paidFromTill = invoice.status == "PAID"
        AlertDialog(
            onDismissRequest = { invoiceToDelete = null },
            title = { Text(stringResource(R.string.confirm_delete_named_title, invoice.supplier?.name ?: invoice.supplierId)) },
            // Deleting the invoice takes its deliveries out of stock too, so
            // say so here rather than letting the inventory drop unannounced -
            // and say where the money goes, which depends on whether the till
            // ever paid for it.
            text = {
                Text(
                    stringResource(
                        if (paidFromTill) R.string.invoice_delete_message_paid
                        else R.string.invoice_delete_message
                    )
                )
            },
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
// restock - the till isn't touched directly, because the invoice's own total
// is what moves the cash, and on a paid invoice that total is resized to
// match whatever the lines now come to.
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
    // Which existing line has its quantity editor open, and the value being
    // typed into it.
    var editingLineId by remember { mutableStateOf<String?>(null) }
    var editedQuantity by remember { mutableStateOf("") }
    var lineError by remember { mutableStateOf<String?>(null) }
    var lineBusy by remember { mutableStateOf(false) }

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
                if (invoice.amountFromLines) {
                    // On a line-managed invoice the total follows the lines,
                    // so every edit below moves it - and on a paid one, moves
                    // the cash with it. Say so before it happens.
                    Text(
                        stringResource(
                            if (invoice.status == "PAID") R.string.invoice_lines_drive_cash
                            else R.string.invoice_lines_drive_total
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (linked != null) {
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
                            color = warningColor(),
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
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
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
                                IconButton(onClick = {
                                    lineError = null
                                    if (editingLineId == line.id) {
                                        editingLineId = null
                                    } else {
                                        editingLineId = line.id
                                        editedQuantity = formatQuantity(line.quantityChange)
                                    }
                                }) {
                                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit))
                                }
                                IconButton(enabled = !lineBusy, onClick = {
                                    lineBusy = true
                                    lineError = null
                                    scope.launch {
                                        lineError = viewModel.removeLinkedStock(invoice.id, line.id)
                                        lineBusy = false
                                    }
                                }) {
                                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                                }
                            }
                            // Correcting the line here corrects the stock it
                            // brought in. On a deferred invoice the till is
                            // untouched - that stock was never paid for from
                            // it. On a paid one the invoice total moves with
                            // the line, and the till follows.
                            if (editingLineId == line.id) {
                                Row(
                                    Modifier.fillMaxWidth().padding(start = 8.dp, top = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    OutlinedTextField(
                                        value = editedQuantity,
                                        onValueChange = { editedQuantity = it },
                                        label = { Text(stringResource(R.string.invoice_line_new_quantity)) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(
                                        enabled = !lineBusy,
                                        onClick = {
                                            val newQty = editedQuantity.toDoubleOrNull()
                                            if (newQty == null || newQty <= 0) return@TextButton
                                            lineBusy = true
                                            lineError = null
                                            scope.launch {
                                                val failure = viewModel.updateLinkedStock(invoice.id, line.id, newQty)
                                                lineBusy = false
                                                if (failure == null) editingLineId = null else lineError = failure
                                            }
                                        },
                                    ) { Text(stringResource(R.string.action_save)) }
                                }
                            }
                        }
                    }
                }

                lineError?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
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
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = unitCost,
                    onValueChange = { unitCost = it },
                    label = { Text(stringResource(R.string.restock_unit_cost)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
    onMarkUnpaid: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onViewImage: () -> Unit,
    onViewLinkedStock: () -> Unit,
) {
    val now = Instant.now()
    val dueInstant = try { Instant.parse(invoice.dueDate) } catch (_: Exception) { now }
    val isOverdue = invoice.status == "PENDING" && dueInstant.isBefore(now)
    val statusColor = when {
        invoice.status == "PAID" -> profitColor()
        isOverdue -> lossColor()
        else -> warningColor()
    }

    val deficit = invoice.deficitAmount.toDoubleOrNull() ?: 0.0

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(invoice.supplier?.name ?: invoice.supplierId, style = MaterialTheme.typography.titleMedium)
                    if (invoice.amountFromLines) {
                        Text(
                            stringResource(R.string.invoice_total_from_stock_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
            // A settled invoice's due date stopped mattering the day it was
            // paid, so show when that happened instead.
            val paidAt = invoice.paidAt
            Text(
                text = if (invoice.status == "PAID" && paidAt != null) {
                    "${stringResource(R.string.invoice_paid_on)}: ${formatIsoDate(paidAt)}"
                } else {
                    "${stringResource(R.string.invoice_due_date)}: ${formatIsoDate(invoice.dueDate)}"
                },
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
                horizontalArrangement = Arrangement.End,
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
                        color = profitColor(),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                    // Marking paid is one tap and drains the till by the
                    // invoice amount, so the way back needs to be one tap too.
                    TextButton(onClick = onMarkUnpaid) {
                        Text(stringResource(R.string.invoice_unmark_paid), maxLines = 1)
                    }
                }
            }
        }
    }
}

// Recording a purchase. The invoice is the document; the products listed on
// it are the stock it delivered, and its total is their sum rather than a
// figure typed alongside them - so the paperwork can't disagree with the
// goods.
//
// "Cash now" settles it against the till the moment it's saved: this is the
// only way to record stock bought with cash, which is what makes every such
// purchase reviewable and correctable afterwards. "On credit" leaves it
// owed until it's marked paid.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PurchaseInvoiceDialog(
    viewModel: InvoicesViewModel,
    onDismiss: () -> Unit,
    onDone: (String?, Boolean, Boolean) -> Unit,
) {
    val state = viewModel.uiState
    var suppliers by remember { mutableStateOf<List<SupplierDto>>(emptyList()) }
    var selectedSupplier by remember { mutableStateOf<SupplierDto?>(null) }
    var expanded by remember { mutableStateOf(false) }
    // Deferred stays the default: this dialog has always meant "an invoice I
    // owe", and cash is money actually leaving the till, so it's a choice
    // that has to be made deliberately.
    var paymentMethod by remember { mutableStateOf(PAYMENT_DEFERRED) }
    var lines by remember { mutableStateOf<List<PurchaseLine>>(emptyList()) }
    var invoiceNumber by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()
    var dueDateMillis by remember { mutableStateOf<Long?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var showQuickAddSupplier by remember { mutableStateOf(false) }
    var imageUrl by remember { mutableStateOf<String?>(null) }
    var isUploadingImage by remember { mutableStateOf(false) }

    // The line being typed, before it's added to the list above.
    var productQuery by remember { mutableStateOf("") }
    var selectedProduct by remember { mutableStateOf<ProductDto?>(null) }
    var productExpanded by remember { mutableStateOf(false) }
    var quantity by remember { mutableStateOf("") }
    var unitCost by remember { mutableStateOf("") }
    var showQuickAddProduct by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val requiredMessage = stringResource(R.string.invoice_needs_supplier)
    val linesOrAmountMessage = stringResource(R.string.invoice_needs_lines_or_amount)
    val dueDateMessage = stringResource(R.string.invoice_needs_due_date)

    val matches = state.products.filter {
        productQuery.isBlank() || it.name.contains(productQuery, ignoreCase = true) ||
            (it.barcode?.contains(productQuery, ignoreCase = true) == true)
    }.take(20)
    val linesTotal = lines.sumOf { it.total }

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
            is ApiResult.Success -> {
                suppliers = result.data
                if (selectId != null) selectedSupplier = result.data.find { it.id == selectId }
            }
            is ApiResult.Error -> error = result.message
        }
    }

    LaunchedEffect(Unit) { reloadSuppliers() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.invoice_purchase_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
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

                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.invoice_payment_label), style = MaterialTheme.typography.labelMedium)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    SegmentedButton(
                        selected = paymentMethod == PAYMENT_CASH,
                        onClick = { paymentMethod = PAYMENT_CASH },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) { Text(stringResource(R.string.invoice_payment_cash), maxLines = 1) }
                    SegmentedButton(
                        selected = paymentMethod == PAYMENT_DEFERRED,
                        onClick = { paymentMethod = PAYMENT_DEFERRED },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) { Text(stringResource(R.string.invoice_payment_deferred), maxLines = 1) }
                }
                Text(
                    stringResource(
                        if (paymentMethod == PAYMENT_CASH) R.string.invoice_payment_cash_hint
                        else R.string.invoice_payment_deferred_hint
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )

                Spacer(Modifier.height(12.dp))
                Divider()
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.invoice_lines_title), style = MaterialTheme.typography.titleSmall)

                if (lines.isEmpty()) {
                    Text(
                        stringResource(R.string.invoice_no_lines),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                } else {
                    Column(Modifier.fillMaxWidth()) {
                        lines.forEachIndexed { index, line ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(line.product.name, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        stringResource(
                                            R.string.invoice_linked_line_detail,
                                            formatQuantity(line.quantity.toString()),
                                            formatAmount(line.unitCost.toString()),
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(formatAmount(line.total.toString()), style = MaterialTheme.typography.bodyMedium)
                                IconButton(onClick = { lines = lines.filterIndexed { i, _ -> i != index } }) {
                                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_delete))
                                }
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.invoice_total_from_lines, formatAmount(linesTotal.toString())),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                // Unlike the picker on an existing invoice, this one opens
                // even when nothing matches - "no such product" is exactly
                // when the create-new entry at the bottom is wanted.
                ExposedDropdownMenuBox(
                    expanded = productExpanded,
                    onExpandedChange = { productExpanded = it },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    OutlinedTextField(
                        value = selectedProduct?.name ?: productQuery,
                        onValueChange = { productQuery = it; selectedProduct = null; productExpanded = true },
                        label = { Text(stringResource(R.string.invoice_pick_product)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = productExpanded) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    DropdownMenu(
                        expanded = productExpanded,
                        onDismissRequest = { productExpanded = false },
                    ) {
                        matches.forEach { product ->
                            DropdownMenuItem(
                                text = { Text(product.name) },
                                onClick = {
                                    selectedProduct = product
                                    productQuery = product.name
                                    if (unitCost.isBlank()) unitCost = product.purchaseCost
                                    productExpanded = false
                                },
                            )
                        }
                        // A delivery often brings something the shop has
                        // never stocked. Creating it here keeps the invoice
                        // being typed intact instead of sending the owner
                        // off to Inventory and back.
                        if (matches.isNotEmpty()) Divider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.product_quick_add)) },
                            onClick = { productExpanded = false; showQuickAddProduct = true },
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it },
                        label = { Text(stringResource(R.string.restock_quantity)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = unitCost,
                        onValueChange = { unitCost = it },
                        label = { Text(stringResource(R.string.restock_unit_cost)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                TextButton(
                    onClick = {
                        val product = selectedProduct ?: return@TextButton
                        val qty = quantity.toDoubleOrNull() ?: return@TextButton
                        val cost = unitCost.toDoubleOrNull() ?: product.purchaseCost.toDoubleOrNull()
                        if (qty <= 0 || cost == null || cost < 0) return@TextButton
                        lines = lines + PurchaseLine(product, qty, cost)
                        selectedProduct = null
                        productQuery = ""
                        quantity = ""
                        unitCost = ""
                        error = null
                    },
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.invoice_line_add))
                }

                // An invoice that isn't for stock at all (a delivery charge,
                // a service) still has to be recordable, so a bare total is
                // accepted - but only while nothing is listed above it.
                if (lines.isEmpty()) {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text(stringResource(R.string.invoice_amount_manual)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }

                Spacer(Modifier.height(8.dp))
                Divider()
                OutlinedTextField(
                    value = invoiceNumber,
                    onValueChange = { invoiceNumber = it },
                    label = { Text(stringResource(R.string.invoice_number)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                // A cash purchase is settled on the spot, so it has no due
                // date to pick - it's dated today.
                if (paymentMethod == PAYMENT_DEFERRED) {
                    OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.padding(top = 8.dp)) {
                        Text(
                            dueDateMillis?.let { formatIsoDate(Instant.ofEpochMilli(it).toString()) }
                                ?: stringResource(R.string.invoice_due_date),
                        )
                    }
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
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSaving,
                onClick = {
                    val supplier = selectedSupplier
                    if (supplier == null) {
                        error = requiredMessage
                        return@TextButton
                    }
                    val typedAmount = amount.toDoubleOrNull()
                    if (lines.isEmpty() && (typedAmount == null || typedAmount <= 0)) {
                        error = linesOrAmountMessage
                        return@TextButton
                    }
                    if (paymentMethod == PAYMENT_DEFERRED && dueDateMillis == null) {
                        error = dueDateMessage
                        return@TextButton
                    }
                    isSaving = true
                    error = null
                    val hasLines = lines.isNotEmpty()
                    val paidNow = paymentMethod == PAYMENT_CASH
                    scope.launch {
                        val dueIso = Instant.ofEpochMilli(dueDateMillis ?: System.currentTimeMillis())
                            .atZone(ZoneOffset.UTC).toInstant().toString()
                        val result = viewModel.createPurchase(
                            supplierId = supplier.id,
                            invoiceNumber = invoiceNumber.ifBlank { null },
                            dueDateIso = dueIso,
                            paymentMethod = paymentMethod,
                            lines = lines.map { InvoiceLineInput(it.product.id, it.quantity, it.unitCost) },
                            amount = if (hasLines) null else typedAmount,
                            imageUrl = imageUrl,
                        )
                        when (result) {
                            is ApiResult.Success -> onDone(result.data.id, hasLines, paidNow)
                            is ApiResult.Error -> { isSaving = false; error = result.message }
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
                        is ApiResult.Success -> {
                            reloadSuppliers(selectId = result.data.id)
                            showQuickAddSupplier = false
                        }
                        is ApiResult.Error -> error = result.message
                    }
                }
            },
        )
    }

    if (showQuickAddProduct) {
        QuickAddProductDialog(
            // Seeded from what's already been typed into the line, so a
            // name half-entered in the picker isn't thrown away.
            initialName = if (selectedProduct == null) productQuery else "",
            initialPurchaseCost = unitCost,
            onDismiss = { showQuickAddProduct = false },
            onSave = { name, barcode, sellingPrice, purchaseCost, onFailure ->
                scope.launch {
                    when (val result = viewModel.createProduct(name, barcode, sellingPrice, purchaseCost)) {
                        is ApiResult.Success -> {
                            // Drop it straight into the line being typed:
                            // the only reason to create it here was to put
                            // it on this invoice.
                            selectedProduct = result.data
                            productQuery = result.data.name
                            if (unitCost.isBlank()) unitCost = result.data.purchaseCost
                            showQuickAddProduct = false
                        }
                        is ApiResult.Error -> onFailure(result.message)
                    }
                }
            },
        )
    }
}

// Creating a product from inside a purchase, for something the shop has
// never carried. Deliberately the short version - name, barcode, what it
// sells for - because it starts with no stock: the invoice line being typed
// is what puts the first units on the shelf, and at the price the invoice
// charged.
@Composable
private fun QuickAddProductDialog(
    initialName: String,
    initialPurchaseCost: String,
    onDismiss: () -> Unit,
    onSave: (String, String?, Double, Double, (String) -> Unit) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var barcode by remember { mutableStateOf("") }
    var sellingPrice by remember { mutableStateOf("") }
    var purchaseCost by remember { mutableStateOf(initialPurchaseCost) }
    var error by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    val nameRequired = stringResource(R.string.product_quick_add_needs_name)
    val priceRequired = stringResource(R.string.product_quick_add_needs_price)
    val barcodeRequired = stringResource(R.string.product_quick_add_needs_barcode)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.product_quick_add_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.product_quick_add_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.product_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = barcode,
                    onValueChange = { barcode = it },
                    label = { Text(stringResource(R.string.product_barcode)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = purchaseCost,
                    onValueChange = { purchaseCost = it },
                    label = { Text(stringResource(R.string.product_purchase_cost)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = sellingPrice,
                    onValueChange = { sellingPrice = it },
                    label = { Text(stringResource(R.string.product_selling_price)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSaving,
                onClick = {
                    val price = sellingPrice.toDoubleOrNull()
                    when {
                        name.isBlank() -> error = nameRequired
                        price == null || price < 0 -> error = priceRequired
                        // A product with no barcode is identified by its
                        // photo instead, and there's nowhere to take one
                        // from here - that one belongs on the full form.
                        barcode.isBlank() -> error = barcodeRequired
                        else -> {
                            isSaving = true
                            error = null
                            onSave(name.trim(), barcode.trim(), price, purchaseCost.toDoubleOrNull() ?: 0.0) { failure ->
                                isSaving = false
                                error = failure
                            }
                        }
                    }
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

// Editing an invoice that already exists: its paperwork, not its contents.
// On a line-managed invoice the total is the sum of the stock booked to it,
// so it's shown here rather than typed - changing it means changing the
// stock, which happens under "Linked stock".
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InvoiceDialog(
    viewModel: InvoicesViewModel,
    invoice: SupplierInvoiceDto,
    onDismiss: () -> Unit,
    onDone: () -> Unit,
) {
    var suppliers by remember { mutableStateOf<List<SupplierDto>>(emptyList()) }
    var selectedSupplier by remember { mutableStateOf<SupplierDto?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf(invoice.amount) }
    var invoiceNumber by remember { mutableStateOf(invoice.invoiceNumber ?: "") }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()
    var dueDateMillis by remember {
        mutableStateOf(runCatching { Instant.parse(invoice.dueDate).toEpochMilli() }.getOrNull())
    }
    var error by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var showQuickAddSupplier by remember { mutableStateOf(false) }
    var imageUrl by remember { mutableStateOf(invoice.imageUrl) }
    var isUploadingImage by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val requiredMessage = stringResource(R.string.invoice_needs_supplier)

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
            is ApiResult.Success -> {
                suppliers = result.data
                if (selectId != null) {
                    selectedSupplier = result.data.find { it.id == selectId }
                } else if (selectedSupplier == null) {
                    selectedSupplier = result.data.find { it.id == invoice.supplierId }
                }
            }
            is ApiResult.Error -> error = result.message
        }
    }

    LaunchedEffect(Unit) { reloadSuppliers() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.invoice_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
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
                if (invoice.amountFromLines) {
                    Text(
                        stringResource(R.string.invoice_amount_label, formatAmount(invoice.amount)),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        stringResource(R.string.invoice_amount_locked_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text(stringResource(R.string.invoice_amount)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
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
                    // A line-managed invoice keeps whatever its stock came to;
                    // only a typed-total invoice takes a new figure from here.
                    val amountValue = if (invoice.amountFromLines) invoice.amount.toDoubleOrNull() else amount.toDoubleOrNull()
                    val due = dueDateMillis
                    if (supplier == null || amountValue == null || due == null) {
                        error = requiredMessage
                        return@TextButton
                    }
                    isSaving = true
                    scope.launch {
                        val dueIso = Instant.ofEpochMilli(due).atZone(ZoneOffset.UTC).toInstant().toString()
                        val result = viewModel.updateInvoice(
                            invoice.id, supplier.id, invoiceNumber.ifBlank { null }, amountValue, dueIso, null, imageUrl,
                        )
                        when (result) {
                            is ApiResult.Success -> onDone()
                            is ApiResult.Error -> { isSaving = false; error = result.message }
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
                        is ApiResult.Success -> {
                            reloadSuppliers(selectId = result.data.id)
                            showQuickAddSupplier = false
                        }
                        is ApiResult.Error -> error = result.message
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
