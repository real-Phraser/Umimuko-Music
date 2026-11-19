package com.lostf1sh.pixelplayeross.desktop.dsp

import java.nio.file.Files
import kotlin.test.*

class EqPresetStoreTest {

    private val tempDir = Files.createTempDirectory("eq-test-")
    private val tempFile = tempDir.resolve("test-eq-presets.json")
    private val store = EqPresetStore(tempFile)

    @AfterTest
    fun cleanup() {
        Files.deleteIfExists(tempFile)
        Files.deleteIfExists(tempDir)
    }

    @Test
    fun builtInPresetsAreLoadedByDefault() {
        val all = store.getAllPresets()
        assertTrue(all.size >= 10)
        assertTrue(all.any { it.name == "Flat" && it.isBuiltIn })
        assertTrue(all.any { it.name == "Bass Boost" && it.isBuiltIn })
        assertTrue(all.any { it.name == "Rock" && it.isBuiltIn })
    }

    @Test
    fun savesAndLoadsCustomPreset() {
        val custom = EqualizerPreset(
            name = "Warm Vinyl",
            preamp = 2.0f,
            bandGains = listOf(4.0f, 3.0f, 2.0f, 1.0f, 0.0f, -1.0f, -2.0f, -2.5f, -3.0f, -4.0f),
            isBuiltIn = false
        )
        val saved = store.savePreset(custom)
        assertTrue(saved)

        // Reload from new store instance pointing to same file
        val newStore = EqPresetStore(tempFile)
        val loaded = newStore.loadPresets()
        assertEquals(1, loaded.size)
        assertEquals("Warm Vinyl", loaded.first().name)
        assertEquals(2.0f, loaded.first().preamp)
        assertEquals(custom.bandGains, loaded.first().bandGains)
        assertFalse(loaded.first().isBuiltIn)
    }

    @Test
    fun cannotOverwriteBuiltInPreset() {
        val attempt = EqualizerPreset(
            name = "Flat",
            preamp = 5.0f,
            bandGains = List(10) { 1.0f }
        )
        val saved = store.savePreset(attempt)
        assertFalse(saved)
    }

    @Test
    fun deletesCustomPreset() {
        val preset = EqualizerPreset("To Delete", 0.0f, List(10) { 0.0f })
        store.savePreset(preset)
        assertEquals(1, store.loadPresets().size)

        val deleted = store.deletePreset("To Delete")
        assertTrue(deleted)
        assertEquals(0, store.loadPresets().size)
    }
}
