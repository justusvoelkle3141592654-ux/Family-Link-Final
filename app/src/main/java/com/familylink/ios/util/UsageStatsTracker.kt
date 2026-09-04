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
    // ---- incremental scan ---------------------------------------------------
    //
    // Re-reading every event since midnight on every tick is fine at one tick per 1.5s and
    // hopeless at four per second — and four per second is what "the app closes the moment the
    // time is up" actually requires. So the scan is kept: closed sessions are added up once,
    // and each later call reads only the events that arrived since.
    //
    // Only the open sessions are recomputed each time, because their length depends on the
    // clock rather than on any event. That is the part that has to be exact.

    private val closedMs = HashMap<String, Long>()
    private val openSince = HashMap<String, Long>()
    private var scannedTo = 0L
    private var scannedDay = 0L

    /**
     * Foreground seconds per package since midnight, aggregated from foreground/background
     * events so the numbers are precise.
     *
     * Safe to call several times a second: the events are only read once each.
     */
    @Synchronized
    fun todayUsageSeconds(context: Context): Map<String, Int> {
        val usm = manager(context) ?: return emptyMap()
        val start = midnightMillis()
        val now = System.currentTimeMillis()

        // A new day, or the first call: start over. Anything else would carry yesterday's
        // minutes into today, which is the one error a day limit must never make.
        if (scannedDay != start) {
            closedMs.clear()
            openSince.clear()
            scannedDay = start
            scannedTo = start
        }

        if (now > scannedTo) {
            // One millisecond of overlap rather than none: an event exactly on the boundary
            // would otherwise fall between two scans and its time would simply vanish.
            val events = usm.queryEvents((scannedTo - 1).coerceAtLeast(start), now)
            val e = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                if (e.timeStamp < scannedTo) continue
                val pkg = e.packageName ?: continue
                when (e.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> openSince[pkg] = e.timeStamp
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                        val from = openSince.remove(pkg)
                        if (from != null && e.timeStamp > from) {
                            closedMs[pkg] = (closedMs[pkg] ?: 0L) + (e.timeStamp - from)
                        }
                    }
                }
            }
            scannedTo = now
        }

        val totalsMs = HashMap<String, Long>(closedMs)
        // Apps still in the foreground right now (no closing event yet).
        for ((pkg, from) in openSince) {
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
