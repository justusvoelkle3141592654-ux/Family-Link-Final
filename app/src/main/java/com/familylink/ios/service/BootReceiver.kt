package com.familylink.ios.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.familylink.ios.BlockActivity
import com.familylink.ios.data.Prefs

/**
 * Restarts the monitor service after boot or if it was killed.
 *
 * The window right after a reboot was the way out: the guard was not up yet, so Settings was
 * reachable for a few seconds and the accessibility service or the admin could be switched off
 * from there. Three things close it, in this order:
 *
 *  1. device-owner policies are re-applied on the very first broadcast — before the user has
 *     even unlocked, on the direct-boot pass — and they need no preferences, so they work while
 *     the encrypted storage is still locked,
 *  2. the lock is put on screen *here*, synchronously, rather than waiting for the monitor to
 *     start and reach its first tick. That wait was the gap: half a second of service startup is
 *     enough to reach the accessibility page, and the seal that was supposed to cover it only
 *     arrived afterwards,
 *  3. the app is brought to the front, so the phone comes back from a restart showing the lock
 *     instead of showing the launcher with the lock somewhere behind it.
 *
 * Steps 2 and 3 need preferences, so they only run on the unlocked pass ([ACTION_BOOT_COMPLETED]);
 * on the direct-boot pass the phone is still at the lock screen, where there is nothing to guard.
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

                // Credential-encrypted storage is not readable yet on the direct-boot pass, so
                // everything below waits for the unlocked one.
                if (intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED) {
                    raiseGuardNow(context)
                }

                // MonitorService.start() already refuses to run on a parent device.
                MonitorService.start(context)
                com.familylink.ios.sync.SyncService.start(context)
                // Parent side: only comes back if notifications were switched on.
                ParentWatchService.sync(context)
            }
        }
    }

    /**
     * Put the restart seal on screen straight away, and bring the app with it.
     *
     * Both are guarded by [runCatching] because this runs in a broadcast on a phone that has
     * just booted: a failure here must not stop the services below from starting, which are what
     * keep the phone protected for the rest of the day.
     */
    private fun raiseGuardNow(context: Context) {
        val prefs = runCatching { Prefs.get(context) }.getOrNull() ?: return
        if (prefs.isParentDevice || !prefs.setupDone) return

        // The overlay (or, without that permission, the full-screen notification) — up now,
        // not on the monitor's first tick.
        runCatching { LockEnforcer.evaluate(context, null) }

        // And the app itself in front. Starting an activity from the background is normally
        // refused from Android 10 on; holding SYSTEM_ALERT_WINDOW is one of the exemptions, and
        // that is the permission this app already rests on. Where it is missing the call is
        // simply dropped and the overlay/notification above still stands.
        runCatching {
            BlockActivity.launch(
                context,
                "Kindersicherung startet",
                "Das Handy ist noch einen Moment gesperrt, während der Schutz hochfährt.",
                bedtime = false,
                hardLock = true
            )
        }
    }

    companion object {
        const val ACTION_RESTART = "com.familylink.ios.RESTART"
    }
}
