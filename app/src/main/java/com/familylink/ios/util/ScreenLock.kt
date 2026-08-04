package com.familylink.ios.util

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Build
import com.familylink.ios.admin.DeviceAdmin
import com.familylink.ios.service.AppAccessibilityService

/**
 * Turning the display off, by whichever route this phone actually grants us.
 *
 * The device administrator used to be the only way, and it is the one permission the app marks
 * as merely "recommended" — so on most installations `lockNow()` silently did nothing and every
 * lock button in the app looked broken. The accessibility service is required for the app to
 * work at all, and from Android 9 it can lock the screen itself, so that is tried first and the
 * admin is only the fallback for Android 8.
 */
object ScreenLock {

    /** @return true if the display was actually locked. */
    fun lockNow(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val service = AppAccessibilityService.instance
            if (service != null) {
                val ok = runCatching {
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
                }.getOrDefault(false)
                if (ok) return true
            }
        }
        return runCatching {
            DeviceAdmin.lockNow(context)
            DeviceAdmin.isActive(context)
        }.getOrDefault(false)
    }

    /**
     * Can this device be locked at all right now? Used to tell the parent up front instead of
     * letting them tap a button that quietly does nothing.
     */
    fun available(context: Context): Boolean =
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && AppAccessibilityService.instance != null) ||
            runCatching { DeviceAdmin.isActive(context) }.getOrDefault(false)
}
