package com.familylink.ios.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familylink.ios.ui.theme.Nova

/*
 * Nova components — redesigned to the reference layout:
 *   • flat white cards on a single quiet canvas, radius 26
 *   • every list row = glyph on a pale-blue disc + title + explaining line
 *   • pill buttons, pale-blue selection pill in the bottom bar
 *   • a floating round top bar (back / bell / avatar) instead of a title bar
 * Palette comes entirely from Nova — no colours are hard-coded here.
 */

/** Primary action: solid fill, pill shape. */
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
        modifier = modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(50))
            .background(fill).clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (enabled) textColor else Nova.InkFaint, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Secondary action: tinted container. */
@Composable
fun NovaButtonTonal(text: String, modifier: Modifier = Modifier, color: Color = Nova.Primary, onClick: () -> Unit) {
    Box(
        modifier = modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(50))
            .background(if (color == Nova.Primary) Nova.Accent else color.copy(alpha = 0.14f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Text(text, color = color, fontSize = 16.sp, fontWeight = FontWeight.Medium) }
}

/**
 * White action pill on the canvas ("Sperren") — the reference's neutral primary action:
 * white surface, optional leading glyph, near-black label.
 */
@Composable
fun NovaButtonSurface(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(50))
            .background(Nova.Surface).clickable { onClick() },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Nova.Ink, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, color = Nova.Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Content card — flat, no shadow, large radius. */
@Composable
fun NovaCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(Nova.RadiusCard.dp)).background(Nova.Surface)
    ) { Column { content() } }
}

/** List row inside a card: glyph on a pale disc, title, explaining line, optional chevron. */
@Composable
fun NovaRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = Nova.Primary,
    titleColor: Color = Nova.Ink,
    chevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth()
            .let { if (onClick != null) it.clickable { onClick() } else it }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .background(if (iconTint == Nova.Primary) Nova.Accent else iconTint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = titleColor)
            if (subtitle != null) {
                Spacer(Modifier.height(3.dp))
                Text(subtitle, fontSize = 14.sp, color = Nova.InkMuted, lineHeight = 19.sp)
            }
        }
        trailing()
        if (chevron) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Filled.ChevronRight, null, tint = Nova.InkFaint, modifier = Modifier.size(20.dp))
        }
    }
}

/** The hero of the screen-time tab: the number, its caption and the device/app tile. */
@Composable
fun NovaHeroTime(
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
    tileIcon: ImageVector = Icons.Filled.BarChart
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(value, fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Nova.Ink, letterSpacing = (-0.5).sp)
            Spacer(Modifier.height(6.dp))
            Text(caption, fontSize = 15.sp, color = Nova.InkMuted, lineHeight = 20.sp)
        }
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(Nova.BrandGradient)),
            contentAlignment = Alignment.Center
        ) { Icon(tileIcon, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
    }
}

/** Floating top bar: round back button on the left, bell + avatar on the right. */
@Composable
fun NovaTopBar(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onNotifications: (() -> Unit)? = null,
    onProfile: (() -> Unit)? = null
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) RoundIconButton(Icons.Filled.ArrowBackIosNew, onBack)
        Spacer(Modifier.weight(1f))
        if (onNotifications != null) {
            RoundIconButton(Icons.Filled.Notifications, onNotifications)
            Spacer(Modifier.width(12.dp))
        }
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(Brush.linearGradient(Nova.BrandGradient))
                .let { if (onProfile != null) it.clickable { onProfile() } else it }
        )
    }
}

@Composable
private fun RoundIconButton(icon: ImageVector, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(Nova.Surface).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = Nova.Ink, modifier = Modifier.size(17.dp)) }
}

/** One entry of [NovaBottomBar]. */
data class NovaTab(val label: String, val icon: ImageVector)

/**
 * Bottom bar in the reference's shape: a pale-blue pill slides behind the selected glyph,
 * the label under it turns brand blue, everything else stays quiet grey.
 */
@Composable
fun NovaBottomBar(
    tabs: List<NovaTab>,
    current: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.fillMaxWidth().background(Nova.Surface).padding(top = 10.dp, bottom = 22.dp)
    ) {
        tabs.forEachIndexed { index, tab ->
            val selected = index == current
            val source = remember { MutableInteractionSource() }
            Column(
                Modifier.weight(1f)
                    .clickable(interactionSource = source, indication = null) { onSelect(index) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier.width(56.dp).height(28.dp).clip(RoundedCornerShape(Nova.RadiusPill.dp))
                        .background(if (selected) Nova.Accent else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(tab.icon, null, tint = if (selected) Nova.Primary else Nova.InkFaint, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    tab.label,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected) Nova.Primary else Nova.InkFaint
                )
            }
        }
    }
}


/**
 * Feature card as used by ParentPortalScreen: glyph on a tinted disc with the control on the
 * far right, then name + explaining line, then the rows. Unchanged API, new spacing/radius.
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
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(Nova.RadiusCard.dp)).background(Nova.Surface)) {
        Column {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape)
                            .background(if (tint == Nova.Primary) Nova.Accent else tint.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
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

/** A row that states a value rather than offering a control. */
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

/** Hairline between rows — indented to the text, as in the reference list. */
@Composable
fun NovaDivider(inset: Boolean = false) {
    Box(
        Modifier.fillMaxWidth().padding(start = if (inset) 70.dp else 0.dp)
            .height(1.dp).background(Nova.Line)
    )
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text = text, color = Nova.InkMuted, fontSize = 14.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 8.dp, top = 26.dp, bottom = 10.dp)
    )
}

/** Toggle switch in brand colours. */
@Composable
fun NovaSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val bg = if (checked) Nova.Primary else Color(0xFFD9D9E3)
    val offset by animateFloatAsState(if (checked) 22f else 2f, label = "switch")
    Box(
        modifier = Modifier.width(51.dp).height(31.dp).clip(RoundedCornerShape(16.dp))
            .background(bg).clickable { onCheckedChange(!checked) }
    ) {
        Box(
            Modifier.padding(vertical = 2.dp).size(27.dp).offset(x = offset.dp).clip(CircleShape).background(Color.White)
        )
    }
}

/** Small status pill (e.g. "Verbunden", "Gesperrt"). */
@Composable
fun NovaPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(Nova.RadiusPill.dp)).background(color.copy(alpha = 0.13f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) { Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
}

/** Rounded progress bar. */
@Composable
fun NovaProgress(fraction: Float, color: Color, modifier: Modifier = Modifier, barHeight: Int = 8) {
    Box(
        modifier.fillMaxWidth().height(barHeight.dp).clip(RoundedCornerShape(Nova.RadiusPill.dp)).background(Nova.Fill)
    ) {
        Box(
            Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(barHeight.dp)
                .clip(RoundedCornerShape(Nova.RadiusPill.dp)).background(color)
        )
    }
}
