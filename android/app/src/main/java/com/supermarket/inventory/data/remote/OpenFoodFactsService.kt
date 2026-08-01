package com.supermarket.inventory.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class OpenFoodFactsResponse(val status: Int, val product: OpenFoodFactsProductDto?)

@JsonClass(generateAdapter = true)
data class OpenFoodFactsProductDto(
    @Json(name = "product_name") val productName: String?,
    @Json(name = "image_front_url") val imageFrontUrl: String?,
    val categories: String?,
)

interface OpenFoodFactsService {
    @GET("api/v2/product/{barcode}.json")
    suspend fun getProduct(@Path("barcode") barcode: String): OpenFoodFactsResponse
}

// Deliberately NOT wired through Hilt's NetworkModule: that OkHttpClient
// rewrites every request's host to the user's self-hosted backend (see
// NetworkModule's base-URL interceptor), which would hijack this call
// meant for the public Open Food Facts API. Kept fully isolated instead.
object OpenFoodFactsClient {
    private val retrofit: Retrofit by lazy {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build()
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        Retrofit.Builder()
            .baseUrl("https://world.openfoodfacts.org/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    val service: OpenFoodFactsService by lazy { retrofit.create(OpenFoodFactsService::class.java) }
}
