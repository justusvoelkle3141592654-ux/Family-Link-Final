package com.familylink.ios.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familylink.ios.ui.theme.Nova

/**
 * Variable-length secure PIN entry with a confirm button, used for the longer parent PIN that
 * unlocks time extensions. Masks the entered digits and only enables OK at [minLength].
 */
@Composable
fun SecurePinPad(
    entered: String,
    title: String,
    subtitle: String,
    minLength: Int,
    error: Boolean = false,
    confirmLabel: String = "OK",
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onConfirm: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, color = Nova.Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            if (error) "Falsche PIN" else subtitle,
            color = if (error) Nova.Danger else Nova.InkMuted, fontSize = 14.sp
        )
        Spacer(Modifier.height(24.dp))

        // masked dots grow with the entry
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val shown = entered.length.coerceAtMost(12)
            repeat(shown) {
                Box(
                    Modifier.size(14.dp).clip(CircleShape)
                        .background(if (error) Nova.Danger else Nova.Ink)
                )
            }
            if (shown == 0) {
                Box(Modifier.size(14.dp).clip(CircleShape).border(1.5.dp, Nova.InkFaint, CircleShape))
            }
        }

        Spacer(Modifier.height(28.dp))

        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("", "0", "⌫")
        )
        rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally)
            ) {
                row.forEach { key ->
                    when (key) {
                        "" -> Spacer(Modifier.size(70.dp))
                        "⌫" -> Key(key, Color.Transparent) { onDelete() }
                        else -> Key(key, Nova.Fill) { onDigit(key[0]) }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        NovaButton(
            text = confirmLabel,
            color = Nova.Success,
            enabled = entered.length >= minLength,
            onClick = onConfirm
        )
    }
}

@Composable
private fun Key(label: String, bg: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(70.dp).clip(CircleShape).background(bg).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Nova.Ink, fontSize = 30.sp, fontWeight = FontWeight.Light)
    }
}
