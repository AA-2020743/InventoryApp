package com.supermarket.inventory.ui.othersales

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.supermarket.inventory.R
import com.supermarket.inventory.data.ApiResult
import com.supermarket.inventory.data.remote.dto.OtherSaleDto
import com.supermarket.inventory.data.repository.OtherSaleRepository
import com.supermarket.inventory.ui.common.MonthGroupHeader
import com.supermarket.inventory.ui.common.PeriodSummaryCard
import com.supermarket.inventory.ui.common.PeriodTabs
import com.supermarket.inventory.ui.common.formatAmount
import com.supermarket.inventory.ui.common.formatIsoDate
import com.supermarket.inventory.ui.common.formatMonth
import com.supermarket.inventory.ui.common.groupByMonth
import com.supermarket.inventory.ui.theme.profitColor
import java.time.YearMonth
import java.util.Locale
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import androidx.compose.material.icons.filled.Savings
import com.supermarket.inventory.ui.common.EmptyState
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

data class OtherSalesUiState(
    val isLoading: Boolean = true,
    val entries: List<OtherSaleDto> = emptyList(),
    val categories: List<String> = emptyList(),
)

@HiltViewModel
class OtherSalesViewModel @Inject constructor(private val repository: OtherSaleRepository) : ViewModel() {
    var uiState by mutableStateOf(OtherSalesUiState())
        private set

    init { load() }

    fun load() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true)
            when (val result = repository.getOtherSales()) {
                is ApiResult.Success -> uiState = uiState.copy(isLoading = false, entries = result.data)
                is ApiResult.Error -> uiState = uiState.copy(isLoading = false)
            }
            when (val catResult = repository.getCategories()) {
                is ApiResult.Success -> uiState = uiState.copy(categories = catResult.data)
                is ApiResult.Error -> Unit
            }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            repository.delete(id)
            load()
        }
    }

    suspend fun create(amount: Double, category: String?, notes: String?, date: String?) =
        repository.create(amount, category, notes, date)

    suspend fun update(id: String, amount: Double, category: String?, notes: String?, date: String?) =
        repository.update(id, amount, category, notes, date)
}

// Miscellaneous profit not tied to inventory or a checkout sale (a service
// fee, a one-off arrangement, etc.) - always credits the cash register in
// full immediately, and feeds the day/month's revenue and profit on the
// dashboard the same way a sale's revenue does.
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OtherSalesTabContent(viewModel: OtherSalesViewModel = hiltViewModel()) {
    val state = viewModel.uiState
    var showAddDialog by remember { mutableStateOf(false) }
    // Set when the add dialog was opened via a category quick-add chip, so
    // the dialog starts with that category already filled in and the owner
    // only has to enter the amount.
    var prefilledCategory by remember { mutableStateOf("") }
    var entryToEdit by remember { mutableStateOf<OtherSaleDto?>(null) }
    var entryToDelete by remember { mutableStateOf<OtherSaleDto?>(null) }

    // Same shelving as expenses: the month in progress stays a plain list,
    // older months fold into one card each.
    val locale = Locale.getDefault()
    val buckets = remember(state.entries) {
        groupByMonth(state.entries, { it.date }, { it.amount.toDoubleOrNull() ?: 0.0 })
    }
    val thisMonth = remember(buckets) { YearMonth.now() }
    val current = buckets.firstOrNull { it.yearMonth == thisMonth }
    val earlier = buckets.filter { it.yearMonth != thisMonth }
    val earlierCount = earlier.sumOf { it.items.size }
    val earlierTotal = earlier.sumOf { it.total }
    var showHistory by remember { mutableStateOf(false) }
    var expandedMonths by remember { mutableStateOf(emptySet<YearMonth>()) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // One chip per category already in use - the common case is
            // logging another entry of a kind that's been logged before, so
            // these skip straight past picking/retyping the category.
            if (state.categories.isNotEmpty()) {
                Text(
                    stringResource(R.string.other_sale_quick_add),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 12.dp, top = 12.dp),
                )
                // Wraps rather than scrolling sideways, same as the expense
                // chips, and at full height - all of them visible at once
                // instead of a band you have to scroll inside.
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    state.categories.forEach { category ->
                        AssistChip(
                            onClick = { prefilledCategory = category; showAddDialog = true },
                            label = { Text(category) },
                            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        )
                    }
                }
            }
            if (earlier.isNotEmpty()) {
                PeriodTabs(
                    currentText = stringResource(
                        R.string.history_tab_current,
                        formatMonth(thisMonth, locale),
                        current?.items?.size ?: 0,
                    ),
                    historyText = stringResource(R.string.history_tab_earlier, earlierCount),
                    showHistory = showHistory,
                    onChange = { showHistory = it },
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            // weight(1f) rather than fillMaxSize(): the chip row above already
            // consumed part of the column, so the list has to take what's
            // left over instead of asking for the full height and overflowing.
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    state.entries.isEmpty() -> EmptyState(
                        icon = Icons.Filled.Savings,
                        title = stringResource(R.string.other_sales_empty),
                        hint = stringResource(R.string.other_sales_empty_hint),
                    )
                    // Bottom padding so the add button doesn't sit on top of
                    // the last row's controls.
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 88.dp),
                    ) {
                        if (!showHistory) {
                            item(key = "summary") {
                                PeriodSummaryCard(
                                    label = formatMonth(thisMonth, locale),
                                    detail = stringResource(R.string.history_entry_count, current?.items?.size ?: 0),
                                    total = formatAmount((current?.total ?: 0.0).toString()),
                                    accent = profitColor(),
                                    modifier = Modifier.padding(bottom = 8.dp),
                                )
                            }
                            if (current == null) {
                                item(key = "empty-month") {
                                    Text(
                                        stringResource(R.string.history_nothing_this_month),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            items(current?.items.orEmpty(), key = { it.id }) { entry ->
                                OtherSaleRow(entry, onEdit = { entryToEdit = entry }, onDelete = { entryToDelete = entry })
                            }
                        } else {
                            item(key = "summary-history") {
                                PeriodSummaryCard(
                                    label = stringResource(R.string.history_earlier_label),
                                    detail = stringResource(R.string.history_entry_count, earlierCount),
                                    total = formatAmount(earlierTotal.toString()),
                                    accent = profitColor(),
                                    modifier = Modifier.padding(bottom = 8.dp),
                                )
                            }
                            earlier.forEach { bucket ->
                                val isOpen = bucket.yearMonth in expandedMonths
                                item(key = "m-${bucket.yearMonth}") {
                                    MonthGroupHeader(
                                        label = formatMonth(bucket.yearMonth, locale),
                                        detail = stringResource(R.string.history_entry_count, bucket.items.size),
                                        total = formatAmount(bucket.total.toString()),
                                        expanded = isOpen,
                                        accent = profitColor(),
                                        onToggle = {
                                            expandedMonths = if (isOpen) {
                                                expandedMonths - bucket.yearMonth
                                            } else {
                                                expandedMonths + bucket.yearMonth
                                            }
                                        },
                                        modifier = Modifier.padding(bottom = 6.dp),
                                    )
                                }
                                if (isOpen) {
                                    items(bucket.items, key = { it.id }) { entry ->
                                        OtherSaleRow(entry, onEdit = { entryToEdit = entry }, onDelete = { entryToDelete = entry })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { prefilledCategory = ""; showAddDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.other_sale_add)) }
    }

    if (showAddDialog) {
        OtherSaleDialog(
            title = stringResource(R.string.other_sale_add),
            initialAmount = "",
            initialCategory = prefilledCategory,
            initialNotes = "",
            initialDateIso = null,
            categorySuggestions = state.categories,
            onDismiss = { showAddDialog = false },
            onSave = { amount, category, notes, date ->
                viewModel.viewModelScope.launch {
                    viewModel.create(amount, category, notes, date)
                    viewModel.load()
                }
                showAddDialog = false
            },
        )
    }

    entryToEdit?.let { entry ->
        OtherSaleDialog(
            title = stringResource(R.string.other_sale_edit),
            initialAmount = entry.amount,
            initialCategory = entry.category ?: "",
            initialNotes = entry.notes ?: "",
            initialDateIso = entry.date,
            categorySuggestions = state.categories,
            onDismiss = { entryToEdit = null },
            onSave = { amount, category, notes, date ->
                viewModel.viewModelScope.launch {
                    viewModel.update(entry.id, amount, category, notes, date)
                    viewModel.load()
                }
                entryToEdit = null
            },
        )
    }

    entryToDelete?.let { entry ->
        val identifier = entry.category?.takeIf { it.isNotBlank() } ?: formatAmount(entry.amount)
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text(stringResource(R.string.confirm_delete_named_title, identifier)) },
            text = { Text(stringResource(R.string.confirm_delete_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(entry.id); entryToDelete = null }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = { TextButton(onClick = { entryToDelete = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun OtherSaleRow(entry: OtherSaleDto, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    entry.category?.takeIf { it.isNotBlank() } ?: stringResource(R.string.other_sale_uncategorized),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(formatIsoDate(entry.date), style = MaterialTheme.typography.bodySmall)
                entry.notes?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
            }
            Text(formatAmount(entry.amount), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit)) }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OtherSaleDialog(
    title: String,
    initialAmount: String,
    initialCategory: String,
    initialNotes: String,
    initialDateIso: String?,
    categorySuggestions: List<String>,
    onDismiss: () -> Unit,
    onSave: (Double, String?, String?, String?) -> Unit,
) {
    var amount by remember { mutableStateOf(initialAmount) }
    var category by remember { mutableStateOf(initialCategory) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf(initialNotes) }
    var showDatePicker by remember { mutableStateOf(false) }
    var dateMillis by remember {
        mutableStateOf(
            initialDateIso?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                ?: System.currentTimeMillis()
        )
    }
    val filteredCategories = categorySuggestions.filter {
        category.isBlank() || it.contains(category, ignoreCase = true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text(stringResource(R.string.other_sale_amount)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded && filteredCategories.isNotEmpty(),
                    onExpandedChange = { categoryExpanded = it },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it; categoryExpanded = true },
                        label = { Text(stringResource(R.string.other_sale_category)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    DropdownMenu(
                        expanded = categoryExpanded && filteredCategories.isNotEmpty(),
                        onDismissRequest = { categoryExpanded = false },
                    ) {
                        filteredCategories.forEach { suggestion ->
                            DropdownMenuItem(
                                text = { Text(suggestion) },
                                onClick = { category = suggestion; categoryExpanded = false },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.other_sale_notes)) },
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    stringResource(R.string.other_sale_date),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.padding(top = 4.dp)) {
                    Text(formatIsoDate(Instant.ofEpochMilli(dateMillis).toString()))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amountValue = amount.toDoubleOrNull()
                if (amountValue != null && amountValue > 0) {
                    onSave(
                        amountValue,
                        category.ifBlank { null },
                        notes.ifBlank { null },
                        Instant.ofEpochMilli(dateMillis).toString(),
                    )
                }
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { dateMillis = it }
                    showDatePicker = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
