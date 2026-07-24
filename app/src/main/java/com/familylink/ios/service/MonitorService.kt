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
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import com.familylink.ios.MainActivity
import com.familylink.ios.R
import com.familylink.ios.data.LimitEngine
import com.familylink.ios.data.LockDecision
import com.familylink.ios.data.Prefs
import com.familylink.ios.lock.LockOverlayManager
import com.familylink.ios.util.ForegroundTracker

/**
 * Always-on foreground service. Once per second it attributes elapsed foreground time to the
 * current app (via [LimitEngine]) and shows/hides the lock overlay accordingly.
 *
 * Sampling every second gives the "precise" enforcement the spec asks for: the lock appears
 * within ~1s of a limit being hit.
 */
class MonitorService : Service() {

    private lateinit var prefs: Prefs
    private lateinit var engine: LimitEngine
    private val handler = Handler(Looper.getMainLooper())
    private var lastTickUptime = 0L

    private val ticker = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs.get(this)
        engine = LimitEngine(prefs)
        startForeground(NOTIF_ID, buildNotification())
        lastTickUptime = SystemClock.uptimeMillis()
        handler.post(ticker)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_RECHECK) {
            // Instant re-evaluation requested by the accessibility service on app switch.
            tick(forceZeroElapsed = true)
        }
        return START_STICKY
    }

    private fun tick(forceZeroElapsed: Boolean = false) {
        val now = SystemClock.uptimeMillis()
        val elapsed = if (forceZeroElapsed) 0 else ((now - lastTickUptime) / 1000L).toInt().coerceIn(0, 5)
        lastTickUptime = now

        val pkg = ForegroundTracker.currentPackage
        val decision = engine.account(pkg, elapsed)

        when (decision) {
            is LockDecision.Allowed -> {
                if (LockOverlayManager.isShowing) LockOverlayManager.hide(this)
            }
            else -> {
                if (LockOverlayManager.isShowing) LockOverlayManager.update(decision)
                else LockOverlayManager.show(this, decision)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        // Self-restart: a child killing the service should not disable the guard for long.
        sendBroadcast(Intent(this, BootReceiver::class.java).setAction(BootReceiver.ACTION_RESTART))
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        // minSdk 26 => notification channels always exist.
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
        private const val TICK_MS = 1000L
        private const val NOTIF_ID = 1001
        private const val CHANNEL_ID = "family_link_monitor"
        const val ACTION_RECHECK = "com.familylink.ios.RECHECK"

        fun start(context: Context) {
            val i = Intent(context, MonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
            else context.startService(i)
        }

        fun recheck(context: Context) {
            val i = Intent(context, MonitorService::class.java).setAction(ACTION_RECHECK)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
            else context.startService(i)
        }
    }
}
