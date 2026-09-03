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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * Drawn with the same cards and rows as the Family Link app, from the shared tokens in Look.
 * Two apps on one phone that are meant to be one product should not look like two products.
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
    // Bumped after anything that changes what the rows below report, so they redraw.
    var v by remember { mutableStateOf(0) }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { v++ }

    Box(Modifier.fillMaxSize().background(Look.Canvas)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            LookTitle("Einstellungen", "Völkle Start")

            LookSection("Startbildschirm")
            LookCard {
                LookRow(
                    title = "Startbildschirm bearbeiten",
                    subtitle = "Apps antippen, statt sie einzeln zu ziehen",
                    icon = Icons.Filled.Apps,
                    onClick = { SetupActivity.start(context, SetupActivity.STEP_APPS) }
                )
                LookDivider()
                LookRow(
                    title = "Leiste unten bearbeiten",
                    subtitle = "Bis zu ${LauncherPrefs.DOCK_MAX} Apps, auf jeder Seite sichtbar",
                    icon = Icons.Filled.ViewModule,
                    onClick = { SetupActivity.start(context, SetupActivity.STEP_DOCK) }
                )
                LookDivider()
                LookRow(
                    title = "Alle Apps anordnen",
                    subtitle = "Legt jede installierte App auf die Seiten",
                    icon = Icons.Filled.Apps,
                    onClick = { confirmFill = true }
                )
                LookDivider()
                LookRow(
                    title = "Einrichtung erneut starten",
                    subtitle = "Der ganze Assistent von vorn",
                    icon = Icons.Filled.Refresh,
                    onClick = { SetupActivity.start(context, SetupActivity.STEP_ALL) }
                )
            }

            LookSection("Aussehen")
            LookCard {
                LookRow(
                    title = "Hintergrundbild ändern",
                    subtitle = "Öffnet die Auswahl von Android",
                    icon = Icons.Filled.Wallpaper,
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SET_WALLPAPER), "Hintergrundbild"
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                )
                LookDivider()
                key(v) {
                    val granted = Weather.hasPermission(context)
                    LookRow(
                        title = "Wetter neben der Uhr",
                        subtitle = if (granted)
                            "Standort ist freigegeben. Nur die grobe Position, nur für die Temperatur."
                        else
                            "Braucht den groben Standort. Ohne ihn steht dort einfach nur die Uhr.",
                        icon = Icons.Filled.Place,
                        iconTint = if (granted) Look.Success else Look.Primary,
                        onClick = {
                            locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        }
                    ) {
                        LookPill(
                            if (granted) "Aktiv" else "Aus",
                            if (granted) Look.Success else Look.InkFaint
                        )
                    }
                }
            }

            LookSection("System")
            LookCard {
                key(v) {
                    val isHome = isDefaultHome(context)
                    LookRow(
                        title = "Als Startbildschirm festlegen",
                        subtitle = if (isHome)
                            "Völkle Start ist der Startbildschirm dieses Handys."
                        else
                            "Android fragt, welche App die Home-Taste öffnet — dort " +
                                "„Völkle Start\" wählen.",
                        icon = Icons.Filled.Home,
                        iconTint = if (isHome) Look.Success else Look.Warning,
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_HOME_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }
                    ) {
                        LookPill(
                            if (isHome) "Aktiv" else "Einrichten",
                            if (isHome) Look.Success else Look.Warning
                        )
                    }
                }
                LookDivider()
                LookRow(
                    title = "Verbindung zur Familie",
                    subtitle = if (prefs.syncConfigured)
                        "Verbunden. Der Startbildschirm kennt die Regeln auch dann, wenn die " +
                            "Haupt-App gestoppt wurde."
                    else
                        "Noch nicht übernommen. Die Haupt-App einmal öffnen, dann holt sich " +
                            "der Startbildschirm die Zugangsdaten von selbst.",
                    icon = Icons.Filled.Cloud,
                    iconTint = if (prefs.syncConfigured) Look.Success else Look.Warning
                ) {
                    LookPill(
                        if (prefs.syncConfigured) "Verbunden" else "Offen",
                        if (prefs.syncConfigured) Look.Success else Look.Warning
                    )
                }
                LookDivider()
                LookRow(
                    title = "Kindersicherung öffnen",
                    subtitle = "Zeiten, Apps und PIN in der Family-Link-App",
                    icon = Icons.Filled.Shield,
                    onClick = { Guard.openPortal(context) }
                )
            }

            Spacer(Modifier.height(28.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.End
            ) {
                LookButton("Fertig", onClick = onClose)
            }
            Spacer(Modifier.height(28.dp))
        }

        if (confirmFill) {
            LookConfirm(
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

/** Is this launcher the phone's home screen? Read live, because the user can change it. */
private fun isDefaultHome(context: Context): Boolean = runCatching {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val res = context.packageManager.resolveActivity(
        intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
    )
    res?.activityInfo?.packageName == context.packageName
}.getOrDefault(false)

/** The one dialog shape, matching the app's cards. */
@Composable
fun LookConfirm(
    title: String,
    body: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        Modifier.fillMaxSize().background(Look.Scrim).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        LookPanel {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(title, color = Look.Ink, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Text(body, color = Look.InkMuted, fontSize = 14.sp, lineHeight = 19.sp)
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    LookButton("Abbrechen", filled = false, small = true, onClick = onDismiss)
                    Spacer(Modifier.width(8.dp))
                    LookButton(confirm, small = true, onClick = onConfirm)
                }
            }
        }
    }
}
