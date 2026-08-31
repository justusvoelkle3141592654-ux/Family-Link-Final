package com.familylink.launcher

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
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

/** What is currently held by a finger, and where. */
private data class Drag(val pkg: String, val position: Offset)

@Composable
private fun Home() {
    val context = LocalContext.current
    val model = remember { HomeModel(context) }
    val prefs = remember { LauncherPrefs(context) }

    val apps = remember { Apps.load(context) }
    val byPackage = remember(apps) { apps.associateBy { it.packageName } }
    LaunchedEffect(apps) { model.seedIfEmpty(apps) }

    var state by remember { mutableStateOf(Guard.State.UNKNOWN) }
    var drawerOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var drag by remember { mutableStateOf<Drag?>(null) }

    // The guard first; the family's database only when it stops answering. Every pass also
    // pokes the guard, so a force stop is undone within a second of looking at the home screen.
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
        if (drawerOpen) { drawerOpen = false; query = "" }
    }

    if (state.sealed && !state.guardAlive) {
        SealedScreen(state.reason) { Guard.openPortal(context) }
        return
    }

    fun open(entry: AppEntry) {
        if (entry.packageName in state.locked || state.sealed) {
            // Never start it: the guard's block screen explains the reason and offers the
            // request for more time, and it works even while the guard is only just waking up.
            if (!Guard.showBlocked(context)) Guard.openPortal(context)
        } else {
            Apps.launch(context, entry)
        }
    }

    val pagerState = rememberPagerState { model.pages.size.coerceAtLeast(1) }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
        ) {
            ClockHeader()
            StatusStrip(state)

            // While something is being dragged, the top of the screen becomes the bin.
            if (drag != null) RemoveTarget()

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(drag == null) {
                        // Only when nothing is being dragged, so a drag does not open the drawer.
                        if (drag == null) {
                            detectVerticalDragGestures { _, dy -> if (dy < -18f) drawerOpen = true }
                        }
                    }
            ) { page ->
                HomePage(
                    slots = model.pages.getOrElse(page) { prefs.emptyPage() },
                    byPackage = byPackage,
                    state = state,
                    onOpen = ::open,
                    onPickUp = { pkg, pos -> drag = Drag(pkg, pos) },
                    onDragMove = { pos -> drag = drag?.copy(position = pos) },
                    onDrop = { slot ->
                        drag?.let { model.dropOnPage(it.pkg, pagerState.currentPage, slot) }
                        drag = null
                    }
                )
            }

            Dock(model.dock, byPackage, state, onOpen = ::open, onRemove = { model.remove(it) })
            SearchRow(
                query = "",
                editable = false,
                onChange = {},
                onFocus = { drawerOpen = true },
                onPortal = { Guard.openPortal(context) }
            )
        }

        AnimatedVisibility(
            visible = drawerOpen,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            Drawer(
                apps = apps,
                recents = prefs.recents,
                state = state,
                query = query,
                onQuery = { query = it },
                onOpen = { drawerOpen = false; query = ""; open(it) },
                onPickUp = { pkg ->
                    // Picking an app up in the drawer drops you onto the home screen still
                    // holding it, which is the whole point of dragging rather than pinning.
                    drawerOpen = false
                    query = ""
                    drag = Drag(pkg, Offset.Zero)
                },
                onClose = { drawerOpen = false; query = "" },
                onPortal = { Guard.openPortal(context) }
            )
        }

        // The tile that follows the finger.
        drag?.let { d ->
            byPackage[d.pkg]?.icon?.let { icon ->
                val density = LocalDensity.current
                Image(
                    icon.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (d.position.x - with(density) { 28.dp.toPx() }).toInt(),
                                (d.position.y - with(density) { 28.dp.toPx() }).toInt()
                            )
                        }
                        .size(56.dp)
                        .alpha(0.85f)
                )
            }
        }
    }
}

/** Clock, date, and the weather beside it. */
@Composable
private fun ClockHeader() {
    val context = LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    var weather by remember { mutableStateOf<Weather.Now?>(null) }
    var askedLocation by remember { mutableStateOf(false) }

    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or not, the next refresh picks it up */ }

    LaunchedEffect(Unit) {
        while (true) { now = Date(); delay(1000) }
    }
    // Weather is slow-moving and optional; half-hourly is plenty and costs almost nothing.
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
            .clickable(enabled = !Weather.hasPermission(context) && !askedLocation) {
                askedLocation = true
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

/** The thin line under the clock: how much is left, or why nothing is. */
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

/** Shown at the top only while something is held, as the place to drop it to remove it. */
@Composable
private fun RemoveTarget() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 8.dp)
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
 * One page: a fixed grid of slots. Empty slots are still drawn, as the places a dragged icon
 * can land — an invisible drop target is the reason dragging felt broken before.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomePage(
    slots: List<String?>,
    byPackage: Map<String, AppEntry>,
    state: Guard.State,
    onOpen: (AppEntry) -> Unit,
    onPickUp: (String, Offset) -> Unit,
    onDragMove: (Offset) -> Unit,
    onDrop: (Int) -> Unit
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
                    .pointerInput(pkg) {
                        // An empty slot is only a drop target; a filled one can be picked up.
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset -> pkg?.let { onPickUp(it, offset) } },
                            onDrag = { change, _ -> onDragMove(change.position) },
                            onDragEnd = { onDrop(index) },
                            onDragCancel = { onDrop(index) }
                        )
                    },
                contentAlignment = Alignment.TopCenter
            ) {
                if (app != null) {
                    Tile(
                        app = app,
                        locked = app.packageName in state.locked || state.sealed,
                        onClick = { onOpen(app) },
                        onLongClick = { }
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
    onOpen: (AppEntry) -> Unit,
    onRemove: (String) -> Unit
) {
    if (packages.isEmpty()) return
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0x22FFFFFF))
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        packages.forEach { pkg ->
            byPackage[pkg]?.let { app ->
                Tile(
                    app = app,
                    locked = pkg in state.locked || state.sealed,
                    showLabel = false,
                    onClick = { onOpen(app) },
                    onLongClick = { onRemove(pkg) }
                )
            }
        }
    }
}

/**
 * One app.
 *
 * A locked app is faded with a lock badge rather than removed: the grid stays still, and the
 * child can see what exists and what is simply not available right now.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Tile(
    app: AppEntry,
    locked: Boolean,
    showLabel: Boolean = true,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(vertical = 4.dp),
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
                // A keyboard cannot be started; the badge says so before the tap does.
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
 * The full app list.
 *
 * The search field sits at the TOP once the drawer is open, with the results directly beneath
 * it. It used to sit at the bottom, where the keyboard covered everything worth looking at —
 * you typed a letter and could not see what you had found.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Drawer(
    apps: List<AppEntry>,
    recents: List<String>,
    state: Guard.State,
    query: String,
    onQuery: (String) -> Unit,
    onOpen: (AppEntry) -> Unit,
    onPickUp: (String) -> Unit,
    onClose: () -> Unit,
    onPortal: () -> Unit
) {
    val searching = query.isNotBlank()
    val visible = remember(apps, query) {
        if (!searching) apps else apps.filter { it.label.contains(query, ignoreCase = true) }
    }
    // Alphabetical sections, so the list reads as an index rather than a wall.
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
        // Search at the top, results underneath: the whole point of this rework.
        SearchRow(
            query = query,
            editable = true,
            autoFocus = false,
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
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            recents.mapNotNull { byPackage[it] }.take(4).forEach { app ->
                                Box(Modifier.weight(1f)) {
                                    Tile(
                                        app = app,
                                        locked = app.packageName in state.locked || state.sealed,
                                        onClick = { onOpen(app) },
                                        onLongClick = { onPickUp(app.packageName) }
                                    )
                                }
                            }
                            repeat(4 - recents.mapNotNull { byPackage[it] }.take(4).size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
                sections.forEach { (letter, entries) ->
                    item("h-$letter") { SectionLabel(letter.toString()) }
                    entries.chunked(LauncherPrefs.PAGE_COLUMNS).forEachIndexed { i, row ->
                        item("$letter-$i") {
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                row.forEach { app ->
                                    Box(Modifier.weight(1f)) {
                                        Tile(
                                            app = app,
                                            locked = app.packageName in state.locked || state.sealed,
                                            onClick = { onOpen(app) },
                                            onLongClick = { onPickUp(app.packageName) }
                                        )
                                    }
                                }
                                repeat(LauncherPrefs.PAGE_COLUMNS - row.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
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

            // The A–Z rail. Only worth drawing when there is enough to scroll past.
            if (!searching && sections.size > 3) {
                AlphabetRail(sections.keys.toList()) { letter ->
                    val index = indexOfSection(sections, letter, recents.isNotEmpty())
                    scope.launch { listState.scrollToItem(index) }
                }
            }
        }
    }
}

/** How many list items sit before [letter]'s heading, so the rail can jump to it. */
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

/** The thin A–Z strip down the right edge. */
@Composable
private fun AlphabetRail(letters: List<Char>, onPick: (Char) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(end = 4.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.Center
    ) {
        letters.forEach { c ->
            Text(
                c.toString(),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clickable { onPick(c) }
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

/**
 * The search field.
 *
 * On the home screen it is a dummy that opens the drawer on a tap; inside the drawer it is the
 * real thing at the top. One composable for both so nothing appears to jump.
 *
 * It filters installed apps and nothing else — a launcher search box that quietly reached the
 * web would undo half the point of the phone.
 */
@Composable
private fun SearchRow(
    query: String,
    editable: Boolean,
    autoFocus: Boolean = false,
    onChange: (String) -> Unit,
    onFocus: () -> Unit,
    onPortal: () -> Unit
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(autoFocus) { if (autoFocus && editable) runCatching { focus.requestFocus() } }

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
 * The launcher's own lock.
 *
 * Only ever seen when the phone is sealed AND the guard is not answering — normally its overlay
 * covers everything long before this. Without it, stopping the guard during bedtime would leave
 * the child holding an unlocked phone, which is the one outcome the second app exists to stop.
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
