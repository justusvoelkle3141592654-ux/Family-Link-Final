package com.familylink.ios.util

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.familylink.ios.service.AppAccessibilityService

/**
 * Closes the app that just ran into a limit — for real, not by covering it.
 *
 * Suspending the package (device owner) is the strongest form of this, but a normally
 * installed app is not device owner, and until now that meant a blocked app simply kept
 * running behind the block screen: the video went on playing, YouTube dropped into
 * picture-in-picture, and pressing HOME put the child straight back into it.
 *
 * So the app is closed with everything that is available, in order of reliability:
 *
 *  1. leave it — the accessibility service performs HOME, which is instantaneous and needs no
 *     special powers beyond the service the setup already asks for,
 *  2. fall back to starting the home screen ourselves when that service is off,
 *  3. stop what is left running in the background, which is what ends a picture-in-picture
 *     window and any playback that survived the switch to the home screen.
 *
 * Nothing is uninstalled, cleared or otherwise changed: the app is closed exactly as the child
 * closing it by hand would close it, and it starts again normally once the limit is lifted.
 */
object AppCloser {

    /** Never act more than once per package inside this window (the monitor ticks faster). */
    private const val REPEAT_MS = 800L

    @Volatile private var lastPackage: String? = null
    @Volatile private var lastActionAt = 0L

    fun close(context: Context, pkg: String) {
        val now = SystemClock.uptimeMillis()
        if (pkg == lastPackage && now - lastActionAt < REPEAT_MS) return
        lastPackage = pkg
        lastActionAt = now

        // 1 + 2 — get out of the app.
        goHome(context)

        // 3 — and stop it from carrying on in the background.
        runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.killBackgroundProcesses(pkg)
        }
    }

    /** Forget the debounce, so the next block reacts immediately. */
    fun reset() {
        lastPackage = null
        lastActionAt = 0L
    }

    /** Leave whatever is in front for the home screen. */
    fun goHome(context: Context) {
        if (AppAccessibilityService.goHome()) return
        goHomeByIntent(context)
    }

    private fun goHomeByIntent(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        }
    }
}
