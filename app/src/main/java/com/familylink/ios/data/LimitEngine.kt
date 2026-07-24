package com.familylink.ios.data

/** Reason an app is (or is not) blocked — drives the block list screen. */
sealed class LockDecision {
    object Allowed : LockDecision()
    object Bedtime : LockDecision()
    data class GlobalLimitReached(val usedSeconds: Int, val limitSeconds: Int) : LockDecision()
    data class AppLimitReached(val pkg: String, val usedSeconds: Int, val limitSeconds: Int) : LockDecision()
    /** App is generally blocked (BLOCKED category), independent of time. */
    data class AppBlocked(val pkg: String) : LockDecision()
}

/**
 * Pure decision logic, fed a real usage map (package -> foreground seconds today).
 *
 *  - Global budget = summed usage of all STANDARD apps.
 *  - LIMIT apps    = checked against their own per-app daily limit.
 *  - PLUS apps     = always allowed, never counted.
 *  - BLOCKED apps  = always blocked.
 */
class LimitEngine(private val prefs: Prefs) {

    /** Sum of today's usage across STANDARD-category apps (the shared global budget). */
    fun computeGlobalUsedSeconds(usage: Map<String, Int>): Int {
        val cats = prefs.getCategories()
        var total = 0
        for ((pkg, sec) in usage) {
            if (pkg == OWN_PACKAGE || isForegroundExempt(pkg)) continue
            val cat = cats[pkg]?.first ?: AppCategory.STANDARD
            if (cat == AppCategory.STANDARD) total += sec
        }
        return total
    }

    fun decide(pkg: String?, usage: Map<String, Int>): LockDecision {
        // Aus-Button wins over everything until 23:00.
        if (prefs.limitsDisabled()) return LockDecision.Allowed

        if (pkg == null) return LockDecision.Allowed
        // Never block PLUS apps, even during bedtime — they stay available (req: Plus-Apps frei).
        val category = prefs.categoryOf(pkg)
        if (category == AppCategory.PLUS) return LockDecision.Allowed

        // Bedtime locks everything except PLUS apps.
        if (prefs.isBedtime()) return LockDecision.Bedtime

        return when (category) {
            AppCategory.PLUS -> LockDecision.Allowed
            AppCategory.BLOCKED -> LockDecision.AppBlocked(pkg)

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

    /**
     * Whether [pkg] should never be redirected away from (launcher / phone / settings / our own
     * app). Public so the service can decide whether to raise the block screen.
     */
    fun isForegroundExempt(pkg: String): Boolean =
        pkg == OWN_PACKAGE || pkg in SYSTEM_EXEMPT || pkg.startsWith("com.android.systemui")

    companion object {
        const val OWN_PACKAGE = "com.familylink.ios"

        // Launcher / phone / settings surfaces must stay reachable so the child can use the home
        // screen and the emergency dialer.
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
