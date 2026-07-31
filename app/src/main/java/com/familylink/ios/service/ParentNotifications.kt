package com.familylink.ios.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.familylink.ios.MainActivity
import com.familylink.ios.R

/**
 * Notifications for the parent device.
 *
 * The parent app deliberately stays quiet by default — no tracking, no permanent entry in the
 * shade. Turning notifications on is what gives it a reason to run in the background at all,
 * so the switch in the settings controls both.
 *
 * Every notice is de-duplicated in [com.familylink.ios.data.Prefs]: a request is announced once,
 * a finished chore once per chore, and the limit notices once per day. Nothing repeats on the
 * poll interval.
 */
object ParentNotifications {

    private const val CHANNEL_ALERTS = "family_link_parent_alerts"

    /** Fixed ids so a newer notice of the same kind replaces the older one. */
    private const val ID_REQUEST = 2001
    private const val ID_CHORE = 2002
    private const val ID_LIMIT = 2003
    private const val ID_CAP = 2004
    private const val ID_OFFLINE = 2005

    private fun manager(context: Context) =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ALERTS,
            "Meldungen vom Kinder-Gerät",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Bonuszeit-Wünsche, erledigte Aufgaben und erreichte Limits."
            setShowBadge(true)
        }
        manager(context).createNotificationChannel(channel)
    }

    private fun show(context: Context, id: Int, title: String, text: String) {
        // The bell's list is written whether or not a notification is allowed to appear: a
        // parent who switched notifications off should still be able to look up what happened.
        runCatching {
            com.familylink.ios.data.Prefs.get(context).addEvent(
                type = when (id) {
                    ID_REQUEST -> "request"
                    ID_CHORE -> "chore"
                    ID_OFFLINE -> "offline"
                    else -> "limit"
                },
                title = title,
                text = text
            )
        }
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(context, CHANNEL_ALERTS)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching { manager(context).notify(id, n) }
    }

    fun timeRequest(context: Context, minutes: Int, reason: String) = show(
        context, ID_REQUEST,
        "Bonuszeit angefragt",
        "Dein Kind bittet um $minutes Minuten." + if (reason.isNotBlank()) "\n„$reason\"" else ""
    )

    fun choreDone(context: Context, title: String, rewardMinutes: Int) = show(
        context, ID_CHORE,
        "Aufgabe erledigt",
        "„$title\" wurde als erledigt gemeldet — $rewardMinutes Minuten Bonus zum Bestätigen."
    )

    fun limitReached(context: Context, deviceName: String) = show(
        context, ID_LIMIT,
        "Tageslimit erreicht",
        "$deviceName hat das Tageslimit aufgebraucht."
    )

    fun hardCapReached(context: Context, deviceName: String) = show(
        context, ID_CAP,
        "Gesamtlimit erreicht",
        "$deviceName hat das Gesamtlimit erreicht. Das Handy ist für heute gesperrt."
    )

    fun childOffline(context: Context, minutes: Int) = show(
        context, ID_OFFLINE,
        "Keine Verbindung",
        "Das Kinder-Gerät hat sich seit $minutes Minuten nicht gemeldet."
    )

    /** True once the user may actually be shown notifications (Android 13+ asks for it). */
    fun permitted(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true
}
