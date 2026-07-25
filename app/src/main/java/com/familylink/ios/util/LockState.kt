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

    fun update(lockActive: Boolean, hardLock: Boolean, bedtime: Boolean) {
        this.lockActive = lockActive
        this.hardLock = hardLock
        this.bedtime = bedtime
    }
}
