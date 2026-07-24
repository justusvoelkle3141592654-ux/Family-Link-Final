package com.familylink.ios.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.familylink.ios.data.LimitEngine
import com.familylink.ios.data.LockDecision
import com.familylink.ios.data.Prefs
import com.familylink.ios.lock.LockOverlayManager
import com.familylink.ios.util.ForegroundTracker

/**
 * The accessibility service is the app's eyes and its first line of anti-bypass defence.
 *
 *  1. Foreground detection: TYPE_WINDOW_STATE_CHANGED tells us instantly which app is on top,
 *     so the monitor service can lock within a second of an app switch.
 *  2. Bypass blocking (best-effort on a non-device-owner install):
 *       - Guest / secondary-profile switch surfaces  -> bounce back with GLOBAL_ACTION_HOME
 *       - System power dialog (safe-mode reboot path) -> dismiss with GLOBAL_ACTION_BACK
 *       - Quick-settings shade while locked           -> collapse it again
 *     A device-owner provisioning (see README) upgrades several of these to hard guarantees.
 */
class AppAccessibilityService : AccessibilityService() {

    private lateinit var prefs: Prefs
    private lateinit var engine: LimitEngine

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = Prefs.get(this)
        engine = LimitEngine(prefs)
        // Make sure the guard service is running the moment accessibility is granted.
        MonitorService.start(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return

        // --- anti-bypass first -------------------------------------------------
        if (handlePotentialBypass(pkg, event)) return

        // --- foreground tracking ----------------------------------------------
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            ForegroundTracker.update(pkg)
            // Ask the monitor to re-evaluate immediately for a snappy lock.
            MonitorService.recheck(this)

            // If we are already in a hard-lock state (bedtime / limit) and the child managed
            // to surface a different app, re-assert the overlay right away.
            val decision = engine.evaluate(pkg)
            if (decision !is LockDecision.Allowed && !LockOverlayManager.isShowing) {
                LockOverlayManager.show(this, decision)
            }
        }
    }

    /**
     * @return true if the event was a bypass attempt we handled (caller should stop).
     */
    private fun handlePotentialBypass(pkg: String, event: AccessibilityEvent): Boolean {
        // Only defend hard while limits are actually in force.
        val guarding = prefs.isBedtime() || engine.evaluate(ForegroundTracker.currentPackage) !is LockDecision.Allowed
        val text = (event.text?.joinToString(" ") ?: "").lowercase() +
            " " + (event.contentDescription?.toString()?.lowercase() ?: "")

        // Guest profile / user switching surfaces.
        val looksLikeUserSwitch = text.contains("gast") || text.contains("guest") ||
            text.contains("nutzer wechseln") || text.contains("switch user") ||
            text.contains("benutzer hinzufügen") || text.contains("add user")
        if (looksLikeUserSwitch) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            return true
        }

        // System power dialog -> route to safe mode. Bounce it away.
        val looksLikePowerMenu = (pkg == "android" || pkg == "com.android.systemui") &&
            (text.contains("ausschalten") || text.contains("neu starten") ||
                text.contains("power off") || text.contains("restart") ||
                text.contains("abgesicherter modus") || text.contains("safe mode"))
        if (looksLikePowerMenu) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            return true
        }

        // While a hard-lock is in force, collapse the quick-settings/notification shade.
        if (guarding && pkg == "com.android.systemui") {
            val shade = text.contains("schnelleinstellungen") || text.contains("quick settings") ||
                event.className?.toString()?.contains("QuickSettings", true) == true
            if (shade) {
                performGlobalAction(GLOBAL_ACTION_HOME)
                return true
            }
        }
        return false
    }

    override fun onInterrupt() { /* no-op */ }
}
