package com.supermarket.inventory.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
        context.dataStore.edit { prefs ->
            if (value == null) prefs.remove(Keys.TOKEN) else prefs[Keys.TOKEN] = value
        }
    }

    suspend fun setServerUrl(value: String) {
        val normalized = value.trimEnd('/')
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

    companion object {
        const val DEFAULT_SERVER_URL = "http://10.0.2.2:4000"
    }
}
