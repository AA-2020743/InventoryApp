package com.supermarket.inventory.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import retrofit2.HttpException
import java.io.IOException

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()

    // isNetworkError distinguishes "never reached the server" (IOException -
    // safe to retry, or fall back to a local cache) from a real server-side
    // rejection (validation failure, 404, etc. - retrying won't help).
    data class Error(val message: String, val isNetworkError: Boolean = false) : ApiResult<Nothing>()
}

suspend fun <T> apiCall(block: suspend () -> T): ApiResult<T> = try {
    ApiResult.Success(block())
} catch (e: HttpException) {
    ApiResult.Error(extractErrorMessage(e))
} catch (e: IOException) {
    ApiResult.Error("Could not reach the server. Check your connection and server URL.", isNetworkError = true)
} catch (e: Exception) {
    ApiResult.Error(e.message ?: "Unknown error")
}

@OptIn(kotlin.ExperimentalStdlibApi::class)
private fun extractErrorMessage(e: HttpException): String {
    return try {
        val body = e.response()?.errorBody()?.string()
        if (body.isNullOrBlank()) return "Server error (${e.code()})"
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter<Map<String, Any?>>()
        val parsed = adapter.fromJson(body)
        (parsed?.get("error") as? String) ?: "Server error (${e.code()})"
    } catch (_: Exception) {
        "Server error (${e.code()})"
    }
}
