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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
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
import com.familylink.ios.ui.theme.Nova
import com.familylink.ios.util.TimeFmt
import kotlinx.coroutines.delay

/**
 * The block screen — a leavable screen, not a lock. Deliberately minimal:
 *  - the live clock and the reason (bedtime shows when it unlocks again),
 *  - a "Zugelassen + Apps" button that opens the grid of allowed apps (tap to launch),
 *  - a parent-protected "Verlängerung" button,
 *  - the phone is always available, plus a discreet parent-portal entry.
 */
@Composable
fun BlockListScreen(
    reasonTitle: String,
    reasonDetail: String,
    bedtime: Boolean,
    hardLock: Boolean,
    /**
     * Fully sealed: bedtime, the absolute daily ceiling or a manual lock by the parent.
     * Nothing can be opened from here — not the allowed apps, not an extension, not the home
     * screen. Only the phone and the PIN-protected parent entry stay reachable.
     */
    sealed: Boolean = bedtime,
    onLaunchApp: (String) -> Unit,
    onExtend: () -> Unit,
    onOpenPortal: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    var showPlus by remember { mutableStateOf(false) }

    val focus = remember { prefs.effectiveFocusSession() }
    val focusRunning = focus.isRunning()

    if (showPlus && !sealed) {
        val apps = remember { InstalledApps.load(context) }
        // While a focus session runs, an allowed-plus app that the session does not include is
        // hidden here as well — the child should not even be offered it.
        val plusApps = remember(focusRunning) {
            if (focusRunning) apps.filter { it.packageName in focus.allowed }
            else apps.filter { prefs.categoryOf(it.packageName) == AppCategory.PLUS }
        }
        PlusAppsView(
            plusApps = plusApps,
            title = if (focusRunning) "Fokus-Apps" else "Zugelassen + Apps",
            onLaunchApp = onLaunchApp,
            onBack = { showPlus = false }
        )
        return
    }

    var clock by remember { mutableStateOf(TimeFmt.now()) }
    LaunchedEffect(Unit) { while (true) { clock = TimeFmt.now(); delay(1000) } }

    Column(
        Modifier.fillMaxSize()
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    if (bedtime) listOf(Color(0xFFEDE9FB), Nova.Canvas)
                    else Nova.PageGradient
                )
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(56.dp))
        val accent = if (bedtime) Nova.Night else Nova.Primary
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = accent, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(TimeFmt.nowLong(), fontSize = 16.sp, color = Nova.InkMuted)
        Text(clock, fontSize = 72.sp, fontWeight = FontWeight.Thin, color = Nova.Ink)
        Spacer(Modifier.height(16.dp))
        Text(reasonTitle, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Nova.Ink)
        Spacer(Modifier.height(6.dp))
        Text(reasonDetail, fontSize = 16.sp, color = Nova.InkMuted, textAlign = TextAlign.Center)

        Spacer(Modifier.weight(1f))

        // Sealed: no allowed apps and no extension — there is nothing to open from here.
        if (!sealed) {
            BigButton(
                if (focusRunning) "Fokus-Apps" else "Zugelassen + Apps",
                Nova.Success
            ) { showPlus = true }
            Spacer(Modifier.height(12.dp))
            BigButton("Bonuszeit", Nova.Primary) { onExtend() }
            Spacer(Modifier.height(24.dp))
        }

        // Phone is always available.
        Box(
            Modifier.size(60.dp).clip(CircleShape).background(Color(0x1A34C759))
                .clickable {
                    val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(dial) }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Phone, contentDescription = "Telefon", tint = Nova.Success, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text("Telefon", fontSize = 13.sp, color = Nova.InkMuted)

        Spacer(Modifier.height(16.dp))
        when {
            // Sealed: no way back to the home screen. The parent entry stays, but it asks for
            // the PIN, so it is not a way around the lock.
            sealed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (bedtime) Text("Gute Nacht", fontSize = 14.sp, color = Nova.InkFaint)
                Text("Eltern-Portal", fontSize = 15.sp, color = Nova.Primary,
                    modifier = Modifier.clickable { onOpenPortal() }.padding(8.dp))
            }
            // Hard lock (day limit): no way to simply close the screen — only Plus apps,
            // an extension, the phone, or the parent portal.
            hardLock -> Text("Eltern-Portal", fontSize = 15.sp, color = Nova.Primary,
                modifier = Modifier.clickable { onOpenPortal() }.padding(8.dp))
            else -> Row {
                Text("Startbildschirm", fontSize = 15.sp, color = Nova.Primary,
                    modifier = Modifier.clickable { onClose() }.padding(8.dp))
                Spacer(Modifier.size(16.dp))
                Text("Eltern-Portal", fontSize = 15.sp, color = Nova.Primary,
                    modifier = Modifier.clickable { onOpenPortal() }.padding(8.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun BigButton(text: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(16.dp)).background(color).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PlusAppsView(
    plusApps: List<InstalledApps.Entry>,
    title: String,
    onLaunchApp: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().background(Nova.Canvas).verticalScroll(rememberScrollState())) {
        Row(
            Modifier.fillMaxWidth().padding(top = 44.dp, start = 8.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(Modifier.clickable { onBack() }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Zurück", tint = Nova.Primary, modifier = Modifier.size(24.dp))
                Text("Zurück", color = Nova.Primary, fontSize = 17.sp)
            }
        }
        Text(
            title, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Nova.Ink,
            modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
        )
        Text(
            "Tippe eine App an, um sie zu öffnen.",
            fontSize = 14.sp, color = Nova.InkMuted, modifier = Modifier.padding(start = 16.dp, bottom = 12.dp)
        )

        if (plusApps.isEmpty()) {
            Text(
                "Es sind keine Apps freigegeben. Im Eltern-Portal können Apps auf Plus gestellt " +
                    "oder für den Fokus freigegeben werden.",
                fontSize = 14.sp, color = Nova.InkMuted, modifier = Modifier.padding(horizontal = 16.dp)
            )
        } else {
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
                                    Modifier.size(60.dp).clip(RoundedCornerShape(15.dp)).background(Color(0x11000000)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (bmp != null) {
                                        Image(bitmap = bmp.asImageBitmap(), contentDescription = app.label, modifier = Modifier.size(54.dp))
                                    } else {
                                        Text(app.label.take(1), fontSize = 24.sp, color = Nova.Ink)
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(app.label, fontSize = 11.sp, color = Nova.Ink, maxLines = 1, textAlign = TextAlign.Center)
                            }
                        }
                        repeat(4 - rowApps.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
