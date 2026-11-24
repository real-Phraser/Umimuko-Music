package com.lostf1sh.pixelplayeross.desktop.playback

import com.lostf1sh.pixelplayeross.desktop.dsp.CrossfadeCurve
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CrossfadeMathTest {

    @Test
    fun linearCurveRampsSmoothlyFromOutgoingToIncoming() {
        val start = CrossfadeMath.computeVolumes(0.0f, 80, CrossfadeCurve.LINEAR)
        assertEquals(80, start.outgoing)
        assertEquals(0, start.incoming)

        val mid = CrossfadeMath.computeVolumes(0.5f, 80, CrossfadeCurve.LINEAR)
        assertEquals(40, mid.outgoing)
        assertEquals(40, mid.incoming)

        val end = CrossfadeMath.computeVolumes(1.0f, 80, CrossfadeCurve.LINEAR)
        assertEquals(0, end.outgoing)
        assertEquals(80, end.incoming)
    }

    @Test
    fun equalPowerCurvePreservesPerceivedLoudnessAtMidpoint() {
        val start = CrossfadeMath.computeVolumes(0.0f, 100, CrossfadeCurve.EQUAL_POWER)
        assertEquals(100, start.outgoing)
        assertEquals(0, start.incoming)

        val mid = CrossfadeMath.computeVolumes(0.5f, 100, CrossfadeCurve.EQUAL_POWER)
        assertEquals(71, mid.outgoing)
        assertEquals(71, mid.incoming)

        val end = CrossfadeMath.computeVolumes(1.0f, 100, CrossfadeCurve.EQUAL_POWER)
        assertEquals(0, end.outgoing)
        assertEquals(100, end.incoming)
    }

    @Test
    fun curvesAreMonotonicAndClamped() {
        for (curve in CrossfadeCurve.entries) {
            var prevOut = 100
            var prevIn = 0
            for (step in 0..20) {
                val p = step / 20f
                val v = CrossfadeMath.computeVolumes(p, 90, curve)
                assertTrue(v.outgoing in 0..90)
                assertTrue(v.incoming in 0..90)
                assertTrue(v.outgoing <= prevOut, "outgoing should not increase for $curve at step $step")
                assertTrue(v.incoming >= prevIn, "incoming should not decrease for $curve at step $step")
                prevOut = v.outgoing
                prevIn = v.incoming
            }
        }
    }

    @Test
    fun lookaheadAccountsForPlaybackRateAndGuards() {
        // At 2x speed, 6000ms media remaining = 3000ms wall-clock remaining -> triggers 3.5s crossfade with 3000ms duration
        val triggered = CrossfadeMath.shouldTriggerCrossfade(
            isPlaying = true,
            isCrossfading = false,
            repeatMode = RepeatMode.OFF,
            durationMs = 120_000L,
            positionMs = 114_000L,
            playbackRate = 2.0f,
            crossfadeDurationSeconds = 3.5f,
            hasNextTrack = true
        )
        assertEquals(3000L, triggered)

        // Repeat ONE or no next track or short track or gapless (0s) must not trigger
        assertNull(
            CrossfadeMath.shouldTriggerCrossfade(
                isPlaying = true,
                isCrossfading = false,
                repeatMode = RepeatMode.ONE,
                durationMs = 120_000L,
                positionMs = 118_000L,
                playbackRate = 1.0f,
                crossfadeDurationSeconds = 3.5f,
                hasNextTrack = true
            )
        )
        assertNull(
            CrossfadeMath.shouldTriggerCrossfade(
                isPlaying = true,
                isCrossfading = false,
                repeatMode = RepeatMode.OFF,
                durationMs = 120_000L,
                positionMs = 118_000L,
                playbackRate = 1.0f,
                crossfadeDurationSeconds = 3.5f,
                hasNextTrack = false
            )
        )
        assertNull(
            CrossfadeMath.shouldTriggerCrossfade(
                isPlaying = true,
                isCrossfading = false,
                repeatMode = RepeatMode.OFF,
                durationMs = 120_000L,
                positionMs = 118_000L,
                playbackRate = 1.0f,
                crossfadeDurationSeconds = 0.0f,
                hasNextTrack = true
            )
        )
    }

    @Test
    fun completionTriggersAtEndOfRampOrNearEof() {
        assertFalse(CrossfadeMath.shouldCompleteCrossfade(progress = 0.5f, wallRemainingMs = 1500L))
        assertTrue(CrossfadeMath.shouldCompleteCrossfade(progress = 1.0f, wallRemainingMs = 1500L))
        assertTrue(CrossfadeMath.shouldCompleteCrossfade(progress = 0.8f, wallRemainingMs = 30L))
    }
}
