package com.lostf1sh.pixelplayeross.desktop.library

import com.lostf1sh.pixelplayeross.desktop.playback.AudioController
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

data class HomeState(val data:HomeStore.Snapshot=HomeStore.Snapshot(emptyMap(),emptyList(),emptyList(),
    HomeStore.Session(emptyList(),-1,0)),val ready:Boolean=false,val error:String?=null)

/** Serializes persistence away from the Compose thread; the position file is small. */
class HomeCoordinator(private val audio:AudioController,private val store:HomeStore=HomeStore.defaultStore()):AutoCloseable {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val mutex=Mutex()
    private val mutable=MutableStateFlow(HomeState())
    val state=mutable.asStateFlow()
    private val started=AtomicBoolean(false)
    init {
        scope.launch {
            try {mutable.value=HomeState(store.load(),true)}
            catch(failure:Exception){mutable.value=HomeState(error=failure.message);return@launch}
            launch {
                audio.state.map {if(it.playing)it.song?.id else null}.filterNotNull().distinctUntilChanged().collect { path ->
                    started.set(true);change {store.played(path)}
                }
            }
            launch {
                audio.queueState.collectLatest { queue ->
                    if(queue.tracks.isNotEmpty())started.set(true)
                    if(started.get()){delay(350);persistPosition()}
                }
            }
            launch {while(isActive){delay(10_000);if(started.get())persistPosition()}}
        }
    }
    private suspend fun change(action:()->HomeStore.Snapshot) = mutex.withLock {
        if(!mutable.value.ready)return@withLock
        try {mutable.value=HomeState(action(),true)}
        catch(failure:Exception){mutable.value=mutable.value.copy(error=failure.message)}
    }
    fun registerLibrary(paths:List<String>) {
        scope.launch {
            state.first {it.ready || it.error!=null}
            change {store.registerLibrary(paths,System.currentTimeMillis())}
        }
    }
    fun togglePin(type:String,id:String){scope.launch{change{store.togglePin(type,id)}}}
    fun movePin(type:String,id:String,delta:Int){scope.launch{change{store.movePin(type,id,delta)}}}
    private suspend fun persistPosition() {
        val queue=audio.queueState.value;val playback=audio.state.value
        val time=if(queue.tracks.getOrNull(queue.index)?.id==playback.song?.id)playback.time else 0
        change{store.saveSession(queue.tracks.map {it.id},queue.index,time)}
    }
    override fun close() {
        // Flush once on normal close; never rewrite the large queue file for a clock tick.
        if(started.get() && mutable.value.ready)runBlocking(Dispatchers.IO){persistPosition()}
        scope.cancel()
    }
}

data class ResolvedSession(val tracks:List<DesktopSong>,val index:Int,val time:Long)
fun resolveSession(session:HomeStore.Session,byPath:Map<String,DesktopSong>):ResolvedSession? {
    val current=session.paths().getOrNull(session.index()) ?: return null
    var selected=-1
    val tracks=buildList {
        session.paths().forEachIndexed { index,path ->
            byPath[path]?.let {if(index==session.index())selected=size;add(it)}
        }
    }
    if(tracks.isEmpty())return null
    return ResolvedSession(tracks,if(selected>=0)selected else 0,if(selected>=0 && current in byPath)session.time() else 0)
}
