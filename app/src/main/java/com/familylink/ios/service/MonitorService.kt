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
import com.familylink.ios.util.BedtimeSound
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
    private var ticksSincePkgRefresh = 0

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
        runCatching { managedPackages = InstalledApps.load(this).map { it.packageName }.toSet() }
    }

    private fun tick() {
        // The guard only ever runs on the supervised (child) device. A parent phone must never
        // lock itself, no matter how the service got started (boot, accessibility, self-heal).
        if (prefs.isParentDevice) {
            LockState.update(lockActive = false, hardLock = false, bedtime = false)
            stopSelf()
            return
        }
        // Never enforce anything until initial setup is finished (so granting permissions and
        // enabling the admin during setup is never interrupted).
        if (!prefs.setupDone) return

        if (ticksSincePkgRefresh++ >= 40) { ticksSincePkgRefresh = 0; refreshManagedPackages() }

        val usage = UsageStatsTracker.todayUsageSeconds(this)
        // UsageStats is authoritative for "what is resumed right now"; the accessibility hint is
        // only a fallback, because a stale hint was blocking freshly-opened PLUS apps.
        val pkg = UsageStatsTracker.currentForegroundPackage(this)
            ?: ForegroundTracker.currentPackage

        val globalUsed = engine.computeGlobalUsedSeconds(usage)
        prefs.cacheUsage(globalUsed, usage)

        // Report upward from here as well (every ~9s). The monitor is the component that
        // always runs on the child and holds the freshest numbers, so the parent no longer
        // depends on SyncService alone to see live usage.
        if (prefs.syncConfigured && ticksSinceStatusPush++ >= 6) {
            ticksSinceStatusPush = 0
            runCatching { syncManager.pushStatus() }
        }

        val decision = engine.decide(pkg, usage)

        val isBedtimeNow = decision is LockDecision.Bedtime
        // Only a single app's own limit may be dismissed; day limit, bedtime and an active
        // focus session are hard locks.
        val hardLock = isBedtimeNow ||
            decision is LockDecision.GlobalLimitReached ||
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

        // Bedtime ambient sound.
        if (isBedtimeNow && prefs.bedtimeSoundEnabled) {
            main.post { BedtimeSound.start(this) }
        } else {
            main.post { BedtimeSound.stop() }
        }

        if (decision is LockDecision.Allowed) return
        if (pkg == null) return
        // Phone / system / our own screens are never blocked (even during bedtime).
        if (engine.isAlwaysExempt(pkg)) return

        val bedtime = isBedtimeNow
        // Bedtime and focus sessions block everything that is not always-exempt.
        val blocksEverything = bedtime || decision is LockDecision.FocusActive
        if (!blocksEverything) {
            when (decision) {
                // Settings is blocked directly (it is not a "managed" launchable app).
                is LockDecision.SettingsBlocked -> { /* fall through to block */ }
                else -> {
                    // Daytime limit blocks: launcher stays free and only real launchable apps count.
                    if (engine.isForegroundExempt(pkg)) return
                    if (pkg !in managedPackages) return
                }
            }
        }
        // Bedtime: block EVERYTHING that is not always-exempt (launcher, PLUS apps, settings, …).

        // Record for the parent portal.
        when (decision) {
            is LockDecision.AppLimitReached -> prefs.recordBlocked(decision.pkg)
            is LockDecision.AppBlocked -> prefs.recordBlocked(decision.pkg)
            is LockDecision.GlobalLimitReached -> prefs.recordBlocked(pkg)
            else -> {}
        }

        // Debounce so we never relaunch in a tight loop (no flicker).
        val now = SystemClock.uptimeMillis()
        if (now - lastBlockLaunchAt < RELAUNCH_COOLDOWN_MS) return
        lastBlockLaunchAt = now

        val (title, detail) = messageFor(decision)
        main.post { BlockActivity.launch(this, title, detail, bedtime, hardLock) }
    }

    private fun messageFor(decision: LockDecision): Pair<String, String> = when (decision) {
        is LockDecision.Bedtime ->
            "Ruhezeit" to "Wieder entsperrt um ${TimeFmt.clock(prefs.bedtimeEndMin)} Uhr."
        is LockDecision.GlobalLimitReached ->
            "Zeitlimit erreicht" to "Genutzt: ${TimeFmt.hm(decision.usedSeconds)} von ${TimeFmt.hm(decision.limitSeconds)}."
        is LockDecision.AppLimitReached ->
            "App-Limit erreicht" to "Genutzt: ${TimeFmt.hm(decision.usedSeconds)} von ${TimeFmt.hm(decision.limitSeconds)}."
        is LockDecision.AppBlocked ->
            "App gesperrt" to "Diese App ist dauerhaft gesperrt."
        is LockDecision.SettingsBlocked ->
            "Einstellungen gesperrt" to "Die Systemeinstellungen sind gesperrt. Freigabe über das Eltern-Portal."
        is LockDecision.FocusActive ->
            "Fokus: ${decision.label}" to "Noch ${TimeFmt.hm(decision.remainingSeconds)} — nur Fokus-Apps sind erlaubt."
        LockDecision.Allowed -> "" to ""
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        worker.removeCallbacks(tickRunnable)
        workerThread.quitSafely()
        main.post { BedtimeSound.stop() }
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
