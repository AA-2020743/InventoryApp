package com.supermarket.inventory

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.supermarket.inventory.data.SessionManager
import com.supermarket.inventory.ui.nav.InventoryNavHost
import com.supermarket.inventory.ui.theme.InventoryAppTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

// AppCompatActivity (not plain ComponentActivity) is required for
// AppCompatDelegate.setApplicationLocales() in SettingsScreen to actually
// recreate this activity and apply the new locale - with ComponentActivity
// the preference persists but nothing on screen updates until an
// unrelated recreation (e.g. rotation) happens to pick it up. Fully
// compatible with Compose: AppCompatActivity extends ComponentActivity.
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by sessionManager.theme.collectAsState()
            InventoryAppTheme(themeMode = themeMode) {
                InventoryNavHost(sessionManager = sessionManager)
            }
        }
    }
}
