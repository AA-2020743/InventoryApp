package com.supermarket.inventory.ui.dashboard

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.supermarket.inventory.R
import com.supermarket.inventory.data.remote.dto.DashboardPeriodDto
import com.supermarket.inventory.data.remote.dto.DashboardSummaryDto
import com.supermarket.inventory.ui.common.formatAmount
import com.supermarket.inventory.ui.theme.lossColor
import com.supermarket.inventory.ui.theme.profitColor
import com.supermarket.inventory.ui.theme.warningColor
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
                modifier = Modifier.padding(padding),
                onViewInventory = onViewInventory,
                onViewInvoices = onViewInvoices,
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
}

@Composable
private fun DashboardContent(
    summary: DashboardSummaryDto,
    modifier: Modifier = Modifier,
    onViewInventory: () -> Unit,
    onViewInvoices: () -> Unit,
) {
    val hasAlerts = summary.alerts.lowStockCount > 0 ||
        summary.alerts.dueSoonInvoicesCount > 0 ||
        summary.alerts.overdueInvoicesCount > 0

    // Day, then month, then valuation, then (if any) alerts - the order the
    // owner asked for: today's figures first, this month's right after,
    // then "what is the business worth", with warnings last.
    val sections = buildList {
        add("today")
        add("month")
        add("valuation")
        if (hasAlerts) add("alerts")
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(sections) { section ->
            when (section) {
                "today" -> TodayCard(summary.today)
                "month" -> MonthCard(summary.month)
                "valuation" -> ValuationCard(summary)
                "alerts" -> AlertsCard(summary, onViewInventory, onViewInvoices)
            }
        }
    }
}

// A label/value pair laid out so the value keeps its natural width and the
// label absorbs any extra space - two numbers (or a long Arabic label)
// side by side never overlap the way a fixed-width Row of columns could.
@Composable
private fun StatLine(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    valueStyle: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(value, style = valueStyle, color = valueColor, textAlign = TextAlign.End)
    }
}

// The headline figure the owner cares about most, front and center: is
// today making money or losing it, and why (revenue/cost/expenses split,
// plus any deficit if an expense couldn't be fully paid from the till).
@Composable
private fun TodayCard(today: DashboardPeriodDto) {
    val profit = today.profit.toDoubleOrNull() ?: 0.0
    val isLoss = profit < 0
    val color = if (isLoss) lossColor() else profitColor()
    val deficit = today.deficit.toDoubleOrNull() ?: 0.0

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
            StatLine(stringResource(R.string.dashboard_revenue), formatAmount(today.revenue))
            Spacer(Modifier.height(8.dp))
            StatLine(stringResource(R.string.dashboard_cost), formatAmount(today.cost))
            Spacer(Modifier.height(8.dp))
            StatLine(stringResource(R.string.dashboard_expenses), formatAmount(today.expenses))
            if (deficit > 0) {
                Spacer(Modifier.height(8.dp))
                StatLine(
                    stringResource(R.string.dashboard_deficit),
                    formatAmount(today.deficit),
                    valueColor = lossColor(),
                    valueStyle = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

// This month's totals, right after today's - revenue, expenses, profit,
// and (if any) the deficit accumulated so far this month.
@Composable
private fun MonthCard(month: DashboardPeriodDto) {
    val profit = month.profit.toDoubleOrNull() ?: 0.0
    val color = if (profit < 0) lossColor() else profitColor()
    val deficit = month.deficit.toDoubleOrNull() ?: 0.0

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.dashboard_this_month), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(12.dp))
            StatLine(stringResource(R.string.dashboard_revenue), formatAmount(month.revenue))
            Spacer(Modifier.height(8.dp))
            // Already counted inside revenue above - shown separately so the
            // owner can see how much of the month came from miscellaneous
            // income rather than checkout sales.
            StatLine(stringResource(R.string.dashboard_other_sales), formatAmount(month.otherSales))
            Spacer(Modifier.height(8.dp))
            StatLine(stringResource(R.string.dashboard_expenses), formatAmount(month.expenses))
            Spacer(Modifier.height(8.dp))
            // Deficit (expenses the cash register couldn't fully cover) and
            // profit aren't opposites of the same figure - a month can be
            // profitable and still have a deficit, or vice versa - but
            // showing both at once reads as redundant/contradictory, so the
            // deficit takes priority as the more urgent number whenever
            // there is one, and profit only shows when there isn't.
            if (deficit > 0) {
                StatLine(
                    stringResource(R.string.dashboard_deficit),
                    formatAmount(month.deficit),
                    valueColor = lossColor(),
                    valueStyle = MaterialTheme.typography.titleMedium,
                )
            } else {
                StatLine(
                    stringResource(R.string.dashboard_profit),
                    formatAmount(month.profit),
                    valueColor = color,
                    valueStyle = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

// What the business is worth right now: inventory + assets + cash +
// receivables, minus what's owed to suppliers and the all-time deficit (an
// expense that couldn't be fully covered by the till is a hole in the
// business's finances the other figures don't otherwise reflect - and
// unlike the Today/Month cards, this isn't scoped to the current month,
// since valuation is what the business is worth right now, not just this
// month's activity).
@Composable
private fun ValuationCard(summary: DashboardSummaryDto) {
    val deficit = summary.allTimeDeficitTotal.toDoubleOrNull() ?: 0.0

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.dashboard_net_valuation), style = MaterialTheme.typography.labelLarge)
            }
            Text(formatAmount(summary.netValuation), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))
            StatLine(stringResource(R.string.dashboard_inventory_value), formatAmount(summary.inventoryValue))
            Spacer(Modifier.height(8.dp))
            StatLine(stringResource(R.string.dashboard_assets_value), formatAmount(summary.assetsValue))
            Spacer(Modifier.height(8.dp))
            StatLine(stringResource(R.string.dashboard_cash_register), formatAmount(summary.cashRegisterBalance))
            Spacer(Modifier.height(8.dp))
            StatLine(stringResource(R.string.dashboard_deferred_receivables), formatAmount(summary.deferredReceivablesTotal))
            Spacer(Modifier.height(8.dp))
            // Called out in its own accent color (distinct from the plain
            // deferred-receivables line above) since it's a different kind
            // of receivable - cash lent to a worker, not a customer sale -
            // even though both add to net valuation the same way.
            StatLine(
                stringResource(R.string.dashboard_debt_receivables),
                formatAmount(summary.debtReceivableTotal),
                valueColor = MaterialTheme.colorScheme.tertiary,
            )
            Spacer(Modifier.height(8.dp))
            StatLine(stringResource(R.string.dashboard_pending_invoices), formatAmount(summary.pendingInvoicesTotal))
            Spacer(Modifier.height(8.dp))
            StatLine(
                stringResource(R.string.dashboard_overall_deficit),
                formatAmount(summary.allTimeDeficitTotal),
                valueColor = if (deficit > 0) lossColor() else MaterialTheme.colorScheme.onSurface,
            )
        }
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
                    color = warningColor(),
                    onClick = onViewInventory,
                )
            }
            if (summary.alerts.overdueInvoicesCount > 0) {
                AlertRow(
                    icon = Icons.Filled.Receipt,
                    text = stringResource(R.string.dashboard_invoices_overdue_alert, summary.alerts.overdueInvoicesCount),
                    color = lossColor(),
                    onClick = onViewInvoices,
                )
            }
            if (summary.alerts.dueSoonInvoicesCount > 0) {
                AlertRow(
                    icon = Icons.Filled.Receipt,
                    text = stringResource(R.string.dashboard_invoices_due_alert, summary.alerts.dueSoonInvoicesCount),
                    color = warningColor(),
                    onClick = onViewInvoices,
                )
            }
        }
    }
}

// A single Row with the alert text weighted (and truncated if it still
// doesn't fit) rather than two independently-sized Rows pushed apart by
// SpaceBetween - a long count-interpolated alert (e.g. "12 items low on
// stock") can never overlap the fixed "View all" label this way.
@Composable
private fun AlertRow(icon: ImageVector, text: String, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            color = color,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.dashboard_view_all),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
