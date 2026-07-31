package com.familylink.ios.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeFmt {
    /** "1 Std 5 Min" / "45 Min" style, from seconds. */
    fun hm(seconds: Int): String {
        val m = seconds / 60
        val h = m / 60
        val rm = m % 60
        return when {
            h > 0 -> "$h Std $rm Min"
            else -> "$rm Min"
        }
    }

    /** minutes-since-midnight -> "20:00" */
    fun clock(minutesSinceMidnight: Int): String {
        val h = minutesSinceMidnight / 60
        val m = minutesSinceMidnight % 60
        return "%02d:%02d".format(h, m)
    }

    fun now(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    fun nowLong(): String = SimpleDateFormat("EEEE, d. MMMM", Locale.getDefault()).format(Date())

    /** "Heute 14:32" for something that happened today, "Mo 14:32" for anything older. */
    fun dayTime(epochMillis: Long): String {
        if (epochMillis <= 0) return ""
        val then = Date(epochMillis)
        val sameDay = SimpleDateFormat("yyyyDDD", Locale.getDefault())
            .let { it.format(then) == it.format(Date()) }
        val clock = SimpleDateFormat("HH:mm", Locale.getDefault()).format(then)
        return if (sameDay) "Heute $clock"
        else SimpleDateFormat("EEE", Locale.getDefault()).format(then) + " " + clock
    }
}
