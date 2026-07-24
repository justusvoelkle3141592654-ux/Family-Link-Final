package com.familylink.ios.admin

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Device Administrator.
 *
 * Purpose is narrow and deliberate: while this admin is active the OS refuses to uninstall
 * the app (uninstall is greyed out in Settings), which is what makes the child-safety app
 * tamper-resistant.
 *
 * Per spec, *enabling* the admin must NOT lock the device — so we do nothing special in
 * onEnabled() and we never call lockNow() from here. Locking is driven only by the
 * time-limit / bedtime logic in the monitor service.
 */
class DeviceAdmin : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        // Intentionally empty: do not lock, do not force any policy on activation.
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        // Shown when the parent tries to deactivate the admin (which is the gate to uninstall).
        return "Der Geräteschutz der Kindersicherung wird deaktiviert. Danach kann die App " +
            "deinstalliert werden."
    }

    companion object {
        fun componentName(context: Context) =
            ComponentName(context.applicationContext, DeviceAdmin::class.java)

        fun isActive(context: Context): Boolean {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            return dpm.isAdminActive(componentName(context))
        }

        /** Lock the screen immediately (anti-tamper). No-op if the admin isn't active. */
        fun lockNow(context: Context) {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (dpm.isAdminActive(componentName(context))) {
                runCatching { dpm.lockNow() }
            }
        }

        /** Intent that opens the system "activate device admin" prompt. */
        fun enableIntent(context: Context): Intent =
            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName(context))
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Aktivieren, um die Deinstallation der Kindersicherung zu verhindern. " +
                        "Das Gerät wird dadurch nicht gesperrt."
                )
            }
    }
}
