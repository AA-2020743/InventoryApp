package com.supermarket.inventory.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.supermarket.inventory.R
import com.supermarket.inventory.data.ApiResult
import com.supermarket.inventory.data.SessionManager
import com.supermarket.inventory.data.ThemeMode
import com.supermarket.inventory.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val sessionManager: SessionManager,
    private val authRepository: AuthRepository,
) : ViewModel() {

    suspend fun setTheme(mode: ThemeMode) = sessionManager.setTheme(mode)

    suspend fun setServerUrl(url: String) = sessionManager.setServerUrl(url)

    suspend fun logout() = authRepository.logout()

    suspend fun changePassword(current: String, new: String): ApiResult<Unit> =
        authRepository.changePassword(current, new)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val scope = rememberCoroutineScope()
    val themeMode by viewModel.sessionManager.theme.collectAsState()
    val serverUrlState by viewModel.sessionManager.serverUrl.collectAsState()

    var serverUrlInput by remember(serverUrlState) { mutableStateOf(serverUrlState) }
    var currentLocaleTag by remember {
        mutableStateOf(AppCompatDelegate.getApplicationLocales().toLanguageTags().ifBlank { null })
    }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var passwordMessage by remember { mutableStateOf<String?>(null) }

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

            OutlinedButton(onClick = { scope.launch { viewModel.logout() } }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_logout))
            }
        }
    }
}

private fun setAppLocale(tag: String?) {
    val locales = if (tag == null) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
    AppCompatDelegate.setApplicationLocales(locales)
}
