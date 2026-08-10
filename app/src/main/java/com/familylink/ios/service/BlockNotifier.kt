package com.familylink.ios.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.familylink.ios.BlockActivity
import com.familylink.ios.R

/**
 * The way a lock reaches the screen when the overlay cannot.
 *
 * From Android 10 an app may not start an activity from the background. The overlay permission
 * happens to grant an exemption, so as long as it is held the block screen simply appears — but
 * the moment it is missing, `startActivity` from the monitor is refused *silently*. That was the
 * bug behind "the app just closes and never says why": the app really was being closed, and the
 * screen explaining it never had a chance to start.
 *
 * A notification always gets through. Marked as a full-screen intent, the system either brings
 * the block screen up itself or shows it as a heads-up the child can tap — and either way the
 * reason is on screen instead of the phone silently doing something inexplicable.
 */
object BlockNotifier {

    private const val CHANNEL_ID = "family_link_lock"
    private const val NOTIF_ID = 1002

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // HIGH rather than DEFAULT: a full-screen intent is only honoured on a high-importance
        // channel, and this is the one notification in the app that must not be quiet.
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.lock_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply { setShowBadge(true) }
        nm.createNotificationChannel(channel)
    }

    /**
     * Put the reason on screen.
     *
     * @return true if something was actually shown, so the caller knows whether it may go on to
     *         close the offending app. Closing an app without having said why is the one outcome
     *         this whole class exists to prevent.
     */
    fun show(
        context: Context,
        title: String,
        detail: String,
        bedtime: Boolean = false,
        hardLock: Boolean = true,
        repair: Boolean = false
    ): Boolean = runCatching {
        ensureChannel(context)

        val intent = Intent(context, BlockActivity::class.java).apply {
            putExtra(BlockActivity.EXTRA_TITLE, title)
            putExtra(BlockActivity.EXTRA_DETAIL, detail)
            putExtra(BlockActivity.EXTRA_BEDTIME, bedtime)
            putExtra(BlockActivity.EXTRA_HARD_LOCK, hardLock)
            putExtra(BlockActivity.EXTRA_REPAIR, repair)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
        }
        val pi = PendingIntent.getActivity(
            context, NOTIF_ID, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val n = Notification.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(detail)
            .setStyle(Notification.BigTextStyle().bigText(detail))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setCategory(Notification.CATEGORY_ALARM)
            .setContentIntent(pi)
            // The part that matters: asks the system to bring the screen up itself.
            .setFullScreenIntent(pi, true)
            .setAutoCancel(false)
            .setOngoing(hardLock)
            .build()

        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_ID, n)
        true
    }.getOrDefault(false)

    fun clear(context: Context) {
        runCatching {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .cancel(NOTIF_ID)
        }
    }
}
