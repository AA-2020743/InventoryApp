package com.supermarket.inventory.ui.common

import android.content.Context
import android.net.Uri
import java.io.File

// Copies a picked-photo Uri into a local cache file so it can be sent as a
// multipart upload body - shared by any screen with a photo picker (product
// photos, invoice attachments, ...).
fun copyUriToCacheFile(context: Context, uri: Uri): File? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val file = File(context.cacheDir, "upload_${System.currentTimeMillis()}.jpg")
        inputStream.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
        file
    } catch (_: Exception) {
        null
    }
}
