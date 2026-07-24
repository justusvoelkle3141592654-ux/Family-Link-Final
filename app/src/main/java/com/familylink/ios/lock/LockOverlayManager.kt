package com.familylink.ios.lock

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.familylink.ios.data.LockDecision
import com.familylink.ios.ui.screens.LockScreen

/**
 * Owns the full-screen, escape-proof lock overlay drawn with
 * [WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY].
 *
 * Escape-proofing:
 *  - fills the whole screen incl. status/nav bars (LAYOUT_IN_SCREEN | fullscreen flags)
 *  - consumes BACK / HOME-adjacent keys it can (BACK, MENU); HOME itself is caught by the
 *    accessibility service which re-shows the overlay if the child leaves.
 *  - marked not focusable-for-touch-outside so nothing behind it receives input.
 */
object LockOverlayManager {

    private var view: View? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private val decisionState = mutableStateOf<LockDecision>(LockDecision.Allowed)

    val isShowing: Boolean get() = view != null

    fun canDrawOverlays(context: Context): Boolean =
        Settings.canDrawOverlays(context)

    @SuppressLint("InflateParams")
    fun show(context: Context, decision: LockDecision) {
        decisionState.value = decision
        if (view != null) return // already up; content updates reactively
        if (!canDrawOverlays(context)) return

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val owner = OverlayLifecycleOwner().apply { onCreate(); onResume() }

        val compose = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            // Swallow BACK/MENU so they can't dismiss the lock.
            isFocusableInTouchMode = true
            setOnKeyListener { _, keyCode, _ ->
                keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MENU
            }
            setContent {
                LockScreen(decision = decisionState.value)
            }
        }

        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        var flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            flags,
            PixelFormat.OPAQUE
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }

        try {
            wm.addView(compose, lp)
            view = compose
            lifecycleOwner = owner
            // Hide system bars behind the overlay so the child can't pull down quick settings.
            hideSystemBars(compose)
        } catch (t: Throwable) {
            owner.onDestroy()
        }
    }

    fun update(decision: LockDecision) {
        decisionState.value = decision
    }

    fun hide(context: Context) {
        val v = view ?: return
        try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeView(v)
        } catch (_: Throwable) {
        } finally {
            lifecycleOwner?.onDestroy()
            lifecycleOwner = null
            view = null
        }
    }

    private fun hideSystemBars(v: View) {
        @Suppress("DEPRECATION")
        v.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
    }
}
