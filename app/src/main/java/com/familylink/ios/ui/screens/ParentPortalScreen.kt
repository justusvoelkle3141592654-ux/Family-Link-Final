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
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
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
import com.familylink.ios.data.UsageMode
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

    // Manual refresh: forces a full exchange instead of waiting for the next tick.
    var refreshing by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableStateOf(0) }
    fun refreshNow() {
        if (refreshing) return
        refreshing = true
        thread(isDaemon = true) {
            runCatching { sync.syncNow() }
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                refreshing = false
                refreshTick++
                v++
            }
        }
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
        // The parent app has no background service, so it fetches live data itself while open.
        while (prefs.isParentDevice && prefs.syncConfigured) {
            val fresh = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { sync.fetchChildStatus() }.getOrNull()
            }
            childStatus = fresh ?: sync.cachedChildStatus()
            delay(2500)
        }
    }

    // The child's app list with its real categories. Much slower cadence than the status: it is
    // a bigger payload and only changes when apps are installed, removed or re-categorised.
    LaunchedEffect(Unit) {
        while (prefs.isParentDevice && prefs.syncConfigured) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { sync.fetchChildApps() }
            }
            delay(20_000)
        }
    }

    // Re-read after a manual refresh so the new numbers appear right away.
    LaunchedEffect(refreshTick) {
        if (refreshTick > 0) childStatus = sync.cachedChildStatus()
    }

    val remote = childStatus.takeIf { prefs.isParentDevice }
    val used = remote?.globalUsedSeconds ?: prefs.globalUsedSeconds
    val limit = remote?.limitSeconds?.takeIf { it > 0 }
        ?: (prefs.globalLimitMinutes * 60 + prefs.bonusSecondsToday)

    // The parent app opens on a compact dashboard; everything else lives behind the ☰ menu.
    // The child device keeps the single long page it always had.
    var showSettings by remember { mutableStateOf(!prefs.isParentDevice) }

    if (prefs.isParentDevice && !showSettings) {
        ParentDashboard(
            prefs = prefs,
            remote = remote,
            used = used,
            limit = limit,
            refreshing = refreshing,
            pendingRequest = pendingRequest,
            onRefresh = { refreshNow() },
            onOpenMenu = { showSettings = true },
            onGrant = { minutes ->
                prefs.addBonusMinutes(minutes)
                v++
            },
            onDecideRequest = { req, approve ->
                thread(isDaemon = true) { sync.decideRequest(req, approve) }
                pendingRequest = null
                v++
            },
            onLockFor = { minutes -> sync.lockForMinutes(minutes); v++ },
            onLockNow = { sync.lockDevice(); v++ },
            onUnlock = { sync.unlockDevice(); sync.stopFocus(); v++ },
            onApproveChore = { id ->
                // Network call inside — never on the main thread.
                thread(isDaemon = true) { sync.approveChore(id) }
                v++
            },
            onOpenChores = onOpenChores,
            onExit = onExit
        )
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(Nova.PageGradient))
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (prefs.isParentDevice) {
                Text(
                    "‹ Übersicht", color = Nova.Primary, fontSize = 17.sp,
                    modifier = Modifier.clickable { showSettings = false }.padding(end = 12.dp)
                )
            }
            Text(
                if (prefs.isParentDevice) "Einstellungen" else "Eltern-Portal",
                fontSize = if (prefs.isParentDevice) 26.sp else 34.sp,
                fontWeight = FontWeight.Bold, color = Nova.Ink
            )
            Spacer(Modifier.weight(1f))
            if (prefs.syncConfigured) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape)
                        .background(Nova.Primary.copy(alpha = 0.12f))
                        .clickable(enabled = !refreshing) { refreshNow() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "Aktualisieren",
                        tint = if (refreshing) Nova.InkFaint else Nova.Primary,
                        modifier = Modifier.size(21.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
            }
            Text("Fertig", color = Nova.Primary, fontSize = 17.sp, modifier = Modifier.clickable { onExit() })
        }
        if (refreshing) {
            Text("Aktualisiere…", fontSize = 12.sp, color = Nova.InkMuted,
                modifier = Modifier.padding(top = 4.dp))
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
            // Never fail silently: if the server rejected a write, say exactly why.
            val err = prefs.lastSyncError
            if (err.isNotBlank()) {
                Text(
                    "Server-Fehler: $err",
                    fontSize = 11.sp, color = Nova.Danger,
                    modifier = Modifier.padding(top = 6.dp, start = 4.dp)
                )
            }
        }

        // ---- usage summary ----
        SectionHeader(if (prefs.isParentDevice) "Nutzung des Kindes heute" else "Heute genutzt")
        NovaCard {
            Column(Modifier.padding(16.dp)) {
                if (prefs.isParentDevice && remote == null) {
                    // Be explicit instead of silently showing 0 — that looked like "never used".
                    Text("Noch keine Daten", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Nova.InkMuted)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (!prefs.syncConfigured)
                            "Dieses Gerät ist mit keinem Konto verbunden."
                        else
                            "Warte auf das Kinder-Gerät. Es meldet sich, sobald es online ist " +
                            "und der Nutzungszugriff dort erteilt wurde.",
                        fontSize = 13.sp, color = Nova.InkMuted
                    )
                } else {
                    // Whole-phone screen time — the number a parent actually asks about.
                    val totalDevice = remote?.totalDeviceSeconds ?: used
                    Text(TimeFmt.hm(totalDevice), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Nova.Ink)
                    Text("Handynutzung gesamt heute", fontSize = 13.sp, color = Nova.InkMuted)

                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Angerechnet", fontSize = 13.sp, color = Nova.InkMuted, modifier = Modifier.weight(1f))
                        Text(
                            "${TimeFmt.hm(used)} von ${TimeFmt.hm(limit)}",
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Nova.Ink
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    ProgressBar(fraction = if (limit == 0) 1f else (used.toFloat() / limit).coerceIn(0f, 1f))

                    remote?.let { r ->
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (r.bedtimeActive) NovaPill("Ruhezeit", Nova.Night)
                            if (r.focusLabel.isNotBlank()) NovaPill("Fokus: ${r.focusLabel}", Nova.Focus)
                            if (r.bonusSeconds > 0) NovaPill("+${r.bonusSeconds / 60} Bonus", Nova.Success)
                            if (r.batteryPercent in 0..100) NovaPill("Akku ${r.batteryPercent}%",
                                if (r.batteryPercent < 20) Nova.Danger else Nova.InkMuted)
                        }
                        Spacer(Modifier.height(10.dp))
                        val age = r.ageSeconds()
                        Text(
                            if (age < 60) "Aktualisiert gerade eben"
                            else "Zuletzt aktualisiert vor ${TimeFmt.hm(age)}",
                            fontSize = 11.sp, color = Nova.InkFaint
                        )
                    }
                }
            }
        }

        // ---- live per-app usage from the child device ----
        if (remote != null && remote.perAppSeconds.isNotEmpty()) {
            val ranked = remote.perAppSeconds.entries.sortedByDescending { it.value }
            SectionHeader("Nutzungszeit pro App (${ranked.size})")
            NovaCard {
                Column(Modifier.padding(vertical = 6.dp)) {
                    val top = ranked.first().value.coerceAtLeast(1)
                    ranked.take(15).forEach { (pkg, secs) ->
                        val label = remote.perAppLabels[pkg] ?: pkg
                        val blocked = pkg in remote.blockedToday
                        val cat = prefs.categoryOf(pkg)
                        val catColor = when (cat) {
                            com.familylink.ios.data.AppCategory.PLUS -> Nova.CatPlus
                            com.familylink.ios.data.AppCategory.LIMIT -> Nova.CatLimit
                            com.familylink.ios.data.AppCategory.BLOCKED -> Nova.CatBlocked
                            else -> Nova.CatStandard
                        }
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 7.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(catColor))
                                Spacer(Modifier.width(8.dp))
                                Text(label, fontSize = 15.sp, color = Nova.Ink, modifier = Modifier.weight(1f))
                                if (blocked) {
                                    NovaPill("Gesperrt", Nova.Danger)
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(
                                    TimeFmt.hm(secs), fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold, color = Nova.InkMuted
                                )
                            }
                            Spacer(Modifier.height(5.dp))
                            Box(
                                Modifier.fillMaxWidth().height(5.dp)
                                    .clip(RoundedCornerShape(3.dp)).background(Nova.Fill)
                            ) {
                                Box(
                                    Modifier.fillMaxWidth((secs.toFloat() / top).coerceIn(0.02f, 1f))
                                        .height(5.dp).clip(RoundedCornerShape(3.dp)).background(catColor)
                                )
                            }
                        }
                    }
                    if (ranked.size > 15) {
                        Text(
                            "… und ${ranked.size - 15} weitere",
                            fontSize = 12.sp, color = Nova.InkFaint,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                    // The total, spelled out under the breakdown, so the sum of the list and the
                    // whole-phone number are always visible next to each other.
                    Box(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                            .height(1.dp).background(Nova.Fill)
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Gesamte Nutzungszeit heute", fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold, color = Nova.Ink,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            TimeFmt.hm(remote.totalDeviceSeconds), fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold, color = Nova.Ink
                        )
                    }
                }
            }
        }

        // ---- how time is measured ----
        SectionHeader("Zeitmessung")
        NovaCard {
            Column(Modifier.padding(16.dp)) {
                listOf(
                    UsageMode.SYSTEM_TOTAL to ("Handynutzung gesamt" to
                        "Die gesamte Bildschirmzeit zählt — abzüglich der zugelassenen Plus-Apps. " +
                        "Auch nicht eingeordnete Apps kosten Zeit."),
                    UsageMode.CATEGORIES to ("Nur eingeordnete Apps" to
                        "Es zählen nur Apps in Standard oder Limit. Neue Apps kosten erst Zeit, " +
                        "wenn du sie einordnest.")
                ).forEach { (m, texts) ->
                    val sel = prefs.usageMode == m
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(Nova.RadiusControl.dp))
                            .background(if (sel) Nova.Primary.copy(alpha = 0.10f) else Color.Transparent)
                            .clickable {
                                prefs.usageMode = m; v++
                                SyncService.pushNow(context)
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            Modifier.padding(top = 2.dp).size(20.dp).clip(CircleShape)
                                .background(if (sel) Nova.Primary else Nova.Fill),
                            contentAlignment = Alignment.Center
                        ) {
                            if (sel) Box(Modifier.size(8.dp).clip(CircleShape).background(Color.White))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(texts.first, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Nova.Ink)
                            Spacer(Modifier.height(2.dp))
                            Text(texts.second, fontSize = 12.sp, color = Nova.InkMuted)
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

        // ---- absolute ceiling over every app ----
        SectionHeader("Gesamtlimit (alle Apps)")
        NovaCard {
            NovaRow(
                title = "Gesamtlimit aktiv",
                subtitle = "Zählt jede App mit — auch Plus. Nicht durch Bonus, Verlängerung " +
                    "oder den Aus-Knopf zu umgehen."
            ) {
                NovaSwitch(checked = prefs.hardCapEnabled) { prefs.hardCapEnabled = it; v++ }
            }
            if (prefs.hardCapEnabled) {
                NovaRow(title = "Maximum", subtitle = "max. ${Prefs.MAX_HARDCAP_MIN / 60} Stunden") {
                    Stepper(
                        value = TimeFmt.hm(prefs.hardCapMinutes * 60),
                        onMinus = { prefs.hardCapMinutes = prefs.hardCapMinutes - 15; v++ },
                        onPlus = { prefs.hardCapMinutes = prefs.hardCapMinutes + 15; v++ }
                    )
                }
                NovaRow(
                    title = "Bei Missachtung",
                    subtitle = "Erst die Sperrseite. Wer trotzdem weitermacht, bekommt den " +
                        "Bildschirm gesperrt — ab ${Prefs.HARDCAP_LOCK_ALWAYS_FROM} Versuchen " +
                        "bei jedem weiteren Versuch."
                ) {
                    val hits = if (prefs.isParentDevice) 0 else prefs.hardCapHitsToday
                    if (hits > 0) NovaPill("$hits heute", Nova.Danger)
                }
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
        SectionHeader("Sperren")
        NovaCard {
            val focus = prefs.focusSession()
            // Manual lock: no end time, stays until it is lifted.
            NovaRow(
                title = "Gerät komplett sperren",
                subtitle = if (prefs.manualLockEnabled) "Gesperrt — tippe zum Aufheben"
                else "Ohne Zeitende. Telefon und Notruf bleiben erreichbar."
            ) {
                NovaSwitch(checked = prefs.manualLockEnabled) {
                    prefs.manualLockEnabled = it
                    if (!it) prefs.manualLockReason = ""
                    v++
                }
            }
            // Timed lock: nothing but phone and emergency for a fixed stretch.
            NovaRow(
                title = if (focus.isRunning() && focus.allowed.isEmpty())
                    "Gesperrt auf Zeit — noch ${TimeFmt.hm(focus.remainingSeconds())}"
                else "Sperren auf Zeit",
                subtitle = "30 Min · 1 Std · 2 Std"
            ) {
                if (focus.isRunning() && focus.allowed.isEmpty()) {
                    Box(
                        Modifier.clip(RoundedCornerShape(10.dp))
                            .background(Nova.Success.copy(alpha = 0.15f))
                            .clickable { sync.stopFocus(); v++ }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text("Freigeben", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Nova.Success)
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(30, 60, 120).forEach { m ->
                            Box(
                                Modifier.clip(RoundedCornerShape(9.dp))
                                    .background(Nova.Danger.copy(alpha = 0.13f))
                                    .clickable { sync.lockForMinutes(m); v++ }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    if (m >= 60) "${m / 60}h" else "${m}m",
                                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Nova.Danger
                                )
                            }
                        }
                    }
                }
            }
            // Focus keeps its own entry: unlike a lock it leaves chosen apps usable.
            NovaRow(
                title = if (focus.isRunning() && focus.allowed.isNotEmpty())
                    "Fokus läuft: ${focus.label}" else "Fokus (nur bestimmte Apps)",
                subtitle = if (focus.isRunning() && focus.allowed.isNotEmpty())
                    "Noch ${TimeFmt.hm(focus.remainingSeconds())}"
                else "Ausgewählte Apps bleiben erlaubt, auf Zeit",
                onClick = onOpenFocus
            ) {
                if (focus.isRunning() && focus.allowed.isNotEmpty()) NovaPill("Aktiv", Nova.Focus) else Chevron()
            }
        }

        // ---- navigation ----
        SectionHeader("Verwaltung")
        NovaCard {
            NovaRow(title = "Apps & Kategorien", onClick = onOpenApps) { Chevron() }
            if (!prefs.isParentDevice) {
                // Only the supervised device needs system permissions.
                NovaRow(title = "Berechtigungen", onClick = onOpenPermissions) { Chevron() }
            }
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

        // ---- protection level (supervised device only) ----
        if (!prefs.isParentDevice) {
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

/**
 * The parent app's landing screen: everything that matters at a glance, nothing to scroll for.
 *
 *  - how much of the day's budget is gone and what is left,
 *  - the absolute ceiling across all apps,
 *  - the three apps the child used most today,
 *  - one prominent button to grant extra time,
 *  - a pending request from the child, answerable right here.
 *
 * Every setting lives behind the ☰ button instead of on one endless page.
 */
@Composable
private fun ParentDashboard(
    prefs: Prefs,
    remote: com.familylink.ios.sync.ChildStatus?,
    used: Int,
    limit: Int,
    refreshing: Boolean,
    pendingRequest: TimeRequest?,
    onRefresh: () -> Unit,
    onOpenMenu: () -> Unit,
    onGrant: (Int) -> Unit,
    onDecideRequest: (TimeRequest, Boolean) -> Unit,
    onLockFor: (Int) -> Unit,
    onLockNow: () -> Unit,
    onUnlock: () -> Unit,
    onApproveChore: (String) -> Unit,
    onOpenChores: () -> Unit,
    onExit: () -> Unit
) {
    val online = prefs.syncConfigured && System.currentTimeMillis() - prefs.lastSyncAt < 120_000
    val remaining = (limit - used).coerceAtLeast(0)
    val total = remote?.totalDeviceSeconds ?: 0
    val cap = prefs.hardCapMinutes * 60
    val timedLock = prefs.focusSession()
    val lockedNow = prefs.manualLockEnabled
    val lockedTimed = timedLock.isRunning()

    Column(
        Modifier.fillMaxSize()
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(Nova.PageGradient))
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // ---- header: ☰ leads to every setting ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
                    .background(Nova.Primary.copy(alpha = 0.12f))
                    .clickable { onOpenMenu() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Menu, "Einstellungen", tint = Nova.Primary, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Übersicht", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Nova.Ink)
                Text(
                    if (online) remote?.deviceName ?: "Kinder-Gerät" else "Keine aktuelle Verbindung",
                    fontSize = 12.sp, color = if (online) Nova.Success else Nova.Warning
                )
            }
            if (prefs.syncConfigured) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape)
                        .background(Nova.Primary.copy(alpha = 0.12f))
                        .clickable(enabled = !refreshing) { onRefresh() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Refresh, "Aktualisieren",
                        tint = if (refreshing) Nova.InkFaint else Nova.Primary,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
        }
        if (refreshing) {
            Text("Aktualisiere…", fontSize = 12.sp, color = Nova.InkMuted, modifier = Modifier.padding(top = 6.dp))
        }

        Spacer(Modifier.height(16.dp))

        if (remote == null) {
            NovaCard {
                Column(Modifier.padding(18.dp)) {
                    Text("Noch keine Daten", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Nova.InkMuted)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (!prefs.syncConfigured) "Dieses Gerät ist mit keinem Konto verbunden."
                        else "Warte auf das Kinder-Gerät.",
                        fontSize = 13.sp, color = Nova.InkMuted
                    )
                }
            }
        } else {
            // ---- headline: what is left of the day's budget ----
            NovaCard {
                Column(Modifier.padding(20.dp)) {
                    Text("Noch übrig heute", fontSize = 13.sp, color = Nova.InkMuted)
                    Text(
                        TimeFmt.hm(remaining),
                        fontSize = 46.sp, fontWeight = FontWeight.ExtraBold,
                        color = if (remaining == 0) Nova.Danger else Nova.Primary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${TimeFmt.hm(used)} von ${TimeFmt.hm(limit)} verbraucht",
                        fontSize = 13.sp, color = Nova.InkMuted
                    )
                    Spacer(Modifier.height(10.dp))
                    ProgressBar(if (limit == 0) 1f else (used.toFloat() / limit).coerceIn(0f, 1f))

                    Spacer(Modifier.height(16.dp))
                    // ---- the absolute ceiling across every app ----
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Gesamtlimit (alle Apps)", fontSize = 13.sp, color = Nova.InkMuted,
                            modifier = Modifier.weight(1f))
                        Text(
                            if (prefs.hardCapEnabled) "${TimeFmt.hm(total)} / ${TimeFmt.hm(cap)}" else "aus",
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            color = if (prefs.hardCapEnabled && total >= cap) Nova.Danger else Nova.Ink
                        )
                    }
                    if (prefs.hardCapEnabled) {
                        Spacer(Modifier.height(8.dp))
                        ProgressBar((total.toFloat() / cap.coerceAtLeast(1)).coerceIn(0f, 1f))
                    }

                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (remote.bedtimeActive) NovaPill("Ruhezeit", Nova.Night)
                        if (remote.focusLabel.isNotBlank()) NovaPill("Fokus: ${remote.focusLabel}", Nova.Focus)
                        if (remote.bonusSeconds > 0) NovaPill("+${remote.bonusSeconds / 60} Bonus", Nova.Success)
                        if (remote.batteryPercent in 0..100) NovaPill(
                            "Akku ${remote.batteryPercent}%",
                            if (remote.batteryPercent < 20) Nova.Danger else Nova.InkMuted
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    val age = remote.ageSeconds()
                    Text(
                        if (age < 60) "Aktualisiert gerade eben" else "Zuletzt aktualisiert vor ${TimeFmt.hm(age)}",
                        fontSize = 11.sp, color = Nova.InkFaint
                    )
                }
            }

            // ---- top 3 apps ----
            Spacer(Modifier.height(16.dp))
            SectionHeader("Meistgenutzt heute")
            NovaCard {
                val top3 = remote.perAppSeconds.entries.sortedByDescending { it.value }.take(3)
                if (top3.isEmpty()) {
                    Text(
                        "Heute noch keine App genutzt.",
                        fontSize = 13.sp, color = Nova.InkMuted, modifier = Modifier.padding(16.dp)
                    )
                } else {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        val top = top3.first().value.coerceAtLeast(1)
                        top3.forEachIndexed { i, (pkg, secs) ->
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${i + 1}", fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                    color = Nova.InkFaint, modifier = Modifier.width(22.dp)
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        remote.perAppLabels[pkg] ?: pkg,
                                        fontSize = 15.sp, color = Nova.Ink
                                    )
                                    Spacer(Modifier.height(5.dp))
                                    Box(
                                        Modifier.fillMaxWidth().height(5.dp)
                                            .clip(RoundedCornerShape(3.dp)).background(Nova.Fill)
                                    ) {
                                        Box(
                                            Modifier.fillMaxWidth((secs.toFloat() / top).coerceIn(0.02f, 1f))
                                                .height(5.dp).clip(RoundedCornerShape(3.dp))
                                                .background(Nova.Primary)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    TimeFmt.hm(secs), fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold, color = Nova.InkMuted
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---- a request waiting for an answer ----
        if (pendingRequest != null) {
            Spacer(Modifier.height(16.dp))
            SectionHeader("Anfrage vom Kind")
            NovaCard {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "+${pendingRequest.minutes} Minuten",
                        fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Nova.Ink
                    )
                    Text(pendingRequest.reason, fontSize = 13.sp, color = Nova.InkMuted)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.weight(1f)) {
                            NovaButton(text = "Geben", color = Nova.Success) {
                                onDecideRequest(pendingRequest, true)
                            }
                        }
                        Box(Modifier.weight(1f)) {
                            NovaButtonTonal(text = "Ablehnen") { onDecideRequest(pendingRequest, false) }
                        }
                    }
                }
            }
        }

        // ---- lock the device, on a timer or outright ----
        Spacer(Modifier.height(16.dp))
        SectionHeader("Gerät sperren")
        NovaCard {
            Column(Modifier.padding(16.dp)) {
                when {
                    lockedNow -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NovaPill("Gesperrt", Nova.Danger)
                            Spacer(Modifier.width(10.dp))
                            Text("Ohne Zeitende — bis du es aufhebst.", fontSize = 13.sp, color = Nova.InkMuted)
                        }
                        Spacer(Modifier.height(12.dp))
                        NovaButton(text = "Sperre aufheben", color = Nova.Success) { onUnlock() }
                    }
                    lockedTimed -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NovaPill("Gesperrt auf Zeit", Nova.Danger)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Noch ${TimeFmt.hm(timedLock.remainingSeconds())}",
                                fontSize = 13.sp, color = Nova.InkMuted
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        NovaButton(text = "Jetzt freigeben", color = Nova.Success) { onUnlock() }
                    }
                    else -> {
                        Text("Auf Zeit sperren", fontSize = 13.sp, color = Nova.InkMuted)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(30, 60, 120).forEach { m ->
                                Box(Modifier.weight(1f)) {
                                    NovaButtonTonal(
                                        text = if (m >= 60) "${m / 60} Std" else "$m Min",
                                        color = Nova.Danger
                                    ) { onLockFor(m) }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        NovaButton(text = "Komplett sperren", color = Nova.Danger) { onLockNow() }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Telefon und Notruf bleiben immer erreichbar.",
                            fontSize = 12.sp, color = Nova.InkFaint
                        )
                    }
                }
            }
        }

        // ---- chores waiting to be confirmed ----
        val chores = prefs.getChores()
        val claimed = chores.filter { it.isClaimed }
        Spacer(Modifier.height(16.dp))
        SectionHeader("Aufgaben")
        NovaCard {
            Column(Modifier.padding(16.dp)) {
                if (claimed.isEmpty()) {
                    Text(
                        if (chores.isEmpty()) "Noch keine Aufgaben angelegt."
                        else "${chores.count { it.isOpen }} offen · nichts zu bestätigen.",
                        fontSize = 13.sp, color = Nova.InkMuted
                    )
                } else {
                    Text(
                        "${claimed.size} erledigt gemeldet",
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Nova.Ink
                    )
                    Spacer(Modifier.height(10.dp))
                    claimed.take(3).forEach { chore ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(chore.title, fontSize = 15.sp, color = Nova.Ink)
                                Text(
                                    "+${chore.rewardMinutes} Min Bonus",
                                    fontSize = 12.sp, color = Nova.InkMuted
                                )
                            }
                            Box(
                                Modifier.clip(RoundedCornerShape(10.dp))
                                    .background(Nova.Success.copy(alpha = 0.15f))
                                    .clickable { onApproveChore(chore.id) }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    "Bestätigen", fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold, color = Nova.Success
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                NovaButtonTonal(text = "Aufgaben verwalten", onClick = onOpenChores)
            }
        }

        // ---- grant extra time straight from here ----
        Spacer(Modifier.height(16.dp))
        SectionHeader("Verlängerung geben")
        NovaCard {
            Column(Modifier.padding(16.dp)) {
                val left = prefs.remainingBonusMinutes()
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(10, 15, 30).forEach { m ->
                        Box(Modifier.weight(1f)) {
                            NovaButton(
                                text = "+$m",
                                color = Nova.Danger,
                                enabled = left >= m
                            ) { onGrant(m) }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    if (left > 0) "Heute noch $left Minuten möglich (max. ${Prefs.MAX_BONUS_MIN} pro Tag)."
                    else "Das Tagesmaximum an Verlängerung ist aufgebraucht.",
                    fontSize = 12.sp, color = Nova.InkMuted
                )
                if (prefs.hardCapEnabled) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Das Gesamtlimit von ${TimeFmt.hm(cap)} gilt trotzdem — eine Verlängerung " +
                            "hebt es nicht auf.",
                        fontSize = 12.sp, color = Nova.InkFaint
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        NovaButtonTonal(text = "Alle Einstellungen", onClick = onOpenMenu)
        Spacer(Modifier.height(10.dp))
        Text(
            "Fertig", color = Nova.Primary, fontSize = 16.sp,
            modifier = Modifier.fillMaxWidth().clickable { onExit() }.padding(12.dp)
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
