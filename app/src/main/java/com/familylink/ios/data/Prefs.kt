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
        private const val K_SHARED_PIN_HASH = "shared_pin_hash"   // the family's PIN
        private const val K_SHARED_PIN_SALT = "shared_pin_salt"
        private const val K_OFFLINE_LOCK = "offline_lock_enabled"
        private const val K_OFFLINE_LOCK_MIN = "offline_lock_minutes"
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

        // daily state keys (cache of the real UsageStats numbers, written by the service)
        private const val K_USAGE_DAY = "usage_day"              // yyyyDDD marker
        private const val K_GLOBAL_USED = "global_used_sec"
        private const val K_PERAPP_USED = "perapp_used_json"
        private const val K_BLOCKED_TODAY = "blocked_today_json" // pkg -> lastBlocked epoch
        private const val K_BONUS_SEC = "bonus_seconds"          // parent-granted extra time today
        private const val K_MANUAL_LOCK = "manual_lock"
        private const val K_MANUAL_LOCK_WHY = "manual_lock_reason"
        private const val K_HARDCAP_ON = "hardcap_on"
        private const val K_HARDCAP_MIN = "hardcap_minutes"
        private const val K_HARDCAP_HITS = "hardcap_hits"        // ignored-ceiling attempts today
        private const val K_TOTAL_USED = "total_used_sec"        // whole-device seconds today
        private const val K_WEEK_MARKER = "week_marker"
        private const val K_WEEK_COUNTED = "week_counted_sec"    // finished days this week
        private const val K_WEEK_TOTAL = "week_total_sec"
        // streak: days in a row inside the daily budget
        private const val K_STREAK_ON = "streak_enabled"

        // The child's allowance for long self-started focus sessions, per calendar week.
        private const val K_OWN_LOCK_WEEK = "own_lock_week"
        private const val K_FOCUS_USED_60 = "focus_used_60"
        private const val K_FOCUS_USED_120 = "focus_used_120"

        // The reward for keeping a self-started lock running: what is owed, and what has
        // already been paid out today.
        private const val K_OWN_LOCK_REWARD_FROM = "own_lock_reward_from"
        private const val K_OWN_LOCK_REWARD_TO = "own_lock_reward_to"
        private const val K_OWN_LOCK_EARNED_DAY = "own_lock_earned_day"
        private const val K_OWN_LOCK_EARNED_MIN = "own_lock_earned_min"
        private const val K_OWN_LOCK_REWARD_PAID = "own_lock_reward_paid"
        private const val K_STREAK_PENALTY = "streak_penalty_min"
        private const val K_STREAK_CUR = "streak_current"
        private const val K_STREAK_BEST = "streak_longest"
        private const val K_STREAK_DAY = "streak_evaluated_day"
        private const val K_STREAK_BONUS = "streak_bonus_min"
        private const val K_STREAK_MALUS = "streak_penalty_today_min"
        private const val K_STREAK_MILE = "streak_milestone"

        const val DEFAULT_GLOBAL_LIMIT_MIN = 60
        const val MAX_GLOBAL_LIMIT_MIN = 120
        /**
         * No longer a cap — the parent decides how much time to hand out and nothing overrules
         * that. Kept only as the step used where a "typical" grant is needed.
         */
        const val TYPICAL_BONUS_MIN = 30

        /** Absolute ceiling across ALL apps. Three hours is the hard maximum. */
        const val DEFAULT_HARDCAP_MIN = 180
        const val MAX_HARDCAP_MIN = 180
        const val MIN_HARDCAP_MIN = 30

        /** Weekly pots. Generous ranges — a week is seven days, not one. */
        const val DEFAULT_WEEK_LIMIT_MIN = 7 * 60          // 7 h of counted time per week
        const val MAX_WEEK_LIMIT_MIN = 21 * 60
        const val DEFAULT_WEEK_HARDCAP_MIN = 10 * 60       // 10 h of phone per week
        const val MAX_WEEK_HARDCAP_MIN = 35 * 60
        const val MIN_WEEK_MIN = 60

        /**
         * Hard ceiling on any manual screen lock. A day, because the child's own menu offers a
         * lock for the rest of the day — nothing may ever run past the next midnight.
         */
        const val MAX_SCREEN_LOCK_MIN = 24 * 60

        /**
         * How often per week the child may start a long focus session. Locking the display is
         * free and unrationed — it costs them screen time, so there is nothing to guard against
         * — but a focus session hides apps and is the one the parent PIN has to end, so the
         * long ones stay a decision rather than a habit.
         */
        const val FOCUS_60_PER_WEEK = 3
        const val FOCUS_120_PER_WEEK = 1

        /** Bounds for the duration the child types in themselves. */
        const val MIN_OWN_LOCK_MIN = 1

        /** Bonus minutes earned per full hour the child keeps their own lock running. */
        const val DEFAULT_OWN_LOCK_REWARD_PER_HOUR = 10
        const val MAX_OWN_LOCK_REWARD_PER_HOUR = 60

        /** Ceiling on what self-locking can earn in one day, so it cannot be farmed. */
        const val OWN_LOCK_REWARD_MAX_PER_DAY = 60

        /** How long the phone may be out of touch with the family before it seals. */
        const val DEFAULT_OFFLINE_LOCK_MIN = 60
        const val MIN_OFFLINE_LOCK_MIN = 15
        const val MAX_OFFLINE_LOCK_MIN = 480
        /** Grace after a restart, so a booting phone is not locked before WLAN is even up. */
        const val BOOT_GRACE_MS = 10 * 60 * 1000L

        /** When this process came up — the anchor for the boot grace above. */
        val processStartedAt: Long = System.currentTimeMillis()
        /** From this many ignored attempts on, the screen is locked every single time. */
        const val HARDCAP_LOCK_ALWAYS_FROM = 3
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
    //
    // One PIN for the whole family. It is set once, salted and hashed, and the hash goes to the
    // family node so every device checks against the same thing. The copy kept here is what
    // makes the PIN work with no network at all — the plain PIN itself is never stored, and
    // never leaves the device.

    val isPinSet: Boolean get() = sp.contains(K_PIN_HASH) || sp.contains(K_SHARED_PIN_HASH)

    fun setPin(pin: String) {
        val salt = System.nanoTime().toString()
        sp.edit()
            .putString(K_PIN_SALT, salt)
            .putString(K_PIN_HASH, hash(pin, salt))
            .apply()
    }

    /** The salt+hash to publish so the other devices can check the same PIN. */
    fun sharablePin(): Pair<String, String>? {
        val salt = sp.getString(K_PIN_SALT, null) ?: return null
        val stored = sp.getString(K_PIN_HASH, null) ?: return null
        return salt to stored
    }

    /** Adopt the family's PIN as received from the server. */
    fun setSharedPin(salt: String, hash: String) {
        if (salt.isBlank() || hash.isBlank()) return
        sp.edit()
            .putString(K_SHARED_PIN_SALT, salt)
            .putString(K_SHARED_PIN_HASH, hash)
            .apply()
    }

    val hasSharedPin: Boolean get() = sp.contains(K_SHARED_PIN_HASH)

    /**
     * Accepts the family PIN and, if one was set on this device before the shared one arrived,
     * that device's own PIN as well. Locking a parent out of their own portal because a sync
     * had not happened yet would be worse than accepting two codes during the changeover.
     */
    fun checkPin(pin: String): Boolean {
        val sharedSalt = sp.getString(K_SHARED_PIN_SALT, null)
        val sharedHash = sp.getString(K_SHARED_PIN_HASH, null)
        if (sharedSalt != null && sharedHash != null && sharedHash == hash(pin, sharedSalt)) return true

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
     * A focus session the CHILD started on itself ("Handy weglegen").
     *
     * Deliberately a separate slot from [focusJson]: the parent's config is copied over that one
     * on every sync, and since the parent's own session is normally OFF, a self-started session
     * stored there would be wiped a second or two after the child started it.
     */
    var selfFocusJson: String
        get() = sp.getString("self_focus_json", "") ?: ""
        set(v) = sp.edit().putString("self_focus_json", v).apply()

    fun selfFocusSession(): com.familylink.ios.sync.FocusSession = runCatching {
        if (selfFocusJson.isBlank()) com.familylink.ios.sync.FocusSession.OFF
        else com.familylink.ios.sync.FocusSession.fromJson(JSONObject(selfFocusJson))
    }.getOrDefault(com.familylink.ios.sync.FocusSession.OFF)

    fun setSelfFocusSession(s: com.familylink.ios.sync.FocusSession) {
        selfFocusJson = s.toJson().toString()
    }

    /** True while the running session is one the child started itself. */
    fun isSelfFocusRunning(): Boolean =
        !focusSession().isRunning() && selfFocusSession().isRunning()

    /**
     * The session that actually governs the device right now. A session pushed by the parent
     * always wins over one the child started itself.
     */
    fun effectiveFocusSession(): com.familylink.ios.sync.FocusSession {
        val fromParent = focusSession()
        if (fromParent.isRunning()) return fromParent
        val own = selfFocusSession()
        return if (own.isRunning()) own else com.familylink.ios.sync.FocusSession.OFF
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

    // ---- Parent notifications ----------------------------------------------
    //
    // Off by default and entirely opt-in. The parent app runs no background service unless
    // notifications are switched on — that is the only reason it would need one, and it is
    // also what puts a permanent (minimum-importance) entry in the shade.

    var notifyEnabled: Boolean
        get() = sp.getBoolean("notify_on", false)
        set(v) = sp.edit().putBoolean("notify_on", v).apply()

    var notifyRequest: Boolean
        get() = sp.getBoolean("notify_request", true)
        set(v) = sp.edit().putBoolean("notify_request", v).apply()

    var notifyChore: Boolean
        get() = sp.getBoolean("notify_chore", true)
        set(v) = sp.edit().putBoolean("notify_chore", v).apply()

    var notifyLimit: Boolean
        get() = sp.getBoolean("notify_limit", true)
        set(v) = sp.edit().putBoolean("notify_limit", v).apply()

    var notifyHardCap: Boolean
        get() = sp.getBoolean("notify_hardcap", true)
        set(v) = sp.edit().putBoolean("notify_hardcap", v).apply()

    var notifyOffline: Boolean
        get() = sp.getBoolean("notify_offline", false)
        set(v) = sp.edit().putBoolean("notify_offline", v).apply()

    // --- de-duplication state, so nothing is ever announced twice ---

    var notifiedRequestAt: Long
        get() = sp.getLong("notified_request_at", 0)
        set(v) = sp.edit().putLong("notified_request_at", v).apply()

    /** Chore ids already announced as done; cleared again when a chore leaves the DONE state. */
    var notifiedChoreIds: Set<String>
        get() = sp.getStringSet("notified_chores", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("notified_chores", HashSet(v)).apply()

    /** Day marker of the last "limit reached" notice, so it fires once per day. */
    var notifiedLimitDay: Int
        get() = sp.getInt("notified_limit_day", -1)
        set(v) = sp.edit().putInt("notified_limit_day", v).apply()

    var notifiedCapDay: Int
        get() = sp.getInt("notified_cap_day", -1)
        set(v) = sp.edit().putInt("notified_cap_day", v).apply()

    var notifiedOfflineAt: Long
        get() = sp.getLong("notified_offline_at", 0)
        set(v) = sp.edit().putLong("notified_offline_at", v).apply()

    /** Today's marker, shared with the notification de-duplication above. */
    fun todayMarker(): Int = dayMarker()

    // ---- Manual device lock ------------------------------------------------
    //
    // The parent locks the phone by hand from their own device. Unlike a focus session this
    // has no end time: it stays locked until the parent lifts it again.

    var manualLockEnabled: Boolean
        get() = sp.getBoolean(K_MANUAL_LOCK, false)
        set(v) = sp.edit().putBoolean(K_MANUAL_LOCK, v).apply()

    /** Optional note shown on the child's block screen ("Beim Essen", "Bis Hausaufgaben fertig"). */
    var manualLockReason: String
        get() = sp.getString(K_MANUAL_LOCK_WHY, "") ?: ""
        set(v) = sp.edit().putString(K_MANUAL_LOCK_WHY, v).apply()

    // ---- Offline lock ------------------------------------------------------
    //
    // Taking the phone off the network was the simplest bypass there is: no new rules arrive,
    // no usage is reported, and a parent cannot lock anything from a distance. The limits
    // themselves always kept working — they are decided on the device — but everything the
    // parent does live went dead, and nobody could tell.
    //
    // A phone that has not been in touch for longer than the parent allows is therefore sealed
    // until it reports back. Not a punishment for a train tunnel: the lock screen carries a
    // button that opens the connection settings, and the lock lifts by itself as soon as one
    // report gets through.

    /**
     * Does this phone currently have working internet? Kept in memory and refreshed by the
     * monitor service, because the decision below needs it and this class has no Context.
     *
     * Optimistic by default: nothing is locked before the first real answer arrives.
     */
    @Volatile
    var networkAvailable: Boolean = true

    var offlineLockEnabled: Boolean
        get() = sp.getBoolean(K_OFFLINE_LOCK, true)
        set(v) = sp.edit().putBoolean(K_OFFLINE_LOCK, v).apply()

    /** How long the device may stay out of touch before it seals. */
    var offlineLockMinutes: Int
        get() = sp.getInt(K_OFFLINE_LOCK_MIN, DEFAULT_OFFLINE_LOCK_MIN)
        set(v) = sp.edit()
            .putInt(K_OFFLINE_LOCK_MIN, v.coerceIn(MIN_OFFLINE_LOCK_MIN, MAX_OFFLINE_LOCK_MIN))
            .apply()

    /**
     * Seconds since the last successful contact with the family server, or -1 when the question
     * does not apply — the device is not paired, or nothing ever synced. A device that never
     * reached the server at all must not be locked for failing to reach it again.
     */
    fun offlineSeconds(): Int {
        if (!syncConfigured) return -1
        val last = lastSyncAt
        if (last <= 0L) return -1
        return ((System.currentTimeMillis() - last) / 1000L).toInt().coerceAtLeast(0)
    }

    /**
     * Is the offline lock due?
     *
     * Two conditions, both needed. The phone has been out of touch for longer than the parent
     * allows, AND this process has been up long enough for the network to have come back —
     * otherwise every restart would lock the phone before Android even has WLAN again.
     */
    fun offlineLockDue(): Boolean {
        if (!offlineLockEnabled) return false
        val offline = offlineSeconds()
        if (offline < 0) return false
        if (System.currentTimeMillis() - processStartedAt < BOOT_GRACE_MS) return false
        if (offline < offlineLockMinutes * 60) return false
        // A phone that is on the internet but cannot reach the family server has run into a
        // server problem, not found a way around the rules. Locking the child for our outage
        // would be indefensible, so the lock needs the connection to be genuinely gone.
        return !networkAvailable
    }

    /**
     * Packages currently suspended because they are blocked. Persisted so a killed service can
     * still release them — a suspended app that nobody un-suspends stays dead forever.
     */
    var suspendedPackages: Set<String>
        get() = sp.getStringSet("suspended_pkgs", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("suspended_pkgs", HashSet(v)).apply()

    // ---- Escape window from a sealed lock ----------------------------------
    //
    // The lock covers everything, but the phone and the parent entry have to stay usable. Rather
    // than lifting the lock whenever an exempt app happens to be in front — which let the child
    // open the phone, swipe away and be free — tapping those buttons opens a short, explicit
    // window for THOSE packages only. Anything else in the foreground cancels it at once.

    private var escapeUntil: Long = 0
    private var escapeFor: Set<String> = emptySet()

    /** Open the window for [packages] for [seconds]. Held in memory: it must not survive a restart. */
    fun openLockEscape(packages: Set<String>, seconds: Int = 120) {
        escapeUntil = System.currentTimeMillis() + seconds * 1000L
        escapeFor = packages
    }

    fun clearLockEscape() {
        escapeUntil = 0
        escapeFor = emptySet()
    }

    /** True while the open window covers any of [packages]. */
    fun lockEscapeAllowsAny(packages: Collection<String>): Boolean =
        packages.any { lockEscapeAllows(it) }

    /** True while [pkg] is one of the packages the open window covers. */
    fun lockEscapeAllows(pkg: String?): Boolean {
        if (System.currentTimeMillis() >= escapeUntil) return false
        return pkg != null && pkg in escapeFor
    }

    // ---- Timed screen lock -------------------------------------------------
    //
    // Not an overlay: the display itself is locked, and every unlock re-locks immediately until
    // the time is up. Capped at [MAX_SCREEN_LOCK_MIN] so a mistake cannot brick the phone for
    // hours — and it always expires by itself.

    var screenLockUntil: Long
        get() = sp.getLong("screen_lock_until", 0)
        set(v) = sp.edit().putLong("screen_lock_until", v).apply()

    /**
     * The lock the child started on themselves, kept apart from [screenLockUntil] on purpose:
     * that one is the parent's rule and travels with every config push, so a shared field would
     * see the child's own lock wiped by the next sync.
     */
    var ownLockUntil: Long
        get() = sp.getLong("own_lock_until", 0)
        set(v) = sp.edit().putLong("own_lock_until", v).apply()

    /**
     * A sealed lock is one nobody lifts — not the child who started it, not the parent. Only the
     * clock ends it, which is the whole point of the "rest of the day" option: a promise you
     * cannot talk yourself out of ten minutes later.
     *
     * Reads false as soon as the lock expires, so the flag can never outlive its lock.
     */
    var ownLockSealed: Boolean
        get() = sp.getBoolean("own_lock_sealed", false) &&
            System.currentTimeMillis() < ownLockUntil
        set(v) = sp.edit().putBoolean("own_lock_sealed", v).apply()

    fun screenLockActive(now: Long = System.currentTimeMillis()): Boolean =
        now < screenLockUntil || now < ownLockUntil

    fun screenLockRemainingSeconds(now: Long = System.currentTimeMillis()): Int =
        ((maxOf(screenLockUntil, ownLockUntil) - now) / 1000L).toInt().coerceAtLeast(0)

    /** Minutes from now until the next midnight — the length of a "rest of the day" lock. */
    fun minutesUntilMidnight(): Int {
        val c = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return ((c.timeInMillis - System.currentTimeMillis()) / 60_000L).toInt().coerceAtLeast(1)
    }

    /** Lock the display for [minutes] on the parent's behalf, clamped to the maximum. */
    fun startScreenLock(minutes: Int) {
        val m = minutes.coerceIn(1, MAX_SCREEN_LOCK_MIN)
        screenLockUntil = System.currentTimeMillis() + m * 60_000L
    }

    /** Start a fresh week's allowance the moment the calendar week turns over. */
    private fun ensureFocusWeek() {
        val week = weekMarker()
        if (sp.getInt(K_OWN_LOCK_WEEK, 0) == week) return
        sp.edit()
            .putInt(K_OWN_LOCK_WEEK, week)
            .putInt(K_FOCUS_USED_60, 0)
            .putInt(K_FOCUS_USED_120, 0)
            .apply()
    }

    private fun focusQuotaKey(minutes: Int): String? = when {
        minutes >= 120 -> K_FOCUS_USED_120
        minutes >= 60 -> K_FOCUS_USED_60
        else -> null
    }

    /**
     * How many self-started focus sessions of this length are left this week. The short ones are
     * not rationed, so they always report [Int.MAX_VALUE].
     */
    fun focusSessionsLeft(minutes: Int): Int {
        val key = focusQuotaKey(minutes) ?: return Int.MAX_VALUE
        ensureFocusWeek()
        val cap = if (key == K_FOCUS_USED_120) FOCUS_120_PER_WEEK else FOCUS_60_PER_WEEK
        return (cap - sp.getInt(key, 0)).coerceAtLeast(0)
    }

    /** Book one long focus session against this week's allowance. */
    fun useFocusSession(minutes: Int): Boolean {
        if (focusSessionsLeft(minutes) <= 0) return false
        focusQuotaKey(minutes)?.let { key ->
            sp.edit().putInt(key, sp.getInt(key, 0) + 1).apply()
        }
        return true
    }

    /**
     * The child locking their own phone. Any length between [MIN_OWN_LOCK_MIN] and the maximum,
     * as often as they like — the lock costs them screen time, so nothing needs rationing here.
     *
     * A sealed lock can only be extended, never cut short — not even by starting a shorter one.
     *
     * @return false when a sealed lock is already running longer than the one asked for.
     */
    fun startOwnLock(minutes: Int, sealed: Boolean = false): Boolean {
        val m = minutes.coerceIn(MIN_OWN_LOCK_MIN, MAX_SCREEN_LOCK_MIN)
        val now = System.currentTimeMillis()
        val until = now + m * 60_000L
        if (ownLockSealed && until <= ownLockUntil) return false

        // Settle whatever an earlier lock already earned before this one overwrites the window.
        settleOwnLockReward()
        ownLockUntil = until
        ownLockSealed = sealed
        sp.edit()
            .putLong(K_OWN_LOCK_REWARD_FROM, now)
            .putLong(K_OWN_LOCK_REWARD_TO, until)
            .apply()
        return true
    }

    /**
     * Lift every lock that may be lifted. The parent's own always goes; the child's goes too
     * unless it was sealed, which is the one lock nothing but time ends.
     *
     * @return true if nothing is left running.
     */
    fun stopScreenLock(): Boolean {
        screenLockUntil = 0
        if (ownLockSealed) return false
        ownLockUntil = 0
        ownLockSealed = false
        // Ending early still pays for the time actually served.
        settleOwnLockReward()
        return true
    }

    // ---- Reward for putting the phone away -------------------------------
    //
    // Locking the phone yourself buys screen time back: every full hour served is worth
    // [ownLockRewardPerHour] bonus minutes, capped per day so it cannot be farmed. It is paid
    // for time actually served, not time promised — a lock the parent lifts after ten minutes
    // pays for ten minutes, and starting a six-hour lock earns nothing until it has run.

    var ownLockRewardEnabled: Boolean
        get() = sp.getBoolean("own_lock_reward_on", true)
        set(v) = sp.edit().putBoolean("own_lock_reward_on", v).apply()

    var ownLockRewardPerHour: Int
        get() = sp.getInt("own_lock_reward_per_hour", DEFAULT_OWN_LOCK_REWARD_PER_HOUR)
        set(v) = sp.edit()
            .putInt("own_lock_reward_per_hour", v.coerceIn(0, MAX_OWN_LOCK_REWARD_PER_HOUR))
            .apply()

    /** Bonus minutes this reward has already paid out today. */
    fun ownLockEarnedToday(): Int {
        if (sp.getInt(K_OWN_LOCK_EARNED_DAY, -1) != dayMarker()) return 0
        return sp.getInt(K_OWN_LOCK_EARNED_MIN, 0)
    }

    /**
     * Pay out what the running or just-finished self-lock has earned so far, and remember the
     * point it was paid up to. Safe to call as often as anything likes — it only ever credits
     * time that has actually passed, and never twice.
     *
     * @return the minutes credited by this call.
     */
    fun settleOwnLockReward(): Int {
        val from = sp.getLong(K_OWN_LOCK_REWARD_FROM, 0)
        val to = sp.getLong(K_OWN_LOCK_REWARD_TO, 0)
        if (from <= 0 || to <= from) return 0

        val now = System.currentTimeMillis()
        // A lock the parent lifted stops earning at the moment it stopped running.
        val servedUntil = minOf(now, to, maxOf(ownLockUntil, from))
        val servedMinutes = ((servedUntil - from) / 60_000L).toInt()
        val finished = now >= to || !screenLockActive(now)

        if (!ownLockRewardEnabled) {
            if (finished) clearOwnLockRewardWindow()
            return 0
        }

        val due = servedMinutes * ownLockRewardPerHour / 60
        val alreadyPaid = ownLockEarnedToday()
        val room = (OWN_LOCK_REWARD_MAX_PER_DAY - alreadyPaid).coerceAtLeast(0)
        val payable = (due - sp.getInt(K_OWN_LOCK_REWARD_PAID, 0)).coerceIn(0, room)

        if (payable > 0) {
            addBonusMinutes(payable)
            sp.edit()
                .putInt(K_OWN_LOCK_EARNED_DAY, dayMarker())
                .putInt(K_OWN_LOCK_EARNED_MIN, alreadyPaid + payable)
                .putInt(K_OWN_LOCK_REWARD_PAID, sp.getInt(K_OWN_LOCK_REWARD_PAID, 0) + payable)
                .apply()
        }
        if (finished) clearOwnLockRewardWindow()
        return payable
    }

    private fun clearOwnLockRewardWindow() {
        sp.edit()
            .remove(K_OWN_LOCK_REWARD_FROM)
            .remove(K_OWN_LOCK_REWARD_TO)
            .remove(K_OWN_LOCK_REWARD_PAID)
            .apply()
    }

    // ---- Weekly limits -----------------------------------------------------
    //
    // A weekly pot is one budget for the whole week: the child may spend it all on Monday, or
    // ration it. With BOTH, the daily limit still applies inside the week, so a single day
    // cannot swallow everything.

    var limitScope: LimitScope
        get() = runCatching { LimitScope.valueOf(sp.getString("limit_scope", null) ?: "DAY") }
            .getOrDefault(LimitScope.DAY)
        set(v) = sp.edit().putString("limit_scope", v.name).apply()

    var weeklyLimitMinutes: Int
        get() = sp.getInt("weekly_limit_min", DEFAULT_WEEK_LIMIT_MIN)
        set(v) = sp.edit().putInt("weekly_limit_min", v.coerceIn(MIN_WEEK_MIN, MAX_WEEK_LIMIT_MIN)).apply()

    var hardCapScope: LimitScope
        get() = runCatching { LimitScope.valueOf(sp.getString("hardcap_scope", null) ?: "DAY") }
            .getOrDefault(LimitScope.DAY)
        set(v) = sp.edit().putString("hardcap_scope", v.name).apply()

    var weeklyHardCapMinutes: Int
        get() = sp.getInt("weekly_hardcap_min", DEFAULT_WEEK_HARDCAP_MIN)
        set(v) = sp.edit().putInt("weekly_hardcap_min", v.coerceIn(MIN_WEEK_MIN, MAX_WEEK_HARDCAP_MIN)).apply()

    /** Year * 100 + calendar week. Changes exactly when a new week starts. */
    private fun weekMarker(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.YEAR) * 100 + c.get(Calendar.WEEK_OF_YEAR)
    }

    /**
     * Fold a finished day into this week's running totals, resetting them first if the week
     * itself has turned over. Only ever called from the daily rollover.
     */
    private fun accumulateWeek(countedSeconds: Int, totalSeconds: Int) {
        val week = weekMarker()
        val known = sp.getInt(K_WEEK_MARKER, -1)
        val baseCounted = if (known == week) sp.getInt(K_WEEK_COUNTED, 0) else 0
        val baseTotal = if (known == week) sp.getInt(K_WEEK_TOTAL, 0) else 0
        sp.edit()
            .putInt(K_WEEK_MARKER, week)
            .putInt(K_WEEK_COUNTED, baseCounted + countedSeconds.coerceAtLeast(0))
            .putInt(K_WEEK_TOTAL, baseTotal + totalSeconds.coerceAtLeast(0))
            .apply()
    }

    /** Seconds already spent on *finished* days of this week (today is added by the caller). */
    private fun weekBase(key: String): Int {
        val week = weekMarker()
        if (sp.getInt(K_WEEK_MARKER, -1) != week) return 0
        return sp.getInt(key, 0)
    }

    /** Counted time this week, today included. */
    fun weekCountedSeconds(): Int {
        ensureToday()
        return weekBase(K_WEEK_COUNTED) + sp.getInt(K_GLOBAL_USED, 0)
    }

    /** Whole-device time this week, today included. */
    fun weekTotalSeconds(): Int {
        ensureToday()
        return weekBase(K_WEEK_TOTAL) + sp.getInt(K_TOTAL_USED, 0)
    }

    /** Today's whole-device seconds, cached by the monitor alongside the counted figure. */
    var totalDeviceSecondsToday: Int
        get() { ensureToday(); return sp.getInt(K_TOTAL_USED, 0) }
        set(v) { ensureToday(); sp.edit().putInt(K_TOTAL_USED, v.coerceAtLeast(0)).apply() }

    // ---- Personal: whose phone this is, and how it looks -------------------
    //
    // The reference greets the parent with the child's name and picture rather than with the
    // word "device". Name and colour travel to the child's phone with the rules; the picture
    // stays on the phone that chose it, because a photo has no business on a sync server.

    var childName: String
        get() = sp.getString("child_name", "") ?: ""
        set(v) = sp.edit().putString("child_name", v.trim().take(24)).apply()

    /** BLUE, GREEN, PURPLE or ORANGE — the accent the whole app is drawn in. */
    var accentChoice: String
        get() = sp.getString("accent_choice", "BLUE") ?: "BLUE"
        set(v) = sp.edit().putString("accent_choice", v).apply()

    var childPhotoUri: String
        get() = sp.getString("child_photo", "") ?: ""
        set(v) = sp.edit().putString("child_photo", v).apply()

    /** The three amounts offered as buttons for bonus time. */
    var bonusPresets: List<Int>
        get() = (sp.getString("bonus_presets", "10,20,45") ?: "10,20,45")
            .split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it > 0 }
            .ifEmpty { listOf(10, 20, 45) }
        set(v) = sp.edit()
            .putString("bonus_presets", v.map { it.coerceIn(1, 240) }.joinToString(","))
            .apply()

    // ---- What happened: the bell's feed ------------------------------------
    //
    // The same events the parent can be notified about, kept as a short list so they can be
    // read back later. A notification is gone the moment it is swiped away; this is not.

    data class Event(val type: String, val title: String, val text: String, val at: Long)

    fun addEvent(type: String, title: String, text: String) {
        val arr = runCatching { org.json.JSONArray(sp.getString("events_json", "[]")) }
            .getOrDefault(org.json.JSONArray())
        val out = org.json.JSONArray()
        out.put(
            JSONObject().put("t", type).put("h", title).put("x", text)
                .put("a", System.currentTimeMillis())
        )
        // Keep the newest forty; the tail is history nobody scrolls to.
        for (i in 0 until minOf(arr.length(), 39)) out.put(arr.get(i))
        sp.edit()
            .putString("events_json", out.toString())
            .putInt("events_unread", sp.getInt("events_unread", 0) + 1)
            .apply()
    }

    fun events(): List<Event> {
        val arr = runCatching { org.json.JSONArray(sp.getString("events_json", "[]")) }
            .getOrDefault(org.json.JSONArray())
        val out = ArrayList<Event>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                Event(
                    type = o.optString("t", ""),
                    title = o.optString("h", ""),
                    text = o.optString("x", ""),
                    at = o.optLong("a", 0)
                )
            )
        }
        return out
    }

    fun unreadEventCount(): Int = sp.getInt("events_unread", 0)

    fun markEventsRead() = sp.edit().putInt("events_unread", 0).apply()

    // ---- School time --------------------------------------------------------
    //
    // The second schedule from the reference. Unlike Ruhezeit it does not shut the phone down:
    // during class the allowed apps stay usable and everything else waits, on the weekdays and
    // between the hours the parent picked.

    var schoolTimeEnabled: Boolean
        get() = sp.getBoolean("school_on", false)
        set(v) = sp.edit().putBoolean("school_on", v).apply()

    var schoolStartMin: Int
        get() = sp.getInt("school_start", 8 * 60)
        set(v) = sp.edit().putInt("school_start", ((v % 1440) + 1440) % 1440).apply()

    var schoolEndMin: Int
        get() = sp.getInt("school_end", 13 * 60)
        set(v) = sp.edit().putInt("school_end", ((v % 1440) + 1440) % 1440).apply()

    /**
     * The weekdays it applies to, one bit per day with Monday as bit 0. Monday to Friday by
     * default, which is what a school week is.
     */
    var schoolDays: Int
        get() = sp.getInt("school_days", 0b0011111)
        set(v) = sp.edit().putInt("school_days", v and 0b1111111).apply()

    fun schoolDayEnabled(mondayBased: Int): Boolean = (schoolDays shr mondayBased) and 1 == 1

    fun toggleSchoolDay(mondayBased: Int) {
        schoolDays = schoolDays xor (1 shl mondayBased)
    }

    /** Is class in session right now? */
    fun isSchoolTime(nowMin: Int = minutesSinceMidnight()): Boolean {
        if (!schoolTimeEnabled) return false
        // Calendar counts Sunday as 1; the mask counts Monday as 0.
        val mondayBased = (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7
        if (!schoolDayEnabled(mondayBased)) return false
        val start = schoolStartMin
        val end = schoolEndMin
        return if (start <= end) nowMin in start until end else nowMin >= start || nowMin < end
    }

    // ---- Streak: days in a row inside the daily budget ---------------------
    //
    // Storage only. Every rule lives in [StreakLogic], which knows nothing about Android and is
    // therefore the one part of the limit machinery with real unit tests. The evaluation runs
    // exactly once per day, from the rollover in ensureToday(), and writes its result here.

    // Off by default and no longer switchable from either portal: the streak was taken out of
    // the UI, and a bonus nobody can see or turn off is worse than no bonus at all. The rules
    // themselves stay — they are the one unit-tested part of the limit machinery.
    var streakEnabled: Boolean
        get() = sp.getBoolean(K_STREAK_ON, false)
        set(v) = sp.edit().putBoolean(K_STREAK_ON, v).apply()

    /** What breaking the streak costs the following day. */
    var streakPenaltyMinutes: Int
        get() = sp.getInt(K_STREAK_PENALTY, StreakLogic.DEFAULT_PENALTY_MIN)
        set(v) = sp.edit()
            .putInt(K_STREAK_PENALTY, v.coerceIn(0, StreakLogic.MAX_PENALTY_MIN))
            .apply()

    /**
     * The state as stored. Reading it rolls the day over first, so a phone that was switched off
     * over midnight evaluates the moment anything asks.
     */
    fun streakState(): StreakState {
        ensureToday()
        return readStreak()
    }

    /** Raw read without the rollover — used from inside the rollover itself. */
    private fun readStreak(): StreakState = StreakState(
        current = sp.getInt(K_STREAK_CUR, 0),
        longest = sp.getInt(K_STREAK_BEST, 0),
        bonusMinutesToday = sp.getInt(K_STREAK_BONUS, 0),
        penaltyMinutesToday = sp.getInt(K_STREAK_MALUS, 0),
        evaluatedDay = sp.getInt(K_STREAK_DAY, -1),
        milestoneReached = sp.getInt(K_STREAK_MILE, 0)
    )

    private fun writeStreak(s: StreakState) {
        sp.edit()
            .putInt(K_STREAK_CUR, s.current)
            .putInt(K_STREAK_BEST, s.longest)
            .putInt(K_STREAK_BONUS, s.bonusMinutesToday)
            .putInt(K_STREAK_MALUS, s.penaltyMinutesToday)
            .putInt(K_STREAK_DAY, s.evaluatedDay)
            .putInt(K_STREAK_MILE, s.milestoneReached)
            .apply()
    }

    /** Extra seconds unlocked today by reaching a milestone. Zero while the feature is off. */
    val streakBonusSecondsToday: Int
        get() {
            if (!streakEnabled) return 0
            ensureToday()
            return sp.getInt(K_STREAK_BONUS, 0) * 60
        }

    /** Seconds taken off today because the streak broke yesterday. */
    val streakPenaltySecondsToday: Int
        get() {
            if (!streakEnabled) return 0
            ensureToday()
            return sp.getInt(K_STREAK_MALUS, 0) * 60
        }

    /**
     * Fold the finished day into the streak. Called from the rollover only, with the numbers of
     * the day that just ended.
     *
     * @param finishedDay   day marker of the day that ended (-1 on a fresh install)
     * @param newDay        day marker of the day now starting
     * @param usedSeconds   counted seconds on the finished day
     * @param limitSeconds  the limit that was actually in force on the finished day
     * @param limitsWereOff true when the Aus-Knopf was active that day, making it no fair test
     */
    private fun rollStreak(
        finishedDay: Int,
        newDay: Int,
        usedSeconds: Int,
        limitSeconds: Int,
        limitsWereOff: Boolean
    ) {
        // A first launch has no finished day to judge. Only mark the day as evaluated, so the
        // day of installation is never counted as one that was kept.
        if (finishedDay <= 0) {
            writeStreak(
                readStreak().copy(
                    evaluatedDay = newDay,
                    bonusMinutesToday = 0,
                    penaltyMinutesToday = 0,
                    milestoneReached = 0
                )
            )
            return
        }
        writeStreak(
            StreakLogic.evaluate(
                previous = readStreak(),
                outcome = StreakLogic.outcomeFor(usedSeconds, limitSeconds, limitsWereOff),
                penaltyMinutes = streakPenaltyMinutes,
                newDay = newDay
            )
        )
    }

    // ---- Absolute daily ceiling (Gesamtlimit) ------------------------------
    //
    // Unlike the daily budget above, EVERY app counts towards this one — Plus apps included.
    // It is a hard ceiling: bonus minutes, the off-button and a granted extension cannot lift
    // it. Once it is reached the phone is done for the day.

    var hardCapEnabled: Boolean
        get() = sp.getBoolean(K_HARDCAP_ON, true)
        set(v) = sp.edit().putBoolean(K_HARDCAP_ON, v).apply()

    var hardCapMinutes: Int
        get() = sp.getInt(K_HARDCAP_MIN, DEFAULT_HARDCAP_MIN)
        set(v) = sp.edit().putInt(K_HARDCAP_MIN, v.coerceIn(MIN_HARDCAP_MIN, MAX_HARDCAP_MIN)).apply()

    /** How often the ceiling was ignored today — drives the escalation to a screen lock. */
    val hardCapHitsToday: Int
        get() { ensureToday(); return sp.getInt(K_HARDCAP_HITS, 0) }

    /** Count one fresh attempt to keep using the phone past the ceiling. Returns the new count. */
    fun recordHardCapHit(): Int {
        ensureToday()
        val next = sp.getInt(K_HARDCAP_HITS, 0) + 1
        sp.edit().putInt(K_HARDCAP_HITS, next).apply()
        return next
    }

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
        val s = effectiveBedtimeStartMin()
        val e = bedtimeEndMin
        return if (s <= e) nowMinutes in s until e else (nowMinutes >= s || nowMinutes < e)
    }

    /**
     * Bedtime start with a granted extension already applied: "15 minutes more" right before
     * bedtime would be worthless otherwise.
     *
     * The shift is clamped so at least a minute of bedtime always survives. Grants are no
     * longer capped, and an hours-long one would otherwise push the start past the end and
     * invert the window into "bedtime all day" — the opposite of what was intended.
     */
    fun effectiveBedtimeStartMin(): Int {
        val start = bedtimeStartMin
        val end = bedtimeEndMin
        val windowLength = ((end - start) + 1440) % 1440
        if (windowLength == 0) return start
        val shift = extensionMinutesToday.coerceIn(0, windowLength - 1)
        return (start + shift) % 1440
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
            // ---- streak: judge the day that just ended, before its counters are wiped ----
            //
            // The limit as it really was yesterday: the base budget, plus what the parent
            // granted, plus yesterday's own milestone bonus, minus yesterday's own penalty.
            // Judging the day against today's numbers instead would break streaks for time the
            // child was actually allowed to spend.
            val finishedDay = sp.getInt(K_USAGE_DAY, -1)
            val limitYesterday = (
                sp.getInt(K_GLOBAL_LIMIT_MIN, DEFAULT_GLOBAL_LIMIT_MIN) * 60 +
                    sp.getInt(K_BONUS_SEC, 0) +
                    sp.getInt(K_STREAK_BONUS, 0) * 60 -
                    sp.getInt(K_STREAK_MALUS, 0) * 60
                ).coerceAtLeast(60)
            // The Aus-Knopf makes a day no fair test, so it must not break the streak. Its
            // target time is 23:00 of the day it was pressed, which is how that day is known.
            val offEpoch = sp.getLong(K_OFF_UNTIL, 0)
            rollStreak(
                finishedDay = finishedDay,
                newDay = today,
                usedSeconds = sp.getInt(K_GLOBAL_USED, 0),
                limitSeconds = limitYesterday,
                limitsWereOff = offEpoch > 0 && dayMarkerOf(offEpoch) == finishedDay
            )

            // Archive the finished day before wiping the counters, so the weekly report has data.
            archiveDay(sp.getInt(K_USAGE_DAY, -1), sp.getInt(K_GLOBAL_USED, 0))
            // Roll the finished day into the week's running totals (which reset on a new week).
            accumulateWeek(sp.getInt(K_GLOBAL_USED, 0), sp.getInt(K_TOTAL_USED, 0))
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
                .putInt(K_HARDCAP_HITS, 0)
                .putInt(K_TOTAL_USED, 0)
                .putInt("extension_min", 0)
                .putLong("bonus_until", 0)
                .apply()
        }
    }

    // ---- Bonus time (parent-granted extension, max 30 min/day) -------------

    /**
     * Two ways to give the child more time, chosen per grant:
     *
     *  EXTENSION — raises the limits by N minutes: the daily budget, the absolute ceiling and
     *              the start of bedtime all move by the same amount. The child still has to
     *              spend the time on apps that count.
     *  BONUS     — a plain N-minute countdown during which everything is open. It runs on the
     *              clock and ends on the clock, whatever was used.
     *
     * Neither is capped: how much time the child gets is the parent's call.
     */
    var extensionMinutesToday: Int
        get() { ensureToday(); return sp.getInt("extension_min", 0) }
        private set(v) { ensureToday(); sp.edit().putInt("extension_min", v.coerceAtLeast(0)).apply() }

    /** Epoch at which a running bonus countdown ends. */
    var bonusUntilEpoch: Long
        get() = sp.getLong("bonus_until", 0)
        private set(v) = sp.edit().putLong("bonus_until", v).apply()

    fun bonusCountdownActive(now: Long = System.currentTimeMillis()): Boolean = now < bonusUntilEpoch

    fun bonusCountdownRemainingSeconds(now: Long = System.currentTimeMillis()): Int =
        ((bonusUntilEpoch - now) / 1000L).toInt().coerceAtLeast(0)

    /** Minutes of the daily allowance already handed out, in either form. */
    fun grantedMinutesToday(): Int = bonusSecondsToday / 60

    /**
     * Grant [minutes] as a straight extension of the limits. Returns the minutes actually given
     * after the daily allowance is applied.
     */
    fun grantExtension(minutes: Int): Int {
        ensureToday()
        val before = bonusSecondsToday / 60
        val after = addBonusMinutes(minutes)
        val given = after - before
        if (given > 0) extensionMinutesToday = extensionMinutesToday + given
        return given
    }

    /**
     * Grant [minutes] as a free countdown. Also drawn from the daily allowance, so a parent
     * cannot hand out unlimited time by switching between the two forms.
     */
    fun grantBonusCountdown(minutes: Int): Int {
        ensureToday()
        val before = bonusSecondsToday / 60
        val after = addBonusMinutes(minutes)
        val given = after - before
        if (given > 0) {
            // Extend a countdown that is still running rather than restarting it.
            val base = maxOf(System.currentTimeMillis(), bonusUntilEpoch)
            bonusUntilEpoch = base + given * 60_000L
        }
        return given
    }

    fun stopBonusCountdown() { bonusUntilEpoch = 0 }

    /**
     * Child side: take the two grant values straight from the parent's config. Absolute values,
     * not increments, so a repeated sync can never hand out the same minutes twice.
     */
    fun applyGrants(extensionMinutes: Int, bonusUntil: Long) {
        ensureToday()
        sp.edit()
            .putInt("extension_min", extensionMinutes.coerceAtLeast(0))
            .putLong("bonus_until", bonusUntil)
            .apply()
    }

    /** Extra global seconds granted by a parent today. Uncapped. */
    val bonusSecondsToday: Int
        get() { ensureToday(); return sp.getInt(K_BONUS_SEC, 0) }

    /** Minutes handed out today, in either form. Informational only — there is no ceiling. */
    fun grantedBonusMinutes(): Int = bonusSecondsToday / 60

    // Absolute setters used when applying a config pushed from the parent device.
    fun setBonusMinutesAbsolute(minutes: Int) {
        ensureToday()
        sp.edit().putInt(K_BONUS_SEC, (minutes * 60).coerceAtLeast(0)).apply()
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

    /** Add [minutes] of bonus time. Returns the new total for today. */
    fun addBonusMinutes(minutes: Int): Int {
        ensureToday()
        val current = sp.getInt(K_BONUS_SEC, 0)
        val total = (current + minutes * 60).coerceAtLeast(0)
        sp.edit().putInt(K_BONUS_SEC, total).apply()
        return total / 60
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

    /** The same marker for an arbitrary point in time. */
    private fun dayMarkerOf(epochMillis: Long): Int {
        val c = Calendar.getInstance().apply { timeInMillis = epochMillis }
        return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
    }
}

fun minutesSinceMidnight(): Int {
    val c = Calendar.getInstance()
    return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
}
