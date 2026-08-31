package com.familylink.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONObject
import java.util.Calendar

/**
 * Everything this app knows about the rules, from two sources that back each other up.
 *
 * The bridge is the guard answering directly: exact, current, and gone the moment someone force
 * stops it. Firebase is the family's own database, which the launcher reads on its own line —
 * slower to notice a limit running out, but still there when the guard is not.
 *
 * That second source is the point. A launcher that only asks the guard is blind exactly when it
 * matters: during bedtime, with the guard stopped, the phone would be wide open. With its own
 * connection the launcher still knows it is night, and still says no.
 */
object Guard {

    const val PACKAGE = "com.familylink.ios"

    private val BRIDGE: Uri = Uri.parse("content://com.familylink.ios.launcherbridge/state")

    data class State(
        /** Packages that must not open right now. */
        val locked: Set<String>,
        /** True when nothing at all may be used. */
        val sealed: Boolean,
        /** Short word for why, for the strip and the block screen. */
        val reason: String,
        /** Seconds of budget left today, or -1 when unknown. */
        val remainingSeconds: Int,
        /** The day's budget in seconds, or -1 when unknown. */
        val limitSeconds: Int,
        /** False when the guard did not answer: it is stopped, or gone. */
        val guardAlive: Boolean
    ) {
        companion object {
            val UNKNOWN = State(emptySet(), false, "", -1, -1, false)
        }
    }

    /** Ask the guard. Null when it did not answer at all. */
    fun readBridge(context: Context): State? {
        val locked = HashSet<String>()
        var sealed = false
        var reason = ""
        var remaining = -1
        var limit = -1
        var syncUrl = ""
        var familyId = ""
        val answered = runCatching {
            context.contentResolver.query(BRIDGE, null, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val value = c.getString(1) ?: ""
                    val detail = c.getString(2) ?: ""
                    when (c.getString(0)) {
                        "locked" -> locked.add(value)
                        "state" -> { sealed = value == "1"; reason = detail }
                        "time" -> {
                            remaining = value.toIntOrNull() ?: -1
                            limit = detail.toIntOrNull() ?: -1
                        }
                        "sync" -> { syncUrl = value; familyId = detail }
                    }
                }
                true
            } ?: false
        }.getOrDefault(false)
        if (!answered) return null

        // Take a copy of the pairing while the guard is still there to give it.
        if (syncUrl.isNotBlank()) LauncherPrefs(context).inheritSync(syncUrl, familyId)

        return State(locked, sealed, reason, remaining, limit, guardAlive = true)
    }

    /**
     * What the family's database says, used only when the guard is silent.
     *
     * Deliberately narrow: bedtime and a manual lock, the two rules that seal the whole phone
     * and that a stopped guard would otherwise simply switch off. Per-app limits are not
     * recomputed here — measuring usage is the guard's job, and guessing at it from a home
     * screen would be worse than admitting the gap.
     */
    fun readFirebase(context: Context, config: JSONObject?): State {
        config ?: return State.UNKNOWN
        val bedtime = runCatching {
            if (!config.optBoolean("bedtimeEnabled", false)) false
            else inWindow(config.optInt("bedtimeStartMin", 0), config.optInt("bedtimeEndMin", 0))
        }.getOrDefault(false)

        // Key names come from the main app's FamilyConfig; "manualLock", not "…Enabled".
        val manual = config.optBoolean("manualLock", false)
        // A timed screen lock the parent started; it carries its own end and expires by itself.
        val screenLock = config.optLong("screenLockUntil", 0L) > System.currentTimeMillis()
        val sealed = bedtime || manual || screenLock
        val reason = when {
            bedtime -> "Ruhezeit"
            manual -> config.optString("manualLockReason", "").ifBlank { "Gesperrt" }
            screenLock -> "Gesperrt"
            else -> ""
        }
        return State(emptySet(), sealed, reason, -1, -1, guardAlive = false)
    }

    /** Is the clock inside a window that may wrap past midnight? */
    private fun inWindow(startMin: Int, endMin: Int): Boolean {
        val c = Calendar.getInstance()
        val now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        return if (startMin <= endMin) now in startMin until endMin
        else now >= startMin || now < endMin
    }

    /**
     * Wake the guard.
     *
     * FLAG_INCLUDE_STOPPED_PACKAGES is the whole point: Android refuses to deliver anything to a
     * force-stopped package without it, and that is precisely the state being undone.
     */
    fun revive(context: Context) {
        runCatching {
            context.sendBroadcast(
                Intent("com.familylink.ios.RESTART")
                    .setPackage(PACKAGE)
                    .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            )
        }
    }

    /** Show the guard's own block screen for a package the child just tried to open. */
    fun showBlocked(context: Context): Boolean = runCatching {
        context.startActivity(
            Intent().setClassName(PACKAGE, "$PACKAGE.BlockActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    }.getOrDefault(false)

    /** The parent portal — and with it the PIN-protected way out of this launcher. */
    fun openPortal(context: Context) {
        runCatching {
            val i = context.packageManager.getLaunchIntentForPackage(PACKAGE)
                ?: Intent().setClassName(PACKAGE, "$PACKAGE.MainActivity")
            context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
