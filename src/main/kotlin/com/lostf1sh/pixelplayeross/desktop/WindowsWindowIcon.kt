package com.lostf1sh.pixelplayeross.desktop

import com.lostf1sh.pixelplayeross.desktop.library.PlatformPaths
import com.lostf1sh.pixelplayeross.desktop.playback.WindowsMediaSession
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import java.nio.file.Files

private interface User32IconApi : Library {
    fun GetSystemMetrics(nIndex: Int): Int
    fun LoadImageW(
        hInst: Pointer?,
        name: WString,
        type: Int,
        cx: Int,
        cy: Int,
        fuLoad: Int
    ): Pointer?

    fun SendMessageW(
        hWnd: Pointer,
        msg: Int,
        wParam: Pointer?,
        lParam: Pointer?
    ): Pointer?
}

/**
 * Explicitly applies the multi-size `.ico` to a Windows HWND via `WM_SETICON`
 * (`ICON_BIG` and `ICON_SMALL`), ensuring the Windows taskbar and Alt+Tab
 * display the proper app icon even when AWT `setIconImages` is ignored or
 * called before the native peer is realized.
 */
object WindowsWindowIcon {
    private const val IMAGE_ICON = 1
    private const val LR_LOADFROMFILE = 0x0010
    private const val WM_SETICON = 0x0080
    private const val SM_CXICON = 11
    private const val SM_CYICON = 12
    private const val SM_CXSMICON = 49
    private const val SM_CYSMICON = 50

    private val user32: User32IconApi? by lazy {
        if (!PlatformPaths.isWindows()) return@lazy null
        try {
            Native.load("user32", User32IconApi::class.java)
        } catch (_: Throwable) {
            null
        }
    }

    private val cachedIcons: Pair<Pointer?, Pointer?>? by lazy {
        val api = user32 ?: return@lazy null
        val icoPath = WindowsMediaSession.extractAppIcon() ?: return@lazy null
        if (!Files.isRegularFile(icoPath) || !icoPath.toString().endsWith(".ico", ignoreCase = true)) {
            return@lazy null
        }
        try {
            val pathW = WString(icoPath.toAbsolutePath().toString())
            val bigW = api.GetSystemMetrics(SM_CXICON).coerceAtLeast(32)
            val bigH = api.GetSystemMetrics(SM_CYICON).coerceAtLeast(32)
            val smallW = api.GetSystemMetrics(SM_CXSMICON).coerceAtLeast(16)
            val smallH = api.GetSystemMetrics(SM_CYSMICON).coerceAtLeast(16)
            val bigIcon = api.LoadImageW(null, pathW, IMAGE_ICON, bigW, bigH, LR_LOADFROMFILE)
            val smallIcon = api.LoadImageW(null, pathW, IMAGE_ICON, smallW, smallH, LR_LOADFROMFILE)
            bigIcon to smallIcon
        } catch (_: Throwable) {
            null
        }
    }

    fun apply(window: java.awt.Window) {
        if (!PlatformPaths.isWindows()) return
        val api = user32 ?: return
        val (bigIcon, smallIcon) = cachedIcons ?: return
        if (bigIcon == null && smallIcon == null) return
        try {
            val hwnd = Native.getComponentPointer(window) ?: return
            if (bigIcon != null) {
                api.SendMessageW(hwnd, WM_SETICON, Pointer.createConstant(1), bigIcon)
            }
            if (smallIcon != null) {
                api.SendMessageW(hwnd, WM_SETICON, Pointer.createConstant(0), smallIcon)
            }
        } catch (_: Throwable) {
            // Native peer may not be realized yet; caller retries on windowOpened/componentShown.
        }
    }
}
