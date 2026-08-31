package com.familylink.launcher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/** Somewhere a held app can be let go of. */
sealed class DropTarget {
    /** A fixed place on a home page. */
    data class Slot(val page: Int, val index: Int) : DropTarget()

    /** The dock, at the position the finger is over. */
    data class Dock(val index: Int) : DropTarget()

    /** The bar at the top that takes the app off the home screen. */
    object Remove : DropTarget()
}

/**
 * What is being dragged, where the finger is, and what is underneath it.
 *
 * ## Why this exists
 *
 * The first attempt stored the drag position as it arrived from the tile's own gesture — which
 * is measured from the tile's top-left corner, not the screen's. Combined with a start value of
 * Offset.Zero it put the held icon in the top-left corner of the display and left it there.
 *
 * The deeper fault was worse: picking an app up in the drawer closed the drawer, and closing it
 * removed from the composition the very element that owned the gesture. Compose then cancels the
 * gesture without ever reporting an end, so the drop never happened and the "drag here to
 * remove" bar stayed on screen for good.
 *
 * Both are fixed by the same two decisions: every position is converted to root coordinates
 * before it is stored, and the drawer is faded rather than removed, so the gesture's owner
 * survives the whole drag. This class is only the shared state between them — deliberately
 * plain, because the bug was never in the arithmetic.
 */
class DragController {

    /** The package currently held, or null. */
    var dragging by mutableStateOf<String?>(null)
        private set

    /** Where the finger is, in root coordinates. */
    var position by mutableStateOf(Offset.Zero)
        private set

    /** True while the finger has not moved and the menu is offered instead of a drag. */
    var menuOpen by mutableStateOf(false)
        private set

    /** Where the drag started, so a small wobble is not mistaken for a move. */
    private var origin = Offset.Zero

    /** Was it picked up in the drawer? Then the drawer fades while the drag runs. */
    var fromDrawer by mutableStateOf(false)
        private set

    private val targets = LinkedHashMap<DropTarget, Rect>()

    fun start(pkg: String, at: Offset, fromDrawer: Boolean) {
        dragging = pkg
        position = at
        origin = at
        this.fromDrawer = fromDrawer
        menuOpen = true      // offered first; the first real movement takes it away
    }

    fun moveTo(at: Offset) {
        position = at
        // A finger resting on a tile jitters by a pixel or two; only a deliberate move should
        // dismiss the menu and turn this into a drag.
        if (menuOpen && (at - origin).getDistance() > MOVE_SLOP) menuOpen = false
    }

    /** Let go. Returns what it was dropped on, or null when it should snap back. */
    fun drop(): DropTarget? {
        val target = if (menuOpen) null else targetAt(position)
        clear()
        return target
    }

    fun clear() {
        dragging = null
        menuOpen = false
        fromDrawer = false
        position = Offset.Zero
        origin = Offset.Zero
    }

    /** Only the menu closes; the app stays where it was. */
    fun dismissMenu() {
        menuOpen = false
        dragging = null
    }

    // ---- drop targets ------------------------------------------------------
    //
    // Registered by the things that can receive a drop, as they are laid out. Held as plain
    // rectangles rather than as composables, so a target that scrolls out of view simply stops
    // being registered and nothing else has to know.

    fun register(target: DropTarget, bounds: Rect) {
        targets[target] = bounds
    }

    fun forgetPage(page: Int) {
        targets.keys.removeAll { it is DropTarget.Slot && it.page == page }
    }

    fun targetAt(point: Offset): DropTarget? =
        targets.entries.lastOrNull { it.value.contains(point) }?.key

    private companion object {
        /** Below this a movement is a wobble, not a drag. Roughly a fingertip's width of slop. */
        const val MOVE_SLOP = 24f
    }
}
