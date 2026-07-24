package com.familylink.ios.util

/**
 * Tiny shared holder for "which app is on screen right now", written by the accessibility
 * service (which gets instant window-change callbacks) and read by the monitor service
 * (which does the per-second time accounting).
 */
object ForegroundTracker {
    @Volatile var currentPackage: String? = null
        private set

    @Volatile var lastChangeUptime: Long = 0L
        private set

    fun update(pkg: String?) {
        if (pkg.isNullOrBlank()) return
        currentPackage = pkg
        lastChangeUptime = android.os.SystemClock.uptimeMillis()
    }
}
