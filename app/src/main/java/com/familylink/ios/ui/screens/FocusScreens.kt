package com.familylink.ios.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familylink.ios.data.AppCategory
import com.familylink.ios.data.InstalledApps
import com.familylink.ios.data.Prefs
import com.familylink.ios.sync.FocusSession
import com.familylink.ios.sync.SyncManager
import com.familylink.ios.sync.SyncService
import com.familylink.ios.sync.TimeRequest
import com.familylink.ios.ui.components.NovaButton
import com.familylink.ios.ui.components.NovaButtonTonal
import com.familylink.ios.ui.components.NovaCard
import com.familylink.ios.ui.components.NovaPill
import com.familylink.ios.ui.theme.Nova
import com.familylink.ios.util.TimeFmt
import kotlin.concurrent.thread
import kotlinx.coroutines.delay

/**
 * FOCUS MODE — parent side.
 * One tap starts a timed session on the child's device where only the chosen apps work.
 * Ends automatically, so nobody has to remember to switch it off.
 */
@Composable
fun FocusScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val sync = remember { SyncManager(context) }
    // The session runs on the CHILD's phone, so the list to choose from must be the child's
    // apps. Picking from the parent's own installed apps produced allow-lists full of packages
    // that do not exist on the child — the session then blocked everything.
    var pulled by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        if (prefs.isParentDevice && prefs.syncConfigured) {
            withIo { runCatching { sync.fetchChildApps() } }
            pulled++
        }
    }
    val apps = remember(pulled) {
        if (!prefs.isParentDevice) InstalledApps.load(context)
        else sync.cachedChildApps()
            .map { InstalledApps.Entry(it.pkg, it.label) }
            .ifEmpty { InstalledApps.load(context) }
            .sortedBy { it.label.lowercase() }
    }

    var session by remember { mutableStateOf(prefs.focusSession()) }
    var minutes by remember { mutableStateOf(45) }
    var label by remember { mutableStateOf("Hausaufgaben") }
    // Focus apps default to whatever is already marked as always-allowed on the child.
    var allowed by remember(apps) {
        mutableStateOf(
            apps.map { it.packageName }
                .filter { prefs.categoryOf(it) == AppCategory.PLUS }
                .toMutableSet()
        )
    }
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1000); tick++; session = prefs.focusSession() } }
    @Suppress("UNUSED_EXPRESSION") tick

    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(Nova.PageGradient))
            .verticalScroll(rememberScrollState()).padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                    .background(Nova.Focus.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.CenterFocusStrong, null, tint = Nova.Focus, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Fokus-Modus", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Nova.Ink)
                Text("Nur ausgewählte Apps, auf Zeit", fontSize = 13.sp, color = Nova.InkMuted)
            }
        }

        Spacer(Modifier.height(20.dp))

        if (session.isRunning()) {
            // --- running session ---
            NovaCard {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    NovaPill("Läuft", Nova.Focus)
                    Spacer(Modifier.height(12.dp))
                    Text(session.label, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Nova.Ink)
                    Text(
                        TimeFmt.hm(session.remainingSeconds()) + " verbleibend",
                        fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = Nova.Focus
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${session.allowed.size} Apps erlaubt",
                        fontSize = 13.sp, color = Nova.InkMuted
                    )
                    Spacer(Modifier.height(16.dp))
                    NovaButton(text = "Fokus beenden", color = Nova.Danger) {
                        sync.stopFocus()
                        SyncService.pushNow(context)
                        session = FocusSession.OFF
                    }
                }
            }
        } else {
            // --- presets ---
            Text("Vorlage", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Nova.InkMuted)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FocusSession.PRESETS.take(2).forEach { (name, mins) ->
                    PresetChip(name, mins, label == name, Modifier.weight(1f)) { label = name; minutes = mins }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FocusSession.PRESETS.drop(2).forEach { (name, mins) ->
                    PresetChip(name, mins, label == name, Modifier.weight(1f)) { label = name; minutes = mins }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("Dauer", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Nova.InkMuted)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundBtn("−") { minutes = (minutes - 15).coerceAtLeast(15) }
                Text(
                    "$minutes Min", modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                    fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Nova.Ink
                )
                RoundBtn("+") { minutes = (minutes + 15).coerceAtMost(240) }
            }

            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Erlaubte Apps (${allowed.size} von ${apps.size})",
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Nova.InkMuted,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "Keine", fontSize = 13.sp, color = Nova.Primary,
                    modifier = Modifier.clickable { allowed = mutableSetOf<String>() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
                Text(
                    "Nur Plus", fontSize = 13.sp, color = Nova.Primary,
                    modifier = Modifier.clickable {
                        allowed = apps.map { it.packageName }
                            .filter { prefs.categoryOf(it) == AppCategory.PLUS }
                            .toMutableSet()
                    }.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            if (prefs.isParentDevice && apps.isEmpty()) {
                Text(
                    "Noch keine App-Liste vom Kinder-Gerät empfangen.",
                    fontSize = 12.sp, color = Nova.Warning
                )
            }
            Spacer(Modifier.height(8.dp))
            NovaCard {
                // The full list — truncating it at 25 made most apps unselectable.
                apps.forEach { app ->
                    val on = app.packageName in allowed
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            allowed = allowed.toMutableSet().apply {
                                if (on) remove(app.packageName) else add(app.packageName)
                            }
                        }.padding(horizontal = 16.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(app.label, fontSize = 15.sp, color = Nova.Ink, modifier = Modifier.weight(1f))
                        Box(
                            Modifier.size(22.dp).clip(CircleShape)
                                .background(if (on) Nova.Focus else Nova.Fill),
                            contentAlignment = Alignment.Center
                        ) {
                            if (on) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            NovaButton(text = "Fokus starten ($minutes Min)", color = Nova.Focus) {
                sync.startFocus(label, minutes, allowed.toList())
                SyncService.pushNow(context)
                session = prefs.focusSession()
            }
        }

        Spacer(Modifier.height(16.dp))
        NovaButtonTonal(text = "Zurück", onClick = onBack)
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * FOCUS MODE — child side ("Handy weglegen").
 *
 * The child starts a session on itself: pick how long, decide whether the allowed Plus apps
 * stay usable, tap start. From then on it behaves exactly like a session the parent pushed —
 * everything else is blocked and the excluded apps disappear from the home screen.
 *
 * Ending early needs the parent PIN on purpose. A session the child could cancel with one tap
 * would not help anyone put their phone away; the countdown running out ends it by itself.
 */
@Composable
fun ChildFocusScreen(onBack: () -> Unit, onRequestEnd: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val apps = remember { InstalledApps.load(context) }

    var tick by remember { mutableStateOf(0) }
    var session by remember { mutableStateOf(prefs.effectiveFocusSession()) }
    LaunchedEffect(Unit) {
        while (true) { delay(1000); tick++; session = prefs.effectiveFocusSession() }
    }
    @Suppress("UNUSED_EXPRESSION") tick

    var minutes by remember { mutableStateOf(30) }
    var allowPlus by remember { mutableStateOf(true) }
    val fromParent = prefs.focusSession().isRunning()

    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(Nova.PageGradient))
            .verticalScroll(rememberScrollState()).padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                    .background(Nova.Focus.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.CenterFocusStrong, null, tint = Nova.Focus, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Handy weglegen", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Nova.Ink)
                Text("Fokus-Zeit, die du selbst startest", fontSize = 13.sp, color = Nova.InkMuted)
            }
        }

        Spacer(Modifier.height(20.dp))

        if (session.isRunning()) {
            NovaCard {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    NovaPill(if (fromParent) "Von den Eltern gestartet" else "Läuft", Nova.Focus)
                    Spacer(Modifier.height(12.dp))
                    Text(session.label, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Nova.Ink)
                    Text(
                        TimeFmt.hm(session.remainingSeconds()) + " übrig",
                        fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = Nova.Focus
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (session.allowed.isEmpty()) "Nur Telefon und Notruf sind erlaubt."
                        else "${session.allowed.size} Apps bleiben erlaubt.",
                        fontSize = 13.sp, color = Nova.InkMuted, textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    if (fromParent) {
                        Text(
                            "Diese Fokus-Zeit haben deine Eltern gestartet. Sie endet automatisch.",
                            fontSize = 13.sp, color = Nova.InkMuted, textAlign = TextAlign.Center
                        )
                    } else {
                        NovaButtonTonal(text = "Früher beenden (Eltern-PIN)", onClick = onRequestEnd)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Sonst endet sie von selbst, wenn die Zeit um ist.",
                            fontSize = 12.sp, color = Nova.InkFaint, textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            Text(
                "Leg dein Handy für eine feste Zeit weg. Währenddessen ist alles gesperrt — " +
                    "die Zeit läuft weiter, auch wenn du die App schließt.",
                fontSize = 14.sp, color = Nova.InkMuted
            )

            Spacer(Modifier.height(20.dp))
            Text("Wie lange?", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Nova.InkMuted)
            Spacer(Modifier.height(8.dp))
            val choices = listOf(
                "Kurz" to 15,
                "Halbe Stunde" to 30,
                "Eine Stunde" to 60,
                "Zwei Stunden" to 120
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                choices.take(2).forEach { (name, m) ->
                    PresetChip(name, m, minutes == m, Modifier.weight(1f)) { minutes = m }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                choices.drop(2).forEach { (name, m) ->
                    PresetChip(name, m, minutes == m, Modifier.weight(1f)) { minutes = m }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("Was bleibt erlaubt?", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Nova.InkMuted)
            Spacer(Modifier.height(8.dp))
            NovaCard {
                Column {
                    ChoiceRow(
                        title = "Zugelassene Plus-Apps",
                        subtitle = "Schule, Musik und alles, was auf Plus steht, geht weiter.",
                        selected = allowPlus
                    ) { allowPlus = true }
                    ChoiceRow(
                        title = "Wirklich nichts",
                        subtitle = "Nur noch Telefon und Notruf. Am konsequentesten.",
                        selected = !allowPlus
                    ) { allowPlus = false }
                }
            }

            Spacer(Modifier.height(20.dp))
            NovaButton(text = "Jetzt weglegen ($minutes Min)", color = Nova.Focus) {
                val now = System.currentTimeMillis()
                val allowed =
                    if (allowPlus) apps.map { it.packageName }
                        .filter { prefs.categoryOf(it) == AppCategory.PLUS }
                    else emptyList()
                prefs.setSelfFocusSession(
                    FocusSession(
                        active = true,
                        endsAt = now + minutes * 60_000L,
                        label = "Handy weggelegt",
                        allowed = allowed,
                        startedAt = now,
                        durationSeconds = minutes * 60
                    )
                )
                // Apply at once instead of waiting for the next tick, and let the parent see it.
                com.familylink.ios.service.MonitorService.recheck(context)
                SyncService.pushNow(context)
                session = prefs.effectiveFocusSession()
            }
        }

        Spacer(Modifier.height(16.dp))
        NovaButtonTonal(text = "Zurück", onClick = onBack)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ChoiceRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Nova.Ink)
            Text(subtitle, fontSize = 12.sp, color = Nova.InkMuted)
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier.size(22.dp).clip(CircleShape)
                .background(if (selected) Nova.Focus else Nova.Fill),
            contentAlignment = Alignment.Center
        ) {
            if (selected) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun PresetChip(name: String, mins: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(Nova.RadiusControl.dp))
            .background(if (selected) Nova.Focus.copy(alpha = 0.15f) else Nova.Surface)
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                color = if (selected) Nova.Focus else Nova.Ink)
            Text("$mins Min", fontSize = 11.sp, color = Nova.InkMuted)
        }
    }
}

@Composable
private fun RoundBtn(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(46.dp).clip(CircleShape).background(Nova.Fill).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 22.sp, color = Nova.Primary, fontWeight = FontWeight.Bold)
    }
}

/** Child side: ask the parent for more time, with a reason. */
@Composable
fun RequestTimeScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val sync = remember { SyncManager(context) }

    var minutes by remember { mutableStateOf(15) }
    var reason by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf<TimeRequest?>(null) }

    // Poll for the parent's decision while waiting.
    LaunchedEffect(sent) {
        while (sent) {
            val r = withIo { sync.readRequest() }
            if (r != null) state = r
            if (r != null && !r.isPending) break
            delay(3000)
        }
    }

    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(Nova.PageGradient))
            .verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))
        Text("Mehr Zeit anfragen", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Nova.Ink)
        Spacer(Modifier.height(8.dp))

        if (!sent) {
            Text(
                "Deine Eltern bekommen die Anfrage sofort auf ihr Handy.",
                fontSize = 14.sp, color = Nova.InkMuted, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundBtn("−") { minutes = (minutes - 5).coerceAtLeast(5) }
                Text(
                    "$minutes Min", modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                    fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Nova.Primary
                )
                RoundBtn("+") { minutes = (minutes + 5).coerceAtMost(60) }
            }
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Nova.RadiusControl.dp))
                    .background(Nova.Surface).padding(14.dp)
            ) {
                if (reason.isEmpty()) Text("Warum? (z. B. Referat fertig machen)", fontSize = 14.sp, color = Nova.InkFaint)
                BasicTextField(
                    value = reason, onValueChange = { reason = it.take(120) },
                    textStyle = TextStyle(fontSize = 14.sp, color = Nova.Ink),
                    cursorBrush = SolidColor(Nova.Primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(24.dp))
            NovaButton(text = "Anfrage senden", enabled = prefs.syncConfigured) {
                thread(isDaemon = true) { sync.sendRequest(minutes, reason.ifBlank { "Keine Begründung" }) }
                sent = true
            }
            if (!prefs.syncConfigured) {
                Spacer(Modifier.height(10.dp))
                Text("Kein Konto verbunden — Anfragen sind nicht möglich.",
                    fontSize = 12.sp, color = Nova.InkFaint, textAlign = TextAlign.Center)
            }
        } else {
            val s = state
            when {
                s == null || s.isPending -> {
                    NovaPill("Warte auf Antwort…", Nova.Warning)
                    Spacer(Modifier.height(16.dp))
                    Text("$minutes Minuten angefragt", fontSize = 16.sp, color = Nova.InkMuted)
                }
                s.state == TimeRequest.APPROVED -> {
                    NovaPill("Genehmigt", Nova.Success)
                    Spacer(Modifier.height(16.dp))
                    Text("+${s.minutes} Minuten gutgeschrieben!",
                        fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Nova.Success)
                }
                else -> {
                    NovaPill("Abgelehnt", Nova.Danger)
                    Spacer(Modifier.height(16.dp))
                    Text("Diesmal leider nicht.", fontSize = 16.sp, color = Nova.InkMuted)
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        NovaButtonTonal(text = "Zurück", onClick = onClose)
    }
}

/** Run a blocking call off the main thread from a composable coroutine. */
private suspend fun <T> withIo(block: () -> T): T =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { block() }
