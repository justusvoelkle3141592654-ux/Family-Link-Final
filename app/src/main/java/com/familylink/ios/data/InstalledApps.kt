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
}
