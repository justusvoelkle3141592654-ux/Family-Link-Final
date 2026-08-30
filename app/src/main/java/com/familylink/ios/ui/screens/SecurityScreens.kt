package com.familylink.ios.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familylink.ios.data.Prefs
import com.familylink.ios.ui.components.NovaButton
import com.familylink.ios.ui.components.SecurePinPad
import com.familylink.ios.ui.theme.Nova

/**
 * Parent-only time extension: verify the long secure PIN, then grant up to the remaining
 * daily bonus (max 30 min/day). Used from both the block screen and the main app.
 */
@Composable
fun ExtendTimeScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }

    var step by remember { mutableStateOf(if (prefs.isSecurePinSet) "pin" else "nopin") }
    var entered by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var minutes by remember { mutableStateOf(15) }
    var granted by remember { mutableStateOf(0) }

    Box(Modifier.fillMaxSize().background(Nova.Canvas), contentAlignment = Alignment.Center) {
        when (step) {
            "nopin" -> InfoCard(
                title = "Sicherheits-PIN fehlt",
                text = "Bitte zuerst im Eltern-Portal unter Sicherheit eine Sicherheits-PIN festlegen. " +
                    "Sie wird benötigt, um Zeit freizugeben.",
                onClose = onClose
            )

            "pin" -> SecurePinPad(
                entered = entered,
                title = "Zeit verlängern",
                subtitle = "Sicherheits-PIN der Eltern eingeben",
                minLength = 4,
                error = error,
                confirmLabel = "Bestätigen",
                onDigit = { if (entered.length < 12) { error = false; entered += it } },
                onDelete = { error = false; if (entered.isNotEmpty()) entered = entered.dropLast(1) },
                onConfirm = {
                    // No daily ceiling any more: with the right PIN there is always time to give.
                    if (prefs.checkSecurePin(entered)) step = "minutes"
                    else { error = true; entered = "" }
                }
            )

            "minutes" -> {
                val already = prefs.grantedBonusMinutes()
                Column(
                    Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Zeit freigeben", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Nova.Ink)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (already > 0) "Heute bereits freigegeben: $already Min"
                        else "Hebt Tageslimit, Gesamtlimit und den Beginn der Ruhezeit an.",
                        fontSize = 14.sp, color = Nova.InkMuted, textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(28.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StepBtn("−") { minutes = (minutes - 5).coerceAtLeast(5) }
                        Text(
                            "$minutes Min",
                            modifier = Modifier.padding(horizontal = 20.dp),
                            fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Nova.Ink
                        )
                        StepBtn("+") { minutes = (minutes + 5).coerceAtMost(240) }
                    }
                    Spacer(Modifier.height(28.dp))
                    NovaButton(text = "$minutes Min freigeben", color = Nova.Success) {
                        granted = prefs.grantExtension(minutes)
                        step = "done"
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Abbrechen", color = Nova.Primary, fontSize = 16.sp, modifier = Modifier.clickable { onClose() }.padding(8.dp))
                }
            }

            "done" -> InfoCard(
                title = "Zeit freigegeben",
                text = "Zusätzliche Zeit heute gesamt: $granted Min.",
                onClose = onClose
            )
        }
    }
}

/** Set or change the long secure PIN (entered twice, min length enforced). */
@Composable
fun SecurePinSetupScreen(onDone: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }

    var entered by remember { mutableStateOf("") }
    var first by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Nova.Canvas), contentAlignment = Alignment.Center) {
        SecurePinPad(
            entered = entered,
            title = if (first == null) "Sicherheits-PIN festlegen" else "Sicherheits-PIN bestätigen",
            subtitle = if (error) "Stimmt nicht überein – erneut versuchen"
            else "Mindestens ${Prefs.SECURE_PIN_MIN_LEN} Ziffern",
            minLength = Prefs.SECURE_PIN_MIN_LEN,
            error = error,
            confirmLabel = if (first == null) "Weiter" else "Speichern",
            onDigit = { if (entered.length < 12) { error = false; entered += it } },
            onDelete = { error = false; if (entered.isNotEmpty()) entered = entered.dropLast(1) },
            onConfirm = {
                val f = first
                if (f == null) { first = entered; entered = "" }
                else if (f == entered) { prefs.setSecurePin(entered); onDone() }
                else { error = true; first = null; entered = "" }
            }
        )
    }
}

@Composable
private fun InfoCard(title: String, text: String, onClose: () -> Unit) {
    Column(
        Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Nova.Ink)
        Spacer(Modifier.height(12.dp))
        Text(text, fontSize = 15.sp, color = Nova.InkMuted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        NovaButton(text = "Fertig", onClick = onClose)
    }
}

@Composable
private fun StepBtn(label: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(10.dp)).background(Nova.Fill).clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 24.sp, color = Nova.Primary)
    }
}
