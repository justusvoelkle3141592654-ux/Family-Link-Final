package com.familylink.ios.util

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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

/** Everything the lock screen needs to draw itself. */
data class LockUi(
    val title: String,
    val detail: String,
    val bedtime: Boolean = false,
    val offline: Boolean = false,
    val repair: Boolean = false
)

/**
 * The lock as a real system overlay instead of an Activity.
 *
 * An Activity can always be left with HOME — that is why the block screen "could be closed the
 * whole time". A window added to the WindowManager sits above everything, the home screen
 * included, and there is no gesture that removes it. The device itself is NOT locked: the screen
 * stays on and the phone remains reachable, the overlay simply covers what is underneath.
 *
 * ## The window outlives its content
 *
 * The first version rebuilt the whole window whenever the text changed. With a countdown in that
 * text it therefore tore the window down and built it again **every second** — which is why the
 * lock flickered, arrived late, or never appeared at all. The window is now created once and
 * kept; only [state] changes, and Compose redraws inside the window that is already there.
 *
 * Requires the "display over other apps" permission. Without it the caller falls back to the
 * notification, which is better than showing nothing.
 */
object LockOverlay {

    private var view: View? = null
    private var owner: OverlayOwner? = null
    private val main = Handler(Looper.getMainLooper())

    /** What the window draws. Changing this never touches the window itself. */
    private val state: MutableState<LockUi?> = mutableStateOf(null)

    /** What the caller last asked for, so the watchdog knows whether a window is owed at all. */
    @Volatile
    private var wanted: LockUi? = null

    val isShowing: Boolean get() = view != null

    /**
     * True when the last attempt to build the window failed. Adding a window can be refused for
     * reasons the permission check cannot see — an OEM that gates overlays separately, a
     * revocation between the check and the call. Silently swallowed, that left the phone
     * enforcing a lock nobody could see; the caller now gets to fall back to something that does
     * reach the screen.
     */
    @Volatile
    var lastShowFailed: Boolean = false
        private set

    /**
     * Put [ui] on screen, creating the window only if there is not one already.
     *
     * Safe to call as often as anything likes — on every tick, on every foreground change. When
     * nothing changed it does nothing at all, which is what makes it cheap enough to call from
     * the accessibility service's event handler.
     */
    fun update(context: Context, ui: LockUi, content: @androidx.compose.runtime.Composable () -> Unit) {
        wanted = ui
        main.post {
            state.value = ui
            if (view != null && view?.isAttachedToWindow == true) {
                lastShowFailed = false
                return@post
            }
            // Either nothing was ever built, or the system tore it down under us.
            if (view != null) removeNow(context)
            runCatching { addNow(context, content) }
                .onSuccess { lastShowFailed = false }
                .onFailure { lastShowFailed = true }
        }
    }

    /**
     * Re-assert the window if it should be up but is not. The system can remove an overlay on a
     * configuration change or after the process was killed, and without this the lock would
     * quietly be gone until the reason itself changed.
     */
    fun ensureAttached(context: Context, content: @androidx.compose.runtime.Composable () -> Unit) {
        val ui = wanted ?: return
        if (view?.isAttachedToWindow == true) return
        update(context, ui, content)
    }

    fun hide(context: Context) {
        wanted = null
        main.post {
            state.value = null
            removeNow(context)
        }
    }

    /** The state the window's content reads. Public so the content composable can observe it. */
    @androidx.compose.runtime.Composable
    fun currentUi(): LockUi? {
        val ui by state
        return ui
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

        // A host that eats BACK. The window is focusable, so the key arrives here first and
        // swallowing it means BACK does nothing rather than reaching whatever is behind.
        // ComposeView itself is final, hence the wrapper rather than a subclass.
        val host = object : FrameLayout(context) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean =
                if (event.keyCode == KeyEvent.KEYCODE_BACK) true
                else super.dispatchKeyEvent(event)
        }.apply {
            setViewTreeLifecycleOwner(o)
            setViewTreeViewModelStoreOwner(o)
            setViewTreeSavedStateRegistryOwner(o)
            addView(cv)
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_SYSTEM_ALERT

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            // LAYOUT_IN_SCREEN + LAYOUT_NO_LIMITS make the window cover the ENTIRE display,
            // including the strips behind the status and navigation bars. Without them the
            // overlay stopped short of both, which left the child a visible, tappable way out.
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE
        )
        // Draw into the display cutout as well, so there is no uncovered notch area.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        wm.addView(host, params)
        view = host
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
