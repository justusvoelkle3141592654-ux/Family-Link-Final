package com.familylink.ios.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.familylink.ios.PinVerificationActivity
import com.familylink.ios.data.Prefs
import com.familylink.ios.util.ForegroundTracker
import com.familylink.ios.util.LockState

/**
 * Second line of defence and a latency booster.
 *
 *  1. Instant foreground hints for the monitor service.
 *  2. Anti-tamper: the Settings app (incl. the App-Info page reached via long-press, which is
 *     where "display over other apps" and the device-admin toggle live) is put behind the family
 *     PIN. The parent types it and Settings is theirs for a few minutes; a wrong or cancelled
 *     entry returns Home, so the child never resumes inside Settings. The accessibility and
 *     device-admin pages are additionally matched by their own text, which catches the routes
 *     into them that never open a settings window.
 *  3. Anti-bypass: block the pop-up / split-screen (multi-window) view while a lock is active,
 *     plus guest/user-switch, the power menu and the quick-settings shade.
 */
class AppAccessibilityService : AccessibilityService() {

    private lateinit var prefs: Prefs

    // Settings-intrusion state: debounce so one burst raises exactly one PIN prompt.
    private var lastSettingsActionAt = 0L

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

        // 2b — the accessibility and device-admin pages, matched by what they say, so the
        // routes into them that never open a settings window are covered as well.
        if (handleProtectedSettingsPage(event)) return

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
            // Raise the lock here, not by asking the monitor to look on its next tick. This
            // event is the same one Android switches screens on, so deciding now puts the lock
            // up before the app underneath has finished drawing — instead of a second and a half
            // later, which was long enough to open something, use it and wonder why nothing
            // happened. The monitor is still nudged, because measuring usage is its job.
            LockEnforcer.onForeground(this, pkg)
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
     * Settings is behind the family PIN.
     *
     * The old reaction — bounce out and raise the block screen — also locked the parent out, so
     * every real repair became a two-device errand. Now the PIN decides: the parent types it and
     * Settings is theirs for a few minutes; anyone else gets the home screen.
     *
     * @return true if this was a Settings intrusion we handled.
     */
    private fun handleSettingsIntrusion(pkg: String): Boolean {
        if (!isSettingsPackage(pkg)) return false
        // Never interfere during setup or while the PIN (or the portal) released Settings.
        if (!prefs.setupDone || prefs.settingsUnlocked()) return false
        // Nor while the lock screen's own button is holding a window open — that window exists
        // precisely so a revoked permission can be granted again, and bouncing the child out of
        // the page they were sent to would make the repair impossible.
        if (prefs.lockEscapeAllowsAny(SETTINGS_ESCAPE_PACKAGES)) return false
        // The PIN is already up; every further settings event belongs to the page behind it.
        if (pinPromptShowing) return true

        val now = android.os.SystemClock.uptimeMillis()
        // Short debounce so one burst of settings events raises exactly one prompt.
        if (now - lastSettingsActionAt < 400) return true
        lastSettingsActionAt = now

        pinPromptShowing = true
        PinVerificationActivity.launch(this)
        return true
    }

    /**
     * The two pages the whole protection rests on: the accessibility list (switch this service
     * off) and the device-admin list (deactivate the admin, then uninstall). They are reached
     * from Settings, so the PIN above covers them — but they are also reachable straight from a
     * notification, a search result or a deep link, which never passes through a settings window
     * we would see. Matching the page by what it says catches those routes too.
     *
     * @return true if this was such a page and we handled it.
     */
    private fun handleProtectedSettingsPage(event: AccessibilityEvent): Boolean {
        if (!prefs.setupDone || prefs.settingsUnlocked()) return false
        if (prefs.lockEscapeAllowsAny(SETTINGS_ESCAPE_PACKAGES)) return false
        if (pinPromptShowing) return true

        val text = ((event.text?.joinToString(" ") ?: "") + " " +
            (event.contentDescription?.toString() ?: "")).lowercase()
        if (text.isBlank()) return false

        val guarded = PROTECTED_PAGE_MARKERS.any { text.contains(it) }
        if (!guarded) return false

        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastSettingsActionAt < 400) return true
        lastSettingsActionAt = now

        // Leave the page first — the PIN window is what should be on top of it, not beside it.
        performGlobalAction(GLOBAL_ACTION_BACK)
        pinPromptShowing = true
        PinVerificationActivity.launch(this)
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
        /** The packages a repair window covers — the settings apps the lock screen sends to. */
        private val SETTINGS_ESCAPE_PACKAGES =
            com.familylink.ios.admin.DeviceOwner.SETTINGS_PACKAGES

        /**
         * What the two protected settings pages say about themselves, in the languages this
         * phone can be set to. Matching text rather than an activity name is deliberate: OEMs
         * rename those activities freely (this started with a Nothing Phone), and the visible
         * label is the one thing that has to stay recognisable to the person using it.
         */
        private val PROTECTED_PAGE_MARKERS = listOf(
            // accessibility
            "bedienungshilfen", "accessibility",
            // device admin / uninstall
            "geräteadministrator", "geräteverwaltungs-apps", "device admin", "device administrator",
            "deinstallieren", "uninstall", "app-info", "app info"
        )

        /** True while the settings PIN is on screen, so prompts do not stack. */
        @Volatile
        private var pinPromptShowing: Boolean = false

        /**
         * Send the phone to the home screen from outside the service (the PIN activity, on a
         * wrong or cancelled entry).
         *
         * @return true when a connected service did it; false when there was none, and the
         *         caller has to fall back to launching Home itself.
         */
        fun goHomeNow(): Boolean {
            val svc = instance ?: return false
            return runCatching {
                svc.performGlobalAction(GLOBAL_ACTION_HOME)
            }.getOrDefault(false)
        }

        /** The PIN closed, either way — allow a fresh prompt on the next intrusion. */
        fun onPinPromptClosed() {
            pinPromptShowing = false
        }

        /**
         * The connected service, or null while the permission is off. [ScreenLock] needs it to
         * lock the display without the device-admin permission.
         */
        @Volatile
        var instance: AppAccessibilityService? = null
            private set
    }
}
