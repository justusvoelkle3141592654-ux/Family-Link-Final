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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familylink.ios.data.InstalledApps
import com.familylink.ios.data.Prefs
import com.familylink.ios.sync.SyncManager
import com.familylink.ios.sync.TimeRequest
import com.familylink.ios.sync.SyncService
import com.familylink.ios.ui.components.NovaButton
import com.familylink.ios.ui.components.NovaButtonTonal
import com.familylink.ios.ui.components.NovaPill
import com.familylink.ios.ui.components.NovaCard
import com.familylink.ios.ui.components.NovaRow
import com.familylink.ios.ui.components.NovaSwitch
import com.familylink.ios.ui.components.SectionHeader
import com.familylink.ios.ui.theme.Nova
import com.familylink.ios.ui.theme.ThemeMode
import com.familylink.ios.util.TimeFmt
import kotlinx.coroutines.delay
import kotlin.concurrent.thread

@Composable
fun ParentPortalScreen(
    onOpenApps: () -> Unit,
    onOpenPermissions: () -> Unit,
    onChangePin: () -> Unit,
    onSetSecurePin: () -> Unit,
    onOpenFocus: () -> Unit,
    onOpenDevices: () -> Unit,
    onOpenChores: () -> Unit,
    onOpenStats: () -> Unit,
    onThemeChanged: () -> Unit,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val sync = remember { SyncManager(context) }
    var v by remember { mutableStateOf(0) }
    @Suppress("UNUSED_EXPRESSION") v

    // Any change the parent makes is pushed to the child immediately.
    LaunchedEffect(v) {
        if (v > 0 && prefs.isParentDevice) SyncService.pushNow(context)
    }

    // Live time requests from the child device.
    var pendingRequest by remember { mutableStateOf<TimeRequest?>(null) }
    LaunchedEffect(Unit) {
        while (prefs.isParentDevice && prefs.syncConfigured) {
            val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { sync.readRequest() }
            pendingRequest = r?.takeIf { it.isPending }
            delay(4000)
        }
    }

    // On the parent device the numbers come from the child, live; otherwise they are local.
    var childStatus by remember { mutableStateOf(sync.cachedChildStatus()) }
    LaunchedEffect(Unit) {
        while (prefs.isParentDevice && prefs.syncConfigured) {
            childStatus = sync.cachedChildStatus()
            delay(3000)
        }
    }

    val remote = childStatus.takeIf { prefs.isParentDevice }
    val used = remote?.globalUsedSeconds ?: prefs.globalUsedSeconds
    val limit = prefs.globalLimitMinutes * 60 + prefs.bonusSecondsToday

    Column(
        Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(Nova.PageGradient))
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Eltern-Portal", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Nova.Ink)
            Spacer(Modifier.weight(1f))
            Text("Fertig", color = Nova.Primary, fontSize = 17.sp, modifier = Modifier.clickable { onExit() })
        }

        // ---- connection status ----
        if (prefs.syncConfigured) {
            val online = System.currentTimeMillis() - prefs.lastSyncAt < 120_000
            val c = if (online) Nova.Success else Nova.Warning
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp)
                    .clip(RoundedCornerShape(12.dp)).background(c.copy(alpha = 0.10f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(c))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (online) "Verbunden mit ${remote?.deviceName ?: "Kinder-Gerät"}"
                    else "Keine aktuelle Verbindung zum Kinder-Gerät",
                    fontSize = 13.sp, color = c
                )
            }
        }

        // ---- usage summary ----
        SectionHeader(if (prefs.isParentDevice) "Nutzung des Kindes heute" else "Heute genutzt")
        NovaCard {
            Column(Modifier.padding(16.dp)) {
                Text(TimeFmt.hm(used), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Nova.Ink)
                Text("von ${TimeFmt.hm(limit)} Tageslimit", fontSize = 14.sp, color = Nova.InkMuted)
                Spacer(Modifier.height(12.dp))
                ProgressBar(fraction = if (limit == 0) 1f else (used.toFloat() / limit).coerceIn(0f, 1f))
            }
        }

        // ---- live per-app usage from the child device ----
        if (remote != null && remote.perAppSeconds.isNotEmpty()) {
            SectionHeader("Apps auf dem Kinder-Gerät")
            NovaCard {
                remote.perAppSeconds.entries
                    .sortedByDescending { it.value }
                    .take(10)
                    .forEach { (pkg, secs) ->
                        val label = remote.perAppLabels[pkg] ?: pkg
                        val blocked = pkg in remote.blockedToday
                        NovaRow(title = label, subtitle = TimeFmt.hm(secs)) {
                            if (blocked) {
                                Text("Gesperrt", color = Nova.Danger, fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
            }
        }

        // ---- global limit ----
        SectionHeader("Tägliches Limit (Standard-Apps)")
        NovaCard {
            NovaRow(title = "Limit", subtitle = "Standard 1 Std · max. 2 Std") {
                Stepper(
                    value = "${prefs.globalLimitMinutes} Min",
                    onMinus = { prefs.globalLimitMinutes = (prefs.globalLimitMinutes - 15).coerceAtLeast(0); v++ },
                    onPlus = { prefs.globalLimitMinutes = (prefs.globalLimitMinutes + 15).coerceAtMost(Prefs.MAX_GLOBAL_LIMIT_MIN); v++ }
                )
            }
        }

        // ---- bedtime ----
        SectionHeader("Ruhezeit")
        NovaCard {
            NovaRow(title = "Ruhezeit aktiv") {
                NovaSwitch(checked = prefs.bedtimeEnabled) { prefs.bedtimeEnabled = it; v++ }
            }
            NovaRow(title = "Beginn") {
                Stepper(
                    value = TimeFmt.clock(prefs.bedtimeStartMin),
                    onMinus = { prefs.bedtimeStartMin = wrap(prefs.bedtimeStartMin - 30); v++ },
                    onPlus = { prefs.bedtimeStartMin = wrap(prefs.bedtimeStartMin + 30); v++ }
                )
            }
            NovaRow(title = "Ende") {
                Stepper(
                    value = TimeFmt.clock(prefs.bedtimeEndMin),
                    onMinus = { prefs.bedtimeEndMin = wrap(prefs.bedtimeEndMin - 30); v++ },
                    onPlus = { prefs.bedtimeEndMin = wrap(prefs.bedtimeEndMin + 30); v++ }
                )
            }
            NovaRow(title = "Beruhigender Ton") {
                NovaSwitch(checked = prefs.bedtimeSoundEnabled) { prefs.bedtimeSoundEnabled = it; v++ }
            }
        }

        // ---- blocked apps today ----
        SectionHeader("Heute gesperrte Apps")
        val blocked = prefs.getBlockedToday()
        val perApp = prefs.getPerAppSeconds()
        NovaCard {
            if (blocked.isEmpty()) {
                Text(
                    "Heute wurde noch keine App gesperrt.",
                    fontSize = 15.sp, color = Nova.InkMuted,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                blocked.entries.sortedByDescending { it.value }.forEach { (pkg, _) ->
                    val label = InstalledApps.labelFor(context, pkg)
                    val used = perApp[pkg] ?: 0
                    NovaRow(title = label, subtitle = "Genutzt: ${TimeFmt.hm(used)}") {
                        Text("Gesperrt", color = Nova.Danger, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // ---- Aus-Button ----
        SectionHeader("Für heute freischalten")
        NovaCard {
            Column(Modifier.padding(16.dp)) {
                val active = prefs.limitsDisabled()
                Text(
                    if (active) "Alle Limits sind bis 23:00 Uhr deaktiviert."
                    else "Deaktiviert alle Limits bis 23:00 Uhr des heutigen Tages.",
                    fontSize = 14.sp, color = Nova.InkMuted
                )
                Spacer(Modifier.height(12.dp))
                if (active) {
                    NovaButton(text = "Limits wieder aktivieren", color = Nova.Danger) {
                        prefs.clearOffButton(); v++
                    }
                } else {
                    NovaButton(text = "Aus-Button – bis 23:00 freischalten", color = Nova.Warning) {
                        prefs.activateOffButton(); v++
                    }
                }
            }
        }

        // ---- live time request from the child ----
        pendingRequest?.let { req ->
            SectionHeader("Anfrage vom Kind")
            NovaCard {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NovaPill("+${req.minutes} Min", Nova.Warning)
                        Spacer(Modifier.width(10.dp))
                        Text(TimeFmt.hm(((System.currentTimeMillis() - req.createdAt) / 1000).toInt()) + " her",
                            fontSize = 12.sp, color = Nova.InkFaint)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(req.reason, fontSize = 15.sp, color = Nova.Ink)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.weight(1f)) {
                            NovaButton(text = "Genehmigen", color = Nova.Success) {
                                thread(isDaemon = true) { sync.decideRequest(req, true) }
                                pendingRequest = null; v++
                            }
                        }
                        Box(Modifier.weight(1f)) {
                            NovaButtonTonal(text = "Ablehnen", color = Nova.Danger) {
                                thread(isDaemon = true) { sync.decideRequest(req, false) }
                                pendingRequest = null
                            }
                        }
                    }
                }
            }
        }

        // ---- focus mode (headline feature) ----
        SectionHeader("Fokus-Modus")
        NovaCard {
            val focus = prefs.focusSession()
            NovaRow(
                title = if (focus.isRunning()) "Fokus läuft: ${focus.label}" else "Fokus-Session starten",
                subtitle = if (focus.isRunning()) "Noch ${TimeFmt.hm(focus.remainingSeconds())}"
                else "Nur ausgewählte Apps, auf Zeit",
                onClick = onOpenFocus
            ) {
                if (focus.isRunning()) NovaPill("Aktiv", Nova.Focus) else Chevron()
            }
        }

        // ---- navigation ----
        SectionHeader("Verwaltung")
        NovaCard {
            NovaRow(title = "Apps & Kategorien", onClick = onOpenApps) { Chevron() }
            NovaRow(title = "Berechtigungen", onClick = onOpenPermissions) { Chevron() }
            NovaRow(title = "Geräte", subtitle = "Max. 3 pro Konto", onClick = onOpenDevices) { Chevron() }
            NovaRow(title = "Wochenbericht", subtitle = "Nutzung der letzten 7 Tage", onClick = onOpenStats) { Chevron() }
        }

        // ---- chores ----
        SectionHeader("Aufgaben & Belohnungen")
        NovaCard {
            val chores = prefs.getChores()
            val waiting = chores.count { it.isClaimed }
            NovaRow(
                title = "Aufgaben verwalten",
                subtitle = if (waiting > 0) "$waiting wartet auf Bestätigung"
                else "${chores.size} Aufgaben angelegt",
                onClick = onOpenChores
            ) {
                if (waiting > 0) NovaPill("$waiting neu", Nova.Warning) else Chevron()
            }
        }

        // ---- protection level ----
        SectionHeader("Schutz-Stufe")
        NovaCard {
            val owner = com.familylink.ios.admin.DeviceOwner.isDeviceOwner(context)
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NovaPill(
                        if (owner) "Maximal" else "Standard",
                        if (owner) Nova.Success else Nova.Warning
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (owner) "Geräteinhaber aktiv" else "Geräteinhaber nicht aktiv",
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Nova.Ink
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    if (owner)
                        "HOME wird bei Sperren blockiert, Einstellungen sind ausgeblendet, " +
                        "abgesicherter Modus, Gastprofil, Zurücksetzen und Deinstallation sind " +
                        "vom System verboten. Daten werden nie gelöscht."
                    else
                        "Die App schützt so gut es ohne Geräteinhaber geht. Für echte " +
                        "Unumgehbarkeit muss die App einmalig als Geräteinhaber eingerichtet " +
                        "werden (Anleitung im README).",
                    fontSize = 13.sp, color = Nova.InkMuted
                )
            }
        }

        // ---- instant pause ----
        SectionHeader("Sofort-Pause")
        NovaCard {
            Column(Modifier.padding(16.dp)) {
                val paused = prefs.focusSession().let { it.isRunning() && it.label == "Pause" }
                Text(
                    if (paused) "Das Gerät ist pausiert. Nur Telefon ist erreichbar."
                    else "Sperrt das Kindergerät sofort für 30 Minuten (z. B. beim Essen).",
                    fontSize = 14.sp, color = Nova.InkMuted
                )
                Spacer(Modifier.height(12.dp))
                if (paused) {
                    NovaButton(text = "Pause beenden", color = Nova.Success) {
                        sync.stopFocus(); v++; SyncService.pushNow(context)
                    }
                } else {
                    NovaButton(text = "Jetzt pausieren (30 Min)", color = Nova.Danger) {
                        sync.startFocus("Pause", 30, emptyList()); v++; SyncService.pushNow(context)
                    }
                }
            }
        }

        // ---- appearance ----
        SectionHeader("Darstellung")
        NovaCard {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    ThemeMode.SYSTEM to "System",
                    ThemeMode.LIGHT to "Hell",
                    ThemeMode.DARK to "Dunkel"
                ).forEach { (m, label) ->
                    val sel = prefs.themeMode == m
                    Box(
                        Modifier.weight(1f)
                            .clip(RoundedCornerShape(Nova.RadiusControl.dp))
                            .background(if (sel) Nova.Primary else Nova.Fill)
                            .clickable { prefs.themeMode = m; v++; onThemeChanged() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            color = if (sel) Color.White else Nova.InkMuted
                        )
                    }
                }
            }
        }

        // ---- security ----
        SectionHeader("Sicherheit")
        NovaCard {
            NovaRow(title = "PIN ändern", subtitle = "4-stellige Zugangs-PIN", onClick = onChangePin) { Chevron() }
            NovaRow(
                title = if (prefs.isSecurePinSet) "Sicherheits-PIN ändern" else "Sicherheits-PIN festlegen",
                subtitle = "Lange PIN für Zeitverlängerung",
                onClick = onSetSecurePin
            ) { Chevron() }
        }

        // ---- device: system settings are locked by default; released here temporarily ----
        SectionHeader("Gerät")
        NovaCard {
            Column(Modifier.padding(16.dp)) {
                val open = prefs.settingsUnlocked()
                Text(
                    if (open) "Systemeinstellungen sind vorübergehend freigegeben."
                    else "Die Systemeinstellungen des Geräts sind gesperrt. Hier für 1 Minute freigeben und öffnen.",
                    fontSize = 14.sp, color = Nova.InkMuted
                )
                Spacer(Modifier.height(12.dp))
                NovaButton(text = "Einstellungen öffnen (1 Min)", color = Nova.Primary) {
                    prefs.unlockSettings(1)
                    v++
                    runCatching {
                        context.startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "Das Eltern-Portal ist jederzeit mit der PIN erreichbar.",
            fontSize = 12.sp, color = Nova.InkFaint
        )
        Spacer(Modifier.height(24.dp))
    }
}

private fun wrap(m: Int): Int = ((m % 1440) + 1440) % 1440

@Composable
private fun ProgressBar(fraction: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0x22000000))
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (fraction >= 1f) Nova.Danger else Nova.Success)
        )
    }
}

@Composable
private fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepBtn("−", onMinus)
        Text(value, modifier = Modifier.padding(horizontal = 12.dp), fontSize = 17.sp, color = Nova.Ink)
        StepBtn("+", onPlus)
    }
}

@Composable
private fun StepBtn(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x11000000))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 20.sp, color = Nova.Primary)
    }
}

@Composable
private fun Chevron() {
    Text("›", color = Nova.InkFaint, fontSize = 22.sp)
}
