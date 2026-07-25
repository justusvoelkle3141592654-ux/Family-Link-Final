package com.familylink.ios.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familylink.ios.data.Prefs
import com.familylink.ios.sync.DeviceRole
import com.familylink.ios.sync.SyncManager
import com.familylink.ios.ui.components.NovaButton
import com.familylink.ios.ui.theme.Nova
import kotlin.concurrent.thread

/** Step 1 — choose whether this phone is the parent's or the child's device. */
@Composable
fun RoleChoiceScreen(onChosen: (DeviceRole) -> Unit) {
    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(Nova.PageGradient))
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))
        Text("Family Link", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Nova.Ink)
        Spacer(Modifier.height(8.dp))
        Text(
            "Auf welchem Gerät wird die App eingerichtet?",
            fontSize = 16.sp, color = Nova.InkMuted, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(36.dp))

        RoleCard(
            title = "Eltern-Gerät",
            subtitle = "Regeln festlegen und die Nutzung des Kindes live sehen.",
            accent = Nova.Primary,
            icon = { Icon(Icons.Filled.Shield, null, tint = Nova.Primary, modifier = Modifier.size(30.dp)) }
        ) { onChosen(DeviceRole.PARENT) }

        Spacer(Modifier.height(16.dp))

        RoleCard(
            title = "Kinder-Gerät",
            subtitle = "Wird überwacht. Zeigt die eigene Nutzung und verbleibende Zeit.",
            accent = Nova.Success,
            icon = { Icon(Icons.Filled.ChildCare, null, tint = Nova.Success, modifier = Modifier.size(30.dp)) }
        ) { onChosen(DeviceRole.CHILD) }

        Spacer(Modifier.height(28.dp))
        Text(
            "Beide Geräte werden über einen 6-stelligen Code verbunden.",
            fontSize = 13.sp, color = Nova.InkFaint, textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun RoleCard(
    title: String,
    subtitle: String,
    accent: Color,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Nova.Surface)
            .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(54.dp).clip(CircleShape).background(accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) { icon() }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = Nova.Ink)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, fontSize = 13.sp, color = Nova.InkMuted)
        }
        Text("›", fontSize = 26.sp, color = Nova.InkFaint)
    }
}

/**
 * Step 2 — connect the two devices.
 * The parent creates a family (generates the code); the child joins with it.
 * Both need the same server URL, entered once here.
 */
@Composable
fun PairingScreen(role: DeviceRole, onPaired: () -> Unit, onSkip: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val manager = remember { SyncManager(context) }

    var url by remember { mutableStateOf(prefs.syncUrl) }
    var code by remember {
        mutableStateOf(
            if (role == DeviceRole.PARENT && prefs.familyId.isBlank()) SyncManager.generatePairingCode()
            else prefs.familyId
        )
    }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var ok by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(Nova.PageGradient))
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))
        Box(
            Modifier.size(60.dp).clip(CircleShape).background(Nova.Primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.CloudDone, null, tint = Nova.Primary, modifier = Modifier.size(28.dp)) }

        Spacer(Modifier.height(16.dp))
        Text("Geräte verbinden", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Nova.Ink)
        Spacer(Modifier.height(6.dp))
        Text(
            if (role == DeviceRole.PARENT)
                "Diesen Code auf dem Kinder-Gerät eingeben."
            else
                "Code vom Eltern-Gerät eingeben.",
            fontSize = 15.sp, color = Nova.InkMuted, textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        // Server URL
        Text("Server-Adresse", fontSize = 13.sp, color = Nova.InkMuted,
            modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        InputBox(value = url, placeholder = "https://…firebasedatabase.app", onChange = { url = it })
        Spacer(Modifier.height(4.dp))
        Text(
            "Einmalig: kostenlose Firebase Realtime Database anlegen und URL hier einfügen (siehe README).",
            fontSize = 11.sp, color = Nova.InkFaint
        )

        Spacer(Modifier.height(20.dp))

        // Pairing code
        Text("Verbindungscode", fontSize = 13.sp, color = Nova.InkMuted,
            modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        if (role == DeviceRole.PARENT) {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(Nova.Primary.copy(alpha = 0.10f)).padding(vertical = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    code.chunked(3).joinToString(" "),
                    fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Nova.Primary
                )
            }
        } else {
            InputBox(value = code, placeholder = "6-stelliger Code", numeric = true, onChange = { code = it.take(6) })
        }

        Spacer(Modifier.height(24.dp))

        message?.let {
            Text(it, fontSize = 14.sp, color = if (ok) Nova.Success else Nova.Danger,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
        }

        NovaButton(
            text = when {
                busy -> "Verbinde…"
                role == DeviceRole.PARENT -> "Familie erstellen & fortfahren"
                else -> "Verbinden"
            },
            enabled = !busy && url.isNotBlank() && code.length == 6,
            color = Nova.Primary
        ) {
            busy = true
            message = null
            thread(isDaemon = true) {
                val success = if (role == DeviceRole.PARENT) {
                    manager.createFamily(url, code)
                } else {
                    manager.familyExists(url, code)
                }
                if (success) {
                    prefs.syncUrl = url
                    prefs.familyId = code
                    if (role == DeviceRole.CHILD) manager.fetchConfigOnce() else manager.pushConfig()
                }
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    busy = false
                    ok = success
                    message = if (success) "Verbunden!" else
                        if (role == DeviceRole.PARENT) "Server nicht erreichbar. URL prüfen."
                        else "Code nicht gefunden. Code und URL prüfen."
                    if (success) onPaired()
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "Ohne Verbindung fortfahren (nur lokal)",
            fontSize = 14.sp, color = Nova.Primary,
            modifier = Modifier.clickable { onSkip() }.padding(8.dp)
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun InputBox(
    value: String,
    placeholder: String,
    numeric: Boolean = false,
    onChange: (String) -> Unit
) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(Nova.Surface).padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        if (value.isEmpty()) {
            Text(placeholder, fontSize = 15.sp, color = Nova.InkFaint)
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = 15.sp, color = Nova.Ink),
            cursorBrush = SolidColor(Nova.Primary),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Uri
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
