package com.supermarket.inventory.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

// Why a request never reached the server. Every one of these is an
// IOException, and reporting them all as one "could not reach the server"
// leaves a misconfigured address, a dead server and a bad certificate
// looking identical - which is exactly when the difference matters most.
enum class NetworkErrorKind {
    // DNS gave no address: a typo, or a name with no record behind it.
    UNKNOWN_HOST,
    // Something answered at that address but nothing was listening on the
    // port - the server is down, or the port is wrong.
    CONNECTION_REFUSED,
    TIMEOUT,
    // TLS failed: a self-signed or expired certificate, or one issued for a
    // different name than the one typed.
    CERTIFICATE,
    OTHER,
}

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()

    // isNetworkError distinguishes "never reached the server" (IOException -
    // safe to retry, or fall back to a local cache) from a real server-side
    // rejection (validation failure, 404, etc. - retrying won't help).
    // networkKind says which way it failed to get there, for the screens
    // that want to tell the user more than that it did.
    data class Error(
        val message: String,
        val isNetworkError: Boolean = false,
        val networkKind: NetworkErrorKind? = null,
    ) : ApiResult<Nothing>()
}

// Most specific first: each of these is an IOException, so the generic catch
// below has to come after all of them or it swallows them.
suspend fun <T> apiCall(block: suspend () -> T): ApiResult<T> = try {
    ApiResult.Success(block())
} catch (e: HttpException) {
    ApiResult.Error(extractErrorMessage(e))
} catch (e: UnknownHostException) {
    networkError(NetworkErrorKind.UNKNOWN_HOST, "Server address not found. Check it for typos.")
} catch (e: ConnectException) {
    networkError(NetworkErrorKind.CONNECTION_REFUSED, "The server refused the connection. It may be down, or the address may be wrong.")
} catch (e: SocketTimeoutException) {
    networkError(NetworkErrorKind.TIMEOUT, "The server took too long to respond. Check your connection and try again.")
} catch (e: SSLException) {
    networkError(NetworkErrorKind.CERTIFICATE, "The server's security certificate isn't trusted.")
} catch (e: IOException) {
    networkError(NetworkErrorKind.OTHER, "Could not reach the server. Check your connection and server URL.")
} catch (e: Exception) {
    ApiResult.Error(e.message ?: "Unknown error")
}

private fun networkError(kind: NetworkErrorKind, message: String) =
    ApiResult.Error(message, isNetworkError = true, networkKind = kind)

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
