package com.shiostudios.dumplingrings.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.shiostudios.dumplingrings.R

object DR {
    val Wood = Color(0xFFC98A4B); val WoodDark = Color(0xFF8B5A2B); val WoodDeep = Color(0xFF5A3A28)
    val Cream = Color(0xFFFFF4E3); val CreamDark = Color(0xFFF1E0C8); val Sakura = Color(0xFFF6A7C1); val SakuraDeep = Color(0xFFE2708F)
    val Bamboo = Color(0xFF7FA865); val Gold = Color(0xFFFFD88A); val GoldDeep = Color(0xFFE0A93A)
    val Indigo = Color(0xFF26305E); val IndigoDeep = Color(0xFF141A3A); val Amber = Color(0xFFFFB347); val Red = Color(0xFFD9453B)
    val MoonBlue = Color(0xFF3F5C8C); val Jade = Color(0xFF5FB89A); val Lavender = Color(0xFFB9A5D9); val Snow = Color(0xFFF4F6FF)
    val Ink = Color(0xFF3B2A1E); val InkSoft = Color(0xFF6B5340)
    val StarOn = Color(0xFFFFC53D); val StarOff = Color(0x66FFFFFF)

    // ring colour ids for colour gates (also distinguishable by pattern in the renderer)
    val gateRed = Color(0xFFE05A4E); val gateJade = Color(0xFF4FB592); val gateUbe = Color(0xFF9B7BD0)
    fun gateColor(id: String?): Color = when (id) { "red" -> gateRed; "jade" -> gateJade; "ube" -> gateUbe; else -> WoodDeep }
}

/** Per-world accent palette for the UI chrome. */
data class WorldPalette(val primary: Color, val accent: Color, val bg: Color, val dark: Color, val onDark: Color = Color.White)

val LocalWorldPalette = staticCompositionLocalOf { WorldPalette(DR.Wood, DR.Sakura, DR.Cream, DR.WoodDeep) }
val LocalReduceMotion = staticCompositionLocalOf { false }
val LocalHighContrast = staticCompositionLocalOf { false }

val Baloo = FontFamily(Font(R.font.baloo2, FontWeight.Normal), Font(R.font.baloo2, FontWeight.Bold), Font(R.font.baloo2, FontWeight.ExtraBold))
val Nunito = FontFamily(Font(R.font.nunito, FontWeight.Normal), Font(R.font.nunito, FontWeight.SemiBold), Font(R.font.nunito, FontWeight.Bold))

val DRTypography = Typography(
    displayLarge = TextStyle(fontFamily = Baloo, fontWeight = FontWeight.ExtraBold, fontSize = 44.sp, lineHeight = 48.sp),
    displayMedium = TextStyle(fontFamily = Baloo, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = Baloo, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 30.sp),
    headlineSmall = TextStyle(fontFamily = Baloo, fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontFamily = Baloo, fontWeight = FontWeight.Bold, fontSize = 19.sp),
    titleMedium = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = Baloo, fontWeight = FontWeight.Bold, fontSize = 16.sp),
    labelMedium = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 12.sp),
)

private val scheme: ColorScheme = lightColorScheme(
    primary = DR.Wood, onPrimary = Color.White, secondary = DR.Sakura, onSecondary = DR.Ink,
    background = DR.Cream, onBackground = DR.Ink, surface = DR.Cream, onSurface = DR.Ink, tertiary = DR.Bamboo,
)

@Composable
fun DumplingRingsTheme(content: @Composable () -> Unit) {
    isSystemInDarkTheme() // the game has its own art-directed palette per world; system dark mode is not applied
    MaterialTheme(colorScheme = scheme, typography = DRTypography, content = content)
}
