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
     */
    fun todayUsageSeconds(context: Context): Map<String, Int> {
        val usm = manager(context) ?: return emptyMap()
        val start = midnightMillis()
        val now = System.currentTimeMillis()

        val events = usm.queryEvents(start, now)
        val lastForegroundAt = HashMap<String, Long>()
        val totalsMs = HashMap<String, Long>()
        val e = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val pkg = e.packageName ?: continue
            when (e.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> lastForegroundAt[pkg] = e.timeStamp
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    val from = lastForegroundAt.remove(pkg)
                    if (from != null && e.timeStamp > from) {
                        totalsMs[pkg] = (totalsMs[pkg] ?: 0L) + (e.timeStamp - from)
                    }
                }
            }
        }
        // Apps still in the foreground right now (no closing event yet).
        for ((pkg, from) in lastForegroundAt) {
            if (now > from) totalsMs[pkg] = (totalsMs[pkg] ?: 0L) + (now - from)
        }

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
