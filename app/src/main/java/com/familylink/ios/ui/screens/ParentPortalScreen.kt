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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreTime
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
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
import androidx.compose.ui.graphics.asImageBitmap
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
import com.familylink.ios.ui.components.NovaFeatureCard
import com.familylink.ios.ui.components.NovaValueRow
import com.familylink.ios.ui.components.NovaRow
import com.familylink.ios.ui.components.NovaSwitch
import com.familylink.ios.ui.components.SectionHeader
import com.familylink.ios.ui.theme.Nova
import com.familylink.ios.ui.theme.ThemeMode
import com.familylink.ios.util.LauncherGuard
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
    var showEvents by remember { mutableStateOf(false) }
    // Which tab an settings area was opened from, so back returns where it came from
    // instead of always dropping into the settings list.
    var groupCameFrom by remember { mutableStateOf(1) }
    /** 0 = Bildschirmzeit, 1 = Einstellungen, 2 = Aufgaben. */
    var tab by remember { mutableStateOf(0) }

    fun showGroup(group: String): Boolean = settingsGroup == null || settingsGroup == group

    /**
     * The back gesture walks up the portal's own hierarchy before it leaves the portal at all:
     * a settings area returns to the settings list, the detail page to the overview, and any
     * tab to the first one. Only from the overview does back close the portal.
     */
    androidx.activity.compose.BackHandler {
        when {
            showEvents -> showEvents = false
            settingsGroup != null -> { settingsGroup = null; showSettings = false; tab = groupCameFrom }
            showDetails -> showDetails = false
            tab != 0 -> tab = 0
            else -> onExit()
        }
    }

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

    // ---- a changed rule reaches the child straight away ----
    //
    // Every control here bumps [v] when it writes. Rather than waiting for the next heartbeat —
    // up to twenty seconds, which felt like the app had not understood — the new config goes out
    // as soon as the hand stops moving. The short delay is what keeps a stepper held down from
    // sending a write per tap.
    LaunchedEffect(v) {
        if (v > 0 && prefs.isParentDevice && prefs.syncConfigured) {
            kotlinx.coroutines.delay(350)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { sync.pushConfig() }
            }
        }
    }

    // ---- the supervised phone's parent area ----
    //
    // Reached with the PIN from the child portal. It has no dashboard — the numbers are on the
    // child's own overview — but it gets the same menu the parent app has, so it is a list of
    // areas to step into rather than one page carrying every setting the app owns.
    if (!prefs.isParentDevice && settingsGroup == null) {
        SettingsList(
            title = "Eltern-Bereich",
            entries = MENU_ENTRIES.filter { it.key !in PARENT_DEVICE_ONLY_GROUPS },
            onPick = { settingsGroup = it },
            onClose = onExit
        )
        return
    }

    // ---- the parent app's shell: three tabs along the bottom, as in the reference ----
    if (prefs.isParentDevice && !showDetails && settingsGroup == null) {
        Box(Modifier.fillMaxSize().background(Nova.Canvas)) {
            when (tab) {
                1 -> SettingsList(onPick = { group -> groupCameFrom = 1; settingsGroup = group; showSettings = true })
                // The third destination manages the child's apps — the thing a parent reaches
                // for most often. The report is one tap away on the screen-time card instead.
                2 -> AppsScreen()
                else -> ParentDashboard(
                    prefs = prefs,
                    remote = remote,
                    used = used,
                    limit = limit,
                    refreshing = refreshing,
                    pendingRequest = pendingRequest,
                    onRefresh = { refreshNow() },
                    onOpenMenu = { tab = 1 },
                    onOpenGroup = { group -> groupCameFrom = 0; settingsGroup = group; showSettings = true },
                    onOpenEvents = { showEvents = true },
                    onGrant = { minutes, asBonus -> sync.grantTime(minutes, asBonus); v++ },
                    onDecideRequest = { req, approve ->
                        thread(isDaemon = true) { sync.decideRequest(req, approve) }
                        pendingRequest = null
                        v++
                    },
                    onLockFor = { minutes -> sync.lockForMinutes(minutes); v++ },
                    onFocusFor = { minutes -> sync.startFocus("Fokus", minutes, prefs.plusPackages()); v++ },
                    onLockNow = { sync.lockDevice(); v++ },
                    onUnlock = { sync.unlockDevice(); sync.stopFocus(); v++ },
                    onLockScreen = { minutes -> startScreenLock(context, sync, minutes); v++ },
                    onReleaseScreen = { releaseScreenLock(context, sync); v++ },
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

    if (prefs.isParentDevice && showEvents) {
        EventsScreen(prefs)
        return
    }

    if (prefs.isParentDevice && showDetails) {
        UsageDetailScreen(
            prefs = prefs,
            remote = remote,
            used = used,
            limit = limit
        )
        return
    }


    Column(
        Modifier
            .fillMaxSize()
            .background(Nova.Canvas)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Both devices arrive here with one area picked from the menu, so the heading is
            // that area — never the whole word "Einstellungen" over an endless page.
            Text(
                settingsGroup?.let { GROUP_TITLES[it] } ?: "Einstellungen",
                fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Nova.Ink
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

        // The settings page is settings only. On the parent phone the usage figures live on
        // the Bildschirmzeit tab and, in full, one tap away on the time itself — repeating the
        // whole app breakdown above every setting made this page unreadable.
        //
        // The child's phone keeps them: its parent portal is one single page with no tabs, so
        // this is the only place the numbers can be seen at all.
        if (!prefs.isParentDevice) {

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
                    Text(TimeFmt.hm(totalDevice), fontSize = 30.sp, fontWeight = FontWeight.Medium, color = Nova.Ink)
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
                            fontWeight = FontWeight.Medium, color = Nova.Ink
                        )
                    }
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
                subtitle = "Zählt jede App mit — auch Plus. Der Aus-Knopf hebt es nicht auf; " +
                    "Bonuszeit öffnet es, solange sie läuft."
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
                // What was handed out today, spelled out where the ceiling is set — otherwise
                // the number in the stepper and the ceiling actually in force drift apart
                // without anyone being told.
                if (prefs.bonusSecondsToday > 0) {
                    val extra = prefs.bonusSecondsToday
                    NovaRow(
                        title = "Heute geschenkt",
                        subtitle = "Das Gesamtlimit liegt heute bei " +
                            "${TimeFmt.hm(prefs.hardCapMinutes * 60 + extra)}, das Tageslimit bei " +
                            "${TimeFmt.hm(prefs.globalLimitMinutes * 60 + extra)}."
                    ) {
                        NovaPill("+${TimeFmt.hm(extra)}", Nova.Success)
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


        }

        // ---- schedules: the reference keeps them apart from the limits ----
        if (showGroup("plaene")) {
        // ---- school time, the reference's second schedule ----
        Spacer(Modifier.height(8.dp))
        NovaFeatureCard(
            icon = Icons.Filled.School,
            title = "Schulzeit",
            description = "Weniger Ablenkung während des Unterrichts: nur die zugelassenen " +
                "Apps bleiben nutzbar.",
            tint = Nova.Focus,
            expanded = prefs.schoolTimeEnabled,
            control = {
                NovaSwitch(checked = prefs.schoolTimeEnabled) { prefs.schoolTimeEnabled = it; v++ }
            }
        ) {
            NovaDivider()
            NovaValueRow(
                "Heute",
                if (prefs.isSchoolTime()) "Läuft bis ${TimeFmt.clock(prefs.schoolEndMin)}"
                else "${TimeFmt.clock(prefs.schoolStartMin)}–${TimeFmt.clock(prefs.schoolEndMin)}",
                valueColor = if (prefs.isSchoolTime()) Nova.Focus else Nova.Ink
            )
            NovaDivider()
            NovaRow(title = "Beginn") {
                Stepper(
                    value = TimeFmt.clock(prefs.schoolStartMin),
                    onMinus = { prefs.schoolStartMin = prefs.schoolStartMin - 15; v++ },
                    onPlus = { prefs.schoolStartMin = prefs.schoolStartMin + 15; v++ }
                )
            }
            NovaDivider()
            NovaRow(title = "Ende") {
                Stepper(
                    value = TimeFmt.clock(prefs.schoolEndMin),
                    onMinus = { prefs.schoolEndMin = prefs.schoolEndMin - 15; v++ },
                    onPlus = { prefs.schoolEndMin = prefs.schoolEndMin + 15; v++ }
                )
            }
            NovaDivider()
            Column(Modifier.padding(16.dp)) {
                Text("Tage", fontSize = 15.sp, color = Nova.Ink)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So").forEachIndexed { i, label ->
                        val on = prefs.schoolDayEnabled(i)
                        Box(
                            Modifier.weight(1f).height(40.dp).clip(CircleShape)
                                .background(if (on) Nova.Focus else Nova.Fill)
                                .clickable { prefs.toggleSchoolDay(i); v++ },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label, fontSize = 12.sp,
                                fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                                color = if (on) Color.White else Nova.InkMuted
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        // ---- bedtime ----
        Spacer(Modifier.height(8.dp))
        NovaFeatureCard(
            icon = Icons.Filled.Bedtime,
            title = "Ruhezeit",
            description = "Hilf deinem Kind zu schlafen, indem das Handy nachts gesperrt wird.",
            tint = Nova.Night,
            expanded = prefs.bedtimeEnabled,
            control = {
                NovaSwitch(checked = prefs.bedtimeEnabled) { prefs.bedtimeEnabled = it; v++ }
            }
        ) {
            NovaDivider()
            NovaValueRow(
                "Heute Nacht",
                "${TimeFmt.clock(prefs.bedtimeStartMin)}–${TimeFmt.clock(prefs.bedtimeEndMin)}"
            )
            NovaDivider()
            NovaRow(title = "Beginn") {
                Stepper(
                    value = TimeFmt.clock(prefs.bedtimeStartMin),
                    onMinus = { prefs.bedtimeStartMin = wrap(prefs.bedtimeStartMin - 30); v++ },
                    onPlus = { prefs.bedtimeStartMin = wrap(prefs.bedtimeStartMin + 30); v++ }
                )
            }
            NovaDivider()
            NovaRow(title = "Ende") {
                Stepper(
                    value = TimeFmt.clock(prefs.bedtimeEndMin),
                    onMinus = { prefs.bedtimeEndMin = wrap(prefs.bedtimeEndMin - 30); v++ },
                    onPlus = { prefs.bedtimeEndMin = wrap(prefs.bedtimeEndMin + 30); v++ }
                )
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
            val timedRunning = focus.isRunning() && focus.allowed.isEmpty()
            val screenLeft = prefs.screenLockRemainingSeconds()
            val canLock = !prefs.isParentDevice || prefs.syncConfigured

            // Timed device lock: everything sealed but the phone, for a fixed stretch. Short
            // lengths only and no ration — this is the everyday reaction.
            if (timedRunning) {
                NovaRow(
                    title = "Gesperrt auf Zeit — noch ${TimeFmt.hm(focus.remainingSeconds())}",
                    subtitle = "Tippe zum Freigeben"
                ) {
                    Box(
                        Modifier.clip(RoundedCornerShape(10.dp))
                            .background(Nova.Success.copy(alpha = 0.15f))
                            .clickable { sync.stopFocus(); v++ }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text("Freigeben", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Nova.Success)
                    }
                }
            } else {
                // Full width, not a trailing slot: four chips do not fit beside a title.
                LockChipRow("Gerät sperren", "Nur Telefon und Notruf, endet von selbst") {
                    Prefs.PARENT_LOCK_MINUTES.forEach { m ->
                        LockChip("$m Min", null, Nova.Danger, enabled = true) {
                            sync.lockForMinutes(m); v++
                        }
                    }
                }
            }
            NovaDivider()

            // Display lock: the screen itself goes dark and re-locks on every unlock. This is
            // where the rationed lengths live, because it is the one that runs longest.
            if (screenLeft > 0) {
                NovaRow(
                    title = "Display gesperrt — noch ${TimeFmt.hm(screenLeft)}",
                    subtitle = "Tippe zum Aufheben"
                ) {
                    Box(
                        Modifier.clip(RoundedCornerShape(10.dp))
                            .background(Nova.Success.copy(alpha = 0.15f))
                            .clickable { releaseScreenLock(context, sync); v++ }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text("Aufheben", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Nova.Success)
                    }
                }
            } else {
                LockChipRow("Display sperren", "Bildschirm aus, endet von selbst") {
                    Prefs.SCREEN_LOCK_MINUTES.forEach { m ->
                        val left = prefs.screenLocksLeft(m)
                        val period = prefs.screenLockPeriodLabel(m)
                        LockChip(
                            label = if (m >= 60) "${m / 60} Std" else "$m Min",
                            note = period?.let { if (left > 0) it else "aufgebraucht" },
                            tint = Nova.Primary,
                            enabled = canLock && left > 0
                        ) {
                            // Book it first: a refused booking must not lock.
                            if (prefs.useScreenLock(m)) { startScreenLock(context, sync, m); v++ }
                        }
                    }
                }
            }
            NovaDivider()

            // Last, and deliberately open-ended: stays sealed until it is lifted again.
            NovaRow(
                title = "Sperren",
                subtitle = if (prefs.manualLockEnabled) "Gesperrt — tippe zum Aufheben"
                else "Ohne Zeitende. Telefon und Notruf bleiben erreichbar."
            ) {
                NovaSwitch(checked = prefs.manualLockEnabled) {
                    prefs.manualLockEnabled = it
                    if (!it) prefs.manualLockReason = ""
                    v++
                }
            }
            NovaDivider()

            // The child's side of locking: they can lock their own phone from their portal, and
            // time served that way buys screen time back. This is the knob for how generous it
            // is — and the switch that turns the whole idea off.
            NovaRow(
                title = "Bonus fürs Weglegen",
                subtitle = "Sperrt das Kind sein Handy selbst, bekommt es Bildschirmzeit " +
                    "zurück — nur für Zeit, die es wirklich durchhält, und höchstens " +
                    "${Prefs.OWN_LOCK_REWARD_MAX_PER_DAY} Min. am Tag."
            ) {
                NovaSwitch(checked = prefs.ownLockRewardEnabled) {
                    prefs.ownLockRewardEnabled = it; v++
                }
            }
            if (prefs.ownLockRewardEnabled) {
                NovaRow(title = "Pro durchgehaltener Stunde") {
                    Stepper(
                        value = "${prefs.ownLockRewardPerHour} Min",
                        onMinus = { prefs.ownLockRewardPerHour = prefs.ownLockRewardPerHour - 5; v++ },
                        onPlus = { prefs.ownLockRewardPerHour = prefs.ownLockRewardPerHour + 5; v++ }
                    )
                }
            }
            // Without the accessibility service (or, on Android 8, the device admin) the OS
            // gives no app any way to switch the display off — say so instead of offering a
            // button that quietly does nothing.
            if (!prefs.isParentDevice && !com.familylink.ios.util.ScreenLock.available(context)) {
                com.familylink.ios.ui.components.NovaNote(
                    "Zum Sperren des Displays wird die Bedienungshilfe (oder auf Android 8 der " +
                        "Geräteadministrator) benötigt. Bitte unter Berechtigungen erteilen.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
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
        // Only meaningful on the phone being supervised: these two are about that device's own
        // home screen and icon, which a parent phone has no business changing.
        if (!prefs.isParentDevice) {
            SectionHeader("Manipulationsschutz")
            NovaCard {
                val installed = LauncherGuard.isLauncherInstalled(context)
                val isHome = LauncherGuard.isLauncherActive(context)
                NovaRow(
                    title = when {
                        isHome -> "Startbildschirm ist aktiv"
                        installed -> "Startbildschirm einrichten"
                        else -> "Startbildschirm-App fehlt"
                    },
                    subtitle = when {
                        isHome ->
                            "Der Startbildschirm l\u00e4uft als eigene App. Wird die Kindersicherung " +
                                "beendet, startet er sie innerhalb einer Sekunde wieder."
                        installed ->
                            "Android fragt, welche App der Startbildschirm sein soll \u2014 w\u00e4hle " +
                                "dort \u201eV\u00f6lkle Start\u201c."
                        else ->
                            "Installiere zus\u00e4tzlich die zweite APK \u201eV\u00f6lkle Start\u201c. Sie " +
                                "ist der Startbildschirm und h\u00e4lt den Schutz am Leben."
                    },
                    onClick = if (installed) {
                        { LauncherGuard.openHomeChooser(context) }
                    } else null
                ) {
                    NovaPill(
                        if (isHome) "Aktiv" else if (installed) "Einrichten" else "Fehlt",
                        if (isHome) Nova.Success else Nova.Warning
                    )
                }
                NovaDivider()
                NovaRow(
                    title = "App-Symbol ausblenden",
                    subtitle = if (isHome)
                        "Ohne Symbol f\u00fchrt kein Langdruck mehr zu \u201eApp-Info\u201c und damit zu " +
                            "\u201eBeenden erzwingen\u201c. Die Kindersicherung bleibt \u00fcber das " +
                            "Schild-Symbol im Startbildschirm erreichbar."
                    else
                        "Erst m\u00f6glich, wenn der eigene Startbildschirm aktiv ist \u2014 sonst g\u00e4be " +
                            "es keinen Weg mehr in die App hinein."
                ) {
                    NovaSwitch(checked = LauncherGuard.isIconHidden(context)) { want ->
                        // Refused while the launcher is not the home screen; the switch simply
                        // stays off, and the subtitle above already says why.
                        LauncherGuard.setIconHidden(context, want)
                        v++
                    }
                }
                if (installed) {
                    NovaDivider()
                    NovaRow(
                        title = "Startbildschirm einrichten",
                        subtitle = "Assistent und Einstellungen von \u201eV\u00f6lkle Start\u201c: " +
                            "welche Apps auf dem Startbildschirm liegen, die Leiste unten, " +
                            "Hintergrundbild. Ohne PIN — welche Symbole wo liegen, darf das " +
                            "Kind selbst bestimmen.",
                        onClick = { LauncherGuard.openLauncherSettings(context) }
                    ) {
                        NovaPill("\u00d6ffnen", Nova.Primary)
                    }
                }
            }
        }
        SectionHeader("Verbindung")
        NovaCard {
            NovaRow(
                title = "Sperren ohne Verbindung",
                subtitle = "Ein Handy, das sich zu lange nicht meldet, wird gesperrt — sonst " +
                    "genügt Flugmodus, um jede Regel abzuschalten. Das Kind kann die " +
                    "Verbindung direkt auf der Sperrseite wieder einschalten."
            ) {
                NovaSwitch(checked = prefs.offlineLockEnabled) { prefs.offlineLockEnabled = it; v++ }
            }
            if (prefs.offlineLockEnabled) {
                NovaRow(
                    title = "Erlaubte Zeit ohne Verbindung",
                    subtitle = "Danach wird gesperrt. Nach einem Neustart gibt es " +
                        "${Prefs.BOOT_GRACE_MS / 60000} Minuten Karenz."
                ) {
                    Stepper(
                        value = TimeFmt.hm(prefs.offlineLockMinutes * 60),
                        onMinus = { prefs.offlineLockMinutes = prefs.offlineLockMinutes - 15; v++ },
                        onPlus = { prefs.offlineLockMinutes = prefs.offlineLockMinutes + 15; v++ }
                    )
                }
                if (!prefs.isParentDevice) {
                    val off = prefs.offlineSeconds()
                    NovaRow(title = "Letzte Meldung") {
                        if (off < 0) NovaPill("nie", Nova.Warning)
                        else NovaPill("vor ${TimeFmt.hm(off)}", if (off > 900) Nova.Warning else Nova.Success)
                    }
                }
            }
        }

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
                NovaRow(title = "Bonuszeit angefragt", subtitle = "Wenn dein Kind um Zeit bittet") {
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
                    com.familylink.ios.ui.components.NovaNote(
                        "Android erlaubt der App noch keine Benachrichtigungen. Bitte in den " +
                            "Systemeinstellungen für Völkle Link freigeben.",
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
    limit: Int
) {
    Column(
        Modifier.fillMaxSize()
            .background(Nova.Canvas)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Text("Bericht", fontSize = 28.sp, fontWeight = FontWeight.Normal, color = Nova.Ink)
        Text("Heute im Detail und die Woche davor", fontSize = 13.sp, color = Nova.InkMuted)

        // ---- the week, as bars, before the detail of today ----
        // The parent phone measures nothing itself, so its own history would be a flat
        // line of zeroes. The child's seven days come up with the status.
        val history = remote?.weekHistory?.takeIf { it.isNotEmpty() }
            ?: if (prefs.isParentDevice) emptyList() else prefs.getWeekHistory()
        if (history.isNotEmpty()) {
            val dayLimit = prefs.globalLimitMinutes * 60
            val peak = (history.maxOfOrNull { it.second } ?: 1)
                .coerceAtLeast(dayLimit).coerceAtLeast(1)
            Spacer(Modifier.height(16.dp))
            SectionHeader("Wochenbericht")
            NovaCard {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth().height(120.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        history.forEach { (day, seconds) ->
                            val over = dayLimit > 0 && seconds > dayLimit
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    TimeFmt.hm(seconds), fontSize = 10.sp,
                                    color = if (over) Nova.Danger else Nova.InkFaint
                                )
                                Spacer(Modifier.height(4.dp))
                                Box(
                                    Modifier.width(22.dp)
                                        .height((8 + 78 * seconds / peak).dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (over) Nova.Danger else Nova.Primary)
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(day, fontSize = 11.sp, color = Nova.InkMuted)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    val week = history.sumOf { it.second }
                    Text(
                        "Diese Woche ${TimeFmt.hm(week)} · Schnitt " +
                            TimeFmt.hm(week / history.size.coerceAtLeast(1)) + " pro Tag",
                        fontSize = 13.sp, color = Nova.InkMuted
                    )
                }
            }
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

        Spacer(Modifier.height(32.dp))
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
    "zeit" to "Zeitlimits",
    "plaene" to "Zeitpläne",
    "apps" to "Apps",
    "sperren" to "Sperren & Fokus",
    "verwaltung" to "Verwaltung",
    "schutz" to "Schutz & Verbindung",
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
        Triple(2, "Apps", Icons.Filled.Apps)
    )
    Column(modifier.fillMaxWidth().background(Nova.Surface)) {
        // Hairline above the bar so it reads as a separate surface on a white card below it.
        Box(Modifier.fillMaxWidth().height(1.dp).background(Nova.Line))
        Row(
            Modifier.fillMaxWidth().height(80.dp).padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Top
        ) {
            items.forEach { (index, label, icon) ->
                val selected = current == index
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onSelect(index) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Material 3's active indicator: a 64x32 pill behind the glyph only.
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
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (selected) Nova.Ink else Nova.InkMuted,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * A lock option: what it does on the left, the durations to pick from on the right. Keeps the
 * three ways to lock in one calm list rather than three shouting red blocks.
 */
@Composable
private fun LockChoiceRow(
    title: String,
    subtitle: String,
    options: List<Pair<Int, String>>,
    onPick: (Int) -> Unit
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Nova.Ink)
        Text(subtitle, fontSize = 13.sp, color = Nova.InkMuted, lineHeight = 17.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, label) ->
                Box(
                    Modifier.weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(Nova.Fill)
                        .clickable { onPick(value) }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Nova.Ink)
                }
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
/**
 * The settings, arranged the way the reference arranges them: one row per area, each saying in
 * one grey line what is inside, and the two that belong to screen time — the limits and the
 * schedules — kept apart from each other.
 */
private val MENU_ENTRIES = listOf(
    MenuEntry("zeit", "Zeitlimits", "Tageslimit, Wochenlimit und Gesamtlimit", Icons.Filled.HourglassBottom),
    MenuEntry("plaene", "Zeitpläne", "Ruhezeit und Schulzeit", Icons.Filled.CalendarMonth),
    MenuEntry("apps", "Apps", "Gesperrte Apps und Freigaben für heute", Icons.Filled.Apps),
    MenuEntry("sperren", "Sperren & Fokus", "Gerät sperren, auf Zeit sperren, Fokus", Icons.Filled.Lock),
    MenuEntry("verwaltung", "Verwaltung", "Kategorien, Aufgaben, Bericht, Geräte", Icons.Filled.Tune),
    MenuEntry("schutz", "Schutz & Verbindung", "Schutz-Stufe, Bypass-Sicherung, Offline-Sperre", Icons.Filled.Shield),
    MenuEntry("meldungen", "Benachrichtigungen", "Anfragen, Aufgaben und Limits melden lassen", Icons.Filled.Notifications),
    MenuEntry("geraet", "Gerät & Design", "Hell/Dunkel, PIN, Systemeinstellungen", Icons.Filled.PhoneAndroid)
)

/**
 * The settings tab: one card, one row per area, each with its glyph, title and a line saying
 * what is inside. Tapping a row opens that area on its own page — the reference never shows a
 * single endless settings list, and neither do we.
 */
@Composable
private fun SettingsList(
    onPick: (String) -> Unit,
    title: String = "Einstellungen",
    entries: List<MenuEntry> = MENU_ENTRIES,
    onClose: (() -> Unit)? = null
) {
    Column(
        Modifier.fillMaxSize()
            .background(Nova.Canvas)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        Text(title, fontSize = 30.sp, fontWeight = FontWeight.Normal, color = Nova.Ink)
        Spacer(Modifier.height(16.dp))
        NovaCard {
            entries.forEachIndexed { i, e ->
                if (i > 0) NovaDivider()
                // No chevron: the reference's lists carry the glyph, the title and the line
                // beneath it, and nothing on the right at all.
                NovaRow(
                    title = e.title,
                    subtitle = e.subtitle,
                    icon = e.icon,
                    onClick = { onPick(e.key) }
                )
            }
        }
        // The supervised phone has no bottom bar to leave by, so the way out is a row.
        if (onClose != null) {
            Spacer(Modifier.height(16.dp))
            NovaCard {
                NovaRow(
                    title = "Zurück zum Kinder-Portal",
                    icon = Icons.Filled.ChevronRight,
                    onClick = onClose
                )
            }
        }
        Spacer(Modifier.height(100.dp))
    }
}

/** Areas that only make sense on the parent's own phone. */
private val PARENT_DEVICE_ONLY_GROUPS = setOf("meldungen")
/**
 * The parent's start page.
 *
 * Modelled on the reference: the device itself at the top with the one number that matters and
 * the way to lock it, then the two areas that own the rules, then only what actually needs an
 * answer today. Everything else lives behind the settings tab — the page is meant to end after
 * a short scroll, not to be a report.
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
    /** Jump straight from the overview into one settings area, as the reference does. */
    onOpenGroup: (String) -> Unit,
    onOpenEvents: () -> Unit,
    onGrant: (Int, Boolean) -> Unit,
    onDecideRequest: (TimeRequest, Boolean) -> Unit,
    onLockFor: (Int) -> Unit,
    /** Start a focus session of this length: the allowed apps stay usable. */
    onFocusFor: (Int) -> Unit,
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
    val lockedNow = prefs.manualLockEnabled
    val timedLock = prefs.focusSession()
    val lockedTimed = timedLock.isRunning()
    // Ticks every second so both countdowns actually count down.
    var secondTick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1000); secondTick++ } }
    @Suppress("UNUSED_EXPRESSION") secondTick
    val screenLockLeft = prefs.screenLockRemainingSeconds()
    var lockSheet by remember { mutableStateOf(false) }
    var bonusSheet by remember { mutableStateOf(false) }

    val remaining = (limit - used).coerceAtLeast(0)
    val bonusRunning = prefs.bonusCountdownActive()
    val unread = prefs.unreadEventCount()

    Column(
        Modifier.fillMaxSize()
            .background(Nova.Canvas)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        // ---- header: the child, the bell, the refresh ----
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                prefs.childName.ifBlank { "Übersicht" },
                fontSize = 26.sp, fontWeight = FontWeight.Normal, color = Nova.Ink,
                modifier = Modifier.weight(1f)
            )
            // The bell carries a dot while something is unread; the list itself is its own page.
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Nova.Surface)
                    .clickable { onOpenEvents() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Notifications, "Meldungen", tint = Nova.Ink,
                    modifier = Modifier.size(20.dp))
                if (unread > 0) {
                    Box(
                        Modifier.size(9.dp).offset(x = 7.dp, y = (-7).dp)
                            .clip(CircleShape).background(Nova.Danger)
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Nova.Surface)
                    .clickable(enabled = !refreshing) { onRefresh() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Refresh, "Aktualisieren",
                    tint = if (refreshing) Nova.InkFaint else Nova.Primary,
                    modifier = Modifier.size(19.dp)
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---- card one: the time spent today, and the apps that made it ----
        //
        // The reference leads with this and nothing else: one number, one line naming it, and
        // the three apps beside it. Tapping it opens the report.
        NovaCard(modifier = Modifier.clickable { onOpenDetails() }) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        TimeFmt.hm(remote?.totalDeviceSeconds ?: 0),
                        fontSize = 34.sp, fontWeight = FontWeight.Normal, color = Nova.Ink
                    )
                    Text("Bildschirmzeit heute", fontSize = 13.sp, color = Nova.InkMuted)
                }
                val topThree = remote?.perAppSeconds.orEmpty()
                    .entries.sortedByDescending { it.value }.take(3)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    topThree.forEach { (pkg, _) ->
                        AppGlyph(pkg, remote?.perAppLabels?.get(pkg) ?: pkg)
                    }
                }
            }
        }

        // ---- card two: the device itself, its state, and what can be done to it ----
        Spacer(Modifier.height(14.dp))
        val bedtimeNow = remote?.bedtimeActive == true
        NovaCard {
            Column {
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { onOpenDetails() }
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Nova.Fill),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.PhoneAndroid, null, tint = Nova.Ink,
                            modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            remote?.deviceName ?: "Kinder-Gerät",
                            fontSize = 17.sp, fontWeight = FontWeight.Normal, color = Nova.Ink
                        )
                        // The state in one line, coloured the way the reference colours it:
                        // red while the phone is shut, plain grey while it is simply in use.
                        val (stateText, stateColour) = when {
                            lockedNow -> "Gesperrt" to Nova.Danger
                            lockedTimed ->
                                "Gesperrt · noch ${TimeFmt.hm(timedLock.remainingSeconds())}" to Nova.Danger
                            bonusRunning ->
                                "Bonuszeit · ${TimeFmt.hm(prefs.bonusCountdownRemainingSeconds())}" to Nova.Success
                            bedtimeNow ->
                                "Ruhezeit bis ${TimeFmt.clock(prefs.bedtimeEndMin)}" to Nova.Danger
                            !prefs.syncConfigured -> "Nicht verbunden" to Nova.InkFaint
                            !online -> "Offline" to Nova.InkFaint
                            else -> "${TimeFmt.hm(remaining)} übrig" to Nova.InkMuted
                        }
                        Text(stateText, fontSize = 13.sp, color = stateColour)
                    }
                    if (remote != null && remote.batteryPercent in 0..100) {
                        Text(
                            "${remote.batteryPercent} %", fontSize = 12.sp,
                            color = if (remote.batteryPercent < 20) Nova.Danger else Nova.InkFaint
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Chevron()
                }

                // The budget as a bar, so the number above has something to sit against.
                Column(Modifier.padding(horizontal = 18.dp)) {
                    ProgressBar(
                        fraction = if (limit == 0) 1f else (used.toFloat() / limit).coerceIn(0f, 1f)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${TimeFmt.hm(used)} von ${TimeFmt.hm(limit)} angerechnet",
                        fontSize = 12.sp, color = Nova.InkFaint
                    )
                }

                // Lock across the width, bonus time as the round button beside it — the two
                // actions the reference puts here, in the same arrangement.
                Row(
                    Modifier.fillMaxWidth().padding(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.weight(1f).height(46.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (lockedNow || lockedTimed) Nova.Danger else Nova.Accent)
                            .clickable { lockSheet = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (lockedNow || lockedTimed) Icons.Filled.LockOpen
                                else Icons.Filled.Lock,
                                null,
                                tint = if (lockedNow || lockedTimed) Color.White else Nova.Primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (lockedNow || lockedTimed) "Entsperren" else "Sperren",
                                fontSize = 15.sp, fontWeight = FontWeight.Medium,
                                color = if (lockedNow || lockedTimed) Color.White else Nova.Primary
                            )
                        }
                    }
                    Box(
                        Modifier.size(46.dp)
                            .clip(CircleShape)
                            .background(if (bonusRunning) Nova.Success else Nova.Accent)
                            .clickable { bonusSheet = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.MoreTime, "Bonuszeit",
                            tint = if (bonusRunning) Color.White else Nova.Primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                if (screenLockLeft > 0) {
                    Text(
                        "Display gesperrt — noch ${TimeFmt.hm(screenLockLeft)}",
                        fontSize = 12.sp, color = Nova.Danger,
                        modifier = Modifier.padding(start = 18.dp, bottom = 14.dp)
                    )
                }
            }
        }

        // ---- the two areas that own the rules ----
        Spacer(Modifier.height(14.dp))
        NovaCard {
            NovaRow(
                title = "Zeitlimits",
                subtitle = buildString {
                    append("Limit von ${TimeFmt.hm(prefs.globalLimitMinutes * 60)}")
                    if (prefs.hardCapEnabled) {
                        append(" · Gesamt ${TimeFmt.hm(prefs.hardCapMinutes * 60)}")
                    }
                },
                icon = Icons.Filled.HourglassBottom,
                onClick = { onOpenGroup("zeit") }
            )
            NovaDivider()
            NovaRow(
                title = "Zeitpläne",
                subtitle = when {
                    prefs.isSchoolTime() ->
                        "Schulzeit · bis ${TimeFmt.clock(prefs.schoolEndMin)} Uhr"
                    bedtimeNow -> "Läuft · bis ${TimeFmt.clock(prefs.bedtimeEndMin)} Uhr"
                    prefs.bedtimeEnabled ->
                        "${TimeFmt.clock(prefs.bedtimeStartMin)}–${TimeFmt.clock(prefs.bedtimeEndMin)} Uhr"
                    else -> "Deaktiviert"
                },
                icon = Icons.Filled.Bedtime,
                // Red while it is actually running, as in the reference.
                iconTint = if (bedtimeNow) Nova.Danger else Nova.Primary,
                onClick = { onOpenGroup("plaene") }
            )
        }

        // ---- only what needs an answer ----
        if (pendingRequest != null) {
            Spacer(Modifier.height(14.dp))
            NovaCard {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Anfrage: ${pendingRequest.minutes} Minuten",
                        fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Nova.Ink
                    )
                    if (pendingRequest.reason.isNotBlank()) {
                        Spacer(Modifier.height(3.dp))
                        Text(pendingRequest.reason, fontSize = 14.sp, color = Nova.InkMuted)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(50))
                                .background(Nova.Accent)
                                .clickable { onDecideRequest(pendingRequest, true) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Geben", fontSize = 14.sp, fontWeight = FontWeight.Medium,
                                color = Nova.Primary)
                        }
                        Box(
                            Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(50))
                                .background(Nova.Fill)
                                .clickable { onDecideRequest(pendingRequest, false) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Ablehnen", fontSize = 14.sp, color = Nova.InkMuted)
                        }
                    }
                }
            }
        }

        val claimed = prefs.getChores().filter { it.isClaimed }
        if (claimed.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            NovaCard {
                Column(Modifier.padding(vertical = 4.dp)) {
                    claimed.forEachIndexed { i, chore ->
                        if (i > 0) NovaDivider()
                        NovaRow(
                            title = chore.title,
                            subtitle = "Erledigt gemeldet · +${chore.rewardMinutes} Min",
                            icon = Icons.Filled.CheckCircle,
                            onClick = { onApproveChore(chore.id) }
                        ) {
                            Text("Bestätigen", fontSize = 14.sp, color = Nova.Primary)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(100.dp))
    }

    if (bonusSheet) {
        BonusSheet(
            presets = prefs.bonusPresets,
            running = bonusRunning,
            remaining = prefs.bonusCountdownRemainingSeconds(),
            onDismiss = { bonusSheet = false },
            onGive = { minutes -> onGrant(minutes, true); bonusSheet = false },
            onStop = { onGrant(0, false); bonusSheet = false }
        )
    }

    if (lockSheet) {
        LockSheet(
            lockedNow = lockedNow,
            lockedTimed = lockedTimed,
            screenLockLeft = screenLockLeft,
            onDismiss = { lockSheet = false },
            onLockNow = { onLockNow(); lockSheet = false },
            onLockFor = { onLockFor(it); lockSheet = false },
            onFocusFor = { onFocusFor(it); lockSheet = false },
            onLockScreen = { onLockScreen(it); lockSheet = false },
            onUnlock = { onUnlock(); lockSheet = false },
            onReleaseScreen = { onReleaseScreen(); lockSheet = false }
        )
    }
}

/**
 * One app's icon. The parent's phone does not have the child's apps installed, so there is
 * usually no icon to draw — the first letter on a tinted disc stands in for it there.
 */
@Composable
private fun AppGlyph(pkg: String, label: String) {
    val context = LocalContext.current
    val icon = remember(pkg) { InstalledApps.iconBitmap(context, pkg) }
    Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Nova.Fill),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            androidx.compose.foundation.Image(
                bitmap = icon.asImageBitmap(), contentDescription = label,
                modifier = Modifier.size(30.dp)
            )
        } else {
            Text(label.take(1).uppercase(), fontSize = 14.sp,
                fontWeight = FontWeight.Medium, color = Nova.InkMuted)
        }
    }
}

/**
 * Bonus time: the amounts at a tap, or a minute count set by hand.
 *
 * Both end in the same place — a countdown in which the device is open — so the sheet
 * only has to answer "how long", never "which kind".
 */
@Composable
private fun BonusSheet(
    presets: List<Int>,
    running: Boolean,
    remaining: Int,
    onDismiss: () -> Unit,
    onGive: (Int) -> Unit,
    onStop: () -> Unit
) {
    var manual by remember { mutableStateOf(15) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(Nova.RadiusCard.dp))
                .background(Nova.Surface)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("Bonuszeit", fontSize = 21.sp, fontWeight = FontWeight.Medium, color = Nova.Ink)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (running) "Läuft noch ${TimeFmt.hm(remaining)} — alles ist offen."
                    else "Ein Countdown, in dem alles offen ist. Auch während der Ruhezeit.",
                    fontSize = 14.sp, color = Nova.InkMuted, lineHeight = 19.sp
                )

                Spacer(Modifier.height(18.dp))
                Text("Schnell", fontSize = 13.sp, color = Nova.InkFaint)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { minutes ->
                        Box(
                            Modifier.weight(1f).height(44.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Nova.Accent)
                                .clickable { onGive(minutes) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "+$minutes Min", fontSize = 14.sp,
                                fontWeight = FontWeight.Medium, color = Nova.Primary
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                Text("Manuell", fontSize = 13.sp, color = Nova.InkFaint)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Stepper(
                        value = "$manual Min",
                        onMinus = { manual = (manual - 5).coerceAtLeast(5) },
                        onPlus = { manual = (manual + 5).coerceAtMost(240) }
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier.height(44.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Nova.Primary)
                            .clickable { onGive(manual) }
                            .padding(horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Geben", fontSize = 15.sp, fontWeight = FontWeight.Medium,
                            color = Color.White)
                    }
                }

                if (running) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Bonuszeit beenden", fontSize = 15.sp, color = Nova.Danger,
                        modifier = Modifier.clickable { onStop() }
                    )
                }
            }
        }
    }
}

/**
 * Start a timed display lock.
 *
 * On the parent's phone this is only a rule that travels to the child with the next config
 * push. On the supervised phone itself — the portal is reachable there with the PIN — the
 * screen has to go off now rather than on the next monitor tick, so it is locked directly and
 * the monitor is nudged to take over keeping it locked.
 */
private fun startScreenLock(context: android.content.Context, sync: SyncManager, minutes: Int) {
    sync.lockScreenForMinutes(minutes)
    if (!Prefs.get(context).isParentDevice) {
        com.familylink.ios.util.ScreenLock.lockNow(context)
        com.familylink.ios.service.MonitorService.recheck(context)
    }
}

private fun releaseScreenLock(context: android.content.Context, sync: SyncManager) {
    sync.releaseScreenLock()
    if (!Prefs.get(context).isParentDevice) {
        com.familylink.ios.service.MonitorService.recheck(context)
    }
}

/**
 * One chip in the sheet's two option rows.
 *
 * [note] carries what the choice costs — the remaining allowance for a rationed lock — so the
 * price is visible before the tap rather than in a refusal afterwards. A spent chip stays on
 * screen, greyed and inert, because a row that loses buttons as the week goes on is harder to
 * read than one whose counters simply reach nought.
 */
@Composable
private fun LockChip(
    label: String,
    note: String?,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val shade = if (enabled) tint else Nova.InkFaint
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(shade.copy(alpha = if (enabled) 0.12f else 0.07f))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = shade)
        if (note != null) {
            Spacer(Modifier.height(2.dp))
            Text(note, fontSize = 11.sp, color = shade.copy(alpha = 0.85f))
        }
    }
}

/**
 * A labelled row of chips, used for the focus lengths and both kinds of lock.
 *
 * The chip row scrolls sideways rather than squeezing: four chips that each carry a counter do
 * not fit across a narrow phone, and a clipped last option is worse than one the parent swipes
 * to. The label keeps its padding while the row itself runs to the card's edge, so a scrollable
 * row visibly continues instead of ending in whitespace.
 */
@Composable
private fun LockChipRow(title: String, subtitle: String, content: @Composable RowScope.() -> Unit) {
    Column(Modifier.padding(vertical = 12.dp)) {
        Text(
            title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Nova.Ink,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            subtitle, fontSize = 13.sp, color = Nova.InkMuted,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

/** Everything about locking, in one sheet, so the card itself stays a single button. */
@Composable
private fun LockSheet(
    lockedNow: Boolean,
    lockedTimed: Boolean,
    screenLockLeft: Int,
    onDismiss: () -> Unit,
    onLockNow: () -> Unit,
    onLockFor: (Int) -> Unit,
    onFocusFor: (Int) -> Unit,
    onLockScreen: (Int) -> Unit,
    onUnlock: () -> Unit,
    onReleaseScreen: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(Nova.RadiusCard.dp))
                .background(Nova.Surface)
        ) {
            Column(Modifier.padding(vertical = 8.dp)) {
                Text(
                    "Sperren & Fokus",
                    fontSize = 19.sp, fontWeight = FontWeight.Medium, color = Nova.Ink,
                    modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 6.dp)
                )
                if (lockedNow || lockedTimed) {
                    NovaRow(
                        title = "Entsperren",
                        subtitle = "Hebt die Sperre sofort auf",
                        icon = Icons.Filled.LockOpen,
                        onClick = onUnlock
                    )
                    NovaDivider()
                }

                // Focus: the allowed apps stay usable, so these are free to reach for.
                LockChipRow("Fokus", "Nur zugelassene Apps, endet von selbst") {
                    listOf(15 to "15 Min", 30 to "30 Min", 60 to "1 Std").forEach { (m, label) ->
                        LockChip(label, null, Nova.Focus, enabled = true) { onFocusFor(m) }
                    }
                }
                NovaDivider()

                // Timed device lock: everything sealed but the phone. Short lengths, no ration —
                // this is the everyday reaction, and the open-ended lock below covers the rest.
                LockChipRow("Gerät sperren", "Nur Telefon und Notruf, endet von selbst") {
                    Prefs.PARENT_LOCK_MINUTES.forEach { m ->
                        LockChip("$m Min", null, Nova.Danger, enabled = true) { onLockFor(m) }
                    }
                }
                NovaDivider()

                // Display lock: the screen itself goes dark. Runs longest, so this is where the
                // rationed lengths live; the booking happens here so a refused one never starts.
                if (screenLockLeft > 0) {
                    NovaRow(
                        title = "Display-Sperre aufheben",
                        subtitle = "Noch ${TimeFmt.hm(screenLockLeft)}",
                        icon = Icons.Filled.PhoneAndroid,
                        onClick = onReleaseScreen
                    )
                    NovaDivider()
                } else {
                    LockChipRow("Display sperren", "Bildschirm aus, endet von selbst") {
                        Prefs.SCREEN_LOCK_MINUTES.forEach { m ->
                            val left = prefs.screenLocksLeft(m)
                            val period = prefs.screenLockPeriodLabel(m)
                            LockChip(
                                label = if (m >= 60) "${m / 60} Std" else "$m Min",
                                note = period?.let { if (left > 0) it else "aufgebraucht" },
                                tint = Nova.Primary,
                                enabled = left > 0
                            ) {
                                if (prefs.useScreenLock(m)) onLockScreen(m)
                            }
                        }
                    }
                    NovaDivider()
                }

                // Last, and deliberately open-ended: press it and the phone stays sealed until
                // the parent lifts it again. No length, no allowance.
                if (!lockedNow) {
                    NovaRow(
                        title = "Sperren",
                        subtitle = "Bleibt gesperrt, bis du es aufhebst",
                        icon = Icons.Filled.Lock,
                        onClick = onLockNow
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** The bell's page: what happened, newest first. */
@Composable
private fun EventsScreen(prefs: Prefs) {
    val events = remember { prefs.events() }
    LaunchedEffect(Unit) { prefs.markEventsRead() }
    Column(
        Modifier.fillMaxSize()
            .background(Nova.Canvas)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        Text("Meldungen", fontSize = 30.sp, fontWeight = FontWeight.Normal, color = Nova.Ink)
        Spacer(Modifier.height(16.dp))
        if (events.isEmpty()) {
            NovaCard {
                Column(Modifier.padding(16.dp)) {
                    Text("Noch nichts passiert.", fontSize = 15.sp, color = Nova.InkMuted)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Anfragen, erledigte Aufgaben und erreichte Limits erscheinen hier.",
                        fontSize = 13.sp, color = Nova.InkFaint
                    )
                }
            }
        } else {
            NovaCard {
                Column(Modifier.padding(vertical = 4.dp)) {
                    events.forEachIndexed { i, e ->
                        if (i > 0) NovaDivider()
                        NovaRow(
                            title = e.title,
                            subtitle = "${e.text} · ${TimeFmt.dayTime(e.at)}",
                            icon = when (e.type) {
                                "request" -> Icons.Filled.HourglassBottom
                                "chore" -> Icons.Filled.CheckCircle
                                "offline" -> Icons.Filled.CloudOff
                                else -> Icons.Filled.Notifications
                            }
                        )
                    }
                }
            }
        }
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
            .background(Nova.InkFaint.copy(alpha = 0.25f))
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
            .background(Nova.Fill)
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
