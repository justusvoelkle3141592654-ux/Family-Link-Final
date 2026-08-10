package com.familylink.ios.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Restarts the monitor service after boot or if it was killed.
 *
 * The window right after a reboot used to be the way out: the guard was not up yet, so Settings
 * was reachable for a few seconds and the accessibility service or the admin could be switched
 * off from there. The policies below are re-applied on the very first broadcast — before the
 * user has even unlocked, on the direct-boot pass — and they need no preferences, so they work
 * while the encrypted storage is still locked.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            ACTION_RESTART -> {
                // First, and before anything that touches preferences: close the boot gap.
                // Both calls are device-owner policy and available in direct boot.
                runCatching { com.familylink.ios.admin.DeviceOwner.applyPolicies(context) }
                runCatching { com.familylink.ios.admin.DeviceOwner.setSettingsHidden(context, true) }

                // MonitorService.start() already refuses to run on a parent device.
                MonitorService.start(context)
                com.familylink.ios.sync.SyncService.start(context)
                // Parent side: only comes back if notifications were switched on.
                ParentWatchService.sync(context)
            }
        }
    }

    companion object {
        const val ACTION_RESTART = "com.familylink.ios.RESTART"
    }
}
