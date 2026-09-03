package com.familylink.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One visual language, shared with the Family Link app.
 *
 * ## Why this file exists
 *
 * The launcher grew its own colours screen by screen — a menu at 0xFF23232A, a field at
 * 0x22FFFFFF, a tick in a blue nobody chose — while the parent app was already built on a
 * proper set of tokens. Two apps on the same phone, meant to be one product, that did not look
 * related. The complaint was never about one screen being ugly; it was that every screen was
 * its own.
 *
 * The values below are the Family Link app's dark palette and shape tokens, copied
 * deliberately rather than imported: the launcher must not depend on the guard's code, because
 * it has to keep working when the guard is force-stopped. Copied constants are the price of
 * that separation, so they are kept in one place and nowhere else.
 *
 * The home screen itself stays white-on-wallpaper — text over a photograph needs its own
 * contrast and always did. Everything that is a panel rather than the desk (menus, dialogs,
 * the drawer, the wizard, the settings) uses these.
 */
object Look {

    // -- Surfaces (the app's dark palette) --------------------------------
    val Canvas = Color(0xFF111318)
    val Surface = Color(0xFF1D2024)
    val SurfaceAlt = Color(0xFF283041)
    val Fill = Color(0xFF2B2F36)
    val Line = Color(0x1FFFFFFF)

    // -- Text -------------------------------------------------------------
    val Ink = Color(0xFFE3E2E6)
    val InkMuted = Color(0xFFC4C6D0)
    val InkFaint = Color(0xFF8E9099)

    // -- Brand and state --------------------------------------------------
    val Primary = Color(0xFFA8C7FA)
    val Accent = Color(0xFF0842A0)
    val Success = Color(0xFF146C2E)
    val Warning = Color(0xFFA8500A)
    val Danger = Color(0xFFB3261E)

    /** On the wallpaper, where a palette cannot help: plain white and its shades. */
    val OnWall = Color.White
    val OnWallMuted = Color(0xCCFFFFFF)
    val OnWallFaint = Color(0x99FFFFFF)
    val Scrim = Color(0xCC000000)

    // -- Shape ------------------------------------------------------------
    const val RadiusCard = 26
    const val RadiusControl = 20
    const val RadiusPill = 999
}

/** The page title at the top of a panel screen. */
@Composable
fun LookTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp)) {
        Text(title, color = Look.Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = Look.InkMuted, fontSize = 14.sp, lineHeight = 19.sp)
        }
    }
}

/** Heading above a group of rows. Same weight and spacing as the app's SectionHeader. */
@Composable
fun LookSection(text: String) {
    Text(
        text,
        color = Look.InkMuted,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 26.dp, bottom = 10.dp)
    )
}

/** The rounded surface everything sits on, as in the app. */
@Composable
fun LookCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(Look.RadiusCard.dp))
            .background(Look.Surface)
    ) {
        Column(Modifier.padding(vertical = 4.dp)) { content() }
    }
}

/**
 * A row inside a card: glyph on a tinted disc, title, explaining line, optional trailing.
 *
 * Same measurements as the app's NovaRow — 40dp disc, 17sp title, 14sp subtitle, generous
 * vertical padding — because matching those is what makes the two apps read as one.
 */
@Composable
fun LookRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = Look.Primary,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        modifier
            .fillMaxWidth()
            .let { if (onClick != null && enabled) it.clickable { onClick() } else it }
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (iconTint == Look.Primary) Look.Accent else iconTint.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) Look.Ink else Look.InkFaint
            )
            if (subtitle != null) {
                Spacer(Modifier.height(3.dp))
                Text(subtitle, fontSize = 14.sp, color = Look.InkMuted, lineHeight = 19.sp)
            }
        }
        trailing()
    }
}

@Composable
fun LookDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 72.dp)
            .height(1.dp)
            .background(Look.Line)
    )
}

/** A standing note in the app's shape: tinted, rounded, quiet. */
@Composable
fun LookNote(text: String, color: Color = Look.Warning, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Look.RadiusCard.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(text, fontSize = 13.sp, color = color, lineHeight = 18.sp)
    }
}

/** The one button shape. Filled is the action; outlined is the way back. */
@Composable
fun LookButton(
    label: String,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    small: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Text(
        label,
        color = when {
            !enabled -> Look.InkFaint
            filled -> Look.Canvas
            else -> Look.Ink
        },
        fontSize = if (small) 14.sp else 15.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier
            .clip(RoundedCornerShape(Look.RadiusPill.dp))
            .background(
                when {
                    !enabled -> Look.Fill
                    filled -> Look.Primary
                    else -> Look.Fill
                }
            )
            .let { if (enabled) it.clickable(onClick = onClick) else it }
            .padding(
                horizontal = if (small) 14.dp else 22.dp,
                vertical = if (small) 8.dp else 12.dp
            )
    )
}

/** A small state label, as on the app's cards. */
@Composable
fun LookPill(text: String, color: Color = Look.Primary) {
    Box(
        Modifier
            .clip(RoundedCornerShape(Look.RadiusPill.dp))
            .background(color.copy(alpha = 0.20f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** The search field, one shape wherever something is searched. */
@Composable
fun LookSearch(
    query: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Suchen",
    enabled: Boolean = true,
    onFocus: (() -> Unit)? = null
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Look.RadiusPill.dp))
            .background(Look.Fill)
            .let { if (onFocus != null) it.clickable { onFocus() } else it }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Search, null, tint = Look.InkMuted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(placeholder, color = Look.InkFaint, fontSize = 15.sp)
            }
            if (enabled) {
                BasicTextField(
                    value = query,
                    onValueChange = onChange,
                    singleLine = true,
                    textStyle = TextStyle(color = Look.Ink, fontSize = 15.sp),
                    cursorBrush = SolidColor(Look.Primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** A row in a popup menu. */
@Composable
fun LookMenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Look.InkMuted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = Look.Ink, fontSize = 15.sp)
    }
}

/** The floating panel a menu or a dialog is drawn on. */
@Composable
fun LookPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .padding(32.dp)
            .clip(RoundedCornerShape(Look.RadiusCard.dp))
            .background(Look.Surface)
    ) {
        Column(Modifier.padding(vertical = 8.dp)) { content() }
    }
}
