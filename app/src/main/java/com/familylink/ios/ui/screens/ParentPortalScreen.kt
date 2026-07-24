package com.familylink.ios.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
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
import com.familylink.ios.data.Prefs
import com.familylink.ios.ui.cupertino.CupertinoButton
import com.familylink.ios.ui.cupertino.CupertinoCard
import com.familylink.ios.ui.cupertino.CupertinoRow
import com.familylink.ios.ui.cupertino.CupertinoSwitch
import com.familylink.ios.ui.cupertino.SectionHeader
import com.familylink.ios.ui.theme.Cupertino
import com.familylink.ios.util.TimeFmt

@Composable
fun ParentPortalScreen(
    onOpenApps: () -> Unit,
    onOpenPermissions: () -> Unit,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    var v by remember { mutableStateOf(0) }
    @Suppress("UNUSED_EXPRESSION") v

    val used = prefs.globalUsedSeconds
    val limit = prefs.globalLimitMinutes * 60

    Column(
        Modifier
            .fillMaxSize()
            .background(Cupertino.SystemBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Eltern-Portal", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Cupertino.Label)
            Spacer(Modifier.weight(1f))
            Text("Fertig", color = Cupertino.Blue, fontSize = 17.sp, modifier = Modifier.clickable { onExit() })
        }

        // ---- usage summary ----
        SectionHeader("Heute genutzt")
        CupertinoCard {
            Column(Modifier.padding(16.dp)) {
                Text(TimeFmt.hm(used), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Cupertino.Label)
                Text("von ${TimeFmt.hm(limit)} Tageslimit", fontSize = 14.sp, color = Cupertino.SecondaryLabel)
                Spacer(Modifier.height(12.dp))
                ProgressBar(fraction = if (limit == 0) 1f else (used.toFloat() / limit).coerceIn(0f, 1f))
            }
        }

        // ---- global limit ----
        SectionHeader("Tägliches Limit (Standard-Apps)")
        CupertinoCard {
            CupertinoRow(title = "Limit", subtitle = "Standard 1 Std · max. 2 Std") {
                Stepper(
                    value = "${prefs.globalLimitMinutes} Min",
                    onMinus = { prefs.globalLimitMinutes = (prefs.globalLimitMinutes - 15).coerceAtLeast(0); v++ },
                    onPlus = { prefs.globalLimitMinutes = (prefs.globalLimitMinutes + 15).coerceAtMost(Prefs.MAX_GLOBAL_LIMIT_MIN); v++ }
                )
            }
        }

        // ---- bedtime ----
        SectionHeader("Ruhezeit")
        CupertinoCard {
            CupertinoRow(title = "Ruhezeit aktiv") {
                CupertinoSwitch(checked = prefs.bedtimeEnabled) { prefs.bedtimeEnabled = it; v++ }
            }
            CupertinoRow(title = "Beginn") {
                Stepper(
                    value = TimeFmt.clock(prefs.bedtimeStartMin),
                    onMinus = { prefs.bedtimeStartMin = wrap(prefs.bedtimeStartMin - 30); v++ },
                    onPlus = { prefs.bedtimeStartMin = wrap(prefs.bedtimeStartMin + 30); v++ }
                )
            }
            CupertinoRow(title = "Ende") {
                Stepper(
                    value = TimeFmt.clock(prefs.bedtimeEndMin),
                    onMinus = { prefs.bedtimeEndMin = wrap(prefs.bedtimeEndMin - 30); v++ },
                    onPlus = { prefs.bedtimeEndMin = wrap(prefs.bedtimeEndMin + 30); v++ }
                )
            }
        }

        // ---- Aus-Button ----
        SectionHeader("Für heute freischalten")
        CupertinoCard {
            Column(Modifier.padding(16.dp)) {
                val active = prefs.limitsDisabled()
                Text(
                    if (active) "Alle Limits sind bis 23:00 Uhr deaktiviert."
                    else "Deaktiviert alle Limits bis 23:00 Uhr des heutigen Tages.",
                    fontSize = 14.sp, color = Cupertino.SecondaryLabel
                )
                Spacer(Modifier.height(12.dp))
                if (active) {
                    CupertinoButton(text = "Limits wieder aktivieren", color = Cupertino.Red) {
                        prefs.clearOffButton(); v++
                    }
                } else {
                    CupertinoButton(text = "Aus-Button – bis 23:00 freischalten", color = Cupertino.Orange) {
                        prefs.activateOffButton(); v++
                    }
                }
            }
        }

        // ---- navigation ----
        SectionHeader("Verwaltung")
        CupertinoCard {
            CupertinoRow(title = "Apps & Kategorien", onClick = onOpenApps) { Chevron() }
            CupertinoRow(title = "Berechtigungen", onClick = onOpenPermissions) { Chevron() }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "Das Eltern-Portal ist nur einmal pro Woche ohne Aus-Button erreichbar.",
            fontSize = 12.sp, color = Cupertino.TertiaryLabel
        )
        Spacer(Modifier.height(24.dp))
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
                .background(if (fraction >= 1f) Cupertino.Red else Cupertino.Green)
        )
    }
}

@Composable
private fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepBtn("−", onMinus)
        Text(value, modifier = Modifier.padding(horizontal = 12.dp), fontSize = 17.sp, color = Cupertino.Label)
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
        Text(label, fontSize = 20.sp, color = Cupertino.Blue)
    }
}

@Composable
private fun Chevron() {
    Text("›", color = Cupertino.TertiaryLabel, fontSize = 22.sp)
}
