package com.familylink.ios

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familylink.ios.data.Prefs
import com.familylink.ios.service.MonitorService
import com.familylink.ios.ui.cupertino.CupertinoButton
import com.familylink.ios.ui.screens.AppsScreen
import com.familylink.ios.ui.screens.HomeScreen
import com.familylink.ios.ui.screens.ParentPortalScreen
import com.familylink.ios.ui.screens.PermissionsScreen
import com.familylink.ios.ui.screens.PinMode
import com.familylink.ios.ui.screens.PinScreen
import com.familylink.ios.ui.theme.Cupertino
import com.familylink.ios.ui.theme.FamilyLinkTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 42)
        }
        // Self-heal: make sure the guard is running every time the app is opened.
        if (Prefs.get(this).setupDone) MonitorService.start(this)
        setContent {
            FamilyLinkTheme {
                Box(Modifier.fillMaxSize().background(Cupertino.SystemBackground)) {
                    RootNav()
                }
            }
        }
    }
}

private sealed class Route {
    // setup wizard
    object SetupPin : Route()
    object SetupPermissions : Route()
    object SetupApps : Route()
    // main
    object Home : Route()
    object VerifyPin : Route()
    object Portal : Route()
    object PortalApps : Route()
    object PortalPermissions : Route()
    object PortalChangePin : Route()
}

@Composable
private fun RootNav() {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }

    var route by remember {
        mutableStateOf<Route>(if (prefs.setupDone) Route.Home else Route.SetupPin)
    }

    when (route) {
        // ---------------- setup wizard ----------------
        Route.SetupPin -> PinScreen(
            mode = PinMode.SET,
            onSuccess = { route = Route.SetupPermissions }
        )

        Route.SetupPermissions -> PermissionsScreen(
            onAllGranted = { route = Route.SetupApps }
        )

        Route.SetupApps -> {
            AppsScreen()
            SetupFooter(text = "Fertig") {
                prefs.setupDone = true
                MonitorService.start(context)
                route = Route.Home
            }
        }

        // ---------------- main ----------------
        Route.Home -> HomeScreen(
            onOpenParentPortal = { route = Route.VerifyPin }
        )

        // Portal opens with the PIN anytime — no weekly restriction (requirement 5).
        Route.VerifyPin -> PinScreen(
            mode = PinMode.VERIFY,
            onSuccess = { route = Route.Portal },
            onCancel = { route = Route.Home }
        )

        Route.Portal -> ParentPortalScreen(
            onOpenApps = { route = Route.PortalApps },
            onOpenPermissions = { route = Route.PortalPermissions },
            onChangePin = { route = Route.PortalChangePin },
            onExit = { route = Route.Home }
        )

        Route.PortalApps -> {
            AppsScreen()
            SetupFooter(text = "Zurück") { route = Route.Portal }
        }

        Route.PortalPermissions -> PermissionsScreen(
            onAllGranted = { route = Route.Portal },
            showContinue = true
        )

        // Change PIN: already authenticated in the portal, so just set a new one.
        Route.PortalChangePin -> PinScreen(
            mode = PinMode.SET,
            onSuccess = { route = Route.Portal },
            onCancel = { route = Route.Portal }
        )
    }
}

@Composable
private fun SetupFooter(text: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Bottom
    ) {
        CupertinoButton(text = text, onClick = onClick)
    }
}
