package com.familylink.ios.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familylink.ios.admin.DeviceAdmin
import com.familylink.ios.ui.components.NovaButton
import com.familylink.ios.ui.components.NovaCard
import com.familylink.ios.ui.components.NovaRow
import com.familylink.ios.ui.components.SectionHeader
import com.familylink.ios.ui.theme.Nova
import com.familylink.ios.util.Permissions

/**
 * Guided permission grants. Each row reflects live status and deep-links into the right
 * system settings screen. Order matches the spec's importance.
 */
@Composable
fun PermissionsScreen(
    onAllGranted: () -> Unit,
    showContinue: Boolean = true
) {
    val context = LocalContext.current
    val tick by rememberResumeTick()

    // read live each time we resume
    @Suppress("UNUSED_EXPRESSION") tick
    val usage = Permissions.hasUsageAccess(context)
    val overlay = Permissions.hasOverlay(context)
    val accessibility = Permissions.accessibilityEnabled(context)
    val admin = DeviceAdmin.isActive(context)
    val allCore = usage && overlay && accessibility

    Column(
        Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(Nova.PageGradient))
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Berechtigungen", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Nova.Ink)
        Spacer(Modifier.height(4.dp))
        Text(
            "Damit die Kindersicherung zuverlässig und unumgehbar funktioniert, werden diese " +
                "Zugriffe benötigt.",
            fontSize = 15.sp, color = Nova.InkMuted
        )

        // Opening any settings page from our own flow authorises settings access briefly, so the
        // lock never fights our own permission / admin screens.
        val openAuthorized: (android.content.Intent) -> Unit = { intent ->
            com.familylink.ios.data.Prefs.get(context).unlockSettings(3)
            runCatching { context.startActivity(intent) }
        }

        SectionHeader("Erforderlich")
        NovaCard {
            PermRow(
                title = "Nutzungszugriff",
                subtitle = "Misst die App-Zeit ab 00:00 Uhr",
                granted = usage
            ) { openAuthorized(Permissions.usageAccessIntent()) }
            Divider()
            PermRow(
                title = "Über anderen Apps anzeigen",
                subtitle = "Damit die Sperr-Liste angezeigt werden kann",
                granted = overlay
            ) { openAuthorized(Permissions.overlayIntent(context)) }
            Divider()
            PermRow(
                title = "Bedienungshilfe",
                subtitle = "Überwachung & Schutz vor Umgehung",
                granted = accessibility
            ) { openAuthorized(Permissions.accessibilityIntent()) }
        }

        SectionHeader("Empfohlen")
        NovaCard {
            PermRow(
                title = "Geräteadministrator",
                subtitle = "Verhindert die Deinstallation (sperrt nicht)",
                granted = admin
            ) { openAuthorized(DeviceAdmin.enableIntent(context)) }
        }

        Spacer(Modifier.height(28.dp))
        if (showContinue) {
            NovaButton(
                text = if (allCore) "Weiter" else "Bitte alle erforderlichen erteilen",
                enabled = allCore,
                onClick = onAllGranted
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PermRow(title: String, subtitle: String, granted: Boolean, onClick: () -> Unit) {
    NovaRow(title = title, subtitle = subtitle, onClick = onClick) {
        if (granted) {
            Icon(Icons.Filled.CheckCircle, contentDescription = "Erteilt", tint = Nova.Success)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Erteilen", color = Nova.Primary, fontSize = 15.sp)
                Spacer(Modifier.height(0.dp))
                Icon(Icons.Filled.RadioButtonUnchecked, contentDescription = null, tint = Nova.InkFaint)
            }
        }
    }
}

@Composable
private fun Divider() {
    Spacer(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(0.5.dp)
            .background(Nova.Line)
    )
}
