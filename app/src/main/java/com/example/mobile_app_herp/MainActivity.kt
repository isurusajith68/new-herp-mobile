package com.example.mobile_app_herp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.mobile_app_herp.data.Herp
import com.example.mobile_app_herp.data.Property
import com.example.mobile_app_herp.data.Push
import com.example.mobile_app_herp.ui.LoginScreen
import com.example.mobile_app_herp.ui.ModulesScreen
import com.example.mobile_app_herp.ui.NewRequestScreen
import com.example.mobile_app_herp.ui.PropertyPickerScreen
import com.example.mobile_app_herp.ui.RequestsScreen
import com.example.mobile_app_herp.ui.UpdateGate
import com.example.mobile_app_herp.ui.theme.HerpTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Herp.init(this)
        // The channel must exist before the first push lands — Android 8+ drops
        // notifications on an unknown channel without any error.
        Push.ensureChannel(this)
        enableEdgeToEdge()
        setContent {
            HerpTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    HerpApp(Modifier.padding(innerPadding))
                    // Sits above the app rather than gating it — an update offer
                    // must never stand between someone and their shift.
                    UpdateGate()
                }
            }
        }
    }
}

/** Where inside a chosen property the user currently is. */
private enum class PropertyScreen { MODULES, REQUESTS, NEW_REQUEST }

/**
 * Sign in → pick a property → that property's modules → the Inventory request
 * queue. A stored session skips straight to the picker; a 401 that survives a
 * refresh attempt drops back to sign-in.
 */
@Composable
private fun HerpApp(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var signedIn by remember { mutableStateOf(Herp.prefs.hasSession) }
    // Held in Herp so rotation does not bounce the user back to the picker.
    var property by remember { mutableStateOf<Property?>(Herp.selectedProperty) }
    var screen by remember { mutableStateOf(PropertyScreen.MODULES) }
    // Bumped after a save so the list refetches instead of showing stale rows.
    var requestsVersion by remember { mutableStateOf(0) }

    fun selectProperty(next: Property?) {
        Herp.selectedProperty = next
        property = next
        screen = PropertyScreen.MODULES
    }

    // Registering needs a session, so it runs on sign-in and on every cold start
    // that already has one. Safe to repeat: the server upserts on the token.
    LaunchedEffect(signedIn) {
        if (signedIn) Push.syncToken()
    }

    // Android 13+ gates the tray behind a runtime grant. Asked only once we are
    // signed in — a permission prompt on the login screen has no context yet.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Declining is fine: everything still works, just quietly. */ }

    LaunchedEffect(signedIn) {
        if (!signedIn || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun endSession(revokeRemotely: Boolean) {
        selectProperty(null)
        Herp.reset()
        signedIn = false
        scope.launch {
            // Before the session is cleared — unregistering is an authenticated
            // call, and a handset left registered would keep buzzing the person
            // who just signed out.
            Push.unregister()
            if (revokeRemotely) Herp.client.logoutRemote() else Herp.client.logout()
        }
    }

    when {
        !signedIn -> LoginScreen(
            onSignedIn = { signedIn = true },
            modifier = modifier,
        )

        property == null -> PropertyPickerScreen(
            onPick = ::selectProperty,
            onSessionLost = { endSession(revokeRemotely = false) },
            onSignOut = { endSession(revokeRemotely = true) },
            modifier = modifier,
        )

        else -> when (screen) {
            PropertyScreen.MODULES -> {
                BackHandler { selectProperty(null) }
                ModulesScreen(
                    property = property!!,
                    onBack = { selectProperty(null) },
                    onOpenModule = { screen = PropertyScreen.REQUESTS },
                    modifier = modifier,
                )
            }

            PropertyScreen.REQUESTS -> {
                BackHandler { screen = PropertyScreen.MODULES }
                RequestsScreen(
                    property = property!!,
                    // `key` forces a fresh load after a save without threading a
                    // reload callback down through the screen.
                    key = requestsVersion,
                    onBack = { screen = PropertyScreen.MODULES },
                    onNewRequest = { screen = PropertyScreen.NEW_REQUEST },
                    modifier = modifier,
                )
            }

            PropertyScreen.NEW_REQUEST -> {
                BackHandler { screen = PropertyScreen.REQUESTS }
                NewRequestScreen(
                    property = property!!,
                    onSaved = {
                        requestsVersion++
                        screen = PropertyScreen.REQUESTS
                    },
                    onCancel = { screen = PropertyScreen.REQUESTS },
                    modifier = modifier,
                )
            }
        }
    }
}
