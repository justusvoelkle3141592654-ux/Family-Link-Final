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

/**
 * iOS passcode UI: a row of filled/empty dots above a 3x4 number pad.
 * [pinLength] dots; fires [onComplete] once [length] digits are entered.
 */
@Composable
fun PinPad(
    entered: String,
    length: Int = 4,
    title: String = "Code eingeben",
    subtitle: String? = null,
    error: Boolean = false,
    dark: Boolean = false,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit
) {
    val fg = if (dark) Color.White else Color(0xFF000000)
    val faint = if (dark) Color(0x33FFFFFF) else Color(0x22000000)

    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, color = fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(subtitle, color = fg.copy(alpha = 0.6f), fontSize = 14.sp)
        }
        Spacer(Modifier.height(28.dp))

        // dots
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            repeat(length) { i ->
                val filled = i < entered.length
                Box(
                    Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .then(
                            if (filled) Modifier.background(if (error) Color(0xFFFF3B30) else fg)
                            else Modifier.border(1.5.dp, fg.copy(alpha = 0.5f), CircleShape)
                        )
                )
            }
        }

        Spacer(Modifier.height(40.dp))

        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("", "0", "⌫")
        )
        rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally)
            ) {
                row.forEach { key ->
                    when (key) {
                        "" -> Spacer(Modifier.size(76.dp))
                        "⌫" -> KeyCircle(label = key, fg = fg, bg = Color.Transparent) { onDelete() }
                        else -> KeyCircle(label = key, fg = fg, bg = faint) { onDigit(key[0]) }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyCircle(label: String, fg: Color, bg: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(76.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = fg, fontSize = 32.sp, fontWeight = FontWeight.Light)
    }
}
