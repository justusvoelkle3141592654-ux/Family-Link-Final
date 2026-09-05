package com.familylink.ios.util

/**
 * Cheap, in-memory snapshot of the current enforcement state, written by MonitorService and
 * read by the accessibility service on every event. Avoids parsing SharedPreferences/JSON in
 * the hot path so anti-bypass reactions stay instant.
 */
object LockState {

    /** True while any lock is in force (day limit, app limit, blocked app, bedtime). */
    @Volatile var lockActive: Boolean = false
        private set

    /** True while the lock must not be dismissible (day limit or bedtime). */
    @Volatile var hardLock: Boolean = false
        private set

    /** True during the bedtime window. */
    @Volatile var bedtime: Boolean = false
        private set

    /**
     * The package that ran into a limit and is closed right now.
     *
     * The monitor needs up to one tick to notice that a blocked app came back to the front —
     * long enough for a video to start playing again. The accessibility service sees the
     * window change instantly, so it reads this and bounces the app on sight. The monitor
     * clears it as soon as the limit no longer applies (new day, granted time, released app).
     */
    @Volatile var blockedPackage: String? = null
        private set

    fun update(lockActive: Boolean, hardLock: Boolean, bedtime: Boolean) {
        this.lockActive = lockActive
        this.hardLock = hardLock
        this.bedtime = bedtime
    }

    fun setBlockedPackage(pkg: String?) {
        blockedPackage = pkg
    }
}
