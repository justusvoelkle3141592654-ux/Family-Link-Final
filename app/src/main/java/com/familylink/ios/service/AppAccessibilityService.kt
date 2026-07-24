package com.familylink.ios.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.familylink.ios.data.Prefs
import com.familylink.ios.util.ForegroundTracker

/**
 * Second line of defence and a latency booster.
 *
 * Measurement now comes from UsageStats (see MonitorService), so this service is optional for
 * counting. What it adds:
 *  1. Instant foreground hints: on a window switch we update [ForegroundTracker] and ask the
 *     monitor to re-check immediately, so the lock appears the moment a limited app opens.
 *  2. Best-effort anti-bypass: guest/user switch, power menu (safe mode) and the quick-settings
 *     shade while a lock is active. (A device-owner install upgrades these to hard blocks.)
 */
class AppAccessibilityService : AccessibilityService() {

    private lateinit var prefs: Prefs

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = Prefs.get(this)
        MonitorService.start(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return

        if (handlePotentialBypass(pkg, event)) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            ForegroundTracker.update(pkg)
            MonitorService.recheck(this)
        }
    }

    /** @return true if the event was a bypass attempt we handled (caller should stop). */
    private fun handlePotentialBypass(pkg: String, event: AccessibilityEvent): Boolean {
        // Only inspect events from the surfaces that can be used to bypass; skip everything
        // else immediately so normal app usage stays cheap.
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

        val looksLikePowerMenu = (pkg == "android" || pkg == "com.android.systemui") &&
            (text.contains("ausschalten") || text.contains("neu starten") ||
                text.contains("power off") || text.contains("restart") ||
                text.contains("abgesicherter modus") || text.contains("safe mode"))
        if (looksLikePowerMenu) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            return true
        }

        if (pkg == "com.android.systemui") {
            val shade = text.contains("schnelleinstellungen") || text.contains("quick settings") ||
                event.className?.toString()?.contains("QuickSettings", true) == true
            // Only fight the shade while a lock is actually in force.
            if (shade && (prefs.isBedtime() || prefs.getBlockedToday().isNotEmpty())) {
                performGlobalAction(GLOBAL_ACTION_HOME)
                return true
            }
        }
        return false
    }

    override fun onInterrupt() { /* no-op */ }
}
