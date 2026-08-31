package com.familylink.launcher

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The home screen's layout, and the rules for editing it.
 *
 * Kept apart from the drawing so the rules stay readable. The one that matters most: a page is
 * a fixed set of slots, not a list. Dropping an icon puts it in the slot it was dropped on, and
 * removing one leaves that slot empty — nothing shuffles up behind it, so the arrangement stays
 * where it was put.
 */
class HomeModel(context: Context) {

    private val prefs = LauncherPrefs(context)

    var pages by mutableStateOf(prefs.pages)
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
        if (dock.isNotEmpty() || pages.any { page -> page.any { it != null } }) return

        val byPackage = installed.associateBy { it.packageName }
        // The four a phone is actually for. Whichever of them exists, in this order.
        val wanted = listOf(
            listOf("com.android.dialer", "com.google.android.dialer", "com.android.phone"),
            listOf("com.google.android.apps.messaging", "com.android.mms"),
            listOf("com.android.chrome", "com.google.android.googlequicksearchbox"),
            listOf("com.google.android.apps.photos", "com.android.gallery3d")
        )
        val seeded = wanted.mapNotNull { group -> group.firstOrNull { it in byPackage } }
        if (seeded.isNotEmpty()) storeDock(seeded.take(LauncherPrefs.DOCK_MAX))

        // Then lay out everything else. Guessing a list of package names is hopeless — the
        // phone knows what it has, and the point is a home screen that is already the child's
        // phone rather than a blank one.
        fillWithAllApps(installed)
    }

    /**
     * Put every installed app onto the pages, alphabetically, in reading order.
     *
     * Offered from the long-press menu as well as run once at setup, because the first run only
     * ever happens once and a phone that was already set up would otherwise be stuck with
     * whatever it had. Apps already in the dock are left there rather than listed twice.
     *
     * This replaces the page layout. The dock is untouched.
     */
    fun fillWithAllApps(installed: List<AppEntry>) {
        val inDock = dock.toSet()
        val ordered = installed
            .filterNot { it.packageName in inDock }
            .sortedBy { it.label.lowercase() }
            .map { it.packageName }

        val grid = ordered.chunked(LauncherPrefs.PAGE_SLOTS).map { chunk ->
            val page = prefs.emptyPage().toMutableList()
            chunk.forEachIndexed { i, pkg -> page[i] = pkg }
            page as List<String?>
        }
        commit(grid.ifEmpty { listOf(prefs.emptyPage()) }, dock)
    }

    // ---- editing -----------------------------------------------------------

    /**
     * Drop [pkg] onto [page] at [slot], taking it out of wherever it was.
     *
     * A slot that is already taken sends the newcomer to the next free one rather than
     * overwriting: losing an icon because a finger landed a few pixels off would be the worse
     * surprise.
     */
    fun dropOnPage(pkg: String, page: Int, slot: Int) {
        val (cleanPages, cleanDock) = removeEverywhere(pkg)
        val grid = cleanPages.map { it.toMutableList() }.toMutableList()
        while (grid.size <= page) grid.add(prefs.emptyPage().toMutableList())

        val target = grid[page]
        val at = if (slot in target.indices && target[slot] == null) slot
        else target.indexOfFirst { it == null }
        if (at < 0) return          // page full: the drop simply does not take
        target[at] = pkg
        commit(grid, cleanDock)
    }

    /**
     * Put [pkg] into the dock at [index], or at the end when no position is given.
     *
     * Inserting at a position rather than always appending is what lets the dock be reordered:
     * dragging an app that is already in the dock onto another of its places moves it there.
     */
    fun addToDock(pkg: String, index: Int = -1): Boolean {
        val (cleanPages, cleanDock) = removeEverywhere(pkg)
        if (cleanDock.size >= LauncherPrefs.DOCK_MAX) return false
        val at = if (index < 0) cleanDock.size else index.coerceIn(0, cleanDock.size)
        val newDock = cleanDock.toMutableList().apply { add(at, pkg) }
        commit(cleanPages.map { it.toMutableList() }, newDock)
        return true
    }

    /** Take [pkg] off the home screen entirely. It stays in the app list, always. */
    fun remove(pkg: String) {
        val (cleanPages, cleanDock) = removeEverywhere(pkg)
        commit(cleanPages.map { it.toMutableList() }, cleanDock)
    }

    fun isOnHome(pkg: String): Boolean =
        pkg in dock || pages.any { page -> page.any { it == pkg } }

    private fun removeEverywhere(pkg: String): Pair<List<List<String?>>, List<String>> =
        pages.map { page -> page.map { if (it == pkg) null else it } } to
            dock.filterNot { it == pkg }

    /**
     * Store the layout, after tidying it: drop trailing empty pages, then always leave exactly
     * one empty page at the end as somewhere to drag to. Empty pages in the middle are kept —
     * a page the child cleared on purpose should stay where it is.
     */
    private fun commit(newPages: List<List<String?>>, newDock: List<String>) {
        val kept = newPages.toMutableList()
        while (kept.size > 1 && kept.last().all { it == null }) kept.removeAt(kept.lastIndex)
        kept.add(prefs.emptyPage())
        pages = kept
        dock = newDock
        prefs.pages = kept
        prefs.dock = newDock
    }

    /** Add an empty page at the end. The commit below always leaves one spare after it. */
    fun addPage() {
        commit(pages + listOf(prefs.emptyPage()), dock)
    }

    /** Named storeDock, not setDock: that name is taken by the property's own setter. */
    private fun storeDock(list: List<String>) {
        dock = list
        prefs.dock = list
    }
}
