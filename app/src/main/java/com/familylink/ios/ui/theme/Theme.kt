package com.familylink.ios.ui.theme

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Typography
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * "Nova" — the product's own design language, in light and dark.
 *
 * A cool electric-blue brand core on deep near-black surfaces (dark) or clean white (light),
 * with vivid semantic accents. Values are swapped by [FamilyLinkTheme] and the whole tree is
 * re-keyed on change, so every screen picks up the new mode instantly.
 */
object Nova {

    // -- Brand (identical in both modes so the product stays recognisable) --
    var Primary = Color(0xFF2E7BFF)          // electric blue
        internal set
    var PrimaryDeep = Color(0xFF1B4FD8)
        internal set
    var Accent = Color(0xFF00D2C3)           // cyan-mint
        internal set

    // -- Semantic ---------------------------------------------------------
    var Success = Color(0xFF146C2E); internal set   // Google green
    var Warning = Color(0xFFA8500A); internal set
    var Danger = Color(0xFFB3261E); internal set    // Material error red
    var Night = Color(0xFF6750A4); internal set     // bedtime
    var Focus = Color(0xFF0B57D0); internal set     // focus sessions

    // -- Neutrals (mode dependent) ----------------------------------------
    var Ink = Color(0xFF0B1020); internal set        // primary text
    var InkMuted = Color(0xFF5B6478); internal set   // secondary text
    var InkFaint = Color(0xFF9AA3B5); internal set   // tertiary text
    var Line = Color(0x14000000); internal set
    var Fill = Color(0x0F000000); internal set

    var Canvas = Color(0xFFF5F7FB); internal set     // page background
    var Surface = Color(0xFFFFFFFF); internal set    // cards
    var SurfaceAlt = Color(0xFFEFF3FA); internal set // nested surfaces

    // -- Gradients --------------------------------------------------------
    var BrandGradient = listOf(Color(0xFF2E7BFF), Color(0xFF00B4FF)); internal set
    var PageGradient = listOf(Color(0xFFFFFFFF), Color(0xFFF5F7FB)); internal set
    var HeroGradient = listOf(Color(0xFF1B4FD8), Color(0xFF2E7BFF), Color(0xFF00B4FF)); internal set

    /** True while the dark palette is active (for mode-aware one-offs). */
    var isDark = false
        internal set

    // -- Category colours -------------------------------------------------
    val CatPlus get() = Success
    val CatLimit get() = Warning
    val CatStandard get() = Primary
    val CatBlocked get() = Danger

    // -- Shape tokens -----------------------------------------------------
    const val RadiusCard = 26
    const val RadiusControl = 20
    const val RadiusPill = 999

    internal fun applyLight() {
        isDark = false
        // Near-black text on a very light blue-grey page with white cards, and quiet greys for
        // subtitles — the Material 3 neutrals Family Link is built on.
        Ink = Color(0xFF1B1C1E)
        InkMuted = Color(0xFF44474E)
        InkFaint = Color(0xFF74777F)
        Line = Color(0x1A000000)
        Fill = Color(0xFFE8EBF3)
        Canvas = Color(0xFFF1F3F9)
        Surface = Color(0xFFFFFFFF)
        SurfaceAlt = Color(0xFFE8EEFB)      // pale blue behind leading icons
        // Flat, not gradient: these screens are a single quiet tone end to end.
        PageGradient = listOf(Color(0xFFF1F3F9), Color(0xFFF1F3F9))
        Primary = Color(0xFF0B57D0)         // Google blue
        PrimaryDeep = Color(0xFF062E6F)
        Accent = Color(0xFFD3E3FD)          // light-blue selection pill
        BrandGradient = listOf(Color(0xFF0B57D0), Color(0xFF4285F4))
        HeroGradient = listOf(Color(0xFF0B57D0), Color(0xFF1A73E8), Color(0xFF4285F4))
    }

    internal fun applyDark() {
        isDark = true
        Ink = Color(0xFFE3E2E6)
        InkMuted = Color(0xFFC4C6D0)
        InkFaint = Color(0xFF8E9099)
        Line = Color(0x1FFFFFFF)
        Fill = Color(0xFF2B2F36)
        Canvas = Color(0xFF111318)
        Surface = Color(0xFF1D2024)
        SurfaceAlt = Color(0xFF283041)
        PageGradient = listOf(Color(0xFF111318), Color(0xFF111318))
        Primary = Color(0xFFA8C7FA)         // Material 3 dark primary
        PrimaryDeep = Color(0xFFD3E3FD)
        Accent = Color(0xFF0842A0)
        BrandGradient = listOf(Color(0xFF0B57D0), Color(0xFF4285F4))
        HeroGradient = listOf(Color(0xFF062E6F), Color(0xFF0B57D0), Color(0xFF1A73E8))
    }
}

/** User-selectable appearance. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val NovaTypography = Typography(
    defaultFontFamily = FontFamily.SansSerif,
    h1 = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, letterSpacing = (-0.6).sp),
    h2 = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = (-0.4).sp),
    h6 = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    body1 = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp),
    body2 = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp),
    caption = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp)
)

@Composable
fun FamilyLinkTheme(dark: Boolean = false, content: @Composable () -> Unit) {
    if (dark) Nova.applyDark() else Nova.applyLight()

    // Re-key so every screen recomposes with the new palette the moment the mode flips.
    key(dark) {
        MaterialTheme(
            colors = if (dark) darkColors(
                primary = Nova.Primary,
                primaryVariant = Nova.PrimaryDeep,
                secondary = Nova.Accent,
                background = Nova.Canvas,
                surface = Nova.Surface,
                onBackground = Nova.Ink,
                onSurface = Nova.Ink,
                error = Nova.Danger
            ) else lightColors(
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
}
