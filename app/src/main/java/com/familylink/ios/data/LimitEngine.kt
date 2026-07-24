package com.familylink.ios.data

/** Reason the screen is (or is not) locked — drives what the overlay shows. */
sealed class LockDecision {
    object Allowed : LockDecision()
    object Bedtime : LockDecision()
    data class GlobalLimitReached(val usedSeconds: Int, val limitSeconds: Int) : LockDecision()
    data class AppLimitReached(val pkg: String, val usedSeconds: Int, val limitSeconds: Int) : LockDecision()
}

/**
 * Pure decision logic. It is fed a real usage map (package -> foreground seconds today,
 * from [com.familylink.ios.util.UsageStatsTracker]) and decides whether to lock.
 *
 *  - Global budget  = sum of foreground time of all STANDARD apps today.
 *  - LIMIT apps      = checked against their own per-app daily limit.
 *  - PLUS apps       = always allowed, never counted.
 */
class LimitEngine(private val prefs: Prefs) {

    /** Sum of today's usage across STANDARD-category apps (the shared global budget). */
    fun computeGlobalUsedSeconds(usage: Map<String, Int>): Int {
        val cats = prefs.getCategories()
        var total = 0
        for ((pkg, sec) in usage) {
            if (pkg == OWN_PACKAGE || isExempt(pkg)) continue
            val cat = cats[pkg]?.first ?: AppCategory.STANDARD
            if (cat == AppCategory.STANDARD) total += sec
        }
        return total
    }

    fun decide(pkg: String?, usage: Map<String, Int>): LockDecision {
        // Aus-Button wins over everything until 23:00.
        if (prefs.limitsDisabled()) return LockDecision.Allowed
        // Bedtime locks the whole device.
        if (prefs.isBedtime()) return LockDecision.Bedtime

        if (pkg == null) return LockDecision.Allowed
        if (pkg == OWN_PACKAGE || isExempt(pkg)) return LockDecision.Allowed

        return when (prefs.categoryOf(pkg)) {
            AppCategory.PLUS -> LockDecision.Allowed

            AppCategory.LIMIT -> {
                val used = usage[pkg] ?: 0
                val limit = prefs.limitMinutesOf(pkg) * 60
                if (used >= limit) LockDecision.AppLimitReached(pkg, used, limit)
                else LockDecision.Allowed
            }

            AppCategory.STANDARD -> {
                val used = computeGlobalUsedSeconds(usage)
                val limit = prefs.globalLimitMinutes * 60
                if (used >= limit) LockDecision.GlobalLimitReached(used, limit)
                else LockDecision.Allowed
            }
        }
    }

    private fun isExempt(pkg: String): Boolean =
        pkg in SYSTEM_EXEMPT || pkg.startsWith("com.android.systemui")

    companion object {
        const val OWN_PACKAGE = "com.familylink.ios"

        // Launcher / phone / settings surfaces must never be locked, so the child can always
        // reach the home screen and the emergency dialer.
        private val SYSTEM_EXEMPT = setOf(
            "com.android.systemui",
            "com.android.launcher",
            "com.android.launcher3",
            "com.google.android.apps.nexuslauncher",
            "com.sec.android.app.launcher",
            "com.android.dialer",
            "com.google.android.dialer",
            "com.android.phone",
            "com.android.emergency",
            "com.android.server.telecom",
            "com.android.settings"
        )
    }
}
