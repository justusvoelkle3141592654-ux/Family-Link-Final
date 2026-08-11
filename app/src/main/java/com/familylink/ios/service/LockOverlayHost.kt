package com.familylink.ios.service

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.familylink.ios.ui.screens.LockOverlayContent
import com.familylink.ios.ui.screens.openParentPortal
import com.familylink.ios.util.LockOverlay

/**
 * What the overlay window actually draws.
 *
 * The window is built once and this composable stays in it for as long as the lock lasts; the
 * reason it shows is read from [LockOverlay.currentUi], so changing the text — a countdown, a
 * different lock taking over — redraws inside the existing window instead of tearing it down and
 * building a new one. That rebuild was what made the lock flicker and, whenever a countdown was
 * running, never settle on screen at all.
 */
@Composable
fun LockOverlayHost() {
    val context = LocalContext.current
    val ui = LockOverlay.currentUi() ?: return
    LockOverlayContent(
        title = ui.title,
        detail = ui.detail,
        bedtime = ui.bedtime,
        offline = ui.offline,
        repair = ui.repair,
        onOpenPortal = { openParentPortal(context) }
    )
}
