package com.lostf1sh.pixelplayeross.desktop.theme

import androidx.compose.material3.ColorScheme
import com.lostf1sh.pixelplayeross.desktop.StartupPrefs
import com.lostf1sh.pixelplayeross.desktop.fullPlayerColorScheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

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
}
