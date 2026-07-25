package com.familylink.ios.admin

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import android.os.UserManager

/**
 * Hard enforcement via Device Owner.
 *
 * When the app is provisioned as device owner (see README: `adb shell dpm set-device-owner …`
 * on a freshly reset phone) Android grants policy powers that a normal app can never have.
 * This is what turns the previous "best effort" protections into real guarantees:
 *
 *  - Lock task mode pins the block screen so HOME and Recents stop working.
 *  - Safe-mode boot, guest/extra users and factory reset are refused by the OS.
 *  - The app cannot be uninstalled or force-stopped.
 *  - Only our accessibility service may run, so it cannot be swapped out.
 *
 * DELIBERATELY NOT USED: wipeData(). The app never erases anything — every call here is
 * restrictive or protective only.
 */
object DeviceOwner {

    fun dpm(context: Context): DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    fun isDeviceOwner(context: Context): Boolean =
        runCatching { dpm(context).isDeviceOwnerApp(context.packageName) }.getOrDefault(false)

    /**
     * Apply every protective policy we can. Each call is guarded because availability differs
     * between OEMs and API levels; a single unsupported restriction must not abort the rest.
     */
    fun applyPolicies(context: Context) {
        if (!isDeviceOwner(context)) return
        val dpm = dpm(context)
        val admin = DeviceAdmin.componentName(context)

        // --- block the classic escape routes -----------------------------
        val restrictions = listOf(
            UserManager.DISALLOW_SAFE_BOOT,          // no safe-mode reboot
            UserManager.DISALLOW_ADD_USER,           // no second user
            UserManager.DISALLOW_FACTORY_RESET,      // no reset from settings
            UserManager.DISALLOW_APPS_CONTROL,       // no force-stop / clear-data on apps
            UserManager.DISALLOW_UNINSTALL_APPS,     // no uninstalling anything
            UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
            UserManager.DISALLOW_CONFIG_CREDENTIALS,
            UserManager.DISALLOW_DEBUGGING_FEATURES  // no ADB tricks
        )
        for (r in restrictions) runCatching { dpm.addUserRestriction(admin, r) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { dpm.addUserRestriction(admin, UserManager.DISALLOW_USER_SWITCH) }
        }

        // --- protect ourselves -------------------------------------------
        runCatching { dpm.setUninstallBlocked(admin, context.packageName, true) }
        // Only our own accessibility service may be active, so it cannot be replaced.
        runCatching {
            dpm.setPermittedAccessibilityServices(admin, listOf(context.packageName))
        }
        // Screens that may be pinned (kiosk) while a hard lock is in force.
        runCatching {
            dpm.setLockTaskPackages(admin, arrayOf(context.packageName))
        }
    }

    /** Release the restrictions again (used when the parent hands the device back). */
    fun clearPolicies(context: Context) {
        if (!isDeviceOwner(context)) return
        val dpm = dpm(context)
        val admin = DeviceAdmin.componentName(context)
        val restrictions = listOf(
            UserManager.DISALLOW_SAFE_BOOT,
            UserManager.DISALLOW_ADD_USER,
            UserManager.DISALLOW_FACTORY_RESET,
            UserManager.DISALLOW_APPS_CONTROL,
            UserManager.DISALLOW_UNINSTALL_APPS,
            UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
            UserManager.DISALLOW_CONFIG_CREDENTIALS,
            UserManager.DISALLOW_DEBUGGING_FEATURES
        )
        for (r in restrictions) runCatching { dpm.clearUserRestriction(admin, r) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { dpm.clearUserRestriction(admin, UserManager.DISALLOW_USER_SWITCH) }
        }
        runCatching { dpm.setUninstallBlocked(admin, context.packageName, false) }
        runCatching { dpm.setPermittedAccessibilityServices(admin, null) }
    }

    /**
     * Hide or reveal the Settings app entirely. Only possible as device owner — this is what
     * finally makes the settings screen unreachable instead of merely bounced.
     */
    fun setSettingsHidden(context: Context, hidden: Boolean) {
        if (!isDeviceOwner(context)) return
        val dpm = dpm(context)
        val admin = DeviceAdmin.componentName(context)
        for (pkg in SETTINGS_PACKAGES) {
            runCatching { dpm.setApplicationHidden(admin, pkg, hidden) }
        }
    }

    /**
     * Pin the current activity so HOME and Recents are disabled (kiosk mode). Used for hard
     * locks: bedtime, day limit and focus sessions.
     */
    fun startKiosk(activity: Activity) {
        if (!isDeviceOwner(activity)) return
        runCatching { activity.startLockTask() }
    }

    fun stopKiosk(activity: Activity) {
        runCatching { activity.stopLockTask() }
    }

    private val SETTINGS_PACKAGES = listOf(
        "com.android.settings",
        "com.samsung.android.settings"
    )
}
