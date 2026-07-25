package com.familylink.ios.sync

import org.json.JSONObject

/**
 * Live time requests: the child asks for extra minutes with a reason, the parent sees it
 * appear in the portal within a second and approves or declines with one tap. The grant
 * flows straight back down through the normal config sync.
 */
data class TimeRequest(
    val minutes: Int,
    val reason: String,
    val createdAt: Long = System.currentTimeMillis(),
    /** PENDING | APPROVED | DECLINED */
    val state: String = PENDING,
    val decidedAt: Long = 0
) {
    fun toJson(): JSONObject = JSONObject()
        .put("minutes", minutes)
        .put("reason", reason)
        .put("createdAt", createdAt)
        .put("state", state)
        .put("decidedAt", decidedAt)

    val isPending: Boolean get() = state == PENDING

    companion object {
        const val PENDING = "PENDING"
        const val APPROVED = "APPROVED"
        const val DECLINED = "DECLINED"

        fun fromJson(o: JSONObject) = TimeRequest(
            minutes = o.optInt("minutes", 0),
            reason = o.optString("reason", ""),
            createdAt = o.optLong("createdAt", 0),
            state = o.optString("state", PENDING),
            decidedAt = o.optLong("decidedAt", 0)
        )
    }
}

/**
 * FOCUS MODE — the headline feature.
 *
 * The parent starts a focus session from their phone (homework, dinner, bedtime routine).
 * The child's device instantly allows only the apps explicitly marked as focus-friendly;
 * everything else is blocked for the duration, with a live countdown on the child screen.
 * It ends automatically — no need to remember to switch it back off.
 */
data class FocusSession(
    val active: Boolean,
    val endsAt: Long,
    val label: String,
    /** Packages that stay usable during the session. */
    val allowed: List<String>
) {
    fun isRunning(now: Long = System.currentTimeMillis()) = active && now < endsAt

    fun remainingSeconds(now: Long = System.currentTimeMillis()): Int =
        ((endsAt - now) / 1000L).toInt().coerceAtLeast(0)

    fun toJson(): JSONObject = JSONObject()
        .put("active", active)
        .put("endsAt", endsAt)
        .put("label", label)
        .put("allowed", org.json.JSONArray(allowed))

    companion object {
        val OFF = FocusSession(false, 0, "", emptyList())

        fun fromJson(o: JSONObject?): FocusSession {
            if (o == null) return OFF
            val allowed = ArrayList<String>()
            o.optJSONArray("allowed")?.let { arr ->
                for (i in 0 until arr.length()) allowed.add(arr.optString(i))
            }
            return FocusSession(
                active = o.optBoolean("active", false),
                endsAt = o.optLong("endsAt", 0),
                label = o.optString("label", "Fokus"),
                allowed = allowed
            )
        }

        /** Preset session lengths offered in the parent portal. */
        val PRESETS = listOf(
            "Hausaufgaben" to 45,
            "Essenszeit" to 30,
            "Lernen" to 60,
            "Auszeit" to 15
        )
    }
}
