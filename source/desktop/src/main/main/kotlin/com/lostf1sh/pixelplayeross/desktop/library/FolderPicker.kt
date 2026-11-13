package com.lostf1sh.pixelplayeross.desktop.library

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Component
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.Base64
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun chooseMusicFolder(parent: Component): Path? {
    if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
        try {
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
        } catch (_: Throwable) {
            return chooseSwingFolder(parent)
        }
    }
    return chooseSwingFolder(parent)
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
