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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familylink.ios.data.Prefs
import com.familylink.ios.sync.Chore
import com.familylink.ios.sync.SyncManager
import com.familylink.ios.sync.SyncService
import com.familylink.ios.ui.components.NovaButton
import com.familylink.ios.ui.components.NovaButtonTonal
import com.familylink.ios.ui.components.NovaCard
import com.familylink.ios.ui.components.NovaPill
import com.familylink.ios.ui.theme.Nova
import kotlin.concurrent.thread

/**
 * Parent side: define chores and confirm the ones the child marked as done.
 * Approving credits the reward minutes instantly and syncs them to the child.
 */
@Composable
fun ChoresParentScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val sync = remember { SyncManager(context) }

    var v by remember { mutableStateOf(0) }
    val chores = remember(v) { prefs.getChores() }
    var newTitle by remember { mutableStateOf("") }
    var newReward by remember { mutableStateOf(15) }

    fun persist(list: List<Chore>) {
        prefs.setChores(list)
        v++
        thread(isDaemon = true) { sync.pushConfig() }
    }

    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(Nova.PageGradient))
            .verticalScroll(rememberScrollState()).padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                    .background(Nova.Warning.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.EmojiEvents, null, tint = Nova.Warning, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Aufgaben", fontSize = 24.sp, fontWeight = FontWeight.Normal, color = Nova.Ink)
                Text("Erledigte Aufgaben geben Bonuszeit", fontSize = 13.sp, color = Nova.InkMuted)
            }
        }

        // --- waiting for confirmation ---
        val claimed = chores.filter { it.isClaimed }
        if (claimed.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text("Wartet auf Bestätigung", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Nova.InkMuted)
            Spacer(Modifier.height(8.dp))
            claimed.forEach { c ->
                NovaCard(Modifier.padding(vertical = 4.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(c.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                                color = Nova.Ink, modifier = Modifier.weight(1f))
                            NovaPill("+${c.rewardMinutes} Min", Nova.Success)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.weight(1f)) {
                                NovaButton(text = "Bestätigen", color = Nova.Success) {
                                    // approveChore pushes the config, which is a network call:
                                    // on the main thread it fails silently and the child never
                                    // learns about the granted bonus.
                                    thread(isDaemon = true) { sync.approveChore(c.id) }
                                    v++
                                    SyncService.pushNow(context)
                                }
                            }
                            Box(Modifier.weight(1f)) {
                                NovaButtonTonal(text = "Zurückgeben", color = Nova.Danger) {
                                    thread(isDaemon = true) { sync.rejectChore(c.id) }
                                    v++
                                    SyncService.pushNow(context)
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- create ---
        Spacer(Modifier.height(24.dp))
        Text("Neue Aufgabe", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Nova.InkMuted)
        Spacer(Modifier.height(8.dp))
        NovaCard {
            Column(Modifier.padding(16.dp)) {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(Nova.Fill).padding(12.dp)
                ) {
                    if (newTitle.isEmpty()) Text("z. B. Zimmer aufräumen", fontSize = 14.sp, color = Nova.InkFaint)
                    BasicTextField(
                        value = newTitle, onValueChange = { newTitle = it.take(60) },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 14.sp, color = Nova.Ink),
                        cursorBrush = SolidColor(Nova.Primary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Belohnung", fontSize = 14.sp, color = Nova.InkMuted, modifier = Modifier.weight(1f))
                    StepChip("−") { newReward = (newReward - 5).coerceAtLeast(5) }
                    Text("$newReward Min", modifier = Modifier.padding(horizontal = 12.dp),
                        fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Nova.Ink)
                    StepChip("+") { newReward = (newReward + 5).coerceAtMost(60) }
                }
                Spacer(Modifier.height(14.dp))
                NovaButton(text = "Aufgabe hinzufügen", enabled = newTitle.isNotBlank()) {
                    persist(chores + Chore(Chore.newId(), newTitle.trim(), newReward))
                    newTitle = ""
                }
            }
        }

        // --- suggestions ---
        if (chores.isEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text("Vorschläge", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Nova.InkMuted)
            Spacer(Modifier.height(8.dp))
            Chore.SUGGESTIONS.forEach { (title, mins) ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp)).background(Nova.Surface)
                        .clickable { persist(chores + Chore(Chore.newId(), title, mins)) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Add, null, tint = Nova.Primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(title, fontSize = 15.sp, color = Nova.Ink, modifier = Modifier.weight(1f))
                    NovaPill("+$mins", Nova.Success)
                }
            }
        }

        // --- existing list ---
        val others = chores.filter { !it.isClaimed }
        if (others.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text("Alle Aufgaben", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Nova.InkMuted)
            Spacer(Modifier.height(8.dp))
            NovaCard {
                others.forEach { c ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(c.title, fontSize = 15.sp, color = Nova.Ink)
                            Text(
                                if (c.isApproved) "Heute erledigt" else "Offen",
                                fontSize = 12.sp,
                                color = if (c.isApproved) Nova.Success else Nova.InkMuted
                            )
                        }
                        NovaPill("+${c.rewardMinutes}", Nova.Warning)
                        Spacer(Modifier.width(10.dp))
                        Icon(
                            Icons.Filled.Delete, "Löschen", tint = Nova.InkFaint,
                            modifier = Modifier.size(20.dp)
                                .clickable { persist(chores.filter { it.id != c.id }) }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        NovaButtonTonal(text = "Zurück", onClick = onBack)
        Spacer(Modifier.height(24.dp))
    }
}

/** Child side: see the available chores and mark them as done. */
@Composable
fun ChoresChildScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val sync = remember { SyncManager(context) }
    var v by remember { mutableStateOf(0) }
    val chores = remember(v) { prefs.getChores() }

    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(Nova.PageGradient))
            .verticalScroll(rememberScrollState()).padding(20.dp)
    ) {
        Text("Aufgaben", fontSize = 26.sp, fontWeight = FontWeight.Normal, color = Nova.Ink)
        Text("Erledige Aufgaben und verdiene Extra-Zeit", fontSize = 13.sp, color = Nova.InkMuted)
        Spacer(Modifier.height(20.dp))

        if (chores.isEmpty()) {
            NovaCard {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.EmojiEvents, null, tint = Nova.InkFaint, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Noch keine Aufgaben", fontSize = 16.sp, color = Nova.InkMuted)
                }
            }
        } else {
            chores.forEach { c ->
                NovaCard(Modifier.padding(vertical = 5.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(38.dp).clip(CircleShape).background(
                                when {
                                    c.isApproved -> Nova.Success.copy(alpha = 0.15f)
                                    c.isClaimed -> Nova.Warning.copy(alpha = 0.15f)
                                    else -> Nova.Fill
                                }
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (c.isApproved) {
                                Icon(Icons.Filled.Check, null, tint = Nova.Success, modifier = Modifier.size(20.dp))
                            } else {
                                Text("+${c.rewardMinutes}", fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (c.isClaimed) Nova.Warning else Nova.InkMuted)
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Nova.Ink)
                            Text(
                                when {
                                    c.isApproved -> "Bestätigt · +${c.rewardMinutes} Min erhalten"
                                    c.isClaimed -> "Warte auf Bestätigung"
                                    else -> "${c.rewardMinutes} Min Bonus"
                                },
                                fontSize = 12.sp,
                                color = when {
                                    c.isApproved -> Nova.Success
                                    c.isClaimed -> Nova.Warning
                                    else -> Nova.InkMuted
                                }
                            )
                        }
                        if (c.isOpen) {
                            Box(
                                Modifier.clip(RoundedCornerShape(Nova.RadiusPill.dp))
                                    .background(Nova.Primary)
                                    .clickable {
                                        sync.claimChore(c.id); v++
                                        SyncService.pushNow(context)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text("Erledigt", color = Color.White, fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold)
                            }
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
private fun StepChip(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(Nova.Fill).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 18.sp, color = Nova.Primary, fontWeight = FontWeight.Bold)
    }
}
