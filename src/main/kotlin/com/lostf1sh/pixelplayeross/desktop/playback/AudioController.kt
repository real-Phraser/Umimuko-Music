package com.lostf1sh.pixelplayeross.desktop.playback

import com.lostf1sh.pixelplayeross.desktop.library.DesktopSong
import com.lostf1sh.pixelplayeross.desktop.library.PlatformPaths
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.base.State
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

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
)

data class QueueSnapshot(val tracks: List<DesktopSong> = emptyList(), val index: Int = -1, val keys: List<Long> = emptyList())

/** All native calls run on one worker. VLC callbacks only pass messages to that worker. */
class AudioController : AutoCloseable {
    private val worker = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "pixel-audio").apply { isDaemon = true }
    }
    private val closed = AtomicBoolean(false)
    private val nativeError = AtomicReference<String?>(null)
    private var factory: MediaPlayerFactory? = null
    private var player: MediaPlayer? = null
    private val queue = EditableQueue<DesktopSong>()
    private val index get() = queue.index()
    private var pendingSeekTime: Long? = null
    private var endHandled = false
    private val mutable = MutableStateFlow(PlaybackState())
    val state = mutable.asStateFlow()
    private val queueMutable = MutableStateFlow(QueueSnapshot())
    val queueState = queueMutable.asStateFlow()

    init {
        worker.execute {
            try {
                val bundled = configureBundledVlc()
                val arguments = mutableListOf("--no-video", "--no-media-library", "--file-caching=500")
                if (PlatformPaths.isWindows()) {
                    val output = System.getenv("PIXELPLAYER_AUDIO_OUTPUT")?.lowercase() ?: "directsound"
                    if (output in setOf("directsound", "mmdevice")) arguments += "--aout=$output"
                }
                factory = if (bundled) MediaPlayerFactory(null as NativeDiscovery?, *arguments.toTypedArray())
                    else MediaPlayerFactory(*arguments.toTypedArray())
                player = factory!!.mediaPlayers().newMediaPlayer()
                player!!.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
                    override fun playing(mediaPlayer: MediaPlayer) {
                        // Audio output is asynchronous. Reapply the requested level on our worker,
                        // never from VLC's callback thread, upon the playback notification.
                        command { endHandled=false;restoreAudioLevel(it) }
                    }
                    override fun finished(mediaPlayer: MediaPlayer) {
                        command { handleEnd(it) }
                    }
                    override fun audioDeviceChanged(mediaPlayer: MediaPlayer, audioDevice: String?) {
                        command { restoreAudioLevel(it) }
                    }
                    override fun error(mediaPlayer: MediaPlayer) {
                        nativeError.set("VLC could not play this file. Try another file or check its path.")
                    }
                })
                player!!.audio().setVolume(80)
                mutable.value = mutable.value.copy(ready = true)
            } catch (failure: Throwable) {
                val helpMsg = when {
                    PlatformPaths.isMac() -> "VLC was not found. Please install VLC from https://www.videolan.org or run 'brew install --cask vlc', then restart Umimuko Music. "
                    PlatformPaths.isLinux() -> "VLC was not found. Please install VLC using your package manager (e.g. 'sudo apt install vlc' or 'sudo pacman -S vlc'), then restart Umimuko Music. "
                    System.getProperty("compose.application.resources.dir") != null -> "Bundled audio engine unavailable. Extract the entire Umimuko Music folder or reinstall. "
                    else -> "Audio engine unavailable. Install VLC 3 (64-bit), then restart Umimuko Music. "
                }
                mutable.value = mutable.value.copy(error = helpMsg + (failure.message ?: failure.javaClass.simpleName))
                player?.release()
                player = null
                factory?.release()
                factory = null
            }
        }
        worker.scheduleWithFixedDelay({
            if (!closed.get()) commandNow {
                val mediaState=it.status().state()
                if(mediaState in setOf(State.OPENING,State.BUFFERING,State.PLAYING,State.PAUSED)) endHandled=false
                if (mediaState == State.ENDED) handleEnd(it)
                pendingSeekTime?.let { requested ->
                    val length=it.status().length()
                    if(it.status().isSeekable && length>0) {
                        it.controls().setTime(requested.coerceIn(0,length-1));pendingSeekTime=null
                    }
                }
                mutable.value = mutable.value.copy(
                    playing = it.status().isPlaying,
                    time = it.status().time().coerceAtLeast(0),
                    duration = it.status().length().coerceAtLeast(0),
                    seekable = it.status().isSeekable,
                    error = nativeError.get() ?: mutable.value.error,
                )
            }
        }, 0, 250, TimeUnit.MILLISECONDS)
    }

    private fun handleEnd(current:MediaPlayer) {
        if(endHandled || index < 0)return
        endHandled=true
        if(mutable.value.repeat == RepeatMode.ONE) start(current)
        else if(queue.step(1,mutable.value.repeat == RepeatMode.ALL)) start(current)
    }

    private fun publishQueue() {
        queueMutable.value=QueueSnapshot(queue.tracks(),index,queue.entries().map { it.key() })
        mutable.value=mutable.value.copy(shuffled=queue.shuffled(),previous=index>=0,
            next=index>=0 && (index<queue.size()-1 || mutable.value.repeat==RepeatMode.ALL))
    }

    private fun commandNow(action: (MediaPlayer) -> Unit) {
        val current = player ?: return
        try { action(current) } catch (failure: Exception) {
            mutable.value = mutable.value.copy(error = failure.message ?: "Audio command failed.")
        }
    }

    private fun command(action: (MediaPlayer) -> Unit) {
        if (!closed.get()) {
            try { worker.execute { if (!closed.get()) commandNow(action) } }
            catch (rejected: RejectedExecutionException) { if (!closed.get()) throw rejected }
        }
    }

    fun play(song: DesktopSong, library: List<DesktopSong>) = command {
        val candidate=library.indexOfFirst { track -> track.id==song.id }
        if(candidate>=0) {queue.replace(library,candidate,mutable.value.shuffled);start(it)}
    }

    fun restore(tracks: List<DesktopSong>,selected:Int,time:Long) = command {
        if(tracks.isNotEmpty()) {queue.replace(tracks,selected, false);start(it,time)}
    }

    private fun restoreAudioLevel(current: MediaPlayer) {
        current.audio().setMute(false)
        current.audio().setVolume(mutable.value.volume)
        current.audio().setDelay(0)
    }

    private fun start(current: MediaPlayer,seekTo:Long=0) {
        val song = queue.current() ?: return
        pendingSeekTime=seekTo.takeIf { it>0 }
        // Ignore a stale ENDED state until the new media starts opening/playing.
        endHandled = true
        publishQueue()
        nativeError.set(null)
        mutable.value = mutable.value.copy(song = song, time = 0, duration = 0,
            playing = false, seekable = false,
            previous = index >= 0,
            next = index < queue.size()-1 || mutable.value.repeat == RepeatMode.ALL, error = null)
        restoreAudioLevel(current)
        if (!current.media().play(song.path)) {
            mutable.value = mutable.value.copy(error = "VLC could not open this file.")
        }
    }

    fun jumpTo(target: Int) = command {
        if (queue.select(target)) start(it)
    }
    fun jumpToKey(key:Long) = command {if(queue.selectKey(key))start(it)}

    fun toggle() = command {
        if (mutable.value.song != null) {
            if (it.status().isPlaying) it.controls().setPause(true) else it.controls().play()
        }
    }
    fun resume() = command {if(mutable.value.song!=null && !it.status().isPlaying) it.controls().play()}
    fun pause() = command {if(it.status().isPlaying) it.controls().setPause(true)}
    fun previous() = command {
        if(index < 0)return@command
        if(it.status().time() > 3000 || index == 0) {
            if(it.status().isSeekable) it.controls().setTime(0) else start(it)
        } else if(queue.step(-1,false)) start(it)
    }
    fun next() = command {if(queue.step(1,mutable.value.repeat==RepeatMode.ALL)) start(it)}
    fun playNext(song: DesktopSong) = command {
        val empty=queue.current()==null
        queue.insertNext(song)
        if(empty)start(it) else publishQueue()
    }
    fun appendQueue(songs: List<DesktopSong>) = command {
        if(songs.isEmpty())return@command
        if(queue.current()==null){queue.replace(songs,0,mutable.value.shuffled);start(it)}
        else {queue.append(songs);publishQueue()}
    }
    fun playNextAll(songs:List<DesktopSong>) = command {
        if(songs.isEmpty())return@command
        if(queue.current()==null){queue.replace(songs,0,mutable.value.shuffled);start(it)}
        else {songs.asReversed().forEach(queue::insertNext);publishQueue()}
    }
    fun moveQueue(key:Long,target:Long) = command {
        val targetIndex=queue.entries().indexOfFirst { entry -> entry.key()==target }
        if(targetIndex>=0){queue.moveTo(key,targetIndex);publishQueue()}
    }
    fun moveQueueBy(key:Long,delta:Int) = command {queue.moveBy(key,delta);publishQueue()}
    fun removeQueue(keys:Set<Long>) = command {
        val changed=queue.remove(keys)
        if(changed && queue.current()!=null)start(it)
        else if(queue.current()==null) {
            it.controls().stop();pendingSeekTime=null
            mutable.value=mutable.value.copy(song=null,playing=false,time=0,duration=0,seekable=false)
            publishQueue()
        } else publishQueue()
    }
    fun clearPlayed() = command {queue.clearPlayed();publishQueue()}
    fun cycleRepeat() = command {
        val mode=when(mutable.value.repeat) {RepeatMode.OFF->RepeatMode.ALL;RepeatMode.ALL->RepeatMode.ONE;RepeatMode.ONE->RepeatMode.OFF}
        mutable.value=mutable.value.copy(repeat=mode);publishQueue()
    }
    fun toggleShuffle() = command {queue.toggleShuffle();publishQueue()}
    fun seek(fraction: Float) = command {
        if (it.status().isSeekable) it.controls().setTime((it.status().length() * fraction.coerceIn(0f, 1f)).toLong())
    }
    fun volume(value: Int) = command {
        val safe = value.coerceIn(0, 100)
        it.audio().setVolume(safe)
        mutable.value = mutable.value.copy(volume = safe)
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            worker.execute {
                try { player?.release() } finally { factory?.release() }
                player = null
                factory = null
            }
            worker.shutdown()
        }
    }
}
