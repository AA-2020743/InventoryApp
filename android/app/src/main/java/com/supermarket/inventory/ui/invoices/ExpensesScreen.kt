package com.supermarket.inventory.ui.invoices

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.supermarket.inventory.R
import com.supermarket.inventory.data.ApiResult
import com.supermarket.inventory.data.remote.dto.ExpenseDto
import com.supermarket.inventory.data.repository.ExpenseRepository
import com.supermarket.inventory.ui.common.formatAmount
import com.supermarket.inventory.ui.common.formatIsoDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class ExpensesUiState(val isLoading: Boolean = true, val expenses: List<ExpenseDto> = emptyList())

@HiltViewModel
class ExpensesViewModel @Inject constructor(private val repository: ExpenseRepository) : ViewModel() {
    var uiState by mutableStateOf(ExpensesUiState())
        private set

    init { load() }

    fun load() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true)
            when (val result = repository.getExpenses()) {
                is ApiResult.Success -> uiState = uiState.copy(isLoading = false, expenses = result.data)
                is ApiResult.Error -> uiState = uiState.copy(isLoading = false)
            }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            repository.deleteExpense(id)
            load()
        }
    }

    suspend fun create(
        name: String,
        amount: Double,
        frequency: String,
        startDate: String?,
        paymentDayOfMonth: Int?,
        fromCashRegister: Boolean,
    ) = repository.createExpense(name, amount, frequency, startDate, null, paymentDayOfMonth, fromCashRegister)

    suspend fun update(
        id: String,
        name: String,
        amount: Double,
        frequency: String,
        startDate: String?,
        paymentDayOfMonth: Int?,
        fromCashRegister: Boolean,
    ) = repository.updateExpense(id, name, amount, frequency, startDate, null, paymentDayOfMonth, fromCashRegister)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(onBack: () -> Unit, viewModel: ExpensesViewModel = hiltViewModel()) {
    val state = viewModel.uiState
    var showAddDialog by remember { mutableStateOf(false) }
    var expenseToEdit by remember { mutableStateOf<ExpenseDto?>(null) }
    var expenseToDelete by remember { mutableStateOf<ExpenseDto?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.expenses_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = null) } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.expense_add)) }
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                items(state.expenses, key = { it.id }) { expense ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(expense.name, style = MaterialTheme.typography.titleMedium)
                                val detail = buildString {
                                    append(formatAmount(expense.amount))
                                    append(" / ")
                                    append(frequencyLabel(expense.frequency))
                                    if (expense.frequency == "ONE_TIME") {
                                        append(" — ")
                                        append(formatIsoDate(expense.startDate))
                                    }
                                    if (expense.frequency == "MONTHLY") {
                                        append(" — ")
                                        append(stringResource(R.string.expense_payment_day_short, expense.paymentDayOfMonth ?: 1))
                                    }
                                }
                                Text(detail, style = MaterialTheme.typography.bodySmall)
                                if (expense.fromCashRegister) {
                                    Text(
                                        stringResource(R.string.expense_from_cash_register_badge),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            IconButton(onClick = { expenseToEdit = expense }) {
                                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit))
                            }
                            IconButton(onClick = { expenseToDelete = expense }) {
                                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        ExpenseDialog(
            title = stringResource(R.string.expense_add),
            initialName = "",
            initialAmount = "",
            initialFrequency = "MONTHLY",
            initialStartDateIso = null,
            initialPaymentDayOfMonth = 1,
            initialFromCashRegister = false,
            onDismiss = { showAddDialog = false },
            onSave = { name, amount, frequency, startDate, paymentDayOfMonth, fromCashRegister ->
                viewModel.viewModelScope.launch {
                    viewModel.create(name, amount, frequency, startDate, paymentDayOfMonth, fromCashRegister)
                    viewModel.load()
                }
                showAddDialog = false
            },
        )
    }

    expenseToEdit?.let { expense ->
        ExpenseDialog(
            title = stringResource(R.string.expense_edit),
            initialName = expense.name,
            initialAmount = expense.amount,
            initialFrequency = expense.frequency,
            initialStartDateIso = expense.startDate,
            initialPaymentDayOfMonth = expense.paymentDayOfMonth ?: 1,
            initialFromCashRegister = expense.fromCashRegister,
            onDismiss = { expenseToEdit = null },
            onSave = { name, amount, frequency, startDate, paymentDayOfMonth, fromCashRegister ->
                viewModel.viewModelScope.launch {
                    viewModel.update(expense.id, name, amount, frequency, startDate, paymentDayOfMonth, fromCashRegister)
                    viewModel.load()
                }
                expenseToEdit = null
            },
        )
    }

    expenseToDelete?.let { expense ->
        AlertDialog(
            onDismissRequest = { expenseToDelete = null },
            title = { Text(stringResource(R.string.confirm_delete_named_title, expense.name)) },
            text = { Text(stringResource(R.string.confirm_delete_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(expense.id); expenseToDelete = null }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = { TextButton(onClick = { expenseToDelete = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun frequencyLabel(frequency: String): String = when (frequency) {
    "DAILY" -> stringResource(R.string.expense_frequency_daily)
    "MONTHLY" -> stringResource(R.string.expense_frequency_monthly)
    else -> stringResource(R.string.expense_frequency_one_time)
}

// Not private: reused by StatsScreen's per-day expenses view to edit a
// ONE_TIME expense inline without duplicating this dialog.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseDialog(
    title: String,
    initialName: String,
    initialAmount: String,
    initialFrequency: String,
    initialStartDateIso: String?,
    initialPaymentDayOfMonth: Int,
    initialFromCashRegister: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, Double, String, String?, Int?, Boolean) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var amount by remember { mutableStateOf(initialAmount) }
    var frequency by remember { mutableStateOf(initialFrequency) }
    var frequencyExpanded by remember { mutableStateOf(false) }
    var dayExpanded by remember { mutableStateOf(false) }
    var paymentDayOfMonth by remember { mutableStateOf(initialPaymentDayOfMonth) }
    var fromCashRegister by remember { mutableStateOf(initialFromCashRegister) }
    var showDatePicker by remember { mutableStateOf(false) }
    var startDateMillis by remember {
        mutableStateOf(
            initialStartDateIso?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                ?: System.currentTimeMillis()
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.expense_name)) }, singleLine = true)
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text(stringResource(R.string.expense_amount)) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.padding(top = 8.dp),
                )
                ExposedDropdownMenuBox(expanded = frequencyExpanded, onExpandedChange = { frequencyExpanded = it }, modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = frequencyLabel(frequency),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.expense_frequency)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = frequencyExpanded) },
                        modifier = Modifier.menuAnchor(),
                    )
                    DropdownMenu(expanded = frequencyExpanded, onDismissRequest = { frequencyExpanded = false }) {
                        listOf("DAILY", "MONTHLY", "ONE_TIME").forEach { option ->
                            DropdownMenuItem(text = { Text(frequencyLabel(option)) }, onClick = { frequency = option; frequencyExpanded = false })
                        }
                    }
                }

                if (frequency == "ONE_TIME") {
                    OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.padding(top = 8.dp)) {
                        Text(formatIsoDate(Instant.ofEpochMilli(startDateMillis).toString()))
                    }
                }

                if (frequency == "MONTHLY") {
                    ExposedDropdownMenuBox(expanded = dayExpanded, onExpandedChange = { dayExpanded = it }, modifier = Modifier.padding(top = 8.dp)) {
                        OutlinedTextField(
                            value = stringResource(R.string.expense_payment_day_short, paymentDayOfMonth),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.expense_payment_day_label)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dayExpanded) },
                            modifier = Modifier.menuAnchor(),
                        )
                        DropdownMenu(expanded = dayExpanded, onDismissRequest = { dayExpanded = false }) {
                            (1..31).forEach { day ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.expense_payment_day_short, day)) },
                                    onClick = { paymentDayOfMonth = day; dayExpanded = false },
                                )
                            }
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = fromCashRegister, onCheckedChange = { fromCashRegister = it })
                    Text(stringResource(R.string.expense_from_cash_register))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amountValue = amount.toDoubleOrNull()
                if (name.isNotBlank() && amountValue != null) {
                    val startDateIso = if (frequency == "ONE_TIME") Instant.ofEpochMilli(startDateMillis).toString() else null
                    val dayOfMonth = if (frequency == "MONTHLY") paymentDayOfMonth else null
                    onSave(name, amountValue, frequency, startDateIso, dayOfMonth, fromCashRegister)
                }
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = startDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { startDateMillis = it }
                    showDatePicker = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
