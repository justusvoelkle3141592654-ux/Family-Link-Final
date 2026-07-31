package com.familylink.ios.ui.screens

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Timer
import androidx.compose.runtime.Composable
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
import com.familylink.ios.data.LimitScope
import com.familylink.ios.data.Prefs
import com.familylink.ios.ui.theme.Nova
import com.familylink.ios.util.TimeFmt

/**
 * A deliberate 1:1 copy of Google Family Link's "Time limits" screen — same two cards, same
 * icons-on-a-pale-blue-disc, same blue switch, same row order. The parent asked for the exact
 * reference design, not our own interpretation of it, so this does not reuse [Nova.Success]
 * (green switches elsewhere in the app) or [com.familylink.ios.ui.components.NovaSwitch] — the
 * reference switch is blue, and only this screen gets it.
 */
@Composable
fun TimeLimitsScreen(
    prefs: Prefs,
    onBack: () -> Unit,
    onOpenApps: () -> Unit,
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    var editDaily by remember { mutableStateOf(false) }
    var editWeekly by remember { mutableStateOf(false) }

    val dailyOn = prefs.limitScope != LimitScope.WEEK
    val childLabel = prefs.childName.ifBlank { "dein Kind" }

    Column(Modifier.fillMaxSize().background(Nova.Canvas)) {
        // ---- top bar: back arrow + centred title, exactly as in the reference ----
        Box(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
            Box(
                Modifier.align(Alignment.CenterStart).padding(start = 8.dp)
                    .size(40.dp).clip(CircleShape).clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück", tint = Nova.Ink, modifier = Modifier.size(22.dp))
            }
            Text(
                "Zeitlimits", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Nova.Ink,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // ---- card 1: daily limit ----
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Nova.Surface)) {
                Column {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).background(Nova.SurfaceAlt),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Timer, null, tint = Nova.Primary, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.weight(1f))
                            BlueSwitch(checked = dailyOn) { on ->
                                // The scope picker only ever knows DAY / WEEK / BOTH — there is no
                                // "neither". Turning the daily side off always falls back to WEEK,
                                // turning it on keeps whatever weekly pot was already set instead
                                // of discarding it.
                                prefs.limitScope = if (on) {
                                    if (prefs.limitScope == LimitScope.WEEK) LimitScope.BOTH else prefs.limitScope
                                } else LimitScope.WEEK
                                onChanged()
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text("Tageslimit", fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Nova.Ink)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "Lege die Zeit fest, die $childLabel insgesamt auf seinen Geräten " +
                                "verbringen darf.",
                            fontSize = 14.sp, color = Nova.InkMuted, lineHeight = 19.sp
                        )
                    }
                    HairlineDivider()
                    Column(
                        Modifier.fillMaxWidth()
                            .clickable(enabled = dailyOn) { editDaily = !editDaily }
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Heute", fontSize = 15.sp, color = Nova.Ink, modifier = Modifier.weight(1f))
                            Text(
                                if (!dailyOn) "Kein Limit" else TimeFmt.hm(prefs.globalLimitMinutes * 60),
                                fontSize = 15.sp, color = Nova.InkMuted
                            )
                        }
                        if (editDaily && dailyOn) {
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                LimitStepper(
                                    value = TimeFmt.hm(prefs.globalLimitMinutes * 60),
                                    onMinus = {
                                        prefs.globalLimitMinutes = (prefs.globalLimitMinutes - 15).coerceAtLeast(0)
                                        onChanged()
                                    },
                                    onPlus = {
                                        prefs.globalLimitMinutes =
                                            (prefs.globalLimitMinutes + 15).coerceAtMost(Prefs.MAX_GLOBAL_LIMIT_MIN)
                                        onChanged()
                                    }
                                )
                            }
                        }
                    }
                    HairlineDivider()
                    Column(
                        Modifier.fillMaxWidth().clickable { editWeekly = !editWeekly }
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Wochenplan", fontSize = 15.sp, color = Nova.Ink)
                                Text(
                                    "Ein Topf für die ganze Woche", fontSize = 13.sp, color = Nova.InkMuted
                                )
                            }
                            Icon(Icons.Filled.ChevronRight, null, tint = Nova.InkFaint, modifier = Modifier.size(20.dp))
                        }
                        if (editWeekly) {
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                LimitStepper(
                                    value = TimeFmt.hm(prefs.weeklyLimitMinutes * 60),
                                    onMinus = { prefs.weeklyLimitMinutes = prefs.weeklyLimitMinutes - 30; onChanged() },
                                    onPlus = { prefs.weeklyLimitMinutes = prefs.weeklyLimitMinutes + 30; onChanged() }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ---- card 2: app limits ----
            val categories = prefs.getCategories()
            val limitedPkgs = categories.filterValues { it.first == AppCategory.LIMIT }.keys.toList()
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Nova.Surface)
                    .clickable { onOpenApps() }
            ) {
                Column {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).background(Nova.SurfaceAlt),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Apps, null, tint = Nova.Primary, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Filled.ChevronRight, null, tint = Nova.InkFaint, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.height(14.dp))
                        Text("App-Limits", fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Nova.Ink)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "Sperren, Zeitlimits setzen oder unbegrenzte Zeit für einzelne Apps " +
                                "festlegen.",
                            fontSize = 14.sp, color = Nova.InkMuted, lineHeight = 19.sp
                        )
                    }
                    HairlineDivider()
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Mit Limit", fontSize = 15.sp, color = Nova.Ink, modifier = Modifier.weight(1f))
                        val firstLimitedPkg = limitedPkgs.firstOrNull()
                        val firstIcon = remember(firstLimitedPkg) {
                            firstLimitedPkg?.let { InstalledApps.iconBitmap(context, it) }
                        }
                        if (firstIcon != null) {
                            Image(
                                bitmap = firstIcon.asImageBitmap(), contentDescription = null,
                                modifier = Modifier.size(28.dp).clip(RoundedCornerShape(7.dp))
                            )
                        } else {
                            Text("Keine", fontSize = 14.sp, color = Nova.InkFaint)
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

/** Hairline divider, edge to edge like the reference's row separators. */
@Composable
private fun HairlineDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Nova.Line))
}

/**
 * The reference's switch is Google blue, not our brand green — kept local to this screen on
 * purpose so the rest of the app's switches are unaffected.
 */
@Composable
private fun BlueSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val bg = if (checked) Nova.Primary else Color(0xFFD9D9E3)
    val offset by animateFloatAsState(if (checked) 22f else 2f, label = "blueSwitch")
    Box(
        Modifier.width(51.dp).height(31.dp).clip(RoundedCornerShape(16.dp))
            .background(bg).clickable { onCheckedChange(!checked) }
    ) {
        Box(
            Modifier.padding(vertical = 2.dp).size(27.dp).offset(x = offset.dp)
                .clip(CircleShape).background(Color.White)
        )
    }
}

@Composable
private fun LimitStepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepButton("–", onMinus)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Nova.Ink,
            modifier = Modifier.padding(horizontal = 12.dp))
        StepButton("+", onPlus)
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(32.dp).clip(CircleShape).background(Nova.SurfaceAlt).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 18.sp, color = Nova.Primary)
    }
}
