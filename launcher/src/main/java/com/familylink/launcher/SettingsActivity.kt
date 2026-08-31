package com.familylink.launcher

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The launcher's own settings.
 *
 * Everything the setup wizard asks, reachable again afterwards — plus the two things that are
 * easier to check than to remember: whether this launcher is actually the phone's home screen,
 * and whether it has its own line to the family's database.
 *
 * No PIN. Arranging a home screen is not a rule to enforce, and locking it behind the parent's
 * PIN would only mean the child asks for the PIN to move an icon. Time limits, app locks and
 * the system settings stay behind it; this does not.
 */
class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { SettingsScreen { finish() } }
    }

    companion object {
        fun start(context: Context) {
            runCatching {
                context.startActivity(
                    Intent(context, SettingsActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { LauncherPrefs(context) }
    val model = remember { HomeModel(context) }
    // Loaded once here rather than when the dialog opens: reading seventy icons is not work to
    // do in the middle of a tap.
    val apps = remember { Apps.load(context) }
    var confirmFill by remember { mutableStateOf(false) }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    Box(Modifier.fillMaxSize().background(Color(0xFF141418))) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "Einstellungen",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 4.dp)
            )
            Text(
                "Völkle Start",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(Modifier.height(20.dp))

            Section("Startbildschirm")
            Item(
                Icons.Filled.Apps,
                "Startbildschirm bearbeiten",
                "Apps antippen, statt sie einzeln zu ziehen"
            ) { SetupActivity.start(context, SetupActivity.STEP_APPS) }
            Item(
                Icons.Filled.Home,
                "Leiste unten bearbeiten",
                "Bis zu ${LauncherPrefs.DOCK_MAX} Apps, auf jeder Seite sichtbar"
            ) { SetupActivity.start(context, SetupActivity.STEP_DOCK) }
            Item(
                Icons.Filled.Apps,
                "Alle Apps anordnen",
                "Legt jede installierte App auf die Seiten"
            ) { confirmFill = true }
            Item(
                Icons.Filled.Refresh,
                "Einrichtung erneut starten",
                "Der ganze Assistent von vorn"
            ) { SetupActivity.start(context, SetupActivity.STEP_ALL) }

            Section("Aussehen")
            Item(
                Icons.Filled.Wallpaper,
                "Hintergrundbild ändern",
                "Öffnet die Auswahl von Android"
            ) {
                runCatching {
                    context.startActivity(
                        Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Hintergrundbild")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
            Item(
                Icons.Filled.Place,
                "Wetter neben der Uhr",
                if (Weather.hasPermission(context)) "Standort ist freigegeben"
                else "Standort noch nicht freigegeben"
            ) { locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }

            Section("System")
            Item(
                Icons.Filled.Home,
                "Als Startbildschirm festlegen",
                "Android fragt, welche App die Home-Taste öffnet"
            ) {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
            Item(
                Icons.Filled.Cloud,
                "Verbindung zur Familie",
                if (prefs.syncConfigured) "Verbunden — der Startbildschirm kennt die Regeln auch " +
                    "dann, wenn die Haupt-App gestoppt wurde"
                else "Noch nicht übernommen. Die Haupt-App einmal öffnen, dann holt sich der " +
                    "Startbildschirm die Zugangsdaten von selbst."
            ) { }
            Item(
                Icons.Filled.Shield,
                "Kindersicherung öffnen",
                "Zeiten, Apps und PIN in der Family-Link-App"
            ) { Guard.openPortal(context) }

            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.End) {
                Text(
                    "Fertig",
                    color = Color(0xFF141418),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.White)
                        .clickable(onClick = onClose)
                        .padding(horizontal = 22.dp, vertical = 12.dp)
                )
            }
        }

        if (confirmFill) {
            ConfirmDialog(
                title = "Alle Apps anordnen",
                body = "Alle ${apps.size} Apps des Telefons werden auf die Seiten gelegt — " +
                    "Telefon, Nachrichten und Kamera zuerst, der Rest nach Namen. " +
                    "Die jetzige Anordnung geht dabei verloren; die Leiste unten bleibt.",
                confirm = "Anordnen",
                onConfirm = { model.fillWithAllApps(apps); confirmFill = false },
                onDismiss = { confirmFill = false }
            )
        }
    }
}

@Composable
private fun Section(label: String) {
    Text(
        label.uppercase(),
        color = Color.White.copy(alpha = 0.4f),
        fontSize = 12.sp,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 6.dp)
    )
}

@Composable
private fun Item(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp)
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        Modifier.fillMaxSize().background(Color(0xCC000000)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .padding(32.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF23232A))
                .padding(20.dp)
        ) {
            Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Text(body, color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    "Abbrechen",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 15.sp,
                    modifier = Modifier.clickable(onClick = onDismiss)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
                Text(
                    confirm,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable(onClick = onConfirm)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}
