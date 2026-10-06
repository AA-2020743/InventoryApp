package com.supermarket.inventory.data

import android.content.Context
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
            if (value == null) prefs.remove(Keys.TOKEN) else prefs[Keys.TOKEN] = value
        }
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

    suspend fun logout() = setToken(null)

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
        scope.launch { context.dataStore.edit { it.remove(Keys.TOKEN) } }
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
