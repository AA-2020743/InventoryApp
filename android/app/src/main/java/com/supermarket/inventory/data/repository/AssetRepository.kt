package com.supermarket.inventory.data.repository

import com.supermarket.inventory.data.ApiResult
import com.supermarket.inventory.data.apiCall
import com.supermarket.inventory.data.remote.ApiService
import com.supermarket.inventory.data.remote.dto.AssetDto
import com.supermarket.inventory.data.remote.dto.AssetInput
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssetRepository @Inject constructor(private val api: ApiService) {
    suspend fun getAssets(): ApiResult<List<AssetDto>> = apiCall { api.getAssets() }

    suspend fun create(name: String, value: Double, category: String?): ApiResult<AssetDto> =
        apiCall { api.createAsset(AssetInput(name, value, category, null)) }

    suspend fun delete(id: String): ApiResult<Unit> = apiCall { api.deleteAsset(id) }
}
