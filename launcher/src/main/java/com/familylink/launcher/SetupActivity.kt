package com.familylink.launcher

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The setup wizard.
 *
 * Arranging a home screen by dragging is precise work with a fingertip, and on a phone with
 * seventy apps it is a lot of it. Picking from a list is neither: every app is one tap, the
 * order is decided afterwards, and nothing can be dropped in the wrong place.
 *
 * It runs itself on the first start and can be reopened at any time from the launcher's
 * settings — deliberately without the family PIN. Which icons sit on a home screen is not a
 * rule to enforce; the child may arrange their own phone. The PIN guards time and apps, and
 * that is where it belongs.
 *
 * Nothing here can hide an app. An app that is not chosen is simply not on the home screen;
 * it stays in the drawer, where it has always been.
 */
class SetupActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        val step = intent?.getStringExtra(EXTRA_STEP) ?: STEP_ALL
        setContent { Wizard(step) { finish() } }
    }

    companion object {
        const val EXTRA_STEP = "step"

        /** The whole wizard, as on a first start. */
        const val STEP_ALL = "all"

        /** Only the home screen apps, from the settings screen. */
        const val STEP_APPS = "apps"

        /** Only the dock. */
        const val STEP_DOCK = "dock"

        fun start(context: Context, step: String = STEP_ALL) {
            runCatching {
                context.startActivity(
                    Intent(context, SetupActivity::class.java)
                        .putExtra(EXTRA_STEP, step)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }
}

private enum class Step { WELCOME, DOCK, APPS, WEATHER, DEFAULT_HOME, DONE }

@Composable
private fun Wizard(mode: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val model = remember { HomeModel(context) }
    val apps = remember { AppOrder.sort(Apps.load(context)) }
    val byPackage = remember(apps) { apps.associateBy { it.packageName } }

    val steps = remember(mode) {
        when (mode) {
            SetupActivity.STEP_APPS -> listOf(Step.APPS)
            SetupActivity.STEP_DOCK -> listOf(Step.DOCK)
            else -> listOf(Step.WELCOME, Step.DOCK, Step.APPS, Step.WEATHER, Step.DEFAULT_HOME, Step.DONE)
        }
    }
    var at by remember { mutableStateOf(0) }

    // Registered here rather than inside the step, because a result launcher must exist before
    // the composable that uses it is first drawn.
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    // Editing an existing layout starts from that layout; a first run starts empty, so the
    // home screen is what was actually asked for rather than what happened to be guessed.
    val editing = mode != SetupActivity.STEP_ALL
    val chosen = remember { mutableStateListOf<String>().apply { if (editing) addAll(model.homeApps()) } }
    val dock = remember { mutableStateListOf<String>().apply { if (editing) addAll(model.dock) } }

    // Only what was touched is written. Skipping a step must never clear a layout that is
    // already there.
    var appsTouched by remember { mutableStateOf(false) }
    var dockTouched by remember { mutableStateOf(false) }

    fun save() {
        if (dockTouched) model.applyDock(dock.toList())
        if (appsTouched) model.applySelection(chosen.toList(), byPackage)
        LauncherPrefs(context).setupDone = true
    }

    fun next() {
        if (at < steps.lastIndex) at++ else { save(); onClose() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF141418))
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        when (steps[at]) {
            Step.WELCOME -> Welcome(Modifier.weight(1f))

            Step.DOCK -> AppPicker(
                title = "Leiste unten",
                subtitle = "Bis zu ${LauncherPrefs.DOCK_MAX} Apps, die auf jeder Seite unten stehen. " +
                    "Antippen genügt.",
                apps = apps,
                selected = dock,
                max = LauncherPrefs.DOCK_MAX,
                onToggle = { pkg ->
                    dockTouched = true
                    if (pkg in dock) dock.remove(pkg)
                    else if (dock.size < LauncherPrefs.DOCK_MAX) dock.add(pkg)
                },
                onQuickSelect = null,
                modifier = Modifier.weight(1f)
            )

            Step.APPS -> AppPicker(
                title = "Apps auf dem Startbildschirm",
                subtitle = "Alles, was hier angetippt ist, liegt auf dem Startbildschirm — " +
                    "Telefon, Nachrichten und Kamera zuerst, der Rest nach Namen. " +
                    "Nicht gewählte Apps bleiben im App-Schrank.",
                apps = apps,
                selected = chosen,
                max = Int.MAX_VALUE,
                onToggle = { pkg ->
                    appsTouched = true
                    if (pkg in chosen) chosen.remove(pkg) else chosen.add(pkg)
                },
                onQuickSelect = { which ->
                    appsTouched = true
                    chosen.clear()
                    when (which) {
                        Quick.ALL -> chosen.addAll(apps.map { it.packageName })
                        Quick.USUAL -> chosen.addAll(apps.map { it.packageName }.take(USUAL_COUNT))
                        Quick.NONE -> Unit
                    }
                },
                modifier = Modifier.weight(1f)
            )

            Step.WEATHER -> Explain(
                icon = Icons.Filled.Place,
                onAction = { locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
                title = "Wetter neben der Uhr",
                body = "Für die Temperatur braucht der Startbildschirm den ungefähren Standort. " +
                    "Abgefragt wird nur die letzte bekannte Position, nichts wird gespeichert " +
                    "und nichts außer Breiten- und Längengrad verlässt das Telefon.\n\n" +
                    "Ohne Freigabe bleibt einfach die Uhr stehen.",
                action = "Standort freigeben",
                modifier = Modifier.weight(1f)
            )

            Step.DEFAULT_HOME -> Explain(
                icon = Icons.Filled.Home,
                onAction = {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                },
                title = "Als Startbildschirm festlegen",
                body = "Damit Völkle Start beim Drücken der Home-Taste erscheint, muss Android " +
                    "es als Standard kennen. Es gibt dafür keine Abkürzung — Android lässt " +
                    "keine App sich selbst zum Startbildschirm machen.\n\n" +
                    "Der Knopf öffnet die Auswahl; dort „Völkle Start\" wählen.",
                action = "Auswahl öffnen",
                modifier = Modifier.weight(1f)
            )

            Step.DONE -> Done(
                homeCount = chosen.size,
                dockCount = dock.size,
                modifier = Modifier.weight(1f)
            )
        }

        Footer(
            step = steps[at],
            index = at,
            count = steps.size,
            onBack = { if (at > 0) at-- else onClose() },
            onSkip = { next() },
            onNext = { next() },
            onSave = { save(); onClose() }
        )
    }
}

/** How much of the sorted list "die üblichen" covers: the priority groups, roughly. */
private const val USUAL_COUNT = 8

private enum class Quick { ALL, NONE, USUAL }

@Composable
private fun Welcome(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Völkle Start", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(12.dp))
        Text(
            "In drei Schritten eingerichtet: die Leiste unten, die Apps auf dem Startbildschirm, " +
                "und ein paar Kleinigkeiten.\n\n" +
                "Alles lässt sich später jederzeit ändern — langer Druck auf eine freie Fläche.",
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 16.sp
        )
    }
}

@Composable
private fun Explain(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onAction: () -> Unit,
    title: String,
    body: String,
    action: String,
    modifier: Modifier = Modifier
) {
    var done by remember { mutableStateOf(false) }

    Column(
        modifier.fillMaxWidth().padding(28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(12.dp))
        Text(body, color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp)
        Spacer(Modifier.height(24.dp))
        Pill(if (done) "Erledigt" else action) { done = true; onAction() }
    }
}

@Composable
private fun Done(homeCount: Int, dockCount: Int, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(16.dp))
        Text("Fertig", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(12.dp))
        Text(
            "$dockCount Apps in der Leiste, $homeCount auf dem Startbildschirm.\n\n" +
                "Ändern geht jederzeit: langer Druck auf eine freie Fläche → Einstellungen. " +
                "Einzelne Apps lassen sich dort auch weiter mit dem Finger verschieben.",
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 15.sp
        )
    }
}

/**
 * The list every choosing step is made of.
 *
 * One row per app with a tick, a search field, and — where it makes sense — the three
 * quick-select buttons. Seventy rows is a long scroll, which is exactly why the search is
 * at the top rather than the bottom.
 */
@Composable
private fun AppPicker(
    title: String,
    subtitle: String,
    apps: List<AppEntry>,
    selected: List<String>,
    max: Int,
    onToggle: (String) -> Unit,
    onQuickSelect: ((Quick) -> Unit)?,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(query, apps) {
        if (query.isBlank()) apps
        else apps.filter { it.label.contains(query, ignoreCase = true) }
    }

    Column(modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 12.dp)) {
            Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(6.dp))
            Text(subtitle, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
            if (max != Int.MAX_VALUE) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "${selected.size} von $max gewählt",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 13.sp
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0x22FFFFFF))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Search, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text("Suchen", color = Color.White.copy(alpha = 0.45f), fontSize = 15.sp)
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        onQuickSelect?.let { quick ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Pill("Alle", small = true) { quick(Quick.ALL) }
                Pill("Keine", small = true) { quick(Quick.NONE) }
                Pill("Die üblichen", small = true) { quick(Quick.USUAL) }
            }
        }

        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(shown, key = { it.packageName }) { app ->
                val on = app.packageName in selected
                val full = !on && selected.size >= max
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !full) { onToggle(app.packageName) }
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    app.icon?.let {
                        Image(
                            it.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(38.dp)
                        )
                    } ?: Spacer(Modifier.size(38.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            app.label,
                            color = Color.White.copy(alpha = if (full) 0.4f else 1f),
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (app.isKeyboard) {
                            Text(
                                "Tastatur",
                                color = Color.White.copy(alpha = 0.45f),
                                fontSize = 12.sp
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (on) Color(0xFF4C8DFF) else Color(0x22FFFFFF)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (on) {
                            Icon(
                                Icons.Filled.Check, null,
                                tint = Color.White, modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Footer(
    step: Step,
    index: Int,
    count: Int,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit,
    onSave: () -> Unit
) {
    // The last step of the full wizard saves; a single-step edit saves straight away.
    val last = index == count - 1
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (index == 0 && count == 1) "Abbrechen" else "Zurück",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 15.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onBack)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        )
        Spacer(Modifier.weight(1f))
        if (count > 1) {
            Text(
                "${index + 1}/$count",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 13.sp
            )
            Spacer(Modifier.width(12.dp))
        }
        // Optional steps say so, rather than making the parent guess whether they must act.
        if (step == Step.WEATHER || step == Step.DEFAULT_HOME) {
            Text(
                "Überspringen",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 15.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(onClick = onSkip)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            )
            Spacer(Modifier.width(6.dp))
        }
        Pill(if (last) "Fertig" else "Weiter") { if (last) onSave() else onNext() }
    }
}

@Composable
private fun Pill(label: String, small: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        color = Color(0xFF141418),
        fontSize = if (small) 14.sp else 15.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(
                horizontal = if (small) 14.dp else 22.dp,
                vertical = if (small) 8.dp else 12.dp
            )
    )
}
