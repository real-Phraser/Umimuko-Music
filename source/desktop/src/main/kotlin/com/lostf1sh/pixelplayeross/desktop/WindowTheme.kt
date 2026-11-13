package com.lostf1sh.pixelplayeross.desktop

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.lostf1sh.pixelplayeross.desktop.library.PlatformPaths
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference

interface DwmApi : Library {
    fun DwmSetWindowAttribute(hwnd: Pointer, attribute: Int, value: IntByReference, size: Int): Int
}

interface User32Api : Library {
    fun SetWindowPos(hwnd: Pointer, insertAfter: Pointer?, x: Int, y: Int, cx: Int, cy: Int, flags: Int): Boolean
}

/**
 * Makes the native window title bar follow the app theme: dark/light caption (Windows 10 2004+ and macOS),
 * and on Windows 11 the exact caption and text colors. Anything that is unsupported is ignored.
 */
object WindowTheme {
    private val windows = System.getProperty("os.name").orEmpty().startsWith("Windows")
    private val dwm: DwmApi? = if (windows) try { Native.load("dwmapi", DwmApi::class.java) } catch (e: Throwable) { null } else null
    private val user32: User32Api? = if (windows) try { Native.load("user32", User32Api::class.java) } catch (e: Throwable) { null } else null

    private fun colorRef(color: Color): Int {
        val argb = color.toArgb()
        return ((argb and 0xFF) shl 16) or (((argb shr 8) and 0xFF) shl 8) or ((argb shr 16) and 0xFF)
    }

    /** Returns true when the colors were applied (or there is nothing to do on this system). */
    fun apply(window: java.awt.Window, dark: Boolean, background: Color, text: Color): Boolean {
        if (PlatformPaths.isMac()) {
            return try {
                val rootPane = (window as? javax.swing.RootPaneContainer)?.rootPane
                val appearance = if (dark) "NSAppearanceNameDarkAqua" else "NSAppearanceNameAqua"
                rootPane?.putClientProperty("apple.awt.windowAppearance", appearance)
                rootPane?.repaint()
                window.repaint()
                true
            } catch (e: Throwable) {
                false
            }
        }
        val api = dwm ?: return true
        return try {
            val hwnd = Native.getComponentPointer(window) ?: return false
            val darkFlag = IntByReference(if (dark) 1 else 0)
            // 20 = DWMWA_USE_IMMERSIVE_DARK_MODE (19 on early Windows 10 builds).
            val ok = api.DwmSetWindowAttribute(hwnd, 20, darkFlag, 4) == 0 || api.DwmSetWindowAttribute(hwnd, 19, darkFlag, 4) == 0
            api.DwmSetWindowAttribute(hwnd, 35, IntByReference(colorRef(background)), 4) // DWMWA_CAPTION_COLOR
            api.DwmSetWindowAttribute(hwnd, 36, IntByReference(colorRef(text)), 4)       // DWMWA_TEXT_COLOR
            // Ask Windows to repaint the frame now (NOMOVE | NOSIZE | NOZORDER | NOACTIVATE | FRAMECHANGED).
            user32?.SetWindowPos(hwnd, null, 0, 0, 0, 0, 0x37)
            ok
        } catch (e: Throwable) {
            false // the native window may not exist yet; the caller retries
        }
    }
}
