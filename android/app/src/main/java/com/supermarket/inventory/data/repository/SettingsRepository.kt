package com.supermarket.inventory.data.repository

import com.supermarket.inventory.data.ApiResult
import com.supermarket.inventory.data.apiCall
import com.supermarket.inventory.data.remote.ApiService
import com.supermarket.inventory.data.remote.dto.SettingsDto
import com.supermarket.inventory.data.remote.dto.UpdateSettingsRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(private val api: ApiService) {
    suspend fun getSettings(): ApiResult<SettingsDto> = apiCall { api.getSettings() }

    suspend fun setStartingValue(value: Double): ApiResult<SettingsDto> =
        apiCall { api.updateSettings(UpdateSettingsRequest(value)) }
}
