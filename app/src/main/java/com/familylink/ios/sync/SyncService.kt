package com.familylink.ios.sync

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
import org.json.JSONObject
import kotlin.concurrent.thread

/**
 * Holds the live connection between the two devices.
 *
 *  CHILD:  keeps an SSE stream on the config node open (settings changed by a parent apply
 *          within a second) and pushes its own usage every few seconds.
 *  PARENT: keeps an SSE stream on the child's status node open so the portal shows live usage.
 *
 * Both directions reconnect automatically with a short backoff.
 */
class SyncService : Service() {

    private lateinit var prefs: Prefs
    private lateinit var manager: SyncManager

    @Volatile private var running = false
    private var streamThread: Thread? = null
    private var pushThread: Thread? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs.get(this)
        manager = SyncManager(this)
        startForeground(NOTIF_ID, buildNotification())
        running = true
        startStreamLoop()
        startPushLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_PUSH_NOW) {
            thread(isDaemon = true) {
                if (prefs.isParentDevice) manager.pushConfig() else manager.pushStatus()
            }
        }
        return START_STICKY
    }

    /** Real-time inbound: config for the child, status for the parent. */
    private fun startStreamLoop() {
        streamThread = thread(isDaemon = true, name = "sync-stream") {
            var backoff = 2000L
            while (running) {
                if (!prefs.syncConfigured) { Thread.sleep(5000); continue }
                val client = SyncClient(prefs.syncUrl)
                val path = if (prefs.isChildDevice) {
                    SyncClient.configPath(prefs.familyId)
                } else {
                    SyncClient.statusPath(prefs.familyId)
                }

                client.stream(path, shouldStop = { !running }) { data ->
                    runCatching {
                        if (prefs.isChildDevice) {
                            manager.applyConfig(FamilyConfig.fromJson(data))
                            // Re-evaluate limits immediately with the new rules.
                            com.familylink.ios.service.MonitorService.recheck(this)
                        } else {
                            prefs.cachedChildStatus = data.toString()
                            prefs.lastSyncAt = System.currentTimeMillis()
                        }
                    }
                    backoff = 2000L
                }

                if (!running) break
                Thread.sleep(backoff)
                backoff = (backoff * 2).coerceAtMost(30_000L)
            }
        }
    }

    /** Outbound heartbeat: the child reports usage, the parent re-asserts its config. */
    private fun startPushLoop() {
        pushThread = thread(isDaemon = true, name = "sync-push") {
            while (running) {
                runCatching {
                    if (!prefs.syncConfigured) return@runCatching
                    if (prefs.isChildDevice) manager.pushStatus() else manager.pushConfig()
                }
                Thread.sleep(if (prefs.isChildDevice) CHILD_PUSH_MS else PARENT_PUSH_MS)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running = false
        streamThread?.interrupt()
        pushThread?.interrupt()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Family Link Sync", NotificationManager.IMPORTANCE_MIN)
                .apply { setShowBadge(false) }
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Family Link verbunden")
            .setContentText(if (prefs.isChildDevice) "Synchronisiert mit Eltern-Gerät" else "Synchronisiert mit Kinder-Gerät")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIF_ID = 1002
        private const val CHANNEL_ID = "family_link_sync"
        private const val CHILD_PUSH_MS = 10_000L
        private const val PARENT_PUSH_MS = 30_000L
        const val ACTION_PUSH_NOW = "com.familylink.ios.PUSH_NOW"

        fun start(context: Context) {
            if (!Prefs.get(context).syncConfigured) return
            try {
                val i = Intent(context, SyncService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
                else context.startService(i)
            } catch (_: Throwable) {
            }
        }

        /** Ask the service to push immediately (called after a settings change). */
        fun pushNow(context: Context) {
            if (!Prefs.get(context).syncConfigured) return
            try {
                val i = Intent(context, SyncService::class.java).setAction(ACTION_PUSH_NOW)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
                else context.startService(i)
            } catch (_: Throwable) {
            }
        }
    }
}
