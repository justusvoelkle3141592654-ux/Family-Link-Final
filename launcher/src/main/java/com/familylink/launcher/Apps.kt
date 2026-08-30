package com.familylink.launcher

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

/** One entry in the grid. */
data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: Bitmap?
)

object Apps {

    /**
     * Everything with a launcher entry, sorted by name.
     *
     * Icons are rasterised once here rather than on every recomposition: an adaptive icon is a
     * layered drawable, and drawing a hundred of them per frame is what makes a home-grown
     * launcher feel slow next to the phone's own.
     */
    fun load(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return runCatching {
            pm.queryIntentActivities(main, 0)
                .asSequence()
                // This launcher is the home screen; offering itself is noise.
                .filter { it.activityInfo.packageName != context.packageName }
                .map { info ->
                    AppEntry(
                        packageName = info.activityInfo.packageName,
                        label = runCatching { info.loadLabel(pm).toString() }
                            .getOrDefault(info.activityInfo.packageName),
                        icon = runCatching { info.loadIcon(pm).toBitmap() }.getOrNull()
                    )
                }
                // A package can expose several launcher entries; one tile each is enough.
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
                .toList()
        }.getOrDefault(emptyList())
    }

    fun launch(context: Context, pkg: String) {
        runCatching {
            context.packageManager.getLaunchIntentForPackage(pkg)?.let {
                context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    private fun Drawable.toBitmap(size: Int = 144): Bitmap {
        if (this is BitmapDrawable && bitmap != null) {
            return Bitmap.createScaledBitmap(bitmap, size, size, true)
        }
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        setBounds(0, 0, size, size)
        draw(canvas)
        return bmp
    }
}
