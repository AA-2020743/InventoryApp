package com.supermarket.inventory.ui.others

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.hilt.navigation.compose.hiltViewModel
import com.supermarket.inventory.R
import com.supermarket.inventory.ui.cashregister.CashRegisterTabContent
import com.supermarket.inventory.ui.common.formatAmount
import com.supermarket.inventory.ui.debts.DebtsTabContent
import com.supermarket.inventory.ui.invoices.AssetsTabContent
import com.supermarket.inventory.ui.invoices.InvoicesTabContent
import com.supermarket.inventory.ui.othersales.OtherSalesTabContent
import com.supermarket.inventory.ui.sales.DeferredSalesTabContent
import com.supermarket.inventory.ui.theme.lossColor
import com.supermarket.inventory.ui.theme.profitColor
import com.supermarket.inventory.ui.theme.warningColor

private enum class OthersTab(val titleRes: Int, val captionRes: Int, val icon: ImageVector) {
    INVOICES(R.string.invoices_title, R.string.others_caption_invoices, Icons.Filled.Receipt),
    CASH_REGISTER(R.string.cash_register_title, R.string.others_caption_cash, Icons.Filled.Payments),
    DEFERRED_SALES(R.string.deferred_sales_title, R.string.others_caption_deferred, Icons.Filled.Schedule),
    DEBTS(R.string.debts_title, R.string.others_caption_debts, Icons.Filled.AccountBalanceWallet),
    OTHER_SALES(R.string.other_sales_title, R.string.others_caption_other_sales, Icons.Filled.Savings),
    ASSETS(R.string.assets_title, R.string.others_caption_assets, Icons.Filled.Inventory2),
}

// Everything under this destination is money the shop owes, is owed, or is
// holding - six sibling views of the same subject.
//
// They used to sit behind a scrolling row of six text tabs, which meant
// never seeing more than three at once, no sense of which was worth opening,
// and (in Arabic, where the labels run longer) a row you had to drag before
// you could read it. It's a hub instead now: one tile per section, each
// carrying the figure that section is about - what's still owed to
// suppliers, what's in the till, what customers owe - so the landing page
// answers the common question without being opened at all, and choosing
// where to go is a glance rather than a hunt.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OthersScreen(viewModel: OthersViewModel = hiltViewModel()) {
    // null = the hub itself. Opening a section swaps the whole screen for
    // it, so each one gets the full height it used to share with the tabs.
    var openTab by remember { mutableStateOf<OthersTab?>(null) }
    val state = viewModel.uiState

    val current = openTab
    BackHandler(enabled = current != null) { openTab = null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(current?.titleRes ?: R.string.nav_others)) },
                navigationIcon = {
                    if (current != null) {
                        IconButton(onClick = { openTab = null }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.others_back_to_hub),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (current == null) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(OthersTab.values()) { tab ->
                        val summary = state.summary
                        // Each tile's figure is the one number that section
                        // is about. Colour follows meaning, not decoration:
                        // what's owed out is a warning, what's held or owed
                        // in reads as value.
                        val (value, accent) = when (tab) {
                            OthersTab.INVOICES -> summary?.pendingInvoicesTotal to warningColor()
                            OthersTab.CASH_REGISTER -> summary?.cashRegisterBalance to profitColor()
                            OthersTab.DEFERRED_SALES -> summary?.deferredReceivablesTotal to warningColor()
                            OthersTab.DEBTS -> summary?.debtReceivableTotal to warningColor()
                            OthersTab.OTHER_SALES -> summary?.month?.otherSales to profitColor()
                            OthersTab.ASSETS -> summary?.assetsValue to MaterialTheme.colorScheme.tertiary
                        }
                        HubTile(
                            icon = tab.icon,
                            title = stringResource(tab.titleRes),
                            caption = stringResource(tab.captionRes),
                            value = value?.let { formatAmount(it) },
                            accent = accent,
                            onClick = { openTab = tab },
                        )
                    }
                }
            } else {
                when (current) {
                    OthersTab.INVOICES -> InvoicesTabContent()
                    OthersTab.ASSETS -> AssetsTabContent()
                    OthersTab.DEFERRED_SALES -> DeferredSalesTabContent()
                    OthersTab.DEBTS -> DebtsTabContent()
                    OthersTab.OTHER_SALES -> OtherSalesTabContent()
                    OthersTab.CASH_REGISTER -> CashRegisterTabContent()
                }
            }
        }
    }
}

// One section of the hub: an icon sitting in a tinted disc, the section's
// name, its headline figure, and a line saying what it holds.
@Composable
private fun HubTile(
    icon: ImageVector,
    title: String,
    caption: String,
    value: String?,
    accent: Color,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(14.dp)) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            // Dashes rather than a spinner or a zero while the figure loads:
            // a wrong number here would be read as real.
            Text(
                value ?: "—",
                style = MaterialTheme.typography.titleMedium,
                color = accent,
                maxLines = 1,
            )
        }
    }
}
