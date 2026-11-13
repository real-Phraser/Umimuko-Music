package com.lostf1sh.pixelplayeross.desktop.playback

import com.lostf1sh.pixelplayeross.desktop.library.PlatformPaths
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

/** Configure VLC native paths for current platform before vlcj touches bindings. */
internal fun configureBundledVlc(): Boolean {
    if (PlatformPaths.isWindows()) {
        return configureWindowsVlc()
    }
    if (PlatformPaths.isMac()) {
        return configureMacVlc()
    }
    return configureLinuxVlc()
}

private fun configureWindowsVlc(): Boolean {
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
    check(NativeLibrary.getInstance("msvcrt").getFunction("_wputenv_s")
        .invokeInt(arrayOf(WString("VLC_PLUGIN_PATH"), WString(plugins))) == 0) { "Could not configure VLC runtime environment." }
    NativeLibrary.addSearchPath("libvlc", root.toString())
    NativeLibrary.addSearchPath("libvlccore", root.toString())
    return true
}

private fun configureMacVlc(): Boolean {
    val envDir = System.getenv("UMIMUKO_VLC_DIR")?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
    val candidates = listOfNotNull(
        envDir,
        Path.of("/Applications/VLC.app/Contents/MacOS"),
        Path.of(System.getProperty("user.home"), "Applications/VLC.app/Contents/MacOS"),
        Path.of("/opt/homebrew/lib"),
        Path.of("/usr/local/lib")
    )
    for (dir in candidates) {
        val libDir = if (dir.endsWith("MacOS")) dir.resolve("lib") else dir
        val vlcDylib = libDir.resolve("libvlc.dylib")
        val coreDylib = libDir.resolve("libvlccore.dylib")
        if (Files.isRegularFile(vlcDylib)) {
            val pluginsDir = if (dir.endsWith("MacOS")) dir.resolve("plugins") else dir.resolve("vlc/plugins")
            if (Files.isDirectory(pluginsDir)) {
                System.setProperty("VLC_PLUGIN_PATH", pluginsDir.toString())
                try {
                    val libc = NativeLibrary.getInstance("c")
                    libc.getFunction("setenv").invoke(arrayOf("VLC_PLUGIN_PATH", pluginsDir.toString(), 1))
                } catch (_: Throwable) {}
            }
            NativeLibrary.addSearchPath("libvlccore", libDir.toString())
            NativeLibrary.addSearchPath("libvlc", libDir.toString())
            if (Files.isRegularFile(coreDylib)) {
                try {
                    NativeLibrary.getInstance(coreDylib.toString())
                } catch (_: Throwable) {}
            }
            try {
                NativeLibrary.getInstance(vlcDylib.toString())
                return true
            } catch (_: Throwable) {}
        }
    }
    return false
}

private fun configureLinuxVlc(): Boolean {
    val envDir = System.getenv("UMIMUKO_VLC_DIR")?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
    val candidates = listOfNotNull(
        envDir,
        Path.of("/usr/lib/x86_64-linux-gnu"),
        Path.of("/usr/lib/aarch64-linux-gnu"),
        Path.of("/usr/lib64"),
        Path.of("/usr/lib"),
        Path.of("/usr/local/lib")
    )
    for (libDir in candidates) {
        val vlcSo = libDir.resolve("libvlc.so.5").takeIf { Files.isRegularFile(it) }
            ?: libDir.resolve("libvlc.so")
        if (Files.isRegularFile(vlcSo)) {
            val plugins = libDir.resolve("vlc/plugins").takeIf { Files.isDirectory(it) }
                ?: Path.of("/usr/lib/vlc/plugins").takeIf { Files.isDirectory(it) }
            if (plugins != null) {
                System.setProperty("VLC_PLUGIN_PATH", plugins.toString())
                try {
                    val libc = NativeLibrary.getInstance("c")
                    libc.getFunction("setenv").invoke(arrayOf("VLC_PLUGIN_PATH", plugins.toString(), 1))
                } catch (_: Throwable) {}
            }
            NativeLibrary.addSearchPath("libvlc", libDir.toString())
            NativeLibrary.addSearchPath("libvlccore", libDir.toString())
            return true
        }
    }
    return false
}
