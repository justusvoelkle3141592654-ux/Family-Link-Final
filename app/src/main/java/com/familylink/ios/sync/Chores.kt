package com.familylink.ios.sync

import org.json.JSONArray
import org.json.JSONObject

/**
 * Chores: the parent defines jobs ("Zimmer aufräumen — 15 Min Bonus"), the child marks one as
 * done, the parent confirms and the bonus minutes are credited automatically.
 *
 * States: OPEN -> DONE (child claims) -> APPROVED (parent confirms, time credited)
 *                                     -> OPEN     (parent rejects, back to the list)
 */
data class Chore(
    val id: String,
    val title: String,
    val rewardMinutes: Int,
    val state: String = OPEN,
    /** Set when the child marks it done. */
    val claimedAt: Long = 0,
    val approvedAt: Long = 0,
    /** Repeating chores return to OPEN at midnight. */
    val repeating: Boolean = true
) {
    val isOpen get() = state == OPEN
    val isClaimed get() = state == DONE
    val isApproved get() = state == APPROVED

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("rewardMinutes", rewardMinutes)
        .put("state", state)
        .put("claimedAt", claimedAt)
        .put("approvedAt", approvedAt)
        .put("repeating", repeating)

    companion object {
        const val OPEN = "OPEN"
        const val DONE = "DONE"
        const val APPROVED = "APPROVED"

        fun fromJson(o: JSONObject) = Chore(
            id = o.optString("id", ""),
            title = o.optString("title", ""),
            rewardMinutes = o.optInt("rewardMinutes", 10),
            state = o.optString("state", OPEN),
            claimedAt = o.optLong("claimedAt", 0),
            approvedAt = o.optLong("approvedAt", 0),
            repeating = o.optBoolean("repeating", true)
        )

        fun listToJson(list: List<Chore>): JSONArray =
            JSONArray().also { arr -> list.forEach { arr.put(it.toJson()) } }

        fun listFromJson(arr: JSONArray?): List<Chore> {
            if (arr == null) return emptyList()
            val out = ArrayList<Chore>()
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { out.add(fromJson(it)) }
            }
            return out
        }

        fun listFromString(raw: String): List<Chore> = runCatching {
            if (raw.isBlank()) emptyList() else listFromJson(JSONArray(raw))
        }.getOrDefault(emptyList())

        /** Suggested starter chores shown when the list is empty. */
        val SUGGESTIONS = listOf(
            "Zimmer aufräumen" to 15,
            "Hausaufgaben erledigt" to 20,
            "Spülmaschine ausräumen" to 10,
            "Müll rausbringen" to 10,
            "Tisch decken" to 5,
            "Eine Stunde gelesen" to 20
        )

        fun newId(): String = java.util.UUID.randomUUID().toString().take(8)
    }
}
