package com.familylink.ios

import android.app.Application
import com.familylink.ios.data.Prefs
import com.familylink.ios.service.MonitorService

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // If setup is complete, start the guard (child device) and the sync link (both).
        val prefs = Prefs.get(this)
        if (prefs.setupDone) {
            if (!prefs.isParentDevice) MonitorService.start(this)
            com.familylink.ios.sync.SyncService.start(this)
        }
    }
}
