package com.lostf1sh.pixelplayeross.desktop.system

import androidx.compose.ui.input.key.KeyEvent
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.awt.Component
import java.awt.event.KeyEvent as AwtKeyEvent
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KeybindingManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun toComposeKeyEvent(awtEvent: AwtKeyEvent): KeyEvent {
        val clazz = Class.forName("androidx.compose.ui.input.key.KeyEvent_desktopKt")
        val method = clazz.getDeclaredMethod("toComposeEvent", AwtKeyEvent::class.java)
        method.isAccessible = true
        val unboxed = method.invoke(null, awtEvent)
        val boxMethod = KeyEvent::class.java.getDeclaredMethod("box-impl", Any::class.java)
        boxMethod.isAccessible = true
        return boxMethod.invoke(null, unboxed) as KeyEvent
    }

    @Test
    fun testDefaultBindingsMacAndWindows() {
        val macManager = KeybindingManager(
            configFile = tempFolder.newFile("mac-keys.json").toPath(),
            isMac = true
        )
        val macBindings = macManager.getDefaultBindings()
        assertEquals(ShortcutAction.entries.size, macBindings.size)
        val macNext = macBindings[ShortcutAction.NEXT_TRACK]
        assertNotNull(macNext)
        assertEquals(AwtKeyEvent.VK_RIGHT, macNext.awtKeyCode)
        assertTrue(macNext.isMeta)
        assertFalse(macNext.isCtrl)

        val winManager = KeybindingManager(
            configFile = tempFolder.newFile("win-keys.json").toPath(),
            isMac = false
        )
        val winBindings = winManager.getDefaultBindings()
        assertEquals(ShortcutAction.entries.size, winBindings.size)
        val winNext = winBindings[ShortcutAction.NEXT_TRACK]
        assertNotNull(winNext)
        assertEquals(AwtKeyEvent.VK_RIGHT, winNext.awtKeyCode)
        assertTrue(winNext.isCtrl)
        assertFalse(winNext.isMeta)
    }

    @Test
    fun testCustomBindingPersistence() {
        val file = tempFolder.newFile("custom-keys.json").toPath()
        val manager = KeybindingManager(configFile = file, isMac = true)

        val newCombo = KeyCombination(
            awtKeyCode = AwtKeyEvent.VK_P,
            keyText = "P",
            isCtrl = true
        )
        val saved = manager.setBinding(ShortcutAction.PLAY_PAUSE, newCombo)
        assertTrue(saved)
        assertEquals(newCombo, manager.bindings.value[ShortcutAction.PLAY_PAUSE])

        // Reload fresh manager from the saved JSON file
        val managerReloaded = KeybindingManager(configFile = file, isMac = true)
        assertEquals(newCombo, managerReloaded.bindings.value[ShortcutAction.PLAY_PAUSE])
    }

    @Test
    fun testConflictDetection() {
        val manager = KeybindingManager(
            configFile = tempFolder.newFile("conflict-keys.json").toPath(),
            isMac = true
        )

        // Space is default for PLAY_PAUSE
        val spaceCombo = KeyCombination(AwtKeyEvent.VK_SPACE, "Space")
        val conflict = manager.findConflict(spaceCombo, excludingAction = ShortcutAction.TOGGLE_FULL_PLAYER)
        assertEquals(ShortcutAction.PLAY_PAUSE, conflict)

        // Excluding PLAY_PAUSE itself should find no conflict
        val noConflict = manager.findConflict(spaceCombo, excludingAction = ShortcutAction.PLAY_PAUSE)
        assertNull(noConflict)
    }

    @Test
    fun testConflictAutoUnbinding() {
        val manager = KeybindingManager(
            configFile = tempFolder.newFile("conflict-unbind-keys.json").toPath(),
            isMac = true
        )

        val spaceCombo = KeyCombination(AwtKeyEvent.VK_SPACE, "Space")
        assertEquals(spaceCombo, manager.bindings.value[ShortcutAction.PLAY_PAUSE])

        // Reassign Space to TOGGLE_FULL_PLAYER
        val saved = manager.setBinding(ShortcutAction.TOGGLE_FULL_PLAYER, spaceCombo)
        assertTrue(saved)
        assertEquals(spaceCombo, manager.bindings.value[ShortcutAction.TOGGLE_FULL_PLAYER])
        assertNull(manager.bindings.value[ShortcutAction.PLAY_PAUSE])
    }

    @Test
    fun testResetSingleAndResetAll() {
        val manager = KeybindingManager(
            configFile = tempFolder.newFile("reset-keys.json").toPath(),
            isMac = true
        )
        val originalSpace = manager.bindings.value[ShortcutAction.PLAY_PAUSE]

        val customCombo = KeyCombination(AwtKeyEvent.VK_X, "X", isShift = true)
        manager.setBinding(ShortcutAction.PLAY_PAUSE, customCombo)
        assertEquals(customCombo, manager.bindings.value[ShortcutAction.PLAY_PAUSE])

        // Reset single
        manager.resetAction(ShortcutAction.PLAY_PAUSE)
        assertEquals(originalSpace, manager.bindings.value[ShortcutAction.PLAY_PAUSE])

        // Customise another and reset all
        manager.setBinding(ShortcutAction.PLAY_PAUSE, customCombo)
        manager.resetAll()
        assertEquals(originalSpace, manager.bindings.value[ShortcutAction.PLAY_PAUSE])
    }

    @Test
    fun testModifierOnlyCheck() {
        assertTrue(KeybindingManager.isModifierOnly(AwtKeyEvent.VK_SHIFT))
        assertTrue(KeybindingManager.isModifierOnly(AwtKeyEvent.VK_CONTROL))
        assertTrue(KeybindingManager.isModifierOnly(AwtKeyEvent.VK_ALT))
        assertTrue(KeybindingManager.isModifierOnly(AwtKeyEvent.VK_META))

        assertFalse(KeybindingManager.isModifierOnly(AwtKeyEvent.VK_SPACE))
        assertFalse(KeybindingManager.isModifierOnly(AwtKeyEvent.VK_ENTER))
        assertFalse(KeybindingManager.isModifierOnly(AwtKeyEvent.VK_RIGHT))
        assertFalse(KeybindingManager.isModifierOnly(AwtKeyEvent.VK_A))
    }

    @Test
    fun testDisplayLabelFormatting() {
        val macCombo = KeyCombination(
            awtKeyCode = AwtKeyEvent.VK_RIGHT,
            keyText = "Right",
            isMeta = true
        )
        assertEquals("⌘ →", macCombo.displayLabel(isMac = true))

        val winCombo = KeyCombination(
            awtKeyCode = AwtKeyEvent.VK_RIGHT,
            keyText = "Right",
            isCtrl = true
        )
        assertEquals("Ctrl + →", winCombo.displayLabel(isMac = false))

        val spaceCombo = KeyCombination(
            awtKeyCode = AwtKeyEvent.VK_SPACE,
            keyText = "Space"
        )
        assertEquals("␣ Space", spaceCombo.displayLabel(isMac = true))
        assertEquals("Space", spaceCombo.displayLabel(isMac = false))
    }

    @Test
    fun testKeyEventMatching() {
        val manager = KeybindingManager(
            configFile = tempFolder.newFile("match-keys.json").toPath(),
            isMac = true
        )
        val dummyComponent: Component = JPanel()

        // Space key down (Play/Pause)
        val awtEvent = AwtKeyEvent(
            dummyComponent,
            AwtKeyEvent.KEY_PRESSED,
            System.currentTimeMillis(),
            0,
            AwtKeyEvent.VK_SPACE,
            ' '
        )
        val composeEvent = toComposeKeyEvent(awtEvent)

        val matched = manager.matchAction(composeEvent, isTextInputActive = false)
        assertEquals(ShortcutAction.PLAY_PAUSE, matched)

        // When text input is active, non-modified Space should be ignored
        val matchedInText = manager.matchAction(composeEvent, isTextInputActive = true)
        assertNull(matchedInText)
    }
}
