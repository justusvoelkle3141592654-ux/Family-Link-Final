package com.familylink.ios.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * The separate launcher app, seen from this one.
 *
 * ## Why a second app
 *
 * A restart used to be the way in: the phone's own launcher came up, and Settings was two taps
 * away before the guard had finished starting. Making this app the home screen closed that, but
 * badly — pressing home opened the parent portal, which is a settings page, not somewhere to
 * live. Worse, both halves were one process: force-stopping the guard took the home screen with
 * it and handed the phone straight back.
 *
 * The home screen is now its own app in its own process. Android keeps the home app alive by
 * definition — it is what every press of home falls back to — so it is still running when the
 * guard is stopped, notices within a second, and starts it again. Force stop stops being an
 * escape and becomes a pause.
 *
 * ## Hiding the icon
 *
 * This app's own icon is the shortcut to "App-Info", and from there to "Beenden erzwingen".
 * Removing the icon removes that shortcut. The page is still reachable the long way through
 * Settings, so it is a speed bump rather than a wall — but Settings is behind the PIN, and the
 * launcher undoes the stop anyway.
 *
 * Hiding is refused while the paired launcher is not the home screen, because then this icon
 * would be the only way back into the app.
 */
object LauncherGuard {

    /** The launcher app's package. Separate APK, same signing key. */
    const val LAUNCHER_PACKAGE = "com.familylink.launcher"

    /** The alias carrying this app's launcher icon, separate so it can be disabled alone. */
    private const val ALIAS = "com.familylink.ios.Launcher"

    private fun alias(context: Context) = ComponentName(context.packageName, ALIAS)

    /** Is the companion launcher installed at all? */
    fun isLauncherInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(LAUNCHER_PACKAGE, 0); true
    }.getOrDefault(false)

    /** Is the companion launcher the phone's current home screen? */
    fun isLauncherActive(context: Context): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val res = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        res?.activityInfo?.packageName == LAUNCHER_PACKAGE
    }.getOrDefault(false)

    /**
     * Android's own "home app" screen. There is no way to set a launcher programmatically —
     * deliberately, on Android's part — so both switching to it and away from it end here.
     */
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
     * Show or hide this app's launcher icon.
     *
     * @return false when hiding was refused because the companion launcher is not the home
     *         screen — taking the icon away then would leave no way to open the app at all.
     */
    fun setIconHidden(context: Context, hidden: Boolean): Boolean {
        if (hidden && !isLauncherActive(context)) return false
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
