package com.familylink.ios.data

/** Reason the screen is (or is not) locked — drives what the overlay shows. */
sealed class LockDecision {
    object Allowed : LockDecision()
    object Bedtime : LockDecision()
    data class GlobalLimitReached(val usedSeconds: Int, val limitSeconds: Int) : LockDecision()
    data class AppLimitReached(val pkg: String, val usedSeconds: Int, val limitSeconds: Int) : LockDecision()
}

/**
 * Decides, for a given foreground package, whether the device should be locked.
 * Also owns the "count this second of usage" accounting so the rules stay in one place.
 */
class LimitEngine(private val prefs: Prefs) {

    /**
     * Attribute [elapsedSeconds] of foreground time to [pkg] and return the resulting decision.
     * Call this once per sampling tick from the monitor service.
     */
    fun account(pkg: String?, elapsedSeconds: Int): LockDecision {
        // The Aus-Button wins over everything until 23:00.
        if (prefs.limitsDisabled()) return LockDecision.Allowed

        // Bedtime locks the whole device regardless of app category.
        if (prefs.isBedtime()) return LockDecision.Bedtime

        if (pkg == null) return LockDecision.Allowed

        // Never touch our own app, the launcher, the dialer, or system UI.
        if (pkg == OWN_PACKAGE || isExempt(pkg)) return LockDecision.Allowed

        when (prefs.categoryOf(pkg)) {
            AppCategory.PLUS -> {
                // Always allowed, never counts.
                return LockDecision.Allowed
            }
            AppCategory.LIMIT -> {
                if (elapsedSeconds > 0) prefs.addPerAppSeconds(pkg, elapsedSeconds)
                val used = prefs.perAppSeconds(pkg)
                val limit = prefs.limitMinutesOf(pkg) * 60
                if (used >= limit) {
                    return LockDecision.AppLimitReached(pkg, used, limit)
                }
                return LockDecision.Allowed
            }
            AppCategory.STANDARD -> {
                if (elapsedSeconds > 0) prefs.addGlobalSeconds(elapsedSeconds)
                val used = prefs.globalUsedSeconds
                val limit = prefs.globalLimitMinutes * 60
                if (used >= limit) {
                    return LockDecision.GlobalLimitReached(used, limit)
                }
                return LockDecision.Allowed
            }
        }
    }

    /** Re-evaluate without adding time (used for periodic re-checks, e.g. bedtime tick). */
    fun evaluate(pkg: String?): LockDecision = account(pkg, 0)

    fun snapshot(): UsageSnapshot = UsageSnapshot(
        globalUsedSeconds = prefs.globalUsedSeconds,
        globalLimitSeconds = prefs.globalLimitMinutes * 60,
        bedtimeActive = prefs.isBedtime(),
        limitsDisabled = prefs.limitsDisabled(),
        perAppUsedSeconds = prefs.getPerAppSeconds()
    )

    private fun isExempt(pkg: String): Boolean =
        pkg in SYSTEM_EXEMPT || pkg.startsWith("com.android.systemui")

    companion object {
        const val OWN_PACKAGE = "com.familylink.ios"

        // Launcher / phone / settings-launcher surfaces should never be locked out,
        // otherwise the child cannot answer a call or reach the emergency dialer.
        private val SYSTEM_EXEMPT = setOf(
            "com.android.systemui",
            "com.android.launcher",
            "com.android.launcher3",
            "com.google.android.apps.nexuslauncher",
            "com.android.dialer",
            "com.google.android.dialer",
            "com.android.phone",
            "com.android.emergency",
            "com.android.server.telecom"
        )
    }
}
