package com.lostf1sh.pixelplayeross.desktop.dsp

import com.lostf1sh.pixelplayeross.desktop.library.PlatformPaths
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Atomic JSON store for custom user equalizer presets.
 */
class EqPresetStore(private val file: Path = PlatformPaths.getDataDirectory().resolve("eq-presets-v1.json")) {

    private val cachedCustomPresets = CopyOnWriteArrayList<EqualizerPreset>()
    private var loaded = false

    @Synchronized
    fun loadPresets(): List<EqualizerPreset> {
        if (loaded) return cachedCustomPresets.toList()
        cachedCustomPresets.clear()
        if (Files.isRegularFile(file)) {
            try {
                val content = Files.readString(file)
                val parsed = parsePresetsJson(content)
                cachedCustomPresets.addAll(parsed)
            } catch (e: Exception) {
                System.err.println("Failed reading EQ presets: ${e.message}")
            }
        }
        loaded = true
        return cachedCustomPresets.toList()
    }

    fun getAllPresets(): List<EqualizerPreset> {
        val custom = loadPresets()
        return EqualizerDefaults.BUILT_IN_PRESETS + custom
    }

    @Synchronized
    fun savePreset(preset: EqualizerPreset): Boolean {
        require(preset.bandGains.size == 10) { "Preset must have 10 bands" }
        require(!preset.name.isBlank()) { "Preset name cannot be blank" }
        // Cannot overwrite built-ins with the same name
        if (EqualizerDefaults.BUILT_IN_PRESETS.any { it.name.equals(preset.name, ignoreCase = true) }) {
            return false
        }
        loadPresets()
        cachedCustomPresets.removeIf { it.name.equals(preset.name, ignoreCase = true) }
        cachedCustomPresets.add(preset.copy(isBuiltIn = false))
        return persist()
    }

    @Synchronized
    fun deletePreset(name: String): Boolean {
        loadPresets()
        val removed = cachedCustomPresets.removeIf { it.name.equals(name, ignoreCase = true) }
        if (removed) {
            persist()
        }
        return removed
    }

    private fun persist(): Boolean {
        return try {
            val json = serializePresetsJson(cachedCustomPresets)
            val parent = file.parent
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent)
            }
            val temp = file.resolveSibling("${file.fileName}.tmp")
            Files.writeString(temp, json)
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            true
        } catch (e: IOException) {
            System.err.println("Failed persisting EQ presets: ${e.message}")
            false
        }
    }

    private fun serializePresetsJson(presets: List<EqualizerPreset>): String {
        val sb = StringBuilder("[\n")
        presets.forEachIndexed { index, preset ->
            sb.append("  {\n")
            sb.append("    \"name\": \"").append(escapeJson(preset.name)).append("\",\n")
            sb.append("    \"preamp\": ").append(preset.preamp).append(",\n")
            sb.append("    \"bandGains\": [").append(preset.bandGains.joinToString(", ")).append("]\n")
            sb.append("  }")
            if (index < presets.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("]\n")
        return sb.toString()
    }

    private fun parsePresetsJson(json: String): List<EqualizerPreset> {
        val result = mutableListOf<EqualizerPreset>()
        val trimmed = json.trim()
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return result

        val objectPattern = Regex("""\{[^{}]*\}""", RegexOption.DOT_MATCHES_ALL)
        for (match in objectPattern.findAll(trimmed)) {
            val block = match.value
            val name = Regex(""""name"\s*:\s*"([^"]*)"""").find(block)?.groupValues?.get(1)
            val preampStr = Regex(""""preamp"\s*:\s*([-+]?[0-9]*\.?[0-9]+)""").find(block)?.groupValues?.get(1)
            val preamp = preampStr?.toFloatOrNull() ?: 0.0f
            val gainsMatch = Regex(""""bandGains"\s*:\s*\[([^]]*)\]""").find(block)?.groupValues?.get(1)
            if (name != null && gainsMatch != null) {
                val gains = gainsMatch.split(",").mapNotNull { it.trim().toFloatOrNull() }
                if (gains.size == 10) {
                    result.add(EqualizerPreset(unescapeJson(name), preamp, gains, isBuiltIn = false))
                }
            }
        }
        return result
    }

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
    }

    private fun unescapeJson(str: String): String {
        return str.replace("\\n", "\n").replace("\\r", "\r").replace("\\\"", "\"").replace("\\\\", "\\")
    }

    companion object {
        fun defaultStore(): EqPresetStore = EqPresetStore()
    }
}
