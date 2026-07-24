package com.familylink.ios.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familylink.ios.data.AppCategory
import com.familylink.ios.data.InstalledApps
import com.familylink.ios.data.Prefs
import com.familylink.ios.ui.cupertino.CupertinoButton
import com.familylink.ios.ui.theme.Cupertino
import com.familylink.ios.util.TimeFmt
import kotlinx.coroutines.delay

/**
 * The block screen (Listen-Ansicht) — a leavable screen, not a lock. Two tabs:
 *  - "Gesperrt": all apps with status; blocked ones greyed with a lock icon.
 *  - "Plus-Apps": grid of always-available apps; tapping an icon opens the app directly.
 * Plus a parent-protected "Zeit verlängern" action.
 */
@Composable
fun BlockListScreen(
    reasonTitle: String,
    reasonDetail: String,
    onLaunchApp: (String) -> Unit,
    onExtend: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val apps = remember { InstalledApps.load(context) }
    var clock by remember { mutableStateOf(TimeFmt.now()) }
    var showPlus by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { while (true) { clock = TimeFmt.now(); delay(1000) } }

    val perApp = prefs.getPerAppSeconds()
    val bedtime = prefs.isBedtime()
    val plusApps = remember { apps.filter { prefs.categoryOf(it.packageName) == AppCategory.PLUS } }

    Column(
        Modifier.fillMaxSize().background(Cupertino.SystemBackground).verticalScroll(rememberScrollState())
    ) {
        // Header
        Column(
            Modifier.fillMaxWidth().padding(top = 44.dp, start = 24.dp, end = 24.dp, bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = Cupertino.Blue, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(8.dp))
            Text(clock, fontSize = 46.sp, fontWeight = FontWeight.Thin, color = Cupertino.Label)
            Text(reasonTitle, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Cupertino.Label)
            Text(reasonDetail, fontSize = 14.sp, color = Cupertino.SecondaryLabel, textAlign = TextAlign.Center)
        }

        Spacer(Modifier.height(14.dp))

        // Segmented control: Gesperrt | Plus-Apps
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(10.dp)).background(Color(0x11000000)).padding(3.dp)
        ) {
            SegTab("Übersicht", selected = !showPlus, modifier = Modifier.weight(1f)) { showPlus = false }
            SegTab("Plus-Apps", selected = showPlus, modifier = Modifier.weight(1f)) { showPlus = true }
        }

        Spacer(Modifier.height(12.dp))

        if (showPlus) {
            PlusGrid(context, plusApps, onLaunchApp)
        } else {
            Column(Modifier.padding(horizontal = 16.dp)) {
                apps.forEach { app ->
                    val cat = prefs.categoryOf(app.packageName)
                    val limit = prefs.limitMinutesOf(app.packageName) * 60
                    val used = perApp[app.packageName] ?: 0
                    val globalLimit = prefs.globalLimitMinutes * 60 + prefs.bonusSecondsToday
                    val isBlocked = when (cat) {
                        AppCategory.PLUS -> false
                        AppCategory.BLOCKED -> true
                        AppCategory.LIMIT -> used >= limit || bedtime
                        AppCategory.STANDARD -> bedtime || prefs.globalUsedSeconds >= globalLimit
                    }
                    AppRow(app.label, cat, isBlocked)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Actions
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CupertinoButton(text = "Zeit verlängern (Eltern)", color = Cupertino.Blue, onClick = onExtend)
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier.size(60.dp).clip(CircleShape).background(Cupertino.Green)
                    .clickable {
                        val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(dial) }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Phone, contentDescription = "Anrufen", tint = Color.White, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text("Notruf / Anrufen", fontSize = 13.sp, color = Cupertino.SecondaryLabel)
            Spacer(Modifier.height(16.dp))
            Text("Zum Startbildschirm", fontSize = 16.sp, color = Cupertino.Blue,
                modifier = Modifier.clickable { onClose() }.padding(8.dp))
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SegTab(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Color.White else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Cupertino.Label else Cupertino.SecondaryLabel
        )
    }
}

@Composable
private fun PlusGrid(
    context: android.content.Context,
    plusApps: List<InstalledApps.Entry>,
    onLaunchApp: (String) -> Unit
) {
    if (plusApps.isEmpty()) {
        Text(
            "Es sind keine Plus-Apps festgelegt. Im Eltern-Portal können Apps auf Plus gestellt werden.",
            fontSize = 14.sp, color = Cupertino.SecondaryLabel,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        return
    }
    Column(Modifier.padding(horizontal = 16.dp)) {
        plusApps.chunked(4).forEach { rowApps ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowApps.forEach { app ->
                    val bmp = remember(app.packageName) { InstalledApps.iconBitmap(context, app.packageName) }
                    Column(
                        Modifier.weight(1f).clickable { onLaunchApp(app.packageName) },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            Modifier.size(58.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x11000000)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (bmp != null) {
                                Image(bitmap = bmp.asImageBitmap(), contentDescription = app.label, modifier = Modifier.size(52.dp))
                            } else {
                                Text(app.label.take(1), fontSize = 24.sp, color = Cupertino.Label)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(app.label, fontSize = 11.sp, color = Cupertino.Label, maxLines = 1, textAlign = TextAlign.Center)
                    }
                }
                repeat(4 - rowApps.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun AppRow(label: String, category: AppCategory, blocked: Boolean) {
    val (statusText, statusColor) = when {
        category == AppCategory.PLUS -> "Verfügbar" to Cupertino.Green
        blocked -> "Gesperrt" to Cupertino.Red
        else -> "Verfügbar" to Cupertino.Green
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label, fontSize = 17.sp,
            color = if (blocked) Cupertino.TertiaryLabel else Cupertino.Label,
            modifier = Modifier.weight(1f)
        )
        if (blocked) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = Cupertino.TertiaryLabel, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(statusText, fontSize = 14.sp, color = statusColor, fontWeight = FontWeight.SemiBold)
    }
}
