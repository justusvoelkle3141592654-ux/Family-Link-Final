package com.familylink.ios.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.familylink.ios.admin.DeviceAdmin
import com.familylink.ios.data.Prefs
import com.familylink.ios.util.ForegroundTracker

/**
 * Second line of defence and a latency booster.
 *
 *  1. Instant foreground hints for the monitor service.
 *  2. Anti-tamper: if the user opens the device-admin deactivation screen or this app's App-Info
 *     page, lock the screen immediately (DevicePolicyManager.lockNow).
 *  3. Anti-bypass: block the pop-up / split-screen (multi-window) view while a lock is active,
 *     plus guest/user-switch, the power menu and the quick-settings shade.
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

        // 2 — anti-tamper: lock immediately on admin-deactivation or our App-Info page.
        if (handleAdminTamper(pkg, event)) return

        // 3 — anti-bypass surfaces.
        if (handlePotentialBypass(pkg, event)) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // Block multi-window / pop-up (split screen) while a lock is active.
            if (isGuarding() && hasMultipleAppWindows()) {
                performGlobalAction(GLOBAL_ACTION_HOME)
                return
            }
            ForegroundTracker.update(pkg)
            MonitorService.recheck(this)
        }
    }

    private fun isGuarding(): Boolean =
        prefs.isBedtime() || prefs.getBlockedToday().isNotEmpty()

    private fun hasMultipleAppWindows(): Boolean = try {
        windows.count { it.type == AccessibilityWindowInfo.TYPE_APPLICATION } >= 2
    } catch (_: Throwable) {
        false
    }

    /** @return true if this was an admin/App-Info tamper attempt we locked on. */
    private fun handleAdminTamper(pkg: String, event: AccessibilityEvent): Boolean {
        if (!pkg.contains("settings")) return false
        // Never auto-lock during setup, or while the parent has authorised settings access
        // (portal release / our own permission + admin-enable flows).
        if (!prefs.setupDone || prefs.settingsUnlocked()) return false
        val text = (event.text?.joinToString(" ") ?: "").lowercase() + " " +
            (event.contentDescription?.toString()?.lowercase() ?: "")

        val adminScreen = text.contains("geräteadministrator") || text.contains("geräte-admin") ||
            text.contains("device admin") || text.contains("device administrator")
        val ourAppInfo = (text.contains("family link")) &&
            (text.contains("deinstallier") || text.contains("uninstall") ||
                text.contains("beenden erzwingen") || text.contains("stopp erzwingen") ||
                text.contains("force stop") || text.contains("app-info") || text.contains("app info"))

        if (adminScreen || ourAppInfo) {
            DeviceAdmin.lockNow(this)
            return true
        }
        return false
    }

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
}
