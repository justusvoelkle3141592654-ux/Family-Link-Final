package com.familylink.ios.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

/** Loads the apps the parent can categorise: everything launchable, plus the keyboards. */
object InstalledApps {

    data class Entry(
        val packageName: String,
        val label: String,
        /**
         * An input method rather than an ordinary app.
         *
         * Keyboards have no launcher icon, so they used to be invisible here — and an app that
         * cannot be listed cannot be categorised or limited. A dictation keyboard could
         * therefore be used all day without ever appearing anywhere.
         *
         * Listing them is honest but limited: a keyboard never runs as a foreground app, so
         * Android attributes no time to it and a limit is meaningless. What it gives the parent
         * is the knowledge that it is installed, and a route to switching it off.
         */
        val isKeyboard: Boolean = false
    )

    fun load(context: Context): List<Entry> {
        val pm = context.packageManager
        val launchables = pm.queryIntentActivities(
            android.content.Intent(android.content.Intent.ACTION_MAIN)
                .addCategory(android.content.Intent.CATEGORY_LAUNCHER),
            0
        )
        val apps = launchables.mapNotNull { ri ->
            val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
            if (pkg == LimitEngine.OWN_PACKAGE) return@mapNotNull null
            Entry(pkg, ri.loadLabel(pm).toString())
        }
        val seen = apps.mapTo(HashSet()) { it.packageName }

        // Keyboards on top, but only those not already listed: some ship a settings activity
        // with its own icon, and those should stay ordinary apps.
        val keyboards = keyboardPackages(context)
            .filterNot { it.packageName in seen || it.packageName == LimitEngine.OWN_PACKAGE }

        return (apps + keyboards)
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    /** Every installed input method, whether or not it is currently switched on. */
    fun keyboardPackages(context: Context): List<Entry> = runCatching {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE)
            as android.view.inputmethod.InputMethodManager
        imm.inputMethodList.map { info ->
            Entry(
                packageName = info.packageName,
                label = runCatching { info.loadLabel(context.packageManager).toString() }
                    .getOrDefault(info.packageName),
                isKeyboard = true
            )
        }
    }.getOrDefault(emptyList())

    fun isSystem(info: ApplicationInfo): Boolean =
        (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0

    /** Human-readable label for a package, falling back to the package name. */
    fun labelFor(context: Context, pkg: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        pkg
    }

    /** Launch intent for a package, or null if it can't be launched. */
    fun launchIntent(context: Context, pkg: String): android.content.Intent? =
        context.packageManager.getLaunchIntentForPackage(pkg)?.apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /** App icon rasterised to a Bitmap for Compose, or null on failure. */
    fun iconBitmap(context: Context, pkg: String): android.graphics.Bitmap? = try {
        val drawable = context.packageManager.getApplicationIcon(pkg)
        val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: 108
        val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: 108
        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(canvas)
        bmp
    } catch (_: Throwable) {
        null
    }
}
