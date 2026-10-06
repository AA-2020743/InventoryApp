package com.supermarket.inventory

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.supermarket.inventory.data.SessionManager
import com.supermarket.inventory.ui.nav.InventoryNavHost
import com.supermarket.inventory.ui.theme.InventoryAppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
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

    // A screen a notification asked to open, waiting for the nav host to
    // take it. Held here rather than read from the intent inside
    // composition, because a tap while the app is already open arrives
    // through onNewIntent, after composition has started.
    private val pendingOpen = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only on a fresh start: after a rotation the intent is the same one,
        // and re-applying it would push the screen a second time.
        if (savedInstanceState == null) pendingOpen.value = intent?.getStringExtra(EXTRA_OPEN)
        setContent {
            val themeMode by sessionManager.theme.collectAsState()
            val token by sessionManager.token.collectAsState()
            val open by pendingOpen.collectAsState()
            InventoryAppTheme(themeMode = themeMode) {
                NotificationPermissionRequest(signedIn = token != null)
                InventoryNavHost(
                    sessionManager = sessionManager,
                    pendingOpen = open,
                    onPendingOpenHandled = { pendingOpen.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_OPEN)?.let { pendingOpen.value = it }
    }

    companion object {
        const val EXTRA_OPEN = "open"
        const val OPEN_DEVICES = "devices"
    }
}

// Android 13+ blocks an app's notifications until the user allows them, and
// declaring the permission in the manifest is not enough - it has to be
// asked for. It never was, so every reminder this app posts was being
// dropped on those phones. Asked once signed in rather than over the login
// screen; Android itself stops showing the prompt after the user declines
// it twice, so asking on each launch until then isn't nagging.
@Composable
private fun NotificationPermissionRequest(signedIn: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(signedIn) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (signedIn && !granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
