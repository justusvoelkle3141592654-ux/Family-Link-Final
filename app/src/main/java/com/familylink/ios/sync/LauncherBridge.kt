package com.familylink.ios.sync

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.familylink.ios.data.AppCategory
import com.familylink.ios.data.LimitEngine
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
        // While anything seals the phone, every app is off limits — the launcher then shows an
        // empty grid with the reason rather than a wall of icons that all refuse to open.
        val sealed = LockState.lockActive || prefs.isBedtime() || prefs.manualLockEnabled ||
            prefs.screenLockActive()

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

        val usage = prefs.getPerAppSeconds()
        val engine = LimitEngine(prefs)
        // The day's budget being gone locks everything that is not a Plus app, so it is asked
        // once here rather than per package.
        val budgetGone = runCatching { engine.sealedReason(usage) != null }.getOrDefault(false)

        for ((pkg, entry) in prefs.getCategories()) {
            val category = entry.first
            val locked = when {
                category == AppCategory.PLUS -> false
                category == AppCategory.BLOCKED -> true
                // A focus session narrows the phone to the apps it names, whatever their category.
                focusRunning && pkg !in focus.allowed -> true
                budgetGone -> true
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

        /** kind | value | detail */
        private val COLUMNS = arrayOf("kind", "value", "detail")
    }
}
