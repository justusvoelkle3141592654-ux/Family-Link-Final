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
     * Lay the chosen apps out on the pages, important ones first.
     *
     * This is what the setup wizard hands back. Choosing by tapping and letting the launcher
     * arrange them is the point: dragging seventy icons into place is work, and the order that
     * results is rarely better than "phone, messages, camera, then the rest by name".
     *
     * Apps that are not chosen are not hidden — they stay in the drawer, always. This only
     * decides what sits on the home screen.
     *
     * The dock is left as it is; [applyDock] handles that separately.
     */
    fun applySelection(packages: List<String>, byPackage: Map<String, AppEntry>) {
        val inDock = dock.toSet()
        val ordered = AppOrder.sortPackages(packages.filterNot { it in inDock }, byPackage)
        val grid = ordered.chunked(LauncherPrefs.PAGE_SLOTS).map { chunk ->
            val page = prefs.emptyPage().toMutableList()
            chunk.forEachIndexed { i, pkg -> page[i] = pkg }
            page as List<String?>
        }
        commit(grid.ifEmpty { listOf(prefs.emptyPage()) }, dock)
    }

    /** Replace the dock, and take those apps off the pages so nothing appears twice. */
    fun applyDock(packages: List<String>) {
        val newDock = packages.take(LauncherPrefs.DOCK_MAX)
        val cleaned = pages.map { page -> page.map { if (it in newDock) null else it } }
        commit(cleaned, newDock)
    }

    /**
     * Put every installed app onto the pages, important ones first, then alphabetically.
     *
     * Offered from the launcher's settings for a phone that is already set up and simply wants
     * everything within reach. Apps already in the dock are left there rather than listed twice.
     */
    fun fillWithAllApps(installed: List<AppEntry>) {
        applySelection(installed.map { it.packageName }, installed.associateBy { it.packageName })
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

    /** Everything currently on a page, so the wizard can show the layout as it stands. */
    fun homeApps(): List<String> = pages.flatten().filterNotNull()
}
