package com.familylink.ios.sync

import com.familylink.ios.data.Prefs
import org.json.JSONArray
import org.json.JSONObject

/** Which side of the pair this installation is. */
enum class DeviceRole { UNSET, PARENT, CHILD }

/**
 * IMPORTANT — why package names are never used as JSON keys here.
 *
 * Firebase Realtime Database rejects keys containing '.', '$', '#', '[', ']' or '/'.
 * Android package names always contain dots, so a payload like
 *     {"com.whatsapp": 300}
 * makes the server refuse the ENTIRE write — silently, from the app's point of view.
 * That is why app categories never reached the child and the app list never reached the
 * parent, while plain numbers went through fine.
 *
 * Everything keyed by package is therefore serialised as an ARRAY of objects, which only
 * uses numeric indices. Reading still accepts the old map format so devices that already
 * wrote legacy data keep working.
 */
private object Keys {
    const val PKG = "p"
    const val VALUE = "v"
    const val SECONDS = "s"
    const val NAME = "n"
    const val CATEGORY = "c"
    const val LIMIT = "l"
}

/**
 * One app installed on the child device, with the category currently in force there.
 *
 * This is the missing direction of the sync: the parent used to know only the apps the child
 * had actually *used* today, and only its own idea of their categories. An app the child never
 * opened could not be classified at all, and a PLUS mark made on the child device was invisible
 * in the parent portal. The child now publishes its full launchable app list together with the
 * category each app really has.
 */
data class ChildApp(
    val pkg: String,
    val label: String,
    val category: String,
    val limitMinutes: Int
) {
    fun toJson(): JSONObject = JSONObject()
        .put(Keys.PKG, pkg)
        .put(Keys.NAME, label)
        .put(Keys.CATEGORY, category)
        .put(Keys.LIMIT, limitMinutes)

    companion object {
        fun listToJson(apps: List<ChildApp>): JSONArray =
            JSONArray().also { arr -> apps.forEach { arr.put(it.toJson()) } }

        fun listFromJson(arr: JSONArray?): List<ChildApp> {
            if (arr == null) return emptyList()
            val out = ArrayList<ChildApp>(arr.length())
            for (i in 0 until arr.length()) {
                val e = arr.optJSONObject(i) ?: continue
                val pkg = e.optString(Keys.PKG, "")
                if (pkg.isBlank()) continue
                out.add(
                    ChildApp(
                        pkg = pkg,
                        label = e.optString(Keys.NAME, pkg),
                        category = e.optString(Keys.CATEGORY, "STANDARD"),
                        limitMinutes = e.optInt(Keys.LIMIT, 30)
                    )
                )
            }
            return out
        }
    }
}

/** Read a legacy map-shaped node, if present. */
private fun legacyMap(o: JSONObject, name: String): Map<String, String> {
    val node = o.optJSONObject(name) ?: return emptyMap()
    val out = HashMap<String, String>()
    node.keys().forEach { out[it] = node.optString(it, "") }
    return out
}

/**
 * The rules the parent owns and the child obeys. Parent writes it, child applies it.
 */
data class FamilyConfig(
    val globalLimitMinutes: Int,
    val bedtimeEnabled: Boolean,
    val bedtimeStartMin: Int,
    val bedtimeEndMin: Int,
    val offlineLockEnabled: Boolean,
    val offlineLockMinutes: Int,
    val bonusMinutes: Int,
    val offUntilEpoch: Long,
    val settingsUnlockedUntil: Long,
    /** packageName -> "CATEGORY:limitMinutes" */
    val categories: Map<String, String>,
    /** Active focus session pushed down from the parent (headline feature). */
    val focus: FocusSession = FocusSession.OFF,
    /** Chore list shared between both devices. */
    val chores: List<Chore> = emptyList(),
    /** How the daily budget is measured on the child device. */
    val usageMode: String = "CATEGORIES",
    /** Absolute daily ceiling across ALL apps (Plus included). */
    val hardCapEnabled: Boolean = true,
    val hardCapMinutes: Int = 180,
    /** Whether limits run per day, per week or both. */
    val limitScope: String = "DAY",
    val weeklyLimitMinutes: Int = 420,
    val hardCapScope: String = "DAY",
    val weeklyHardCapMinutes: Int = 600,
    /** Epoch until which the display itself is locked (max 15 min, expires by itself). */
    val screenLockUntil: Long = 0,
    /** Minutes granted today as a limit extension (raises budget, ceiling and bedtime). */
    val extensionMinutes: Int = 0,
    /** Epoch at which a running free-time countdown ends. */
    val bonusUntilEpoch: Long = 0,
    /** Parent locked the device by hand; stays until they lift it. */
    val manualLock: Boolean = false,
    val manualLockReason: String = "",
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("focus", focus.toJson())
        put("chores", Chore.listToJson(chores))
        put("usageMode", usageMode)
        put("hardCapEnabled", hardCapEnabled)
        put("hardCapMinutes", hardCapMinutes)
        put("limitScope", limitScope)
        put("weeklyLimitMinutes", weeklyLimitMinutes)
        put("hardCapScope", hardCapScope)
        put("weeklyHardCapMinutes", weeklyHardCapMinutes)
        put("screenLockUntil", screenLockUntil)
        put("extensionMinutes", extensionMinutes)
        put("bonusUntilEpoch", bonusUntilEpoch)
        put("manualLock", manualLock)
        put("manualLockReason", manualLockReason)
        put("globalLimitMinutes", globalLimitMinutes)
        put("bedtimeEnabled", bedtimeEnabled)
        put("bedtimeStartMin", bedtimeStartMin)
        put("bedtimeEndMin", bedtimeEndMin)
        put("offlineLockEnabled", offlineLockEnabled)
        put("offlineLockMinutes", offlineLockMinutes)
        put("bonusMinutes", bonusMinutes)
        put("offUntilEpoch", offUntilEpoch)
        put("settingsUnlockedUntil", settingsUnlockedUntil)
        put("updatedAt", updatedAt)
        // Array form — package names must never become keys (see Keys doc above).
        put("categoryList", JSONArray().also { arr ->
            categories.forEach { (pkg, value) ->
                arr.put(JSONObject().put(Keys.PKG, pkg).put(Keys.VALUE, value))
            }
        })
    }

    companion object {
        fun fromJson(o: JSONObject): FamilyConfig {
            val cats = HashMap<String, String>()
            // Preferred: array form.
            o.optJSONArray("categoryList")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val e = arr.optJSONObject(i) ?: continue
                    val pkg = e.optString(Keys.PKG, "")
                    if (pkg.isNotBlank()) cats[pkg] = e.optString(Keys.VALUE, "STANDARD:30")
                }
            }
            // Fallback: legacy map form written by older builds.
            if (cats.isEmpty()) cats.putAll(legacyMap(o, "categories"))

            return FamilyConfig(
                globalLimitMinutes = o.optInt("globalLimitMinutes", 60),
                bedtimeEnabled = o.optBoolean("bedtimeEnabled", true),
                bedtimeStartMin = o.optInt("bedtimeStartMin", 20 * 60),
                bedtimeEndMin = o.optInt("bedtimeEndMin", 6 * 60),
                offlineLockEnabled = o.optBoolean("offlineLockEnabled", true),
                offlineLockMinutes = o.optInt("offlineLockMinutes", Prefs.DEFAULT_OFFLINE_LOCK_MIN),
                bonusMinutes = o.optInt("bonusMinutes", 0),
                offUntilEpoch = o.optLong("offUntilEpoch", 0),
                settingsUnlockedUntil = o.optLong("settingsUnlockedUntil", 0),
                categories = cats,
                focus = FocusSession.fromJson(o.optJSONObject("focus")),
                chores = Chore.listFromJson(o.optJSONArray("chores")),
                usageMode = o.optString("usageMode", "CATEGORIES"),
                hardCapEnabled = o.optBoolean("hardCapEnabled", true),
                hardCapMinutes = o.optInt("hardCapMinutes", 180),
                limitScope = o.optString("limitScope", "DAY"),
                weeklyLimitMinutes = o.optInt("weeklyLimitMinutes", 420),
                hardCapScope = o.optString("hardCapScope", "DAY"),
                weeklyHardCapMinutes = o.optInt("weeklyHardCapMinutes", 600),
                screenLockUntil = o.optLong("screenLockUntil", 0),
                extensionMinutes = o.optInt("extensionMinutes", 0),
                bonusUntilEpoch = o.optLong("bonusUntilEpoch", 0),
                manualLock = o.optBoolean("manualLock", false),
                manualLockReason = o.optString("manualLockReason", ""),
                updatedAt = o.optLong("updatedAt", 0)
            )
        }
    }
}

/** What the child reports upward: live usage so the parent sees it in real time. */
data class ChildStatus(
    /** Time charged against the daily budget (PLUS apps excluded). */
    val globalUsedSeconds: Int,
    /** Total foreground time of the whole phone today, across every app. */
    val totalDeviceSeconds: Int = 0,
    /** The limit in force on the child right now, so the parent sees the real ratio. */
    val limitSeconds: Int = 0,
    val bonusSeconds: Int = 0,
    /** Counted and whole-device time so far this week, today included. */
    val weekCountedSeconds: Int = 0,
    val weekTotalSeconds: Int = 0,
    val perAppSeconds: Map<String, Int>,
    val perAppLabels: Map<String, String>,
    val blockedToday: List<String>,
    val bedtimeActive: Boolean,
    val focusLabel: String = "",
    val deviceName: String,
    val batteryPercent: Int = -1,
    val updatedAt: Long = System.currentTimeMillis()
) {
    /** How old this snapshot is, in seconds. */
    fun ageSeconds(now: Long = System.currentTimeMillis()): Int =
        (((now - updatedAt) / 1000L).toInt()).coerceAtLeast(0)

    fun toJson(): JSONObject = JSONObject().apply {
        put("globalUsedSeconds", globalUsedSeconds)
        put("totalDeviceSeconds", totalDeviceSeconds)
        put("limitSeconds", limitSeconds)
        put("bonusSeconds", bonusSeconds)
        put("weekCountedSeconds", weekCountedSeconds)
        put("weekTotalSeconds", weekTotalSeconds)
        put("focusLabel", focusLabel)
        put("batteryPercent", batteryPercent)
        put("bedtimeActive", bedtimeActive)
        put("deviceName", deviceName)
        put("updatedAt", updatedAt)
        put("blockedToday", JSONArray(blockedToday))
        // One array carrying package, seconds and label together — no dotted keys.
        put("apps", JSONArray().also { arr ->
            perAppSeconds.entries
                .sortedByDescending { it.value }
                .take(40) // keep the payload small; the tail is noise anyway
                .forEach { (pkg, secs) ->
                    arr.put(
                        JSONObject()
                            .put(Keys.PKG, pkg)
                            .put(Keys.SECONDS, secs)
                            .put(Keys.NAME, perAppLabels[pkg] ?: pkg)
                    )
                }
        })
    }

    companion object {
        fun fromJson(o: JSONObject): ChildStatus {
            val usage = HashMap<String, Int>()
            val labels = HashMap<String, String>()

            // Preferred: array form.
            o.optJSONArray("apps")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val e = arr.optJSONObject(i) ?: continue
                    val pkg = e.optString(Keys.PKG, "")
                    if (pkg.isBlank()) continue
                    usage[pkg] = e.optInt(Keys.SECONDS, 0)
                    labels[pkg] = e.optString(Keys.NAME, pkg)
                }
            }
            // Fallback: legacy map form.
            if (usage.isEmpty()) {
                o.optJSONObject("perAppSeconds")?.let { a -> a.keys().forEach { usage[it] = a.optInt(it) } }
                o.optJSONObject("perAppLabels")?.let { a -> a.keys().forEach { labels[it] = a.optString(it) } }
            }

            val blocked = ArrayList<String>()
            o.optJSONArray("blockedToday")?.let { arr ->
                for (i in 0 until arr.length()) blocked.add(arr.optString(i))
            }
            return ChildStatus(
                globalUsedSeconds = o.optInt("globalUsedSeconds", 0),
                totalDeviceSeconds = o.optInt("totalDeviceSeconds", 0),
                limitSeconds = o.optInt("limitSeconds", 0),
                bonusSeconds = o.optInt("bonusSeconds", 0),
                weekCountedSeconds = o.optInt("weekCountedSeconds", 0),
                weekTotalSeconds = o.optInt("weekTotalSeconds", 0),
                perAppSeconds = usage,
                perAppLabels = labels,
                blockedToday = blocked,
                bedtimeActive = o.optBoolean("bedtimeActive", false),
                focusLabel = o.optString("focusLabel", ""),
                deviceName = o.optString("deviceName", "Kindergerät"),
                batteryPercent = o.optInt("batteryPercent", -1),
                updatedAt = o.optLong("updatedAt", 0)
            )
        }
    }
}
