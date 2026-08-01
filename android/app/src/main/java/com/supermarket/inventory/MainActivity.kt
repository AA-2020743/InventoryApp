package com.supermarket.inventory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.supermarket.inventory.data.SessionManager
import com.supermarket.inventory.ui.nav.InventoryNavHost
import com.supermarket.inventory.ui.theme.InventoryAppTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

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
