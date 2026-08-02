package com.supermarket.inventory.ui.invoices

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.supermarket.inventory.R
import com.supermarket.inventory.data.remote.dto.SupplierDto
import com.supermarket.inventory.data.remote.dto.SupplierInvoiceDto
import com.supermarket.inventory.ui.common.formatAmount
import com.supermarket.inventory.ui.common.formatIsoDate
import com.supermarket.inventory.ui.theme.LossRed
import com.supermarket.inventory.ui.theme.ProfitGreen
import com.supermarket.inventory.ui.theme.WarningAmber
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoicesScreen(
    onOpenSuppliers: () -> Unit,
    onOpenExpenses: () -> Unit,
    onOpenAssets: () -> Unit,
    onOpenDeferredSales: () -> Unit,
    onOpenCashRegister: () -> Unit,
    viewModel: InvoicesViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState
    var showAddDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var payInvoiceTarget by remember { mutableStateOf<SupplierInvoiceDto?>(null) }
    var invoiceToEdit by remember { mutableStateOf<SupplierInvoiceDto?>(null) }
    var invoiceToDelete by remember { mutableStateOf<SupplierInvoiceDto?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.invoices_title)) },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.suppliers_title)) }, onClick = { showMenu = false; onOpenSuppliers() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.expenses_title)) }, onClick = { showMenu = false; onOpenExpenses() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.assets_title)) }, onClick = { showMenu = false; onOpenAssets() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.deferred_sales_title)) }, onClick = { showMenu = false; onOpenDeferredSales() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.cash_register_title)) }, onClick = { showMenu = false; onOpenCashRegister() })
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.invoices_add))
            }
        },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.invoices.isEmpty() -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.inventory_empty))
            }
            else -> LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            ) {
                items(state.invoices.sortedBy { it.dueDate }, key = { it.id }) { invoice ->
                    InvoiceRow(
                        invoice = invoice,
                        onMarkPaid = { payInvoiceTarget = invoice },
                        onEdit = { invoiceToEdit = invoice },
                        onDelete = { invoiceToDelete = invoice },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    if (showAddDialog) {
        InvoiceDialog(
            viewModel = viewModel,
            title = stringResource(R.string.invoices_add),
            invoiceToEdit = null,
            onDismiss = { showAddDialog = false },
            onDone = { showAddDialog = false; viewModel.load() },
        )
    }

    invoiceToEdit?.let { invoice ->
        InvoiceDialog(
            viewModel = viewModel,
            title = stringResource(R.string.invoice_edit),
            invoiceToEdit = invoice,
            onDismiss = { invoiceToEdit = null },
            onDone = { invoiceToEdit = null; viewModel.load() },
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

    payInvoiceTarget?.let { invoice ->
        PayInvoiceDialog(
            invoice = invoice,
            onDismiss = { payInvoiceTarget = null },
            onConfirm = { payFromCashRegister ->
                viewModel.markPaid(invoice.id, payFromCashRegister)
                payInvoiceTarget = null
            },
        )
    }
}

@Composable
private fun InvoiceRow(invoice: SupplierInvoiceDto, onMarkPaid: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val now = Instant.now()
    val dueInstant = try { Instant.parse(invoice.dueDate) } catch (_: Exception) { now }
    val isOverdue = invoice.status == "PENDING" && dueInstant.isBefore(now)
    val statusColor = when {
        invoice.status == "PAID" -> ProfitGreen
        isOverdue -> LossRed
        else -> WarningAmber
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(invoice.supplier?.name ?: invoice.supplierId, style = MaterialTheme.typography.titleMedium)
                }
                Text(formatAmount(invoice.amount), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit)) }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete)) }
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${stringResource(R.string.invoice_due_date)}: ${formatIsoDate(invoice.dueDate)}",
                    color = statusColor,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (invoice.status == "PENDING") {
                    TextButton(onClick = onMarkPaid) { Text(stringResource(R.string.invoice_mark_paid)) }
                } else {
                    Text(stringResource(R.string.invoice_status_paid), color = ProfitGreen, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun PayInvoiceDialog(invoice: SupplierInvoiceDto, onDismiss: () -> Unit, onConfirm: (Boolean) -> Unit) {
    var payFromCashRegister by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.invoice_mark_paid)) },
        text = {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = payFromCashRegister, onCheckedChange = { payFromCashRegister = it })
                Text(stringResource(R.string.invoice_pay_from_cash_register))
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(payFromCashRegister) }) { Text(stringResource(R.string.action_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InvoiceDialog(
    viewModel: InvoicesViewModel,
    title: String,
    invoiceToEdit: SupplierInvoiceDto?,
    onDismiss: () -> Unit,
    onDone: () -> Unit,
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
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        when (val result = viewModel.loadSuppliers()) {
            is com.supermarket.inventory.data.ApiResult.Success -> {
                suppliers = result.data
                if (invoiceToEdit != null && selectedSupplier == null) {
                    selectedSupplier = result.data.find { it.id == invoiceToEdit.supplierId }
                }
            }
            is com.supermarket.inventory.data.ApiResult.Error -> error = result.message
        }
    }

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
                            viewModel.updateInvoice(invoiceToEdit.id, supplier.id, invoiceNumber.ifBlank { null }, amountValue, dueIso, null)
                        } else {
                            viewModel.createInvoice(supplier.id, invoiceNumber.ifBlank { null }, amountValue, dueIso, null)
                        }
                        when (result) {
                            is com.supermarket.inventory.data.ApiResult.Success -> onDone()
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
}
