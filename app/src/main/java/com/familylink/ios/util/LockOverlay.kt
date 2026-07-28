package com.familylink.ios.util

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * The lock as a real system overlay instead of an Activity.
 *
 * An Activity can always be left with HOME — that is why the block screen "could be closed the
 * whole time". A window added to the WindowManager sits above everything, the home screen
 * included, and there is no gesture that removes it. The device itself is NOT locked: the screen
 * stays on and the phone remains reachable, the overlay simply covers what is underneath.
 *
 * Requires the "display over other apps" permission. Without it the caller falls back to the
 * Activity, which is better than showing nothing.
 */
object LockOverlay {

    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null
    private val main = Handler(Looper.getMainLooper())

    /** What is currently on screen, so an unchanged state never rebuilds the window (no flicker). */
    private var shownKey: String? = null

    val isShowing: Boolean get() = view != null

    /**
     * Show (or update) the overlay. Rebuilding only happens when [key] changes, so the window is
     * created once per distinct lock state and simply stays there.
     */
    fun show(
        context: Context,
        key: String,
        content: @androidx.compose.runtime.Composable () -> Unit
    ) {
        main.post {
            if (shownKey == key && view != null) return@post
            if (view != null) removeNow(context)
            runCatching { addNow(context, content) }.onSuccess { shownKey = key }
        }
    }

    fun hide(context: Context) {
        main.post {
            shownKey = null
            removeNow(context)
        }
    }

    @SuppressLint("InflateParams")
    private fun addNow(context: Context, content: @androidx.compose.runtime.Composable () -> Unit) {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val o = OverlayOwner().also { owner = it }
        o.start()

        val cv = ComposeView(context).apply {
            setViewTreeLifecycleOwner(o)
            setViewTreeViewModelStoreOwner(o)
            setViewTreeSavedStateRegistryOwner(o)
            setContent(content)
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_SYSTEM_ALERT

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            // Focusable so BACK is delivered to us and swallowed rather than reaching whatever
            // is underneath. Not "watch outside touch": every touch belongs to the overlay.
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        )
        wm.addView(cv, params)
        view = cv
    }

    private fun removeNow(context: Context) {
        val cv = view ?: return
        view = null
        runCatching {
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeViewImmediate(cv)
        }
        owner?.stop()
        owner = null
    }

    /**
     * Compose refuses to run in a plain WindowManager view without these three owners, so the
     * overlay brings its own minimal implementations.
     */
    private class OverlayOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val controller = SavedStateRegistryController.create(this)
        private val store = ViewModelStore()

        override val lifecycle: Lifecycle get() = registry
        override val viewModelStore: ViewModelStore get() = store
        override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry

        fun start() {
            controller.performRestore(null)
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun stop() {
            registry.currentState = Lifecycle.State.DESTROYED
            store.clear()
        }
    }
}
