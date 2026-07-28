package com.familylink.ios.ui.screens

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
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
import com.familylink.ios.service.ParentNotifications
import com.familylink.ios.service.ParentWatchService
import com.familylink.ios.data.LimitScope
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
import com.familylink.ios.ui.components.NovaDivider
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
    // Which group of settings is on screen. Null means "all of them", which is what the child
    // device shows — the parent always arrives with one group picked from the menu sheet, so it
    // never faces the whole endless list at once.
    var settingsGroup by remember { mutableStateOf<String?>(null) }
    var showDetails by remember { mutableStateOf(false) }
    /** 0 = Bildschirmzeit, 1 = Einstellungen, 2 = Aufgaben. */
    var tab by remember { mutableStateOf(0) }

    fun showGroup(group: String): Boolean = settingsGroup == null || settingsGroup == group

    // Android 13+ needs an explicit grant before any notification is shown.
    var notificationsAllowed by remember { mutableStateOf(ParentNotifications.permitted(context)) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notificationsAllowed = granted }

    fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsAllowed) {
            runCatching { notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
        }
    }

    // Keep the watcher in step with the setting whenever the portal is opened.
    LaunchedEffect(Unit) { ParentWatchService.sync(context) }

    // ---- the parent app's shell: three tabs along the bottom, as in the reference ----
    if (prefs.isParentDevice && !showDetails && settingsGroup == null) {
        Box(Modifier.fillMaxSize().background(Nova.Canvas)) {
            when (tab) {
                1 -> SettingsList(onPick = { group -> settingsGroup = group; showSettings = true })
                2 -> {
                    // Chores live on their own page; the tab just hands over to it.
                    LaunchedEffect(Unit) { tab = 0; onOpenChores() }
                }
                else -> ParentDashboard(
                    prefs = prefs,
                    remote = remote,
                    used = used,
                    limit = limit,
                    refreshing = refreshing,
                    pendingRequest = pendingRequest,
                    onRefresh = { refreshNow() },
                    onOpenMenu = { tab = 1 },
                    onGrant = { minutes, asBonus -> sync.grantTime(minutes, asBonus); v++ },
                    onDecideRequest = { req, approve ->
                        thread(isDaemon = true) { sync.decideRequest(req, approve) }
                        pendingRequest = null
                        v++
                    },
                    onLockFor = { minutes -> sync.lockForMinutes(minutes); v++ },
                    onLockNow = { sync.lockDevice(); v++ },
                    onUnlock = { sync.unlockDevice(); sync.stopFocus(); v++ },
                    onLockScreen = { minutes -> sync.lockScreenForMinutes(minutes); v++ },
                    onReleaseScreen = { sync.releaseScreenLock(); v++ },
                    onApproveChore = { id ->
                        thread(isDaemon = true) { sync.approveChore(id) }
                        v++
                    },
                    onOpenChores = onOpenChores,
                    onOpenDetails = { showDetails = true },
                    onExit = onExit
                )
            }
            BottomBar(
                current = tab,
                onSelect = { tab = it },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
        return
    }

    if (prefs.isParentDevice && showDetails) {
        UsageDetailScreen(
            prefs = prefs,
            remote = remote,
            used = used,
            limit = limit,
            onBack = { showDetails = false }
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
                    modifier = Modifier
                        .clickable { showSettings = false; settingsGroup = null; tab = 1 }
                        .padding(end = 12.dp)
                )
            }
            Text(
                if (prefs.isParentDevice) settingsGroup?.let { GROUP_TITLES[it] } ?: "Einstellungen"
                else "Eltern-Portal",
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
        if (showGroup("zeit")) {
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
        SectionHeader("Limit für Standard-Apps")
        NovaCard {
            ScopePicker(prefs.limitScope) { prefs.limitScope = it; v++ }
            if (prefs.limitScope != LimitScope.WEEK) {
                NovaRow(title = "Pro Tag", subtitle = "Standard 1 Std · max. 2 Std") {
                    Stepper(
                        value = TimeFmt.hm(prefs.globalLimitMinutes * 60),
                        onMinus = { prefs.globalLimitMinutes = (prefs.globalLimitMinutes - 15).coerceAtLeast(0); v++ },
                        onPlus = { prefs.globalLimitMinutes = (prefs.globalLimitMinutes + 15).coerceAtMost(Prefs.MAX_GLOBAL_LIMIT_MIN); v++ }
                    )
                }
            }
            if (prefs.limitScope != LimitScope.DAY) {
                NovaRow(
                    title = "Pro Woche",
                    subtitle = "Ein Topf für die ganze Woche — frei einteilbar."
                ) {
                    Stepper(
                        value = TimeFmt.hm(prefs.weeklyLimitMinutes * 60),
                        onMinus = { prefs.weeklyLimitMinutes = prefs.weeklyLimitMinutes - 30; v++ },
                        onPlus = { prefs.weeklyLimitMinutes = prefs.weeklyLimitMinutes + 30; v++ }
                    )
                }
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
                ScopePicker(prefs.hardCapScope) { prefs.hardCapScope = it; v++ }
                if (prefs.hardCapScope != LimitScope.WEEK) {
                    NovaRow(title = "Pro Tag", subtitle = "max. ${Prefs.MAX_HARDCAP_MIN / 60} Stunden") {
                        Stepper(
                            value = TimeFmt.hm(prefs.hardCapMinutes * 60),
                            onMinus = { prefs.hardCapMinutes = prefs.hardCapMinutes - 15; v++ },
                            onPlus = { prefs.hardCapMinutes = prefs.hardCapMinutes + 15; v++ }
                        )
                    }
                }
                if (prefs.hardCapScope != LimitScope.DAY) {
                    NovaRow(title = "Pro Woche", subtitle = "Gilt für die ganze Woche") {
                        Stepper(
                            value = TimeFmt.hm(prefs.weeklyHardCapMinutes * 60),
                            onMinus = { prefs.weeklyHardCapMinutes = prefs.weeklyHardCapMinutes - 30; v++ },
                            onPlus = { prefs.weeklyHardCapMinutes = prefs.weeklyHardCapMinutes + 30; v++ }
                        )
                    }
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

        }

        // ---- blocked apps today ----
        if (showGroup("apps")) {
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

        }

        // ---- locking: manual, timed, and focus ----
        if (showGroup("sperren")) {
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

        }

        // ---- navigation ----
        if (showGroup("verwaltung")) {
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
        }
        if (showGroup("schutz")) {
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

        }

        // ---- appearance ----
        // ---- notifications (parent device only) ----
        if (prefs.isParentDevice && showGroup("meldungen")) {
        SectionHeader("Benachrichtigungen")
        NovaCard {
            NovaRow(
                title = "Benachrichtigungen",
                subtitle = "Aus lässt die App völlig still — sie läuft dann auch nicht im " +
                    "Hintergrund. An bedeutet einen unauffälligen Dauer-Eintrag in der Leiste, " +
                    "den Android für Hintergrund-Apps vorschreibt."
            ) {
                NovaSwitch(checked = prefs.notifyEnabled) { on ->
                    prefs.notifyEnabled = on
                    if (on) askNotificationPermission()
                    ParentWatchService.sync(context)
                    v++
                }
            }
            if (prefs.notifyEnabled) {
                NovaRow(title = "Verlängerung angefragt", subtitle = "Wenn dein Kind um Zeit bittet") {
                    NovaSwitch(checked = prefs.notifyRequest) {
                        prefs.notifyRequest = it; v++
                    }
                }
                NovaRow(title = "Aufgabe erledigt", subtitle = "Wenn eine Aufgabe zum Bestätigen ansteht") {
                    NovaSwitch(checked = prefs.notifyChore) { prefs.notifyChore = it; v++ }
                }
                NovaRow(title = "Tageslimit erreicht", subtitle = "Einmal pro Tag") {
                    NovaSwitch(checked = prefs.notifyLimit) { prefs.notifyLimit = it; v++ }
                }
                NovaRow(title = "Gesamtlimit erreicht", subtitle = "Einmal pro Tag") {
                    NovaSwitch(checked = prefs.notifyHardCap) { prefs.notifyHardCap = it; v++ }
                }
                NovaRow(
                    title = "Kind offline",
                    subtitle = "Wenn sich das Gerät über 30 Minuten nicht meldet"
                ) {
                    NovaSwitch(checked = prefs.notifyOffline) { prefs.notifyOffline = it; v++ }
                }
                if (!notificationsAllowed) {
                    Text(
                        "Android erlaubt der App noch keine Benachrichtigungen. Bitte in den " +
                            "Systemeinstellungen für Family Link freigeben.",
                        fontSize = 12.sp, color = Nova.Warning,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }
        }

        if (showGroup("geraet")) {
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
 * The full picture behind the numbers on the overview: every app the child used today with its
 * time, its category and whether it was blocked, plus the day and week totals.
 *
 * Reached by tapping the time on the dashboard. It used to live buried among the settings,
 * which is the last place anyone looks for it.
 */
@Composable
private fun UsageDetailScreen(
    prefs: Prefs,
    remote: com.familylink.ios.sync.ChildStatus?,
    used: Int,
    limit: Int,
    onBack: () -> Unit
) {
    Column(
        Modifier.fillMaxSize()
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(Nova.PageGradient))
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "‹ Zurück", color = Nova.Primary, fontSize = 17.sp,
                modifier = Modifier.clickable { onBack() }.padding(end = 12.dp)
            )
            Text("Nutzung im Detail", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Nova.Ink)
        }

        if (remote == null) {
            Spacer(Modifier.height(20.dp))
            Text("Noch keine Daten vom Kinder-Gerät.", fontSize = 14.sp, color = Nova.InkMuted)
            return@Column
        }

        Spacer(Modifier.height(16.dp))
        NovaCard {
            Column(Modifier.padding(18.dp)) {
                DetailRow("Angerechnet heute", "${TimeFmt.hm(used)} von ${TimeFmt.hm(limit)}")
                DetailRow("Handynutzung gesamt heute", TimeFmt.hm(remote.totalDeviceSeconds))
                if (prefs.limitScope != LimitScope.DAY) {
                    DetailRow(
                        "Angerechnet diese Woche",
                        "${TimeFmt.hm(remote.weekCountedSeconds)} von ${TimeFmt.hm(prefs.weeklyLimitMinutes * 60)}"
                    )
                }
                if (prefs.hardCapEnabled) {
                    if (prefs.hardCapScope != LimitScope.WEEK) {
                        DetailRow(
                            "Gesamtlimit heute",
                            "${TimeFmt.hm(remote.totalDeviceSeconds)} von ${TimeFmt.hm(prefs.hardCapMinutes * 60)}"
                        )
                    }
                    if (prefs.hardCapScope != LimitScope.DAY) {
                        DetailRow(
                            "Gesamtlimit diese Woche",
                            "${TimeFmt.hm(remote.weekTotalSeconds)} von ${TimeFmt.hm(prefs.weeklyHardCapMinutes * 60)}"
                        )
                    }
                }
                if (remote.bonusSeconds > 0) {
                    DetailRow("Bonus heute", "+${remote.bonusSeconds / 60} Min")
                }
                if (remote.batteryPercent in 0..100) {
                    DetailRow("Akku", "${remote.batteryPercent}%")
                }
            }
        }

        val ranked = remote.perAppSeconds.entries.sortedByDescending { it.value }
        SectionHeader("Jede App heute (${ranked.size})")
        NovaCard {
            if (ranked.isEmpty()) {
                Text(
                    "Heute wurde noch keine App benutzt.",
                    fontSize = 14.sp, color = Nova.InkMuted, modifier = Modifier.padding(16.dp)
                )
            } else {
                Column(Modifier.padding(vertical = 6.dp)) {
                    val top = ranked.first().value.coerceAtLeast(1)
                    ranked.forEach { (pkg, secs) ->
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
                                Text(
                                    remote.perAppLabels[pkg] ?: pkg,
                                    fontSize = 15.sp, color = Nova.Ink, modifier = Modifier.weight(1f)
                                )
                                if (pkg in remote.blockedToday) {
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
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        NovaButtonTonal(text = "Zurück", onClick = onBack)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = Nova.InkMuted, modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Nova.Ink)
    }
}

/** Day / week / both, as three segments. */
@Composable
private fun ScopePicker(current: LimitScope, onPick: (LimitScope) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            LimitScope.DAY to "Pro Tag",
            LimitScope.WEEK to "Pro Woche",
            LimitScope.BOTH to "Beides"
        ).forEach { (scope, label) ->
            val sel = current == scope
            Box(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(Nova.RadiusControl.dp))
                    .background(if (sel) Nova.Primary else Nova.Fill)
                    .clickable { onPick(scope) }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = if (sel) Color.White else Nova.InkMuted
                )
            }
        }
    }
}

/** Group key -> the title shown once that group is open. */
private val GROUP_TITLES = mapOf(
    "zeit" to "Zeit & Limits",
    "apps" to "Apps",
    "sperren" to "Sperren & Fokus",
    "verwaltung" to "Verwaltung",
    "schutz" to "Schutz",
    "meldungen" to "Benachrichtigungen",
    "geraet" to "Gerät & Design"
)

/**
 * The bottom navigation from the reference: three destinations, the active one marked by a
 * pale blue pill behind its glyph rather than by colour alone.
 */
@Composable
private fun BottomBar(current: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val items = listOf(
        Triple(0, "Bildschirmzeit", Icons.Filled.BarChart),
        Triple(1, "Einstellungen", Icons.Filled.Person),
        Triple(2, "Aufgaben", Icons.Filled.CheckCircle)
    )
    Row(
        modifier
            .fillMaxWidth()
            .background(Nova.Surface)
            .padding(top = 8.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { (index, label, icon) ->
            val selected = current == index
            Column(
                Modifier
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onSelect(index) }
                    .padding(horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .width(64.dp).height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) Nova.Accent else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon, label,
                        tint = if (selected) Nova.Primary else Nova.InkMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    label,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) Nova.Primary else Nova.InkMuted
                )
            }
        }
    }
}

/** One entry in the settings list: key, title, explanatory line, glyph. */
private data class MenuEntry(
    val key: String,
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

/** What the settings tab offers, in order. */
private val MENU_ENTRIES = listOf(
    MenuEntry("zeit", "Zeitlimits", "Tageslimit, Wochenlimit, Gesamtlimit, Ruhezeit", Icons.Filled.HourglassBottom),
    MenuEntry("apps", "Apps", "Gesperrte Apps und Freigaben für heute", Icons.Filled.Apps),
    MenuEntry("sperren", "Sperren & Fokus", "Gerät sperren, auf Zeit sperren, Fokus", Icons.Filled.Lock),
    MenuEntry("verwaltung", "Verwaltung", "Kategorien, Aufgaben, Bericht, Geräte", Icons.Filled.Tune),
    MenuEntry("schutz", "Schutz", "Schutz-Stufe und Bypass-Sicherung", Icons.Filled.Shield),
    MenuEntry("meldungen", "Benachrichtigungen", "Anfragen, Aufgaben und Limits melden lassen", Icons.Filled.Notifications),
    MenuEntry("geraet", "Gerät & Design", "Hell/Dunkel, PIN, Systemeinstellungen", Icons.Filled.PhoneAndroid)
)

/**
 * The settings tab: one card, one row per area, each with its glyph, title and a line saying
 * what is inside. Tapping a row opens that area on its own page — the reference never shows a
 * single endless settings list, and neither do we.
 */
@Composable
private fun SettingsList(onPick: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize()
            .background(Nova.Canvas)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        Text("Einstellungen", fontSize = 28.sp, fontWeight = FontWeight.Normal, color = Nova.Ink)
        Spacer(Modifier.height(16.dp))
        NovaCard {
            MENU_ENTRIES.forEachIndexed { i, e ->
                if (i > 0) NovaDivider()
                NovaRow(
                    title = e.title,
                    subtitle = e.subtitle,
                    icon = e.icon,
                    onClick = { onPick(e.key) }
                ) { Chevron() }
            }
        }
        Spacer(Modifier.height(100.dp))
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
    onGrant: (Int, Boolean) -> Unit,
    onDecideRequest: (TimeRequest, Boolean) -> Unit,
    onLockFor: (Int) -> Unit,
    onLockNow: () -> Unit,
    onUnlock: () -> Unit,
    onLockScreen: (Int) -> Unit,
    onReleaseScreen: () -> Unit,
    onApproveChore: (String) -> Unit,
    onOpenChores: () -> Unit,
    onOpenDetails: () -> Unit,
    onExit: () -> Unit
) {
    val online = prefs.syncConfigured && System.currentTimeMillis() - prefs.lastSyncAt < 120_000
    val remaining = (limit - used).coerceAtLeast(0)
    val total = remote?.totalDeviceSeconds ?: 0
    val cap = prefs.hardCapMinutes * 60
    val timedLock = prefs.focusSession()
    val lockedNow = prefs.manualLockEnabled
    val lockedTimed = timedLock.isRunning()
    // Ticks every second so the screen-lock countdown actually counts down.
    var secondTick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1000); secondTick++ } }
    @Suppress("UNUSED_EXPRESSION") secondTick
    val screenLockLeft = prefs.screenLockRemainingSeconds()
    var grantAsBonus by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize()
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(Nova.PageGradient))
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // ---- header: the device pill on the left, actions on the right ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Nova.RadiusPill.dp))
                    .background(Nova.Surface)
                    .clickable { onOpenMenu() }
                    .padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Stands in for the avatar in the reference: the child device's initial.
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(Nova.Accent),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        (remote?.deviceName?.trim()?.firstOrNull() ?: 'K').uppercase(),
                        fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Nova.Primary
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        remote?.deviceName ?: "Kinder-Gerät",
                        fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Nova.Ink,
                        maxLines = 1
                    )
                    Text(
                        if (online) "Verbunden" else "Keine Verbindung",
                        fontSize = 11.sp, color = if (online) Nova.Success else Nova.Warning
                    )
                }
            }
            if (prefs.syncConfigured) {
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier.size(42.dp).clip(CircleShape)
                        .background(Nova.Surface)
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
        Spacer(Modifier.height(18.dp))
        Text("Bildschirmzeit", fontSize = 28.sp, fontWeight = FontWeight.Normal, color = Nova.Ink)
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
            // The whole card opens the detailed breakdown — the numbers are what a parent
            // taps on, so that is where the details belong rather than among the settings.
            NovaCard(modifier = Modifier.clickable { onOpenDetails() }) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Noch übrig heute", fontSize = 13.sp, color = Nova.InkMuted,
                            modifier = Modifier.weight(1f))
                        Text("Details ›", fontSize = 13.sp, color = Nova.Primary)
                    }
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

                    // ---- the weekly pot, when one is in force ----
                    if (prefs.limitScope != LimitScope.DAY) {
                        Spacer(Modifier.height(14.dp))
                        val weekPot = prefs.weeklyLimitMinutes * 60
                        val weekUsed = remote.weekCountedSeconds
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Diese Woche", fontSize = 13.sp, color = Nova.InkMuted,
                                modifier = Modifier.weight(1f))
                            Text(
                                "${TimeFmt.hm(weekUsed)} von ${TimeFmt.hm(weekPot)}",
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                color = if (weekUsed >= weekPot) Nova.Danger else Nova.Ink
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        ProgressBar((weekUsed.toFloat() / weekPot.coerceAtLeast(1)).coerceIn(0f, 1f))
                    }

                    Spacer(Modifier.height(16.dp))
                    // ---- the absolute ceiling across every app ----
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Gesamtlimit (alle Apps)", fontSize = 13.sp, color = Nova.InkMuted,
                            modifier = Modifier.weight(1f))
                        val weekly = prefs.hardCapScope == LimitScope.WEEK
                        val shownUsed = if (weekly) remote.weekTotalSeconds else total
                        val shownCap = if (weekly) prefs.weeklyHardCapMinutes * 60 else cap
                        Text(
                            if (prefs.hardCapEnabled)
                                "${TimeFmt.hm(shownUsed)} / ${TimeFmt.hm(shownCap)}" +
                                    if (weekly) " (Woche)" else ""
                            else "aus",
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            color = if (prefs.hardCapEnabled && shownUsed >= shownCap) Nova.Danger else Nova.Ink
                        )
                    }
                    if (prefs.hardCapEnabled) {
                        val weekly = prefs.hardCapScope == LimitScope.WEEK
                        val shownUsed = if (weekly) remote.weekTotalSeconds else total
                        val shownCap = if (weekly) prefs.weeklyHardCapMinutes * 60 else cap
                        Spacer(Modifier.height(8.dp))
                        ProgressBar((shownUsed.toFloat() / shownCap.coerceAtLeast(1)).coerceIn(0f, 1f))
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
            Row(Modifier.fillMaxWidth().clickable { onOpenDetails() },
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SectionHeader("Meistgenutzt heute") }
                Text("Alle ›", fontSize = 13.sp, color = Nova.Primary)
            }
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

        // ---- the hard one: the display itself, not an overlay ----
        Spacer(Modifier.height(16.dp))
        SectionHeader("Display sperren")
        NovaCard {
            Column(Modifier.padding(16.dp)) {
                if (screenLockLeft > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NovaPill("Display aus", Nova.Danger)
                        Spacer(Modifier.width(10.dp))
                        Text("Noch ${TimeFmt.hm(screenLockLeft)}", fontSize = 13.sp, color = Nova.InkMuted)
                    }
                    Spacer(Modifier.height(12.dp))
                    NovaButton(text = "Sofort freigeben", color = Nova.Success) { onReleaseScreen() }
                } else {
                    Text(
                        "Schaltet den Bildschirm wirklich aus — kein Overlay. Jedes Entsperren " +
                            "sperrt sofort wieder, bis die Zeit um ist.",
                        fontSize = 12.sp, color = Nova.InkMuted
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 10, 15).forEach { m ->
                            Box(Modifier.weight(1f)) {
                                NovaButton(text = "$m Min", color = Nova.Danger) { onLockScreen(m) }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Maximal ${Prefs.MAX_SCREEN_LOCK_MIN} Minuten — läuft immer von selbst ab.",
                        fontSize = 12.sp, color = Nova.InkFaint
                    )
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
        SectionHeader("Mehr Zeit geben")
        NovaCard {
            Column(Modifier.padding(16.dp)) {
                val left = prefs.remainingBonusMinutes()
                val bonusLeft = prefs.bonusCountdownRemainingSeconds()

                if (bonusLeft > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NovaPill("Bonuszeit läuft", Nova.Success)
                        Spacer(Modifier.width(10.dp))
                        Text("Noch ${TimeFmt.hm(bonusLeft)}", fontSize = 13.sp, color = Nova.InkMuted)
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // Which kind of time is being handed out.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(false to "Verlängerung", true to "Bonuszeit").forEach { (isBonus, label) ->
                        val sel = grantAsBonus == isBonus
                        Box(
                            Modifier.weight(1f)
                                .clip(RoundedCornerShape(Nova.RadiusControl.dp))
                                .background(if (sel) Nova.Primary else Nova.Fill)
                                .clickable { grantAsBonus = isBonus }
                                .padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                color = if (sel) Color.White else Nova.InkMuted
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (grantAsBonus)
                        "Bonuszeit: ein Countdown, in dem alles offen ist. Läuft er ab, ist " +
                            "wieder gesperrt — egal was benutzt wurde."
                    else
                        "Verlängerung: hebt Tageslimit, Gesamtlimit und den Beginn der Ruhezeit " +
                            "um dieselbe Zeit an.",
                    fontSize = 12.sp, color = Nova.InkMuted
                )

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(10, 15, 30).forEach { m ->
                        Box(Modifier.weight(1f)) {
                            NovaButton(
                                text = "+$m",
                                color = Nova.Danger,
                                enabled = left >= m
                            ) { onGrant(m, grantAsBonus) }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    if (left > 0) "Heute noch $left Minuten möglich (max. ${Prefs.MAX_BONUS_MIN} pro Tag)."
                    else "Das Tagesmaximum ist aufgebraucht.",
                    fontSize = 12.sp, color = Nova.InkMuted
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "Fertig", color = Nova.Primary, fontSize = 16.sp,
            modifier = Modifier.fillMaxWidth().clickable { onExit() }.padding(12.dp)
        )
        // Clearance for the bottom navigation bar.
        Spacer(Modifier.height(100.dp))
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
