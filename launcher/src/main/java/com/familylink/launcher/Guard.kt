package com.familylink.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Everything this app knows about the app that does the supervising.
 *
 * The two run in separate processes on purpose. Force-stopping the guard used to end the story:
 * nothing was left running to notice. Now the home screen is a different process that Android
 * keeps alive by definition — it is what the phone falls back to on every press of home — so it
 * can see the guard is gone and wake it up again.
 */
object Guard {

    const val PACKAGE = "com.familylink.ios"

    private val BRIDGE: Uri = Uri.parse("content://com.familylink.ios.launcherbridge/state")

    /** What the guard says about the phone right now. */
    data class State(
        /** Packages the launcher must not offer. */
        val locked: Set<String>,
        /** True when everything is sealed — bedtime, a manual lock, the day's budget. */
        val sealed: Boolean,
        /** Short word for why, for the empty grid. */
        val reason: String,
        /** False when the guard could not be reached at all: it is stopped or uninstalled. */
        val reachable: Boolean
    )

    /**
     * Ask the guard what is locked.
     *
     * A failure here is the interesting case rather than an error to swallow: the provider is
     * unreachable exactly when the guard has been force-stopped or removed, which is the thing
     * the launcher exists to notice.
     */
    fun read(context: Context): State {
        val locked = HashSet<String>()
        var sealed = false
        var reason = ""
        val ok = runCatching {
            context.contentResolver.query(BRIDGE, null, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    when (c.getString(0)) {
                        "locked" -> locked.add(c.getString(1))
                        "state" -> {
                            sealed = c.getString(1) == "1"
                            reason = c.getString(2) ?: ""
                        }
                    }
                }
                true
            } ?: false
        }.getOrDefault(false)
        return State(locked, sealed, reason, ok)
    }

    /**
     * Wake the guard.
     *
     * FLAG_INCLUDE_STOPPED_PACKAGES is the point of the whole call: without it Android refuses
     * to deliver anything to a package that was force-stopped, which is precisely the state we
     * are trying to undo. Sent on every resume, so returning to the home screen is enough to
     * bring the protection back — the child cannot leave it off and simply carry on.
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

    /** Open the supervising app itself — the parent portal, and the way out of this launcher. */
    fun openPortal(context: Context) {
        runCatching {
            val i = context.packageManager.getLaunchIntentForPackage(PACKAGE)
                ?: Intent().setClassName(PACKAGE, "$PACKAGE.MainActivity")
            context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun isInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(PACKAGE, 0); true
    }.getOrDefault(false)
}
