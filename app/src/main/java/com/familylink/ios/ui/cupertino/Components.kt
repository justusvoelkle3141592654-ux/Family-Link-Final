package com.familylink.ios.ui.cupertino

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familylink.ios.ui.theme.Cupertino

/** Filled iOS-style rounded button. */
@Composable
fun CupertinoButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Cupertino.Blue,
    textColor: Color = Color.White,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) color else color.copy(alpha = 0.4f))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = textColor, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Grouped-inset card, as in the iOS Settings app. */
@Composable
fun CupertinoCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Cupertino.SecondaryBackground)
    ) {
        Column(Modifier.padding(vertical = 2.dp)) {
            content()
        }
    }
}

/** A single settings row: title on the left, trailing content on the right. */
@Composable
fun CupertinoRow(
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, color = Cupertino.Label)
            if (subtitle != null) {
                Text(subtitle, fontSize = 13.sp, color = Cupertino.SecondaryLabel)
            }
        }
        trailing()
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        color = Cupertino.SecondaryLabel,
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 6.dp)
    )
}

/** iOS toggle switch. */
@Composable
fun CupertinoSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val bg = if (checked) Cupertino.Green else Color(0xFFE9E9EA)
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
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White)
        )
    }
}

@Composable
fun NavBar(title: String, trailing: @Composable () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().background(Cupertino.SystemBackground)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Cupertino.Label)
            Spacer(Modifier.weight(1f))
            trailing()
        }
    }
}

val screenPadding = PaddingValues(horizontal = 16.dp)
