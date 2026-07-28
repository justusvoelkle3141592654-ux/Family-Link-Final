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
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable { onClick() } else it }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Nova.SurfaceAlt),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = Nova.Primary, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Nova.Ink)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, fontSize = 13.sp, color = Nova.InkMuted, lineHeight = 17.sp)
            }
        }
        trailing()
    }
}

/** Hairline between rows, matching the reference's very light dividers. */
@Composable
fun NovaDivider() {
    Box(
        Modifier.fillMaxWidth().padding(start = 70.dp).height(1.dp).background(Nova.Line)
    )
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
    val bg = if (checked) Nova.Success else Color(0xFFD9D9E3)
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
