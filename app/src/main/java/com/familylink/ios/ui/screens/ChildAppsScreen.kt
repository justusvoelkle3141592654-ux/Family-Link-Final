package com.familylink.ios.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familylink.ios.data.AppCategory
import com.familylink.ios.data.InstalledApps
import com.familylink.ios.data.Prefs
import com.familylink.ios.ui.components.NovaDivider
import com.familylink.ios.ui.components.NovaHeroTime
import com.familylink.ios.ui.theme.Nova
import com.familylink.ios.util.TimeFmt

/**
 * The child's own view of every app on the phone — read only.
 *
 * Deliberately not [AppsScreen]: that one is the parent's tool and carries the category chip and
 * the per-app stepper. Here the child only *sees* what is installed, how long it was used today
 * and what is blocked; nothing on this page changes a rule.
 */
@Composable
fun ChildAppsScreen() {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }

    val apps = remember { InstalledApps.load(context) }
    val perApp = remember { prefs.getPerAppSeconds() }

    val used = prefs.globalUsedSeconds
    val limit = prefs.globalLimitMinutes * 60 + prefs.bonusSecondsToday
    val remaining = (limit - used).coerceAtLeast(0)

    Column(
        Modifier.fillMaxSize().background(Nova.Canvas).padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        NovaHeroTime(TimeFmt.hm(remaining), "Noch heute übrig")
        Spacer(Modifier.height(18.dp))

        // One card for the whole list, lazily filled: a phone can carry a hundred apps, and
        // building them all up front would make the page stutter on open.
        LazyColumn(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(Nova.RadiusCard.dp))
                .background(Nova.Surface)
        ) {
            itemsIndexed(apps, key = { _, app -> app.packageName }) { i, app ->
                if (i > 0) NovaDivider(inset = true)
                val category = prefs.categoryOf(app.packageName)
                val seconds = perApp[app.packageName] ?: 0
                AppRow(
                    pkg = app.packageName,
                    title = app.label,
                    subtitle = when {
                        category == AppCategory.BLOCKED -> "Gesperrt"
                        seconds > 0 -> "${TimeFmt.hm(seconds)} heute"
                        else -> "Noch nicht genutzt heute"
                    },
                    subtitleColor = if (category == AppCategory.BLOCKED) Nova.Danger else Nova.InkMuted
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/**
 * A list row for an app, in the same geometry [com.familylink.ios.ui.components.NovaRow] uses —
 * 16dp padding, a 40dp leading tile, 14dp gap — so that `NovaDivider(inset = true)`, which
 * indents by exactly those 70dp, lines up with the text.
 *
 * It exists next to NovaRow rather than inside it because an app's icon is a Bitmap from the
 * package manager, and NovaRow's glyph slot takes an ImageVector.
 */
@Composable
internal fun AppRow(
    pkg: String,
    title: String,
    subtitle: String,
    subtitleColor: Color = Nova.InkMuted,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val icon = remember(pkg) { InstalledApps.iconBitmap(context, pkg) }
    Row(
        Modifier.fillMaxWidth()
            .let { if (onClick != null) it.clickable { onClick() } else it }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Nova.Fill),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                Image(
                    bitmap = icon.asImageBitmap(), contentDescription = null,
                    modifier = Modifier.size(34.dp)
                )
            } else {
                Text(title.take(1), fontSize = 17.sp, color = Nova.Ink)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Nova.Ink)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, fontSize = 14.sp, color = subtitleColor, lineHeight = 19.sp)
        }
        trailing()
    }
}
