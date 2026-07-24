package com.familylink.ios.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Calendar

/**
 * Single source of truth for all configuration and the rolling daily state.
 * Backed by SharedPreferences so it survives reboots and process death.
 */
class Prefs private constructor(private val sp: SharedPreferences) {

    companion object {
        private const val FILE = "family_link_prefs"

        // config keys
        private const val K_PIN_HASH = "pin_hash"
        private const val K_PIN_SALT = "pin_salt"
        private const val K_GLOBAL_LIMIT_MIN = "global_limit_min"
        private const val K_BEDTIME_START = "bedtime_start_min"   // minutes since midnight
        private const val K_BEDTIME_END = "bedtime_end_min"
        private const val K_BEDTIME_ENABLED = "bedtime_enabled"
        private const val K_CATEGORIES = "categories_json"        // pkg -> {cat,limit}
        private const val K_OFF_UNTIL = "off_until_epoch"         // Aus-Button target time
        private const val K_LAST_PORTAL = "last_portal_epoch"
        private const val K_SETUP_DONE = "setup_done"

        // daily state keys
        private const val K_USAGE_DAY = "usage_day"              // yyyyDDD marker
        private const val K_GLOBAL_USED = "global_used_sec"
        private const val K_PERAPP_USED = "perapp_used_json"

        const val DEFAULT_GLOBAL_LIMIT_MIN = 60
        const val MAX_GLOBAL_LIMIT_MIN = 120

        @Volatile private var instance: Prefs? = null

        fun get(context: Context): Prefs = instance ?: synchronized(this) {
            instance ?: Prefs(
                context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            ).also { instance = it }
        }
    }

    // ---- PIN ---------------------------------------------------------------

    val isPinSet: Boolean get() = sp.contains(K_PIN_HASH)

    fun setPin(pin: String) {
        val salt = System.nanoTime().toString()
        sp.edit()
            .putString(K_PIN_SALT, salt)
            .putString(K_PIN_HASH, hash(pin, salt))
            .apply()
    }

    fun checkPin(pin: String): Boolean {
        val salt = sp.getString(K_PIN_SALT, null) ?: return false
        val stored = sp.getString(K_PIN_HASH, null) ?: return false
        return stored == hash(pin, salt)
    }

    private fun hash(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest((salt + "|" + pin).toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // ---- Setup flag --------------------------------------------------------

    var setupDone: Boolean
        get() = sp.getBoolean(K_SETUP_DONE, false)
        set(v) = sp.edit().putBoolean(K_SETUP_DONE, v).apply()

    // ---- Limits ------------------------------------------------------------

    var globalLimitMinutes: Int
        get() = sp.getInt(K_GLOBAL_LIMIT_MIN, DEFAULT_GLOBAL_LIMIT_MIN)
        set(v) = sp.edit().putInt(K_GLOBAL_LIMIT_MIN, v.coerceIn(0, MAX_GLOBAL_LIMIT_MIN)).apply()

    // ---- Bedtime -----------------------------------------------------------

    var bedtimeEnabled: Boolean
        get() = sp.getBoolean(K_BEDTIME_ENABLED, true)
        set(v) = sp.edit().putBoolean(K_BEDTIME_ENABLED, v).apply()

    /** Minutes since midnight, default 20:00. */
    var bedtimeStartMin: Int
        get() = sp.getInt(K_BEDTIME_START, 20 * 60)
        set(v) = sp.edit().putInt(K_BEDTIME_START, v).apply()

    /** Minutes since midnight, default 06:00. */
    var bedtimeEndMin: Int
        get() = sp.getInt(K_BEDTIME_END, 6 * 60)
        set(v) = sp.edit().putInt(K_BEDTIME_END, v).apply()

    /** True when [nowMinutes] falls inside the (possibly midnight-crossing) bedtime window. */
    fun isBedtime(nowMinutes: Int = minutesSinceMidnight()): Boolean {
        if (!bedtimeEnabled) return false
        val s = bedtimeStartMin
        val e = bedtimeEndMin
        return if (s <= e) nowMinutes in s until e else (nowMinutes >= s || nowMinutes < e)
    }

    // ---- Aus-Button (temporary disable until 23:00) ------------------------

    /** Disable all limits until 23:00 of the current day. */
    fun activateOffButton() {
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // If it is already past 23:00, the off-window is effectively over.
        sp.edit().putLong(K_OFF_UNTIL, c.timeInMillis).apply()
    }

    fun clearOffButton() = sp.edit().putLong(K_OFF_UNTIL, 0).apply()

    val offUntilEpoch: Long get() = sp.getLong(K_OFF_UNTIL, 0)

    fun limitsDisabled(now: Long = System.currentTimeMillis()): Boolean = now < offUntilEpoch

    // ---- Parent portal weekly gate ----------------------------------------

    val lastPortalEpoch: Long get() = sp.getLong(K_LAST_PORTAL, 0)

    fun markPortalOpened() = sp.edit().putLong(K_LAST_PORTAL, System.currentTimeMillis()).apply()

    /**
     * Portal is openable once per week, OR anytime while the Aus-Button window is active
     * (so the parent can always reach the controls on a day they unlocked).
     */
    fun canOpenPortal(now: Long = System.currentTimeMillis()): Boolean {
        if (limitsDisabled(now)) return true
        val week = 7L * 24 * 60 * 60 * 1000
        return now - lastPortalEpoch >= week
    }

    // ---- App categories ----------------------------------------------------

    fun getCategories(): Map<String, Pair<AppCategory, Int>> {
        val raw = sp.getString(K_CATEGORIES, null) ?: return emptyMap()
        val obj = JSONObject(raw)
        val out = HashMap<String, Pair<AppCategory, Int>>()
        obj.keys().forEach { pkg ->
            val o = obj.getJSONObject(pkg)
            val cat = AppCategory.valueOf(o.optString("cat", AppCategory.STANDARD.name))
            val lim = o.optInt("limit", 30)
            out[pkg] = cat to lim
        }
        return out
    }

    fun setCategory(pkg: String, category: AppCategory, limitMinutes: Int = 30) {
        val obj = JSONObject(sp.getString(K_CATEGORIES, "{}") ?: "{}")
        obj.put(pkg, JSONObject().put("cat", category.name).put("limit", limitMinutes))
        sp.edit().putString(K_CATEGORIES, obj.toString()).apply()
    }

    fun categoryOf(pkg: String): AppCategory =
        getCategories()[pkg]?.first ?: AppCategory.STANDARD

    fun limitMinutesOf(pkg: String): Int =
        getCategories()[pkg]?.second ?: 30

    // ---- Daily usage state -------------------------------------------------

    /** Rolls the counters over when the calendar day changes (tracking starts at 00:00). */
    private fun ensureToday() {
        val today = dayMarker()
        if (sp.getInt(K_USAGE_DAY, -1) != today) {
            sp.edit()
                .putInt(K_USAGE_DAY, today)
                .putInt(K_GLOBAL_USED, 0)
                .putString(K_PERAPP_USED, "{}")
                .apply()
        }
    }

    var globalUsedSeconds: Int
        get() { ensureToday(); return sp.getInt(K_GLOBAL_USED, 0) }
        set(v) { ensureToday(); sp.edit().putInt(K_GLOBAL_USED, v.coerceAtLeast(0)).apply() }

    fun addGlobalSeconds(delta: Int) { globalUsedSeconds = globalUsedSeconds + delta }

    fun getPerAppSeconds(): Map<String, Int> {
        ensureToday()
        val obj = JSONObject(sp.getString(K_PERAPP_USED, "{}") ?: "{}")
        val out = HashMap<String, Int>()
        obj.keys().forEach { out[it] = obj.getInt(it) }
        return out
    }

    fun perAppSeconds(pkg: String): Int = getPerAppSeconds()[pkg] ?: 0

    fun addPerAppSeconds(pkg: String, delta: Int) {
        ensureToday()
        val obj = JSONObject(sp.getString(K_PERAPP_USED, "{}") ?: "{}")
        obj.put(pkg, obj.optInt(pkg, 0) + delta)
        sp.edit().putString(K_PERAPP_USED, obj.toString()).apply()
    }

    // ---- helpers -----------------------------------------------------------

    private fun dayMarker(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
    }
}

fun minutesSinceMidnight(): Int {
    val c = Calendar.getInstance()
    return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
}
