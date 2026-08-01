package com.supermarket.inventory.data.repository

import com.supermarket.inventory.data.ApiResult
import com.supermarket.inventory.data.apiCall
import com.supermarket.inventory.data.remote.ApiService
import com.supermarket.inventory.data.remote.dto.RestoreResponse
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepository @Inject constructor(private val api: ApiService) {

    suspend fun exportBackup(): ApiResult<ByteArray> = apiCall { api.exportBackup().bytes() }

    suspend fun restoreBackup(json: ByteArray): ApiResult<RestoreResponse> = apiCall {
        api.restoreBackup(json.toRequestBody("application/json".toMediaType()))
    }
}
