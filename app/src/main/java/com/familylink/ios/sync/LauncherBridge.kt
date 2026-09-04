package com.familylink.ios.sync

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.familylink.ios.data.AppCategory
import com.familylink.ios.data.LimitEngine
import com.familylink.ios.data.LockDecision
import com.familylink.ios.data.Prefs
import com.familylink.ios.util.LockState

/**
 * What the separate launcher app is allowed to ask this one.
 *
 * The launcher runs in its own process so that force-stopping the guard cannot take the home
 * screen down with it — but that separation means it cannot read this app's preferences
 * directly. This provider is the whole of the channel between them, and it is deliberately
 * one-way and read-only: the launcher asks what is locked, and can do nothing else.
 *
 * Guarded by a signature-level permission, so only an app signed with the same key can read it.
 * That is the reason both modules share one keystore.
 */
class LauncherBridge : ContentProvider() {

    override fun onCreate(): Boolean = true

    /**
     * One row per package the launcher must not offer, plus a single-column row carrying the
     * overall state. Kept as a plain cursor rather than anything cleverer because the launcher
     * reads it on every resume and a cursor costs nothing to build.
     */
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val context = context ?: return MatrixCursor(COLUMNS)
        val cursor = MatrixCursor(COLUMNS)
        val prefs = runCatching { Prefs.get(context) }.getOrNull() ?: return cursor

        // A parent's phone is never restricted by its own launcher.
        if (prefs.isParentDevice || !prefs.setupDone) return cursor

        return runCatching { fill(cursor, prefs) }.getOrDefault(cursor)
    }

    private fun fill(cursor: MatrixCursor, prefs: Prefs): MatrixCursor {
        val focus = prefs.effectiveFocusSession()
        val focusRunning = focus.isRunning()
        val usage = prefs.getPerAppSeconds()
        val engine = LimitEngine(prefs)

        // What actually seals the phone: bedtime, a manual lock, the absolute ceiling, the
        // offline lock, a focus session that allows nothing, a display lock.
        //
        // This used to read LockState.lockActive, and that was the bug behind "the Plus apps
        // are gone when the day limit is reached". lockActive says whether the app in the
        // FOREGROUND is blocked — one app's truth. The day limit blocks the app the child was
        // in, lockActive went true, the launcher read it as "the phone is sealed" and put up
        // the reason screen instead of the grid. The apps that are supposed to survive the day
        // limit — school, music, everything on Plus — became unreachable at exactly the moment
        // they matter, and the day limit was the only lock where that could happen.
        //
        // The day's budget running out is deliberately NOT a seal: the home screen stays, the
        // Plus apps open, and everything else is marked locked below.
        val sealedReason = runCatching { engine.sealedReason(usage) }.getOrNull()
        val sealed = sealedReason != null || prefs.screenLockActive()

        cursor.addRow(arrayOf(ROW_STATE, if (sealed) "1" else "0", stateReason(prefs, focusRunning)))

        // What the launcher puts in its top strip, so the child sees where they stand without
        // opening anything.
        val limit = prefs.globalLimitMinutes * 60 + prefs.bonusSecondsToday
        val remaining = (limit - prefs.globalUsedSeconds).coerceAtLeast(0)
        cursor.addRow(arrayOf(ROW_TIME, remaining.toString(), limit.toString()))

        // The family's own Firebase, handed over once so the launcher can hold its own live
        // connection. Without it the launcher only knows what this app tells it — and this app
        // is exactly what stops answering when someone force-stops it.
        if (prefs.syncConfigured) {
            cursor.addRow(arrayOf(ROW_SYNC, prefs.syncUrl, prefs.familyId))
        }

        // Is an ordinary app allowed right now? Asked once, of the same decide() the guard
        // enforces, using a package name no category knows — an unclassified app is treated as
        // STANDARD, which is exactly the question.
        //
        // This covers the day's budget being gone and school hours alike: both leave the phone
        // usable and narrow it to the Plus apps. It replaces asking sealedReason(), which never
        // returns either of them, so before this the day limit reached the launcher only
        // through the seal above — as "the phone is locked", which it is not.
        val probe = runCatching { engine.decide(BUDGET_PROBE, usage) }
            .getOrDefault(LockDecision.Allowed)
        val plusOnly = probe !is LockDecision.Allowed
        val plusOnlyReason = when (probe) {
            is LockDecision.SchoolTime -> "Schulzeit"
            is LockDecision.GlobalLimitReached -> "Zeit ist um"
            else -> ""
        }

        // Said outright rather than left to be inferred from the rows below. The rows can only
        // name apps that have a category; an app nobody ever sorted has none, so under the day
        // limit the launcher would have offered exactly the apps it knows least about. One flag
        // plus the Plus list lets it apply the same rule the guard does: everything that is not
        // Plus, known or not.
        cursor.addRow(arrayOf(ROW_BUDGET, if (plusOnly) "1" else "0", plusOnlyReason))

        // The apps that open whatever the clock says, sent alongside the Plus ones because the
        // launcher applies the rule itself and would otherwise grey out the dialler the moment
        // the budget ran out — while this app would have opened it without hesitating.
        LimitEngine.ALWAYS_OPEN.forEach { cursor.addRow(arrayOf(ROW_PLUS, it, "")) }

        for ((pkg, entry) in prefs.getCategories()) {
            val category = entry.first
            if (category == AppCategory.PLUS) cursor.addRow(arrayOf(ROW_PLUS, pkg, ""))
            val locked = when {
                category == AppCategory.PLUS -> false
                pkg in LimitEngine.ALWAYS_OPEN -> false
                category == AppCategory.BLOCKED -> true
                // A focus session narrows the phone to the apps it names, whatever their category.
                focusRunning && pkg !in focus.allowed -> true
                plusOnly -> true
                category == AppCategory.LIMIT ->
                    (usage[pkg] ?: 0) >= entry.second * 60
                else -> false
            }
            if (locked) cursor.addRow(arrayOf(ROW_LOCKED, pkg, ""))
        }
        return cursor
    }

    private fun stateReason(prefs: Prefs, focusRunning: Boolean): String = when {
        prefs.isBedtime() -> "Ruhezeit"
        prefs.manualLockEnabled -> "Gesperrt"
        prefs.screenLockActive() -> "Display gesperrt"
        focusRunning -> "Fokus"
        LockState.lockActive -> "Zeit ist um"
        else -> ""
    }

    // Read-only by design: nothing the launcher does may change what is enforced.
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, s: String?, a: Array<out String>?) = 0
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun getType(uri: Uri): String? = null

    companion object {
        const val AUTHORITY = "com.familylink.ios.launcherbridge"
        val URI: Uri = Uri.parse("content://$AUTHORITY/state")

        const val ROW_STATE = "state"
        const val ROW_LOCKED = "locked"
        const val ROW_TIME = "time"
        const val ROW_SYNC = "sync"

        /**
         * "1" when only the Plus apps may be opened — today's budget is gone, or class is in
         * session. The phone itself is not locked: the home screen stays, and so do those apps.
         * The detail column carries the short reason for the strip.
         */
        const val ROW_BUDGET = "budget"

        /** One row per app on Plus — the apps that survive the day limit. */
        const val ROW_PLUS = "plus"

        /**
         * A package name that belongs to no category, used only to ask the engine "is the day's
         * budget gone?" without naming a real app. An unknown package is treated as STANDARD,
         * which is exactly the question being asked.
         */
        private const val BUDGET_PROBE = "com.familylink.probe.budget"

        /** kind | value | detail */
        private val COLUMNS = arrayOf("kind", "value", "detail")
    }
}
