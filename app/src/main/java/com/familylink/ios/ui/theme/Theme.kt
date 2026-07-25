package com.familylink.ios.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Apple system palette (iOS 17-ish). */
object Cupertino {
    val Blue = Color(0xFF0A84FF)
    val Green = Color(0xFF34C759)
    val Red = Color(0xFFFF3B30)
    val Orange = Color(0xFFFF9500)
    val Yellow = Color(0xFFFFCC00)
    val Purple = Color(0xFFAF52DE)

    val Label = Color(0xFF000000)
    val SecondaryLabel = Color(0x993C3C43)
    val TertiaryLabel = Color(0x4D3C3C43)

    val Teal = Color(0xFF5AC8FA)
    val Indigo = Color(0xFF5856D6)
    val Pink = Color(0xFFFF2D55)

    val SystemBackground = Color(0xFFF2F2F7)      // grouped background
    val SecondaryBackground = Color(0xFFFFFFFF)   // cards
    val Separator = Color(0x5C3C3C43)
    val Fill = Color(0x14000000)                  // subtle control fill

    // Dark variants (used by the lock overlay, which is always dark for an iOS look).
    val DarkBackground = Color(0xFF000000)
    val DarkLabel = Color(0xFFFFFFFF)
    val DarkSecondaryLabel = Color(0x99EBEBF5)

    /** Soft top-to-bottom page gradient used on the main screens. */
    val PageGradient = listOf(Color(0xFFFFFFFF), SystemBackground)
}

private val CupertinoTypography = Typography(
    defaultFontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
    h1 = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.Bold, fontSize = 34.sp),
    h2 = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp),
    h6 = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    body1 = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.Normal, fontSize = 17.sp),
    body2 = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.Normal, fontSize = 15.sp),
    caption = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.Normal, fontSize = 13.sp)
)

@Composable
fun FamilyLinkTheme(content: @Composable () -> Unit) {
    // We render a fixed light "iOS Settings" look regardless of system dark mode for the
    // parent UI; the lock overlay styles itself dark independently.
    @Suppress("UNUSED_EXPRESSION")
    isSystemInDarkTheme()
    MaterialTheme(
        colors = androidx.compose.material.lightColors(
            primary = Cupertino.Blue,
            background = Cupertino.SystemBackground,
            surface = Cupertino.SecondaryBackground,
            onBackground = Cupertino.Label,
            onSurface = Cupertino.Label,
            error = Cupertino.Red
        ),
        typography = CupertinoTypography,
        content = content
    )
}
