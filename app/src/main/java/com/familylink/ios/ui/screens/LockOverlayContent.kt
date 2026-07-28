package com.familylink.ios.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familylink.ios.MainActivity
import com.familylink.ios.ui.theme.FamilyLinkTheme
import com.familylink.ios.ui.theme.Nova
import com.familylink.ios.ui.theme.ThemeMode
import com.familylink.ios.util.TimeFmt
import kotlinx.coroutines.delay

/**
 * What the system overlay draws.
 *
 * Deliberately has no close button and no route to the home screen — the whole point is that
 * this cannot be dismissed. Two things stay reachable, because locking a child out of them
 * would be irresponsible: the phone (and with it the emergency dialler) and the PIN-protected
 * parent entry.
 */
@Composable
fun LockOverlayContent(
    title: String,
    detail: String,
    bedtime: Boolean,
    onOpenPortal: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { com.familylink.ios.data.Prefs.get(context) }
    val dark = when (prefs.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }

    FamilyLinkTheme(dark = dark) {
        var clock by remember { mutableStateOf(TimeFmt.now()) }
        LaunchedEffect(Unit) { while (true) { clock = TimeFmt.now(); delay(1000) } }

        Column(
            Modifier.fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        if (bedtime) listOf(Color(0xFFEDE9FB), Nova.Canvas) else Nova.PageGradient
                    )
                )
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(64.dp))
            val accent = if (bedtime) Nova.Night else Nova.Primary
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Lock, null, tint = accent, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(TimeFmt.nowLong(), fontSize = 16.sp, color = Nova.InkMuted)
            Text(clock, fontSize = 72.sp, fontWeight = FontWeight.Thin, color = Nova.Ink)
            Spacer(Modifier.height(16.dp))
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Nova.Ink,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(detail, fontSize = 16.sp, color = Nova.InkMuted, textAlign = TextAlign.Center)

            Spacer(Modifier.weight(1f))

            // The phone is never taken away.
            Box(
                Modifier.size(60.dp).clip(CircleShape).background(Color(0x1A34C759))
                    .clickable {
                        val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(dial) }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Phone, "Telefon", tint = Nova.Success, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text("Telefon", fontSize = 13.sp, color = Nova.InkMuted)

            Spacer(Modifier.height(18.dp))
            if (bedtime) {
                Text("Gute Nacht", fontSize = 14.sp, color = Nova.InkFaint)
                Spacer(Modifier.height(6.dp))
            }
            Text(
                "Eltern-Portal", fontSize = 15.sp, color = Nova.Primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onOpenPortal() }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}

/** Open the parent portal from the overlay. */
fun openParentPortal(context: Context) {
    runCatching {
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
    }
}
