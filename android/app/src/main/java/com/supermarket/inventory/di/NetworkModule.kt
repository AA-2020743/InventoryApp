package com.supermarket.inventory.di

import com.squareup.moshi.Moshi
import com.squareup.moshi.KotlinJsonAdapterFactory
import com.supermarket.inventory.data.SessionManager
import com.supermarket.inventory.data.remote.ApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    // The server URL is user-configurable (self-hosted backend), so Retrofit
    // is built once with a placeholder base URL and this interceptor
    // rewrites each request's scheme/host/port to whatever SessionManager
    // currently holds — read synchronously from an in-memory StateFlow so no
    // suspending is needed on the OkHttp dispatcher thread.
    @Provides
    @Singleton
    fun provideBaseUrlInterceptor(sessionManager: SessionManager): Interceptor =
        Interceptor { chain ->
            val configured = sessionManager.serverUrl.value.toHttpUrlOrNull()
            val request = if (configured != null) {
                val newUrl = chain.request().url.newBuilder()
                    .scheme(configured.scheme)
                    .host(configured.host)
                    .port(configured.port)
                    .build()
                chain.request().newBuilder().url(newUrl).build()
            } else {
                chain.request()
            }
            chain.proceed(request)
        }

    @Provides
    @Singleton
    fun provideAuthInterceptor(sessionManager: SessionManager): Interceptor =
        Interceptor { chain ->
            val token = sessionManager.token.value
            val request = if (token != null) {
                chain.request().newBuilder().addHeader("Authorization", "Bearer $token").build()
            } else {
                chain.request()
            }
            chain.proceed(request)
        }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        baseUrlInterceptor: Interceptor,
        authInterceptor: Interceptor,
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        return OkHttpClient.Builder()
            .addInterceptor(baseUrlInterceptor)
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit =
        Retrofit.Builder()
            // Never actually dialed as-is: BaseUrlInterceptor rewrites the
            // host on every request. Must be a well-formed absolute URL for
            // Retrofit's constructor to accept it.
            .baseUrl("http://localhost/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService = retrofit.create(ApiService::class.java)
}
