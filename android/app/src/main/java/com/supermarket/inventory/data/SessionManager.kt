package com.supermarket.inventory.data

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "inventory_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Holds auth token, server URL, language and theme preference.
 *
 * Backed by DataStore for persistence, but mirrored into in-memory
 * StateFlows so the OkHttp interceptors (which run off the main thread on
 * every request) can read the current value without suspending — they're
 * seeded once at startup via [preload] and kept in sync on every write.
 */
@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val TOKEN = stringPreferencesKey("token")
        val SERVER_URL = stringPreferencesKey("server_url")
        val LANGUAGE = stringPreferencesKey("language") // "system" | "en" | "ar"
        val THEME = stringPreferencesKey("theme") // "system" | "light" | "dark"
        val INSTALL_ID = stringPreferencesKey("install_id")
        val SIGN_IN_WATERMARK = stringPreferencesKey("sign_in_watermark")
    }

    val token = MutableStateFlow<String?>(null)
    // True when the session ended because the server rejected the token
    // rather than because the owner chose to sign out - the login screen
    // says so, so being sent back there doesn't look like a fault.
    val sessionExpired = MutableStateFlow(false)
    val serverUrl = MutableStateFlow(DEFAULT_SERVER_URL)
    val language = MutableStateFlow("system")
    val theme = MutableStateFlow(ThemeMode.SYSTEM)

    suspend fun preload() {
        val prefs = context.dataStore.data.first()
        token.value = prefs[Keys.TOKEN]
        serverUrl.value = prefs[Keys.SERVER_URL] ?: DEFAULT_SERVER_URL
        language.value = prefs[Keys.LANGUAGE] ?: "system"
        theme.value = when (prefs[Keys.THEME]) {
            "light" -> ThemeMode.LIGHT
            "dark" -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    suspend fun setToken(value: String?) {
        token.value = value
        if (value != null) sessionExpired.value = false
        context.dataStore.edit { prefs ->
            if (value == null) {
                prefs.remove(Keys.TOKEN)
                // Signed out, so this device stops watching for new sign-ins.
                // Keeping the old watermark would, on signing back in, report
                // every device that appeared in the meantime at once - stale
                // alerts about sign-ins the owner was in no position to see
                // live. The next check after signing in starts afresh.
                prefs.remove(Keys.SIGN_IN_WATERMARK)
            } else {
                prefs[Keys.TOKEN] = value
            }
        }
    }

    // Identifies this phone to the server, so it's recognised when it signs in
    // again and listed once however many times it has. (Still called an
    // install id on the wire; it now outlives the install.)
    //
    // Derived from ANDROID_ID, which Android scopes to this app's signing key
    // on this device and keeps across reinstalls and cleared app data. The
    // first version was a random id stored in the app's own data, so a
    // reinstall - or clearing storage - made the same phone look new: a
    // fresh "new device" alert, and a second entry in the list beside the
    // first. Hashed, so the raw value never leaves the phone.
    //
    // The random stored id remains only as a fallback for a device that
    // reports no ANDROID_ID.
    suspend fun installId(): String = deviceBoundId() ?: storedRandomId()

    @SuppressLint("HardwareIds")
    private fun deviceBoundId(): String? {
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()?.trim()
        if (androidId.isNullOrEmpty()) return null
        return UUID.nameUUIDFromBytes("supermarket-inventory:$androidId".toByteArray()).toString()
    }

    private suspend fun storedRandomId(): String {
        context.dataStore.data.first()[Keys.INSTALL_ID]?.let { return it }
        val fresh = UUID.randomUUID().toString()
        var stored = fresh
        context.dataStore.edit { prefs ->
            // Re-read inside the edit: two first callers racing must agree.
            stored = prefs[Keys.INSTALL_ID] ?: fresh.also { prefs[Keys.INSTALL_ID] = it }
        }
        return stored
    }

    // Server time up to which new-device sign-ins have been reported here.
    suspend fun signInWatermark(): String? = context.dataStore.data.first()[Keys.SIGN_IN_WATERMARK]

    suspend fun setSignInWatermark(value: String) {
        context.dataStore.edit { it[Keys.SIGN_IN_WATERMARK] = value }
    }

    suspend fun setServerUrl(value: String) {
        val normalized = normalizeServerUrl(value)
        serverUrl.value = normalized
        context.dataStore.edit { it[Keys.SERVER_URL] = normalized }
    }

    suspend fun setLanguage(value: String) {
        language.value = value
        context.dataStore.edit { it[Keys.LANGUAGE] = value }
    }

    suspend fun setTheme(value: ThemeMode) {
        theme.value = value
        context.dataStore.edit { it[Keys.THEME] = value.name.lowercase() }
    }

    // A deliberate sign-out, so the login screen shouldn't then say the
    // session "ended". The server call that precedes this can itself come
    // back 401 - the session already revoked from elsewhere - and that
    // trips onUnauthorized, which sets the flag; it's cleared here because
    // the owner asked to leave either way.
    suspend fun logout() {
        setToken(null)
        sessionExpired.value = false
    }

    // Called from the network layer when the server rejects the token.
    //
    // Not a suspend function on purpose: it runs on an OkHttp thread, in an
    // interceptor that cannot suspend. Clearing the in-memory flow is what
    // matters and is immediate - the navigation watches it, so the app drops
    // to the login screen on its own - and erasing the stored copy follows
    // in the background so a restart doesn't resurrect the dead token.
    fun onUnauthorized() {
        if (token.value == null) return
        token.value = null
        sessionExpired.value = true
        scope.launch {
            context.dataStore.edit {
                it.remove(Keys.TOKEN)
                it.remove(Keys.SIGN_IN_WATERMARK)
            }
        }
    }

    // Outlives any screen: the write below has to finish even though the
    // caller is an interceptor with no scope of its own.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        // Empty, not a guess. This used to be the emulator's route to a dev
        // machine (http://10.0.2.2:4000), which on a real phone is an address
        // that goes nowhere - and pre-filling it meant the first thing anyone
        // saw was a wrong value they had to know to delete.
        const val DEFAULT_SERVER_URL = ""

        // What the owner types is what a phone keyboard produces: often no
        // scheme, sometimes a trailing space or slash. A bare host fails to
        // parse as a URL at all, and a request with no parseable base falls
        // through to localhost - which reads as "could not reach the server"
        // even though the name was right. https is the assumption because
        // that's what a real deployment serves; anyone on plain http types
        // the scheme, and it's kept.
        fun normalizeServerUrl(value: String): String {
            val trimmed = value.trim().trimEnd('/')
            if (trimmed.isEmpty()) return trimmed
            return if (trimmed.contains("://")) trimmed else "https://$trimmed"
        }
    }
}
