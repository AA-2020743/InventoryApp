package com.supermarket.inventory.ui.debts

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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.supermarket.inventory.R
import com.supermarket.inventory.data.ApiResult
import com.supermarket.inventory.data.remote.dto.DebtDto
import com.supermarket.inventory.data.repository.DebtRepository
import com.supermarket.inventory.ui.common.MonthGroupHeader
import com.supermarket.inventory.ui.common.PeriodSummaryCard
import com.supermarket.inventory.ui.common.PeriodTabs
import com.supermarket.inventory.ui.common.formatAmount
import com.supermarket.inventory.ui.common.formatIsoDate
import com.supermarket.inventory.ui.common.formatIsoDateTime
import com.supermarket.inventory.ui.common.formatMonth
import com.supermarket.inventory.ui.common.groupByMonth
import com.supermarket.inventory.ui.theme.profitColor
import com.supermarket.inventory.ui.theme.warningColor
import java.time.YearMonth
import java.util.Locale
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.compose.material.icons.filled.AccountBalanceWallet
import com.supermarket.inventory.ui.common.EmptyState

data class DebtsUiState(
    val isLoading: Boolean = true,
    val debts: List<DebtDto> = emptyList(),
    // Debts already paid back in full, kept for the Settled tab.
    val settled: List<DebtDto> = emptyList(),
    val workerSuggestions: List<String> = emptyList(),
)

@HiltViewModel
class DebtsViewModel @Inject constructor(
    private val repository: DebtRepository,
) : ViewModel() {

    var uiState by mutableStateOf(DebtsUiState())
        private set

    init {
        load()
        loadWorkerSuggestions()
    }

    fun load() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true)
            when (val result = repository.getDebts(status = "OUTSTANDING")) {
                is ApiResult.Success -> uiState = uiState.copy(isLoading = false, debts = result.data)
                is ApiResult.Error -> uiState = uiState.copy(isLoading = false)
            }
            // Settled debts used to disappear the moment they were repaid,
            // leaving no way to answer "did he ever pay that back?". They
            // live in the Settled tab now, folded by the month they were
            // repaid in.
            when (val settledResult = repository.getDebts(status = "REPAID")) {
                is ApiResult.Success -> uiState = uiState.copy(settled = settledResult.data)
                is ApiResult.Error -> Unit
            }
        }
    }

    private fun loadWorkerSuggestions() {
        viewModelScope.launch {
            when (val result = repository.getWorkerNames()) {
                is ApiResult.Success -> uiState = uiState.copy(workerSuggestions = result.data)
                is ApiResult.Error -> Unit
            }
        }
    }

    suspend fun addDebt(workerName: String, amount: Double): ApiResult<DebtDto> =
        repository.createDebt(workerName, amount)

    // A worker's merged tab can span several separate debts - "repay all"
    // on that merged row settles every one of them in full, same as
    // collectGroup does for a customer's deferred-sale tab.
    suspend fun repayGroup(debtIds: List<String>): ApiResult<Unit> {
        for (id in debtIds) {
            val result = repository.repayDebt(id)
            if (result is ApiResult.Error) return result
        }
        load()
        return ApiResult.Success(Unit)
    }

    // Applies one entered amount across a worker's merged tab, oldest debt
    // first, spilling into the next debt once the current one is fully
    // covered - mirrors collectPartialGroup in DeferredSalesViewModel.
    suspend fun repayPartialGroup(debts: List<DebtDto>, amount: Double): ApiResult<Unit> {
        var remaining = amount
        for (debt in debts.sortedBy { it.createdAt }) {
            if (remaining <= 0) break
            val owed = (debt.amount.toDoubleOrNull() ?: 0.0) - (debt.amountRepaid.toDoubleOrNull() ?: 0.0)
            if (owed <= 0) continue
            val toApply = minOf(remaining, owed)
            val result = repository.repayDebtPartial(debt.id, toApply)
            if (result is ApiResult.Error) return result
            remaining -= toApply
        }
        load()
        return ApiResult.Success(Unit)
    }
}

// Cash lent to a worker out of the till - grouped and repaid the same way
// Deferred Sales groups and collects a customer's tab, but flowing in the
// opposite direction (money left the register when the debt was recorded,
// rather than arriving later) and deliberately never touching revenue or
// profit - only the dashboard's net-worth composition.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtsTabContent(viewModel: DebtsViewModel = hiltViewModel()) {
    val state = viewModel.uiState
    var showAddDialog by remember { mutableStateOf(false) }
    var addError by remember { mutableStateOf<String?>(null) }

    val groups = remember(state.debts) {
        state.debts.groupBy { it.workerName.trim() }.toSortedMap()
    }
    // Settled debts, folded by the month they were paid back in - the record
    // of who cleared what, which the outstanding list can't hold.
    val locale = Locale.getDefault()
    val settledMonths = remember(state.settled) {
        groupByMonth(state.settled, { it.repaidAt ?: it.createdAt }, { it.amount.toDoubleOrNull() ?: 0.0 })
    }
    val settledTotal = settledMonths.sumOf { it.total }
    var showHistory by remember { mutableStateOf(false) }
    var expandedMonths by remember { mutableStateOf(emptySet<YearMonth>()) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
        if (state.settled.isNotEmpty()) {
            PeriodTabs(
                currentText = stringResource(R.string.debts_tab_outstanding, state.debts.size),
                historyText = stringResource(R.string.debts_tab_settled, state.settled.size),
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
                item(key = "settled-summary") {
                    PeriodSummaryCard(
                        label = stringResource(R.string.debts_settled_label),
                        detail = stringResource(R.string.history_entry_count, state.settled.size),
                        total = formatAmount(settledTotal.toString()),
                        accent = profitColor(),
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                settledMonths.forEach { bucket ->
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
                        items(bucket.items, key = { it.id }) { debt ->
                            SettledDebtRow(debt)
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }
            state.debts.isEmpty() -> EmptyState(
                icon = Icons.Filled.AccountBalanceWallet,
                title = stringResource(R.string.debts_empty),
                hint = stringResource(R.string.debts_empty_hint),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 88.dp),
            ) {
                items(groups.entries.toList(), key = { it.key }) { (workerName, debts) ->
                    WorkerDebtGroupCard(
                        workerName = workerName,
                        debts = debts,
                        onRepayAll = {
                            viewModel.viewModelScope.launch { viewModel.repayGroup(debts.map { it.id }) }
                        },
                        onRepayPartial = { amount ->
                            viewModel.viewModelScope.launch { viewModel.repayPartialGroup(debts, amount) }
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        }
        FloatingActionButton(
            onClick = { addError = null; showAddDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.debt_add)) }
    }

    if (showAddDialog) {
        AddDebtDialog(
            workerSuggestions = state.workerSuggestions,
            error = addError,
            onDismiss = { showAddDialog = false },
            onSave = { amount, workerName ->
                viewModel.viewModelScope.launch {
                    when (val result = viewModel.addDebt(workerName, amount)) {
                        is ApiResult.Success -> { viewModel.load(); showAddDialog = false }
                        is ApiResult.Error -> addError = result.message
                    }
                }
            },
        )
    }
}

// A debt that has been paid back in full. Nothing to act on - it's here so
// the question "did that ever come back?" has an answer.
@Composable
private fun SettledDebtRow(debt: DebtDto) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(debt.workerName, style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.debt_repaid_on, formatIsoDate(debt.repaidAt ?: debt.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(formatAmount(debt.amount), style = MaterialTheme.typography.bodyMedium, color = profitColor())
        }
    }
}

// A worker's merged tab: one headline total (the sum of every underlying
// debt's remaining balance) with repay actions that apply across all of
// them, and an expandable log of the individual entries that add up to it.
@Composable
private fun WorkerDebtGroupCard(
    workerName: String,
    debts: List<DebtDto>,
    onRepayAll: () -> Unit,
    onRepayPartial: (Double) -> Unit,
) {
    val totalRemaining = debts.sumOf { debt ->
        val total = debt.amount.toDoubleOrNull() ?: 0.0
        val repaid = debt.amountRepaid.toDoubleOrNull() ?: 0.0
        (total - repaid).coerceAtLeast(0.0)
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
                    Text(workerName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.debt_remaining, formatAmount(totalRemaining.toString())),
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
                    stringResource(R.string.debt_history_title),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                debts.sortedByDescending { it.createdAt }.forEach { debt -> DebtHistoryLogRow(debt) }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { showPartialDialog = true }) { Text(stringResource(R.string.debt_repay_partial)) }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onRepayAll) { Text(stringResource(R.string.debt_repay_all)) }
            }
        }
    }

    if (showPartialDialog) {
        PartialRepayDialog(
            remaining = totalRemaining,
            onDismiss = { showPartialDialog = false },
            onConfirm = { amount -> onRepayPartial(amount); showPartialDialog = false },
        )
    }
}

@Composable
private fun DebtHistoryLogRow(debt: DebtDto) {
    val total = debt.amount.toDoubleOrNull() ?: 0.0
    val repaid = debt.amountRepaid.toDoubleOrNull() ?: 0.0
    val remaining = (total - repaid).coerceAtLeast(0.0)
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatIsoDateTime(debt.createdAt), style = MaterialTheme.typography.bodySmall)
        Text(
            if (repaid > 0) {
                stringResource(R.string.debt_history_entry_partial, formatAmount(debt.amount), formatAmount(remaining.toString()))
            } else {
                formatAmount(debt.amount)
            },
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

// Lets the owner record a real partial repayment toward a worker's tab
// (capped at what's actually still owed) instead of only being able to
// mark the whole debt repaid at once.
@Composable
private fun PartialRepayDialog(remaining: Double, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val invalidMessage = stringResource(R.string.debt_invalid_amount)
    val exceedsMessage = stringResource(R.string.debt_partial_exceeds_remaining)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.debt_repay_partial)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.debt_remaining, formatAmount(remaining.toString())),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it; error = null },
                    label = { Text(stringResource(R.string.debt_amount)) },
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

// Records money lent to a worker out of the till - merges into that
// worker's existing group on this screen if they already have one owed.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddDebtDialog(
    workerSuggestions: List<String>,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (Double, String) -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    var workerName by remember { mutableStateOf("") }
    var workerExpanded by remember { mutableStateOf(false) }
    // Client-side validation (empty amount, missing name) takes priority
    // over a stale server error from a previous attempt.
    var localError by remember { mutableStateOf<String?>(null) }
    val displayedError = localError ?: error
    val invalidAmountMessage = stringResource(R.string.debt_invalid_amount)
    val missingWorkerMessage = stringResource(R.string.debt_worker_name_required)
    val filteredWorkers = workerSuggestions.filter {
        workerName.isBlank() || it.contains(workerName, ignoreCase = true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.debt_add)) },
        text = {
            Column {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text(stringResource(R.string.debt_amount)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                ExposedDropdownMenuBox(
                    expanded = workerExpanded && filteredWorkers.isNotEmpty(),
                    onExpandedChange = { workerExpanded = it },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    OutlinedTextField(
                        value = workerName,
                        onValueChange = { workerName = it; workerExpanded = true },
                        label = { Text(stringResource(R.string.debt_worker_name_label)) },
                        singleLine = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = workerExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    DropdownMenu(
                        expanded = workerExpanded && filteredWorkers.isNotEmpty(),
                        onDismissRequest = { workerExpanded = false },
                    ) {
                        filteredWorkers.forEach { suggestion ->
                            DropdownMenuItem(
                                text = { Text(suggestion) },
                                onClick = { workerName = suggestion; workerExpanded = false },
                            )
                        }
                    }
                }
                displayedError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amountValue = amount.toDoubleOrNull()
                when {
                    amountValue == null || amountValue <= 0 -> localError = invalidAmountMessage
                    workerName.isBlank() -> localError = missingWorkerMessage
                    else -> { localError = null; onSave(amountValue, workerName.trim()) }
                }
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
