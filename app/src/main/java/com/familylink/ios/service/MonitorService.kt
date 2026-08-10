package com.familylink.ios.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import com.familylink.ios.BlockActivity
import com.familylink.ios.MainActivity
import com.familylink.ios.R
import com.familylink.ios.data.InstalledApps
import com.familylink.ios.data.LimitEngine
import com.familylink.ios.data.LockDecision
import com.familylink.ios.data.Prefs
import com.familylink.ios.util.ForegroundTracker
import com.familylink.ios.util.LockState
import com.familylink.ios.util.TimeFmt
import com.familylink.ios.util.UsageStatsTracker

/**
 * Always-on guard. Every ~1.5s it reads real usage from the OS, decides whether the current
 * foreground app is blocked, and — if so — brings up the block list screen (Listen-Ansicht).
 *
 * The block screen is a normal, leavable Activity, not a screen-locking overlay:
 *  - no full-screen lock, the child can always go Home and use PLUS apps,
 *  - single surface, and it does not flicker because we only (re)launch it when a *blocked*
 *    app is actually in the foreground and not more than once per cooldown window.
 */
class MonitorService : Service() {

    private lateinit var prefs: Prefs
    private lateinit var engine: LimitEngine

    private val workerThread = HandlerThread("monitor-worker")
    private lateinit var worker: Handler
    private val main = Handler(Looper.getMainLooper())

    private var lastBlockLaunchAt = 0L
    private var lastSettingsHidden: Boolean? = null
    private var ticksSinceStatusPush = 0
    private val syncManager by lazy { com.familylink.ios.sync.SyncManager(this) }

    // Only real, user-launchable apps are ever blocked. Everything else (keyboards, ad SDKs,
    // Play services, system surfaces) is left alone so it can never appear over a PLUS app.
    @Volatile private var managedPackages: Set<String> = emptySet()
    // The home screens of this device, asked for by intent — never blocked, never suspended.
    @Volatile private var homePackages: Set<String> = emptySet()
    private var ticksSincePkgRefresh = 0

    // Focus mode hides the apps a session does not allow; remember exactly which ones we hid
    // so the same set is revealed again when it ends.
    private var focusHidingActive = false
    private var hiddenForFocus: Set<String> = emptySet()

    // Absolute-ceiling escalation timers (see enforceHardCap).
    private var lastHardCapCountAt = 0L
    private var lastHardCapLockAt = 0L
    private var lastScreenLockAt = 0L
    private var statusBarBlocked: Boolean? = null

    /** Toggle the status bar only when the state actually changes — it is a policy call. */
    private fun setStatusBarBlocked(blocked: Boolean) {
        if (statusBarBlocked == blocked) return
        statusBarBlocked = blocked
        runCatching { com.familylink.ios.admin.DeviceOwner.setStatusBarDisabled(this, blocked) }
    }

    private val tickRunnable = object : Runnable {
        override fun run() {
            tick()
            worker.postDelayed(this, TICK_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs.get(this)
        engine = LimitEngine(prefs)
        startForeground(NOTIF_ID, buildNotification())
        // If a previous run was killed while a focus session was hiding apps, treat the
        // persisted set as "still hiding" so the first tick without a session reveals it again.
        focusHidingActive = prefs.focusHiddenPackages.isNotEmpty()
        refreshManagedPackages()
        workerThread.start()
        worker = Handler(workerThread.looper)
        worker.post(tickRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_RECHECK) worker.post { tick() }
        return START_STICKY
    }

    private fun refreshManagedPackages() {
        runCatching { homePackages = resolveHomePackages() }
        runCatching {
            managedPackages = InstalledApps.load(this).map { it.packageName }.toSet() - homePackages
        }
    }

    /**
     * Every package that can serve as a home screen on THIS phone.
     *
     * The hard-coded launcher list only knows the common ones; a Xiaomi, Huawei or Motorola
     * launcher is not in it. Since a blocked app is now actually suspended, guessing wrong here
     * would leave the child staring at an empty home screen, so the launcher is asked for by
     * intent rather than assumed by name.
     */
    private fun resolveHomePackages(): Set<String> {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.queryIntentActivities(home, 0)
            .mapNotNull { it.activityInfo?.packageName }
            .toSet()
    }

    /**
     * Is there real internet right now?
     *
     * "Validated" rather than merely "connected": a WLAN that leads nowhere, or one whose
     * captive portal was never signed into, is not a connection the phone can report over.
     * Any failure to ask is read as connected, so a surprise here never locks the phone.
     */
    private fun hasWorkingInternet(): Boolean = runCatching {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val net = cm.activeNetwork ?: return@runCatching false
        val caps = cm.getNetworkCapabilities(net) ?: return@runCatching false
        caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }.getOrDefault(true)

    /**
     * May this package be blocked?
     *
     * The cached list is refreshed periodically, so a freshly installed or cloned app is not in
     * it yet. Anything with a launcher entry counts as a real app regardless — that is the test
     * that catches clones, which arrive under a package name we have never categorised.
     * Keyboards, ad SDKs and background services have no launcher entry and stay untouched.
     */
    private fun isBlockableApp(pkg: String): Boolean {
        if (pkg in homePackages) return false
        if (pkg in managedPackages) return true
        val launchable = runCatching { InstalledApps.launchIntent(this, pkg) != null }
            .getOrDefault(false)
        if (launchable) {
            // Remember it so the next tick does not have to ask the package manager again.
            managedPackages = managedPackages + pkg
        }
        return launchable
    }

    private fun tick() {
        // The guard only ever runs on the supervised (child) device. A parent phone must never
        // lock itself, no matter how the service got started (boot, accessibility, self-heal).
        if (prefs.isParentDevice) {
            LockState.update(lockActive = false, hardLock = false, bedtime = false)
            com.familylink.ios.util.LockOverlay.hide(this)
            setStatusBarBlocked(false)
            stopSelf()
            return
        }
        // Never enforce anything until initial setup is finished (so granting permissions and
        // enabling the admin during setup is never interrupted).
        if (!prefs.setupDone) return

        // The half-minute after a restart outranks everything, including a guard that is
        // missing: this is the window someone used to reboot into, and it is now simply dead
        // time. No launcher, no app, no Settings — nothing to do but wait it out, by which
        // point every guard is back up and watching.
        if (prefs.bootLockActive()) {
            enforceBootLock()
            return
        }

        // Someone switched a guard off. Turning off the accessibility service — or "display over
        // other apps", which used to take the overlay away with it — used to be the way to make
        // the locks stop working; now it is the one thing that makes the phone useless until it
        // is switched back on, so there is nothing to gain by it.
        if (!guardsIntact()) {
            enforceGuardLoss(
                UsageStatsTracker.currentForegroundPackage(this) ?: ForegroundTracker.currentPackage
            )
            return
        }

        if (ticksSincePkgRefresh++ >= 10) {
            ticksSincePkgRefresh = 0
            refreshManagedPackages()
            prefs.networkAvailable = hasWorkingInternet()
        }

        // Pay out what a self-started lock has earned so far. Cheap, and doing it on every tick
        // means the bonus is already credited by the time the lock ends.
        runCatching { prefs.settleOwnLockReward() }

        // A running screen lock outranks everything: the display itself goes off and every
        // unlock puts it straight back, until the timer expires on its own.
        if (prefs.screenLockActive()) {
            com.familylink.ios.util.LockOverlay.hide(this)
            val now = SystemClock.uptimeMillis()
            if (now - lastScreenLockAt >= SCREEN_LOCK_REPEAT_MS) {
                lastScreenLockAt = now
                runCatching { com.familylink.ios.util.ScreenLock.lockNow(this) }
            }
            return
        }

        val usage = UsageStatsTracker.todayUsageSeconds(this)
        // UsageStats is authoritative for "what is resumed right now"; the accessibility hint is
        // only a fallback, because a stale hint was blocking freshly-opened PLUS apps.
        val pkg = UsageStatsTracker.currentForegroundPackage(this)
            ?: ForegroundTracker.currentPackage

        val globalUsed = engine.computeGlobalUsedSeconds(usage)
        prefs.cacheUsage(globalUsed, usage)
        // Also cache the whole-device figure: the weekly ceiling is built from the finished
        // days plus today, so today's number has to survive the rollover into the week total.
        prefs.totalDeviceSecondsToday = engine.computeTotalDeviceSeconds(usage)

        // Report upward from here as well (every ~9s). The monitor is the component that
        // always runs on the child and holds the freshest numbers, so the parent no longer
        // depends on SyncService alone to see live usage.
        // ~4.5s cadence: fast enough that the parent portal feels live.
        if (prefs.syncConfigured && ticksSinceStatusPush++ >= 3) {
            ticksSinceStatusPush = 0
            runCatching { syncManager.pushStatus() }
        }

        // ---- the sealed lock, decided by the state and not by what is on top of it ----
        //
        // The overlay used to come and go with the decision for the current app. Opening the
        // phone (always exempt) therefore took it off screen, and from there anything could be
        // reached — a cloned app included. Now it stays up for as long as the state lasts, and
        // only the short window opened by its own phone/portal buttons hides it.
        val sealedReason = engine.sealedReason(usage)
        if (sealedReason != null) {
            if (prefs.lockEscapeAllows(pkg)) {
                com.familylink.ios.util.LockOverlay.hide(this)
            } else {
                prefs.clearLockEscape()
                val bedtimeNow = sealedReason is LockDecision.Bedtime
                LockState.update(lockActive = true, hardLock = true, bedtime = bedtimeNow)
                setStatusBarBlocked(true)
                // The overlay only covers the screen — the app behind it keeps running, and
                // YouTube drops into picture-in-picture and plays on over everything. Suspending
                // it terminates it and takes the PiP window with it.
                if (pkg != null && !engine.isForegroundExempt(pkg) && isBlockableApp(pkg)) {
                    suspendBlocked(pkg, sealedReason)
                    prefs.recordBlocked(pkg)
                    if (sealedReason is LockDecision.HardCapReached) enforceHardCap()
                }
                val (t, d) = messageFor(sealedReason)
                if (com.familylink.ios.util.Permissions.hasOverlay(this)) {
                    showSealedOverlay(t, d, bedtimeNow, sealedReason is LockDecision.OfflineLock)
                } else {
                    // No overlay permission: fall back to the pinned activity.
                    val now = SystemClock.uptimeMillis()
                    if (now - lastBlockLaunchAt >= RELAUNCH_COOLDOWN_MS) {
                        lastBlockLaunchAt = now
                        // Without the overlay the block screen is an Activity. Pinning it is
                        // right for every lock except the offline one — pinning that would
                        // leave no way to reach the connection settings at all.
                        val pin = sealedReason !is LockDecision.OfflineLock
                        main.post { BlockActivity.launch(this, t, d, bedtimeNow, true, pin) }
                    }
                }
            }
            // The connection button on the offline lock leads into the system's internet panel,
            // and that panel lives in the settings app — which is hidden while the phone is
            // locked. Reveal it for exactly as long as the window that button opened lasts, and
            // hide it again the moment the window closes.
            val revealForInternet = sealedReason is LockDecision.OfflineLock &&
                prefs.lockEscapeAllowsAny(com.familylink.ios.admin.DeviceOwner.SETTINGS_PACKAGES)
            if (lastSettingsHidden != !revealForInternet) {
                lastSettingsHidden = !revealForInternet
                runCatching {
                    com.familylink.ios.admin.DeviceOwner.setSettingsHidden(this, !revealForInternet)
                }
            }

            // Everything below is about which single app to block; a sealed device has no such
            // question. Suspensions are still released so nothing stays dead past its lock.
            releaseExpiredSuspensions(usage)
            applyFocusHiding(prefs.effectiveFocusSession().isRunning())
            return
        }

        val decision = engine.decide(pkg, usage)

        val isBedtimeNow = decision is LockDecision.Bedtime
        // Only a single app's own limit may be dismissed; day limit, bedtime, the absolute
        // ceiling and an active focus session are hard locks.
        val hardLock = isBedtimeNow ||
            decision is LockDecision.GlobalLimitReached ||
            decision is LockDecision.HardCapReached ||
            decision is LockDecision.ManualLock ||
            decision is LockDecision.FocusActive
        // Publish state for the accessibility service (multi-window / bypass hardening).
        LockState.update(
            lockActive = decision !is LockDecision.Allowed,
            hardLock = hardLock,
            bedtime = isBedtimeNow
        )

        // Device owner: hide the Settings app outright unless the parent released it.
        // (Falls back to the bounce-and-overlay behaviour when not device owner.)
        val settingsShouldHide = !prefs.settingsUnlocked()
        if (settingsShouldHide != lastSettingsHidden) {
            lastSettingsHidden = settingsShouldHide
            runCatching {
                com.familylink.ios.admin.DeviceOwner.setSettingsHidden(this, settingsShouldHide)
            }
        }

        // Focus mode: take the apps the session does not allow off the launcher entirely, so
        // an allowed-plus app that is not part of this session is not sitting there tempting
        // the child. Restored the moment the session ends.
        //
        // Keyed on the session itself, NOT on the decision: while the child is inside an
        // allowed app the decision is "Allowed", and reading that as "no focus" would reveal
        // every hidden app again on each tick.
        applyFocusHiding(prefs.effectiveFocusSession().isRunning())

        // Release anything whose block no longer applies (new day, extension granted, focus
        // over, ceiling reset). Without this a suspended app would stay dead for good.
        releaseExpiredSuspensions(usage)

        // Whatever happens below, the overlay must disappear the moment nothing seals the
        // device any more — otherwise it would outlive the lock that raised it.
        if (!engine.sealsDevice(decision)) {
            com.familylink.ios.util.LockOverlay.hide(this)
            setStatusBarBlocked(false)
        }

        if (decision is LockDecision.Allowed) return
        if (pkg == null) return
        // Phone / system / our own screens are never blocked (even during bedtime).
        if (engine.isAlwaysExempt(pkg)) return

        val bedtime = isBedtimeNow
        // Bedtime and the absolute ceiling block literally everything, launcher included — at
        // that point the phone is simply done. A focus session is different: it must leave the
        // home screen usable, otherwise the child can never reach the apps the session allows.
        val blocksEverything = bedtime ||
            decision is LockDecision.HardCapReached ||
            decision is LockDecision.ManualLock
        if (!blocksEverything) {
            when (decision) {
                // Settings is blocked directly (it is not a "managed" launchable app).
                is LockDecision.SettingsBlocked -> { /* fall through to block */ }
                // Once the day's budget is gone, "we have never seen this package" is not a
                // reason to let it run. A cloned app arrives under a name no category knows and
                // its minutes never reach the counter, so the only honest answer is to block it
                // with everything else. Home screen and the exempt list are still spared.
                is LockDecision.GlobalLimitReached -> {
                    if (engine.isForegroundExempt(pkg)) return
                    if (pkg in homePackages) return
                }
                else -> {
                    // Other daytime blocks: launcher stays free and only real launchable apps
                    // count. "Launchable" is checked live as well as from the cached list —
                    // a cloned app appears under a package we have never seen, and skipping
                    // everything unknown let exactly those through.
                    if (engine.isForegroundExempt(pkg)) return
                    if (!isBlockableApp(pkg)) return
                }
            }
        }
        // Bedtime: block EVERYTHING that is not always-exempt (launcher, PLUS apps, settings, …).

        // Record for the parent portal.
        when (decision) {
            is LockDecision.AppLimitReached -> prefs.recordBlocked(decision.pkg)
            is LockDecision.AppBlocked -> prefs.recordBlocked(decision.pkg)
            is LockDecision.GlobalLimitReached -> prefs.recordBlocked(pkg)
            is LockDecision.OfflineLock -> prefs.recordBlocked(pkg)
            is LockDecision.HardCapReached -> {
                prefs.recordBlocked(pkg)
                // Escalate ONLY when a real app was opened. The home screen is shown after
                // every screen unlock, so counting it as an "attempt" made the counter climb
                // to the threshold on its own within seconds and locked the phone in a loop.
                if (!engine.isForegroundExempt(pkg) && pkg in managedPackages) enforceHardCap()
            }
            else -> {}
        }

        // Say why FIRST, close afterwards.
        //
        // The other way round is what made the app look broken: the app was closed, the screen
        // meant to explain it was refused in the background, and all the child saw was their app
        // vanishing for no stated reason. Nothing is closed now unless the reason is on screen —
        // suspending is exempt because a suspended app shows Android's own "app is paused"
        // dialogue, which is an explanation in itself.
        val (title, detail) = messageFor(decision)
        val shown = if (hardLock) {
            // A hard lock — the day's budget gone, the ceiling hit — goes on the overlay like
            // every other one. It used to be the single exception, shown through the block
            // activity alone, and that is why "the limit is reached" was the one case where
            // nothing appeared: from Android 10 a service may not start an activity in the
            // background, so the screen meant to explain it was refused without a word.
            raiseLock(title, detail, bedtime, sealedLock = false)
        } else {
            val now = SystemClock.uptimeMillis()
            if (now - lastBlockLaunchAt < RELAUNCH_COOLDOWN_MS) return
            lastBlockLaunchAt = now
            // A single app's limit stays a dismissible screen: the rest of the phone still works
            // and an overlay would say otherwise.
            main.post {
                BlockActivity.launch(this, title, detail, bedtime, hardLock, sealed = false)
            }
            if (com.familylink.ios.util.Permissions.hasOverlay(this)) true
            else BlockNotifier.show(this, title, detail, bedtime, hardLock)
        }

        // Raising the block screen only puts a window on top; the app behind keeps running, and
        // YouTube in particular drops into picture-in-picture and carries on playing over
        // everything. Suspending the package terminates it and takes the PiP window with it.
        suspendBlocked(pkg, decision, mayGoHome = shown)
    }

    /**
     * Seal the phone for the first moments after a restart.
     *
     * Nothing is exempt here on purpose — not Settings, not the launcher. The point of the
     * window is that there is no window: by the time it lifts, the services are up, the
     * policies are re-applied and a missing permission has already been noticed.
     */
    private fun enforceBootLock() {
        LockState.update(lockActive = true, hardLock = true, bedtime = false)
        setStatusBarBlocked(true)
        if (lastSettingsHidden != true) {
            lastSettingsHidden = true
            runCatching { com.familylink.ios.admin.DeviceOwner.setSettingsHidden(this, true) }
        }
        val left = prefs.bootLockRemainingSeconds()
        val title = "Kindersicherung startet"
        val detail = "Das Handy ist noch $left Sekunden gesperrt, während der Schutz hochfährt."
        raiseLock(title, detail, bedtime = false, sealedLock = true)
    }

    /**
     * Put a lock on screen by whichever route this phone actually allows.
     *
     * With the overlay permission the overlay is both the strongest and the quietest option.
     * Without it, `startActivity` from here is refused without a word from Android 10 on, so the
     * only thing that still reaches the child is a notification — marked full-screen, which asks
     * the system to bring the block screen up on our behalf.
     *
     * @return true if something was actually shown.
     */
    private fun raiseLock(
        title: String,
        detail: String,
        bedtime: Boolean,
        sealedLock: Boolean,
        repair: Boolean = false
    ): Boolean {
        // The permission check alone is not proof: building the window can still be refused,
        // and when it is, this lock has to travel by another route or nobody sees it at all.
        if (com.familylink.ios.util.Permissions.hasOverlay(this) &&
            !com.familylink.ios.util.LockOverlay.lastShowFailed
        ) {
            showSealedOverlay(title, detail, bedtime, repair = repair)
            return true
        }
        val now = SystemClock.uptimeMillis()
        if (now - lastBlockLaunchAt < BOOT_LOCK_RELAUNCH_MS) return true
        lastBlockLaunchAt = now
        // Both: the activity start works whenever the app happens to be allowed to make it, and
        // the notification is what carries the reason when it is not.
        main.post {
            BlockActivity.launch(
                this, title, detail,
                bedtime = bedtime, hardLock = true, sealed = sealedLock, repair = repair
            )
        }
        return BlockNotifier.show(
            this, title, detail, bedtime = bedtime, hardLock = true, repair = repair
        )
    }

    /**
     * Are the permissions the enforcement rests on still granted?
     *
     * The device admin is not in here: it only blocks uninstalling, and it is legitimately
     * optional. Neither is the overlay permission, and that one is a lesson rather than an
     * oversight — sealing the phone over a missing overlay meant sealing it with no way left to
     * say so, because from Android 10 an activity cannot be started from the background without
     * exactly that permission. The phone simply went dead and silent. A missing overlay is now
     * handled where it belongs: the lock still happens, it just arrives as a full-screen
     * notification instead.
     */
    private fun guardsIntact(): Boolean =
        com.familylink.ios.util.Permissions.accessibilityEnabled(this) &&
            com.familylink.ios.util.Permissions.hasUsageAccess(this)

    /**
     * React to a guard being switched off: seal the phone until it is granted again.
     *
     * Deliberately the same treatment as a hard lock rather than a warning — a warning would be
     * the reward for switching it off. The one way forward is the button on the lock screen,
     * which opens the page that grants the missing permission and nothing else: the window it
     * opens covers the settings app alone, and leaving it for anything else cancels the window
     * on the spot and puts this screen straight back.
     */
    private fun enforceGuardLoss(pkg: String?) {
        LockState.update(lockActive = true, hardLock = true, bedtime = false)
        // Settings has to be reachable — it is where the repair happens — but only while the
        // lock screen's own button is holding the window open.
        val repairing = prefs.lockEscapeAllows(pkg)
        if (lastSettingsHidden != !repairing) {
            lastSettingsHidden = !repairing
            runCatching {
                com.familylink.ios.admin.DeviceOwner.setSettingsHidden(this, !repairing)
            }
        }
        if (repairing) {
            com.familylink.ios.util.LockOverlay.hide(this)
            return
        }
        prefs.clearLockEscape()

        val missing = com.familylink.ios.util.Permissions.firstMissing(this)
        val title = "Schutz ausgeschaltet"
        val detail = "Die Kindersicherung braucht ${missing?.label ?: "ihre Berechtigungen"}. " +
            "Tippe unten auf „Berechtigung erteilen“ — vorher bleibt das Handy gesperrt."
        // Not pinned: pinning would make Settings unreachable and the phone unrecoverable.
        raiseLock(title, detail, bedtime = false, sealedLock = false, repair = true)
    }

    /** Put the non-dismissible overlay on screen (or leave it there if it already matches). */
    private fun showSealedOverlay(
        title: String,
        detail: String,
        bedtime: Boolean,
        offline: Boolean = false,
        repair: Boolean = false
    ) {
        // The shade would otherwise slide down over the overlay and hand the child quick
        // settings. Only possible as device owner; without it the overlay still covers the
        // screen, the shade just remains reachable.
        setStatusBarBlocked(true)
        val key = "$title|$detail|$bedtime|$offline|$repair"
        com.familylink.ios.util.LockOverlay.show(this, key = key) {
            com.familylink.ios.ui.screens.LockOverlayContent(
                title = title,
                detail = detail,
                bedtime = bedtime,
                offline = offline,
                repair = repair,
                onOpenPortal = { com.familylink.ios.ui.screens.openParentPortal(this) }
            )
        }
    }

    /**
     * Suspend the package that just got blocked, so it stops running rather than merely being
     * covered. Only real, launchable apps — never the launcher, the phone or our own screens.
     */
    private fun suspendBlocked(pkg: String, decision: LockDecision, mayGoHome: Boolean = true) {
        if (decision is LockDecision.SettingsBlocked) return   // settings is hidden, not suspended
        if (engine.isForegroundExempt(pkg)) return
        if (pkg in homePackages) return
        // Live check, not just the cached list: a cloned app carries a package name we have
        // never categorised, and testing against the cache alone let exactly those keep running.
        // Once the budget itself is gone that check is dropped entirely — an app nobody can
        // identify is exactly the one that must not survive the limit.
        val budgetGone = decision is LockDecision.GlobalLimitReached ||
            decision is LockDecision.HardCapReached
        if (!budgetGone && !isBlockableApp(pkg)) return
        if (pkg in prefs.suspendedPackages) return

        val done = runCatching {
            com.familylink.ios.admin.DeviceOwner.setPackagesSuspended(this, listOf(pkg), true)
        }.getOrDefault(emptySet())
        if (done.isNotEmpty()) {
            prefs.suspendedPackages = prefs.suspendedPackages + done
            return
        }
        // Not device owner, so the app cannot be suspended — but it must still stop being on
        // screen right now. Going Home leaves it immediately; the overlay or block screen then
        // lands on the launcher instead of on top of a still-running app.
        //
        // Only when the reason is actually on screen. Sending the child Home out of an app with
        // nothing to explain it is worse than letting the app run one more tick.
        if (mayGoHome) runCatching { AppAccessibilityService.instance?.goHome() }
    }

    /** Un-suspend every package the engine would now allow again. */
    private fun releaseExpiredSuspensions(usage: Map<String, Int>) {
        val suspended = prefs.suspendedPackages
        if (suspended.isEmpty()) return
        val free = suspended.filter { engine.decide(it, usage) is LockDecision.Allowed }
        if (free.isEmpty()) return
        runCatching { com.familylink.ios.admin.DeviceOwner.setPackagesSuspended(this, free, false) }
        prefs.suspendedPackages = suspended - free.toSet()
    }

    /**
     * Escalation for the absolute daily ceiling.
     *
     * Reaching it shows the block screen like any other lock. Ignoring it and opening something
     * anyway locks the display. The first attempts get a grace window so a single accidental tap
     * does not lock the phone over and over; from [Prefs.HARDCAP_LOCK_ALWAYS_FROM] attempts on
     * the display locks on every further attempt — at that point the child is clearly trying to
     * sit the ceiling out.
     *
     * The phone and emergency dialler are never affected: the engine returns Allowed for those
     * before this code is ever reached.
     */
    private fun enforceHardCap() {
        val now = SystemClock.uptimeMillis()

        // One "attempt" per window, so a 1.5s tick loop does not inflate the counter.
        if (now - lastHardCapCountAt < HARDCAP_ATTEMPT_MS) return
        lastHardCapCountAt = now
        val hits = prefs.recordHardCapHit()

        // The first time the ceiling is hit, the block screen alone is the answer — the child
        // has not ignored anything yet. Locking the display starts with the second attempt.
        if (hits < 2) return

        val persistent = hits >= Prefs.HARDCAP_LOCK_ALWAYS_FROM
        val cooldown = if (persistent) HARDCAP_LOCK_PERSISTENT_MS else HARDCAP_LOCK_GRACE_MS
        if (now - lastHardCapLockAt < cooldown) return
        lastHardCapLockAt = now
        runCatching { com.familylink.ios.util.ScreenLock.lockNow(this) }
    }

    /**
     * Hide every managed app that the running focus session does not allow, and put them all
     * back when it ends. Only does anything as device owner; without that the focus block
     * screen remains the enforcement, as before.
     */
    private fun applyFocusHiding(focusRunning: Boolean) {
        if (focusRunning == focusHidingActive) return
        focusHidingActive = focusRunning

        if (!focusRunning) {
            // Read the set back from disk, not from memory: if we were killed mid-session this
            // is the only record of what has to be revealed again.
            val restore = prefs.focusHiddenPackages.toList()
            hiddenForFocus = emptySet()
            prefs.focusHiddenPackages = emptySet()
            if (restore.isNotEmpty()) {
                runCatching { com.familylink.ios.admin.DeviceOwner.setAppsHidden(this, restore, false) }
            }
            return
        }

        val allowed = prefs.effectiveFocusSession().allowed.toSet()
        val toHide = managedPackages.filterNot { it in allowed }
        hiddenForFocus = runCatching {
            com.familylink.ios.admin.DeviceOwner.setAppsHidden(this, toHide, true)
        }.getOrDefault(emptySet())
        // Keep anything an earlier, killed session had hidden in the set as well.
        prefs.focusHiddenPackages = prefs.focusHiddenPackages + hiddenForFocus
    }

    private fun messageFor(decision: LockDecision): Pair<String, String> = when (decision) {
        is LockDecision.Bedtime ->
            "Ruhezeit" to "Wieder entsperrt um ${TimeFmt.clock(prefs.bedtimeEndMin)} Uhr."
        is LockDecision.SchoolTime ->
            "Schulzeit" to
                "Bis ${TimeFmt.clock(decision.endMinute)} Uhr sind nur die zugelassenen Apps erlaubt."
        is LockDecision.OfflineLock ->
            "Keine Verbindung" to (
                "Dieses Handy hat sich seit ${TimeFmt.hm(decision.offlineSeconds)} nicht bei " +
                    "deinen Eltern gemeldet. Schalte WLAN oder mobile Daten ein — dann geht es " +
                    "von selbst weiter."
                )
        is LockDecision.GlobalLimitReached ->
            (if (decision.weekly) "Wochenlimit erreicht" else "Zeitlimit erreicht") to
                (if (decision.weekly)
                    "Diese Woche genutzt: ${TimeFmt.hm(decision.usedSeconds)} von " +
                        "${TimeFmt.hm(decision.limitSeconds)}. Am Montag gibt es wieder Zeit."
                else "Genutzt: ${TimeFmt.hm(decision.usedSeconds)} von ${TimeFmt.hm(decision.limitSeconds)}.")
        is LockDecision.AppLimitReached ->
            "App-Limit erreicht" to "Genutzt: ${TimeFmt.hm(decision.usedSeconds)} von ${TimeFmt.hm(decision.limitSeconds)}."
        is LockDecision.AppBlocked ->
            "App gesperrt" to "Diese App ist dauerhaft gesperrt."
        is LockDecision.SettingsBlocked ->
            "Einstellungen gesperrt" to "Die Systemeinstellungen sind gesperrt. Freigabe über das Eltern-Portal."
        is LockDecision.FocusActive ->
            "Fokus: ${decision.label}" to "Noch ${TimeFmt.hm(decision.remainingSeconds)} — nur Fokus-Apps sind erlaubt."
        is LockDecision.ManualLock ->
            "Gesperrt" to (
                if (decision.reason.isNotBlank()) decision.reason
                else "Deine Eltern haben das Handy gesperrt."
            )
        is LockDecision.HardCapReached ->
            (if (decision.weekly) "Wochen-Gesamtlimit erreicht" else "Gesamtlimit erreicht") to
                (if (decision.weekly)
                    "Das Handy wurde diese Woche ${TimeFmt.hm(decision.usedSeconds)} benutzt — " +
                        "das Maximum sind ${TimeFmt.hm(decision.capSeconds)}. Nächste Woche geht es weiter."
                else
                    "Das Handy wurde heute ${TimeFmt.hm(decision.usedSeconds)} benutzt — das Maximum " +
                        "sind ${TimeFmt.hm(decision.capSeconds)}. Morgen geht es weiter.")
        LockDecision.Allowed -> "" to ""
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        com.familylink.ios.util.LockOverlay.hide(this)
        setStatusBarBlocked(false)
        worker.removeCallbacks(tickRunnable)
        workerThread.quitSafely()
        sendBroadcast(Intent(this, BootReceiver::class.java).setAction(BootReceiver.ACTION_RESTART))
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.monitor_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }
        nm.createNotificationChannel(channel)

        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.monitor_notification_title))
            .setContentText(getString(R.string.monitor_notification_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }

    companion object {
        private const val TICK_MS = 1500L
        private const val RELAUNCH_COOLDOWN_MS = 2500L
        /**
         * How fast the block screen is put back while the phone is sealed and the overlay is
         * unavailable. Far tighter than the normal cooldown: this is the only thing holding the
         * lock, so a leisurely relaunch would be the gap itself.
         */
        private const val BOOT_LOCK_RELAUNCH_MS = 700L
        /** One counted attempt per this window, so ticks do not inflate the counter. */
        private const val HARDCAP_ATTEMPT_MS = 15_000L
        /** Grace between screen locks while the child is still under the attempt threshold. */
        private const val HARDCAP_LOCK_GRACE_MS = 120_000L
        /** Once the threshold is passed, lock again almost immediately on every attempt. */
        private const val HARDCAP_LOCK_PERSISTENT_MS = 5_000L
        /** How often the display is re-locked while a timed screen lock runs. */
        private const val SCREEN_LOCK_REPEAT_MS = 3_000L
        private const val NOTIF_ID = 1001
        private const val CHANNEL_ID = "family_link_monitor"
        const val ACTION_RECHECK = "com.familylink.ios.RECHECK"

        fun start(context: Context) {
            // Guard at the call site too, so a parent device never even spins the service up.
            if (Prefs.get(context).isParentDevice) return
            try {
                val i = Intent(context, MonitorService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
                else context.startService(i)
            } catch (_: Throwable) {
            }
        }

        fun recheck(context: Context) {
            try {
                val i = Intent(context, MonitorService::class.java).setAction(ACTION_RECHECK)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
                else context.startService(i)
            } catch (_: Throwable) {
            }
        }
    }
}
