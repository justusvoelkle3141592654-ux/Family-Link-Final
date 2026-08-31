package com.familylink.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * The home screen.
 *
 * An ordinary launcher on the surface — swipeable pages, a fixed dock, an app drawer that pulls
 * up from the bottom — and a second line of defence underneath. Two things are deliberate:
 *
 *  - Every app stays in the drawer, always. Hiding what is locked made the phone feel like it
 *    was breaking; a greyed tile with a lock says the same thing without the mystery, and
 *    tapping it explains itself.
 *  - The rules come from the guard when it is answering and from the family's own database when
 *    it is not, so force-stopping the guard during bedtime no longer hands over a free phone.
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

    val apps = remember { Apps.load(context) }
    val byPackage = remember(apps) { apps.associateBy { it.packageName } }
    LaunchedEffect(apps) { model.seedIfEmpty(apps) }

    var state by remember { mutableStateOf(Guard.State.UNKNOWN) }
    var drawerOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    // The guard first; the family's database only when it stops answering. Every pass also
    // pokes the guard, so a force stop is undone within a second of looking at the home screen.
    LaunchedEffect(Unit) {
        var config: org.json.JSONObject? = null
        while (true) {
            val bridge = withContext(Dispatchers.IO) { Guard.readBridge(context) }
            state = if (bridge != null) {
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
            // The cached config goes stale the moment the guard is back to being authoritative.
            if (bridge != null) config = null
            delay(1000)
        }
    }

    // The home screen is the bottom of the stack: back closes the drawer and nothing more.
    BackHandler(enabled = true) {
        if (drawerOpen) { drawerOpen = false; query = "" }
    }

    // Everything sealed AND the guard cannot draw its own overlay: this is the fallback the
    // whole second connection exists for.
    if (state.sealed && !state.guardAlive) {
        SealedScreen(state.reason) { Guard.openPortal(context) }
        return
    }

    fun open(pkg: String) {
        if (pkg in state.locked || state.sealed) {
            // Never start it: the guard's block screen explains the reason and offers the
            // request for more time, and it works even while the guard is only just waking up.
            if (!Guard.showBlocked(context)) Guard.openPortal(context)
        } else {
            Apps.launch(context, pkg)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            StatusStrip(state)

            HorizontalPager(
                state = rememberPagerState { model.pages.size.coerceAtLeast(1) },
                modifier = Modifier
                    .weight(1f)
                    // Pulling up anywhere on the pages opens the drawer, as on any phone.
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            if (dragAmount < -18f) drawerOpen = true
                        }
                    }
            ) { page ->
                HomePage(
                    packages = model.pages.getOrElse(page) { emptyList() },
                    byPackage = byPackage,
                    state = state,
                    onOpen = ::open,
                    onRemove = { model.remove(it) }
                )
            }

            Dock(model.dock, byPackage, state, onOpen = ::open, onRemove = { model.remove(it) })
            SearchRow(
                query = "",
                readOnly = true,
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
                state = state,
                query = query,
                onQuery = { query = it },
                onOpen = { drawerOpen = false; query = ""; open(it) },
                onPinToHome = { pkg ->
                    if (!model.addToDock(pkg)) model.addToPage(pkg, 0)
                    drawerOpen = false
                    query = ""
                },
                onClose = { drawerOpen = false; query = "" },
                onPortal = { Guard.openPortal(context) }
            )
        }
    }
}

/** The thin line at the top: how much is left, or why nothing is. */
@Composable
private fun StatusStrip(state: Guard.State) {
    val text = when {
        state.sealed -> state.reason.ifBlank { "Gesperrt" }
        state.remainingSeconds < 0 -> ""
        state.remainingSeconds == 0 -> "Zeit ist aufgebraucht"
        else -> "Noch ${fmt(state.remainingSeconds)}"
    }
    if (text.isBlank()) { Spacer(Modifier.height(10.dp)); return }
    Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp), contentAlignment = Alignment.Center) {
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

/** One page of the home screen. Empty pages carry a hint rather than nothing at all. */
@Composable
private fun HomePage(
    packages: List<String>,
    byPackage: Map<String, AppEntry>,
    state: Guard.State,
    onOpen: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    if (packages.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Nach oben wischen für alle Apps",
                color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp
            )
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(packages, key = { it }) { pkg ->
            val app = byPackage[pkg]
            if (app != null) {
                Tile(
                    app = app,
                    locked = pkg in state.locked || state.sealed,
                    onClick = { onOpen(pkg) },
                    onLongClick = { onRemove(pkg) }
                )
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
    onOpen: (String) -> Unit,
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
                    onClick = { onOpen(pkg) },
                    onLongClick = { onRemove(pkg) }
                )
            }
        }
    }
}

/**
 * One app.
 *
 * A locked app is drawn faded with a lock badge rather than removed: the grid stays still, and
 * the child can see what exists and what is simply not available right now.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun Tile(
    app: AppEntry,
    locked: Boolean,
    showLabel: Boolean = true,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        Modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 4.dp),
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
                if (icon != null) {
                    Image(icon.asImageBitmap(), app.label, Modifier.size(50.dp))
                } else {
                    Text(app.label.take(1), color = Color.White, fontSize = 22.sp)
                }
            }
            if (locked) {
                Box(
                    Modifier.size(20.dp).clip(CircleShape).background(Color(0xE6B3261E)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Lock, null, tint = Color.White, modifier = Modifier.size(12.dp))
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

/** The full app list, pulled up from the bottom. Everything installed, always. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun Drawer(
    apps: List<AppEntry>,
    state: Guard.State,
    query: String,
    onQuery: (String) -> Unit,
    onOpen: (String) -> Unit,
    onPinToHome: (String) -> Unit,
    onClose: () -> Unit,
    onPortal: () -> Unit
) {
    val visible = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter { it.label.contains(query, ignoreCase = true) }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xF21A1A1E))
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            // Pulling back down closes it again.
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount -> if (dragAmount > 22f) onClose() }
            }
    ) {
        Text(
            "Alle Apps",
            color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 22.dp, top = 16.dp, bottom = 10.dp)
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(visible, key = { it.packageName }) { app ->
                Tile(
                    app = app,
                    locked = app.packageName in state.locked || state.sealed,
                    onClick = { onOpen(app.packageName) },
                    // Long press is "put this on my home screen" — the drawer is the source,
                    // the home screen is the arrangement.
                    onLongClick = { onPinToHome(app.packageName) }
                )
            }
        }
        if (visible.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Nichts gefunden", color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp)
            }
        }
        SearchRow(
            query = query,
            readOnly = false,
            onChange = onQuery,
            onFocus = {},
            onPortal = onPortal
        )
    }
}

/**
 * The search field.
 *
 * On the home screen it is a dummy that opens the drawer on a tap; inside the drawer it is the
 * real thing. One composable for both so the bar does not appear to jump when the drawer opens.
 *
 * It filters installed apps and nothing else — a launcher search box that quietly reached the
 * web would undo half the point of the phone.
 */
@Composable
private fun SearchRow(
    query: String,
    readOnly: Boolean,
    onChange: (String) -> Unit,
    onFocus: () -> Unit,
    onPortal: () -> Unit
) {
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
                .then(if (readOnly) Modifier.clickable { onFocus() } else Modifier)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Search, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text("Apps suchen", color = Color.White.copy(alpha = 0.65f), fontSize = 15.sp)
                }
                if (!readOnly) {
                    BasicTextField(
                        value = query,
                        onValueChange = onChange,
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                        cursorBrush = SolidColor(Color.White),
                        modifier = Modifier.fillMaxWidth()
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
 * the child holding an unlocked phone, which is the one outcome the whole second app exists to
 * prevent.
 */
@Composable
private fun SealedScreen(reason: String, onPortal: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0xF20B1020)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxHeight().padding(32.dp),
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
            Text(
                reason.ifBlank { "Gesperrt" },
                color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Medium
            )
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
