package com.supermarket.inventory.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.supermarket.inventory.R
import com.supermarket.inventory.data.remote.dto.DashboardPeriodDto
import com.supermarket.inventory.data.remote.dto.DashboardSummaryDto
import com.supermarket.inventory.ui.common.formatAmount
import com.supermarket.inventory.ui.theme.LossRed
import com.supermarket.inventory.ui.theme.ProfitGreen
import com.supermarket.inventory.ui.theme.WarningAmber
import kotlinx.coroutines.delay

private const val AUTO_REFRESH_INTERVAL_MS = 8_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenSettings: () -> Unit,
    onViewInventory: () -> Unit,
    onViewInvoices: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState

    // Refreshes immediately whenever this screen (re-)enters composition -
    // e.g. switching back to the Dashboard tab after completing a sale -
    // not just on the ViewModel's first creation, then keeps polling while
    // the screen stays visible so figures stay live without a manual pull.
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.refresh()
            delay(AUTO_REFRESH_INTERVAL_MS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dashboard_title)) },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            ) { CircularProgressIndicator() }

            state.summary != null -> DashboardContent(
                summary = state.summary,
                modifier = Modifier.padding(padding),
                onViewInventory = onViewInventory,
                onViewInvoices = onViewInvoices,
            )

            state.error != null -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            ) {
                Text(state.error)
                Spacer(Modifier.height(12.dp))
                Button(onClick = viewModel::refresh) { Text(stringResource(R.string.action_retry)) }
            }
        }
    }

    if (state.showWorkingDayPrompt) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {}, // must be answered - re-shows next refresh otherwise
            title = { Text(stringResource(R.string.workday_prompt_title)) },
            text = { Text(stringResource(R.string.workday_prompt_message)) },
            confirmButton = {
                Button(onClick = { viewModel.answerWorkingDay(true) }) { Text(stringResource(R.string.workday_yes)) }
            },
            dismissButton = {
                Button(onClick = { viewModel.answerWorkingDay(false) }) { Text(stringResource(R.string.workday_no)) }
            },
        )
    }
}

@Composable
private fun DashboardContent(
    summary: DashboardSummaryDto,
    modifier: Modifier = Modifier,
    onViewInventory: () -> Unit,
    onViewInvoices: () -> Unit,
) {
    val items = buildList {
        add("valuation")
        add("periods")
        if (summary.alerts.lowStockCount > 0 || summary.alerts.dueSoonInvoicesCount > 0 || summary.alerts.overdueInvoicesCount > 0) {
            add("alerts")
        }
        add("expenses")
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items) { section ->
            when (section) {
                "valuation" -> ValuationCard(summary)
                "periods" -> PeriodsCard(summary)
                "alerts" -> AlertsCard(summary, onViewInventory, onViewInvoices)
                "expenses" -> ExpensesCard(summary)
            }
        }
    }
}

@Composable
private fun ValuationCard(summary: DashboardSummaryDto) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.dashboard_net_valuation), style = MaterialTheme.typography.labelLarge)
            Text(formatAmount(summary.netValuation), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(stringResource(R.string.dashboard_inventory_value), style = MaterialTheme.typography.bodySmall)
                    Text(formatAmount(summary.inventoryValue), style = MaterialTheme.typography.bodyLarge)
                }
                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                    Text(stringResource(R.string.dashboard_pending_invoices), style = MaterialTheme.typography.bodySmall)
                    Text(formatAmount(summary.pendingInvoicesTotal), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun PeriodsCard(summary: DashboardSummaryDto) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PeriodCard(
            title = stringResource(R.string.dashboard_today),
            period = summary.today,
            modifier = Modifier.weight(1f),
        )
        PeriodCard(
            title = stringResource(R.string.dashboard_this_month),
            period = summary.month,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PeriodCard(title: String, period: DashboardPeriodDto, modifier: Modifier = Modifier) {
    val profit = period.profit.toDoubleOrNull() ?: 0.0
    val profitColor = if (profit < 0) LossRed else ProfitGreen

    Card(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.dashboard_revenue), style = MaterialTheme.typography.bodySmall)
            Text(formatAmount(period.revenue), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.dashboard_profit), style = MaterialTheme.typography.bodySmall)
            Text(
                formatAmount(period.profit),
                style = MaterialTheme.typography.titleMedium,
                color = profitColor,
            )
        }
    }
}

@Composable
private fun AlertsCard(summary: DashboardSummaryDto, onViewInventory: () -> Unit, onViewInvoices: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            if (summary.alerts.lowStockCount > 0) {
                AlertRow(
                    text = stringResource(R.string.dashboard_low_stock_alert, summary.alerts.lowStockCount),
                    color = WarningAmber,
                    onClick = onViewInventory,
                )
            }
            if (summary.alerts.overdueInvoicesCount > 0) {
                AlertRow(
                    text = stringResource(R.string.dashboard_invoices_overdue_alert, summary.alerts.overdueInvoicesCount),
                    color = LossRed,
                    onClick = onViewInvoices,
                )
            }
            if (summary.alerts.dueSoonInvoicesCount > 0) {
                AlertRow(
                    text = stringResource(R.string.dashboard_invoices_due_alert, summary.alerts.dueSoonInvoicesCount),
                    color = WarningAmber,
                    onClick = onViewInvoices,
                )
            }
        }
    }
}

@Composable
private fun AlertRow(text: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(text, color = color, style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onClick) { Text(stringResource(R.string.dashboard_view_all)) }
    }
}

@Composable
private fun ExpensesCard(summary: DashboardSummaryDto) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.dashboard_daily_expenses), style = MaterialTheme.typography.labelLarge)
            Text(formatAmount(summary.recurringExpenses.dailyRate), style = MaterialTheme.typography.titleMedium)
        }
    }
}
