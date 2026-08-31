package com.familylink.launcher

import android.content.Context
import org.json.JSONArray

/**
 * The launcher's own small store: the home screen layout, and the family's Firebase details
 * once they have been inherited from the guard.
 *
 * The Firebase details are copied in rather than asked for again. Setting the same thing up
 * twice is how a pairing ends up half-wrong, and the guard already has it — the launcher takes
 * a copy the first time it can and keeps it, so a later force stop cannot take it away.
 */
class LauncherPrefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("launcher", Context.MODE_PRIVATE)

    // ---- inherited Firebase ------------------------------------------------

    var syncUrl: String
        get() = sp.getString("sync_url", "") ?: ""
        set(v) = sp.edit().putString("sync_url", v).apply()

    var familyId: String
        get() = sp.getString("family_id", "") ?: ""
        set(v) = sp.edit().putString("family_id", v).apply()

    val syncConfigured: Boolean get() = syncUrl.isNotBlank() && familyId.isNotBlank()

    /** Take a copy of the guard's pairing, once and only when it is actually new. */
    fun inheritSync(url: String, family: String) {
        if (url.isBlank() || family.isBlank()) return
        if (url == syncUrl && family == familyId) return
        sp.edit().putString("sync_url", url).putString("family_id", family).apply()
    }

    // ---- the home screen layout -------------------------------------------
    //
    // Pages and dock are plain package lists. Storing positions rather than a grid means a
    // phone rotation or a different screen size rearranges nothing: the tiles simply flow.

    /** Package names on each home page, outermost list = pages. */
    var pages: List<List<String>>
        get() = runCatching {
            val outer = JSONArray(sp.getString("pages", "[[]]") ?: "[[]]")
            (0 until outer.length()).map { i ->
                val inner = outer.getJSONArray(i)
                (0 until inner.length()).map { inner.getString(it) }
            }
        }.getOrDefault(listOf(emptyList()))
        set(v) {
            val outer = JSONArray()
            v.forEach { page -> outer.put(JSONArray().also { a -> page.forEach(a::put) }) }
            sp.edit().putString("pages", outer.toString()).apply()
        }

    /** The fixed row that stays on every page. */
    var dock: List<String>
        get() = runCatching {
            val a = JSONArray(sp.getString("dock", "[]") ?: "[]")
            (0 until a.length()).map { a.getString(it) }
        }.getOrDefault(emptyList())
        set(v) = sp.edit().putString("dock", JSONArray().also { a -> v.forEach(a::put) }.toString()).apply()

    /** True once the first run has filled the dock, so we never overwrite the child's choices. */
    var seeded: Boolean
        get() = sp.getBoolean("seeded", false)
        set(v) = sp.edit().putBoolean("seeded", v).apply()

    companion object {
        /** Beyond this the dock stops taking new icons; five is what fits on a phone. */
        const val DOCK_MAX = 5
    }
}
