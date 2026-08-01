package com.supermarket.inventory.data.repository

import com.supermarket.inventory.data.ApiResult
import com.supermarket.inventory.data.SessionManager
import com.supermarket.inventory.data.apiCall
import com.supermarket.inventory.data.remote.ApiService
import com.supermarket.inventory.data.remote.dto.ChangePasswordRequest
import com.supermarket.inventory.data.remote.dto.LoginRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: ApiService,
    private val sessionManager: SessionManager,
) {
    suspend fun login(serverUrl: String, email: String, password: String): ApiResult<Unit> {
        sessionManager.setServerUrl(serverUrl)
        return apiCall { api.login(LoginRequest(email, password)) }.also { result ->
            if (result is ApiResult.Success) {
                sessionManager.setToken(result.data.token)
            }
        }.let { result ->
            when (result) {
                is ApiResult.Success -> ApiResult.Success(Unit)
                is ApiResult.Error -> result
            }
        }
    }

    suspend fun changePassword(currentPassword: String, newPassword: String): ApiResult<Unit> =
        apiCall { api.changePassword(ChangePasswordRequest(currentPassword, newPassword)) }

    suspend fun logout() = sessionManager.logout()
}
