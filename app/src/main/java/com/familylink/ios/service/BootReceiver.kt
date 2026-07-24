package com.familylink.ios.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts the monitor service after boot or if it was killed. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            ACTION_RESTART -> MonitorService.start(context)
        }
    }

    companion object {
        const val ACTION_RESTART = "com.familylink.ios.RESTART"
    }
}
