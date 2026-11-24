package com.lostf1sh.pixelplayeross.desktop.playback

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WindowsMediaSessionTest {

    @Test
    fun testConfigureProcessAndAumidRegistrationSafe() {
        // Calling configureProcess() and ensureAumidRegistration() must not throw
        WindowsMediaSession.configureProcess()
        WindowsMediaSession.ensureAumidRegistration()
    }

    @Test
    fun testAppUserModelIdConstant() {
        val field = WindowsMediaSession::class.java.getDeclaredField("APP_ID")
        field.isAccessible = true
        val appId = field.get(null) as String
        assertEquals("Umimuko.Music", appId, "AUMID must be exactly Umimuko.Music")
    }

    @Test
    fun testExtractAppIconProducesValidFile() {
        val method = WindowsMediaSession::class.java.getDeclaredMethod("extractAppIcon")
        method.isAccessible = true
        val iconPath = method.invoke(null) as? Path
        assertNotNull(iconPath, "extractAppIcon() should successfully locate or extract an icon")
        assertTrue(Files.isRegularFile(iconPath), "Extracted icon file must exist: $iconPath")
        assertTrue(Files.size(iconPath) > 0, "Extracted icon file must not be empty")

        val filename = iconPath.fileName.toString()
        assertTrue(
            filename.endsWith(".ico"),
            "Extracted icon should be a .ico file for Windows shell/taskbar: $filename"
        )
    }

    @Test
    fun testPackagingWindowsAssets() {
        val icoPath = Path.of("packaging", "umimuko.ico")
        assertTrue(Files.isRegularFile(icoPath), "packaging/umimuko.ico must exist")
        assertTrue(Files.size(icoPath) > 1000, "packaging/umimuko.ico must be non-empty")
    }
}
