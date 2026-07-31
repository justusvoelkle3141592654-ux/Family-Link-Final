package com.familylink.ios.util

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.Calendar

/**
 * Real usage measurement backed by Android's [UsageStatsManager].
 *
 * This replaces the previous self-maintained per-second counter, which stopped counting
 * whenever the monitor service was killed. Reading directly from the OS usage events is:
 *  - accurate (the OS records exactly when each app is in the foreground),
 *  - resilient (survives our process being killed),
 *  - and starts from 00:00, exactly as required.
 *
 * Only needs the "Usage access" permission — no accessibility required for measurement.
 */
object UsageStatsTracker {

    private fun midnightMillis(): Long {
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return c.timeInMillis
    }

    private fun manager(context: Context): UsageStatsManager? =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    /**
     * Foreground seconds per package since midnight, aggregated from foreground/background
     * events so the numbers are precise.
     *
     * Only one app can really be in front at a time, so a single "currently open" package is
     * tracked instead of a last-seen timestamp per package. A foreground event for a new
     * package always closes out whatever was open before it — even when the OS never sends the
     * matching MOVE_TO_BACKGROUND for the old one, which happens for example behind our own
     * lock overlay (a SYSTEM_ALERT_WINDOW does not pause the activity underneath it). With a
     * per-package map that old session was simply left running until the app happened to send a
     * background event, so it kept collecting time in parallel with whatever ran on top of it —
     * inflating the "whole phone" total well past what Android's own usage report shows.
     */
    fun todayUsageSeconds(context: Context): Map<String, Int> {
        val usm = manager(context) ?: return emptyMap()
        val start = midnightMillis()
        val now = System.currentTimeMillis()

        val events = usm.queryEvents(start, now)
        val totalsMs = HashMap<String, Long>()
        val e = UsageEvents.Event()

        var openPkg: String? = null
        var openAt = 0L

        fun close(at: Long) {
            val pkg = openPkg ?: return
            if (at > openAt) totalsMs[pkg] = (totalsMs[pkg] ?: 0L) + (at - openAt)
            openPkg = null
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val pkg = e.packageName ?: continue
            when (e.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    close(e.timeStamp)
                    openPkg = pkg
                    openAt = e.timeStamp
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND -> if (pkg == openPkg) close(e.timeStamp)
            }
        }
        // Still in the foreground right now (no closing event yet).
        close(now)

        return totalsMs.mapValues { (it.value / 1000L).toInt() }
    }

    /**
     * Total foreground time of the whole phone today, across every app. This is the number a
     * parent means by "how much has the phone been used" — independent of categories, limits
     * or which apps are allowed.
     */
    fun totalDeviceSecondsToday(context: Context): Int =
        todayUsageSeconds(context)
            .filterKeys { it != "com.familylink.ios" }
            .values.sum()

    /**
     * The package currently in the foreground according to the OS. Works without the
     * accessibility service, so tracking/locking still functions on Usage-access + Overlay alone.
     */
    fun currentForegroundPackage(context: Context): String? {
        val usm = manager(context) ?: return null
        val now = System.currentTimeMillis()
        // Wide window so a long, event-free session in one app still resolves to that app.
        val events = usm.queryEvents(now - 60_000, now)
        var pkg: String? = null
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) pkg = e.packageName
        }
        return pkg
    }
}
