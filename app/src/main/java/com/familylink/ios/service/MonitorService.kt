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
import com.familylink.ios.MainActivity
import com.familylink.ios.R
import com.familylink.ios.data.LimitEngine
import com.familylink.ios.data.LockDecision
import com.familylink.ios.data.Prefs
import com.familylink.ios.lock.LockOverlayManager
import com.familylink.ios.util.BedtimeSound
import com.familylink.ios.util.ForegroundTracker
import com.familylink.ios.util.UsageStatsTracker

/**
 * Always-on foreground service and the heart of enforcement.
 *
 * Every ~1.5s it:
 *  1. reads the *real* usage numbers from the OS (UsageStatsManager),
 *  2. finds the current foreground app (OS usage events, or the accessibility hint),
 *  3. asks [LimitEngine] whether to lock, and shows/hides the overlay accordingly,
 *  4. caches the numbers so the UI can display live usage,
 *  5. records blocked apps and drives the bedtime sound.
 *
 * Because measurement comes from the OS, tracking keeps working even if the service is
 * briefly killed, and it does not depend on the accessibility service being enabled.
 */
class MonitorService : Service() {

    private lateinit var prefs: Prefs
    private lateinit var engine: LimitEngine

    // Do the UsageStats query off the main thread; touch the overlay on the main thread.
    private val workerThread = HandlerThread("monitor-worker")
    private lateinit var worker: Handler
    private val main = Handler(Looper.getMainLooper())

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
        workerThread.start()
        worker = Handler(workerThread.looper)
        worker.post(tickRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_RECHECK) worker.post { tick() }
        return START_STICKY
    }

    private fun tick() {
        val usage = UsageStatsTracker.todayUsageSeconds(this)
        val pkg = ForegroundTracker.currentPackage
            ?: UsageStatsTracker.currentForegroundPackage(this)

        val globalUsed = engine.computeGlobalUsedSeconds(usage)
        prefs.cacheUsage(globalUsed, usage)

        val decision = engine.decide(pkg, usage)

        // Bedtime ambient sound.
        if (decision is LockDecision.Bedtime && prefs.bedtimeSoundEnabled) {
            main.post { BedtimeSound.start(this) }
        } else {
            main.post { BedtimeSound.stop() }
        }

        // Record which app got blocked (for the parent portal list).
        when (decision) {
            is LockDecision.AppLimitReached -> prefs.recordBlocked(decision.pkg)
            is LockDecision.GlobalLimitReached -> if (pkg != null) prefs.recordBlocked(pkg)
            else -> {}
        }

        main.post {
            when (decision) {
                is LockDecision.Allowed ->
                    if (LockOverlayManager.isShowing) LockOverlayManager.hide(this)
                else ->
                    if (LockOverlayManager.isShowing) LockOverlayManager.update(decision)
                    else LockOverlayManager.show(this, decision)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        worker.removeCallbacks(tickRunnable)
        workerThread.quitSafely()
        main.post { BedtimeSound.stop() }
        // Self-heal: ask the system to restart us if a child killed the service.
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
        private const val NOTIF_ID = 1001
        private const val CHANNEL_ID = "family_link_monitor"
        const val ACTION_RECHECK = "com.familylink.ios.RECHECK"

        fun start(context: Context) {
            // Starting a FGS from the background can throw on Android 12+; never let that crash us.
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
