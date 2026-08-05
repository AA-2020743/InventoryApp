package com.supermarket.inventory.ui.sales

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.supermarket.inventory.R

// Selling has no dedicated bottom tab - this hovering, semi-transparent
// shortcut is the only entry point, reachable from every other tab, so the
// owner doesn't have to navigate anywhere just to tap scan.
// A gentle breathing pulse hints it's there without demanding attention.
// Tapping it opens the scanner directly; tapping it again (it morphs into
// a close button) reveals two fallback actions: "search" for when a barcode
// won't scan (jumps to Sell with the manual-entry field already focused),
// and "spoiled product" for pulling damaged/expired stock out of inventory.
@Composable
fun BoxScope.SellFab(
    visible: Boolean,
    onScan: () -> Unit,
    onSearch: () -> Unit,
    onSpoil: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(visible) { if (!visible) expanded = false }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        modifier = Modifier.align(Alignment.BottomEnd),
    ) {
        Box {
            // A full-area scrim while expanded - tapping anywhere outside
            // the two mini actions collapses the cluster instead of firing
            // whatever's underneath.
            if (expanded) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { expanded = false },
                        ),
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            ) {
                AnimatedVisibility(visible = expanded, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                    Column(horizontalAlignment = Alignment.End) {
                        SellFabMiniAction(
                            icon = Icons.Filled.DeleteForever,
                            label = stringResource(R.string.sell_fab_spoil),
                            onClick = { expanded = false; onSpoil() },
                        )
                        Spacer(Modifier.height(12.dp))
                        SellFabMiniAction(
                            icon = Icons.Filled.Search,
                            label = stringResource(R.string.sell_fab_search),
                            onClick = { expanded = false; onSearch() },
                        )
                        Spacer(Modifier.height(12.dp))
                        SellFabMiniAction(
                            icon = Icons.Filled.QrCodeScanner,
                            label = stringResource(R.string.sell_fab_scan),
                            onClick = { expanded = false; onScan() },
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                }

                val infiniteTransition = rememberInfiniteTransition(label = "sell_fab_pulse")
                val pulse by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.08f,
                    animationSpec = infiniteRepeatable(animation = tween(1400), repeatMode = RepeatMode.Reverse),
                    label = "sell_fab_pulse_scale",
                )
                val rotation by animateFloatAsState(if (expanded) 45f else 0f, label = "sell_fab_rotation")

                Box(
                    modifier = Modifier
                        .scale(if (expanded) 1f else pulse)
                        .size(64.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.62f), CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { if (expanded) { expanded = false; onScan() } else expanded = true },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (expanded) Icons.Filled.Close else Icons.Filled.QrCodeScanner,
                        contentDescription = stringResource(if (expanded) R.string.action_cancel else R.string.sell_fab_scan),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp).rotate(rotation),
                    )
                }
            }
        }
    }
}

@Composable
private fun SellFabMiniAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f), CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}
