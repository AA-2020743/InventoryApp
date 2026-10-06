package com.supermarket.inventory.ui.settings

import android.text.format.DateUtils
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.supermarket.inventory.R
import com.supermarket.inventory.data.ApiResult
import com.supermarket.inventory.data.isUnknownDevice
import com.supermarket.inventory.data.remote.dto.SessionDto
import com.supermarket.inventory.data.repository.AuthRepository
import com.supermarket.inventory.ui.common.formatIsoDateTime
import com.supermarket.inventory.ui.theme.warningColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class DevicesUiState(
    val isLoading: Boolean = true,
    val sessions: List<SessionDto> = emptyList(),
    // A session being signed out, so its row can show progress rather than
    // a button that could be pressed twice.
    val busyId: String? = null,
    val signingOutAll: Boolean = false,
    val error: String? = null,
    val signedOutAll: Boolean = false,
)

@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    var uiState by mutableStateOf(DevicesUiState())
        private set

    init { load() }

    fun load() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null)
            uiState = when (val result = authRepository.getSessions()) {
                is ApiResult.Success -> uiState.copy(isLoading = false, sessions = result.data)
                is ApiResult.Error -> uiState.copy(isLoading = false, error = result.message)
            }
        }
    }

    fun signOut(id: String) {
        viewModelScope.launch {
            uiState = uiState.copy(busyId = id, error = null, signedOutAll = false)
            uiState = when (val result = authRepository.signOutSession(id)) {
                is ApiResult.Success -> uiState.copy(busyId = null, sessions = uiState.sessions.filterNot { it.id == id })
                is ApiResult.Error -> uiState.copy(busyId = null, error = result.message)
            }
        }
    }

    fun signOutOthers() {
        viewModelScope.launch {
            uiState = uiState.copy(signingOutAll = true, error = null)
            uiState = when (val result = authRepository.signOutOtherSessions()) {
                is ApiResult.Success -> uiState.copy(
                    signingOutAll = false,
                    signedOutAll = true,
                    sessions = uiState.sessions.filter { it.current },
                )
                is ApiResult.Error -> uiState.copy(signingOutAll = false, error = result.message)
            }
        }
    }
}

// Within this long of the last request a device reads as "Active now".
// The server records activity at most once a minute, so anything tighter
// would flicker between "now" and "1 minute ago" for a phone in use.
private const val ACTIVE_NOW_MS = 2 * 60 * 1000L

private fun epochMillis(iso: String): Long? = runCatching { Instant.parse(iso).toEpochMilli() }.getOrNull()

// A guess from the model string, only to pick an icon: tablets commonly
// carry "Tab", "Pad" or Lenovo's "TB-" in their model names.
private fun iconFor(session: SessionDto): ImageVector {
    val model = session.deviceModel ?: return Icons.Filled.DevicesOther
    val tablet = listOf("tab", "pad", "tb-").any { model.contains(it, ignoreCase = true) }
    return if (tablet) Icons.Filled.TabletAndroid else Icons.Filled.PhoneAndroid
}

@Composable
private fun displayName(session: SessionDto): String =
    if (isUnknownDevice(session.deviceName, session.deviceModel)) stringResource(R.string.devices_unknown)
    else session.deviceName

// "Samsung SM-A525F · Android 14", leaving out whatever wasn't reported, and
// the model when it's already the name (no name set on the phone).
private fun detailLine(session: SessionDto): String =
    listOfNotNull(
        session.deviceModel?.takeIf { it != session.deviceName },
        session.osVersion,
    ).joinToString(" · ")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(onBack: () -> Unit, viewModel: DevicesViewModel = hiltViewModel()) {
    val state = viewModel.uiState
    var confirmOne by remember { mutableStateOf<SessionDto?>(null) }
    var confirmAll by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.devices_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = null) }
                },
                actions = {
                    IconButton(onClick = viewModel::load) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.devices_refresh))
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading && state.sessions.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val current = state.sessions.firstOrNull { it.current }
        val others = state.sessions.filterNot { it.current }
        val now = System.currentTimeMillis()

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.devices_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.error?.let { message ->
                item { Text(message, color = MaterialTheme.colorScheme.error) }
            }
            if (state.signedOutAll) {
                item {
                    Text(stringResource(R.string.devices_signed_out_all), color = MaterialTheme.colorScheme.primary)
                }
            }
            current?.let { session ->
                item { ThisDeviceCard(session) }
            }
            item {
                Text(
                    stringResource(R.string.devices_other_devices),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (others.isEmpty()) {
                item { NoOtherDevicesCard() }
            } else {
                items(others, key = { it.id }) { session ->
                    OtherDeviceCard(
                        session = session,
                        now = now,
                        busy = state.busyId == session.id || state.signingOutAll,
                        onSignOut = { confirmOne = session },
                    )
                }
                item {
                    OutlinedButton(
                        onClick = { confirmAll = true },
                        enabled = !state.signingOutAll && state.busyId == null,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    ) {
                        Text(stringResource(R.string.devices_sign_out_all), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    confirmOne?.let { session ->
        AlertDialog(
            onDismissRequest = { confirmOne = null },
            title = { Text(stringResource(R.string.devices_confirm_one_title, displayName(session))) },
            text = { Text(stringResource(R.string.devices_confirm_one_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.signOut(session.id)
                    confirmOne = null
                }) { Text(stringResource(R.string.devices_sign_out), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmOne = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text(stringResource(R.string.devices_confirm_all_title)) },
            text = { Text(stringResource(R.string.devices_confirm_all_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.signOutOthers()
                    confirmAll = false
                }) { Text(stringResource(R.string.devices_sign_out_all), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmAll = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

// The phone in hand, set apart from the rest: it's the one reference point
// the owner is certain of, and the one that can't be signed out from here.
@Composable
private fun ThisDeviceCard(session: SessionDto) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            DeviceAvatar(
                icon = iconFor(session),
                background = MaterialTheme.colorScheme.primary,
                tint = MaterialTheme.colorScheme.onPrimary,
                size = 56,
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Pill(
                    text = stringResource(R.string.devices_this_device),
                    background = MaterialTheme.colorScheme.primary,
                    content = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    displayName(session),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                detailLine(session).takeIf { it.isNotEmpty() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Text(
                    stringResource(R.string.devices_signed_in_at, formatIsoDateTime(session.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                )
            }
        }
    }
}

@Composable
private fun OtherDeviceCard(session: SessionDto, now: Long, busy: Boolean, onSignOut: () -> Unit) {
    val lastSeen = epochMillis(session.lastSeenAt)
    val activeNow = lastSeen != null && now - lastSeen < ACTIVE_NOW_MS
    val activity = when {
        activeNow -> stringResource(R.string.devices_active_now)
        lastSeen != null -> stringResource(
            R.string.devices_active_ago,
            DateUtils.getRelativeTimeSpanString(lastSeen, now, DateUtils.MINUTE_IN_MILLIS).toString(),
        )
        else -> null
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Box {
                DeviceAvatar(
                    icon = iconFor(session),
                    background = MaterialTheme.colorScheme.secondaryContainer,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    size = 44,
                )
                // A live dot on the avatar for a device in use right now -
                // the one thing worth seeing at a glance down the list.
                if (activeNow) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    displayName(session),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (session.newDevice) {
                    Spacer(Modifier.height(4.dp))
                    Pill(
                        text = stringResource(R.string.devices_new_badge),
                        background = warningColor().copy(alpha = 0.18f),
                        content = warningColor(),
                    )
                }
                detailLine(session).takeIf { it.isNotEmpty() }?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                activity?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (activeNow) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (activeNow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    stringResource(R.string.devices_signed_in_at, formatIsoDateTime(session.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Secondary on purpose: a phone's address changes with every
                // network it joins, so it's a hint, not an identity.
                session.lastIp?.let {
                    Text(
                        stringResource(R.string.devices_last_ip, it),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
            if (busy) {
                CircularProgressIndicator(Modifier.size(24.dp).padding(2.dp), strokeWidth = 2.dp)
            } else {
                TextButton(onClick = onSignOut) {
                    Text(stringResource(R.string.devices_sign_out), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun NoOtherDevicesCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.devices_none_other), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun DeviceAvatar(icon: ImageVector, background: Color, tint: Color, size: Int) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size((size * 0.55f).dp))
    }
}

@Composable
private fun Pill(text: String, background: Color, content: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = content,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}
