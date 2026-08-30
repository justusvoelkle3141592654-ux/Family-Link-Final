package com.familylink.ios.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Two protections that need no device owner, and that between them close the two fastest
 * routes around the guard on a stock phone.
 *
 * ## Being the home screen
 *
 * A restart used to be the way in: the launcher came up, and Settings was two taps away before
 * the guard had finished starting. As the home app there is no such moment — the phone boots
 * into this app, and every press of the home button comes back to it. Android starts the home
 * app before anything else and restarts it immediately if it dies, which is a far stronger
 * guarantee than a boot broadcast racing a child's thumb.
 *
 * ## Hiding the icon
 *
 * The app's own icon is the shortcut to "App-Info", and from there to "Beenden erzwingen",
 * which kills the guard outright — nothing an app can prevent. Removing the icon removes that
 * shortcut. The page is still reachable the long way through Settings, so this is a speed bump
 * rather than a wall, but it is the difference between a long-press and a hunt.
 *
 * Hiding is refused unless this app is actually the home screen, because otherwise it would
 * strand the parent: no icon and no home screen means no way back in.
 */
object LauncherGuard {

    /** The alias carrying the launcher icon. Separate from the activity so it can be disabled. */
    private const val ALIAS = "com.familylink.ios.Launcher"

    private fun alias(context: Context) = ComponentName(context.packageName, ALIAS)

    /** True when this app is the phone's current home screen. */
    fun isDefaultHome(context: Context): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val res = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        res?.activityInfo?.packageName == context.packageName
    }.getOrDefault(false)

    /** Android's own "choose a home app" screen; there is no way to set it programmatically. */
    fun openHomeChooser(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun isIconHidden(context: Context): Boolean = runCatching {
        context.packageManager.getComponentEnabledSetting(alias(context)) ==
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }.getOrDefault(false)

    /**
     * Show or hide the launcher icon.
     *
     * @return false when hiding was refused because this app is not the home screen — taking
     *         the icon away then would leave no way to open the app at all.
     */
    fun setIconHidden(context: Context, hidden: Boolean): Boolean {
        if (hidden && !isDefaultHome(context)) return false
        return runCatching {
            context.packageManager.setComponentEnabledSetting(
                alias(context),
                if (hidden) PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                else PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            true
        }.getOrDefault(false)
    }
}
