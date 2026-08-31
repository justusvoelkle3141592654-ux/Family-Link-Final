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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familylink.ios.data.AppCategory
import com.familylink.ios.data.InstalledApps
import com.familylink.ios.data.Prefs
import com.familylink.ios.ui.theme.Nova
import com.familylink.ios.util.TimeFmt

/**
 * Lets the parent assign each installed app to a category:
 *  Plus (always allowed) · Limit (own daily limit) · Standard (shared global budget).
 * Tapping the trailing chip cycles Standard -> Plus -> Limit -> Standard.
 */
@Composable
fun AppsScreen() {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }

    val sync = remember { com.familylink.ios.sync.SyncManager(context) }
    // local mirror so the UI updates immediately; persisted on each change
    var version by remember { mutableStateOf(0) }
    var pulled by remember { mutableStateOf(0) }

    // Pull the child's app list once when this screen opens, so a parent that just installed
    // the app does not have to wait for the portal's slow poll.
    LaunchedEffect(Unit) {
        if (prefs.isParentDevice && prefs.syncConfigured) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { sync.fetchChildApps() }
            }
            pulled++
        }
    }

    // On the parent device we manage the CHILD's apps, not the ones installed on the parent
    // phone. Preferred source is the child's full launchable app list; the apps it merely used
    // today are the fallback, and only if neither arrived do we fall back to local apps.
    val remoteApps = remember(pulled, version) {
        if (!prefs.isParentDevice) emptyList()
        else sync.cachedChildApps()
            .map { InstalledApps.Entry(it.pkg, it.label) }
            .ifEmpty {
                sync.cachedChildStatus()?.perAppLabels
                    ?.map { (pkg, label) -> InstalledApps.Entry(pkg, label) }
                    .orEmpty()
            }
            .sortedBy { it.label.lowercase() }
    }
    val apps = remember(remoteApps) {
        if (remoteApps.isNotEmpty()) remoteApps else InstalledApps.load(context)
    }
    val managingRemote = remoteApps.isNotEmpty()

    // Usage must come from the child too — reading the parent phone's own numbers here made
    // every app show "Heute noch nicht genutzt".
    val perApp = remember(version, pulled) {
        if (prefs.isParentDevice) sync.cachedChildStatus()?.perAppSeconds.orEmpty()
        else prefs.getPerAppSeconds()
    }

    Column(
        Modifier.fillMaxSize()
            .background(Nova.Canvas)
    ) {
        Text(
            "Apps verwalten",
            fontSize = 30.sp, fontWeight = FontWeight.Normal, color = Nova.Ink,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp)
        )
        Text(
            if (managingRemote) "Apps des Kinder-Geräts · tippe die Markierung zum Wechseln."
            else "Tippe auf die Markierung, um die Kategorie zu wechseln.",
            fontSize = 13.sp, color = Nova.InkMuted,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        if (prefs.isParentDevice && !managingRemote) {
            com.familylink.ios.ui.components.NovaNote(
                "Noch keine App-Daten vom Kinder-Gerät empfangen. Sobald es verbunden ist und " +
                    "Apps genutzt wurden, erscheinen sie hier.",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        Legend()
        // Keyboards are listed but cannot be limited; the only real lever is switching one off
        // in Android's own settings, which the PIN gate then keeps switched off.
        if (!prefs.isParentDevice && apps.any { it.isKeyboard }) {
            com.familylink.ios.ui.components.NovaNote(
                "Tastaturen stehen mit in der Liste, lassen sich aber nicht zeitlich begrenzen — " +
                    "Android misst ihnen keine Nutzungszeit zu. Abschalten geht nur in den " +
                    "Tastatur-Einstellungen; die PIN-Sperre verhindert danach das Wiedereinschalten.",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Tastatur-Einstellungen öffnen",
                fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Nova.Primary,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .clickable {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
            )
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            items(apps, key = { it.packageName }) { app ->
                @Suppress("UNUSED_EXPRESSION") version
                val cat = prefs.categoryOf(app.packageName)
                val limit = prefs.limitMinutesOf(app.packageName)
                val used = perApp[app.packageName] ?: 0
                val icon = remember(app.packageName) { InstalledApps.iconBitmap(context, app.packageName) }

                Box(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Nova.Surface)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // App icon
                        Box(
                            Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(Nova.Fill),
                            contentAlignment = Alignment.Center
                        ) {
                            if (icon != null) {
                                Image(
                                    bitmap = icon.asImageBitmap(), contentDescription = null,
                                    modifier = Modifier.size(38.dp)
                                )
                            } else {
                                Text(app.label.take(1), fontSize = 18.sp, color = Nova.Ink)
                            }
                        }
                        Spacer(Modifier.width(12.dp))

                        Column(Modifier.weight(1f)) {
                            Text(app.label, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Nova.Ink)
                            val sub = when {
                                // A keyboard never runs as a foreground app, so there is no time
                                // to show and no limit to set — say that instead of "0 Min".
                                app.isKeyboard -> "Tastatur · Zeit nicht messbar"
                                cat == AppCategory.LIMIT -> "Limit $limit Min · heute ${TimeFmt.hm(used)}"
                                used > 0 -> "Heute ${TimeFmt.hm(used)}"
                                else -> "Heute noch nicht genutzt"
                            }
                            Text(sub, fontSize = 12.sp, color = Nova.InkMuted)
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            CategoryChip(cat) {
                                val next = when (cat) {
                                    AppCategory.STANDARD -> AppCategory.PLUS
                                    AppCategory.PLUS -> AppCategory.LIMIT
                                    AppCategory.LIMIT -> AppCategory.BLOCKED
                                    AppCategory.BLOCKED -> AppCategory.STANDARD
                                }
                                prefs.setCategory(app.packageName, next, limit)
                                version++
                                com.familylink.ios.sync.SyncService.pushNow(context)
                            }
                            if (cat == AppCategory.LIMIT) {
                                Spacer(Modifier.height(6.dp))
                                StepperMinutes(limit) { newVal ->
                                    prefs.setCategory(app.packageName, AppCategory.LIMIT, newVal)
                                    version++
                                    com.familylink.ios.sync.SyncService.pushNow(context)
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(90.dp)) }
        }
    }
}

/** Compact colour legend for the four categories. */
@Composable
private fun Legend() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        LegendDot("Plus", Nova.Success)
        LegendDot("Limit", Nova.Warning)
        LegendDot("Standard", Nova.Primary)
        LegendDot("Gesperrt", Nova.Danger)
    }
}

@Composable
private fun LegendDot(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(text, fontSize = 11.sp, color = Nova.InkMuted)
    }
}

@Composable
private fun CategoryChip(cat: AppCategory, onClick: () -> Unit) {
    val (label, color) = when (cat) {
        AppCategory.PLUS -> "Plus" to Nova.Success
        AppCategory.LIMIT -> "Limit" to Nova.Warning
        AppCategory.STANDARD -> "Standard" to Nova.Primary
        AppCategory.BLOCKED -> "Gesperrt" to Nova.Danger
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.15f))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(label, color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StepperMinutes(minutes: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.height(0.dp))
        StepBtn("−") { onChange((minutes - 5).coerceAtLeast(5)) }
        Text("$minutes", modifier = Modifier.padding(horizontal = 6.dp), fontSize = 15.sp, color = Nova.Ink)
        StepBtn("+") { onChange((minutes + 5).coerceAtMost(240)) }
    }
}

@Composable
private fun StepBtn(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(start = 6.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Nova.Fill)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(label, fontSize = 18.sp, color = Nova.Primary)
    }
}
