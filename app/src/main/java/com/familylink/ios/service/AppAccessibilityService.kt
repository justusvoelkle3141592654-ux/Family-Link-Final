package com.familylink.ios.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.familylink.ios.BlockActivity
import com.familylink.ios.data.Prefs
import com.familylink.ios.util.ForegroundTracker
import com.familylink.ios.util.LockState

/**
 * Second line of defence and a latency booster.
 *
 *  1. Instant foreground hints for the monitor service.
 *  2. Anti-tamper: the Settings app (incl. the App-Info page reached via long-press, which is
 *     where "display over other apps" and the device-admin toggle live) is bounced immediately
 *     and replaced by our block overlay. Locking the screen is only a last resort after repeated
 *     attempts, and it always returns to Home so the child never resumes inside Settings.
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
        instance = this
        if (!prefs.isParentDevice) MonitorService.start(this)
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        // Anti-bypass applies to the supervised device only.
        if (prefs.isParentDevice) return
        val pkg = event.packageName?.toString() ?: return

        // 2 — anti-tamper: the Settings app must not even be reachable unless the parent
        // released it from the portal. This fires on the very first event from Settings
        // (including the App-Info page reached via long-press), so the child never gets far
        // enough to toggle "display over other apps" or deactivate the admin.
        if (handleSettingsIntrusion(pkg)) return

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

    /**
     * Leave whatever is on screen for the home screen.
     *
     * Without device owner a blocked app cannot be suspended, so this is the only way to
     * actually get it off the screen instead of merely covering it.
     */
    fun goHome() {
        runCatching { performGlobalAction(GLOBAL_ACTION_HOME) }
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
     * The Settings app is off-limits unless the parent released it from the portal.
     * Strategy (overlay first, screen lock only as a last resort):
     *   1. immediately leave Settings (BACK, then HOME) and raise our block overlay,
     *   2. only if the child keeps hammering it (repeated attempts in a short window)
     *      do we fall back to locking the screen — and after unlocking they land on Home,
     *      not back inside Settings.
     *
     * @return true if this was a Settings intrusion we handled.
     */
    private fun handleSettingsIntrusion(pkg: String): Boolean {
        if (!isSettingsPackage(pkg)) return false
        // Never interfere during setup or while the parent authorised settings access.
        if (!prefs.setupDone || prefs.settingsUnlocked()) return false

        val now = android.os.SystemClock.uptimeMillis()
        // Short debounce only — we want to react on essentially every settings event so the
        // child cannot linger on a toggle page between two reactions.
        if (now - lastSettingsActionAt < 400) return true
        lastSettingsActionAt = now

        // Count rapid repeat attempts; reset the streak after a calm period.
        if (now - lastIntrusionStreakAt > 15_000) settingsAttempts = 0
        lastIntrusionStreakAt = now
        settingsAttempts++

        // 1) Always: get out of Settings and show the overlay instead.
        performGlobalAction(GLOBAL_ACTION_BACK)
        performGlobalAction(GLOBAL_ACTION_HOME)
        BlockActivity.launch(
            this,
            "Einstellungen gesperrt",
            "Die Systemeinstellungen sind gesperrt. Freigabe über das Eltern-Portal.",
            bedtime = false,
            // Dismissible: the child just needs to leave Settings, not stay locked out.
            hardLock = false
        )

        // 2) Last resort only: persistent attempts -> lock the screen, then return Home so the
        //    child does not reappear inside Settings after unlocking.
        if (settingsAttempts >= LOCK_AFTER_ATTEMPTS) {
            settingsAttempts = 0
            com.familylink.ios.util.ScreenLock.lockNow(this)
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

    companion object {
        /** Screen locking is the last resort — only after this many rapid Settings attempts. */
        private const val LOCK_AFTER_ATTEMPTS = 3

        /**
         * The connected service, or null while the permission is off. [ScreenLock] needs it to
         * lock the display without the device-admin permission.
         */
        @Volatile
        var instance: AppAccessibilityService? = null
            private set
    }
}
