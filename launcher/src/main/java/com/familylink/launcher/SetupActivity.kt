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
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
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
            .background(Look.Canvas)
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
        Text("Völkle Start", color = Look.Ink, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            "In drei Schritten eingerichtet: die Leiste unten, die Apps auf dem Startbildschirm, " +
                "und ein paar Kleinigkeiten.\n\n" +
                "Alles lässt sich später jederzeit ändern — langer Druck auf eine freie Fläche.",
            color = Look.InkMuted,
            fontSize = 16.sp,
            lineHeight = 22.sp
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
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(Look.Accent),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Look.Primary, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, color = Look.Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(body, color = Look.InkMuted, fontSize = 15.sp, lineHeight = 21.sp)
        Spacer(Modifier.height(24.dp))
        LookButton(if (done) "Erledigt" else action) { done = true; onAction() }
    }
}

@Composable
private fun Done(homeCount: Int, dockCount: Int, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(Look.Success.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Check, null, tint = Look.Success, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("Fertig", color = Look.Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            "$dockCount Apps in der Leiste, $homeCount auf dem Startbildschirm.\n\n" +
                "Ändern geht jederzeit: langer Druck auf eine freie Fläche → Einstellungen. " +
                "Einzelne Apps lassen sich dort auch weiter mit dem Finger verschieben.",
            color = Look.InkMuted,
            fontSize = 15.sp,
            lineHeight = 21.sp
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
        LookTitle(title, subtitle)
        if (max != Int.MAX_VALUE) {
            Text(
                "${selected.size} von $max gewählt",
                color = Look.InkFaint,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
            )
        }
        Spacer(Modifier.height(4.dp))

        LookSearch(
            query = query,
            onChange = { query = it },
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        onQuickSelect?.let { quick ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LookButton("Alle", filled = false, small = true) { quick(Quick.ALL) }
                LookButton("Keine", filled = false, small = true) { quick(Quick.NONE) }
                LookButton("Die üblichen", filled = false, small = true) { quick(Quick.USUAL) }
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
                            color = if (full) Look.InkFaint else Look.Ink,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (app.isKeyboard) {
                            Text(
                                "Tastatur",
                                color = Look.InkFaint,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (on) Look.Primary else Look.Fill),
                        contentAlignment = Alignment.Center
                    ) {
                        if (on) {
                            Icon(
                                Icons.Filled.Check, null,
                                tint = Look.Canvas, modifier = Modifier.size(15.dp)
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
        LookButton(
            if (index == 0 && count == 1) "Abbrechen" else "Zurück",
            filled = false,
            onClick = onBack
        )
        Spacer(Modifier.weight(1f))
        if (count > 1) {
            Text("${index + 1}/$count", color = Look.InkFaint, fontSize = 13.sp)
            Spacer(Modifier.width(12.dp))
        }
        // Optional steps say so, rather than making the parent guess whether they must act.
        if (step == Step.WEATHER || step == Step.DEFAULT_HOME) {
            LookButton("Überspringen", filled = false, small = true, onClick = onSkip)
            Spacer(Modifier.width(8.dp))
        }
        LookButton(if (last) "Fertig" else "Weiter") { if (last) onSave() else onNext() }
    }
}
