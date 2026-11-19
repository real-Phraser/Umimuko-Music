package com.lostf1sh.pixelplayeross.desktop.system

import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import com.lostf1sh.pixelplayeross.desktop.library.PlatformPaths
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.awt.event.KeyEvent as AwtKeyEvent
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

class KeybindingManager(
    private val configFile: Path = PlatformPaths.getDataDirectory().resolve("keybindings-v1.json"),
    private val isMac: Boolean = PlatformPaths.isMac()
) {
    private val _bindings = MutableStateFlow<Map<ShortcutAction, KeyCombination>>(emptyMap())
    val bindings: StateFlow<Map<ShortcutAction, KeyCombination>> = _bindings.asStateFlow()

    init {
        loadBindings()
    }

    fun getDefaultBindings(): Map<ShortcutAction, KeyCombination> {
        val map = mutableMapOf<ShortcutAction, KeyCombination>()
        if (isMac) {
            map[ShortcutAction.PLAY_PAUSE] = KeyCombination(AwtKeyEvent.VK_SPACE, "Space")
            map[ShortcutAction.NEXT_TRACK] = KeyCombination(AwtKeyEvent.VK_RIGHT, "Right", isMeta = true)
            map[ShortcutAction.PREV_TRACK] = KeyCombination(AwtKeyEvent.VK_LEFT, "Left", isMeta = true)
            map[ShortcutAction.SEEK_FORWARD] = KeyCombination(AwtKeyEvent.VK_RIGHT, "Right", isShift = true)
            map[ShortcutAction.SEEK_BACKWARD] = KeyCombination(AwtKeyEvent.VK_LEFT, "Left", isShift = true)
            map[ShortcutAction.VOLUME_UP] = KeyCombination(AwtKeyEvent.VK_UP, "Up", isMeta = true)
            map[ShortcutAction.VOLUME_DOWN] = KeyCombination(AwtKeyEvent.VK_DOWN, "Down", isMeta = true)
            map[ShortcutAction.MUTE] = KeyCombination(AwtKeyEvent.VK_M, "M")
            map[ShortcutAction.TOGGLE_FULLSCREEN] = KeyCombination(AwtKeyEvent.VK_F, "F", isCtrl = true, isMeta = true)
            map[ShortcutAction.TOGGLE_FULL_PLAYER] = KeyCombination(AwtKeyEvent.VK_F, "F")
            map[ShortcutAction.TOGGLE_LYRICS] = KeyCombination(AwtKeyEvent.VK_L, "L")
        } else {
            map[ShortcutAction.PLAY_PAUSE] = KeyCombination(AwtKeyEvent.VK_SPACE, "Space")
            map[ShortcutAction.NEXT_TRACK] = KeyCombination(AwtKeyEvent.VK_RIGHT, "Right", isCtrl = true)
            map[ShortcutAction.PREV_TRACK] = KeyCombination(AwtKeyEvent.VK_LEFT, "Left", isCtrl = true)
            map[ShortcutAction.SEEK_FORWARD] = KeyCombination(AwtKeyEvent.VK_RIGHT, "Right", isShift = true)
            map[ShortcutAction.SEEK_BACKWARD] = KeyCombination(AwtKeyEvent.VK_LEFT, "Left", isShift = true)
            map[ShortcutAction.VOLUME_UP] = KeyCombination(AwtKeyEvent.VK_UP, "Up", isCtrl = true)
            map[ShortcutAction.VOLUME_DOWN] = KeyCombination(AwtKeyEvent.VK_DOWN, "Down", isCtrl = true)
            map[ShortcutAction.MUTE] = KeyCombination(AwtKeyEvent.VK_M, "M")
            map[ShortcutAction.TOGGLE_FULLSCREEN] = KeyCombination(AwtKeyEvent.VK_F11, "F11")
            map[ShortcutAction.TOGGLE_FULL_PLAYER] = KeyCombination(AwtKeyEvent.VK_F, "F")
            map[ShortcutAction.TOGGLE_LYRICS] = KeyCombination(AwtKeyEvent.VK_L, "L")
        }
        return map
    }

    @Synchronized
    fun loadBindings(): Map<ShortcutAction, KeyCombination> {
        val defaults = getDefaultBindings().toMutableMap()
        if (Files.isRegularFile(configFile)) {
            try {
                val json = Files.readString(configFile)
                val parsed = parseJson(json)
                parsed.forEach { (action, combo) ->
                    defaults[action] = combo
                }
            } catch (e: Exception) {
                System.err.println("Failed reading keybindings: ${e.message}")
            }
        }
        _bindings.value = defaults
        return defaults
    }

    @Synchronized
    fun setBinding(action: ShortcutAction, combo: KeyCombination): Boolean {
        if (isModifierOnly(combo.awtKeyCode)) return false

        val current = _bindings.value.toMutableMap()
        val conflict = findConflict(combo, excludingAction = action)
        if (conflict != null) {
            current.remove(conflict)
        }
        current[action] = combo
        _bindings.value = current
        return persist()
    }

    @Synchronized
    fun resetAction(action: ShortcutAction): Boolean {
        val defaults = getDefaultBindings()
        val defaultCombo = defaults[action] ?: return false
        val current = _bindings.value.toMutableMap()
        current[action] = defaultCombo
        _bindings.value = current
        return persist()
    }

    @Synchronized
    fun resetAll(): Boolean {
        val defaults = getDefaultBindings()
        _bindings.value = defaults
        return persist()
    }

    fun findConflict(combo: KeyCombination, excludingAction: ShortcutAction? = null): ShortcutAction? {
        if (isModifierOnly(combo.awtKeyCode)) return null
        return _bindings.value.entries.firstOrNull { (action, existing) ->
            action != excludingAction &&
                existing.awtKeyCode == combo.awtKeyCode &&
                existing.isCtrl == combo.isCtrl &&
                existing.isMeta == combo.isMeta &&
                existing.isAlt == combo.isAlt &&
                existing.isShift == combo.isShift
        }?.key
    }

    fun matchAction(event: KeyEvent, isTextInputActive: Boolean = false): ShortcutAction? {
        if (event.type != KeyEventType.KeyDown) return null

        val currentBindings = _bindings.value
        for ((action, combo) in currentBindings) {
            if (combo.matches(event)) {
                // If user is focused on a text input, don't trigger non-modified shortcuts (e.g., Space or letters)
                if (isTextInputActive && !combo.hasModifiers()) {
                    return null
                }
                return action
            }
        }
        return null
    }

    private fun persist(): Boolean {
        try {
            val parent = configFile.parent
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent)
            }
            val json = serializeJson(_bindings.value)
            val tmp = configFile.resolveSibling("${configFile.fileName}.tmp")
            Files.writeString(tmp, json)
            Files.move(tmp, configFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            return true
        } catch (e: IOException) {
            System.err.println("Failed saving keybindings: ${e.message}")
            return false
        }
    }

    private fun serializeJson(bindings: Map<ShortcutAction, KeyCombination>): String {
        val sb = StringBuilder("{\n  \"keybindings\": [\n")
        val list = bindings.entries.toList()
        for (i in list.indices) {
            val (action, combo) = list[i]
            sb.append("    {\n")
            sb.append("      \"action\": \"${action.id}\",\n")
            sb.append("      \"awtKeyCode\": ${combo.awtKeyCode},\n")
            sb.append("      \"keyText\": \"${combo.keyText}\",\n")
            sb.append("      \"isCtrl\": ${combo.isCtrl},\n")
            sb.append("      \"isMeta\": ${combo.isMeta},\n")
            sb.append("      \"isAlt\": ${combo.isAlt},\n")
            sb.append("      \"isShift\": ${combo.isShift}\n")
            sb.append("    }")
            if (i < list.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("  ]\n}\n")
        return sb.toString()
    }

    private fun parseJson(json: String): Map<ShortcutAction, KeyCombination> {
        val result = mutableMapOf<ShortcutAction, KeyCombination>()
        val itemRegex = Regex("""\{[^{}]*\}""", RegexOption.DOT_MATCHES_ALL)
        for (match in itemRegex.findAll(json)) {
            val block = match.value
            val actionId = Regex(""""action"\s*:\s*"([^"]+)"""").find(block)?.groupValues?.get(1) ?: continue
            val action = ShortcutAction.fromId(actionId) ?: continue
            val awtKeyCode = Regex(""""awtKeyCode"\s*:\s*(-?\d+)""").find(block)?.groupValues?.get(1)?.toIntOrNull() ?: continue
            val keyText = Regex(""""keyText"\s*:\s*"([^"]*)"""").find(block)?.groupValues?.get(1) ?: ""
            val isCtrl = Regex(""""isCtrl"\s*:\s*(true|false)""").find(block)?.groupValues?.get(1)?.toBoolean() ?: false
            val isMeta = Regex(""""isMeta"\s*:\s*(true|false)""").find(block)?.groupValues?.get(1)?.toBoolean() ?: false
            val isAlt = Regex(""""isAlt"\s*:\s*(true|false)""").find(block)?.groupValues?.get(1)?.toBoolean() ?: false
            val isShift = Regex(""""isShift"\s*:\s*(true|false)""").find(block)?.groupValues?.get(1)?.toBoolean() ?: false

            result[action] = KeyCombination(
                awtKeyCode = awtKeyCode,
                keyText = keyText,
                isCtrl = isCtrl,
                isMeta = isMeta,
                isAlt = isAlt,
                isShift = isShift
            )
        }
        return result
    }

    companion object {
        fun isModifierOnly(awtKeyCode: Int): Boolean {
            return awtKeyCode in listOf(
                AwtKeyEvent.VK_SHIFT,
                AwtKeyEvent.VK_CONTROL,
                AwtKeyEvent.VK_ALT,
                AwtKeyEvent.VK_META,
                AwtKeyEvent.VK_ALT_GRAPH
            )
        }
    }
}
