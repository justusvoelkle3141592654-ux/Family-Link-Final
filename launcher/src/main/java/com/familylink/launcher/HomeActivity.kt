package com.familylink.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * The home screen.
 *
 * Deliberately small: a grid of apps, a search field, and nothing else. No widgets, no pages, no
 * folders — every one of those is a surface to hide something behind, and none of them is what
 * this phone is for.
 *
 * Two things make it more than cosmetic:
 *  - Locked apps are not drawn at all. A grid that offers what cannot be opened is a list of
 *    things to be annoyed about; leaving them out means the phone simply is what it is allowed
 *    to be right now.
 *  - Every resume wakes the guard. Coming back to the home screen is therefore enough to undo a
 *    force stop, and there is no way to use the phone that does not pass through here.
 */
class HomeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { HomeScreen() }
    }
}

@Composable
private fun HomeScreen() {
    val context = LocalContext.current

    var query by remember { mutableStateOf("") }
    val apps = remember { Apps.load(context) }
    var state by remember { mutableStateOf(Guard.read(context)) }

    // Home is the one screen there is: back does nothing, exactly as on a real launcher.
    BackHandler(enabled = true) { }

    // Poll rather than subscribe: the guard's state changes on a clock (a limit runs out, a
    // bedtime starts) and a second's lag on a home screen is invisible. Every pass also wakes
    // the guard, so a force stop is undone within a second of coming back here.
    LaunchedEffect(Unit) {
        while (true) {
            state = Guard.read(context)
            if (!state.reachable) Guard.revive(context)
            delay(1000)
        }
    }

    val visible = remember(apps, query, state) {
        apps.asSequence()
            .filter { it.packageName !in state.locked }
            .filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
            .toList()
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // ---- the guard is gone: say so, plainly and permanently ----
        if (!state.reachable) {
            Banner(
                "Schutz ist nicht aktiv",
                "Die Kindersicherung wurde beendet. Sie wird automatisch neu gestartet."
            ) { Guard.openPortal(context) }
        }

        Spacer(Modifier.height(8.dp))

        if (state.sealed) {
            SealedNotice(state.reason) { Guard.openPortal(context) }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 76.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(visible, key = { it.packageName }) { app ->
                    AppTile(app) { Apps.launch(context, app.packageName) }
                }
            }
            if (visible.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        if (query.isBlank()) "Keine Apps freigegeben"
                        else "Nichts gefunden",
                        color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp
                    )
                }
            }
        }

        SearchBar(query, onChange = { query = it }, onPortal = { Guard.openPortal(context) })
    }
}

/** One app: rounded icon, name underneath. */
@Composable
private fun AppTile(app: AppEntry, onClick: () -> Unit) {
    Column(
        Modifier.clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(Color(0x22FFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            val icon = app.icon
            if (icon != null) {
                Image(icon.asImageBitmap(), contentDescription = app.label, Modifier.size(50.dp))
            } else {
                Text(app.label.take(1), color = Color.White, fontSize = 22.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            app.label,
            color = Color.White,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * The search field, at the bottom where a thumb is.
 *
 * It filters the grid rather than searching the web: this is a phone with a short list of
 * allowed apps, and a launcher that quietly offers a browser box would undo half the point.
 */
@Composable
private fun SearchBar(query: String, onChange: (String) -> Unit, onPortal: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier
                .weight(1f)
                .height(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0x33FFFFFF))
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Search, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text("Apps suchen", color = Color.White.copy(alpha = 0.65f), fontSize = 15.sp)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onChange,
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.fillMaxWidth()
                )
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
        // The way to the portal, and with it the PIN-protected way out of this launcher.
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(Color(0x33FFFFFF))
                .clickable(onClick = onPortal),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Shield, "Kindersicherung", tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun Banner(title: String, text: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xCCB3261E))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text(text, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
    }
}

/** Everything is locked: no grid at all, just the reason and the way to the portal. */
@Composable
private fun SealedNotice(reason: String, onPortal: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(Color(0x33FFFFFF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Lock, null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(
                reason.ifBlank { "Gesperrt" },
                color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Das Handy ist gerade gesperrt.",
                color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Kindersicherung öffnen",
                color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0x33FFFFFF))
                    .clickable(onClick = onPortal)
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            )
        }
    }
}
