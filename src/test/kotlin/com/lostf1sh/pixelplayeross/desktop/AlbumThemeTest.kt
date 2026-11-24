package com.lostf1sh.pixelplayeross.desktop

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertTrue

class AlbumThemeTest {
    private fun contrast(a: Color, b: Color): Float {
        val x = a.luminance(); val y = b.luminance()
        return (maxOf(x,y)+.05f)/(minOf(x,y)+.05f)
    }
    @Test fun fallbackPaletteIsBlue() {
        for (dark in listOf(true,false)) {
            val primary=albumColorScheme(dark,null).primary
            assertTrue(primary.blue>primary.red,"fallback must be blue")
        }
    }
    @Test fun textStaysReadableAcrossExtremeCoverColors() {
        val seeds = listOf<Int?>(null, 0xFFFF0000.toInt(), 0xFF00FF00.toInt(),
            0xFF0000FF.toInt(), 0xFFFFFF00.toInt(), 0xFF000000.toInt(), 0xFFFFFFFF.toInt())
        for (dark in listOf(true,false)) for (seed in seeds) {
            val scheme = albumColorScheme(dark,seed)
            for ((background,foreground) in listOf(
                scheme.primary to scheme.onPrimary,
                scheme.primaryContainer to scheme.onPrimaryContainer,
                scheme.surface to scheme.onSurface,
                scheme.surfaceVariant to scheme.onSurfaceVariant,
                scheme.surfaceVariant to scheme.onSurface,
                scheme.background to scheme.onBackground,
            )) assertTrue(contrast(background,foreground)>=4.5f,"contrast failed: $dark/$seed ($background vs $foreground)")
            assertTrue(contrast(scheme.primary,scheme.surface)>=4.5f,"accent text on rows")
            assertTrue(contrast(scheme.primary,scheme.surfaceVariant)>=3.0f,"accent icons on player bar: $dark/$seed")
        }
    }

    @Test fun ensureContrastFallsBackWhenRatioIsTooLow() {
        val lowContrast = Color(0xFF6272A4)
        val darkBg = Color(0xFF44475A)
        val fixed = ensureContrast(lowContrast, darkBg, minRatio = 4.5f)
        assertTrue(contrastRatio(fixed, darkBg) >= 4.5f)
        val good = Color(0xFFF8F8F2)
        assertTrue(ensureContrast(good, darkBg, minRatio = 4.5f) == good)
    }
}
