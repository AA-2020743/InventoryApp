package com.supermarket.inventory.data.repository

import com.supermarket.inventory.data.remote.OpenFoodFactsClient
import com.supermarket.inventory.data.remote.OpenFoodFactsProductDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenFoodFactsRepository @Inject constructor() {

    // Best-effort only: returns null on any failure (not found, network
    // unreachable, timeout, unexpected response) so callers can silently
    // fall through to manual entry - this must never block product creation.
    suspend fun lookup(barcode: String): OpenFoodFactsProductDto? = try {
        val response = OpenFoodFactsClient.service.getProduct(barcode)
        if (response.status == 1) response.product else null
    } catch (_: Exception) {
        null
    }
}
