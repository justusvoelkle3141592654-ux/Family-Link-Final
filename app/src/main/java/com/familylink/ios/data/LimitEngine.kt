package com.familylink.ios.data

/** Reason an app is (or is not) blocked — drives the block screen. */
sealed class LockDecision {
    object Allowed : LockDecision()
    object Bedtime : LockDecision()
    data class GlobalLimitReached(
        val usedSeconds: Int,
        val limitSeconds: Int,
        /** True when it is the weekly pot that ran out rather than today's budget. */
        val weekly: Boolean = false
    ) : LockDecision()
    data class AppLimitReached(val pkg: String, val usedSeconds: Int, val limitSeconds: Int) : LockDecision()
    /** App is generally blocked (BLOCKED category), independent of time. */
    data class AppBlocked(val pkg: String) : LockDecision()
    /** System settings are blocked unless temporarily released via the portal. */
    object SettingsBlocked : LockDecision()
    /** A parent-started focus session is running; only focus apps are allowed. */
    data class FocusActive(val label: String, val remainingSeconds: Int) : LockDecision()
    /**
     * The absolute daily ceiling across ALL apps is reached. Outranks everything except
     * bedtime and cannot be lifted by bonus time, an extension or the off-button.
     */
    data class HardCapReached(
        val usedSeconds: Int,
        val capSeconds: Int,
        val weekly: Boolean = false
    ) : LockDecision()
    /** The parent locked the device by hand. Stays until they lift it again. */
    data class ManualLock(val reason: String) : LockDecision()
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

    /**
     * Shared consumed budget for the day. PLUS apps never count in either mode.
     *
     *  - SYSTEM_TOTAL: the phone's whole foreground time minus the allowed apps. Only our own
     *    screens and the phone/emergency surfaces are excluded, so unclassified apps still cost
     *    time and nothing slips through.
     *  - CATEGORIES: only apps explicitly marked STANDARD or LIMIT.
     */
    fun computeGlobalUsedSeconds(usage: Map<String, Int>): Int {
        val cats = prefs.getCategories()
        val mode = prefs.usageMode
        var total = 0
        for ((pkg, sec) in usage) {
            // Our own app and the phone must never consume the child's budget.
            if (pkg == OWN_PACKAGE || isAlwaysExempt(pkg)) continue
            val cat = cats[pkg]?.first ?: AppCategory.STANDARD

            // Allowed apps are subtracted in both modes — that is the whole point of PLUS.
            if (cat == AppCategory.PLUS) continue

            when (mode) {
                UsageMode.SYSTEM_TOTAL -> {
                    // Everything else the child did counts, including unclassified apps.
                    // The launcher is skipped so idle home-screen time is not billed.
                    if (isLauncher(pkg)) continue
                    total += sec
                }
                UsageMode.CATEGORIES -> {
                    if (isForegroundExempt(pkg) || isSettings(pkg)) continue
                    if (cat == AppCategory.STANDARD || cat == AppCategory.LIMIT) total += sec
                }
            }
        }
        return total
    }

    /**
     * The whole phone's foreground time today — EVERY app counts, Plus apps and the launcher
     * included. Only our own screens and the phone/emergency surfaces are left out, because
     * those must stay reachable no matter what. This feeds the absolute ceiling.
     */
    fun computeTotalDeviceSeconds(usage: Map<String, Int>): Int {
        var total = 0
        for ((pkg, sec) in usage) {
            // Our own screens, the phone and the emergency dialler never count.
            if (isAlwaysExempt(pkg)) continue
            // Neither does the home screen: sitting on the launcher is not "using an app", and
            // billing it made the ceiling fill up on its own without the child doing anything.
            if (isLauncher(pkg)) continue
            total += sec
        }
        return total
    }

    private fun isLauncher(pkg: String): Boolean = pkg in LAUNCHER_EXEMPT

    private fun globalLimitSeconds() = prefs.globalLimitMinutes * 60 + prefs.bonusSecondsToday

    /**
     * Has the weekly pot run out? Bonus minutes granted today count against it too, otherwise
     * an extension would quietly reopen a week that is already spent.
     */
    private fun weeklyBudgetExhausted(): Pair<Boolean, Pair<Int, Int>> {
        if (prefs.limitScope == LimitScope.DAY) return false to (0 to 0)
        val spent = prefs.weekCountedSeconds()
        val pot = prefs.weeklyLimitMinutes * 60 + prefs.bonusSecondsToday
        return (spent >= pot) to (spent to pot)
    }

    fun decide(pkg: String?, usage: Map<String, Int>): LockDecision {
        // Bedtime is a HARD lock: it blocks EVERYTHING (PLUS included). The service keeps only
        // phone/system usable and makes it non-dismissible. It outranks the off-button.
        // A manual lock is the parent's direct instruction, so nothing overrides it. Only the
        // phone and emergency dialler stay reachable.
        if (prefs.manualLockEnabled) {
            if (pkg == null) return LockDecision.Allowed
            if (isAlwaysExempt(pkg)) return LockDecision.Allowed
            return LockDecision.ManualLock(prefs.manualLockReason)
        }

        if (prefs.isBedtime()) return LockDecision.Bedtime

        // The absolute ceiling across ALL apps. Deliberately checked this early: no bonus
        // minutes, no granted extension, no off-button and no Plus category may lift it.
        // Only the phone and emergency surfaces survive it.
        if (prefs.hardCapEnabled) {
            val totalToday = computeTotalDeviceSeconds(usage)
            val scope = prefs.hardCapScope
            val dayHit = scope != LimitScope.WEEK && totalToday >= prefs.hardCapMinutes * 60
            val weekSpent = prefs.weekTotalSeconds()
            val weekCap = prefs.weeklyHardCapMinutes * 60
            val weekHit = scope != LimitScope.DAY && weekSpent >= weekCap
            if (dayHit || weekHit) {
                if (pkg == null) return LockDecision.Allowed
                if (isAlwaysExempt(pkg)) return LockDecision.Allowed
                // Report the week when that is what ran out, so the child is told the truth
                // about when the phone works again.
                return if (weekHit) LockDecision.HardCapReached(weekSpent, weekCap, weekly = true)
                else LockDecision.HardCapReached(totalToday, prefs.hardCapMinutes * 60)
            }
        }

        // Focus mode: only the explicitly allowed apps stay usable. Either the parent pushed
        // the session, or the child started one on itself to put the phone away.
        val focus = prefs.effectiveFocusSession()
        if (focus.isRunning()) {
            if (pkg == null) return LockDecision.Allowed
            // The home screen MUST stay reachable — otherwise the block screen fires on the
            // launcher itself and the child can never open any of the allowed apps, which made
            // focus mode look completely broken. (Bedtime is different: there it blocks all.)
            if (isForegroundExempt(pkg) || pkg in focus.allowed) return LockDecision.Allowed
            return LockDecision.FocusActive(focus.label, focus.remainingSeconds())
        }

        // Aus-Button wins over the remaining time limits until 23:00.
        if (prefs.limitsDisabled()) return LockDecision.Allowed

        if (pkg == null) return LockDecision.Allowed

        // System settings are locked by default; only a portal-granted window opens them.
        if (isSettings(pkg)) {
            return if (prefs.settingsUnlocked()) LockDecision.Allowed else LockDecision.SettingsBlocked
        }

        val category = prefs.categoryOf(pkg)
        if (category == AppCategory.PLUS) return LockDecision.Allowed

        val globalUsed = computeGlobalUsedSeconds(usage)
        val globalLimit = globalLimitSeconds()
        // The weekly pot is checked for every non-PLUS app, exactly like the daily budget.
        val (weekOut, weekNumbers) = weeklyBudgetExhausted()
        // With DAY the daily budget is the only gate; with WEEK the weekly pot is; with BOTH
        // whichever runs out first wins.
        val dayCounts = prefs.limitScope != LimitScope.WEEK

        if (weekOut && category != AppCategory.BLOCKED) {
            return LockDecision.GlobalLimitReached(weekNumbers.first, weekNumbers.second, weekly = true)
        }

        return when (category) {
            AppCategory.PLUS -> LockDecision.Allowed
            AppCategory.BLOCKED -> LockDecision.AppBlocked(pkg)

            AppCategory.LIMIT -> {
                val own = usage[pkg] ?: 0
                val ownLimit = prefs.limitMinutesOf(pkg) * 60
                when {
                    own >= ownLimit -> LockDecision.AppLimitReached(pkg, own, ownLimit)
                    dayCounts && globalUsed >= globalLimit ->
                        LockDecision.GlobalLimitReached(globalUsed, globalLimit)
                    else -> LockDecision.Allowed
                }
            }

            AppCategory.STANDARD -> {
                if (dayCounts && globalUsed >= globalLimit)
                    LockDecision.GlobalLimitReached(globalUsed, globalLimit)
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

        internal val LAUNCHER_EXEMPT = setOf(
            "com.android.launcher",
            "com.android.launcher3",
            "com.google.android.apps.nexuslauncher",
            "com.sec.android.app.launcher",
            "com.microsoft.launcher",
            "com.teslacoilsw.launcher"
        )
    }
}
