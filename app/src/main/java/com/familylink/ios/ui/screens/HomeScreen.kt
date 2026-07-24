package com.familylink.ios.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familylink.ios.data.Prefs
import com.familylink.ios.ui.cupertino.CupertinoButton
import com.familylink.ios.ui.theme.Cupertino
import com.familylink.ios.util.TimeFmt
import kotlinx.coroutines.delay

/**
 * Child-facing status screen: how much time is left today, plus a discreet entrance to the
 * (PIN- and weekly-gated) parent portal.
 */
@Composable
fun HomeScreen(onOpenParentPortal: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    var tick by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) { tick++; delay(1000) }
    }
    @Suppress("UNUSED_EXPRESSION") tick

    val used = prefs.globalUsedSeconds
    val limit = prefs.globalLimitMinutes * 60
    val remaining = (limit - used).coerceAtLeast(0)
    val fraction = if (limit == 0) 1f else (used.toFloat() / limit).coerceIn(0f, 1f)
    val disabled = prefs.limitsDisabled()
    val bedtime = prefs.isBedtime()

    Column(
        Modifier.fillMaxSize().background(Cupertino.SystemBackground).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Family Link", fontSize = 17.sp, color = Cupertino.SecondaryLabel)
        Text(TimeFmt.nowLong(), fontSize = 14.sp, color = Cupertino.TertiaryLabel)

        Spacer(Modifier.height(48.dp))

        // big remaining-time ring-ish tile
        Box(
            Modifier
                .size(220.dp)
                .clip(CircleShape)
                .background(
                    when {
                        disabled -> Cupertino.Green.copy(alpha = 0.15f)
                        bedtime -> Cupertino.Purple.copy(alpha = 0.15f)
                        fraction >= 1f -> Cupertino.Red.copy(alpha = 0.15f)
                        else -> Cupertino.Blue.copy(alpha = 0.12f)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                when {
                    disabled -> {
                        Text("Frei", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Cupertino.Green)
                        Text("bis 23:00 Uhr", fontSize = 15.sp, color = Cupertino.SecondaryLabel)
                    }
                    bedtime -> {
                        Text("Ruhezeit", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Cupertino.Purple)
                    }
                    else -> {
                        Text(TimeFmt.hm(remaining), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Cupertino.Label)
                        Text("übrig heute", fontSize = 15.sp, color = Cupertino.SecondaryLabel)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "Genutzt: ${TimeFmt.hm(used)} von ${TimeFmt.hm(limit)}",
            fontSize = 15.sp, color = Cupertino.SecondaryLabel
        )

        Spacer(Modifier.fillMaxWidth().weight(1f))

        // discreet parent entrance
        Text(
            "Eltern-Portal öffnen",
            fontSize = 15.sp,
            color = Cupertino.Blue,
            modifier = Modifier.clickable { onOpenParentPortal() }.padding(16.dp)
        )
    }
}
