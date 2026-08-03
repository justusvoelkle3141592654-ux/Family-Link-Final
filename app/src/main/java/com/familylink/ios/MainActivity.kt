package com.familylink.ios

import android.os.Build
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
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
import com.familylink.ios.ui.screens.ChildAppsScreen
import com.familylink.ios.ui.screens.ChildFocusScreen
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
    object ChildFocus : Route()
    object ChildFocusEnd : Route()
    object ChildDisplayLockPin : Route()
    object ChildApps : Route()
    object PortalStats : Route()
}

/**
 * Everything that sits behind the PIN. Leaving the app closes these; the child's own screens
 * are not in the list, because they are open to the child anyway.
 */
private val PARENT_AREA: Set<Route> = setOf(
    Route.VerifyPin, Route.Portal, Route.PortalApps, Route.PortalPermissions,
    Route.PortalChangePin, Route.PortalSecurePin, Route.PortalFocus, Route.PortalDevices,
    Route.PortalChores, Route.PortalStats, Route.ExtendTime
)

/** How long a trip to the system settings may take before the permissions page is closed too. */
private const val SETTINGS_TRIP_MS = 3 * 60 * 1000L

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
    // Minutes chosen on the child's display-lock chip, held while the PIN check runs.
    var pendingDisplayLockMinutes by remember { mutableStateOf(0) }

    /**
     * Where the back gesture leads from each screen.
     *
     * Navigation is by gesture, not by buttons: swiping back (or the system back key) walks up
     * the same path an in-app "Zurück" control used to. Setup steps are deliberately absent —
     * backing out of the wizard mid-way would leave the device half-configured.
     */
    val backTarget: Route? = when (route) {
        Route.PortalApps, Route.PortalPermissions, Route.PortalChangePin, Route.PortalSecurePin,
        Route.PortalFocus, Route.PortalDevices, Route.PortalChores, Route.PortalStats -> Route.Portal
        Route.VerifyPin, Route.ExtendTime, Route.RequestTime,
        Route.ChildChores, Route.ChildFocus, Route.ChildApps -> Route.Home
        Route.ChildFocusEnd, Route.ChildDisplayLockPin -> Route.ChildFocus
        else -> null
    }
    androidx.activity.compose.BackHandler(enabled = backTarget != null) {
        backTarget?.let { route = it }
    }

    // ---- leaving the app closes the parent area ----------------------------
    //
    // Anything behind the PIN is only open while the app is actually in front. The moment it
    // goes to the background — Home, app switcher, swiped out of recents — the portal is shut
    // and coming back lands on the lock screen again, never on the page that was open. On the
    // parent phone that is the unlock screen, on the child's phone its own home screen.
    //
    // The one exception is the permissions page: its whole job is to send the user into the
    // system settings and back, so it survives a short trip and is closed like everything else
    // once that trip took too long to have been one.
    val owner = LocalLifecycleOwner.current
    var leftPermissionsAt by remember { mutableStateOf(0L) }
    val lockedEntry: Route = if (prefs.isParentDevice) Route.ParentUnlock else Route.Home
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, event ->
            // On the child's phone only the parent area is closed — its own screens (chores,
            // "Handy weglegen", asking for more time) are not behind a PIN and have nothing to
            // protect. On the parent phone every screen is the parent area.
            val protectedNow = prefs.setupDone && (prefs.isParentDevice || route in PARENT_AREA)
            if (event == Lifecycle.Event.ON_STOP && protectedNow) {
                if (route == Route.PortalPermissions) leftPermissionsAt = System.currentTimeMillis()
                else if (route != lockedEntry) route = lockedEntry
            }
            // Back from the settings trip? Keep the page only if it really was a short one.
            if (event == Lifecycle.Event.ON_START && protectedNow &&
                route == Route.PortalPermissions &&
                System.currentTimeMillis() - leftPermissionsAt > SETTINGS_TRIP_MS
            ) {
                route = lockedEntry
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

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
                // One PIN for the whole family: publish it so the other device checks the same
                // code. Only the salt and hash travel, never the PIN itself.
                publishPin(context)
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
                onOpenFocus = { route = Route.ChildFocus },
                onOpenParentArea = { route = Route.VerifyPin },
                onOpenAllApps = { route = Route.ChildApps }
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

        }

        Route.PortalPermissions -> PermissionsScreen(
            onAllGranted = { route = Route.Portal },
            showContinue = true
        )

        // Change PIN: already authenticated in the portal, so just set a new one — and share
        // it, otherwise the two devices would drift apart on different codes.
        Route.PortalChangePin -> PinScreen(
            mode = PinMode.SET,
            onSuccess = { publishPin(context); route = Route.Portal },
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

        // Every app on the phone, as the child may see it: look, don't touch. Reached from the
        // "Bildschirmzeit" row on the child's home screen.
        Route.ChildApps -> ChildAppsScreen()

        // Child-started focus ("Handy weglegen"). Ending it early needs the parent PIN, so a
        // session the child committed to cannot be undone with a single tap.
        Route.ChildFocus -> ChildFocusScreen(
            onBack = { route = Route.Home },
            onRequestEnd = { route = Route.ChildFocusEnd },
            onRequestDisplayLock = { minutes ->
                pendingDisplayLockMinutes = minutes
                route = Route.ChildDisplayLockPin
            }
        )
        Route.ChildFocusEnd -> PinScreen(
            mode = PinMode.VERIFY,
            onSuccess = {
                prefs.setSelfFocusSession(com.familylink.ios.sync.FocusSession.OFF)
                MonitorService.recheck(context)
                route = Route.Home
            },
            onCancel = { route = Route.ChildFocus }
        )

        // Display lock triggered from the child's own Fokus screen: guarded by the child's own
        // PIN (not the family/parent one), so a sibling who grabs the phone cannot lock it. The
        // first-ever use doubles as setup — the code chosen there is what future taps verify
        // against. Unrationed on purpose: the child is choosing this for themselves, so none of
        // the weekly caps a parent-triggered lock enforces apply here.
        Route.ChildDisplayLockPin -> PinScreen(
            mode = if (prefs.isChildLockPinSet) PinMode.CHILD_LOCK_VERIFY else PinMode.CHILD_LOCK_SET,
            onSuccess = {
                prefs.startScreenLockUnrationed(pendingDisplayLockMinutes)
                MonitorService.recheck(context)
                SyncService.pushNow(context)
                route = Route.ChildFocus
            },
            onCancel = { route = Route.ChildFocus }
        )

        // Weekly statistics.
        Route.PortalStats -> StatsScreen(onBack = { route = Route.Portal })
    }
}

/**
 * Push the PIN just set to the family node, on a worker thread.
 *
 * Adopting it locally as the shared one too means this device keeps working even if the write
 * never reaches the server — it would otherwise be the only device not on the family PIN.
 */
private fun publishPin(context: android.content.Context) {
    val prefs = Prefs.get(context)
    prefs.sharablePin()?.let { (salt, hash) -> prefs.setSharedPin(salt, hash) }
    kotlin.concurrent.thread(isDaemon = true) {
        runCatching { com.familylink.ios.sync.SyncManager(context).pushPortalPin() }
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
