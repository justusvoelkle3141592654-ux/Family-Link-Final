package com.familylink.launcher

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.provider.Settings
import android.view.inputmethod.InputMethodManager

/** One entry in the grid. */
data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: Bitmap?,
    /**
     * An installed keyboard with no launcher icon.
     *
     * These used to be invisible everywhere, which is how a dictation keyboard could be in
     * daily use and appear in no list at all. It cannot be started like an app, so tapping one
     * opens Android's keyboard settings instead.
     */
    val isKeyboard: Boolean = false
)

/**
 * The order apps are laid out in: the ones a phone is actually for first, then the rest by name.
 *
 * Purely alphabetical put "Adobe Acrobat" before the dialler, which is a poor first screen. The
 * groups below are matched by package name because labels differ per language and per vendor;
 * each group lists the usual candidates and only the installed one is used.
 */
object AppOrder {

    private val PRIORITY: List<List<String>> = listOf(
        listOf("com.android.dialer", "com.google.android.dialer", "com.android.phone", "com.android.contacts"),
        listOf("com.google.android.apps.messaging", "com.android.mms", "com.android.messaging"),
        listOf("com.whatsapp", "com.whatsapp.w4b"),
        listOf("com.android.camera", "com.android.camera2", "com.google.android.GoogleCamera", "com.nothing.camera"),
        listOf("com.android.chrome", "org.mozilla.firefox", "com.google.android.googlequicksearchbox"),
        listOf("com.google.android.apps.photos", "com.android.gallery3d", "com.nothing.gallery"),
        listOf("com.google.android.deskclock", "com.android.deskclock", "com.nothing.clock"),
        listOf("com.google.android.calendar", "com.android.calendar"),
        listOf("com.spotify.music", "com.google.android.youtube", "com.google.android.apps.youtube.music"),
        listOf("com.google.android.apps.maps")
    )

    private fun rank(pkg: String): Int {
        val i = PRIORITY.indexOfFirst { pkg in it }
        return if (i < 0) PRIORITY.size else i
    }

    /** Important first, everything else alphabetically. Keyboards last: they cannot be started. */
    fun sort(entries: List<AppEntry>): List<AppEntry> =
        entries.sortedWith(
            compareBy({ if (it.isKeyboard) 1 else 0 }, { rank(it.packageName) }, { it.label.lowercase() })
        )

    /** The same order for a list of package names, when the entries are looked up by package. */
    fun sortPackages(packages: List<String>, byPackage: Map<String, AppEntry>): List<String> =
        sort(packages.mapNotNull { byPackage[it] }).map { it.packageName }
}

object Apps {

    /**
     * Everything with a launcher entry, plus installed keyboards that have none, sorted by name.
     *
     * Icons are rasterised once here rather than on every recomposition: an adaptive icon is a
     * layered drawable, and drawing a hundred of them per frame is what makes a home-grown
     * launcher feel slow next to the phone's own.
     */
    fun load(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val launchable = runCatching {
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
                .toList()
        }.getOrDefault(emptyList())

        val seen = launchable.mapTo(HashSet()) { it.packageName }
        val keyboards = runCatching {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.inputMethodList
                .filterNot { it.packageName in seen || it.packageName == context.packageName }
                .map { info ->
                    AppEntry(
                        packageName = info.packageName,
                        label = runCatching { info.loadLabel(pm).toString() }
                            .getOrDefault(info.packageName),
                        icon = runCatching { pm.getApplicationIcon(info.packageName).toBitmap() }
                            .getOrNull(),
                        isKeyboard = true
                    )
                }
                .distinctBy { it.packageName }
        }.getOrDefault(emptyList())

        return (launchable + keyboards).sortedBy { it.label.lowercase() }
    }

    /**
     * Start an app, and remember that it was started.
     *
     * A keyboard has nothing to start, so it opens the keyboard settings — the only place it
     * can actually be acted on.
     */
    fun launch(context: Context, entry: AppEntry) {
        if (entry.isKeyboard) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            return
        }
        runCatching {
            context.packageManager.getLaunchIntentForPackage(entry.packageName)?.let {
                LauncherPrefs(context).noteLaunched(entry.packageName)
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
