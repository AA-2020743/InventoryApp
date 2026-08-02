package com.supermarket.inventory.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local backup files live in app-specific external storage (no runtime
 * permission needed on any supported API level, unlike the public Downloads
 * collection). They're not meant to be the off-device copy by themselves -
 * [shareIntentFor] hands the file to the share sheet so the owner can drop
 * it into Drive, email, etc. for real off-device safety.
 */
object BackupFiles {
    private const val MAX_KEPT = 8

    private fun dir(context: Context): File =
        File(context.getExternalFilesDir(null), "backups").apply { mkdirs() }

    fun write(context: Context, bytes: ByteArray): File {
        val filename = "inventory-backup-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.zip"
        val file = File(dir(context), filename)
        file.writeBytes(bytes)
        rotate(context)
        return file
    }

    fun list(context: Context): List<File> =
        dir(context).listFiles { f -> f.extension == "zip" }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    fun shareIntentFor(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun rotate(context: Context) {
        val files = list(context)
        if (files.size > MAX_KEPT) {
            files.drop(MAX_KEPT).forEach { it.delete() }
        }
    }
}
