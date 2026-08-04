package com.familylink.ios.ui.screens

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
import androidx.compose.material.icons.filled.AddAlarm
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.LocalFireDepartment
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familylink.ios.data.AppCategory
import com.familylink.ios.data.InstalledApps
import com.familylink.ios.data.Prefs
import com.familylink.ios.sync.Account
import com.familylink.ios.ui.theme.Nova
import com.familylink.ios.util.TimeFmt
import kotlinx.coroutines.delay

/**
 * The child device's home screen — deliberately different from the parent app.
 * It is purely informational: remaining time, what was used today and for how long,
 * which apps are free, and when bedtime starts. No settings, no rules to change.
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

    val used = prefs.globalUsedSeconds
    val bonus = prefs.bonusSecondsToday
    val limit = prefs.globalLimitMinutes * 60 + bonus
    val remaining = (limit - used).coerceAtLeast(0)
    val fraction = if (limit == 0) 1f else (used.toFloat() / limit).coerceIn(0f, 1f)
    val bedtime = prefs.isBedtime()
    val disabled = prefs.limitsDisabled()

    val perAppAll = prefs.getPerAppSeconds().filterKeys { it != "com.familylink.ios" }
    val perApp = perAppAll.entries.sortedByDescending { it.value }.take(12)

    val battery = remember { readBattery(context) }

    Column(Modifier.fillMaxSize().background(Nova.Canvas)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
        ) {
            OverviewHeader(refreshing = refreshing, onRefresh = ::refreshNow)

            Spacer(Modifier.height(16.dp))
            ScreenTimeCard(context, used, perApp)

            Spacer(Modifier.height(14.dp))
            DeviceCard(
                remaining = remaining, used = used, limit = limit, fraction = fraction,
                deviceName = remember { Account.deviceName() }, battery = battery
            )

            if (prefs.limitScope != com.familylink.ios.data.LimitScope.DAY) {
                val weekUsed = prefs.weekCountedSeconds()
                val weekPot = prefs.weeklyLimitMinutes * 60
                SubText(
                    "Diese Woche: ${TimeFmt.hm(weekUsed)} von ${TimeFmt.hm(weekPot)}",
                    if (weekUsed >= weekPot) Nova.Danger else Nova.InkFaint
                )
            }
            if (prefs.hardCapEnabled && prefs.hardCapScope != com.familylink.ios.data.LimitScope.DAY) {
                SubText(
                    "Gesamt diese Woche: ${TimeFmt.hm(prefs.weekTotalSeconds())} von " +
                        TimeFmt.hm(prefs.weeklyHardCapMinutes * 60),
                    Nova.InkFaint
                )
            }

            Spacer(Modifier.height(14.dp))
            LockActionRow(
                locked = bedtime,
                onLock = onOpenFocus,
                onAddTime = onExtendTime
            )

            Spacer(Modifier.height(14.dp))
            InfoListCard(prefs, disabled)

            Spacer(Modifier.height(20.dp))

            // ---- streak ----
            if (prefs.streakEnabled) {
                StreakCard(prefs.streakState())
                Spacer(Modifier.height(16.dp))
            }

            // ---- chores ----
            val chores = prefs.getChores()
            val openChores = chores.count { it.isOpen }
            Column(Modifier.padding(horizontal = 20.dp)) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Nova.RadiusCard.dp))
                        .background(Nova.Surface)
                        .clickable { onOpenChores() }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(Nova.SurfaceAlt),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.CheckCircle, null, tint = Nova.Primary,
                            modifier = Modifier.size(21.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Aufgaben erledigen", color = Nova.Ink, fontSize = 16.sp,
                            fontWeight = FontWeight.Medium)
                        Text(
                            if (openChores > 0) "$openChores offen · verdiene Extra-Zeit"
                            else "Keine offenen Aufgaben",
                            color = Nova.InkMuted, fontSize = 13.sp
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = Nova.InkFaint,
                        modifier = Modifier.size(20.dp))
                }
            }

            // ---- usage list ----
            Spacer(Modifier.height(20.dp))
            SectionTitle("Heute genutzt")
            if (perApp.isEmpty()) {
                Text(
                    "Heute noch keine App genutzt.",
                    fontSize = 14.sp, color = Nova.InkMuted,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            } else {
                val maxSec = perApp.first().value.coerceAtLeast(1)
                Column(Modifier.padding(horizontal = 16.dp)) {
                    perApp.forEach { (pkg, secs) ->
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
            Spacer(Modifier.height(20.dp))
        }

        BottomNav(onOpenParentArea = onOpenParentArea)
    }
}

private fun readBattery(context: android.content.Context): Int = runCatching {
    val bm = context.getSystemService(android.content.Context.BATTERY_SERVICE) as android.os.BatteryManager
    bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
}.getOrDefault(-1)

@Composable
private fun OverviewHeader(refreshing: Boolean, onRefresh: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp),
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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

@Composable
private fun ScreenTimeCard(
    context: android.content.Context,
    used: Int,
    perApp: List<Map.Entry<String, Int>>
) {
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth()
            .clip(RoundedCornerShape(Nova.RadiusCard.dp))
            .background(Nova.Surface)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(TimeFmt.hm(used), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Nova.Ink)
                Text("Heutige Bildschirmzeit", fontSize = 14.sp, color = Nova.InkMuted)
            }
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Nova.Primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.BarChart, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
        if (perApp.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Row {
                perApp.take(3).forEach { (pkg, _) ->
                    AppBadgeIcon(context, pkg)
                    Spacer(Modifier.width(10.dp))
                }
            }
        }
    }
}

@Composable
private fun AppBadgeIcon(context: android.content.Context, pkg: String) {
    val icon = remember(pkg) { InstalledApps.iconBitmap(context, pkg) }
    Box(Modifier.size(44.dp)) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(Nova.Fill),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                Image(bitmap = icon.asImageBitmap(), contentDescription = null, modifier = Modifier.size(36.dp))
            }
        }
        Box(
            Modifier.size(16.dp).clip(CircleShape).background(Nova.Success)
                .align(Alignment.BottomEnd),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(10.dp))
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
                Text("${TimeFmt.hm(remaining)} übrig", fontSize = 19.sp, fontWeight = FontWeight.Medium, color = Nova.Ink)
                Text(deviceName, fontSize = 13.sp, color = Nova.InkMuted)
            }
            if (battery in 0..100) {
                Text("$battery %", fontSize = 14.sp, color = Nova.InkMuted)
                Spacer(Modifier.width(2.dp))
                Icon(Icons.Filled.ChevronRight, null, tint = Nova.InkFaint, modifier = Modifier.size(18.dp))
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
private fun SubText(text: String, color: Color) {
    Text(
        text, fontSize = 13.sp, color = color,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp)
    )
}

@Composable
private fun LockActionRow(locked: Boolean, onLock: () -> Unit, onAddTime: () -> Unit) {
    Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(50))
                .background(Nova.Surface).clickable { onLock() },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Lock, null, tint = Nova.Ink, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (locked) "Gesperrt" else "Sperren",
                fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Nova.Ink
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(Nova.SurfaceAlt)
                .clickable { onAddTime() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.AddAlarm, "Mehr Zeit anfragen", tint = Nova.Primary, modifier = Modifier.size(22.dp))
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
private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
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

@Composable
private fun BottomNav(onOpenParentArea: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Nova.Surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        NavItem(Icons.Filled.BarChart, "Bildschirmzeit", selected = true) {}
        NavItem(Icons.Filled.Shield, "Einstellungen", selected = false) { onOpenParentArea() }
        NavItem(Icons.Filled.Apps, "Apps", selected = false) { onOpenParentArea() }
    }
}

@Composable
private fun NavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
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

/**
 * Days in a row inside the daily budget, what the next milestone is worth, and — when there is
 * one today — the reward or the reduction in force right now.
 *
 * Written for the child: it says what they get and what they can reach next, not what they lost.
 */
@Composable
private fun StreakCard(state: com.familylink.ios.data.StreakState) {
    val streak = state.current
    val bonus = state.bonusMinutesToday
    val malus = state.penaltyMinutesToday
    val accent = when {
        bonus > 0 -> Nova.Success
        malus > 0 -> Nova.Warning
        streak > 0 -> Nova.Primary
        else -> Nova.InkMuted
    }
    Column(Modifier.padding(horizontal = 16.dp)) {
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(Nova.RadiusCard.dp))
                .background(Nova.Surface)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(accent.copy(alpha = 0.13f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.LocalFireDepartment, null, tint = accent,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when (streak) {
                            0 -> "Noch keine Serie"
                            1 -> "1 Tag im Limit"
                            else -> "$streak Tage im Limit"
                        },
                        fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Nova.Ink
                    )
                    val toGo = com.familylink.ios.data.StreakLogic.daysToNextMilestone(streak)
                    val reward = com.familylink.ios.data.StreakLogic.nextMilestoneBonus(streak)
                    Text(
                        when {
                            toGo == null -> "Alle Stufen erreicht — stark!"
                            toGo == 1 -> "Noch 1 Tag bis +$reward Min."
                            else -> "Noch $toGo Tage bis +$reward Min."
                        },
                        fontSize = 13.sp, color = Nova.InkMuted
                    )
                }
                if (state.longest > 0) {
                    Text("Best: ${state.longest}", fontSize = 12.sp, color = Nova.InkFaint)
                }
            }

            if (bonus > 0 || malus > 0) {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(accent.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (bonus > 0)
                            "Heute +$bonus Min. für ${state.milestoneReached} Tage Serie!"
                        else
                            "Heute −$malus Min., weil das Limit gestern überschritten war.",
                        fontSize = 13.sp, fontWeight = FontWeight.Medium, color = accent
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(), fontSize = 12.sp, color = Nova.InkMuted,
        modifier = Modifier.padding(start = 20.dp, bottom = 6.dp)
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
                Image(bitmap = icon.asImageBitmap(), contentDescription = null, modifier = Modifier.size(32.dp))
            } else {
                Text(label.take(1), fontSize = 15.sp, color = Nova.Ink)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontSize = 15.sp, color = Nova.Ink, modifier = Modifier.weight(1f))
                Text(TimeFmt.hm(seconds), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Nova.InkMuted)
            }
            Spacer(Modifier.height(5.dp))
            Box(
                Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp))
                    .background(Color(0x11000000))
            ) {
                Box(
                    Modifier.fillMaxWidth(fraction.coerceIn(0.02f, 1f)).height(5.dp)
                        .clip(RoundedCornerShape(3.dp)).background(barColor)
                )
            }
        }
    }
}
