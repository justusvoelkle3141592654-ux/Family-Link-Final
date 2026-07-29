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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familylink.ios.data.Prefs
import com.familylink.ios.sync.Account
import com.familylink.ios.sync.AccountClient
import com.familylink.ios.sync.AuthResult
import com.familylink.ios.sync.DeviceRole
import com.familylink.ios.sync.SyncClient
import com.familylink.ios.ui.components.NovaButton
import com.familylink.ios.ui.theme.Nova
import kotlin.concurrent.thread

/**
 * Account step of the wizard: create a family account or sign in to an existing one, then
 * register this device. A family account holds at most [Account.MAX_DEVICES] devices.
 */
@Composable
fun AuthScreen(role: DeviceRole, onDone: () -> Unit, onSkip: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }

    var signUp by remember { mutableStateOf(role == DeviceRole.PARENT) }
    var url by remember { mutableStateOf(prefs.syncUrl) }
    var email by remember { mutableStateOf(prefs.accountEmail) }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize()
            .background(Nova.Canvas)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))
        Box(
            Modifier.size(70.dp).clip(RoundedCornerShape(22.dp))
                .background(Brush.linearGradient(Nova.BrandGradient)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Shield, null, tint = Color.White, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(
            if (signUp) "Familien-Konto erstellen" else "Anmelden",
            fontSize = 26.sp, fontWeight = FontWeight.Normal, color = Nova.Ink
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (signUp) "Ein Konto verbindet alle Geräte deiner Familie."
            else "Mit dem Konto der Familie anmelden.",
            fontSize = 14.sp, color = Nova.InkMuted, textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(28.dp))

        FieldLabel("Server-Adresse")
        AuthField(url, "https://…firebasedatabase.app", KeyboardType.Uri) { url = it }
        Spacer(Modifier.height(6.dp))
        Text(
            "Einmalig pro Familie. Kostenlos anlegbar — Anleitung im README.",
            fontSize = 11.sp, color = Nova.InkFaint
        )

        Spacer(Modifier.height(16.dp))
        FieldLabel("E-Mail")
        AuthField(email, "familie@example.com", KeyboardType.Email) { email = it }

        Spacer(Modifier.height(16.dp))
        FieldLabel("Passwort")
        AuthField(password, "mindestens 6 Zeichen", KeyboardType.Password, isPassword = true) { password = it }

        Spacer(Modifier.height(20.dp))

        error?.let {
            Text(it, fontSize = 13.sp, color = Nova.Danger, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
        }

        NovaButton(
            text = when {
                busy -> "Bitte warten…"
                signUp -> "Konto erstellen"
                else -> "Anmelden"
            },
            enabled = !busy && url.isNotBlank() && email.isNotBlank() && password.isNotBlank()
        ) {
            busy = true
            error = null
            thread(isDaemon = true) {
                val client = AccountClient(SyncClient(url))
                val auth = if (signUp) client.signUp(email, password) else client.signIn(email, password)
                var message: String? = null

                if (auth is AuthResult.Success) {
                    // Enforce the three-device limit before we commit anything locally.
                    val reg = client.registerDevice(auth.familyId, prefs.deviceId, role)
                    if (reg is AuthResult.Error) {
                        message = reg.message
                    } else {
                        prefs.syncUrl = url
                        prefs.accountEmail = email
                        prefs.familyId = auth.familyId
                    }
                } else if (auth is AuthResult.Error) {
                    message = auth.message
                }

                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    busy = false
                    if (message == null) onDone() else error = message
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (signUp) "Schon ein Konto?" else "Noch kein Konto?",
                fontSize = 14.sp, color = Nova.InkMuted
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (signUp) "Anmelden" else "Konto erstellen",
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Nova.Primary,
                modifier = Modifier.clickable { signUp = !signUp; error = null }
            )
        }

        Spacer(Modifier.height(10.dp))
        Text(
            "Ohne Konto fortfahren (nur dieses Gerät)",
            fontSize = 13.sp, color = Nova.InkFaint,
            modifier = Modifier.clickable { onSkip() }.padding(8.dp)
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Nova.InkMuted,
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
    )
}

@Composable
private fun AuthField(
    value: String,
    placeholder: String,
    keyboard: KeyboardType,
    isPassword: Boolean = false,
    onChange: (String) -> Unit
) {
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(Nova.RadiusControl.dp))
            .background(Nova.Surface)
            .padding(horizontal = 14.dp, vertical = 15.dp)
    ) {
        if (value.isEmpty()) Text(placeholder, fontSize = 15.sp, color = Nova.InkFaint)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = 15.sp, color = Nova.Ink),
            cursorBrush = SolidColor(Nova.Primary),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else
                androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Device management shown in the parent portal (3-device limit). */
@Composable
fun DevicesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    var devices by remember { mutableStateOf(emptyList<com.familylink.ios.sync.RegisteredDevice>()) }
    var busy by remember { mutableStateOf(true) }

    fun reload() {
        busy = true
        thread(isDaemon = true) {
            val list = AccountClient(SyncClient(prefs.syncUrl)).listDevices(prefs.familyId)
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                devices = list; busy = false
            }
        }
    }
    remember { reload(); 0 }

    Column(
        Modifier.fillMaxSize().background(Nova.Canvas)
            .verticalScroll(rememberScrollState()).padding(20.dp)
    ) {
        Text("Geräte", fontSize = 28.sp, fontWeight = FontWeight.Normal, color = Nova.Ink)
        Text(
            "${devices.size} von ${Account.MAX_DEVICES} Geräten verbunden",
            fontSize = 14.sp, color = Nova.InkMuted
        )
        Spacer(Modifier.height(20.dp))

        if (busy) {
            Text("Lade…", fontSize = 14.sp, color = Nova.InkMuted)
        } else if (devices.isEmpty()) {
            Text("Noch keine Geräte registriert.", fontSize = 14.sp, color = Nova.InkMuted)
        } else {
            devices.forEach { d ->
                val isSelf = d.id == prefs.deviceId
                val online = System.currentTimeMillis() - d.lastSeen < 120_000
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp)
                        .clip(RoundedCornerShape(Nova.RadiusCard.dp))
                        .background(Nova.Surface).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(10.dp).clip(CircleShape)
                            .background(if (online) Nova.Success else Nova.InkFaint)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            d.name + if (isSelf) " (dieses Gerät)" else "",
                            fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Nova.Ink
                        )
                        Text(
                            if (d.role == "PARENT") "Eltern-Gerät" else "Kinder-Gerät",
                            fontSize = 12.sp, color = Nova.InkMuted
                        )
                    }
                    if (!isSelf) {
                        Text(
                            "Entfernen", fontSize = 13.sp, color = Nova.Danger,
                            modifier = Modifier.clickable {
                                thread(isDaemon = true) {
                                    AccountClient(SyncClient(prefs.syncUrl))
                                        .removeDevice(prefs.familyId, d.id)
                                    android.os.Handler(android.os.Looper.getMainLooper()).post { reload() }
                                }
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        NovaButton(text = "Zurück", onClick = onBack)
    }
}
