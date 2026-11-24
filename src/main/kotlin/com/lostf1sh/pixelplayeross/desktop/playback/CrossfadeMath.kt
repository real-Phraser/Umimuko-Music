package com.lostf1sh.pixelplayeross.desktop.playback

import com.lostf1sh.pixelplayeross.desktop.dsp.CrossfadeCurve
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

internal data class CrossfadeVolumes(
    val outgoing: Int,
    val incoming: Int
)

internal object CrossfadeMath {
    const val TICK_INTERVAL_MS = 30L
    const val MIN_TRACK_DURATION_MS = 3000L
    const val MIN_CROSSFADE_DURATION_MS = 200L
    const val TRIGGER_MIN_REMAINING_MS = 50L
    const val COMPLETE_REMAINING_MS = 40L

    fun computeVolumes(progress: Float, masterVolume: Int, curve: CrossfadeCurve): CrossfadeVolumes {
        val clampedProgress = progress.coerceIn(0f, 1f)
        val master = masterVolume.coerceIn(0, 100).toFloat()
        val (out, inc) = when (curve) {
            CrossfadeCurve.EQUAL_POWER -> {
                val angle = clampedProgress * (Math.PI.toFloat() / 2f)
                (master * cos(angle)).roundToInt() to (master * sin(angle)).roundToInt()
            }
            CrossfadeCurve.LINEAR -> {
                (master * (1f - clampedProgress)).roundToInt() to (master * clampedProgress).roundToInt()
            }
        }
        return CrossfadeVolumes(
            outgoing = out.coerceIn(0, 100),
            incoming = inc.coerceIn(0, 100)
        )
    }

    fun wallClockRemainingMs(durationMs: Long, positionMs: Long, playbackRate: Float): Long {
        if (durationMs <= 0L || positionMs < 0L) return 0L
        val mediaRemaining = (durationMs - positionMs).coerceAtLeast(0L)
        val safeRate = playbackRate.coerceAtLeast(0.5f)
        return (mediaRemaining / safeRate).toLong()
    }

    fun shouldTriggerCrossfade(
        isPlaying: Boolean,
        isCrossfading: Boolean,
        repeatMode: RepeatMode,
        durationMs: Long,
        positionMs: Long,
        playbackRate: Float,
        crossfadeDurationSeconds: Float,
        hasNextTrack: Boolean
    ): Long? {
        if (!isPlaying || isCrossfading || repeatMode == RepeatMode.ONE || !hasNextTrack) return null
        if (durationMs <= MIN_TRACK_DURATION_MS || positionMs <= 0L) return null
        val crossfadeMs = (crossfadeDurationSeconds * 1000f).toLong()
        if (crossfadeMs <= MIN_CROSSFADE_DURATION_MS) return null

        val wallRemainingMs = wallClockRemainingMs(durationMs, positionMs, playbackRate)
        if (wallRemainingMs !in TRIGGER_MIN_REMAINING_MS..crossfadeMs) return null
        return wallRemainingMs.coerceAtMost(crossfadeMs).coerceAtLeast(1L)
    }

    fun computeProgress(elapsedMs: Long, totalDurationMs: Long): Float {
        return (elapsedMs.coerceAtLeast(0L).toFloat() / totalDurationMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
    }

    fun shouldCompleteCrossfade(progress: Float, wallRemainingMs: Long): Boolean {
        return progress >= 1.0f || wallRemainingMs <= COMPLETE_REMAINING_MS
    }
}
