package com.familylink.ios.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.familylink.ios.R
import com.familylink.ios.data.Prefs
import com.familylink.ios.sync.Chore
import com.familylink.ios.sync.SyncManager
import com.familylink.ios.sync.TimeRequest
import kotlin.concurrent.thread

/**
 * Watches the child device on behalf of the parent and raises notifications.
 *
 * This is the only background component the parent app ever runs, and it exists solely because
 * notifications are useless while the app is closed. It starts only when the parent switched
 * notifications on, and stops the moment they switch them off.
 *
 * It never tracks the parent's own phone, never enforces anything and never touches the device
 * admin — it only reads the child's state from the server.
 */
class ParentWatchService : Service() {

    private lateinit var prefs: Prefs
    private lateinit var sync: SyncManager

    @Volatile private var running = false
    private var worker: Thread? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs.get(this)
        sync = SyncManager(this)

        // A parent phone that is not a parent phone, or has notifications off, has no business
        // running this at all.
        if (!prefs.isParentDevice || !prefs.notifyEnabled || !prefs.syncConfigured) {
            stopSelf()
            return
        }

        startForeground(NOTIF_ID, buildOngoingNotification())
        ParentNotifications.ensureChannel(this)
        running = true
        startLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun startLoop() {
        worker = thread(isDaemon = true, name = "parent-watch") {
            while (running) {
                runCatching { pollOnce() }
                var slept = 0L
                while (running && slept < POLL_MS) {
                    Thread.sleep(1000); slept += 1000
                }
            }
        }
    }

    private fun pollOnce() {
        if (!prefs.notifyEnabled || !prefs.syncConfigured) { stopSelf(); return }

        val status = runCatching { sync.fetchChildStatus() }.getOrNull()

        // --- extension request ---
        if (prefs.notifyRequest) {
            val req = runCatching { sync.readRequest() }.getOrNull()
            if (req != null && req.state == TimeRequest.PENDING &&
                req.createdAt > prefs.notifiedRequestAt
            ) {
                prefs.notifiedRequestAt = req.createdAt
                ParentNotifications.timeRequest(this, req.minutes, req.reason)
            }
        }

        // --- finished chores ---
        if (prefs.notifyChore) {
            val chores = runCatching { sync.fetchChoreClaims() }.getOrDefault(emptyList())
            val done = chores.filter { it.state == Chore.DONE }
            val already = prefs.notifiedChoreIds
            for (c in done) {
                if (c.id in already) continue
                ParentNotifications.choreDone(this, c.title, c.rewardMinutes)
            }
            // Keep only ids that are still DONE, so a repeating chore can announce itself again
            // the next time it is completed.
            prefs.notifiedChoreIds = done.map { it.id }.toSet()
        }

        if (status == null) {
            maybeReportOffline()
            return
        }

        val name = status.deviceName.ifBlank { "Das Kinder-Gerät" }
        val today = prefs.todayMarker()

        // --- daily budget used up ---
        if (prefs.notifyLimit && status.limitSeconds > 0 &&
            status.globalUsedSeconds >= status.limitSeconds &&
            prefs.notifiedLimitDay != today
        ) {
            prefs.notifiedLimitDay = today
            ParentNotifications.limitReached(this, name)
        }

        // --- absolute ceiling reached ---
        if (prefs.notifyHardCap && prefs.hardCapEnabled &&
            status.totalDeviceSeconds >= prefs.hardCapMinutes * 60 &&
            prefs.notifiedCapDay != today
        ) {
            prefs.notifiedCapDay = today
            ParentNotifications.hardCapReached(this, name)
        }

        // --- the protection on the child's phone was switched off ---
        //
        // Announced on the edge, not on every poll, and deliberately not behind one of the
        // notify* switches: a parent who turned notifications down still needs to hear that
        // the thing doing the supervising has stopped.
        if (status.guardMissingSince > 0L) {
            if (prefs.notifiedGuardAt != status.guardMissingSince) {
                prefs.notifiedGuardAt = status.guardMissingSince
                ParentNotifications.guardOff(this, name)
            }
        } else if (prefs.notifiedGuardAt != 0L) {
            prefs.notifiedGuardAt = 0L
        }

        maybeReportOffline(status.ageSeconds())
    }

    /** Announce a silent child at most once every few hours, never on every poll. */
    private fun maybeReportOffline(ageSeconds: Int? = null) {
        if (!prefs.notifyOffline) return
        val age = ageSeconds ?: ((System.currentTimeMillis() - prefs.lastSyncAt) / 1000L).toInt()
        if (age < OFFLINE_AFTER_S) return
        val now = System.currentTimeMillis()
        if (now - prefs.notifiedOfflineAt < OFFLINE_REPEAT_MS) return
        prefs.notifiedOfflineAt = now
        ParentNotifications.childOffline(this, age / 60)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running = false
        worker?.interrupt()
        super.onDestroy()
    }

    /**
     * Android requires a visible notification for a background service. Kept at minimum
     * importance so it collapses into the "silent" area rather than sitting in the parent's
     * face — the whole point of the parent app is to stay quiet.
     */
    private fun buildOngoingNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Hintergrund", NotificationManager.IMPORTANCE_MIN)
                .apply { setShowBadge(false) }
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Völkle Link")
            .setContentText("Beobachtet das Kinder-Gerät")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIF_ID = 2000
        private const val CHANNEL_ID = "family_link_parent_watch"
        private const val POLL_MS = 30_000L
        private const val OFFLINE_AFTER_S = 30 * 60
        private const val OFFLINE_REPEAT_MS = 6 * 60 * 60 * 1000L

        /** Start or stop the watcher to match the current setting. Safe to call repeatedly. */
        fun sync(context: Context) {
            val p = Prefs.get(context)
            if (p.isParentDevice && p.notifyEnabled && p.syncConfigured) start(context) else stop(context)
        }

        private fun start(context: Context) {
            try {
                val i = Intent(context, ParentWatchService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
                else context.startService(i)
            } catch (_: Throwable) {
            }
        }

        private fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, ParentWatchService::class.java)) }
        }
    }
}
