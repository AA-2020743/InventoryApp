package com.supermarket.inventory.ui.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.annotation.StringRes
import com.supermarket.inventory.R
import com.supermarket.inventory.data.ApiResult
import com.supermarket.inventory.data.NetworkErrorKind
import com.supermarket.inventory.data.SessionManager
import com.supermarket.inventory.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject

data class LoginUiState(
    val serverUrl: String = "",
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    // The server's own wording (wrong password, disabled account), shown as
    // it arrives.
    val error: String? = null,
    // Problems this side of the server, which this screen can name itself
    // and so show in the app's language. Takes precedence over [error].
    @StringRes val errorRes: Int? = null,
    // Set when the previous session ended because the server rejected the
    // token, so this screen can say why it is being shown.
    val sessionExpired: Boolean = false,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    var uiState by mutableStateOf(
        LoginUiState(
            serverUrl = sessionManager.serverUrl.value,
            sessionExpired = sessionManager.sessionExpired.value,
        )
    )
        private set

    fun onServerUrlChange(value: String) {
        uiState = uiState.copy(serverUrl = value, error = null, errorRes = null)
    }

    fun onEmailChange(value: String) {
        uiState = uiState.copy(email = value, error = null, errorRes = null)
    }

    fun onPasswordChange(value: String) {
        uiState = uiState.copy(password = value, error = null, errorRes = null)
    }

    fun login() {
        if (uiState.serverUrl.isBlank() || uiState.email.isBlank() || uiState.password.isBlank()) {
            uiState = uiState.copy(error = null, errorRes = R.string.login_error_fields_required)
            return
        }
        // Caught here rather than sent: an address that doesn't parse would
        // otherwise go out against the placeholder base URL and come back as
        // a connection failure, blaming the network for a typo.
        val serverUrl = SessionManager.normalizeServerUrl(uiState.serverUrl)
        if (serverUrl.toHttpUrlOrNull() == null) {
            uiState = uiState.copy(error = null, errorRes = R.string.login_error_invalid_url)
            return
        }
        uiState = uiState.copy(serverUrl = serverUrl, isLoading = true, error = null, errorRes = null)
        viewModelScope.launch {
            when (val result = authRepository.login(serverUrl, uiState.email, uiState.password)) {
                is ApiResult.Success -> uiState = uiState.copy(isLoading = false)
                is ApiResult.Error -> uiState = uiState.copy(
                    isLoading = false,
                    error = result.message,
                    errorRes = result.networkKind?.let(::networkErrorText),
                )
            }
        }
    }

    @StringRes
    private fun networkErrorText(kind: NetworkErrorKind): Int = when (kind) {
        NetworkErrorKind.UNKNOWN_HOST -> R.string.login_error_unknown_host
        NetworkErrorKind.CONNECTION_REFUSED -> R.string.login_error_connection_refused
        NetworkErrorKind.TIMEOUT -> R.string.login_error_timeout
        NetworkErrorKind.CERTIFICATE -> R.string.login_error_certificate
        NetworkErrorKind.OTHER -> R.string.login_error_unreachable
    }
}
