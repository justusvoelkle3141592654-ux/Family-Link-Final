package com.familylink.ios.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.familylink.ios.BlockActivity
import com.familylink.ios.R
import com.familylink.ios.admin.DeviceAdmin
import com.familylink.ios.data.Prefs
import com.familylink.ios.util.ForegroundTracker
import com.familylink.ios.util.LockState

/**
 * Second line of defence and a latency booster.
 *
 *  1. Instant foreground hints for the monitor service.
 *  2. Anti-tamper: Settings is otherwise reachable, but any Settings screen that names this app
 *     by title — the App-Info page, its entry in the accessibility-service list, the per-app
 *     "display over other apps" toggle, its entry in device-admin apps — is bounced immediately
 *     and replaced by our block overlay. Those are exactly the screens that could switch this
 *     app's own protections off. Locking the screen is only a last resort after repeated
 *     attempts, and it always returns to Home so the child never resumes on that screen.
 *  3. Anti-bypass: block the pop-up / split-screen (multi-window) view while a lock is active,
 *     plus guest/user-switch, the power menu and the quick-settings shade.
 */
class AppAccessibilityService : AccessibilityService() {

    private lateinit var prefs: Prefs

    // Settings-intrusion state: debounce + escalation counter.
    private var lastSettingsActionAt = 0L
    private var lastIntrusionStreakAt = 0L
    private var settingsAttempts = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = Prefs.get(this)
        if (!prefs.isParentDevice) MonitorService.start(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        // Anti-bypass applies to the supervised device only.
        if (prefs.isParentDevice) return
        val pkg = event.packageName?.toString() ?: return

        // 2 — anti-tamper: a Settings screen that names this app is never reachable unless the
        // parent released it from the portal. General Settings navigation is otherwise fine.
        if (handleSettingsIntrusion(pkg, event)) return

        // 3 — anti-bypass surfaces.
        if (handlePotentialBypass(pkg, event)) return

        // Block multi-window / pop-up (split screen) whenever a lock is active. Checked on every
        // event (not just window-state) because some launchers open pop-up views without a
        // TYPE_WINDOW_STATE_CHANGED for our package.
        if (isGuarding() && hasMultipleAppWindows()) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            MonitorService.recheck(this)
            return
        }

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            ForegroundTracker.update(pkg)
            MonitorService.recheck(this)
        }
    }

    /** Fast path: read the shared in-memory state instead of parsing preferences. */
    private fun isGuarding(): Boolean = LockState.lockActive || prefs.isBedtime()

    private fun hasMultipleAppWindows(): Boolean = try {
        var appWindows = 0
        var hasSplitOrPip = false
        for (w in windows) {
            when (w.type) {
                AccessibilityWindowInfo.TYPE_APPLICATION -> appWindows++
                AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER -> hasSplitOrPip = true
            }
            if (w.isInPictureInPictureMode) hasSplitOrPip = true
        }
        hasSplitOrPip || appWindows >= 2
    } catch (_: Throwable) {
        false
    }

    /**
     * General Settings screens (Wi-Fi, display, sound, date & time, …) are left alone. Only a
     * screen that names this app is off-limits — that is where the accessibility-service
     * toggle, the "display over other apps" permission and the device-admin entry for THIS app
     * live, and switching any of those off disables every protection the app provides. Android
     * titles exactly those screens with the app's own label ("Völkle Link"), which is what this
     * checks for instead of blocking Settings wholesale.
     *
     * Strategy (overlay first, screen lock only as a last resort):
     *   1. immediately leave the screen (BACK, then HOME) and raise our block overlay,
     *   2. only if the child keeps hammering it (repeated attempts in a short window)
     *      do we fall back to locking the screen — and after unlocking they land on Home,
     *      not back on that screen.
     *
     * @return true if this was an intrusion we handled.
     */
    private fun handleSettingsIntrusion(pkg: String, event: AccessibilityEvent): Boolean {
        if (!isSettingsPackage(pkg)) return false
        // Never interfere during setup or while the parent authorised settings access.
        if (!prefs.setupDone || prefs.settingsUnlocked()) return false

        val label = getString(R.string.app_name)
        val text = (event.text?.joinToString(" ") ?: "") +
            " " + (event.contentDescription?.toString() ?: "")
        if (!text.contains(label, ignoreCase = true)) return false

        val now = android.os.SystemClock.uptimeMillis()
        // Short debounce only — we want to react on essentially every matching event so the
        // child cannot linger on a toggle page between two reactions.
        if (now - lastSettingsActionAt < 400) return true
        lastSettingsActionAt = now

        // Count rapid repeat attempts; reset the streak after a calm period.
        if (now - lastIntrusionStreakAt > 15_000) settingsAttempts = 0
        lastIntrusionStreakAt = now
        settingsAttempts++

        // 1) Always: get out of the screen and show the overlay instead.
        performGlobalAction(GLOBAL_ACTION_BACK)
        performGlobalAction(GLOBAL_ACTION_HOME)
        BlockActivity.launch(
            this,
            "Geschützter Bereich",
            "Diese Seite betrifft $label selbst und ist gesperrt. Freigabe über das Eltern-Portal.",
            bedtime = false,
            // Dismissible: the child just needs to leave the screen, not stay locked out.
            hardLock = false
        )

        // 2) Last resort only: persistent attempts -> lock the screen, then return Home so the
        //    child does not reappear on that screen after unlocking.
        if (settingsAttempts >= LOCK_AFTER_ATTEMPTS) {
            settingsAttempts = 0
            DeviceAdmin.lockNow(this)
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
        return true
    }

    private fun isSettingsPackage(pkg: String): Boolean =
        pkg == "com.android.settings" ||
            pkg == "com.samsung.android.settings" ||
            pkg.endsWith(".settings") ||
            pkg == "com.android.packageinstaller" ||
            pkg == "com.google.android.packageinstaller" ||
            pkg == "com.android.permissioncontroller"

    /** @return true if the event was a bypass attempt we handled (caller should stop). */
    private fun handlePotentialBypass(pkg: String, event: AccessibilityEvent): Boolean {
        if (pkg != "android" && pkg != "com.android.systemui") return false

        val text = (event.text?.joinToString(" ") ?: "").lowercase() +
            " " + (event.contentDescription?.toString()?.lowercase() ?: "")

        val looksLikeUserSwitch = text.contains("gast") || text.contains("guest") ||
            text.contains("nutzer wechseln") || text.contains("switch user") ||
            text.contains("benutzer hinzufügen") || text.contains("add user")
        if (looksLikeUserSwitch) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            return true
        }

        val looksLikePowerMenu = text.contains("ausschalten") || text.contains("neu starten") ||
            text.contains("power off") || text.contains("restart") ||
            text.contains("abgesicherter modus") || text.contains("safe mode")
        if (looksLikePowerMenu) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            return true
        }

        if (pkg == "com.android.systemui") {
            val shade = text.contains("schnelleinstellungen") || text.contains("quick settings") ||
                event.className?.toString()?.contains("QuickSettings", true) == true
            if (shade && isGuarding()) {
                performGlobalAction(GLOBAL_ACTION_HOME)
                return true
            }
        }
        return false
    }

    override fun onInterrupt() { /* no-op */ }

    private companion object {
        /** Screen locking is the last resort — only after this many rapid Settings attempts. */
        const val LOCK_AFTER_ATTEMPTS = 3
    }
}
