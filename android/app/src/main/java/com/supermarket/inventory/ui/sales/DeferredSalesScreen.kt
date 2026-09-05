package com.supermarket.inventory.ui.sales

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import com.supermarket.inventory.R
import com.supermarket.inventory.data.ApiResult
import com.supermarket.inventory.data.remote.dto.SaleDto
import com.supermarket.inventory.ui.common.MonthGroupHeader
import com.supermarket.inventory.ui.common.PeriodSummaryCard
import com.supermarket.inventory.ui.common.PeriodTabs
import com.supermarket.inventory.ui.common.formatAmount
import com.supermarket.inventory.ui.common.formatIsoDate
import com.supermarket.inventory.ui.common.formatIsoDateTime
import com.supermarket.inventory.ui.common.formatMonth
import com.supermarket.inventory.ui.common.groupByMonth
import com.supermarket.inventory.ui.theme.profitColor
import java.time.YearMonth
import java.util.Locale
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Schedule
import com.supermarket.inventory.ui.common.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeferredSalesTabContent(viewModel: DeferredSalesViewModel = hiltViewModel()) {
    val state = viewModel.uiState
    var showAddDialog by remember { mutableStateOf(false) }

    // Sales sharing the same (non-blank) customer name are one running tab
    // in the owner's eyes even though each checkout/manual entry is its own
    // Sale record underneath - grouped here into a single merged row per
    // customer, sorted alphabetically. A blank/missing customer name can't
    // be assumed to be the same person twice, so those stay ungrouped.
    val namedGroups = remember(state.sales) {
        state.sales
            .filter { !it.customerName.isNullOrBlank() }
            .groupBy { it.customerName!!.trim() }
            .toSortedMap()
    }
    val unnamedSales = remember(state.sales) { state.sales.filter { it.customerName.isNullOrBlank() } }

    // Settled tabs, folded by the month they were collected in.
    val locale = Locale.getDefault()
    val collectedMonths = remember(state.collected) {
        groupByMonth(state.collected, { it.collectedAt ?: it.createdAt }, { it.totalAmount.toDoubleOrNull() ?: 0.0 })
    }
    val collectedTotal = collectedMonths.sumOf { it.total }
    var showHistory by remember { mutableStateOf(false) }
    var expandedMonths by remember { mutableStateOf(emptySet<YearMonth>()) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
        if (state.collected.isNotEmpty()) {
            PeriodTabs(
                currentText = stringResource(R.string.deferred_tab_open, state.sales.size),
                historyText = stringResource(R.string.deferred_tab_collected, state.collected.size),
                showHistory = showHistory,
                onChange = { showHistory = it },
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            showHistory -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 88.dp),
            ) {
                item(key = "collected-summary") {
                    PeriodSummaryCard(
                        label = stringResource(R.string.deferred_collected_label),
                        detail = stringResource(R.string.history_entry_count, state.collected.size),
                        total = formatAmount(collectedTotal.toString()),
                        accent = profitColor(),
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                collectedMonths.forEach { bucket ->
                    val isOpen = bucket.yearMonth in expandedMonths
                    item(key = "m-${bucket.yearMonth}") {
                        MonthGroupHeader(
                            label = formatMonth(bucket.yearMonth, locale),
                            detail = stringResource(R.string.history_entry_count, bucket.items.size),
                            total = formatAmount(bucket.total.toString()),
                            expanded = isOpen,
                            accent = profitColor(),
                            onToggle = {
                                expandedMonths = if (isOpen) expandedMonths - bucket.yearMonth
                                else expandedMonths + bucket.yearMonth
                            },
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    if (isOpen) {
                        items(bucket.items, key = { it.id }) { sale ->
                            CollectedSaleRow(sale)
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }
            state.sales.isEmpty() -> EmptyState(
                icon = Icons.Filled.Schedule,
                title = stringResource(R.string.deferred_sales_empty),
                hint = stringResource(R.string.deferred_sales_empty_hint),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 88.dp),
            ) {
                items(namedGroups.entries.toList(), key = { it.key }) { (customerName, sales) ->
                    CustomerDeferredGroupCard(
                        customerName = customerName,
                        sales = sales,
                        onCollectAll = {
                            viewModel.viewModelScope.launch { viewModel.collectGroup(sales.map { it.id }) }
                        },
                        onCollectPartial = { amount ->
                            viewModel.viewModelScope.launch { viewModel.collectPartialGroup(sales, amount) }
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                }
                items(unnamedSales, key = { it.id }) { sale ->
                    DeferredSaleRow(
                        sale = sale,
                        onCollect = { viewModel.collect(sale.id) },
                        onCollectPartial = { amount ->
                            viewModel.viewModelScope.launch { viewModel.collectPartial(sale.id, amount) }
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
        ) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.deferred_sale_add)) }
    }

    if (showAddDialog) {
        AddDeferredSaleDialog(
            customerSuggestions = state.customerSuggestions,
            onDismiss = { showAddDialog = false },
            onSave = { amount, customerName ->
                viewModel.viewModelScope.launch {
                    when (viewModel.addManualDeferredSale(amount, customerName)) {
                        is ApiResult.Success -> viewModel.load()
                        is ApiResult.Error -> Unit
                    }
                }
                showAddDialog = false
            },
        )
    }
}

// A customer's merged tab: one headline total (the sum of every underlying
// deferred sale's remaining balance) with "collect" actions that apply
// across all of them, and an expandable log of the individual entries that
// add up to it - so adding a new deferred amount for a repeat customer
// just grows this one total instead of piling up separate rows.
// A deferred tab that has since been settled. Nothing left to collect - it
// is here so "who paid up, and when" has an answer.
@Composable
private fun CollectedSaleRow(sale: SaleDto) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    sale.customerName?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.deferred_sale_no_customer),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.deferred_collected_on, formatIsoDate(sale.collectedAt ?: sale.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(formatAmount(sale.totalAmount), style = MaterialTheme.typography.bodyMedium, color = profitColor())
        }
    }
}

@Composable
private fun CustomerDeferredGroupCard(
    customerName: String,
    sales: List<SaleDto>,
    onCollectAll: () -> Unit,
    onCollectPartial: (Double) -> Unit,
) {
    val totalRemaining = sales.sumOf { sale ->
        val total = sale.totalAmount.toDoubleOrNull() ?: 0.0
        val collected = sale.amountCollected.toDoubleOrNull() ?: 0.0
        (total - collected).coerceAtLeast(0.0)
    }
    var expanded by remember { mutableStateOf(false) }
    var showPartialDialog by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(customerName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.deferred_sale_remaining, formatAmount(totalRemaining.toString())),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = stringResource(if (expanded) R.string.action_collapse else R.string.action_expand),
                    )
                }
            }
            if (expanded) {
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    stringResource(R.string.deferred_sale_history_title),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                sales.sortedByDescending { it.createdAt }.forEach { sale -> DeferredHistoryLogRow(sale) }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { showPartialDialog = true }) { Text(stringResource(R.string.deferred_sale_collect_partial)) }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onCollectAll) { Text(stringResource(R.string.deferred_sale_mark_collected)) }
            }
        }
    }

    if (showPartialDialog) {
        PartialCollectDialog(
            remaining = totalRemaining,
            onDismiss = { showPartialDialog = false },
            onConfirm = { amount -> onCollectPartial(amount); showPartialDialog = false },
        )
    }
}

@Composable
private fun DeferredHistoryLogRow(sale: SaleDto) {
    val total = sale.totalAmount.toDoubleOrNull() ?: 0.0
    val collected = sale.amountCollected.toDoubleOrNull() ?: 0.0
    val remaining = (total - collected).coerceAtLeast(0.0)
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatIsoDateTime(sale.createdAt), style = MaterialTheme.typography.bodySmall)
        Text(
            if (collected > 0) {
                stringResource(R.string.deferred_sale_history_entry_partial, formatAmount(sale.totalAmount), formatAmount(remaining.toString()))
            } else {
                formatAmount(sale.totalAmount)
            },
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun DeferredSaleRow(sale: SaleDto, onCollect: () -> Unit, onCollectPartial: (Double) -> Unit) {
    val totalAmount = sale.totalAmount.toDoubleOrNull() ?: 0.0
    val collected = sale.amountCollected.toDoubleOrNull() ?: 0.0
    val remaining = (totalAmount - collected).coerceAtLeast(0.0)
    var showPartialDialog by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatIsoDateTime(sale.createdAt), style = MaterialTheme.typography.bodyMedium)
                Text(formatAmount(sale.totalAmount), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.deferred_sale_customer_unknown),
                style = MaterialTheme.typography.bodySmall,
            )
            if (collected > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.deferred_sale_remaining, formatAmount(remaining.toString())),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { showPartialDialog = true }) { Text(stringResource(R.string.deferred_sale_collect_partial)) }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onCollect) { Text(stringResource(R.string.deferred_sale_mark_collected)) }
            }
        }
    }

    if (showPartialDialog) {
        PartialCollectDialog(
            remaining = remaining,
            onDismiss = { showPartialDialog = false },
            onConfirm = { amount -> onCollectPartial(amount); showPartialDialog = false },
        )
    }
}

// Lets the owner record a real partial payment toward a customer's tab
// (capped at what's actually still owed) instead of only being able to
// mark the whole deferred sale collected at once.
@Composable
private fun PartialCollectDialog(remaining: Double, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val invalidMessage = stringResource(R.string.deferred_sale_invalid_amount)
    val exceedsMessage = stringResource(R.string.deferred_sale_partial_exceeds_remaining)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.deferred_sale_collect_partial)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.deferred_sale_remaining, formatAmount(remaining.toString())),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it; error = null },
                    label = { Text(stringResource(R.string.deferred_sale_amount)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.padding(top = 8.dp),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val value = amount.toDoubleOrNull()
                when {
                    value == null || value <= 0 -> error = invalidMessage
                    value > remaining -> error = exceedsMessage
                    else -> onConfirm(value)
                }
            }) { Text(stringResource(R.string.action_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

// Records a customer's owed total directly - for a tab/IOU that isn't tied
// to specific inventory - without going through the Sell screen's checkout
// flow. It's still created as a DEFERRED sale server-side, so it shows up
// in revenue/receivables (and the dashboard's net valuation) the same way
// a normal deferred sale would - and, once created, merges into that
// customer's existing group on this screen if they already have one.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddDeferredSaleDialog(
    customerSuggestions: List<String>,
    onDismiss: () -> Unit,
    onSave: (Double, String?) -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    var customerName by remember { mutableStateOf("") }
    var customerExpanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val invalidAmountMessage = stringResource(R.string.deferred_sale_invalid_amount)
    val filteredCustomers = customerSuggestions.filter {
        customerName.isBlank() || it.contains(customerName, ignoreCase = true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.deferred_sale_add)) },
        text = {
            Column {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text(stringResource(R.string.deferred_sale_amount)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                ExposedDropdownMenuBox(
                    expanded = customerExpanded && filteredCustomers.isNotEmpty(),
                    onExpandedChange = { customerExpanded = it },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    OutlinedTextField(
                        value = customerName,
                        onValueChange = { customerName = it; customerExpanded = true },
                        label = { Text(stringResource(R.string.sales_customer_name_label)) },
                        singleLine = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = customerExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    DropdownMenu(
                        expanded = customerExpanded && filteredCustomers.isNotEmpty(),
                        onDismissRequest = { customerExpanded = false },
                    ) {
                        filteredCustomers.forEach { suggestion ->
                            DropdownMenuItem(
                                text = { Text(suggestion) },
                                onClick = { customerName = suggestion; customerExpanded = false },
                            )
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amountValue = amount.toDoubleOrNull()
                if (amountValue == null || amountValue <= 0) {
                    error = invalidAmountMessage
                } else {
                    onSave(amountValue, customerName.ifBlank { null })
                }
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
