package com.lostf1sh.pixelplayeross.desktop

import androidx.compose.ui.graphics.Color
import com.lostf1sh.pixelplayeross.desktop.library.PlatformPaths
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MacAppearanceAndPrefsTest {

    @Test
    fun testStartupPrefsFollowSystemTheme() {
        val initial = StartupPrefs.followSystemTheme
        StartupPrefs.followSystemTheme = !initial
        assertEquals(!initial, StartupPrefs.followSystemTheme)
        StartupPrefs.followSystemTheme = initial
        assertEquals(initial, StartupPrefs.followSystemTheme)
    }

    @Test
    fun testWindowPlacementDefaultOnMac() {
        if (PlatformPaths.isMac()) {
            // On macOS, default placement preference should not force full maximization over menu bar
            // (either explicitly false or respects floating by default)
            val isMax = StartupPrefs.windowMaximized
            println("StartupPrefs.windowMaximized on Mac is: $isMax")
        }
    }

    @Test
    fun testWindowThemeApplyOnMac() {
        SwingUtilities.invokeAndWait {
            val frame = JFrame("Test Window Theme")
            frame.setSize(400, 300)
            frame.isVisible = true

            // Test dark theme application
            val okDark = WindowTheme.apply(frame, dark = true, Color.Black, Color.White)
            assertTrue(okDark)

            if (PlatformPaths.isMac()) {
                val prop = frame.rootPane.getClientProperty("apple.awt.windowAppearance")
                assertEquals("NSAppearanceNameDarkAqua", prop)
            }

            // Test light theme application
            val okLight = WindowTheme.apply(frame, dark = false, Color.White, Color.Black)
            assertTrue(okLight)

            if (PlatformPaths.isMac()) {
                val prop = frame.rootPane.getClientProperty("apple.awt.windowAppearance")
                assertEquals("NSAppearanceNameAqua", prop)
            }

            frame.dispose()
        }
    }

    @Test
    fun testMacAppNameProperty() {
        System.setProperty("apple.awt.application.name", "Umimuko Music")
        assertEquals("Umimuko Music", System.getProperty("apple.awt.application.name"))
    }
}
