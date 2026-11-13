package com.lostf1sh.pixelplayeross.desktop.playback

import com.sun.jna.Function
import com.sun.jna.NativeLibrary
import com.sun.jna.WString
import java.nio.file.Files
import java.nio.file.Path

/** Resolve known layouts without depending on the shortcut's working directory. */
internal fun locateBundledVlc(roots: List<Path>): Path? {
    val layouts = listOf("vlc", "windows/vlc", "common/vlc", "app/resources/vlc", "resources/vlc")
    return roots.flatMap { root -> layouts.map(root::resolve) }
        .map { it.toAbsolutePath().normalize() }.distinct().firstOrNull { root ->
            Files.isRegularFile(root.resolve("libvlc.dll")) &&
                Files.isRegularFile(root.resolve("libvlccore.dll")) &&
                Files.isDirectory(root.resolve("plugins")) &&
                Files.walk(root.resolve("plugins")).use { files ->
                    files.anyMatch { Files.isRegularFile(it) && it.toString().endsWith(".dll", ignoreCase = true) }
                }
        }
}

/** Configure the private VLC before vlcj touches its native bindings. */
internal fun configureBundledVlc(): Boolean {
    if (!System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) return false
    fun path(value: String?) = value?.takeIf { it.isNotBlank() }?.let {
        runCatching { Path.of(it.trim().removeSurrounding("\"")) }.getOrNull()
    }
    val javaHome = Path.of(System.getProperty("java.home"))
    val roots = listOfNotNull(
        path(System.getProperty("compose.application.resources.dir")),
        path(System.getProperty("jpackage.app-path"))?.parent,
        path(ProcessHandle.current().info().command().orElse(null))?.parent,
        javaHome.parent,
    ).distinct()
    val packaged = System.getProperty("jpackage.app-path") != null ||
        javaHome.fileName?.toString().equals("runtime", ignoreCase = true)
    val root = locateBundledVlc(roots) ?: run {
        check(!packaged) {
            "Bundled VLC not found. Expected a complete vlc folder beside Umimuko Music.exe. Checked: " +
                roots.joinToString()
        }
        return false // Gradle development run: use regular VLC discovery.
    }
    val kernel = NativeLibrary.getInstance("kernel32")
    check(kernel.getFunction("SetDllDirectoryW", Function.ALT_CONVENTION)
        .invokeInt(arrayOf(WString(root.toString()))) != 0) { "Could not configure bundled VLC DLL directory." }
    val plugins = root.resolve("plugins").toString()
    check(kernel.getFunction("SetEnvironmentVariableW", Function.ALT_CONVENTION)
        .invokeInt(arrayOf(WString("VLC_PLUGIN_PATH"), WString(plugins))) != 0) { "Could not configure VLC plugins." }
    // VLC 3 uses the C runtime environment. Keep it in sync with the process environment.
    check(NativeLibrary.getInstance("msvcrt").getFunction("_wputenv_s")
        .invokeInt(arrayOf(WString("VLC_PLUGIN_PATH"), WString(plugins))) == 0) { "Could not configure VLC runtime environment." }
    NativeLibrary.addSearchPath("libvlc", root.toString())
    NativeLibrary.addSearchPath("libvlccore", root.toString())
    return true
}
