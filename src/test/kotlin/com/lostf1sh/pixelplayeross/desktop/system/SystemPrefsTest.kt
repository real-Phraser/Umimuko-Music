package com.lostf1sh.pixelplayeross.desktop.system

import com.lostf1sh.pixelplayeross.desktop.StartupPrefs
import kotlin.test.Test
import kotlin.test.assertEquals

class SystemPrefsTest {

    @Test
    fun testMinimizeToTrayPreference() {
        val original = StartupPrefs.minimizeToTray
        try {
            StartupPrefs.minimizeToTray = true
            assertEquals(true, StartupPrefs.minimizeToTray)

            StartupPrefs.minimizeToTray = false
            assertEquals(false, StartupPrefs.minimizeToTray)
        } finally {
            StartupPrefs.minimizeToTray = original
        }
    }

    @Test
    fun testCloseToTrayPreference() {
        val original = StartupPrefs.closeToTray
        try {
            StartupPrefs.closeToTray = true
            assertEquals(true, StartupPrefs.closeToTray)

            StartupPrefs.closeToTray = false
            assertEquals(false, StartupPrefs.closeToTray)
        } finally {
            StartupPrefs.closeToTray = original
        }
    }

    @Test
    fun testAudioDspAndEqualizerPersistence() {
        val origEqEnabled = StartupPrefs.eqEnabled
        val origEqPreset = StartupPrefs.eqPreset
        val origEqPreamp = StartupPrefs.eqPreamp
        val origBands = StartupPrefs.eqCustomBands
        val origSpeed = StartupPrefs.playbackSpeed
        val origCrossfade = StartupPrefs.crossfadeDuration
        val origCrossfadeCurve = StartupPrefs.crossfadeCurve

        try {
            StartupPrefs.eqEnabled = true
            assertEquals(true, StartupPrefs.eqEnabled)

            StartupPrefs.eqPreset = "Rock"
            assertEquals("Rock", StartupPrefs.eqPreset)

            StartupPrefs.eqPreamp = 3.5f
            assertEquals(3.5f, StartupPrefs.eqPreamp)

            StartupPrefs.eqCustomBands = "1.0,2.0,3.0,4.0,5.0,6.0,7.0,8.0,9.0,10.0"
            assertEquals("1.0,2.0,3.0,4.0,5.0,6.0,7.0,8.0,9.0,10.0", StartupPrefs.eqCustomBands)

            StartupPrefs.playbackSpeed = 1.25f
            assertEquals(1.25f, StartupPrefs.playbackSpeed)

            StartupPrefs.crossfadeDuration = 4.5f
            assertEquals(4.5f, StartupPrefs.crossfadeDuration)

            StartupPrefs.crossfadeCurve = "LINEAR"
            assertEquals("LINEAR", StartupPrefs.crossfadeCurve)
        } finally {
            StartupPrefs.eqEnabled = origEqEnabled
            StartupPrefs.eqPreset = origEqPreset
            StartupPrefs.eqPreamp = origEqPreamp
            StartupPrefs.eqCustomBands = origBands
            StartupPrefs.playbackSpeed = origSpeed
            StartupPrefs.crossfadeDuration = origCrossfade
            StartupPrefs.crossfadeCurve = origCrossfadeCurve
        }
    }

    @Test
    fun testFollowSystemThemeFlowReactiveness() {
        val orig = StartupPrefs.followSystemTheme
        try {
            StartupPrefs.followSystemTheme = false
            assertEquals(false, StartupPrefs.followSystemThemeFlow.value)

            StartupPrefs.followSystemTheme = true
            assertEquals(true, StartupPrefs.followSystemThemeFlow.value)
        } finally {
            StartupPrefs.followSystemTheme = orig
        }
    }
}
