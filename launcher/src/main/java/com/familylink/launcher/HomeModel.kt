package com.familylink.launcher

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The home screen's layout, and the rules for editing it.
 *
 * Kept apart from the drawing so the rules stay readable: a package lives in exactly one place
 * at a time, pages never end up with a hole in the middle, and an empty trailing page is kept
 * so there is always somewhere to drag to.
 */
class HomeModel(context: Context) {

    private val prefs = LauncherPrefs(context)

    var pages by mutableStateOf(prefs.pages.ifEmpty { listOf(emptyList()) })
        private set

    var dock by mutableStateOf(prefs.dock)
        private set

    /**
     * First run: put a few obvious apps in the dock so the phone is not a blank slate.
     *
     * Only ever once — after that the layout is the child's, and re-seeding it on an update
     * would silently undo their arrangement.
     */
    fun seedIfEmpty(installed: List<AppEntry>) {
        if (prefs.seeded) return
        prefs.seeded = true
        if (dock.isNotEmpty() || pages.any { it.isNotEmpty() }) return

        val byPackage = installed.associateBy { it.packageName }
        // The four a phone is actually for. Whichever of them exists, in this order.
        val wanted = listOf(
            listOf("com.android.dialer", "com.google.android.dialer", "com.android.phone"),
            listOf("com.google.android.apps.messaging", "com.android.mms"),
            listOf("com.android.chrome", "com.google.android.googlequicksearchbox"),
            listOf("com.google.android.apps.photos", "com.android.gallery3d")
        )
        val seeded = wanted.mapNotNull { group -> group.firstOrNull { it in byPackage } }
        if (seeded.isNotEmpty()) setDock(seeded.take(LauncherPrefs.DOCK_MAX))
    }

    // ---- editing -----------------------------------------------------------

    /** Put [pkg] on [pageIndex], taking it out of wherever it was. */
    fun addToPage(pkg: String, pageIndex: Int) {
        val cleaned = removeEverywhere(pkg)
        val target = pageIndex.coerceIn(0, cleaned.first.lastIndex)
        val newPages = cleaned.first.toMutableList()
        newPages[target] = newPages[target] + pkg
        commit(newPages, cleaned.second)
    }

    /** Put [pkg] in the dock, unless it is already full. */
    fun addToDock(pkg: String): Boolean {
        val cleaned = removeEverywhere(pkg)
        if (cleaned.second.size >= LauncherPrefs.DOCK_MAX) return false
        commit(cleaned.first, cleaned.second + pkg)
        return true
    }

    /** Take [pkg] off the home screen entirely. It stays in the app list, always. */
    fun remove(pkg: String) {
        val cleaned = removeEverywhere(pkg)
        commit(cleaned.first, cleaned.second)
    }

    fun isOnHome(pkg: String): Boolean =
        pkg in dock || pages.any { pkg in it }

    private fun removeEverywhere(pkg: String): Pair<List<List<String>>, List<String>> =
        pages.map { page -> page.filterNot { it == pkg } } to dock.filterNot { it == pkg }

    /**
     * Store the layout, after tidying it: drop empty pages in the middle so icons do not sit
     * behind a blank swipe, and always leave exactly one empty page at the end as a drop target.
     */
    private fun commit(newPages: List<List<String>>, newDock: List<String>) {
        val kept = newPages.filter { it.isNotEmpty() }.toMutableList()
        kept.add(emptyList())
        pages = kept
        dock = newDock
        prefs.pages = kept
        prefs.dock = newDock
    }

    private fun setDock(list: List<String>) {
        dock = list
        prefs.dock = list
    }
}
