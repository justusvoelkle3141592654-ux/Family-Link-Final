package com.familylink.ios.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familylink.ios.ui.theme.Nova

/** Primary action: solid fill, generous rounding, flat like the rest of the surface. */
@Composable
fun NovaButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
    textColor: Color = Color.White,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val fill = if (enabled) (color ?: Nova.Primary) else Nova.Fill
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            // Fully round: Material 3 buttons are pills, not rounded rectangles.
            .clip(RoundedCornerShape(50))
            .background(fill)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (enabled) textColor else Nova.InkFaint,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/** Secondary action: tinted container, no fill. */
@Composable
fun NovaButtonTonal(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Nova.Primary,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(50))
            .background(if (color == Nova.Primary) Nova.Accent else color.copy(alpha = 0.14f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = color, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * Content card.
 *
 * Flat on purpose — no drop shadow. The reference design separates cards from the page by
 * colour and a large corner radius alone, and a shadow under every card makes a list of them
 * look muddy.
 */
@Composable
fun NovaCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Nova.RadiusCard.dp))
            .background(Nova.Surface)
    ) {
        Column(Modifier.padding(vertical = 4.dp)) { content() }
    }
}

/**
 * List row inside a card.
 *
 * With [icon] it takes the reference layout: a glyph on a pale blue disc, the title in near
 * black and a grey explanatory line beneath it.
 */
@Composable
fun NovaRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    /** The reference colours a row's glyph by state — red while downtime is running. */
    iconTint: Color = Nova.Primary,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable { onClick() } else it }
            // Taller than a plain list row: the reference gives every entry room to breathe,
            // which is most of what makes the page look like Family Link rather than a table.
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .background(if (iconTint == Nova.Primary) Nova.Accent else iconTint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Nova.Ink)
            if (subtitle != null) {
                Spacer(Modifier.height(3.dp))
                Text(subtitle, fontSize = 14.sp, color = Nova.InkMuted, lineHeight = 19.sp)
            }
        }
        trailing()
    }
}

/**
 * The card the reference builds its settings out of.
 *
 * Its top line carries the glyph on a tinted disc and, on the far right, whatever controls the
 * feature — a switch or a chevron. The name and the sentence explaining it sit underneath,
 * across the full width, and only then come the rows. Nothing is squeezed onto one line.
 *
 * [content] is drawn only when [expanded]; a feature that is switched off collapses to its
 * header, exactly as it does in the reference.
 */
@Composable
fun NovaFeatureCard(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    tint: Color = Nova.Primary,
    expanded: Boolean = true,
    control: @Composable () -> Unit = {},
    content: @Composable () -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Nova.RadiusCard.dp))
            .background(Nova.Surface)
    ) {
        Column {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    control()
                }
                Spacer(Modifier.height(14.dp))
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Nova.Ink)
                if (description.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(description, fontSize = 14.sp, color = Nova.InkMuted, lineHeight = 19.sp)
                }
            }
            if (expanded) content()
        }
    }
}

/** A row that states a value rather than offering a control: "Heute — 1 Std 30 Min". */
@Composable
fun NovaValueRow(label: String, value: String, valueColor: Color = Nova.Ink) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 15.sp, color = Nova.Ink, modifier = Modifier.weight(1f))
        Text(value, fontSize = 15.sp, color = valueColor)
    }
}

/** Hairline between rows, matching the reference's very light dividers. */
@Composable
fun NovaDivider() {
    // Edge to edge, exactly as in the reference: its lists separate rows across the whole card
    // rather than indenting the line to the text.
    Box(Modifier.fillMaxWidth().height(1.dp).background(Nova.Line))
}

/**
 * A short standing note — a warning, a hint, a waiting-for-data message.
 *
 * These used to be written as a bare paragraph wherever one was needed, which is why they read
 * as leftover text rather than part of the screen. Giving them one tinted, rounded shape means
 * a notice looks the same everywhere and is told apart from ordinary content at a glance.
 */
@Composable
fun NovaNote(
    text: String,
    color: Color = Nova.Warning,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Nova.RadiusCard.dp))
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(text, fontSize = 13.sp, color = color, lineHeight = 18.sp)
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text = text,
        color = Nova.InkMuted,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 8.dp, top = 26.dp, bottom = 10.dp)
    )
}

/** Toggle switch in brand colours. */
@Composable
fun NovaSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val bg = if (checked) Nova.Success else Nova.InkFaint.copy(alpha = 0.35f)
    val offset by animateFloatAsState(if (checked) 22f else 2f, label = "switch")
    Box(
        modifier = Modifier
            .width(51.dp)
            .height(31.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable { onCheckedChange(!checked) }
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = 2.dp)
                .size(27.dp)
                .offset(x = offset.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

/** Small status pill (e.g. "Verbunden", "Gesperrt"). */
@Composable
fun NovaPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(Nova.RadiusPill.dp))
            .background(color.copy(alpha = 0.13f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Rounded progress bar. */
@Composable
fun NovaProgress(fraction: Float, color: Color, modifier: Modifier = Modifier, barHeight: Int = 8) {
    Box(
        modifier
            .fillMaxWidth()
            .height(barHeight.dp)
            .clip(RoundedCornerShape(Nova.RadiusPill.dp))
            .background(Nova.Fill)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(barHeight.dp)
                .clip(RoundedCornerShape(Nova.RadiusPill.dp))
                .background(color)
        )
    }
}
