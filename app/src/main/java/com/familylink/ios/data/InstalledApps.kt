package com.familylink.ios.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

/** Loads the user-visible launchable apps so the parent can categorise them. */
object InstalledApps {

    data class Entry(val packageName: String, val label: String)

    fun load(context: Context): List<Entry> {
        val pm = context.packageManager
        val launchables = pm.queryIntentActivities(
            android.content.Intent(android.content.Intent.ACTION_MAIN)
                .addCategory(android.content.Intent.CATEGORY_LAUNCHER),
            0
        )
        return launchables
            .mapNotNull { ri ->
                val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == LimitEngine.OWN_PACKAGE) return@mapNotNull null
                Entry(pkg, ri.loadLabel(pm).toString())
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

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
