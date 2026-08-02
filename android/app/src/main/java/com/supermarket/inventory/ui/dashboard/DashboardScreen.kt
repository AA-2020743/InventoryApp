package com.supermarket.inventory.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { CircularProgressIndicator() }

            state.summary != null -> DashboardContent(
                summary = state.summary,
                todayIsWorkingDay = state.todayIsWorkingDay,
                modifier = Modifier.padding(padding),
                onViewInventory = onViewInventory,
                onViewInvoices = onViewInvoices,
                onWorkingDayBadgeClick = viewModel::reopenWorkingDayPrompt,
            )

            state.error != null -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
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
    todayIsWorkingDay: Boolean?,
    modifier: Modifier = Modifier,
    onViewInventory: () -> Unit,
    onViewInvoices: () -> Unit,
    onWorkingDayBadgeClick: () -> Unit,
) {
    val hasAlerts = summary.alerts.lowStockCount > 0 ||
        summary.alerts.dueSoonInvoicesCount > 0 ||
        summary.alerts.overdueInvoicesCount > 0

    val sections = buildList {
        add("hero")
        if (hasAlerts) add("alerts")
        add("valuation")
        add("month")
        add("expenses")
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(sections) { section ->
            when (section) {
                "hero" -> TodayHeroCard(summary.today, todayIsWorkingDay, onWorkingDayBadgeClick)
                "alerts" -> AlertsCard(summary, onViewInventory, onViewInvoices)
                "valuation" -> ValuationCard(summary)
                "month" -> MonthCard(summary.month)
                "expenses" -> ExpensesCard(summary)
            }
        }
    }
}

// The headline figure the owner cares about most, front and center: is
// today making money or losing it, and why (revenue/cost/expenses split).
@Composable
private fun TodayHeroCard(today: DashboardPeriodDto, todayIsWorkingDay: Boolean?, onWorkingDayBadgeClick: () -> Unit) {
    val profit = today.profit.toDoubleOrNull() ?: 0.0
    val isLoss = profit < 0
    val color = if (isLoss) LossRed else ProfitGreen

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isLoss) Icons.Filled.TrendingDown else Icons.Filled.TrendingUp,
                    contentDescription = null,
                    tint = color,
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.dashboard_today), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.weight(1f))
                if (todayIsWorkingDay != null) {
                    WorkingDayBadge(isWorking = todayIsWorkingDay, onClick = onWorkingDayBadgeClick)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                formatAmount(today.profit),
                style = MaterialTheme.typography.headlineLarge,
                color = color,
            )
            Text(
                stringResource(if (isLoss) R.string.dashboard_loss else R.string.dashboard_profit),
                style = MaterialTheme.typography.bodyMedium,
                color = color,
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                HeroStat(stringResource(R.string.dashboard_revenue), formatAmount(today.revenue))
                HeroStat(stringResource(R.string.dashboard_cost), formatAmount(today.cost))
            }
        }
    }
}

@Composable
private fun WorkingDayBadge(isWorking: Boolean, onClick: () -> Unit) {
    val color = if (isWorking) ProfitGreen else WarningAmber
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.15f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (isWorking) Icons.Filled.CheckCircle else Icons.Filled.RemoveCircle,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            stringResource(if (isWorking) R.string.dashboard_status_open else R.string.dashboard_status_closed),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@Composable
private fun HeroStat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun AlertsCard(summary: DashboardSummaryDto, onViewInventory: () -> Unit, onViewInvoices: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.dashboard_needs_attention), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            if (summary.alerts.lowStockCount > 0) {
                AlertRow(
                    icon = Icons.Filled.Inventory2,
                    text = stringResource(R.string.dashboard_low_stock_alert, summary.alerts.lowStockCount),
                    color = WarningAmber,
                    onClick = onViewInventory,
                )
            }
            if (summary.alerts.overdueInvoicesCount > 0) {
                AlertRow(
                    icon = Icons.Filled.Receipt,
                    text = stringResource(R.string.dashboard_invoices_overdue_alert, summary.alerts.overdueInvoicesCount),
                    color = LossRed,
                    onClick = onViewInvoices,
                )
            }
            if (summary.alerts.dueSoonInvoicesCount > 0) {
                AlertRow(
                    icon = Icons.Filled.Receipt,
                    text = stringResource(R.string.dashboard_invoices_due_alert, summary.alerts.dueSoonInvoicesCount),
                    color = WarningAmber,
                    onClick = onViewInvoices,
                )
            }
        }
    }
}

@Composable
private fun AlertRow(icon: ImageVector, text: String, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, color = color, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            stringResource(R.string.dashboard_view_all),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun ValuationCard(summary: DashboardSummaryDto) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.dashboard_net_valuation), style = MaterialTheme.typography.labelLarge)
            }
            Text(formatAmount(summary.netValuation), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                HeroStat(stringResource(R.string.dashboard_inventory_value), formatAmount(summary.inventoryValue))
                HeroStat(stringResource(R.string.dashboard_assets_value), formatAmount(summary.assetsValue))
                HeroStat(stringResource(R.string.dashboard_pending_invoices), formatAmount(summary.pendingInvoicesTotal))
            }
        }
    }
}

@Composable
private fun MonthCard(month: DashboardPeriodDto) {
    val profit = month.profit.toDoubleOrNull() ?: 0.0
    val color = if (profit < 0) LossRed else ProfitGreen

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.dashboard_this_month), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                HeroStat(stringResource(R.string.dashboard_revenue), formatAmount(month.revenue))
                Column(horizontalAlignment = Alignment.End) {
                    Text(stringResource(R.string.dashboard_profit), style = MaterialTheme.typography.bodySmall)
                    Text(formatAmount(month.profit), style = MaterialTheme.typography.titleMedium, color = color)
                }
            }
        }
    }
}

@Composable
private fun ExpensesCard(summary: DashboardSummaryDto) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Payments, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.dashboard_daily_expenses), style = MaterialTheme.typography.labelLarge)
            }
            Text(formatAmount(summary.recurringExpenses.dailyRate), style = MaterialTheme.typography.titleMedium)
        }
    }
}
