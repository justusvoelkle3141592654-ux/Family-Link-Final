package com.familylink.ios.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAlarm
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familylink.ios.data.AppCategory
import com.familylink.ios.data.InstalledApps
import com.familylink.ios.data.Prefs
import com.familylink.ios.sync.Account
import com.familylink.ios.ui.theme.Nova
import com.familylink.ios.util.ScreenLock
import com.familylink.ios.util.TimeFmt
import kotlinx.coroutines.delay

/**
 * The child device's home screen — deliberately different from the parent app.
 *
 * Two destinations along the bottom, as on the reference: the overview and everything else.
 * The overview is one fixed screenful on purpose — the numbers that matter are all visible at
 * a glance and nothing hides below the fold. Only the second tab scrolls.
 */
@Composable
fun ChildPortalScreen(
    onExtendTime: () -> Unit,
    onOpenChores: () -> Unit,
    onOpenFocus: () -> Unit,
    onOpenParentArea: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val sync = remember { com.familylink.ios.sync.SyncManager(context) }
    var refreshing by remember { mutableStateOf(false) }
    fun refreshNow() {
        if (refreshing) return
        refreshing = true
        kotlin.concurrent.thread(isDaemon = true) {
            runCatching { sync.syncNow() }
            android.os.Handler(android.os.Looper.getMainLooper()).post { refreshing = false }
        }
    }
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1000); tick++ } }
    @Suppress("UNUSED_EXPRESSION") tick

    /** 0 = Übersicht, 1 = Einstellungen. */
    var tab by remember { mutableStateOf(0) }

    val used = prefs.globalUsedSeconds
    val bonus = prefs.bonusSecondsToday
    val limit = prefs.globalLimitMinutes * 60 + bonus
    val remaining = (limit - used).coerceAtLeast(0)
    val fraction = if (limit == 0) 1f else (used.toFloat() / limit).coerceIn(0f, 1f)
    val bedtime = prefs.isBedtime()
    val disabled = prefs.limitsDisabled()

    val perAppAll = prefs.getPerAppSeconds().filterKeys { it != "com.familylink.ios" }
    val perApp = perAppAll.entries.sortedByDescending { it.value }
    // The headline number is the whole phone, not just what counts against the limit — the
    // budget below it is where the counted time belongs.
    val totalDevice = remember(perAppAll) {
        com.familylink.ios.data.LimitEngine(prefs).computeTotalDeviceSeconds(perAppAll)
    }

    // Pay out whatever a finished self-lock earned. The monitor does this too; doing it here as
    // well means the number is already right the moment the child unlocks and looks.
    LaunchedEffect(tick) { prefs.settleOwnLockReward() }

    // Tapping "Sperren" opens the same kind of sheet the parent portal has, so the button is
    // one choice ("how long?") instead of one silent action.
    var lockSheet by remember { mutableStateOf(false) }
    fun lock(minutes: Int, sealed: Boolean) {
        if (prefs.startOwnLock(minutes, sealed)) {
            ScreenLock.lockNow(context)
            com.familylink.ios.service.MonitorService.recheck(context)
        }
        lockSheet = false
    }
    if (lockSheet) {
        ChildLockSheet(
            rewardPerHour = if (prefs.ownLockRewardEnabled) prefs.ownLockRewardPerHour else 0,
            onDismiss = { lockSheet = false },
            onLockFor = { minutes -> lock(minutes, sealed = false) },
            onLockRestOfDay = { lock(prefs.minutesUntilMidnight(), sealed = true) }
        )
    }

    Column(Modifier.fillMaxSize().background(Nova.Canvas)) {
        Box(Modifier.weight(1f)) {
            if (tab == 0) {
                OverviewTab(
                    prefs = prefs,
                    refreshing = refreshing,
                    onRefresh = ::refreshNow,
                    totalDevice = totalDevice,
                    used = used,
                    limit = limit,
                    remaining = remaining,
                    fraction = fraction,
                    bedtime = bedtime,
                    disabled = disabled,
                    topApps = perApp.take(TOP_APPS_ON_OVERVIEW),
                    // Read here, where the one-second tick lands, so the countdown on the
                    // button actually ticks down.
                    lockedFor = prefs.screenLockRemainingSeconds(),
                    lockSealed = prefs.ownLockSealed,
                    onOpenLockSheet = { lockSheet = true },
                    onExtendTime = onExtendTime
                )
            } else {
                SettingsTab(
                    prefs = prefs,
                    perApp = perApp,
                    totalDevice = totalDevice,
                    onOpenChores = onOpenChores,
                    onOpenFocus = onOpenFocus,
                    onExtendTime = onExtendTime,
                    onOpenParentArea = onOpenParentArea
                )
            }
        }
        BottomNav(current = tab, onSelect = { tab = it })
    }
}

/**
 * The child's own lock menu. Deliberately not the parent's sheet: there is no "unlock" here —
 * lifting a lock stays the parent's job.
 *
 * Nothing is rationed. Locking the display costs the child screen time rather than buying them
 * anything, so there is nothing to guard against — and the free field at the bottom means a
 * two-minute lock is as easy to reach as an hour. Each row says what it is worth in bonus time,
 * because that is the reason to pick a longer one.
 */
@Composable
private fun ChildLockSheet(
    rewardPerHour: Int,
    onDismiss: () -> Unit,
    onLockFor: (Int) -> Unit,
    onLockRestOfDay: () -> Unit
) {
    var confirmDay by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf("") }

    /** What this length is worth, phrased for the row it sits on. */
    fun reward(minutes: Int): String? {
        if (rewardPerHour <= 0) return null
        val earned = (minutes * rewardPerHour / 60).coerceAtMost(Prefs.OWN_LOCK_REWARD_MAX_PER_DAY)
        return if (earned > 0) "+$earned Min. Bonus" else null
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(Nova.RadiusCard.dp))
                .background(Nova.Surface)
        ) {
            Column(Modifier.padding(vertical = 8.dp)) {
                Text(
                    if (confirmDay) "Wirklich für heute?" else "Handy sperren",
                    fontSize = 19.sp, fontWeight = FontWeight.Medium, color = Nova.Ink,
                    modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 6.dp)
                )
                if (confirmDay) {
                    Text(
                        "Das Handy bleibt bis Mitternacht gesperrt. Das kannst weder du noch " +
                            "deine Eltern vorher aufheben. Notrufe gehen weiter.",
                        fontSize = 13.sp, color = Nova.InkMuted,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
                    )
                    SheetRow(
                        icon = Icons.Filled.Lock,
                        title = "Ja, für heute sperren",
                        subtitle = "Endet erst um Mitternacht",
                        tint = Nova.Danger,
                        onClick = onLockRestOfDay
                    )
                    SheetDivider()
                    SheetRow(
                        icon = Icons.Filled.ChevronRight,
                        title = "Doch nicht",
                        subtitle = "Zurück zur Auswahl",
                        onClick = { confirmDay = false }
                    )
                } else {
                    if (rewardPerHour > 0) {
                        Text(
                            "Jede Stunde, die du durchhältst, bringt dir $rewardPerHour Minuten " +
                                "Bildschirmzeit zurück.",
                            fontSize = 13.sp, color = Nova.InkMuted,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp)
                        )
                    }
                    LOCK_DURATIONS.forEachIndexed { i, option ->
                        if (i > 0) SheetDivider()
                        SheetRow(
                            icon = Icons.Filled.HourglassBottom,
                            title = option.label,
                            subtitle = listOfNotNull("Endet von selbst", reward(option.minutes))
                                .joinToString(" · "),
                            onClick = { onLockFor(option.minutes) }
                        )
                    }
                    SheetDivider()
                    CustomDurationRow(
                        value = custom,
                        onValueChange = { custom = it.filter { c -> c.isDigit() }.take(4) },
                        onStart = {
                            val m = custom.toIntOrNull() ?: 0
                            if (m >= Prefs.MIN_OWN_LOCK_MIN) onLockFor(m)
                        }
                    )
                    SheetDivider()
                    SheetRow(
                        icon = Icons.Filled.Lock,
                        title = "Für heute sperren",
                        subtitle = "Bis Mitternacht — nicht mehr aufhebbar",
                        tint = Nova.Danger,
                        onClick = { confirmDay = true }
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** Type any number of minutes — two is as valid as sixty. */
@Composable
private fun CustomDurationRow(
    value: String,
    onValueChange: (String) -> Unit,
    onStart: () -> Unit
) {
    val minutes = value.toIntOrNull() ?: 0
    val valid = minutes >= Prefs.MIN_OWN_LOCK_MIN && minutes <= Prefs.MAX_SCREEN_LOCK_MIN
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(Nova.Primary.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Edit, null, tint = Nova.Primary, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Eigene Dauer", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Nova.Ink)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.width(64.dp).clip(RoundedCornerShape(10.dp))
                        .background(Nova.Fill).padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    if (value.isEmpty()) {
                        Text("z. B. 2", fontSize = 14.sp, color = Nova.InkFaint)
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 14.sp, color = Nova.Ink),
                        cursorBrush = SolidColor(Nova.Primary),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text("Minuten", fontSize = 13.sp, color = Nova.InkMuted)
            }
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier.clip(RoundedCornerShape(50))
                .background(if (valid) Nova.Primary else Nova.Fill)
                .clickable(enabled = valid) { onStart() }
                .padding(horizontal = 16.dp, vertical = 9.dp)
        ) {
            Text(
                "Sperren", fontSize = 14.sp, fontWeight = FontWeight.Medium,
                color = if (valid) Color.White else Nova.InkFaint
            )
        }
    }
}

/** One entry in the child's lock menu. */
private data class LockOption(val label: String, val minutes: Int)

private val LOCK_DURATIONS = listOf(
    LockOption("15 Minuten", 15),
    LockOption("30 Minuten", 30),
    LockOption("1 Stunde", 60),
    LockOption("6 Stunden", 360)
)

@Composable
private fun SheetRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color = Nova.Primary,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(tint.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Nova.Ink)
            Text(subtitle, fontSize = 13.sp, color = Nova.InkMuted)
        }
    }
}

@Composable
private fun SheetDivider() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Nova.Line))
    }
}

/** How many apps fit into the screen-time card without pushing the overview off screen. */
private const val TOP_APPS_ON_OVERVIEW = 3

// ---------------------------------------------------------------------------
// Übersicht — one screenful, never scrolls.
// ---------------------------------------------------------------------------

@Composable
private fun OverviewTab(
    prefs: Prefs,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    totalDevice: Int,
    used: Int,
    limit: Int,
    remaining: Int,
    fraction: Float,
    bedtime: Boolean,
    disabled: Boolean,
    topApps: List<Map.Entry<String, Int>>,
    lockedFor: Int,
    lockSealed: Boolean,
    onOpenLockSheet: () -> Unit,
    onExtendTime: () -> Unit
) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        OverviewHeader(refreshing = refreshing, onRefresh = onRefresh)

        Spacer(Modifier.height(14.dp))
        ScreenTimeCard(
            totalDevice = totalDevice,
            counted = used,
            apps = topApps,
            categoryOf = { prefs.categoryOf(it) }
        )

        Spacer(Modifier.height(12.dp))
        DeviceCard(
            remaining = remaining, used = used, limit = limit, fraction = fraction,
            deviceName = remember { Account.deviceName() },
            battery = remember { readBattery(context) }
        )

        Spacer(Modifier.height(12.dp))
        LockActionRow(
            lockedUntil = lockedFor,
            sealed = lockSealed,
            bedtime = bedtime,
            onLock = onOpenLockSheet,
            onAddTime = onExtendTime
        )

        Spacer(Modifier.height(12.dp))
        InfoListCard(prefs, disabled)
    }
}

@Composable
private fun OverviewHeader(refreshing: Boolean, onRefresh: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Übersicht", fontSize = 30.sp, fontWeight = FontWeight.Normal, color = Nova.Ink,
            modifier = Modifier.weight(1f)
        )
        RoundIconButton(Icons.Filled.Notifications, "Mitteilungen") {}
        Spacer(Modifier.width(10.dp))
        RoundIconButton(
            Icons.Filled.Refresh, "Aktualisieren",
            tint = if (refreshing) Nova.InkFaint else Nova.Primary
        ) { onRefresh() }
    }
}

@Composable
private fun RoundIconButton(
    icon: ImageVector,
    label: String,
    tint: Color = Nova.Ink,
    onClick: () -> Unit
) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(Nova.Surface).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(19.dp))
    }
}

/**
 * The headline is the whole phone: every minute the screen was on today, whether it counts
 * against the budget or not. Underneath sits how much of that was counted, and then which apps
 * the time went into — the three belong together, so they share one card.
 */
@Composable
private fun ScreenTimeCard(
    totalDevice: Int,
    counted: Int,
    apps: List<Map.Entry<String, Int>>,
    categoryOf: (String) -> AppCategory
) {
    val context = LocalContext.current
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth()
            .clip(RoundedCornerShape(Nova.RadiusCard.dp))
            .background(Nova.Surface)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    TimeFmt.hm(totalDevice),
                    fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Nova.Ink
                )
                Text("Heutige Bildschirmzeit gesamt", fontSize = 14.sp, color = Nova.InkMuted)
                Spacer(Modifier.height(2.dp))
                // The headline counts the whole phone; this says how much of it the budget
                // actually saw, so the two numbers below never look like a contradiction.
                Text(
                    "Davon ${TimeFmt.hm(counted)} angerechnet",
                    fontSize = 13.sp, color = Nova.InkFaint
                )
            }
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Nova.Primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.BarChart, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
        if (apps.isEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("Heute noch keine App genutzt.", fontSize = 13.sp, color = Nova.InkFaint)
        } else {
            val maxSec = apps.first().value.coerceAtLeast(1)
            Spacer(Modifier.height(12.dp))
            apps.forEach { (pkg, secs) ->
                UsageRow(
                    pkg = pkg,
                    label = InstalledApps.labelFor(context, pkg),
                    seconds = secs,
                    fraction = secs.toFloat() / maxSec,
                    category = categoryOf(pkg)
                )
            }
        }
    }
}

@Composable
private fun DeviceCard(
    remaining: Int, used: Int, limit: Int, fraction: Float,
    deviceName: String, battery: Int
) {
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth()
            .clip(RoundedCornerShape(Nova.RadiusCard.dp))
            .background(Nova.Surface)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Nova.SurfaceAlt),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.PhoneAndroid, null, tint = Nova.InkMuted, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${TimeFmt.hm(remaining)} übrig",
                    fontSize = 19.sp, fontWeight = FontWeight.Medium, color = Nova.Ink
                )
                Text(deviceName, fontSize = 13.sp, color = Nova.InkMuted)
            }
            if (battery in 0..100) {
                Text("$battery %", fontSize = 14.sp, color = Nova.InkMuted)
            }
        }
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Nova.Fill)
        ) {
            Box(
                Modifier.fillMaxWidth(fraction.coerceIn(0.02f, 1f)).height(4.dp)
                    .clip(RoundedCornerShape(2.dp)).background(Nova.Success)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "${TimeFmt.hm(used)} von ${TimeFmt.hm(limit)} angerechnet",
            fontSize = 13.sp, color = Nova.InkMuted
        )
    }
}

@Composable
private fun LockActionRow(
    lockedUntil: Int,
    sealed: Boolean,
    bedtime: Boolean,
    onLock: () -> Unit,
    onAddTime: () -> Unit
) {
    val label = when {
        sealed -> "Für heute gesperrt"
        lockedUntil > 0 -> "Gesperrt — ${TimeFmt.hm(lockedUntil)}"
        bedtime -> "Ruhezeit"
        else -> "Sperren"
    }
    val tint = if (sealed || lockedUntil > 0) Nova.Danger else Nova.Ink
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(50))
                .background(Nova.Surface)
                // A running lock is a statement, not a button — there is nothing left to pick.
                .clickable(enabled = lockedUntil == 0) { onLock() },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Lock, null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = tint)
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(Nova.SurfaceAlt)
                .clickable { onAddTime() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.AddAlarm, "Mehr Zeit anfragen",
                tint = Nova.Primary, modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun InfoListCard(prefs: Prefs, disabled: Boolean) {
    val limitText = "Limit von ${TimeFmt.hm(prefs.globalLimitMinutes * 60)}" +
        if (prefs.hardCapEnabled) " · Gesamt ${TimeFmt.hm(prefs.hardCapMinutes * 60)}" else ""
    val scheduleText = if (prefs.bedtimeEnabled)
        "${TimeFmt.clock(prefs.bedtimeStartMin)}–${TimeFmt.clock(prefs.bedtimeEndMin)} Uhr"
    else "Keine Ruhezeit festgelegt"

    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth()
            .clip(RoundedCornerShape(Nova.RadiusCard.dp))
            .background(Nova.Surface)
    ) {
        InfoRow(Icons.Filled.HourglassBottom, "Zeitlimits", if (disabled) "Heute frei" else limitText)
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Nova.Line))
        }
        InfoRow(Icons.Filled.Bedtime, "Zeitpläne", scheduleText)
    }
}

@Composable
private fun InfoRow(icon: ImageVector, title: String, subtitle: String) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(Nova.SurfaceAlt),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Nova.Primary, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Nova.Ink)
            Text(subtitle, fontSize = 13.sp, color = Nova.InkMuted)
        }
    }
}

// ---------------------------------------------------------------------------
// Einstellungen — everything that does not fit on one screen lives here, and this
// is the only tab that scrolls.
// ---------------------------------------------------------------------------

@Composable
private fun SettingsTab(
    prefs: Prefs,
    perApp: List<Map.Entry<String, Int>>,
    totalDevice: Int,
    onOpenChores: () -> Unit,
    onOpenFocus: () -> Unit,
    onExtendTime: () -> Unit,
    onOpenParentArea: () -> Unit
) {
    val context = LocalContext.current

    // Turning the launcher off is the one thing on this page that is not the child's to do.
    var askPinThenHome by remember { mutableStateOf(false) }
    if (askPinThenHome) {
        PinScreen(
            mode = PinMode.VERIFY,
            onSuccess = {
                askPinThenHome = false
                // Android has no API to set a launcher; both directions go through its own
                // chooser. Settings is released for a few minutes so the PIN gate in front of
                // it does not ask a second time on the way there.
                prefs.unlockSettings(3)
                com.familylink.ios.util.LauncherGuard.openHomeChooser(context)
            },
            onCancel = { askPinThenHome = false }
        )
        return
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            "Einstellungen", fontSize = 30.sp, fontWeight = FontWeight.Normal, color = Nova.Ink,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp)
        )

        // ---- what the child can actually do ------------------------------
        SectionTitle("Zeit verdienen")
        Card {
            val chores = prefs.getChores()
            val openChores = chores.count { it.isOpen }
            SettingsRow(
                icon = Icons.Filled.CheckCircle,
                title = "Aufgaben erledigen",
                subtitle = if (openChores > 0) "$openChores offen · verdiene Extra-Zeit"
                else "Keine offenen Aufgaben",
                onClick = onOpenChores
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Filled.AddAlarm,
                title = "Mehr Zeit anfragen",
                subtitle = "Deine Eltern entscheiden",
                onClick = onExtendTime
            )
        }

        // ---- focus, with what is left of this week's long sessions -------
        SectionTitle("Handy weglegen")
        Card {
            val ownFocus = prefs.effectiveFocusSession()
            SettingsRow(
                icon = Icons.Filled.Lock,
                title = "Fokus-Zeit starten",
                subtitle = if (ownFocus.isRunning())
                    "Läuft — noch ${TimeFmt.hm(ownFocus.remainingSeconds())}"
                else "Nur erlaubte Apps, für eine feste Zeit",
                onClick = onOpenFocus
            )
            Text(
                "So oft und so lange du willst — und Zeit, die du durchhältst, bringt dir " +
                    "Bildschirmzeit zurück.",
                fontSize = 12.sp, color = Nova.InkFaint,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
            )
        }

        // ---- the reward, where it is earned ------------------------------
        if (prefs.ownLockRewardEnabled && prefs.ownLockRewardPerHour > 0) {
            SectionTitle("Bonus fürs Sperren")
            Card {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${prefs.ownLockRewardPerHour} Min. pro Stunde",
                            fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Nova.Ink
                        )
                        Text(
                            "Für jede Stunde, die du dein Handy selbst gesperrt lässt — " +
                                "höchstens ${Prefs.OWN_LOCK_REWARD_MAX_PER_DAY} Min. am Tag.",
                            fontSize = 13.sp, color = Nova.InkMuted
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "heute +${prefs.ownLockEarnedToday()}",
                        fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Nova.Success
                    )
                }
            }
        }

        // ---- the numbers -------------------------------------------------
        SectionTitle("Zahlen")
        Card {
            NumberRow("Handynutzung gesamt", TimeFmt.hm(totalDevice), Nova.Ink)
            if (prefs.limitScope != com.familylink.ios.data.LimitScope.DAY) {
                val weekUsed = prefs.weekCountedSeconds()
                val weekPot = prefs.weeklyLimitMinutes * 60
                RowDivider()
                NumberRow(
                    "Diese Woche",
                    "${TimeFmt.hm(weekUsed)} von ${TimeFmt.hm(weekPot)}",
                    if (weekUsed >= weekPot) Nova.Danger else Nova.Ink
                )
            }
            if (prefs.hardCapEnabled && prefs.hardCapScope != com.familylink.ios.data.LimitScope.DAY) {
                RowDivider()
                NumberRow(
                    "Gesamt diese Woche",
                    "${TimeFmt.hm(prefs.weekTotalSeconds())} von " +
                        TimeFmt.hm(prefs.weeklyHardCapMinutes * 60),
                    Nova.Ink
                )
            }
        }

        // ---- the full per-app list; the overview only has room for the top few
        SectionTitle("Heute genutzt")
        Card {
            if (perApp.isEmpty()) {
                Text(
                    "Heute noch keine App genutzt.",
                    fontSize = 14.sp, color = Nova.InkMuted,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                val maxSec = perApp.first().value.coerceAtLeast(1)
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    perApp.take(20).forEach { (pkg, secs) ->
                        UsageRow(
                            pkg = pkg,
                            label = InstalledApps.labelFor(context, pkg),
                            seconds = secs,
                            fraction = secs.toFloat() / maxSec,
                            category = prefs.categoryOf(pkg)
                        )
                    }
                }
            }
        }

        SectionTitle("Für Eltern")
        Card {
            SettingsRow(
                icon = Icons.Filled.Shield,
                title = "Eltern-Bereich",
                subtitle = "Regeln ändern — mit PIN",
                onClick = onOpenParentArea
            )
            // The way back to the phone's own launcher. Behind the PIN, because a home screen
            // the child can swap out is not a home screen — this row would otherwise be the
            // first thing they tapped.
            if (com.familylink.ios.util.LauncherGuard.isLauncherActive(context)) {
                RowDivider()
                SettingsRow(
                    icon = Icons.Filled.Home,
                    title = "Startbildschirm zurücksetzen",
                    subtitle = "Zurück zum normalen Startbildschirm — mit PIN",
                    onClick = { askPinThenHome = true }
                )
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

/** The one card shape the settings page uses, so every group looks the same. */
@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth()
            .clip(RoundedCornerShape(Nova.RadiusCard.dp))
            .background(Nova.Surface),
        content = content
    )
}

@Composable
private fun RowDivider() {
    Box(Modifier.fillMaxWidth().padding(start = 70.dp, end = 16.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Nova.Line))
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(Nova.SurfaceAlt),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Nova.Primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Nova.Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Nova.InkMuted, fontSize = 13.sp)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = Nova.InkFaint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun NumberRow(label: String, value: String, valueColor: Color) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 15.sp, color = Nova.InkMuted, modifier = Modifier.weight(1f))
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = valueColor)
    }
}

// ---------------------------------------------------------------------------
// Shared pieces
// ---------------------------------------------------------------------------

@Composable
private fun BottomNav(current: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Nova.Surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        NavItem(Icons.Filled.BarChart, "Übersicht", current == 0) { onSelect(0) }
        NavItem(Icons.Filled.Shield, "Einstellungen", current == 1) { onSelect(1) }
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clickable { onClick() }.padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.clip(RoundedCornerShape(50))
                .background(if (selected) Nova.Accent else Color.Transparent)
                .padding(horizontal = 18.dp, vertical = 4.dp)
        ) {
            Icon(
                icon, null,
                tint = if (selected) Nova.Primary else Nova.InkMuted,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label, fontSize = 12.sp,
            color = if (selected) Nova.Primary else Nova.InkMuted,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
        )
    }
}

private fun readBattery(context: android.content.Context): Int = runCatching {
    val bm = context.getSystemService(android.content.Context.BATTERY_SERVICE)
        as android.os.BatteryManager
    bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
}.getOrDefault(-1)

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Nova.InkMuted,
        modifier = Modifier.padding(start = 22.dp, end = 20.dp, top = 22.dp, bottom = 8.dp)
    )
}

@Composable
private fun UsageRow(
    pkg: String,
    label: String,
    seconds: Int,
    fraction: Float,
    category: AppCategory
) {
    val context = LocalContext.current
    val icon = remember(pkg) { InstalledApps.iconBitmap(context, pkg) }
    val barColor = when (category) {
        AppCategory.PLUS -> Nova.Success
        AppCategory.LIMIT -> Nova.Warning
        AppCategory.BLOCKED -> Nova.Danger
        AppCategory.STANDARD -> Nova.Primary
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)).background(Nova.Fill),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                Image(
                    bitmap = icon.asImageBitmap(), contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
            } else {
                Text(label.take(1), fontSize = 15.sp, color = Nova.Ink)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontSize = 15.sp, color = Nova.Ink, modifier = Modifier.weight(1f))
                Text(
                    TimeFmt.hm(seconds), fontSize = 13.sp,
                    fontWeight = FontWeight.Medium, color = Nova.InkMuted
                )
            }
            Spacer(Modifier.height(5.dp))
            Box(
                Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp))
                    .background(Nova.Fill)
            ) {
                Box(
                    Modifier.fillMaxWidth(fraction.coerceIn(0.02f, 1f)).height(5.dp)
                        .clip(RoundedCornerShape(3.dp)).background(barColor)
                )
            }
        }
    }
}
