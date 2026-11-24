package com.lostf1sh.pixelplayeross.desktop.theme

import androidx.compose.ui.graphics.Color
import com.lostf1sh.pixelplayeross.desktop.contrastRatio
import kotlin.test.*

class ThemePresetTest {

    @Test
    fun allPresetsGenerateValidColorSchemes() {
        for (preset in ThemePreset.entries) {
            val scheme = preset.colorScheme(isDark = true, coverAccent = 0xFF4DA3FF.toInt())
            assertNotNull(scheme.primary)
            assertNotNull(scheme.background)
            assertNotNull(scheme.surface)
            assertNotNull(scheme.onBackground)
            assertNotNull(scheme.onSurface)
        }
    }

    @Test
    fun oledBlackHasPureBlackBackground() {
        val scheme = ThemePreset.OLED_BLACK.colorScheme(isDark = true, coverAccent = null)
        assertEquals(Color(0xFF000000), scheme.background)
    }

    @Test
    fun resolvesPresetsFromIdWithCaseInsensitivityAndFallback() {
        assertEquals(ThemePreset.DRACULA, ThemePreset.fromId("dracula"))
        assertEquals(ThemePreset.NORD, ThemePreset.fromId("NORD"))
        assertEquals(ThemePreset.SYNTHWAVE, ThemePreset.fromId("synthwave"))
        assertEquals(ThemePreset.ROSE_PINE, ThemePreset.fromId("rose_pine"))
        assertEquals(ThemePreset.CATPPUCCIN, ThemePreset.fromId("catppuccin"))
        assertEquals(ThemePreset.LIGHT_MINIMAL, ThemePreset.fromId("light_minimal"))
        assertEquals(ThemePreset.OLED_BLACK, ThemePreset.fromId("oled_black"))
        // Unknown id falls back to DYNAMIC
        assertEquals(ThemePreset.DYNAMIC, ThemePreset.fromId("unknown_custom_id"))
    }

    @Test
    fun dynamicFullscreenAmbientUsesCoverAccent() {
        val coverAccent = 0xFFFF5500.toInt()
        val ambient = ThemeManager.resolveFullPlayerAmbientColors(coverAccent, ThemePreset.DYNAMIC)
        assertEquals(Color(coverAccent), ambient.glowAccent)
        assertEquals(Color(0xFF14171E), ambient.glowBase)
        assertEquals(Color(0xFF0F1117), ambient.deepBackground)
    }

    @Test
    fun nonDynamicFullscreenAmbientUsesThemeColorsInsteadOfCoverAccent() {
        val coverAccent = 0xFFFF5500.toInt()
        for (preset in ThemePreset.entries) {
            if (preset == ThemePreset.DYNAMIC) continue
            val scheme = preset.colorScheme(isDark = true, coverAccent = coverAccent)
            val ambient = ThemeManager.resolveFullPlayerAmbientColors(coverAccent, preset)
            assertEquals(scheme.primary, ambient.glowAccent, "${preset.id} should use theme primary for glowAccent")
            assertEquals(scheme.surface, ambient.glowBase, "${preset.id} should use theme surface for glowBase")
            assertEquals(scheme.background, ambient.deepBackground, "${preset.id} should use theme background for deepBackground")
            assertNotEquals(Color(coverAccent), ambient.glowAccent, "${preset.id} must ignore album cover accent")
        }
    }

    @Test
    fun allPresetsMeetContrastRequirementsInLightAndDarkModes() {
        val coverAccents = listOf<Int?>(null, 0xFF4DA3FF.toInt(), 0xFFFF0000.toInt(), 0xFFFFFF00.toInt())
        for (preset in ThemePreset.entries) {
            for (isDark in listOf(true, false)) {
                for (accent in coverAccents) {
                    val scheme = preset.colorScheme(isDark = isDark, coverAccent = accent)
                    val label = "${preset.id} (dark=$isDark, accent=$accent)"
                    assertTrue(contrastRatio(scheme.onBackground, scheme.background) >= 4.5f, "$label onBackground/background")
                    assertTrue(contrastRatio(scheme.onSurface, scheme.surface) >= 4.5f, "$label onSurface/surface")
                    assertTrue(contrastRatio(scheme.onSurfaceVariant, scheme.surfaceVariant) >= 4.5f, "$label onSurfaceVariant/surfaceVariant")
                    assertTrue(contrastRatio(scheme.onSurface, scheme.surfaceVariant) >= 4.5f, "$label onSurface/surfaceVariant")
                    assertTrue(contrastRatio(scheme.onPrimary, scheme.primary) >= 4.5f, "$label onPrimary/primary")
                    assertTrue(contrastRatio(scheme.onPrimaryContainer, scheme.primaryContainer) >= 4.5f, "$label onPrimaryContainer/primaryContainer")
                    assertTrue(contrastRatio(scheme.primary, scheme.surfaceVariant) >= 3.0f, "$label primary/surfaceVariant")
                }
            }
        }
    }
}
