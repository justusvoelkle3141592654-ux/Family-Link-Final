package com.familylink.ios

import android.app.Application
import com.familylink.ios.data.Prefs
import com.familylink.ios.service.MonitorService

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // If setup is already complete, make sure the guard is running from app start.
        if (Prefs.get(this).setupDone) {
            MonitorService.start(this)
        }
    }
}
