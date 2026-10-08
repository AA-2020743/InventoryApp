package com.supermarket.inventory.data.repository

import android.content.Context
import com.supermarket.inventory.data.ApiResult
import com.supermarket.inventory.data.DeviceDetailsReader
import com.supermarket.inventory.data.SessionManager
import com.supermarket.inventory.data.apiCall
import com.supermarket.inventory.data.remote.ApiService
import com.supermarket.inventory.data.remote.dto.ChangePasswordRequest
import com.supermarket.inventory.data.remote.dto.DeviceCheckInRequest
import com.supermarket.inventory.data.remote.dto.LoginRequest
import com.supermarket.inventory.data.remote.dto.NewDevicesDto
import com.supermarket.inventory.data.remote.dto.SessionDto
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: ApiService,
    private val sessionManager: SessionManager,
) {
    suspend fun login(serverUrl: String, email: String, password: String): ApiResult<Unit> {
        sessionManager.setServerUrl(serverUrl)
        val device = DeviceDetailsReader.read(context)
        val request = LoginRequest(
            email = email,
            password = password,
            installId = sessionManager.installId(),
            deviceName = device.name,
            deviceModel = device.model,
            osVersion = device.osVersion,
            appVersion = device.appVersion,
        )
        return apiCall { api.login(request) }.also { result ->
            if (result is ApiResult.Success) {
                sessionManager.setToken(result.data.token)
                startWatchingSignIns()
            }
        }.let { result ->
            when (result) {
                is ApiResult.Success -> ApiResult.Success(Unit)
                is ApiResult.Error -> result
            }
        }
    }

    // Returns how many other devices the server signed out along with the
    // change, so the screen can say so.
    suspend fun changePassword(currentPassword: String, newPassword: String): ApiResult<Int> =
        when (val result = apiCall { api.changePassword(ChangePasswordRequest(currentPassword, newPassword)) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.signedOutDevices)
            is ApiResult.Error -> result
        }

    suspend fun getNewDevices(since: String?): ApiResult<NewDevicesDto> = apiCall { api.getNewDevices(since) }

    // Marks "now" as where this device starts watching for new sign-ins.
    // Left to the background check, the starting point would be wherever its
    // first run happened to land - up to fifteen minutes later - and a
    // device signing in during that stretch would never be reported. Best
    // effort: if it fails, the first background check sets it instead.
    private suspend fun startWatchingSignIns() {
        val start = apiCall { api.getNewDevices(null) }
        if (start is ApiResult.Success) sessionManager.setSignInWatermark(start.data.serverTime)
    }

    // Tells the server which phone this session belongs to and what it is
    // now. Best effort - the device list just shows what it last knew if
    // this doesn't get through.
    suspend fun checkInDevice() {
        val device = DeviceDetailsReader.read(context)
        apiCall {
            api.checkInDevice(
                DeviceCheckInRequest(
                    installId = sessionManager.installId(),
                    deviceName = device.name,
                    deviceModel = device.model,
                    osVersion = device.osVersion,
                    appVersion = device.appVersion,
                )
            )
        }
    }

    suspend fun getSessions(): ApiResult<List<SessionDto>> = apiCall { api.getSessions() }

    suspend fun signOutSession(id: String): ApiResult<Unit> = apiCall { api.signOutSession(id) }

    suspend fun signOutOtherSessions(): ApiResult<Int> =
        when (val result = apiCall { api.signOutOtherSessions() }) {
            is ApiResult.Success -> ApiResult.Success(result.data.signedOutDevices)
            is ApiResult.Error -> result
        }

    // Ends this device's session on the server, then forgets the token here.
    // The server call is best-effort: signing out has to work offline too,
    // and a session that couldn't be ended remotely is still listed - and
    // can be signed out - from any other device, or simply expires.
    suspend fun logout() {
        apiCall { api.logout() }
        sessionManager.logout()
    }
}
