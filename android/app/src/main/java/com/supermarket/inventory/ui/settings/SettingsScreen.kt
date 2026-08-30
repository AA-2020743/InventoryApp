package com.supermarket.inventory.ui.settings

import android.content.Intent
import androidx.appcompat.app.AppCompatDelegate
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.supermarket.inventory.R
import com.supermarket.inventory.data.ApiResult
import com.supermarket.inventory.data.BackupFiles
import com.supermarket.inventory.data.SessionManager
import com.supermarket.inventory.data.ThemeMode
import com.supermarket.inventory.data.remote.dto.RestoreResponse
import com.supermarket.inventory.data.repository.AuthRepository
import com.supermarket.inventory.data.repository.BackupRepository
import com.supermarket.inventory.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject
import androidx.core.content.pm.PackageInfoCompat
import androidx.compose.foundation.layout.Arrangement

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val sessionManager: SessionManager,
    private val authRepository: AuthRepository,
    private val backupRepository: BackupRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    suspend fun setTheme(mode: ThemeMode) = sessionManager.setTheme(mode)

    suspend fun setServerUrl(url: String) = sessionManager.setServerUrl(url)

    suspend fun logout() = authRepository.logout()

    suspend fun changePassword(current: String, new: String): ApiResult<Unit> =
        authRepository.changePassword(current, new)

    suspend fun exportBackup(): ApiResult<ByteArray> = backupRepository.exportBackup()

    suspend fun restoreBackup(archive: ByteArray): ApiResult<RestoreResponse> = backupRepository.restoreBackup(archive)

    suspend fun getStartingValue() = settingsRepository.getSettings()

    suspend fun setStartingValue(value: Double) = settingsRepository.setStartingValue(value)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val themeMode by viewModel.sessionManager.theme.collectAsState()
    val serverUrlState by viewModel.sessionManager.serverUrl.collectAsState()

    var serverUrlInput by remember(serverUrlState) { mutableStateOf(serverUrlState) }
    var currentLocaleTag by remember {
        mutableStateOf(AppCompatDelegate.getApplicationLocales().toLanguageTags().ifBlank { null })
    }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var passwordMessage by remember { mutableStateOf<String?>(null) }

    var startingValueInput by remember { mutableStateOf("") }
    var startingValueMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        when (val result = viewModel.getStartingValue()) {
            is ApiResult.Success -> startingValueInput = result.data.startingValue
            is ApiResult.Error -> Unit
        }
    }

    var lastBackupFile by remember { mutableStateOf(BackupFiles.list(context).firstOrNull()) }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    var pendingRestoreBytes by remember { mutableStateOf<ByteArray?>(null) }
    var showRestoreConfirm by remember { mutableStateOf(false) }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) {
                pendingRestoreBytes = bytes
                showRestoreConfirm = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = null) } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            // Language
            Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row {
                FilterChip(
                    selected = currentLocaleTag == null,
                    onClick = { setAppLocale(null); currentLocaleTag = null },
                    label = { Text(stringResource(R.string.settings_language_system)) },
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = currentLocaleTag == "en",
                    onClick = { setAppLocale("en"); currentLocaleTag = "en" },
                    label = { Text(stringResource(R.string.settings_language_english)) },
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = currentLocaleTag == "ar",
                    onClick = { setAppLocale("ar"); currentLocaleTag = "ar" },
                    label = { Text(stringResource(R.string.settings_language_arabic)) },
                )
            }

            Divider(Modifier.padding(vertical = 24.dp))

            // Theme
            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row {
                FilterChip(
                    selected = themeMode == ThemeMode.SYSTEM,
                    onClick = { scope.launch { viewModel.setTheme(ThemeMode.SYSTEM) } },
                    label = { Text(stringResource(R.string.settings_language_system)) },
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = themeMode == ThemeMode.LIGHT,
                    onClick = { scope.launch { viewModel.setTheme(ThemeMode.LIGHT) } },
                    label = { Text(stringResource(R.string.settings_theme_light)) },
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = themeMode == ThemeMode.DARK,
                    onClick = { scope.launch { viewModel.setTheme(ThemeMode.DARK) } },
                    label = { Text(stringResource(R.string.settings_theme_dark)) },
                )
            }

            Divider(Modifier.padding(vertical = 24.dp))

            // Server URL
            Text(stringResource(R.string.settings_server_url), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = serverUrlInput,
                onValueChange = { serverUrlInput = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { scope.launch { viewModel.setServerUrl(serverUrlInput) } }) {
                Text(stringResource(R.string.action_save))
            }

            Divider(Modifier.padding(vertical = 24.dp))

            // Starting value - the capital the owner started with, used to
            // compute how far current net worth has fallen below it (shown
            // as part of the dashboard's overall deficit).
            Text(stringResource(R.string.settings_starting_value_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.settings_starting_value_description), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = startingValueInput,
                onValueChange = { startingValueInput = it; startingValueMessage = null },
                label = { Text(stringResource(R.string.settings_starting_value_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            startingValueMessage?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                val value = startingValueInput.toDoubleOrNull()
                if (value == null || value < 0) {
                    startingValueMessage = context.getString(R.string.settings_starting_value_invalid)
                } else {
                    scope.launch {
                        startingValueMessage = when (val result = viewModel.setStartingValue(value)) {
                            is ApiResult.Success -> context.getString(R.string.settings_starting_value_saved)
                            is ApiResult.Error -> result.message
                        }
                    }
                }
            }) { Text(stringResource(R.string.action_save)) }

            Divider(Modifier.padding(vertical = 24.dp))

            // Change password
            Text(stringResource(R.string.settings_change_password), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = currentPassword,
                onValueChange = { currentPassword = it; passwordMessage = null },
                label = { Text(stringResource(R.string.settings_current_password)) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it; passwordMessage = null },
                label = { Text(stringResource(R.string.settings_new_password)) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            passwordMessage?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                scope.launch {
                    when (val result = viewModel.changePassword(currentPassword, newPassword)) {
                        is ApiResult.Success -> {
                            passwordMessage = null
                            currentPassword = ""
                            newPassword = ""
                        }
                        is ApiResult.Error -> passwordMessage = result.message
                    }
                }
            }) { Text(stringResource(R.string.action_save)) }

            Divider(Modifier.padding(vertical = 24.dp))

            // Backup & restore
            Text(stringResource(R.string.settings_backup_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.settings_backup_description), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Text(
                lastBackupFile?.let {
                    stringResource(R.string.settings_backup_last_saved, DateFormat.getDateTimeInstance().format(Date(it.lastModified())))
                } ?: stringResource(R.string.settings_backup_none_saved),
                style = MaterialTheme.typography.bodySmall,
            )
            backupMessage?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(8.dp))
            Row {
                Button(onClick = {
                    scope.launch {
                        when (val result = viewModel.exportBackup()) {
                            is ApiResult.Success -> {
                                val file = BackupFiles.write(context, result.data)
                                lastBackupFile = file
                                backupMessage = context.getString(R.string.settings_backup_export_success)
                                context.startActivity(Intent.createChooser(BackupFiles.shareIntentFor(context, file), null))
                            }
                            is ApiResult.Error -> backupMessage = result.message
                        }
                    }
                }) { Text(stringResource(R.string.settings_backup_export_now)) }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { restoreLauncher.launch("application/zip") }) {
                    Text(stringResource(R.string.settings_backup_restore))
                }
            }

            Divider(Modifier.padding(vertical = 24.dp))

            OutlinedButton(onClick = { scope.launch { viewModel.logout() } }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_logout))
            }

            // Read from the installed package rather than BuildConfig, so it
            // needs no extra build configuration and always reflects the APK
            // actually running. Shown as name + build number: the build
            // number is the unambiguous one when staff report an issue.
            val versionLabel = remember(context) {
                runCatching {
                    val info = context.packageManager.getPackageInfo(context.packageName, 0)
                    "${info.versionName} (${PackageInfoCompat.getLongVersionCode(info)})"
                }.getOrDefault("-")
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.settings_version),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    versionLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showRestoreConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showRestoreConfirm = false; pendingRestoreBytes = null },
            title = { Text(stringResource(R.string.settings_backup_restore_confirm_title)) },
            text = { Text(stringResource(R.string.settings_backup_restore_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreConfirm = false
                    val bytes = pendingRestoreBytes
                    pendingRestoreBytes = null
                    if (bytes != null) {
                        scope.launch {
                            backupMessage = when (val result = viewModel.restoreBackup(bytes)) {
                                is ApiResult.Success -> context.getString(R.string.settings_backup_restore_success)
                                is ApiResult.Error -> result.message
                            }
                        }
                    }
                }) { Text(stringResource(R.string.settings_backup_restore), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false; pendingRestoreBytes = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

private fun setAppLocale(tag: String?) {
    val locales = if (tag == null) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
    AppCompatDelegate.setApplicationLocales(locales)
}
