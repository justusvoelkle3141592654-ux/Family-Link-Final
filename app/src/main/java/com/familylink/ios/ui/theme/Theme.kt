package com.familylink.ios.ui.theme

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Typography
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * "Nova" — the product's own design language.
 *
 * Deliberately NOT an iOS clone: a deep indigo/violet brand core, a mint accent for positive
 * states, generous rounding, layered surfaces and soft brand-tinted gradients. The palette is
 * built around one hue family so the app reads as a single, deliberate product.
 */
object Nova {

    // -- Brand ------------------------------------------------------------
    val Primary = Color(0xFF4C3FE0)        // brand indigo
    val PrimaryDeep = Color(0xFF2D2496)    // pressed / depth
    val PrimarySoft = Color(0xFFEDEBFF)    // tinted container

    val Accent = Color(0xFF00C9A7)         // mint — "you're fine"
    val AccentSoft = Color(0xFFDFF8F3)

    // -- Semantic ---------------------------------------------------------
    val Success = Color(0xFF14B87A)
    val Warning = Color(0xFFFF9F1C)
    val Danger = Color(0xFFF0455F)
    val Night = Color(0xFF7A5CF0)          // bedtime / quiet hours
    val Focus = Color(0xFF2B9BF3)          // focus sessions

    // -- Neutrals ---------------------------------------------------------
    val Ink = Color(0xFF14142B)            // primary text
    val InkMuted = Color(0xFF5A5A78)       // secondary text
    val InkFaint = Color(0xFF9A9AB5)       // tertiary text
    val Line = Color(0x1A14142B)           // hairline
    val Fill = Color(0x0D14142B)           // control fill

    val Canvas = Color(0xFFF6F6FB)         // page background
    val Surface = Color(0xFFFFFFFF)        // cards
    val SurfaceAlt = Color(0xFFFBFBFE)     // nested surfaces

    // -- Gradients --------------------------------------------------------
    val BrandGradient = listOf(Color(0xFF5B4BE8), Color(0xFF8B5CF0))
    val PageGradient = listOf(Color(0xFFFFFFFF), Canvas)
    val NightGradient = listOf(Color(0xFFEFEAFE), Canvas)
    val SuccessGradient = listOf(Color(0xFF14B87A), Color(0xFF00C9A7))

    // -- Category colours (single source of truth) ------------------------
    val CatPlus = Success
    val CatLimit = Warning
    val CatStandard = Primary
    val CatBlocked = Danger

    // -- Radii / elevation tokens ----------------------------------------
    const val RadiusCard = 20
    const val RadiusControl = 14
    const val RadiusPill = 999
}

private val NovaTypography = Typography(
    defaultFontFamily = FontFamily.SansSerif,
    h1 = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, letterSpacing = (-0.5).sp),
    h2 = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = (-0.3).sp),
    h6 = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    body1 = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp),
    body2 = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp),
    caption = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp)
)

@Composable
fun FamilyLinkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = lightColors(
            primary = Nova.Primary,
            primaryVariant = Nova.PrimaryDeep,
            secondary = Nova.Accent,
            background = Nova.Canvas,
            surface = Nova.Surface,
            onBackground = Nova.Ink,
            onSurface = Nova.Ink,
            error = Nova.Danger
        ),
        typography = NovaTypography,
        content = content
    )
}
