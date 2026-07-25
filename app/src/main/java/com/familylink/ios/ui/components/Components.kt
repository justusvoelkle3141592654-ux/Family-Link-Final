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
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familylink.ios.ui.theme.Nova

/** Primary action: brand gradient, soft brand-tinted shadow, generous rounding. */
@Composable
fun NovaButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
    textColor: Color = Color.White,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val brush = if (color != null) Brush.horizontalGradient(listOf(color, color))
    else Brush.horizontalGradient(Nova.BrandGradient)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .shadow(
                elevation = if (enabled) 10.dp else 0.dp,
                shape = RoundedCornerShape(Nova.RadiusControl.dp),
                ambientColor = (color ?: Nova.Primary).copy(alpha = 0.5f),
                spotColor = (color ?: Nova.Primary).copy(alpha = 0.5f)
            )
            .clip(RoundedCornerShape(Nova.RadiusControl.dp))
            .background(
                if (enabled) brush
                else Brush.horizontalGradient(listOf(Nova.InkFaint, Nova.InkFaint))
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = textColor, fontSize = 17.sp, fontWeight = FontWeight.Bold)
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
            .height(50.dp)
            .clip(RoundedCornerShape(Nova.RadiusControl.dp))
            .background(color.copy(alpha = 0.12f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = color, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Elevated content card. */
@Composable
fun NovaCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(Nova.RadiusCard.dp),
                ambientColor = Nova.Ink.copy(alpha = 0.10f),
                spotColor = Nova.Ink.copy(alpha = 0.10f)
            )
            .clip(RoundedCornerShape(Nova.RadiusCard.dp))
            .background(Nova.Surface)
    ) {
        Column(Modifier.padding(vertical = 4.dp)) { content() }
    }
}

/** List row inside a card. */
@Composable
fun NovaRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable { onClick() } else it }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Nova.Ink)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, fontSize = 13.sp, color = Nova.InkMuted)
            }
        }
        trailing()
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text = text,
        color = Nova.InkMuted,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 24.dp, bottom = 8.dp)
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
