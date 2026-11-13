package com.lostf1sh.pixelplayeross.desktop

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp

/** Tonal palette from the cover hue; black/white foregrounds use the higher contrast. */
fun albumColorScheme(dark: Boolean, accentArgb: Int?): ColorScheme {
    val seed = accentArgb ?: if (dark) 0xFF9CCAFF.toInt() else 0xFF2166C2.toInt()
    val hsv = java.awt.Color.RGBtoHSB((seed shr 16) and 255, (seed shr 8) and 255, seed and 255, null)
    fun tone(saturation: Float, value: Float) = Color(java.awt.Color.HSBtoRGB(hsv[0], saturation, value))
    val neutralSaturation = hsv[1].coerceAtMost(.18f)
    val primary = tone(if (dark) hsv[1].coerceAtMost(.45f) else hsv[1].coerceAtMost(.75f), if (dark) .98f else .43f)
    val background = tone(neutralSaturation, if (dark) .075f else .99f)
    val surface = tone(neutralSaturation, if (dark) .11f else .97f)
    val variant = tone(neutralSaturation, if (dark) .17f else .91f)
    val container = tone(hsv[1].coerceAtMost(.32f), if (dark) .24f else .88f)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary, onPrimary = contrastingText(primary),
        primaryContainer = container, onPrimaryContainer = contrastingText(container),
        secondary = primary, onSecondary = contrastingText(primary),
        background = background, onBackground = contrastingText(background),
        surface = surface, onSurface = contrastingText(surface),
        surfaceVariant = variant, onSurfaceVariant = contrastingText(variant).copy(alpha = .82f),
        secondaryContainer = container, onSecondaryContainer = contrastingText(container),
    )
}

/** A quieter palette for controls and dialogs inside the full-screen window. */
fun fullPlayerColorScheme(accentArgb: Int?): ColorScheme {
    val original=albumColorScheme(true,accentArgb)
    val accent=lerp(Color(0xFFB6C4DA),original.primary,.38f)
    val container=lerp(Color(0xFF2A303A),original.primaryContainer,.30f)
    return original.copy(primary=accent,onPrimary=contrastingText(accent),secondary=accent,
        primaryContainer=container,onPrimaryContainer=contrastingText(container),
        secondaryContainer=container,onSecondaryContainer=contrastingText(container))
}

fun contrastingText(background: Color): Color {
    val luminance = background.luminance()
    return if ((luminance + .05f) / .05f > 1.05f / (luminance + .05f)) Color.Black else Color.White
}
