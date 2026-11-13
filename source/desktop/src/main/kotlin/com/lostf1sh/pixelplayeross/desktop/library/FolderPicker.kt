package com.lostf1sh.pixelplayeross.desktop.library

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Component
import java.awt.FileDialog
import java.awt.Frame
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.Base64
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun chooseMusicFolder(parent: Component): Path? {
    if (PlatformPaths.isWindows()) {
        try {
            return chooseWindowsFolder(parent)
        } catch (_: Throwable) {
            return chooseSwingFolder(parent)
        }
    }
    if (PlatformPaths.isMac()) {
        try {
            return chooseMacFolder(parent)
        } catch (_: Throwable) {
            return chooseSwingFolder(parent)
        }
    }
    if (PlatformPaths.isLinux()) {
        return chooseLinuxFolder(parent)
    }
    return chooseSwingFolder(parent)
}

private suspend fun chooseWindowsFolder(parent: Component): Path? {
    return withContext(Dispatchers.IO) {
        val script = requireNotNull(FolderScanner::class.java.classLoader.getResourceAsStream("windows/choose-folder.ps1")) {
            "Windows folder picker resource is missing"
        }.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        val encoded = Base64.getEncoder().encodeToString(script.toByteArray(StandardCharsets.UTF_16LE))
        val process = ProcessBuilder("powershell.exe", "-NoLogo", "-NoProfile", "-STA", "-EncodedCommand", encoded).start()
        try {
            val lines = process.inputStream.bufferedReader(StandardCharsets.UTF_8).readLines()
            val code = process.waitFor()
            if (code == 0) {
                val pathStr = lines.map { it.trim() }.lastOrNull { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("<") }
                pathStr?.let(Path::of)
            } else {
                val err = process.errorStream.bufferedReader(StandardCharsets.UTF_8).readText().trim()
                error("Windows folder picker exited with code $code: $err")
            }
        } finally {
            if (process.isAlive) process.destroy()
        }
    }
}

private suspend fun chooseMacFolder(parent: Component): Path? = suspendCancellableCoroutine { continuation ->
    SwingUtilities.invokeLater {
        if (!continuation.isActive) return@invokeLater
        try {
            val owner = SwingUtilities.getWindowAncestor(parent) as? Frame
            System.setProperty("apple.awt.fileDialogForDirectories", "true")
            val dialog = FileDialog(owner, "Choose your music folder", FileDialog.LOAD)
            dialog.isVisible = true
            System.setProperty("apple.awt.fileDialogForDirectories", "false")
            val dir = dialog.directory
            val file = dialog.file
            val result = when {
                dir != null && file != null -> Path.of(dir, file)
                dir != null -> Path.of(dir)
                else -> null
            }
            if (continuation.isActive) continuation.resume(result)
        } catch (error: Exception) {
            if (continuation.isActive) continuation.resumeWithException(error)
        }
    }
}

private suspend fun chooseLinuxFolder(parent: Component): Path? {
    return withContext(Dispatchers.IO) {
        val zenity = runCatching {
            val p = ProcessBuilder("zenity", "--file-selection", "--directory", "--title=Choose your music folder").start()
            val text = p.inputStream.bufferedReader(StandardCharsets.UTF_8).readText().trim()
            if (p.waitFor() == 0 && text.isNotBlank()) Path.of(text) else null
        }.getOrNull()
        if (zenity != null) return@withContext zenity

        val kdialog = runCatching {
            val p = ProcessBuilder("kdialog", "--getexistingdirectory", "--title", "Choose your music folder").start()
            val text = p.inputStream.bufferedReader(StandardCharsets.UTF_8).readText().trim()
            if (p.waitFor() == 0 && text.isNotBlank()) Path.of(text) else null
        }.getOrNull()
        if (kdialog != null) return@withContext kdialog

        chooseSwingFolder(parent)
    }
}

private suspend fun chooseSwingFolder(parent: Component): Path? = suspendCancellableCoroutine { continuation ->
    SwingUtilities.invokeLater {
        if (!continuation.isActive) return@invokeLater
        try {
            val chooser = JFileChooser().apply {
                dialogTitle = "Choose your music folder"
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                isAcceptAllFileFilterUsed = false
            }
            val result = if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
                chooser.selectedFile.toPath()
            } else null
            if (continuation.isActive) continuation.resume(result)
        } catch (error: Exception) {
            if (continuation.isActive) continuation.resumeWithException(error)
        }
    }
}
