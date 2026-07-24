package com.familylink.ios.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familylink.ios.data.AppCategory
import com.familylink.ios.data.InstalledApps
import com.familylink.ios.data.Prefs
import com.familylink.ios.ui.theme.Cupertino

/**
 * Lets the parent assign each installed app to a category:
 *  Plus (always allowed) · Limit (own daily limit) · Standard (shared global budget).
 * Tapping the trailing chip cycles Standard -> Plus -> Limit -> Standard.
 */
@Composable
fun AppsScreen() {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val apps = remember { InstalledApps.load(context) }
    // local mirror so the UI updates immediately; persisted on each change
    var version by remember { mutableStateOf(0) }

    Column(Modifier.fillMaxSize().background(Cupertino.SystemBackground)) {
        Text(
            "Apps verwalten",
            fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Cupertino.Label,
            modifier = Modifier.padding(16.dp)
        )
        Text(
            "Plus = immer erlaubt · Limit = eigenes Limit · Standard = gemeinsames Guthaben · Gesperrt = nie",
            fontSize = 13.sp, color = Cupertino.SecondaryLabel,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(12.dp))

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            items(apps, key = { it.packageName }) { app ->
                @Suppress("UNUSED_EXPRESSION") version
                val cat = prefs.categoryOf(app.packageName)
                val limit = prefs.limitMinutesOf(app.packageName)

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(app.label, fontSize = 17.sp, color = Cupertino.Label)
                        if (cat == AppCategory.LIMIT) {
                            Text("Limit: $limit Min/Tag", fontSize = 13.sp, color = Cupertino.SecondaryLabel)
                        } else {
                            Text(app.packageName, fontSize = 11.sp, color = Cupertino.TertiaryLabel)
                        }
                    }
                    CategoryChip(cat) {
                        val next = when (cat) {
                            AppCategory.STANDARD -> AppCategory.PLUS
                            AppCategory.PLUS -> AppCategory.LIMIT
                            AppCategory.LIMIT -> AppCategory.BLOCKED
                            AppCategory.BLOCKED -> AppCategory.STANDARD
                        }
                        prefs.setCategory(app.packageName, next, limit)
                        version++
                    }
                    if (cat == AppCategory.LIMIT) {
                        Spacer(Modifier.height(0.dp))
                        StepperMinutes(limit) { newVal ->
                            prefs.setCategory(app.packageName, AppCategory.LIMIT, newVal)
                            version++
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(cat: AppCategory, onClick: () -> Unit) {
    val (label, color) = when (cat) {
        AppCategory.PLUS -> "Plus" to Cupertino.Green
        AppCategory.LIMIT -> "Limit" to Cupertino.Orange
        AppCategory.STANDARD -> "Standard" to Cupertino.Blue
        AppCategory.BLOCKED -> "Gesperrt" to Cupertino.Red
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
        Text("$minutes", modifier = Modifier.padding(horizontal = 6.dp), fontSize = 15.sp, color = Cupertino.Label)
        StepBtn("+") { onChange((minutes + 5).coerceAtMost(240)) }
    }
}

@Composable
private fun StepBtn(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(start = 6.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0x11000000))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(label, fontSize = 18.sp, color = Cupertino.Blue)
    }
}
