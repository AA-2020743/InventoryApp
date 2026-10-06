package com.supermarket.inventory.data

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.core.content.pm.PackageInfoCompat

// What this phone says about itself at sign-in, so the server's list of
// signed-in devices reads as phones the owner recognises rather than as a
// column of IP addresses that change every time the phone changes network.
data class DeviceDetails(
    val name: String,
    val model: String,
    val osVersion: String,
    val appVersion: String?,
)

object DeviceDetailsReader {
    // The server caps these; trimming here keeps a long custom name from
    // being refused at sign-in, which would be a strange reason to fail.
    private const val MAX_NAME = 100

    fun read(context: Context): DeviceDetails {
        val model = modelName()
        return DeviceDetails(
            name = userDeviceName(context)?.take(MAX_NAME) ?: model,
            model = model,
            osVersion = "Android ${Build.VERSION.RELEASE}",
            appVersion = appVersion(context),
        )
    }

    // The name shown in the phone's own "About phone" screen, which the owner
    // may have changed to something personal ("Ahmed's Galaxy") - the most
    // recognisable label there is. Readable without any permission.
    private fun userDeviceName(context: Context): String? =
        runCatching { Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME) }
            .getOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    // "Samsung SM-A525F", not "samsung samsung SM-A525F": some makers already
    // put their name at the front of the model string.
    private fun modelName(): String {
        val manufacturer = Build.MANUFACTURER.trim().replaceFirstChar { it.uppercase() }
        val model = Build.MODEL.trim()
        val name = if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
        return name.take(MAX_NAME)
    }

    // Read from the installed package, as Settings does for its version line,
    // so it can't disagree with what's actually installed.
    private fun appVersion(context: Context): String? = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        "${info.versionName} (${PackageInfoCompat.getLongVersionCode(info)})"
    }.getOrNull()
}

// The server's label for a sign-in from an app too old to describe its
// device (see createSession). Matched to show a translated label instead -
// together with the missing model, so a phone someone actually named
// "Unknown device" keeps its name.
private const val SERVER_UNKNOWN_DEVICE = "Unknown device"

fun isUnknownDevice(name: String, model: String?): Boolean =
    model == null && name == SERVER_UNKNOWN_DEVICE
