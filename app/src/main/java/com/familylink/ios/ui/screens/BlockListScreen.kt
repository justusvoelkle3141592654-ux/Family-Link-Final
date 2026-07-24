package com.familylink.ios.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.familylink.ios.data.AppCategory
import com.familylink.ios.data.InstalledApps
import com.familylink.ios.data.Prefs
import com.familylink.ios.ui.theme.Cupertino
import com.familylink.ios.util.TimeFmt
import kotlinx.coroutines.delay

/**
 * The block screen (Listen-Ansicht). Shown as a normal, leavable screen — NOT a full-screen
 * lock — when the child opens a blocked app. It lists all managed apps: blocked ones are
 * greyed out with a lock icon, PLUS apps stay highlighted as still available.
 */
@Composable
fun BlockListScreen(reasonTitle: String, reasonDetail: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val apps = remember { InstalledApps.load(context) }
    var clock by remember { mutableStateOf(TimeFmt.now()) }
    LaunchedEffect(Unit) { while (true) { clock = TimeFmt.now(); delay(1000) } }

    val perApp = prefs.getPerAppSeconds()
    val bedtime = prefs.isBedtime()

    Column(
        Modifier
            .fillMaxSize()
            .background(Cupertino.SystemBackground)
            .verticalScroll(rememberScrollState())
    ) {
        // header
        Column(
            Modifier.fillMaxWidth().padding(top = 48.dp, start = 24.dp, end = 24.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = Cupertino.Blue, modifier = Modifier.size(30.dp))
            Spacer(Modifier.height(10.dp))
            Text(clock, fontSize = 48.sp, fontWeight = FontWeight.Thin, color = Cupertino.Label)
            Spacer(Modifier.height(6.dp))
            Text(reasonTitle, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Cupertino.Label)
            Text(reasonDetail, fontSize = 15.sp, color = Cupertino.SecondaryLabel)
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "APPS",
            fontSize = 13.sp, color = Cupertino.SecondaryLabel,
            modifier = Modifier.padding(start = 16.dp, bottom = 6.dp)
        )

        Column(Modifier.padding(horizontal = 16.dp)) {
            apps.forEach { app ->
                val cat = prefs.categoryOf(app.packageName)
                val limit = prefs.limitMinutesOf(app.packageName) * 60
                val used = perApp[app.packageName] ?: 0
                val isBlocked = when (cat) {
                    AppCategory.PLUS -> false
                    AppCategory.BLOCKED -> true
                    AppCategory.LIMIT -> used >= limit || bedtime
                    AppCategory.STANDARD -> bedtime ||
                        prefs.globalUsedSeconds >= prefs.globalLimitMinutes * 60
                }
                AppRow(label = app.label, category = cat, blocked = isBlocked)
            }
        }

        Spacer(Modifier.height(24.dp))

        // Emergency call + leave
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Cupertino.Green)
                    .clickable {
                        val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(dial) }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Phone, contentDescription = "Anrufen", tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text("Notruf / Anrufen", fontSize = 14.sp, color = Cupertino.SecondaryLabel)

            Spacer(Modifier.height(20.dp))
            Text(
                "Zum Startbildschirm",
                fontSize = 16.sp, color = Cupertino.Blue,
                modifier = Modifier.clickable { onClose() }.padding(8.dp)
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AppRow(label: String, category: AppCategory, blocked: Boolean) {
    val (statusText, statusColor) = when {
        category == AppCategory.PLUS -> "Verfügbar" to Cupertino.Green
        blocked -> "Gesperrt" to Cupertino.Red
        else -> "Verfügbar" to Cupertino.Green
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App name (greyed when blocked)
        Text(
            label,
            fontSize = 17.sp,
            color = if (blocked) Cupertino.TertiaryLabel else Cupertino.Label,
            modifier = Modifier.weight(1f)
        )
        if (blocked) {
            Icon(
                Icons.Filled.Lock, contentDescription = null,
                tint = Cupertino.TertiaryLabel, modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(statusText, fontSize = 14.sp, color = statusColor, fontWeight = FontWeight.SemiBold)
    }
}
