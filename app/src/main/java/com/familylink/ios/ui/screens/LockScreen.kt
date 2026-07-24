package com.familylink.ios.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familylink.ios.data.LockDecision
import com.familylink.ios.util.TimeFmt
import kotlinx.coroutines.delay

/**
 * Full-screen iOS-style lock overlay. Shows the live clock, the used/limit time and an
 * emergency phone button so the child can always place a call.
 */
@Composable
fun LockScreen(decision: LockDecision) {
    val context = LocalContext.current
    var clock by remember { mutableStateOf(TimeFmt.now()) }

    LaunchedEffect(Unit) {
        while (true) {
            clock = TimeFmt.now()
            delay(1000)
        }
    }

    val (headline, detail) = when (decision) {
        is LockDecision.Bedtime -> "Ruhezeit" to "Das Gerät ist jetzt gesperrt. Gute Nacht!"
        is LockDecision.GlobalLimitReached ->
            "Zeit ist um" to "Genutzt: ${TimeFmt.hm(decision.usedSeconds)} von ${TimeFmt.hm(decision.limitSeconds)}"
        is LockDecision.AppLimitReached ->
            "App-Limit erreicht" to "Genutzt: ${TimeFmt.hm(decision.usedSeconds)} von ${TimeFmt.hm(decision.limitSeconds)}"
        LockDecision.Allowed -> "" to ""
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            Modifier.fillMaxSize().padding(top = 96.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(18.dp))

            Text(TimeFmt.nowLong(), color = Color(0xB3FFFFFF), fontSize = 17.sp)
            Text(
                clock,
                color = Color.White,
                fontSize = 82.sp,
                fontWeight = FontWeight.Thin
            )

            Spacer(Modifier.height(40.dp))

            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0x1FFFFFFF))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(headline, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text(detail, color = Color(0xCCFFFFFF), fontSize = 16.sp)
                }
            }
        }

        // Emergency / phone button pinned to the bottom, iOS-lock-screen style.
        Column(
            Modifier.fillMaxSize().padding(bottom = 64.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color(0x33FFFFFF))
                    .border(1.dp, Color(0x66FFFFFF), CircleShape)
                    .clickable {
                        // Open the emergency dialer; the dialer is exempt from locking.
                        val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        runCatching { context.startActivity(dial) }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Phone, contentDescription = "Anrufen", tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text("Notruf / Anrufen", color = Color(0xB3FFFFFF), fontSize = 14.sp)
        }
    }
}
