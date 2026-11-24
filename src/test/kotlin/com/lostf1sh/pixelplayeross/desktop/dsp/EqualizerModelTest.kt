package com.lostf1sh.pixelplayeross.desktop.dsp

import kotlin.test.Test
import kotlin.test.assertEquals

class EqualizerModelTest {

    @Test
    fun flatPresetHasZeroUserPreampAndZeroBands() {
        val flat = EqualizerDefaults.findPreset("Flat")!!
        assertEquals(0.0f, flat.preamp)
        assertEquals(List(10) { 0.0f }, flat.bandGains)
    }

    @Test
    fun toLibVlcPreampAppliesUnityCompensationOffset() {
        assertEquals(12.0f, EqualizerDefaults.toLibVlcPreamp(0.0f))
        assertEquals(13.0f, EqualizerDefaults.toLibVlcPreamp(1.0f))
        assertEquals(-3.0f, EqualizerDefaults.toLibVlcPreamp(-15.0f))
    }

    @Test
    fun toLibVlcPreampClampsWithinLibVlcBounds() {
        assertEquals(20.0f, EqualizerDefaults.toLibVlcPreamp(8.0f))
        assertEquals(20.0f, EqualizerDefaults.toLibVlcPreamp(15.0f))
        assertEquals(-20.0f, EqualizerDefaults.toLibVlcPreamp(-35.0f))
    }
}
