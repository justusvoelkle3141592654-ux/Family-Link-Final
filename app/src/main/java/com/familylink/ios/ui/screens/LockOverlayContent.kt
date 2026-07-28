package com.familylink.ios.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
 *
 * Laid out like the rest of the app now: a flat page, one white card carrying the reason, and
 * Material 3 proportions throughout.
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

        val accent = if (bedtime) Nova.Night else Nova.Primary

        Column(
            Modifier.fillMaxSize().background(Nova.Canvas).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(72.dp))

            // ---- the clock, as the page's own headline ----
            Text(TimeFmt.nowLong(), fontSize = 15.sp, color = Nova.InkMuted)
            Spacer(Modifier.height(2.dp))
            Text(clock, fontSize = 76.sp, fontWeight = FontWeight.Light, color = Nova.Ink)

            Spacer(Modifier.height(28.dp))

            // ---- the reason, in the same card style as everywhere else ----
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(Nova.RadiusCard.dp))
                    .background(Nova.Surface)
                    .padding(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()) {
                    Box(
                        Modifier.size(56.dp).clip(CircleShape)
                            .background(accent.copy(alpha = 0.13f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Lock, null, tint = accent, modifier = Modifier.size(26.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        title, fontSize = 22.sp, fontWeight = FontWeight.Medium,
                        color = Nova.Ink, textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        detail, fontSize = 15.sp, color = Nova.InkMuted,
                        textAlign = TextAlign.Center, lineHeight = 21.sp
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // ---- the phone, as a filled tonal button rather than a bare disc ----
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Nova.SurfaceAlt)
                    .clickable {
                        val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(dial) }
                    }
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Phone, null, tint = Nova.Primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Telefon", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Nova.Primary)
            }

            Spacer(Modifier.height(20.dp))
            if (bedtime) {
                Text("Gute Nacht", fontSize = 14.sp, color = Nova.InkFaint)
                Spacer(Modifier.height(8.dp))
            }
            Text(
                "Eltern-Portal", fontSize = 14.sp, color = Nova.InkMuted,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { onOpenPortal() }
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            )
            Spacer(Modifier.height(28.dp))
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
