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
        private const val K_SECURE_PIN_HASH = "secure_pin_hash"   // longer parent PIN
        private const val K_SECURE_PIN_SALT = "secure_pin_salt"
        private const val K_GLOBAL_LIMIT_MIN = "global_limit_min"
        private const val K_BEDTIME_START = "bedtime_start_min"   // minutes since midnight
        private const val K_BEDTIME_END = "bedtime_end_min"
        private const val K_BEDTIME_ENABLED = "bedtime_enabled"
        private const val K_CATEGORIES = "categories_json"        // pkg -> {cat,limit}
        private const val K_OFF_UNTIL = "off_until_epoch"         // Aus-Button target time
        private const val K_LAST_PORTAL = "last_portal_epoch"
        private const val K_SETUP_DONE = "setup_done"
        private const val K_BEDTIME_SOUND = "bedtime_sound_enabled"

        // daily state keys (cache of the real UsageStats numbers, written by the service)
        private const val K_USAGE_DAY = "usage_day"              // yyyyDDD marker
        private const val K_GLOBAL_USED = "global_used_sec"
        private const val K_PERAPP_USED = "perapp_used_json"
        private const val K_BLOCKED_TODAY = "blocked_today_json" // pkg -> lastBlocked epoch
        private const val K_BONUS_SEC = "bonus_seconds"          // parent-granted extra time today

        const val DEFAULT_GLOBAL_LIMIT_MIN = 60
        const val MAX_GLOBAL_LIMIT_MIN = 120
        const val MAX_BONUS_MIN = 30
        const val SECURE_PIN_MIN_LEN = 6
        const val OWN_PKG = "com.familylink.ios"

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

    // ---- Secure PIN (longer; required to grant time extensions) ------------

    val isSecurePinSet: Boolean get() = sp.contains(K_SECURE_PIN_HASH)

    fun setSecurePin(pin: String) {
        val salt = System.nanoTime().toString()
        sp.edit()
            .putString(K_SECURE_PIN_SALT, salt)
            .putString(K_SECURE_PIN_HASH, hash(pin, salt))
            .apply()
    }

    fun checkSecurePin(pin: String): Boolean {
        val salt = sp.getString(K_SECURE_PIN_SALT, null) ?: return false
        val stored = sp.getString(K_SECURE_PIN_HASH, null) ?: return false
        return stored == hash(pin, salt)
    }

    // ---- Setup flag --------------------------------------------------------

    var setupDone: Boolean
        get() = sp.getBoolean(K_SETUP_DONE, false)
        set(v) = sp.edit().putBoolean(K_SETUP_DONE, v).apply()

    // ---- Pairing / online sync --------------------------------------------

    /** PARENT (control device) or CHILD (supervised device). */
    var deviceRole: com.familylink.ios.sync.DeviceRole
        get() = runCatching {
            com.familylink.ios.sync.DeviceRole.valueOf(
                sp.getString("device_role", null) ?: "UNSET"
            )
        }.getOrDefault(com.familylink.ios.sync.DeviceRole.UNSET)
        set(v) = sp.edit().putString("device_role", v.name).apply()

    val isChildDevice: Boolean get() = deviceRole == com.familylink.ios.sync.DeviceRole.CHILD
    val isParentDevice: Boolean get() = deviceRole == com.familylink.ios.sync.DeviceRole.PARENT

    /** Shared pairing code — identifies the family node on the server. */
    var familyId: String
        get() = sp.getString("family_id", "") ?: ""
        set(v) = sp.edit().putString("family_id", v).apply()

    /** Realtime-database base URL (set once during setup). */
    var syncUrl: String
        get() = sp.getString("sync_url", "") ?: ""
        set(v) = sp.edit().putString("sync_url", v.trim()).apply()

    val syncConfigured: Boolean get() = syncUrl.isNotBlank() && familyId.isNotBlank()

    /** Timestamp of the last successful sync, for the status display. */
    var lastSyncAt: Long
        get() = sp.getLong("last_sync_at", 0)
        set(v) = sp.edit().putLong("last_sync_at", v).apply()

    /** Last sync failure message, shown in the portal so problems are not invisible. */
    var lastSyncError: String
        get() = sp.getString("last_sync_error", "") ?: ""
        set(v) = sp.edit().putString("last_sync_error", v).apply()

    /** Latest config revision applied from the server (avoids redundant writes). */
    var lastConfigStamp: Long
        get() = sp.getLong("last_config_stamp", 0)
        set(v) = sp.edit().putLong("last_config_stamp", v).apply()

    /** Cached child status JSON, shown in the parent portal. */
    var cachedChildStatus: String
        get() = sp.getString("child_status_json", "") ?: ""
        set(v) = sp.edit().putString("child_status_json", v).apply()

    // ---- Account ----------------------------------------------------------

    var accountEmail: String
        get() = sp.getString("account_email", "") ?: ""
        set(v) = sp.edit().putString("account_email", v.trim().lowercase()).apply()

    val isSignedIn: Boolean get() = accountEmail.isNotBlank() && familyId.isNotBlank()

    /** Stable id for this installation, used for the 3-device limit. */
    var deviceId: String
        get() {
            val existing = sp.getString("device_id", null)
            if (!existing.isNullOrBlank()) return existing
            val fresh = com.familylink.ios.sync.Account.deviceId(null)
            sp.edit().putString("device_id", fresh).apply()
            return fresh
        }
        set(v) = sp.edit().putString("device_id", v).apply()

    // ---- Focus mode (pushed from the parent) ------------------------------

    var focusJson: String
        get() = sp.getString("focus_json", "") ?: ""
        set(v) = sp.edit().putString("focus_json", v).apply()

    fun focusSession(): com.familylink.ios.sync.FocusSession = runCatching {
        if (focusJson.isBlank()) com.familylink.ios.sync.FocusSession.OFF
        else com.familylink.ios.sync.FocusSession.fromJson(JSONObject(focusJson))
    }.getOrDefault(com.familylink.ios.sync.FocusSession.OFF)

    fun setFocusSession(s: com.familylink.ios.sync.FocusSession) {
        focusJson = s.toJson().toString()
    }

    /**
     * Packages currently hidden from the launcher because a focus session excludes them.
     * Persisted on purpose: if the monitor service is killed while a session runs, the set
     * would otherwise be lost and those apps would stay hidden forever.
     */
    var focusHiddenPackages: Set<String>
        get() = sp.getStringSet("focus_hidden", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("focus_hidden", HashSet(v)).apply()

    // ---- Child app list mirrored on the parent device ----------------------

    /** Last app list received from the child (JSON array), so the parent can manage it. */
    var cachedChildApps: String
        get() = sp.getString("child_apps", "") ?: ""
        set(v) = sp.edit().putString("child_apps", v).apply()

    /** Fingerprint of the app list the child last uploaded, so it only re-sends on change. */
    var lastAppListHash: Int
        get() = sp.getInt("app_list_hash", 0)
        set(v) = sp.edit().putInt("app_list_hash", v).apply()

    // ---- Time requests ----------------------------------------------------

    /** Latest request as JSON (child writes, parent decides). */
    var requestJson: String
        get() = sp.getString("request_json", "") ?: ""
        set(v) = sp.edit().putString("request_json", v).apply()

    // ---- Chores -----------------------------------------------------------

    var choresJson: String
        get() = sp.getString("chores_json", "") ?: ""
        set(v) = sp.edit().putString("chores_json", v).apply()

    fun getChores(): List<com.familylink.ios.sync.Chore> =
        com.familylink.ios.sync.Chore.listFromString(choresJson)

    fun setChores(list: List<com.familylink.ios.sync.Chore>) {
        choresJson = com.familylink.ios.sync.Chore.listToJson(list).toString()
    }

    // ---- Appearance -------------------------------------------------------

    /** How the daily budget is measured (system total minus PLUS, or categories only). */
    var usageMode: UsageMode
        get() = runCatching { UsageMode.valueOf(sp.getString("usage_mode", null) ?: "CATEGORIES") }
            .getOrDefault(UsageMode.CATEGORIES)
        set(v) = sp.edit().putString("usage_mode", v.name).apply()

    var themeMode: com.familylink.ios.ui.theme.ThemeMode
        get() = runCatching {
            com.familylink.ios.ui.theme.ThemeMode.valueOf(sp.getString("theme_mode", null) ?: "SYSTEM")
        }.getOrDefault(com.familylink.ios.ui.theme.ThemeMode.SYSTEM)
        set(v) = sp.edit().putString("theme_mode", v.name).apply()

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

    var bedtimeSoundEnabled: Boolean
        get() = sp.getBoolean(K_BEDTIME_SOUND, true)
        set(v) = sp.edit().putBoolean(K_BEDTIME_SOUND, v).apply()

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

    // ---- Temporary system-settings release (granted from the portal) ------

    private val kSettingsUntil = "settings_until_epoch"

    /** Open the device system settings for [minutes] minutes. */
    fun unlockSettings(minutes: Int) {
        sp.edit().putLong(kSettingsUntil, System.currentTimeMillis() + minutes * 60_000L).apply()
    }

    fun lockSettingsNow() = sp.edit().putLong(kSettingsUntil, 0).apply()

    fun settingsUnlocked(now: Long = System.currentTimeMillis()): Boolean =
        now < sp.getLong(kSettingsUntil, 0)

    val settingsUnlockedUntil: Long get() = sp.getLong(kSettingsUntil, 0)

    // The parent portal is openable anytime with the PIN — the weekly restriction was removed.

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

    // ---- Daily usage cache -------------------------------------------------
    // These are a cache of the real UsageStats numbers, refreshed by MonitorService every
    // couple of seconds. The UI reads them so it never has to query UsageStats on the main
    // thread. Everything resets automatically at midnight via the day marker.

    /** Rolls the cache over when the calendar day changes (tracking starts at 00:00). */
    private fun ensureToday() {
        val today = dayMarker()
        if (sp.getInt(K_USAGE_DAY, -1) != today) {
            // Archive the finished day before wiping the counters, so the weekly report has data.
            archiveDay(sp.getInt(K_USAGE_DAY, -1), sp.getInt(K_GLOBAL_USED, 0))
            // Repeating chores become available again each day.
            runCatching {
                val reset = getChores().map {
                    if (it.repeating && !it.isOpen) it.copy(
                        state = com.familylink.ios.sync.Chore.OPEN, claimedAt = 0, approvedAt = 0
                    ) else it
                }
                if (reset.isNotEmpty()) choresJson =
                    com.familylink.ios.sync.Chore.listToJson(reset).toString()
            }
            sp.edit()
                .putInt(K_USAGE_DAY, today)
                .putInt(K_GLOBAL_USED, 0)
                .putString(K_PERAPP_USED, "{}")
                .putString(K_BLOCKED_TODAY, "{}")
                .putInt(K_BONUS_SEC, 0)
                .apply()
        }
    }

    // ---- Bonus time (parent-granted extension, max 30 min/day) -------------

    /** Extra global seconds granted by a parent today (capped at MAX_BONUS_MIN). */
    val bonusSecondsToday: Int
        get() { ensureToday(); return sp.getInt(K_BONUS_SEC, 0) }

    fun remainingBonusMinutes(): Int = (MAX_BONUS_MIN - bonusSecondsToday / 60).coerceAtLeast(0)

    // Absolute setters used when applying a config pushed from the parent device.
    fun setBonusMinutesAbsolute(minutes: Int) {
        ensureToday()
        sp.edit().putInt(K_BONUS_SEC, (minutes * 60).coerceIn(0, MAX_BONUS_MIN * 60)).apply()
    }

    fun setOffUntilEpoch(epoch: Long) = sp.edit().putLong(K_OFF_UNTIL, epoch).apply()

    fun setSettingsUnlockedUntil(epoch: Long) = sp.edit().putLong(kSettingsUntil, epoch).apply()

    /** Replace the whole category map (remote config wins on the child device). */
    fun replaceCategories(map: Map<String, Pair<AppCategory, Int>>) {
        val obj = JSONObject()
        for ((pkg, v) in map) {
            obj.put(pkg, JSONObject().put("cat", v.first.name).put("limit", v.second))
        }
        sp.edit().putString(K_CATEGORIES, obj.toString()).apply()
    }

    /** Add [minutes] of bonus time, capped at MAX_BONUS_MIN/day. Returns the new total minutes. */
    fun addBonusMinutes(minutes: Int): Int {
        ensureToday()
        val current = sp.getInt(K_BONUS_SEC, 0)
        val capped = (current + minutes * 60).coerceAtMost(MAX_BONUS_MIN * 60).coerceAtLeast(0)
        sp.edit().putInt(K_BONUS_SEC, capped).apply()
        return capped / 60
    }

    /** Called by the monitor service with the freshly measured usage numbers. */
    fun cacheUsage(globalUsedSeconds: Int, perAppSeconds: Map<String, Int>) {
        ensureToday()
        val obj = JSONObject()
        for ((pkg, sec) in perAppSeconds) obj.put(pkg, sec)
        sp.edit()
            .putInt(K_GLOBAL_USED, globalUsedSeconds.coerceAtLeast(0))
            .putString(K_PERAPP_USED, obj.toString())
            .apply()
    }

    val globalUsedSeconds: Int
        get() { ensureToday(); return sp.getInt(K_GLOBAL_USED, 0) }

    fun getPerAppSeconds(): Map<String, Int> {
        ensureToday()
        val obj = JSONObject(sp.getString(K_PERAPP_USED, "{}") ?: "{}")
        val out = HashMap<String, Int>()
        obj.keys().forEach { out[it] = obj.getInt(it) }
        return out
    }

    fun perAppSeconds(pkg: String): Int = getPerAppSeconds()[pkg] ?: 0

    // ---- Blocked apps (today) ---------------------------------------------

    /** Record that [pkg] was blocked now (limit reached). Shown in the parent portal. */
    fun recordBlocked(pkg: String) {
        ensureToday()
        val obj = JSONObject(sp.getString(K_BLOCKED_TODAY, "{}") ?: "{}")
        obj.put(pkg, System.currentTimeMillis())
        sp.edit().putString(K_BLOCKED_TODAY, obj.toString()).apply()
    }

    /** Map of package -> last-blocked epoch millis for today. */
    fun getBlockedToday(): Map<String, Long> {
        ensureToday()
        val obj = JSONObject(sp.getString(K_BLOCKED_TODAY, "{}") ?: "{}")
        val out = HashMap<String, Long>()
        obj.keys().forEach { out[it] = obj.getLong(it) }
        return out
    }

    // ---- 7-day history (weekly report) ------------------------------------

    /** Store a finished day's total; keeps the last 7 entries. */
    private fun archiveDay(dayMarker: Int, seconds: Int) {
        if (dayMarker <= 0) return
        val arr = runCatching { org.json.JSONArray(sp.getString("history_json", "[]")) }
            .getOrDefault(org.json.JSONArray())
        val out = org.json.JSONArray()
        // Keep the newest 6 so this day becomes the 7th.
        val start = (arr.length() - 6).coerceAtLeast(0)
        for (i in start until arr.length()) out.put(arr.get(i))
        out.put(org.json.JSONObject().put("d", dayMarker).put("s", seconds))
        sp.edit().putString("history_json", out.toString()).apply()
    }

    /** Weekday label -> seconds, oldest first, including today. */
    fun getWeekHistory(): List<Pair<String, Int>> {
        val names = listOf("So", "Mo", "Di", "Mi", "Do", "Fr", "Sa")
        val arr = runCatching { org.json.JSONArray(sp.getString("history_json", "[]")) }
            .getOrDefault(org.json.JSONArray())
        val out = ArrayList<Pair<String, Int>>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val marker = o.optInt("d")
            val dayOfYear = marker % 1000
            val year = marker / 1000
            val c = Calendar.getInstance().apply {
                set(Calendar.YEAR, year); set(Calendar.DAY_OF_YEAR, dayOfYear)
            }
            out.add(names[c.get(Calendar.DAY_OF_WEEK) - 1] to o.optInt("s"))
        }
        // Append today (live value).
        val todayName = names[Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1]
        out.add(todayName to globalUsedSeconds)
        return out.takeLast(7)
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
