package com.familylink.ios.sync

import org.json.JSONObject

/** Which side of the pair this installation is. */
enum class DeviceRole { UNSET, PARENT, CHILD }

/**
 * The rules the parent owns and the child obeys. Parent writes it, child applies it.
 * Serialised as flat JSON so it maps 1:1 onto a Firebase RTDB node.
 */
data class FamilyConfig(
    val globalLimitMinutes: Int,
    val bedtimeEnabled: Boolean,
    val bedtimeStartMin: Int,
    val bedtimeEndMin: Int,
    val bedtimeSoundEnabled: Boolean,
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
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("focus", focus.toJson())
        put("chores", Chore.listToJson(chores))
        put("usageMode", usageMode)
        put("globalLimitMinutes", globalLimitMinutes)
        put("bedtimeEnabled", bedtimeEnabled)
        put("bedtimeStartMin", bedtimeStartMin)
        put("bedtimeEndMin", bedtimeEndMin)
        put("bedtimeSoundEnabled", bedtimeSoundEnabled)
        put("bonusMinutes", bonusMinutes)
        put("offUntilEpoch", offUntilEpoch)
        put("settingsUnlockedUntil", settingsUnlockedUntil)
        put("updatedAt", updatedAt)
        put("categories", JSONObject().also { c -> categories.forEach { (k, v) -> c.put(k, v) } })
    }

    companion object {
        fun fromJson(o: JSONObject): FamilyConfig {
            val cats = HashMap<String, String>()
            o.optJSONObject("categories")?.let { c ->
                c.keys().forEach { k -> cats[k] = c.optString(k, "STANDARD:30") }
            }
            return FamilyConfig(
                globalLimitMinutes = o.optInt("globalLimitMinutes", 60),
                bedtimeEnabled = o.optBoolean("bedtimeEnabled", true),
                bedtimeStartMin = o.optInt("bedtimeStartMin", 20 * 60),
                bedtimeEndMin = o.optInt("bedtimeEndMin", 6 * 60),
                bedtimeSoundEnabled = o.optBoolean("bedtimeSoundEnabled", true),
                bonusMinutes = o.optInt("bonusMinutes", 0),
                offUntilEpoch = o.optLong("offUntilEpoch", 0),
                settingsUnlockedUntil = o.optLong("settingsUnlockedUntil", 0),
                categories = cats,
                focus = FocusSession.fromJson(o.optJSONObject("focus")),
                chores = Chore.listFromJson(o.optJSONArray("chores")),
                usageMode = o.optString("usageMode", "CATEGORIES"),
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
        put("focusLabel", focusLabel)
        put("batteryPercent", batteryPercent)
        put("bedtimeActive", bedtimeActive)
        put("deviceName", deviceName)
        put("updatedAt", updatedAt)
        put("perAppSeconds", JSONObject().also { a -> perAppSeconds.forEach { (k, v) -> a.put(k, v) } })
        put("perAppLabels", JSONObject().also { a -> perAppLabels.forEach { (k, v) -> a.put(k, v) } })
        put("blockedToday", org.json.JSONArray(blockedToday))
    }

    companion object {
        fun fromJson(o: JSONObject): ChildStatus {
            val usage = HashMap<String, Int>()
            o.optJSONObject("perAppSeconds")?.let { a -> a.keys().forEach { usage[it] = a.optInt(it) } }
            val labels = HashMap<String, String>()
            o.optJSONObject("perAppLabels")?.let { a -> a.keys().forEach { labels[it] = a.optString(it) } }
            val blocked = ArrayList<String>()
            o.optJSONArray("blockedToday")?.let { arr ->
                for (i in 0 until arr.length()) blocked.add(arr.optString(i))
            }
            return ChildStatus(
                globalUsedSeconds = o.optInt("globalUsedSeconds", 0),
                totalDeviceSeconds = o.optInt("totalDeviceSeconds", 0),
                limitSeconds = o.optInt("limitSeconds", 0),
                bonusSeconds = o.optInt("bonusSeconds", 0),
                focusLabel = o.optString("focusLabel", ""),
                batteryPercent = o.optInt("batteryPercent", -1),
                perAppSeconds = usage,
                perAppLabels = labels,
                blockedToday = blocked,
                bedtimeActive = o.optBoolean("bedtimeActive", false),
                deviceName = o.optString("deviceName", "Kindergerät"),
                updatedAt = o.optLong("updatedAt", 0)
            )
        }
    }
}
