package com.familylink.ios

import android.os.Build
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
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
import com.familylink.ios.ui.components.NovaButton
import com.familylink.ios.sync.DeviceRole
import com.familylink.ios.sync.SyncService
import com.familylink.ios.ui.screens.AppsScreen
import com.familylink.ios.ui.screens.AuthScreen
import com.familylink.ios.ui.screens.ChoresChildScreen
import com.familylink.ios.ui.screens.ChoresParentScreen
import com.familylink.ios.ui.screens.StatsScreen
import com.familylink.ios.ui.screens.DevicesScreen
import com.familylink.ios.ui.screens.FocusScreen
import com.familylink.ios.ui.screens.RequestTimeScreen
import com.familylink.ios.ui.screens.ChildPortalScreen
import com.familylink.ios.ui.screens.ExtendTimeScreen
import com.familylink.ios.ui.screens.HomeScreen
import com.familylink.ios.ui.screens.PairingScreen
import com.familylink.ios.ui.screens.RoleChoiceScreen
import com.familylink.ios.ui.screens.SecurePinSetupScreen
import com.familylink.ios.ui.screens.ParentPortalScreen
import com.familylink.ios.ui.screens.PermissionsScreen
import com.familylink.ios.ui.screens.PinMode
import com.familylink.ios.ui.screens.ParentUnlockScreen
import com.familylink.ios.ui.screens.PinScreen
import com.familylink.ios.ui.theme.Nova
import com.familylink.ios.ui.theme.ThemeMode
import com.familylink.ios.ui.theme.FamilyLinkTheme

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val p = Prefs.get(this)
        // The parent app is a plain management app: no notifications, no background service,
        // no usage tracking of the parent's own phone.
        if (!p.isParentDevice) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 42)
            }
            if (p.setupDone) {
                MonitorService.start(this)
                SyncService.start(this)
            }
        }
        setContent {
            val prefs = Prefs.get(this)
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            var mode by remember { mutableStateOf(prefs.themeMode) }
            val dark = when (mode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> systemDark
            }
            FamilyLinkTheme(dark = dark) {
                Box(Modifier.fillMaxSize().background(Nova.Canvas)) {
                    RootNav(onThemeChanged = { mode = prefs.themeMode })
                }
            }
        }
    }
}

private sealed class Route {
    // setup wizard
    object SetupRole : Route()
    object SetupAuth : Route()
    object SetupPairing : Route()
    object SetupPin : Route()
    object SetupPermissions : Route()
    object SetupApps : Route()
    // main
    object Home : Route()
    object ExtendTime : Route()
    object VerifyPin : Route()
    object ParentUnlock : Route()
    object Portal : Route()
    object PortalApps : Route()
    object PortalPermissions : Route()
    object PortalChangePin : Route()
    object PortalSecurePin : Route()
    object PortalFocus : Route()
    object PortalDevices : Route()
    object RequestTime : Route()
    object PortalChores : Route()
    object ChildChores : Route()
    object PortalStats : Route()
}

@Composable
private fun RootNav(onThemeChanged: () -> Unit = {}) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }

    var route by remember {
        mutableStateOf<Route>(
            when {
                // Parent app: unlock (fingerprint or PIN) and go straight to the menu.
                prefs.setupDone && prefs.isParentDevice -> Route.ParentUnlock
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
            route = Route.SetupAuth
        }

        // Account step: sign up / sign in and register this device (max 3 per family).
        Route.SetupAuth -> AuthScreen(
            role = role,
            onDone = {
                SyncService.start(context)
                route = Route.SetupPin
            },
            onSkip = { route = Route.SetupPairing }
        )

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
                    Route.Portal
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
        // Parent app entry: fingerprint or PIN, then straight into the management menu.
        Route.ParentUnlock -> ParentUnlockScreen(onUnlocked = { route = Route.Portal })

        // Only the supervised device shows a home screen at all.
        Route.Home -> if (prefs.isParentDevice) {
            ParentUnlockScreen(onUnlocked = { route = Route.Portal })
        } else {
            ChildPortalScreen(
                onExtendTime = { route = Route.RequestTime },
                onOpenChores = { route = Route.ChildChores },
                onOpenParentArea = { route = Route.VerifyPin }
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
            onOpenFocus = { route = Route.PortalFocus },
            onOpenDevices = { route = Route.PortalDevices },
            onOpenChores = { route = Route.PortalChores },
            onOpenStats = { route = Route.PortalStats },
            onThemeChanged = onThemeChanged,
            onExit = { route = if (prefs.isParentDevice) Route.ParentUnlock else Route.Home }
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

        // Headline feature: timed focus sessions pushed to the child device.
        Route.PortalFocus -> FocusScreen(onBack = { route = Route.Portal })

        // Device management with the three-device limit.
        Route.PortalDevices -> DevicesScreen(onBack = { route = Route.Portal })

        // Child asks the parent for extra minutes.
        Route.RequestTime -> RequestTimeScreen(onClose = { route = Route.Home })

        // Chores: parent defines and confirms, child claims.
        Route.PortalChores -> ChoresParentScreen(onBack = { route = Route.Portal })
        Route.ChildChores -> ChoresChildScreen(onBack = { route = Route.Home })

        // Weekly statistics.
        Route.PortalStats -> StatsScreen(onBack = { route = Route.Portal })
    }
}

@Composable
private fun SetupFooter(text: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Bottom
    ) {
        NovaButton(text = text, onClick = onClick)
    }
}
