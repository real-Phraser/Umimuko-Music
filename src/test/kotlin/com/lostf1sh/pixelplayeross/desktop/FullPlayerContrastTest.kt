package com.lostf1sh.pixelplayeross.desktop

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.lostf1sh.pixelplayeross.desktop.theme.FullPlayerAmbientColors
import com.lostf1sh.pixelplayeross.desktop.theme.ThemeManager
import com.lostf1sh.pixelplayeross.desktop.theme.ThemePreset
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FullPlayerContrastTest {

    @Test
    fun allThemePresetsHaveReadableContrastAgainstFullscreenBackground() {
        val sampleAccents = listOf(null, 0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFEB3B.toInt(), 0xFF2166C2.toInt())
        for (preset in ThemePreset.entries) {
            for (accent in sampleAccents) {
                val ambient = ThemeManager.resolveFullPlayerAmbientColors(accent, preset)
                val baseScheme = if (preset == ThemePreset.DYNAMIC) {
                    fullPlayerColorScheme(accent)
                } else {
                    preset.colorScheme(isDark = true, coverAccent = accent)
                }
                val resolved = resolveFullPlayerContrastColors(baseScheme, ambient)
                val backgrounds = listOf(
                    ambient.deepBackground,
                    lerp(ambient.glowBase, ambient.glowAccent, 0.38f)
                )

                for (bg in backgrounds) {
                    assertTrue(
                        fullPlayerContrastRatio(resolved.onBackground, bg) >= 4.5f,
                        "${preset.id} (accent=$accent) onBackground contrast too low"
                    )
                    assertTrue(
                        fullPlayerContrastRatio(resolved.onSurface, bg) >= 4.5f,
                        "${preset.id} (accent=$accent) onSurface contrast too low"
                    )
                    assertTrue(
                        fullPlayerContrastRatio(resolved.onSurfaceVariant, bg) >= 4.5f,
                        "${preset.id} (accent=$accent) onSurfaceVariant contrast too low"
                    )
                    assertTrue(
                        fullPlayerContrastRatio(resolved.primary, bg) >= 4.5f,
                        "${preset.id} (accent=$accent) primary contrast too low"
                    )
                    assertTrue(
                        fullPlayerContrastRatio(resolved.error, bg) >= 4.5f,
                        "${preset.id} (accent=$accent) error contrast too low"
                    )
                }
                assertTrue(
                    fullPlayerContrastRatio(resolved.onPrimary, resolved.primary) >= 4.5f,
                    "${preset.id} onPrimary contrast against primary too low"
                )
                assertTrue(
                    fullPlayerContrastRatio(resolved.onPrimaryContainer, resolved.primaryContainer) >= 4.5f,
                    "${preset.id} onPrimaryContainer contrast against primaryContainer too low"
                )
            }
        }
    }

    @Test
    fun lightThemeTextPairedWithDarkAmbientBackgroundIsCorrectedToHighContrastLightText() {
        val darkAmbient = FullPlayerAmbientColors(
            glowAccent = Color(0xFF2166C2),
            glowBase = Color(0xFF14171E),
            deepBackground = Color(0xFF0F1117)
        )
        val mismatchedLightScheme = lightColorScheme(
            primary = Color(0xFF1A73E8),
            onBackground = Color(0xFF1C1B1F),
            onSurface = Color(0xFF1C1B1F),
            onSurfaceVariant = Color(0xFF49454F)
        )
        val resolved = resolveFullPlayerContrastColors(mismatchedLightScheme, darkAmbient)

        assertTrue(fullPlayerContrastRatio(resolved.onBackground, darkAmbient.deepBackground) >= 4.5f)
        assertTrue(fullPlayerContrastRatio(resolved.onSurface, darkAmbient.deepBackground) >= 4.5f)
        assertTrue(fullPlayerContrastRatio(resolved.onSurfaceVariant, darkAmbient.deepBackground) >= 4.5f)
        assertTrue(fullPlayerContrastRatio(resolved.primary, darkAmbient.deepBackground) >= 4.5f)
    }

    @Test
    fun darkThemeTextPairedWithLightAmbientBackgroundIsCorrectedToHighContrastDarkText() {
        val lightAmbient = FullPlayerAmbientColors(
            glowAccent = Color(0xFF1A73E8),
            glowBase = Color(0xFFFFFFFF),
            deepBackground = Color(0xFFF8F9FA)
        )
        val mismatchedDarkScheme = darkColorScheme(
            primary = Color(0xFF9ECAFF),
            onBackground = Color(0xFFE6E1E5),
            onSurface = Color(0xFFE6E1E5),
            onSurfaceVariant = Color(0xFFCAC4D0)
        )
        val resolved = resolveFullPlayerContrastColors(mismatchedDarkScheme, lightAmbient)
        val glowBg = lerp(lightAmbient.glowBase, lightAmbient.glowAccent, 0.38f)

        assertTrue(fullPlayerContrastRatio(resolved.onBackground, lightAmbient.deepBackground) >= 4.5f)
        assertTrue(fullPlayerContrastRatio(resolved.onBackground, glowBg) >= 4.5f)
        assertTrue(fullPlayerContrastRatio(resolved.onSurfaceVariant, glowBg) >= 4.5f)
        assertTrue(fullPlayerContrastRatio(resolved.primary, glowBg) >= 4.5f)
    }

    @Test
    fun detectsDarkVsLightFullscreenBackground() {
        assertTrue(isDarkFullPlayerBackground(ThemeManager.resolveFullPlayerAmbientColors(null, ThemePreset.DYNAMIC)))
        assertTrue(isDarkFullPlayerBackground(ThemeManager.resolveFullPlayerAmbientColors(null, ThemePreset.DRACULA)))
        assertFalse(isDarkFullPlayerBackground(ThemeManager.resolveFullPlayerAmbientColors(null, ThemePreset.LIGHT_MINIMAL)))
    }
}
