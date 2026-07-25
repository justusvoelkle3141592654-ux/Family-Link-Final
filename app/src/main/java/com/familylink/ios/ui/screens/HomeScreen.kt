package com.familylink.ios.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familylink.ios.data.Prefs
import com.familylink.ios.ui.theme.Nova
import com.familylink.ios.util.TimeFmt
import kotlinx.coroutines.delay

/**
 * Modernised child-facing status screen: a soft gradient, a circular time ring, and quick
 * actions (parent-protected time extension + discreet parent portal entrance).
 */
@Composable
fun HomeScreen(onOpenParentPortal: () -> Unit, onExtendTime: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    var tick by remember { mutableStateOf(0) }
    LaunchedEffectTicker { tick++ }
    @Suppress("UNUSED_EXPRESSION") tick

    val used = prefs.globalUsedSeconds
    val bonus = prefs.bonusSecondsToday
    val limit = prefs.globalLimitMinutes * 60 + bonus
    val remaining = (limit - used).coerceAtLeast(0)
    val fraction = if (limit == 0) 1f else (used.toFloat() / limit).coerceIn(0f, 1f)
    val disabled = prefs.limitsDisabled()
    val bedtime = prefs.isBedtime()

    val ringColor = when {
        disabled -> Nova.Success
        bedtime -> Nova.Night
        fraction >= 1f -> Nova.Danger
        fraction >= 0.8f -> Nova.Warning
        else -> Nova.Primary
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Nova.Canvas))
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Family Link", fontSize = 15.sp, color = Nova.InkMuted)
        Text(TimeFmt.nowLong(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Nova.Ink)

        Spacer(Modifier.height(36.dp))

        // Time ring
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(240.dp)) {
                val stroke = 22.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val topLeft = Offset(inset, inset)
                drawArc(
                    color = Color(0x14000000),
                    startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round)
                )
                drawArc(
                    color = ringColor,
                    startAngle = -90f, sweepAngle = 360f * (1f - fraction), useCenter = false,
                    topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                when {
                    disabled -> {
                        Text("Frei", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Nova.Success)
                        Text("bis 23:00 Uhr", fontSize = 14.sp, color = Nova.InkMuted)
                    }
                    bedtime -> Text("Ruhezeit", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Nova.Night)
                    else -> {
                        Text(TimeFmt.hm(remaining), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Nova.Ink)
                        Text("übrig heute", fontSize = 14.sp, color = Nova.InkMuted)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "Genutzt: ${TimeFmt.hm(used)} von ${TimeFmt.hm(limit)}" +
                if (bonus > 0) "  (+${bonus / 60} Min Bonus)" else "",
            fontSize = 14.sp, color = Nova.InkMuted
        )

        Spacer(Modifier.weight(1f))

        // Actions
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(Nova.Primary).clickable { onExtendTime() }.padding(vertical = 15.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Zeit verlängern (Eltern)", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(
            "Eltern-Portal öffnen",
            fontSize = 15.sp, color = Nova.Primary,
            modifier = Modifier.clickable { onOpenParentPortal() }.padding(16.dp)
        )
    }
}

/** 1-second ticker to keep the screen live without importing LaunchedEffect at each call site. */
@Composable
private fun LaunchedEffectTicker(onTick: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) { delay(1000); onTick() }
    }
}
