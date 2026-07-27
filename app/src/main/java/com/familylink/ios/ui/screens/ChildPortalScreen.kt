package com.familylink.ios.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familylink.ios.data.AppCategory
import com.familylink.ios.data.InstalledApps
import com.familylink.ios.data.Prefs
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

    val accent = when {
        disabled -> Nova.Success
        bedtime -> Nova.Night
        fraction >= 1f -> Nova.Danger
        fraction >= 0.8f -> Nova.Warning
        else -> Nova.Primary
    }

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(Nova.PageGradient))
            .verticalScroll(rememberScrollState())
    ) {
        // ---- header ----
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Meine Zeit", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Nova.Ink)
                Text(TimeFmt.nowLong(), fontSize = 13.sp, color = Nova.InkMuted)
            }
            if (prefs.syncConfigured) {
                Box(
                    Modifier.size(38.dp).clip(CircleShape)
                        .background(Nova.Primary.copy(alpha = 0.12f))
                        .clickable(enabled = !refreshing) { refreshNow() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Refresh, "Aktualisieren",
                        tint = if (refreshing) Nova.InkFaint else Nova.Primary,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            SyncBadge(prefs)
        }

        Spacer(Modifier.height(20.dp))

        // ---- time ring ----
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(200.dp)) {
                    val stroke = 20.dp.toPx()
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    val topLeft = Offset(stroke / 2, stroke / 2)
                    drawArc(
                        color = Color(0x14000000), startAngle = 0f, sweepAngle = 360f,
                        useCenter = false, topLeft = topLeft, size = arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = accent, startAngle = -90f, sweepAngle = 360f * (1f - fraction),
                        useCenter = false, topLeft = topLeft, size = arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    when {
                        disabled -> {
                            Text("Frei", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Nova.Success)
                            Text("bis 23:00", fontSize = 13.sp, color = Nova.InkMuted)
                        }
                        bedtime -> {
                            Text("Ruhezeit", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Nova.Night)
                            Text("bis ${TimeFmt.clock(prefs.bedtimeEndMin)}", fontSize = 13.sp, color = Nova.InkMuted)
                        }
                        else -> {
                            Text(TimeFmt.hm(remaining), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Nova.Ink)
                            Text("übrig", fontSize = 13.sp, color = Nova.InkMuted)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "Angerechnet ${TimeFmt.hm(used)} von ${TimeFmt.hm(limit)}" +
                if (bonus > 0) " (+${bonus / 60} Bonus)" else "",
            fontSize = 13.sp, color = Nova.InkMuted,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
        )
        // Same figure the parent portal shows, so both sides never disagree.
        val totalDevice = perAppAll.values.sum()
        Text(
            "Handynutzung gesamt: ${TimeFmt.hm(totalDevice)}" +
                if (prefs.hardCapEnabled) " von max. ${TimeFmt.hm(prefs.hardCapMinutes * 60)}" else "",
            fontSize = 13.sp, color = Nova.InkFaint,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp)
        )

        Spacer(Modifier.height(8.dp))

        // ---- next bedtime hint ----
        if (!bedtime && prefs.bedtimeEnabled) {
            InfoStrip("Ruhezeit beginnt um ${TimeFmt.clock(prefs.bedtimeStartMin)} Uhr", Nova.Night)
        }

        Spacer(Modifier.height(16.dp))

        // ---- usage list ----
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

        // ---- actions ----
        Column(Modifier.padding(horizontal = 20.dp)) {
            // Chores: earn extra time by doing jobs.
            val chores = prefs.getChores()
            val openChores = chores.count { it.isOpen }
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(Brush.horizontalGradient(Nova.BrandGradient))
                    .clickable { onOpenChores() }.padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Aufgaben erledigen", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (openChores > 0) "$openChores offen · verdiene Extra-Zeit"
                            else "Keine offenen Aufgaben",
                            color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp
                        )
                    }
                    Text("→", color = Color.White, fontSize = 22.sp)
                }
            }
            Spacer(Modifier.height(10.dp))

            // Focus you start yourself — for deliberately putting the phone away.
            val ownFocus = prefs.effectiveFocusSession()
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(Nova.Focus.copy(alpha = 0.14f))
                    .clickable { onOpenFocus() }.padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Handy weglegen", color = Nova.Focus, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (ownFocus.isRunning())
                                "Läuft — noch ${TimeFmt.hm(ownFocus.remainingSeconds())}"
                            else "Fokus-Zeit selbst starten",
                            color = Nova.InkMuted, fontSize = 13.sp
                        )
                    }
                    Text("→", color = Nova.Focus, fontSize = 22.sp)
                }
            }

            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(15.dp))
                    .background(Nova.Fill).clickable { onExtendTime() },
                contentAlignment = Alignment.Center
            ) {
                Text("Mehr Zeit anfragen", color = Nova.Primary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Eltern-Bereich",
                fontSize = 14.sp, color = Nova.InkFaint,
                modifier = Modifier.fillMaxWidth().clickable { onOpenParentArea() }.padding(10.dp)
            )
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun SyncBadge(prefs: Prefs) {
    val online = prefs.syncConfigured &&
        System.currentTimeMillis() - prefs.lastSyncAt < 120_000
    val color = if (online) Nova.Success else Nova.InkFaint
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (online) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
            contentDescription = null, tint = color, modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text(if (online) "Verbunden" else "Offline", fontSize = 12.sp, color = color)
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
private fun InfoStrip(text: String, color: Color) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.10f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Lock, null, tint = color, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 13.sp, color = color)
    }
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
