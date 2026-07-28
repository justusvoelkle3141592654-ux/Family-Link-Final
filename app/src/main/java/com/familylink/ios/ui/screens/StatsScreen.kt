package com.familylink.ios.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familylink.ios.data.Prefs
import com.familylink.ios.ui.components.NovaButtonTonal
import com.familylink.ios.ui.components.NovaCard
import com.familylink.ios.ui.components.NovaPill
import com.familylink.ios.ui.components.NovaProgress
import com.familylink.ios.ui.theme.Nova
import com.familylink.ios.util.TimeFmt

/**
 * Weekly report: a 7-day bar chart of daily usage against the limit, plus the week's totals
 * and the apps that dominated it. History is recorded locally each day by [Prefs].
 */
@Composable
fun StatsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val history = remember { prefs.getWeekHistory() }
    val limit = prefs.globalLimitMinutes * 60

    val total = history.sumOf { it.second }
    val avg = if (history.isNotEmpty()) total / history.size else 0
    val overDays = history.count { it.second > limit && limit > 0 }
    val maxVal = (history.maxOfOrNull { it.second } ?: 1).coerceAtLeast(limit).coerceAtLeast(1)

    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(Nova.PageGradient))
            .verticalScroll(rememberScrollState()).padding(20.dp)
    ) {
        Text("Wochenbericht", fontSize = 26.sp, fontWeight = FontWeight.Normal, color = Nova.Ink)
        Text("Die letzten 7 Tage", fontSize = 13.sp, color = Nova.InkMuted)
        Spacer(Modifier.height(20.dp))

        // --- summary tiles ---
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Gesamt", TimeFmt.hm(total), Nova.Primary, Modifier.weight(1f))
            StatTile("Ø pro Tag", TimeFmt.hm(avg), Nova.Focus, Modifier.weight(1f))
            StatTile("Über Limit", "$overDays Tage", if (overDays > 0) Nova.Warning else Nova.Success, Modifier.weight(1f))
        }

        Spacer(Modifier.height(20.dp))

        // --- bar chart ---
        NovaCard {
            Column(Modifier.padding(16.dp)) {
                Text("Tägliche Nutzung", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Nova.Ink)
                Spacer(Modifier.height(16.dp))
                if (history.isEmpty()) {
                    Text("Noch keine Daten — die Historie beginnt heute.",
                        fontSize = 13.sp, color = Nova.InkMuted)
                } else {
                    Row(
                        Modifier.fillMaxWidth().height(140.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        history.forEach { (day, secs) ->
                            val frac = secs.toFloat() / maxVal
                            val over = limit > 0 && secs > limit
                            Column(
                                Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    if (secs > 0) "${secs / 60}" else "",
                                    fontSize = 10.sp, color = Nova.InkFaint
                                )
                                Spacer(Modifier.height(4.dp))
                                Box(
                                    Modifier.fillMaxWidth()
                                        .height((110 * frac).dp.coerceAtLeast(4.dp))
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (over) Nova.Warning else Nova.Primary)
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(day, fontSize = 11.sp, color = Nova.InkMuted)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // --- today's top apps ---
        val today = prefs.getPerAppSeconds()
            .filterKeys { it != "com.familylink.ios" }
            .entries.sortedByDescending { it.value }.take(5)
        if (today.isNotEmpty()) {
            Text("Top-Apps heute", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Nova.InkMuted)
            Spacer(Modifier.height(8.dp))
            NovaCard {
                val top = today.first().value.coerceAtLeast(1)
                Column(Modifier.padding(16.dp)) {
                    today.forEach { (pkg, secs) ->
                        Row(verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 6.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    com.familylink.ios.data.InstalledApps.labelFor(context, pkg),
                                    fontSize = 14.sp, color = Nova.Ink
                                )
                                Spacer(Modifier.height(5.dp))
                                NovaProgress(secs.toFloat() / top, Nova.Primary, barHeight = 5)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(TimeFmt.hm(secs), fontSize = 13.sp, color = Nova.InkMuted)
                        }
                    }
                }
            }
        }

        // --- blocked today ---
        val blocked = prefs.getBlockedToday()
        if (blocked.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text("Heute gesperrt", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Nova.InkMuted)
            Spacer(Modifier.height(8.dp))
            NovaCard {
                Column(Modifier.padding(16.dp)) {
                    blocked.keys.take(8).forEach { pkg ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                com.familylink.ios.data.InstalledApps.labelFor(context, pkg),
                                fontSize = 14.sp, color = Nova.Ink, modifier = Modifier.weight(1f)
                            )
                            NovaPill("Gesperrt", Nova.Danger)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        NovaButtonTonal(text = "Zurück", onClick = onBack)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatTile(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(Nova.RadiusCard.dp)).background(color.copy(alpha = 0.12f))
            .padding(vertical = 16.dp, horizontal = 10.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(value, fontSize = 17.sp, fontWeight = FontWeight.Normal, color = color)
            Spacer(Modifier.height(2.dp))
            Text(label, fontSize = 11.sp, color = Nova.InkMuted)
        }
    }
}
