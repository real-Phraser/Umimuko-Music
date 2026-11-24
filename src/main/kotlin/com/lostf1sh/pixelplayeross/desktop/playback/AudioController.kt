package com.lostf1sh.pixelplayeross.desktop.playback

import com.lostf1sh.pixelplayeross.desktop.StartupPrefs
import com.lostf1sh.pixelplayeross.desktop.dsp.*
import com.lostf1sh.pixelplayeross.desktop.library.DesktopSong
import com.lostf1sh.pixelplayeross.desktop.library.PlatformPaths
import com.lostf1sh.pixelplayeross.desktop.stats.ListeningStatsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.Equalizer
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.base.State
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private fun parseBands(raw: String): List<Float> {
    val parsed = raw.split(",").mapNotNull { it.trim().toFloatOrNull() }
    return if (parsed.size == 10) parsed else List(10) { 0.0f }
}

enum class RepeatMode { OFF, ALL, ONE }

data class PlaybackState(
    val ready: Boolean = false,
    val song: DesktopSong? = null,
    val playing: Boolean = false,
    val time: Long = 0,
    val duration: Long = 0,
    val seekable: Boolean = false,
    val volume: Int = 80,
    val previous: Boolean = false,
    val next: Boolean = false,
    val error: String? = null,
    val repeat: RepeatMode = RepeatMode.OFF,
    val shuffled: Boolean = false,
    val rate: Float = 1.0f,
)

data class QueueSnapshot(
    val tracks: List<DesktopSong> = emptyList(),
    val index: Int = -1,
    val keys: List<Long> = emptyList()
)

/**
 * High-performance audio controller wrapping LibVLC via vlcj.
 * Features:
 * - Dual-player architecture for seamless gapless transitions & smooth equal-power crossfades.
 * - 10-band hardware DSP Equalizer with presets.
 * - Real-time playback speed rate control (0.5x to 2.0x).
 * - Automatic listening stats and analytics tracking.
 */
class AudioController : AutoCloseable {
    private val worker = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "pixel-audio").apply { isDaemon = true }
    }
    private val closed = AtomicBoolean(false)
    private val nativeError = AtomicReference<String?>(null)
    private var factory: MediaPlayerFactory? = null

    // Dual MediaPlayer orchestrator
    private var primaryPlayer: MediaPlayer? = null
    private var standbyPlayer: MediaPlayer? = null
    private var activeEqualizer: Equalizer? = null

    private val queue = EditableQueue<DesktopSong>()
    private val index get() = queue.index()
    private var pendingSeekTime: Long? = null
    private var endHandled = false

    // Crossfading state
    private var isCrossfading = false
    private var crossfadeStartTimeMs = 0L
    private var crossfadePausedAtMs = 0L
    private var crossfadeTotalDurationMs = 0L

    // Listening statistics tracking
    private val statsStore = ListeningStatsStore.defaultStore()
    private val eqStore = EqPresetStore.defaultStore()
    private var trackStartedEpochMs = 0L
    private var trackListenedAccumulatedMs = 0L
    private var lastTickTimeMs = 0L

    private val mutable = MutableStateFlow(PlaybackState())
    val state = mutable.asStateFlow()

    private val queueMutable = MutableStateFlow(QueueSnapshot())
    val queueState = queueMutable.asStateFlow()

    private val audioSettingsMutable = MutableStateFlow(
        AudioSettingsState(
            equalizer = EqualizerConfig(
                enabled = StartupPrefs.eqEnabled,
                preamp = StartupPrefs.eqPreamp,
                bands = parseBands(StartupPrefs.eqCustomBands),
                activePreset = StartupPrefs.eqPreset
            ),
            crossfade = CrossfadeConfig(
                durationSeconds = StartupPrefs.crossfadeDuration,
                curve = CrossfadeCurve.entries.firstOrNull { it.name == StartupPrefs.crossfadeCurve }
                    ?: CrossfadeCurve.EQUAL_POWER
            ),
            playbackRate = StartupPrefs.playbackSpeed
        )
    )
    val audioSettings = audioSettingsMutable.asStateFlow()

    init {
        worker.execute {
            try {
                val bundled = configureBundledVlc()
                val arguments = mutableListOf(
                    "--no-video",
                    "--no-media-library",
                    "--file-caching=500",
                    "--audio-resampler=soxr"
                )
                if (PlatformPaths.isWindows()) {
                    val output = System.getenv("PIXELPLAYER_AUDIO_OUTPUT")?.lowercase() ?: "directsound"
                    if (output in setOf("directsound", "mmdevice")) arguments += "--aout=$output"
                }
                factory = if (bundled) MediaPlayerFactory(null as NativeDiscovery?, *arguments.toTypedArray())
                else MediaPlayerFactory(*arguments.toTypedArray())

                primaryPlayer = factory!!.mediaPlayers().newMediaPlayer()
                standbyPlayer = factory!!.mediaPlayers().newMediaPlayer()

                setupPlayerListeners(primaryPlayer!!, isPrimary = true)
                setupPlayerListeners(standbyPlayer!!, isPrimary = false)

                primaryPlayer!!.audio().setVolume(80)
                standbyPlayer!!.audio().setVolume(0)

                initEqualizer()

                mutable.value = mutable.value.copy(ready = true)
            } catch (failure: Throwable) {
                val helpMsg = when {
                    PlatformPaths.isMac() -> "VLC was not found. Please install VLC from https://www.videolan.org or run 'brew install --cask vlc', then restart Umimuko Music. "
                    PlatformPaths.isLinux() -> "VLC was not found. Please install VLC using your package manager (e.g. 'sudo apt install vlc' or 'sudo pacman -S vlc'), then restart Umimuko Music. "
                    System.getProperty("compose.application.resources.dir") != null -> "Bundled audio engine unavailable. Extract the entire Umimuko Music folder or reinstall. "
                    else -> "Audio engine unavailable. Install VLC 3 (64-bit), then restart Umimuko Music. "
                }
                mutable.value = mutable.value.copy(error = helpMsg + (failure.message ?: failure.javaClass.simpleName))
                primaryPlayer?.release()
                primaryPlayer = null
                standbyPlayer?.release()
                standbyPlayer = null
                factory?.release()
                factory = null
            }
        }

        // Periodic state & crossfade driver (~30ms for smooth volume ramping)
        worker.scheduleWithFixedDelay({
            if (!closed.get()) commandNow {
                val active = primaryPlayer ?: return@commandNow
                val mediaState = active.status().state()
                if (mediaState in setOf(State.OPENING, State.BUFFERING, State.PLAYING, State.PAUSED)) {
                    endHandled = false
                }
                if (mediaState == State.ENDED) {
                    handleEnd(active)
                }
                pendingSeekTime?.let { requested ->
                    val length = active.status().length()
                    if (active.status().isSeekable && length > 0) {
                        active.controls().setTime(requested.coerceIn(0, length - 1))
                        pendingSeekTime = null
                    }
                }

                // Update listen duration stats
                val now = System.currentTimeMillis()
                if (active.status().isPlaying && lastTickTimeMs > 0L) {
                    val delta = (now - lastTickTimeMs).coerceIn(0, 500)
                    trackListenedAccumulatedMs += delta
                }
                lastTickTimeMs = now

                // Crossfade & Gapless lookahead orchestration
                checkCrossfadeLookahead(active)

                val currentPrimary = primaryPlayer ?: active
                mutable.value = mutable.value.copy(
                    playing = currentPrimary.status().isPlaying,
                    time = currentPrimary.status().time().coerceAtLeast(0),
                    duration = currentPrimary.status().length().coerceAtLeast(0),
                    seekable = currentPrimary.status().isSeekable,
                    error = nativeError.get() ?: mutable.value.error,
                    rate = audioSettingsMutable.value.playbackRate
                )
            }
        }, 0, CrossfadeMath.TICK_INTERVAL_MS, TimeUnit.MILLISECONDS)
    }

    private fun currentCrossfadeVolumes(): CrossfadeVolumes {
        val elapsed = if (crossfadePausedAtMs > 0L) {
            crossfadePausedAtMs - crossfadeStartTimeMs
        } else {
            System.currentTimeMillis() - crossfadeStartTimeMs
        }
        val progress = CrossfadeMath.computeProgress(elapsed, crossfadeTotalDurationMs)
        return CrossfadeMath.computeVolumes(
            progress = progress,
            masterVolume = mutable.value.volume,
            curve = audioSettingsMutable.value.crossfade.curve
        )
    }

    private fun applyCrossfadeVolumes(volumes: CrossfadeVolumes) {
        primaryPlayer?.audio()?.let { audio ->
            audio.setMute(false)
            audio.setVolume(volumes.outgoing)
        }
        standbyPlayer?.audio()?.let { audio ->
            audio.setMute(false)
            audio.setVolume(volumes.incoming)
        }
    }

    private fun cancelCrossfade(restorePrimary: Boolean = true) {
        if (!isCrossfading) return
        isCrossfading = false
        crossfadePausedAtMs = 0L
        standbyPlayer?.let { standby ->
            standby.controls().stop()
            standby.audio().setVolume(0)
        }
        if (restorePrimary) {
            primaryPlayer?.let { restoreAudioLevel(it) }
        }
    }

    private fun setupPlayerListeners(player: MediaPlayer, isPrimary: Boolean) {
        player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer) {
                command {
                    when (mediaPlayer) {
                        primaryPlayer -> {
                            endHandled = false
                            if (isCrossfading) {
                                applyCrossfadeVolumes(currentCrossfadeVolumes())
                            } else {
                                restoreAudioLevel(mediaPlayer)
                            }
                        }
                        standbyPlayer -> {
                            if (isCrossfading) {
                                mediaPlayer.controls().setRate(audioSettingsMutable.value.playbackRate)
                                applyEqualizerToPlayers()
                                applyCrossfadeVolumes(currentCrossfadeVolumes())
                            } else {
                                mediaPlayer.audio().setVolume(0)
                            }
                        }
                    }
                }
            }
            override fun finished(mediaPlayer: MediaPlayer) {
                command {
                    if (mediaPlayer == primaryPlayer) {
                        handleEnd(mediaPlayer)
                    }
                }
            }
            override fun audioDeviceChanged(mediaPlayer: MediaPlayer, audioDevice: String?) {
                command {
                    when (mediaPlayer) {
                        primaryPlayer -> {
                            if (isCrossfading) {
                                applyCrossfadeVolumes(currentCrossfadeVolumes())
                            } else {
                                restoreAudioLevel(mediaPlayer)
                            }
                        }
                        standbyPlayer -> {
                            if (isCrossfading) {
                                applyCrossfadeVolumes(currentCrossfadeVolumes())
                            } else {
                                mediaPlayer.audio().setVolume(0)
                            }
                        }
                    }
                }
            }
            override fun error(mediaPlayer: MediaPlayer) {
                command {
                    when (mediaPlayer) {
                        primaryPlayer -> {
                            nativeError.set("VLC could not play this file. Try another file or check its path.")
                        }
                        standbyPlayer -> {
                            if (isCrossfading) {
                                cancelCrossfade(restorePrimary = true)
                            }
                        }
                    }
                }
            }
        })
    }

    private fun initEqualizer() {
        val f = factory ?: return
        try {
            activeEqualizer = f.equalizer().newEqualizer()
            val eqState = audioSettingsMutable.value.equalizer
            activeEqualizer?.setPreamp(EqualizerDefaults.toLibVlcPreamp(eqState.preamp))
            eqState.bands.forEachIndexed { idx, gain ->
                activeEqualizer?.setAmp(idx, gain.coerceIn(EqualizerDefaults.LIBVLC_MIN_AMP_DB, EqualizerDefaults.LIBVLC_MAX_AMP_DB))
            }
            applyEqualizerToPlayers()
        } catch (_: Throwable) {}
    }

    private fun applyEqualizerToPlayers() {
        val eq = if (audioSettingsMutable.value.equalizer.enabled) activeEqualizer else null
        primaryPlayer?.audio()?.setEqualizer(eq)
        standbyPlayer?.audio()?.setEqualizer(eq)
    }

    private fun checkCrossfadeLookahead(active: MediaPlayer) {
        val duration = active.status().length()
        val time = active.status().time()
        val playbackRate = audioSettingsMutable.value.playbackRate

        // Case 1: Active crossfade interpolation tick
        if (isCrossfading) {
            if (!active.status().isPlaying || crossfadePausedAtMs > 0L) return

            val elapsed = System.currentTimeMillis() - crossfadeStartTimeMs
            val progress = CrossfadeMath.computeProgress(elapsed, crossfadeTotalDurationMs)
            val volumes = CrossfadeMath.computeVolumes(
                progress = progress,
                masterVolume = mutable.value.volume,
                curve = audioSettingsMutable.value.crossfade.curve
            )
            applyCrossfadeVolumes(volumes)

            val wallRemainingMs = CrossfadeMath.wallClockRemainingMs(duration, time, playbackRate)
            if (CrossfadeMath.shouldCompleteCrossfade(progress, wallRemainingMs)) {
                completeCrossfade()
            }
            return
        }

        // Case 2: Triggering new crossfade window
        val hasNext = (index < queue.size() - 1) || (mutable.value.repeat == RepeatMode.ALL)
        val effectiveDurationMs = CrossfadeMath.shouldTriggerCrossfade(
            isPlaying = active.status().isPlaying,
            isCrossfading = isCrossfading,
            repeatMode = mutable.value.repeat,
            durationMs = duration,
            positionMs = time,
            playbackRate = playbackRate,
            crossfadeDurationSeconds = audioSettingsMutable.value.crossfade.durationSeconds,
            hasNextTrack = hasNext
        ) ?: return

        val standby = standbyPlayer ?: return
        val nextIndex = if (index < queue.size() - 1) index + 1 else 0
        val nextSong = queue.tracks().getOrNull(nextIndex) ?: return

        isCrossfading = true
        crossfadeStartTimeMs = System.currentTimeMillis()
        crossfadePausedAtMs = 0L
        crossfadeTotalDurationMs = effectiveDurationMs

        standby.audio().setVolume(0)
        standby.controls().setRate(playbackRate)
        standby.audio().setEqualizer(if (audioSettingsMutable.value.equalizer.enabled) activeEqualizer else null)
        if (!standby.media().play(nextSong.path)) {
            cancelCrossfade(restorePrimary = true)
        }
    }

    private fun completeCrossfade() {
        isCrossfading = false
        crossfadePausedAtMs = 0L
        endHandled = false
        val currentPrimary = primaryPlayer ?: return
        val currentStandby = standbyPlayer ?: return

        finalizeSongListeningStats()

        currentPrimary.controls().stop()
        currentPrimary.audio().setVolume(0)

        // Swap players
        primaryPlayer = currentStandby
        standbyPlayer = currentPrimary

        // Advance queue
        queue.step(1, mutable.value.repeat == RepeatMode.ALL)
        val newSong = queue.current()

        trackStartedEpochMs = System.currentTimeMillis()
        trackListenedAccumulatedMs = 0L
        lastTickTimeMs = trackStartedEpochMs

        restoreAudioLevel(primaryPlayer!!)
        publishQueue()

        mutable.value = mutable.value.copy(
            song = newSong,
            time = primaryPlayer!!.status().time().coerceAtLeast(0),
            duration = primaryPlayer!!.status().length().coerceAtLeast(0),
            playing = true,
            seekable = primaryPlayer!!.status().isSeekable,
            previous = index >= 0,
            next = index < queue.size() - 1 || mutable.value.repeat == RepeatMode.ALL,
            error = null
        )
    }

    private fun finalizeSongListeningStats() {
        val currentSong = mutable.value.song ?: return
        val duration = mutable.value.duration
        val listened = trackListenedAccumulatedMs

        if (listened >= 10000L || (duration > 0 && listened >= duration / 2)) {
            statsStore.recordPlay(currentSong, listened)
        } else if (listened in 1000L..9999L) {
            statsStore.recordSkip(currentSong)
        }
        trackListenedAccumulatedMs = 0L
    }

    private fun handleEnd(current: MediaPlayer) {
        if (endHandled || index < 0) return
        endHandled = true

        if (isCrossfading) {
            completeCrossfade()
            return
        }

        finalizeSongListeningStats()

        if (mutable.value.repeat == RepeatMode.ONE) {
            start(current)
        } else if (queue.step(1, mutable.value.repeat == RepeatMode.ALL)) {
            start(current)
        }
    }

    private fun publishQueue() {
        queueMutable.value = QueueSnapshot(queue.tracks(), index, queue.entries().map { it.key() })
        mutable.value = mutable.value.copy(
            shuffled = queue.shuffled(),
            previous = index >= 0,
            next = index >= 0 && (index < queue.size() - 1 || mutable.value.repeat == RepeatMode.ALL)
        )
    }

    private fun commandNow(action: (MediaPlayer) -> Unit) {
        val current = primaryPlayer ?: return
        try {
            action(current)
        } catch (failure: Exception) {
            mutable.value = mutable.value.copy(error = failure.message ?: "Audio command failed.")
        }
    }

    private fun command(action: (MediaPlayer) -> Unit) {
        if (!closed.get()) {
            try {
                worker.execute { if (!closed.get()) commandNow(action) }
            } catch (rejected: RejectedExecutionException) {
                if (!closed.get()) throw rejected
            }
        }
    }

    fun play(song: DesktopSong, library: List<DesktopSong>) = command {
        finalizeSongListeningStats()
        cancelCrossfade(restorePrimary = false)
        val candidate = library.indexOfFirst { track -> track.id == song.id }
        if (candidate >= 0) {
            queue.replace(library, candidate, mutable.value.shuffled)
            start(it)
        }
    }

    fun restore(tracks: List<DesktopSong>, selected: Int, time: Long) = command {
        if (tracks.isNotEmpty()) {
            queue.replace(tracks, selected, false)
            start(it, time)
        }
    }

    private fun restoreAudioLevel(current: MediaPlayer) {
        current.audio().setMute(false)
        current.audio().setVolume(mutable.value.volume)
        current.audio().setDelay(0)
        current.controls().setRate(audioSettingsMutable.value.playbackRate)
        applyEqualizerToPlayers()
    }

    private fun start(current: MediaPlayer, seekTo: Long = 0) {
        val song = queue.current() ?: return
        finalizeSongListeningStats()
        cancelCrossfade(restorePrimary = false)

        trackStartedEpochMs = System.currentTimeMillis()
        trackListenedAccumulatedMs = 0L
        lastTickTimeMs = trackStartedEpochMs

        pendingSeekTime = seekTo.takeIf { it > 0 }
        endHandled = true
        publishQueue()
        nativeError.set(null)
        mutable.value = mutable.value.copy(
            song = song,
            time = 0,
            duration = 0,
            playing = false,
            seekable = false,
            previous = index >= 0,
            next = index < queue.size() - 1 || mutable.value.repeat == RepeatMode.ALL,
            error = null
        )
        restoreAudioLevel(current)
        if (!current.media().play(song.path)) {
            mutable.value = mutable.value.copy(error = "VLC could not open this file.")
        }
    }

    fun jumpTo(target: Int) = command {
        if (queue.select(target)) start(it)
    }

    fun jumpToKey(key: Long) = command {
        if (queue.selectKey(key)) start(it)
    }

    fun toggle() = command {
        if (mutable.value.song != null) {
            if (it.status().isPlaying) {
                it.controls().setPause(true)
                if (isCrossfading) {
                    standbyPlayer?.controls()?.setPause(true)
                    crossfadePausedAtMs = System.currentTimeMillis()
                }
            } else {
                if (isCrossfading && crossfadePausedAtMs > 0L) {
                    crossfadeStartTimeMs += (System.currentTimeMillis() - crossfadePausedAtMs)
                    crossfadePausedAtMs = 0L
                    standbyPlayer?.controls()?.play()
                }
                it.controls().play()
            }
        }
    }

    fun resume() = command {
        if (mutable.value.song != null && !it.status().isPlaying) {
            if (isCrossfading && crossfadePausedAtMs > 0L) {
                crossfadeStartTimeMs += (System.currentTimeMillis() - crossfadePausedAtMs)
                crossfadePausedAtMs = 0L
                standbyPlayer?.controls()?.play()
            }
            it.controls().play()
        }
    }

    fun pause() = command {
        if (it.status().isPlaying) {
            it.controls().setPause(true)
            if (isCrossfading) {
                standbyPlayer?.controls()?.setPause(true)
                crossfadePausedAtMs = System.currentTimeMillis()
            }
        }
    }

    fun previous() = command {
        if (index < 0) return@command
        if (it.status().time() > 3000 || index == 0) {
            if (it.status().isSeekable) {
                cancelCrossfade(restorePrimary = true)
                it.controls().setTime(0)
            } else {
                start(it)
            }
        } else if (queue.step(-1, false)) {
            start(it)
        }
    }

    fun next() = command {
        if (queue.step(1, mutable.value.repeat == RepeatMode.ALL)) start(it)
    }

    fun playNext(song: DesktopSong) = command {
        val empty = queue.current() == null
        queue.insertNext(song)
        if (empty) start(it) else publishQueue()
    }

    fun appendQueue(songs: List<DesktopSong>) = command {
        if (songs.isEmpty()) return@command
        if (queue.current() == null) {
            queue.replace(songs, 0, mutable.value.shuffled)
            start(it)
        } else {
            queue.append(songs)
            publishQueue()
        }
    }

    fun playNextAll(songs: List<DesktopSong>) = command {
        if (songs.isEmpty()) return@command
        if (queue.current() == null) {
            queue.replace(songs, 0, mutable.value.shuffled)
            start(it)
        } else {
            songs.asReversed().forEach(queue::insertNext)
            publishQueue()
        }
    }

    fun moveQueue(key: Long, target: Long) = command {
        val targetIndex = queue.entries().indexOfFirst { entry -> entry.key() == target }
        if (targetIndex >= 0) {
            queue.moveTo(key, targetIndex)
            publishQueue()
        }
    }

    fun moveQueueBy(key: Long, delta: Int) = command {
        queue.moveBy(key, delta)
        publishQueue()
    }

    fun removeQueue(keys: Set<Long>) = command {
        val changed = queue.remove(keys)
        if (changed && queue.current() != null) {
            start(it)
        } else if (queue.current() == null) {
            it.controls().stop()
            pendingSeekTime = null
            mutable.value = mutable.value.copy(song = null, playing = false, time = 0, duration = 0, seekable = false)
            publishQueue()
        } else {
            publishQueue()
        }
    }

    fun evictSongs(songIds: Set<String>) = command {
        if (songIds.isEmpty()) return@command
        val keysToRemove = queue.entries().filter { it.value().id in songIds }.map { it.key() }.toSet()
        if (keysToRemove.isNotEmpty()) {
            val changed = queue.remove(keysToRemove)
            if (changed && queue.current() != null) {
                start(it)
            } else if (queue.current() == null) {
                it.controls().stop()
                pendingSeekTime = null
                mutable.value = mutable.value.copy(song = null, playing = false, time = 0, duration = 0, seekable = false)
                publishQueue()
            } else {
                publishQueue()
            }
        }
    }

    fun removeQueueBySongIds(songIds: Set<String>) = evictSongs(songIds)

    fun clearPlayed() = command {
        queue.clearPlayed()
        publishQueue()
    }

    fun cycleRepeat() = command {
        val mode = when (mutable.value.repeat) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        mutable.value = mutable.value.copy(repeat = mode)
        publishQueue()
    }

    fun toggleShuffle() = command {
        queue.toggleShuffle()
        publishQueue()
    }

    fun seek(fraction: Float) = command {
        if (it.status().isSeekable) {
            cancelCrossfade(restorePrimary = true)
            it.controls().setTime((it.status().length() * fraction.coerceIn(0f, 1f)).toLong())
        }
    }

    fun volume(value: Int) = command {
        val safe = value.coerceIn(0, 100)
        mutable.value = mutable.value.copy(volume = safe)
        if (isCrossfading) {
            applyCrossfadeVolumes(currentCrossfadeVolumes())
        } else {
            it.audio().setVolume(safe)
        }
    }

    // --- Audio DSP & Equalizer Controls ---

    fun setPlaybackRate(rate: Float) = command {
        val safe = rate.coerceIn(EqualizerDefaults.MIN_RATE, EqualizerDefaults.MAX_RATE)
        it.controls().setRate(safe)
        standbyPlayer?.controls()?.setRate(safe)
        audioSettingsMutable.value = audioSettingsMutable.value.copy(playbackRate = safe)
        mutable.value = mutable.value.copy(rate = safe)
        StartupPrefs.playbackSpeed = safe
    }

    fun setCrossfadeDuration(seconds: Float) = command {
        val safe = seconds.coerceIn(0f, EqualizerDefaults.MAX_CROSSFADE_SECONDS)
        val current = audioSettingsMutable.value.crossfade
        audioSettingsMutable.value = audioSettingsMutable.value.copy(
            crossfade = current.copy(durationSeconds = safe)
        )
        StartupPrefs.crossfadeDuration = safe
    }

    fun setCrossfadeCurve(curve: CrossfadeCurve) = command {
        val current = audioSettingsMutable.value.crossfade
        audioSettingsMutable.value = audioSettingsMutable.value.copy(
            crossfade = current.copy(curve = curve)
        )
        StartupPrefs.crossfadeCurve = curve.name
    }

    fun setEqualizerEnabled(enabled: Boolean) = command {
        val current = audioSettingsMutable.value.equalizer
        audioSettingsMutable.value = audioSettingsMutable.value.copy(
            equalizer = current.copy(enabled = enabled)
        )
        applyEqualizerToPlayers()
        StartupPrefs.eqEnabled = enabled
    }

    fun setEqualizerPreamp(preampDb: Float) = command {
        val safe = preampDb.coerceIn(EqualizerDefaults.MIN_PREAMP_DB, EqualizerDefaults.MAX_PREAMP_DB)
        val current = audioSettingsMutable.value.equalizer
        audioSettingsMutable.value = audioSettingsMutable.value.copy(
            equalizer = current.copy(preamp = safe, activePreset = "Custom")
        )
        activeEqualizer?.setPreamp(EqualizerDefaults.toLibVlcPreamp(safe))
        applyEqualizerToPlayers()
        StartupPrefs.eqPreamp = safe
        StartupPrefs.eqPreset = "Custom"
    }

    fun setEqualizerBandGain(bandIndex: Int, gainDb: Float) = command {
        if (bandIndex !in 0..9) return@command
        val safe = gainDb.coerceIn(EqualizerDefaults.MIN_GAIN_DB, EqualizerDefaults.MAX_GAIN_DB)
        val current = audioSettingsMutable.value.equalizer
        val newBands = current.bands.toMutableList()
        newBands[bandIndex] = safe
        audioSettingsMutable.value = audioSettingsMutable.value.copy(
            equalizer = current.copy(bands = newBands, activePreset = "Custom")
        )
        activeEqualizer?.setAmp(bandIndex, safe)
        applyEqualizerToPlayers()
        StartupPrefs.eqCustomBands = newBands.joinToString(",")
        StartupPrefs.eqPreset = "Custom"
    }

    fun applyPreset(preset: EqualizerPreset) = command {
        val current = audioSettingsMutable.value.equalizer
        audioSettingsMutable.value = audioSettingsMutable.value.copy(
            equalizer = current.copy(
                preamp = preset.preamp,
                bands = preset.bandGains,
                activePreset = preset.name
            )
        )
        activeEqualizer?.setPreamp(EqualizerDefaults.toLibVlcPreamp(preset.preamp))
        preset.bandGains.forEachIndexed { idx, gain ->
            activeEqualizer?.setAmp(idx, gain.coerceIn(EqualizerDefaults.LIBVLC_MIN_AMP_DB, EqualizerDefaults.LIBVLC_MAX_AMP_DB))
        }
        applyEqualizerToPlayers()
        StartupPrefs.eqPreamp = preset.preamp
        StartupPrefs.eqCustomBands = preset.bandGains.joinToString(",")
        StartupPrefs.eqPreset = preset.name
    }

    fun saveCurrentPreset(name: String): Boolean {
        val current = audioSettingsMutable.value.equalizer
        val preset = EqualizerPreset(
            name = name,
            preamp = current.preamp,
            bandGains = current.bands,
            isBuiltIn = false
        )
        val saved = eqStore.savePreset(preset)
        if (saved) {
            audioSettingsMutable.value = audioSettingsMutable.value.copy(
                equalizer = current.copy(activePreset = name)
            )
            StartupPrefs.eqPreset = name
        }
        return saved
    }

    fun deletePreset(name: String): Boolean {
        return eqStore.deletePreset(name)
    }

    fun getAllPresets(): List<EqualizerPreset> {
        return eqStore.getAllPresets()
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            finalizeSongListeningStats()
            worker.execute {
                try {
                    primaryPlayer?.release()
                    standbyPlayer?.release()
                } finally {
                    factory?.release()
                }
                primaryPlayer = null
                standbyPlayer = null
                factory = null
                activeEqualizer = null
            }
            worker.shutdown()
        }
    }
}
