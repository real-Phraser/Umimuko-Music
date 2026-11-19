package com.lostf1sh.pixelplayeross.desktop.theme

import androidx.compose.ui.graphics.Color
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
}
