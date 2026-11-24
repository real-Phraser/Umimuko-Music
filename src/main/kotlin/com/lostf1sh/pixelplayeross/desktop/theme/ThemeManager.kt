package com.lostf1sh.pixelplayeross.desktop.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.lostf1sh.pixelplayeross.desktop.StartupPrefs
import com.lostf1sh.pixelplayeross.desktop.fullPlayerColorScheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FullPlayerAmbientColors(
    val glowAccent: Color,
    val glowBase: Color,
    val deepBackground: Color
)

object ThemeManager {

    private val mutablePreset = MutableStateFlow(ThemePreset.fromId(StartupPrefs.themePreset))
    val currentPreset = mutablePreset.asStateFlow()

    fun setPreset(preset: ThemePreset) {
        mutablePreset.value = preset
        StartupPrefs.themePreset = preset.id
    }

    fun resolveColorScheme(isDark: Boolean, coverAccent: Int?): ColorScheme {
        return mutablePreset.value.colorScheme(isDark, coverAccent)
    }

    fun resolveFullPlayerColorScheme(coverAccent: Int?): ColorScheme {
        return if (mutablePreset.value == ThemePreset.DYNAMIC) {
            fullPlayerColorScheme(coverAccent)
        } else {
            mutablePreset.value.colorScheme(isDark = true, coverAccent = coverAccent)
        }
    }

    fun resolveFullPlayerAmbientColors(
        coverAccent: Int?,
        preset: ThemePreset = mutablePreset.value
    ): FullPlayerAmbientColors {
        return if (preset == ThemePreset.DYNAMIC) {
            FullPlayerAmbientColors(
                glowAccent = Color(coverAccent ?: 0xFF2166C2.toInt()),
                glowBase = Color(0xFF14171E),
                deepBackground = Color(0xFF0F1117)
            )
        } else {
            val scheme = preset.colorScheme(isDark = true, coverAccent = coverAccent)
            FullPlayerAmbientColors(
                glowAccent = scheme.primary,
                glowBase = scheme.surface,
                deepBackground = scheme.background
            )
        }
    }
}
