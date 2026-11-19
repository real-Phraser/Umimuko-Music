package com.lostf1sh.pixelplayeross.desktop.system

import com.lostf1sh.pixelplayeross.desktop.playback.AudioController
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

sealed class SleepTimerTarget {
    data class Duration(val minutes: Int) : SleepTimerTarget()
    object EndOfTrack : SleepTimerTarget()
}

data class SleepTimerState(
    val isActive: Boolean = false,
    val target: SleepTimerTarget? = null,
    val totalSeconds: Long = 0L,
    val remainingSeconds: Long = 0L,
    val fadeoutEnabled: Boolean = true,
    val isFading: Boolean = false
)

interface SleepTimerAudioTarget {
    val volume: Int
    val isPlaying: Boolean
    val currentSongId: String?
    val currentTimeMs: Long
    val durationMs: Long
    fun setVolume(vol: Int)
    fun pause()
}

class AudioControllerTarget(private val audio: AudioController) : SleepTimerAudioTarget {
    override val volume: Int get() = audio.state.value.volume
    override val isPlaying: Boolean get() = audio.state.value.playing
    override val currentSongId: String? get() = audio.state.value.song?.id
    override val currentTimeMs: Long get() = audio.state.value.time
    override val durationMs: Long get() = audio.state.value.duration
    override fun setVolume(vol: Int) = audio.volume(vol)
    override fun pause() = audio.pause()
}

class SleepTimer(
    private val audioTarget: SleepTimerAudioTarget,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    constructor(audio: AudioController, scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())) :
        this(AudioControllerTarget(audio), scope)

    private val _state = MutableStateFlow(SleepTimerState())
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    private var timerJob: Job? = null
    private var initialVolume: Int? = null

    /**
     * Start a sleep timer with the given [target].
     * If [fadeout] is enabled, volume will smoothly fade out over the last 30 seconds.
     */
    fun start(target: SleepTimerTarget, fadeout: Boolean = true) {
        cancel(restoreVolume = true)

        val currentVol = audioTarget.volume
        initialVolume = currentVol

        val totalSecs = when (target) {
            is SleepTimerTarget.Duration -> (target.minutes * 60L).coerceAtLeast(10L)
            SleepTimerTarget.EndOfTrack -> {
                val duration = audioTarget.durationMs
                val time = audioTarget.currentTimeMs
                val remainingMs = if (duration > 1000L) (duration - time).coerceAtLeast(0L) else 0L
                (remainingMs / 1000L).coerceAtLeast(1L)
            }
        }

        _state.value = SleepTimerState(
            isActive = true,
            target = target,
            totalSeconds = totalSecs,
            remainingSeconds = totalSecs,
            fadeoutEnabled = fadeout,
            isFading = false
        )

        timerJob = scope.launch {
            when (target) {
                is SleepTimerTarget.Duration -> runDurationTimer(totalSecs, fadeout)
                SleepTimerTarget.EndOfTrack -> runEndOfTrackTimer(fadeout)
            }
        }
    }

    /**
     * Extend an active duration timer by [additionalMinutes].
     */
    fun extend(additionalMinutes: Int) {
        val current = _state.value
        if (!current.isActive) return

        val addSecs = additionalMinutes * 60L
        val newRemaining = current.remainingSeconds + addSecs
        val newTotal = current.totalSeconds + addSecs

        // If we were fading, restore volume back to normal since we now have more time
        if (current.isFading && newRemaining > 30) {
            initialVolume?.let { audioTarget.setVolume(it) }
        }

        _state.value = current.copy(
            totalSeconds = newTotal,
            remainingSeconds = newRemaining,
            isFading = newRemaining <= 30 && current.fadeoutEnabled
        )
    }

    /**
     * Cancel any active sleep timer, optionally restoring original volume.
     */
    fun cancel(restoreVolume: Boolean = true) {
        timerJob?.cancel()
        timerJob = null

        if (restoreVolume && _state.value.isFading) {
            initialVolume?.let { audioTarget.setVolume(it) }
        }
        initialVolume = null

        _state.value = SleepTimerState()
    }

    private suspend fun runDurationTimer(totalSecs: Long, fadeout: Boolean) {
        var remaining = totalSecs
        val fadeDuration = 30L

        while (remaining > 0) {
            delay(1000L)
            remaining -= 1

            val isFading = fadeout && remaining <= fadeDuration
            if (isFading) {
                applyFadeVolume(remaining, fadeDuration)
            } else {
                initialVolume = audioTarget.volume
            }

            _state.value = _state.value.copy(
                remainingSeconds = remaining,
                isFading = isFading
            )
        }

        finishTimer()
    }

    private suspend fun runEndOfTrackTimer(fadeout: Boolean) {
        val initialSongId = audioTarget.currentSongId
        val fadeDuration = 30L

        while (true) {
            delay(500L)
            val currentSongId = audioTarget.currentSongId

            // If track changed or playback stopped completely
            if (currentSongId == null || currentSongId != initialSongId) {
                finishTimer()
                return
            }

            // Do not trigger end-of-track if playback is paused
            if (!audioTarget.isPlaying) {
                continue
            }

            val durationMs = audioTarget.durationMs
            val currentTimeMs = audioTarget.currentTimeMs

            // Guard: wait until LibVLC reports a valid track duration (> 1000ms)
            if (durationMs <= 1000L) {
                continue
            }

            val remainingMs = (durationMs - currentTimeMs).coerceAtLeast(0L)
            val remainingSecs = remainingMs / 1000L

            val isFading = fadeout && remainingSecs <= fadeDuration
            if (isFading) {
                applyFadeVolume(remainingSecs, fadeDuration)
            } else {
                initialVolume = audioTarget.volume
            }

            _state.value = _state.value.copy(
                remainingSeconds = remainingSecs,
                isFading = isFading
            )

            if (remainingMs <= 400L) {
                finishTimer()
                return
            }
        }
    }

    fun calculateFadedVolume(baseVol: Int, remainingSecs: Long, fadeDuration: Long = 30L): Int {
        val factor = (remainingSecs.toDouble() / fadeDuration.toDouble()).coerceIn(0.0, 1.0)
        return (baseVol * factor).roundToInt().coerceIn(0, 100)
    }

    private fun applyFadeVolume(remainingSecs: Long, fadeDuration: Long) {
        val baseVol = initialVolume ?: audioTarget.volume
        val fadedVol = calculateFadedVolume(baseVol, remainingSecs, fadeDuration)
        audioTarget.setVolume(fadedVol)
    }

    private suspend fun finishTimer() {
        val savedVol = initialVolume
        audioTarget.pause()
        delay(150L)
        if (savedVol != null) {
            audioTarget.setVolume(savedVol)
        }
        initialVolume = null
        _state.value = SleepTimerState()
    }

    /**
     * Call if user manually adjusts volume while timer is running (before fade begins),
     * so that the restored volume matches their new intended level.
     */
    fun onUserVolumeChanged(newVolume: Int) {
        if (!_state.value.isFading) {
            initialVolume = newVolume
        }
    }

    companion object {
        fun formatRemaining(seconds: Long): String {
            if (seconds <= 0) return "0:00"
            val m = seconds / 60
            val s = seconds % 60
            return "%d:%02d".format(m, s)
        }
    }
}
