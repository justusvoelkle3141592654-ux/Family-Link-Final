package com.familylink.ios.data

/** Reason an app is (or is not) blocked — drives the block screen. */
sealed class LockDecision {
    object Allowed : LockDecision()
    object Bedtime : LockDecision()
    data class GlobalLimitReached(val usedSeconds: Int, val limitSeconds: Int) : LockDecision()
    data class AppLimitReached(val pkg: String, val usedSeconds: Int, val limitSeconds: Int) : LockDecision()
    /** App is generally blocked (BLOCKED category), independent of time. */
    data class AppBlocked(val pkg: String) : LockDecision()
    /** System settings are blocked unless temporarily released via the portal. */
    object SettingsBlocked : LockDecision()
}

/**
 * Pure decision logic, fed a real usage map (package -> foreground seconds today).
 *
 *  - Shared quota: STANDARD *and* LIMIT apps both count toward the global daily budget.
 *  - A LIMIT app is blocked when its own per-app limit OR the shared global budget is reached.
 *  - PLUS apps  = allowed during the day, never counted; blocked during bedtime.
 *  - BLOCKED    = always blocked.
 *  - Settings   = blocked by default; only usable while temporarily released from the portal.
 *  - Bedtime    = hard lock on everything except phone/system.
 */
class LimitEngine(private val prefs: Prefs) {

    /** Shared consumed budget: usage of STANDARD + LIMIT apps (PLUS/BLOCKED excluded). */
    fun computeGlobalUsedSeconds(usage: Map<String, Int>): Int {
        val cats = prefs.getCategories()
        var total = 0
        for ((pkg, sec) in usage) {
            if (pkg == OWN_PACKAGE || isForegroundExempt(pkg) || isSettings(pkg)) continue
            val cat = cats[pkg]?.first ?: AppCategory.STANDARD
            if (cat == AppCategory.STANDARD || cat == AppCategory.LIMIT) total += sec
        }
        return total
    }

    private fun globalLimitSeconds() = prefs.globalLimitMinutes * 60 + prefs.bonusSecondsToday

    fun decide(pkg: String?, usage: Map<String, Int>): LockDecision {
        // Aus-Button wins over everything until 23:00.
        if (prefs.limitsDisabled()) return LockDecision.Allowed

        // Bedtime is a HARD lock: it blocks EVERYTHING (PLUS included). The service keeps only
        // phone/system usable and makes it non-dismissible.
        if (prefs.isBedtime()) return LockDecision.Bedtime

        if (pkg == null) return LockDecision.Allowed

        // System settings are locked by default; only a portal-granted window opens them.
        if (isSettings(pkg)) {
            return if (prefs.settingsUnlocked()) LockDecision.Allowed else LockDecision.SettingsBlocked
        }

        val category = prefs.categoryOf(pkg)
        if (category == AppCategory.PLUS) return LockDecision.Allowed

        val globalUsed = computeGlobalUsedSeconds(usage)
        val globalLimit = globalLimitSeconds()

        return when (category) {
            AppCategory.PLUS -> LockDecision.Allowed
            AppCategory.BLOCKED -> LockDecision.AppBlocked(pkg)

            AppCategory.LIMIT -> {
                val own = usage[pkg] ?: 0
                val ownLimit = prefs.limitMinutesOf(pkg) * 60
                when {
                    own >= ownLimit -> LockDecision.AppLimitReached(pkg, own, ownLimit)
                    globalUsed >= globalLimit -> LockDecision.GlobalLimitReached(globalUsed, globalLimit)
                    else -> LockDecision.Allowed
                }
            }

            AppCategory.STANDARD -> {
                if (globalUsed >= globalLimit) LockDecision.GlobalLimitReached(globalUsed, globalLimit)
                else LockDecision.Allowed
            }
        }
    }

    /** Settings-family surfaces that must stay locked (kept in sync with the a11y service). */
    fun isSettings(pkg: String): Boolean =
        pkg == "com.android.settings" ||
            pkg == "com.samsung.android.settings" ||
            pkg.endsWith(".settings") ||
            pkg == "com.android.packageinstaller" ||
            pkg == "com.google.android.packageinstaller" ||
            pkg == "com.android.permissioncontroller"

    /** Usable even during bedtime: phone, system UI, our own screens. */
    fun isAlwaysExempt(pkg: String): Boolean =
        pkg == OWN_PACKAGE || pkg in PHONE_SYSTEM_EXEMPT || pkg.startsWith("com.android.systemui")

    /** Daytime exemptions: the above plus the launcher (settings is NOT exempt). */
    fun isForegroundExempt(pkg: String): Boolean =
        isAlwaysExempt(pkg) || pkg in LAUNCHER_EXEMPT

    companion object {
        const val OWN_PACKAGE = "com.familylink.ios"

        private val PHONE_SYSTEM_EXEMPT = setOf(
            "com.android.systemui",
            "com.android.dialer",
            "com.google.android.dialer",
            "com.android.phone",
            "com.android.emergency",
            "com.android.server.telecom"
        )

        private val LAUNCHER_EXEMPT = setOf(
            "com.android.launcher",
            "com.android.launcher3",
            "com.google.android.apps.nexuslauncher",
            "com.sec.android.app.launcher",
            "com.microsoft.launcher",
            "com.teslacoilsw.launcher"
        )
    }
}
