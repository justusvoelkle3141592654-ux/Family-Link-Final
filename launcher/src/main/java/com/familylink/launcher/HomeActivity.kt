package com.familylink.launcher

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The home screen.
 *
 * An ordinary launcher on the surface — swipeable pages, a fixed dock, an app drawer pulled up
 * from the bottom — with a second line of defence underneath. The deliberate parts:
 *
 *  - Every app stays in the drawer, always, keyboards included. Hiding what is locked made the
 *    phone feel broken; a faded tile with a lock says the same thing and explains itself.
 *  - Pages are fixed slots, so removing an icon leaves a gap rather than shuffling the rest.
 *  - The rules come from the guard while it answers and from the family's own database when it
 *    does not, so stopping the guard during bedtime no longer hands over a free phone.
 */
class HomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { Home() }
    }
}

@Composable
private fun Home() {
    val context = LocalContext.current
    val model = remember { HomeModel(context) }
    val prefs = remember { LauncherPrefs(context) }
    val drag = remember { DragController() }

    val apps = remember { Apps.load(context) }
    val byPackage = remember(apps) { apps.associateBy { it.packageName } }
    // The first start asks instead of guessing. Choosing apps from a list is less work than
    // dragging them into place, and the result is the one that was actually wanted.
    LaunchedEffect(Unit) {
        if (!prefs.setupDone) SetupActivity.start(context, SetupActivity.STEP_ALL)
    }

    var state by remember { mutableStateOf(Guard.State.UNKNOWN) }
    var drawerOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var spaceMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        var config: org.json.JSONObject? = null
        while (true) {
            val bridge = withContext(Dispatchers.IO) { Guard.readBridge(context) }
            state = if (bridge != null) {
                config = null
                bridge
            } else {
                Guard.revive(context)
                if (prefs.syncConfigured) {
                    if (config == null) {
                        config = withContext(Dispatchers.IO) {
                            Sync(prefs.syncUrl).get(Sync.configPath(prefs.familyId))
                        }
                    }
                    Guard.readFirebase(context, config)
                } else {
                    Guard.State.UNKNOWN
                }
            }
            delay(1000)
        }
    }

    BackHandler(enabled = true) {
        when {
            drag.dragging != null -> drag.clear()
            spaceMenu -> spaceMenu = false
            drawerOpen -> { drawerOpen = false; query = "" }
        }
    }

    if (state.sealed && !state.guardAlive) {
        SealedScreen(state.reason) { Guard.openPortal(context) }
        return
    }

    fun open(entry: AppEntry) {
        if (entry.packageName in state.locked || state.sealed) {
            if (!Guard.showBlocked(context)) Guard.openPortal(context)
        } else {
            Apps.launch(context, entry)
        }
    }

    val pagerState = rememberPagerState { model.pages.size.coerceAtLeast(1) }

    /** Let go: hand the held app to whatever is under the finger. */
    fun release() {
        val pkg = drag.dragging ?: return
        // Read before drop(), which clears the state.
        val cameFromDrawer = drag.fromDrawer
        var landed = true
        when (val target = drag.drop()) {
            is DropTarget.Slot -> model.dropOnPage(pkg, target.page, target.index)
            is DropTarget.Dock -> if (!model.addToDock(pkg, target.index)) model.remove(pkg)
            DropTarget.Remove -> model.remove(pkg)
            // The menu is showing, or it was let go over nothing: leave everything as it was.
            null -> landed = false
        }
        // An app dragged out of the drawer and put down has arrived; the drawer's job is over.
        if (landed && cameFromDrawer) { drawerOpen = false; query = "" }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            ClockHeader()
            StatusStrip(state)

            // Only while something is actually held. The old build left this on screen forever,
            // because the gesture that should have ended it was cancelled by the drawer closing.
            if (drag.dragging != null && !drag.menuOpen) RemoveTarget(drag)

            HorizontalPager(
                state = pagerState,
                userScrollEnabled = drag.dragging == null,
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(drag.dragging == null) {
                        if (drag.dragging == null) {
                            detectVerticalDragGestures { _, dy -> if (dy < -18f) drawerOpen = true }
                        }
                    }
                    // Long-press on free space: the launcher's own settings, as on any phone.
                    .pointerInput(Unit) {
                        detectTapGestures(onLongPress = { if (drag.dragging == null) spaceMenu = true })
                    }
            ) { page ->
                HomePage(
                    page = page,
                    slots = model.pages.getOrElse(page) { prefs.emptyPage() },
                    byPackage = byPackage,
                    state = state,
                    drag = drag,
                    onOpen = ::open,
                    onRelease = ::release
                )
            }

            PageDots(count = model.pages.size, current = pagerState.currentPage)

            Dock(model.dock, byPackage, state, drag, onOpen = ::open, onRelease = ::release)
            SearchRow(
                query = "",
                editable = false,
                onChange = {},
                onFocus = { drawerOpen = true },
                onPortal = { Guard.openPortal(context) }
            )
        }

        // The drawer is faded, never removed. Removing it was what cancelled the drag: a
        // composable cannot own a gesture that outlives it.
        if (drawerOpen) {
            val hidden = drag.dragging != null && drag.fromDrawer
            Box(
                Modifier
                    .fillMaxSize()
                    .alpha(if (hidden) 0f else 1f)
            ) {
                Drawer(
                    apps = apps,
                    recents = prefs.recents,
                    state = state,
                    query = query,
                    drag = drag,
                    enabled = !hidden,
                    onQuery = { query = it },
                    onOpen = { drawerOpen = false; query = ""; open(it) },
                    onRelease = ::release,
                    onPortal = { Guard.openPortal(context) }
                )
            }
        }

        // The tile that follows the finger, in root coordinates — the position the first
        // attempt never actually had.
        drag.dragging?.let { pkg ->
            if (!drag.menuOpen) {
                byPackage[pkg]?.icon?.let { icon ->
                    val density = LocalDensity.current
                    val half = with(density) { 28.dp.toPx() }
                    Image(
                        icon.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (drag.position.x - half).toInt(),
                                    (drag.position.y - half).toInt()
                                )
                            }
                            .size(56.dp)
                            .alpha(0.9f)
                    )
                }
            }
        }

        // The menu that appears when the finger is held still instead of moved.
        if (drag.menuOpen) {
            drag.dragging?.let { pkg ->
                AppMenu(
                    app = byPackage[pkg],
                    onHome = { model.dropOnPage(pkg, pagerState.currentPage, -1); drag.dismissMenu(); drawerOpen = false },
                    onDock = { model.addToDock(pkg); drag.dismissMenu(); drawerOpen = false },
                    onRemove = { model.remove(pkg); drag.dismissMenu() },
                    onInfo = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                    .setData(Uri.parse("package:$pkg"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                        drag.dismissMenu()
                    },
                    onHomeScreen = model.isOnHome(pkg),
                    onDismiss = { drag.dismissMenu() }
                )
            }
        }

        if (spaceMenu) {
            SpaceMenu(
                onWallpaper = {
                    runCatching {
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SET_WALLPAPER), "Hintergrundbild"
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                    spaceMenu = false
                },
                onAddPage = { model.addPage(); spaceMenu = false },
                onSettings = { spaceMenu = false; SettingsActivity.start(context) },
                onEditHome = {
                    spaceMenu = false
                    SetupActivity.start(context, SetupActivity.STEP_APPS)
                },
                onDismiss = { spaceMenu = false }
            )
        }
    }
}

/** Clock, date, and the weather beside it. */
@Composable
private fun ClockHeader() {
    val context = LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    var weather by remember { mutableStateOf<Weather.Now?>(null) }
    var asked by remember { mutableStateOf(false) }

    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) { while (true) { now = Date(); delay(1000) } }
    LaunchedEffect(Unit) {
        while (true) {
            weather = withContext(Dispatchers.IO) { runCatching { Weather.fetch(context) }.getOrNull() }
            delay(30 * 60 * 1000L)
        }
    }

    val clock = remember(now) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(now) }
    val date = remember(now) { SimpleDateFormat("EEEE, d. MMMM", Locale.getDefault()).format(now) }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 20.dp)
            .clickable(enabled = !Weather.hasPermission(context) && !asked) {
                asked = true
                permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(clock, color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Light)
            weather?.let { w ->
                Spacer(Modifier.width(14.dp))
                Text("${w.symbol} ${w.celsius}°", color = Color.White.copy(alpha = 0.9f), fontSize = 18.sp)
            }
        }
        Text(date, color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
    }
}

@Composable
private fun StatusStrip(state: Guard.State) {
    val text = when {
        state.sealed -> state.reason.ifBlank { "Gesperrt" }
        state.remainingSeconds < 0 -> ""
        state.remainingSeconds == 0 -> "Zeit ist aufgebraucht"
        else -> "Noch ${fmt(state.remainingSeconds)}"
    }
    if (text.isBlank()) { Spacer(Modifier.height(10.dp)); return }
    Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color(0x33000000))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

private fun fmt(seconds: Int): String {
    val m = seconds / 60
    return if (m >= 60) "${m / 60} Std ${m % 60} Min" else "$m Min"
}

/** Which page of how many. */
@Composable
private fun PageDots(count: Int, current: Int) {
    if (count <= 1) return
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(count) { i ->
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(if (i == current) 7.dp else 5.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (i == current) 0.9f else 0.35f))
            )
        }
    }
}

/** The bar that takes an app off the home screen. Registers itself as a drop target. */
@Composable
private fun RemoveTarget(drag: DragController) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 8.dp)
            .onGloballyPositioned { drag.register(DropTarget.Remove, it.boundsInRoot()) }
            .clip(RoundedCornerShape(50))
            .background(Color(0x66B3261E))
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Delete, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Zum Entfernen hierher ziehen", color = Color.White, fontSize = 13.sp)
    }
}

/**
 * One page: a fixed grid of slots.
 *
 * Every slot registers its own bounds, filled or not, so an empty place is a real target rather
 * than a gap the drop falls through.
 */
@Composable
private fun HomePage(
    page: Int,
    slots: List<String?>,
    byPackage: Map<String, AppEntry>,
    state: Guard.State,
    drag: DragController,
    onOpen: (AppEntry) -> Unit,
    onRelease: () -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(LauncherPrefs.PAGE_COLUMNS),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(slots.indices.toList(), key = { it }) { index ->
            val pkg = slots.getOrNull(index)
            val app = pkg?.let { byPackage[it] }
            Box(
                Modifier
                    .height(86.dp)
                    .onGloballyPositioned {
                        drag.register(DropTarget.Slot(page, index), it.boundsInRoot())
                    },
                contentAlignment = Alignment.TopCenter
            ) {
                if (app != null) {
                    DraggableTile(
                        app = app,
                        locked = app.packageName in state.locked || state.sealed,
                        drag = drag,
                        fromDrawer = false,
                        onClick = { onOpen(app) },
                        onRelease = onRelease
                    )
                }
            }
        }
    }
}

/** The fixed row that stays on every page. */
@Composable
private fun Dock(
    packages: List<String>,
    byPackage: Map<String, AppEntry>,
    state: Guard.State,
    drag: DragController,
    onOpen: (AppEntry) -> Unit,
    onRelease: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0x22FFFFFF))
            .padding(vertical = 10.dp)
            .height(64.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // One target per place, including one past the end, so an app can be dropped anywhere
        // along the row rather than only appended.
        val count = (packages.size + 1).coerceAtMost(LauncherPrefs.DOCK_MAX)
        repeat(count) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .onGloballyPositioned { drag.register(DropTarget.Dock(i), it.boundsInRoot()) },
                contentAlignment = Alignment.Center
            ) {
                packages.getOrNull(i)?.let { pkg ->
                    byPackage[pkg]?.let { app ->
                        DraggableTile(
                            app = app,
                            locked = pkg in state.locked || state.sealed,
                            drag = drag,
                            fromDrawer = false,
                            showLabel = false,
                            onClick = { onOpen(app) },
                            onRelease = onRelease
                        )
                    }
                }
            }
        }
    }
}

/**
 * A tile that can be picked up.
 *
 * The gesture converts every position into root coordinates before handing it on. The old build
 * passed the position as the gesture reports it — measured from this tile's own corner — which
 * is why the held icon sat in the top-left of the display instead of under the finger.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DraggableTile(
    app: AppEntry,
    locked: Boolean,
    drag: DragController,
    fromDrawer: Boolean,
    showLabel: Boolean = true,
    onClick: () -> Unit,
    onRelease: () -> Unit
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier
            .onGloballyPositioned { origin = it.boundsInRoot().topLeft }
            .pointerInput(app.packageName) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { local -> drag.start(app.packageName, origin + local, fromDrawer) },
                    onDrag = { change, _ -> drag.moveTo(origin + change.position) },
                    onDragEnd = { onRelease() },
                    onDragCancel = { onRelease() }
                )
            }
    ) {
        Tile(app, locked, showLabel, onClick)
    }
}

/** One app, drawn. Locked means faded with a lock, never removed. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Tile(
    app: AppEntry,
    locked: Boolean,
    showLabel: Boolean = true,
    onClick: () -> Unit
) {
    Column(
        Modifier.combinedClickable(onClick = onClick, onLongClick = {}).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0x22FFFFFF))
                    .alpha(if (locked) 0.4f else 1f),
                contentAlignment = Alignment.Center
            ) {
                val icon = app.icon
                if (icon != null) Image(icon.asImageBitmap(), app.label, Modifier.size(50.dp))
                else Text(app.label.take(1), color = Color.White, fontSize = 22.sp)
            }
            when {
                locked -> Badge(Color(0xE6B3261E)) {
                    Icon(Icons.Filled.Lock, null, tint = Color.White, modifier = Modifier.size(12.dp))
                }
                app.isKeyboard -> Badge(Color(0xE60B57D0)) {
                    Icon(Icons.Filled.Keyboard, null, tint = Color.White, modifier = Modifier.size(12.dp))
                }
            }
        }
        if (showLabel) {
            Spacer(Modifier.height(6.dp))
            Text(
                app.label,
                color = Color.White.copy(alpha = if (locked) 0.6f else 1f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun Badge(color: Color, content: @Composable () -> Unit) {
    Box(
        Modifier.size(20.dp).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center
    ) { content() }
}

/**
 * The full app list, with the search field at the TOP.
 *
 * At the bottom the keyboard covered everything worth reading: you typed a letter and could not
 * see what you had found.
 */
@Composable
private fun Drawer(
    apps: List<AppEntry>,
    recents: List<String>,
    state: Guard.State,
    query: String,
    drag: DragController,
    enabled: Boolean,
    onQuery: (String) -> Unit,
    onOpen: (AppEntry) -> Unit,
    onRelease: () -> Unit,
    onPortal: () -> Unit
) {
    val searching = query.isNotBlank()
    val visible = remember(apps, query) {
        if (!searching) apps else apps.filter { it.label.contains(query, ignoreCase = true) }
    }
    val sections = remember(visible) {
        visible.groupBy { it.label.firstOrNull()?.uppercaseChar()?.takeIf { c -> c.isLetter() } ?: '#' }
            .toSortedMap()
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val byPackage = remember(apps) { apps.associateBy { it.packageName } }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xF21A1A1E))
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        SearchRow(
            query = query,
            editable = enabled,
            onChange = onQuery,
            onFocus = {},
            onPortal = onPortal
        )

        Box(Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 28.dp, bottom = 16.dp)
            ) {
                if (!searching && recents.isNotEmpty()) {
                    item("recents-h") { SectionLabel("Zuletzt benutzt") }
                    item("recents") {
                        TileRow(
                            entries = recents.mapNotNull { byPackage[it] }.take(LauncherPrefs.PAGE_COLUMNS),
                            state = state, drag = drag, onOpen = onOpen, onRelease = onRelease
                        )
                    }
                }
                sections.forEach { (letter, entries) ->
                    item("h-$letter") { SectionLabel(letter.toString()) }
                    entries.chunked(LauncherPrefs.PAGE_COLUMNS).forEachIndexed { i, row ->
                        item("$letter-$i") {
                            TileRow(row, state, drag, onOpen, onRelease)
                        }
                    }
                }
                if (visible.isEmpty()) {
                    item("empty") {
                        Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            Text("Nichts gefunden", color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp)
                        }
                    }
                }
            }

            if (!searching && sections.size > 3) {
                AlphabetRail(sections.keys.toList()) { letter ->
                    scope.launch {
                        listState.scrollToItem(indexOfSection(sections, letter, recents.isNotEmpty()))
                    }
                }
            }
        }
    }
}

@Composable
private fun TileRow(
    entries: List<AppEntry>,
    state: Guard.State,
    drag: DragController,
    onOpen: (AppEntry) -> Unit,
    onRelease: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        entries.forEach { app ->
            Box(Modifier.weight(1f)) {
                DraggableTile(
                    app = app,
                    locked = app.packageName in state.locked || state.sealed,
                    drag = drag,
                    fromDrawer = true,
                    onClick = { onOpen(app) },
                    onRelease = onRelease
                )
            }
        }
        repeat(LauncherPrefs.PAGE_COLUMNS - entries.size) { Spacer(Modifier.weight(1f)) }
    }
}

private fun indexOfSection(
    sections: Map<Char, List<AppEntry>>,
    letter: Char,
    hasRecents: Boolean
): Int {
    var index = if (hasRecents) 2 else 0
    for ((c, entries) in sections) {
        if (c == letter) return index
        index += 1 + (entries.size + LauncherPrefs.PAGE_COLUMNS - 1) / LauncherPrefs.PAGE_COLUMNS
    }
    return index
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = Color.White.copy(alpha = 0.55f),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 8.dp, top = 14.dp, bottom = 2.dp)
    )
}

@Composable
private fun AlphabetRail(letters: List<Char>, onPick: (Char) -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(end = 4.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.Center
    ) {
        letters.forEach { c ->
            Text(
                c.toString(),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable { onPick(c) }.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

/**
 * What a held-still finger gets instead of a drag.
 *
 * Dragging is the nicer gesture but an unforgiving one; the menu is the way that always works.
 * Uninstalling is deliberately absent — a one-tap route to removing any app on a child's phone
 * would be a poor idea. App info leads there for a parent who means it.
 */
@Composable
private fun AppMenu(
    app: AppEntry?,
    onHome: () -> Unit,
    onDock: () -> Unit,
    onRemove: () -> Unit,
    onInfo: () -> Unit,
    onHomeScreen: Boolean,
    onDismiss: () -> Unit
) {
    app ?: return
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .padding(32.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF23232A))
                .padding(vertical = 8.dp)
        ) {
            Text(
                app.label,
                color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
            )
            if (!onHomeScreen) {
                MenuItem(Icons.Filled.Add, "Zum Startbildschirm", onHome)
                MenuItem(Icons.Filled.Add, "Ins Dock", onDock)
            } else {
                MenuItem(Icons.Filled.Delete, "Vom Startbildschirm entfernen", onRemove)
            }
            MenuItem(Icons.Filled.Info, "App-Info", onInfo)
        }
    }
}

/** Long-press on empty space, as on any launcher. */
@Composable
private fun SpaceMenu(
    onWallpaper: () -> Unit,
    onAddPage: () -> Unit,
    onSettings: () -> Unit,
    onEditHome: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        Modifier.fillMaxSize().background(Color(0x99000000)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .padding(32.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF23232A))
                .padding(vertical = 8.dp)
        ) {
            MenuItem(Icons.Filled.Wallpaper, "Hintergrundbild ändern", onWallpaper)
            MenuItem(Icons.Filled.Add, "Seite hinzufügen", onAddPage)
            MenuItem(Icons.Filled.Apps, "Startbildschirm bearbeiten", onEditHome)
            MenuItem(Icons.Filled.Settings, "Einstellungen", onSettings)
        }
    }
}


@Composable
private fun MenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = Color.White, fontSize = 15.sp)
    }
}

@Composable
private fun SearchRow(
    query: String,
    editable: Boolean,
    onChange: (String) -> Unit,
    onFocus: () -> Unit,
    onPortal: () -> Unit
) {
    val focus = remember { FocusRequester() }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier
                .weight(1f)
                .height(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0x33FFFFFF))
                .then(if (!editable) Modifier.clickable { onFocus() } else Modifier)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Search, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text("Apps suchen", color = Color.White.copy(alpha = 0.65f), fontSize = 15.sp)
                }
                if (editable) {
                    BasicTextField(
                        value = query,
                        onValueChange = onChange,
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                        cursorBrush = SolidColor(Color.White),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus)
                    )
                }
            }
            if (query.isNotEmpty()) {
                Icon(
                    Icons.Filled.Close, "Leeren",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp).clickable { onChange("") }
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(Color(0x33FFFFFF))
                .clickable(onClick = onPortal),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Shield, "Kindersicherung", tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

/**
 * The launcher's own lock: only when the phone is sealed AND the guard is not answering.
 * Without it, stopping the guard during bedtime would leave an unlocked phone.
 */
@Composable
private fun SealedScreen(reason: String, onPortal: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0xF20B1020)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(Color(0x33FFFFFF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Lock, null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text(reason.ifBlank { "Gesperrt" }, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Das Handy ist gerade gesperrt.",
                color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(22.dp))
            Text(
                "Kindersicherung öffnen",
                color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0x33FFFFFF))
                    .clickable(onClick = onPortal)
                    .padding(horizontal = 22.dp, vertical = 12.dp)
            )
        }
    }
}
