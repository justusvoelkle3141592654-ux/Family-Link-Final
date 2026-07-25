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
import com.familylink.ios.sync.DeviceRole
import com.familylink.ios.sync.SyncService
import com.familylink.ios.ui.screens.AppsScreen
import com.familylink.ios.ui.screens.ChildPortalScreen
import com.familylink.ios.ui.screens.ExtendTimeScreen
import com.familylink.ios.ui.screens.HomeScreen
import com.familylink.ios.ui.screens.PairingScreen
import com.familylink.ios.ui.screens.RoleChoiceScreen
import com.familylink.ios.ui.screens.SecurePinSetupScreen
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
        // Self-heal: make sure the guard and the sync link are running on every app start.
        val p = Prefs.get(this)
        if (p.setupDone) {
            if (p.isChildDevice || p.deviceRole == DeviceRole.UNSET) MonitorService.start(this)
            SyncService.start(this)
        }
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
    object SetupRole : Route()
    object SetupPairing : Route()
    object SetupPin : Route()
    object SetupPermissions : Route()
    object SetupApps : Route()
    // main
    object Home : Route()
    object ExtendTime : Route()
    object VerifyPin : Route()
    object Portal : Route()
    object PortalApps : Route()
    object PortalPermissions : Route()
    object PortalChangePin : Route()
    object PortalSecurePin : Route()
}

@Composable
private fun RootNav() {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }

    var route by remember {
        mutableStateOf<Route>(
            when {
                prefs.setupDone -> Route.Home
                prefs.deviceRole == DeviceRole.UNSET -> Route.SetupRole
                else -> Route.SetupPairing
            }
        )
    }
    // Role drives both the wizard path and which home screen is shown.
    var role by remember { mutableStateOf(prefs.deviceRole) }

    when (route) {
        // ---------------- setup wizard ----------------
        Route.SetupRole -> RoleChoiceScreen { chosen ->
            prefs.deviceRole = chosen
            role = chosen
            route = Route.SetupPairing
        }

        Route.SetupPairing -> PairingScreen(
            role = role,
            onPaired = {
                SyncService.start(context)
                route = Route.SetupPin
            },
            onSkip = { route = Route.SetupPin }
        )

        Route.SetupPin -> PinScreen(
            mode = PinMode.SET,
            onSuccess = {
                // Only the supervised device needs system permissions.
                route = if (role == DeviceRole.CHILD) Route.SetupPermissions else {
                    prefs.setupDone = true
                    Route.Home
                }
            }
        )

        Route.SetupPermissions -> PermissionsScreen(
            onAllGranted = { route = Route.SetupApps }
        )

        Route.SetupApps -> {
            AppsScreen()
            SetupFooter(text = "Fertig") {
                prefs.setupDone = true
                MonitorService.start(context)
                SyncService.start(context)
                route = Route.Home
            }
        }

        // ---------------- main ----------------
        // The child device gets its own informational portal; the parent keeps the control UI.
        Route.Home -> if (prefs.isChildDevice) {
            ChildPortalScreen(
                onExtendTime = { route = Route.ExtendTime },
                onOpenParentArea = { route = Route.VerifyPin }
            )
        } else {
            HomeScreen(
                onOpenParentPortal = { route = Route.VerifyPin },
                onExtendTime = { route = Route.ExtendTime }
            )
        }

        // Time extension is protected inside the flow by the secure PIN.
        Route.ExtendTime -> ExtendTimeScreen(onClose = { route = Route.Home })

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
            onSetSecurePin = { route = Route.PortalSecurePin },
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

        // Set/change the long secure PIN used to grant time extensions.
        Route.PortalSecurePin -> SecurePinSetupScreen(
            onDone = { route = Route.Portal },
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
