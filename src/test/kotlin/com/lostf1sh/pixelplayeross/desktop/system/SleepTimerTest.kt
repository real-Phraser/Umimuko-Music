package com.lostf1sh.pixelplayeross.desktop.system

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SleepTimerTest {

    private class MockAudioTarget(
        initialVolume: Int = 80,
        override var isPlaying: Boolean = true,
        override var currentSongId: String? = "song-1",
        override var currentTimeMs: Long = 100_000L,
        override var durationMs: Long = 200_000L
    ) : SleepTimerAudioTarget {
        private var _volume: Int = initialVolume
        override val volume: Int get() = _volume
        var paused = false

        override fun setVolume(vol: Int) {
            _volume = vol
        }

        override fun pause() {
            paused = true
            isPlaying = false
        }
    }

    @Test
    fun testFormatRemaining() {
        assertEquals("0:00", SleepTimer.formatRemaining(0))
        assertEquals("0:00", SleepTimer.formatRemaining(-10))
        assertEquals("0:05", SleepTimer.formatRemaining(5))
        assertEquals("1:05", SleepTimer.formatRemaining(65))
        assertEquals("15:00", SleepTimer.formatRemaining(900))
        assertEquals("60:00", SleepTimer.formatRemaining(3600))
    }

    @Test
    fun testStartDurationTimer() {
        val mock = MockAudioTarget(initialVolume = 75)
        val timer = SleepTimer(audioTarget = mock)

        timer.start(SleepTimerTarget.Duration(15), fadeout = true)
        val state = timer.state.value

        assertTrue(state.isActive)
        assertEquals(900L, state.totalSeconds)
        assertEquals(900L, state.remainingSeconds)
        assertTrue(state.fadeoutEnabled)
        assertFalse(state.isFading)
        assertEquals(SleepTimerTarget.Duration(15), state.target)

        timer.cancel(restoreVolume = false)
        assertFalse(timer.state.value.isActive)
    }

    @Test
    fun testTimerExtension() {
        val mock = MockAudioTarget()
        val timer = SleepTimer(audioTarget = mock)

        timer.start(SleepTimerTarget.Duration(10), fadeout = true)
        assertEquals(600L, timer.state.value.totalSeconds)

        // Extend by 5 minutes (+300s)
        timer.extend(5)
        assertEquals(900L, timer.state.value.totalSeconds)
        assertEquals(900L, timer.state.value.remainingSeconds)

        // Extend by 15 minutes (+900s)
        timer.extend(15)
        assertEquals(1800L, timer.state.value.totalSeconds)
        assertEquals(1800L, timer.state.value.remainingSeconds)

        timer.cancel()
    }

    @Test
    fun testVolumeFadeCalculation() {
        val mock = MockAudioTarget(initialVolume = 80)
        val timer = SleepTimer(audioTarget = mock)

        // At 30s of 30s fade: full volume
        assertEquals(80, timer.calculateFadedVolume(80, 30L, 30L))

        // At 15s of 30s fade: 50% volume
        assertEquals(40, timer.calculateFadedVolume(80, 15L, 30L))

        // At 6s of 30s fade: 20% volume
        assertEquals(16, timer.calculateFadedVolume(80, 6L, 30L))

        // At 0s of 30s fade: silent
        assertEquals(0, timer.calculateFadedVolume(80, 0L, 30L))
    }

    @Test
    fun testCancelRestoresVolumeWhenFading() {
        val mock = MockAudioTarget(initialVolume = 80)
        val timer = SleepTimer(audioTarget = mock)

        timer.start(SleepTimerTarget.Duration(1), fadeout = true)
        // Manually simulate fading volume drop
        mock.setVolume(20)

        // Cancel with restoreVolume = true (default)
        timer.cancel(restoreVolume = true)
        assertFalse(timer.state.value.isActive)
    }

    @Test
    fun testStartEndOfTrackTimer() {
        val mock = MockAudioTarget(
            currentTimeMs = 120_000L,
            durationMs = 180_000L // 60s remaining
        )
        val timer = SleepTimer(audioTarget = mock)

        timer.start(SleepTimerTarget.EndOfTrack, fadeout = true)
        val state = timer.state.value

        assertTrue(state.isActive)
        assertEquals(60L, state.totalSeconds)
        assertEquals(60L, state.remainingSeconds)
        assertEquals(SleepTimerTarget.EndOfTrack, state.target)

        timer.cancel()
    }

    @Test
    fun testEndOfTrackGuardZeroDuration() {
        val mock = MockAudioTarget(
            currentTimeMs = 0L,
            durationMs = 0L,
            isPlaying = true
        )
        val timer = SleepTimer(audioTarget = mock)

        timer.start(SleepTimerTarget.EndOfTrack, fadeout = false)
        val state = timer.state.value
        assertTrue(state.isActive)
        // With 0 duration, totalSeconds defaults to 1s
        assertEquals(1L, state.totalSeconds)
        // Ensure playback was NOT immediately paused on start
        assertFalse(mock.paused)

        timer.cancel()
    }

    @Test
    fun testEndOfTrackDoesNotTriggerWhenPaused() {
        val mock = MockAudioTarget(
            currentTimeMs = 199_800L,
            durationMs = 200_000L,
            isPlaying = false
        )
        val timer = SleepTimer(audioTarget = mock)

        timer.start(SleepTimerTarget.EndOfTrack, fadeout = false)
        // Since isPlaying is false, pause() must not have been triggered by the timer
        assertFalse(mock.paused)
        timer.cancel()
    }

    @Test
    fun testExtendUpdatesCountdownAndRestoresFadeout() = runBlocking {
        val mock = MockAudioTarget(initialVolume = 80)
        val timer = SleepTimer(audioTarget = mock)

        // Duration(0) coerces to 10s, which is within the 30s fadeout window
        timer.start(SleepTimerTarget.Duration(0), fadeout = true)
        assertEquals(10L, timer.state.value.remainingSeconds)

        // Wait for at least one tick so countdown enters fadeout
        delay(1200L)
        assertTrue(timer.state.value.isFading)
        assertTrue(timer.state.value.remainingSeconds < 10L)
        assertTrue(mock.volume < 80)

        // Extend by 5 minutes (+300s): should exit fadeout, restore volume, and stay extended across ticks
        val beforeExtend = timer.state.value.remainingSeconds
        timer.extend(5)
        assertEquals(beforeExtend + 300L, timer.state.value.remainingSeconds)
        assertFalse(timer.state.value.isFading)
        assertEquals(80, mock.volume)

        delay(1200L)
        assertTrue(timer.state.value.isActive)
        assertTrue(timer.state.value.remainingSeconds >= beforeExtend + 298L)
        assertFalse(timer.state.value.isFading)
        assertEquals(80, mock.volume)

        // Extend by 15 minutes (+900s) and verify countdown continues from the extended value
        val beforeFifteen = timer.state.value.remainingSeconds
        timer.extend(15)
        assertEquals(beforeFifteen + 900L, timer.state.value.remainingSeconds)

        delay(1200L)
        assertTrue(timer.state.value.remainingSeconds >= beforeFifteen + 898L)
        assertFalse(mock.paused)

        timer.cancel()
    }

    @Test
    fun testExtendEndOfTrackSwitchesToDurationCountdown() = runBlocking {
        val mock = MockAudioTarget(
            currentTimeMs = 120_000L,
            durationMs = 180_000L // 60s remaining
        )
        val timer = SleepTimer(audioTarget = mock)

        timer.start(SleepTimerTarget.EndOfTrack, fadeout = true)
        assertEquals(SleepTimerTarget.EndOfTrack, timer.state.value.target)
        assertEquals(60L, timer.state.value.remainingSeconds)

        timer.extend(5)
        assertTrue(timer.state.value.target is SleepTimerTarget.Duration)
        assertEquals(360L, timer.state.value.remainingSeconds)

        // Ensure runEndOfTrackTimer was cancelled and does not overwrite remainingSeconds back to 60
        delay(1200L)
        assertTrue(timer.state.value.isActive)
        assertTrue(timer.state.value.remainingSeconds in 358L..359L)

        timer.cancel()
    }
}
