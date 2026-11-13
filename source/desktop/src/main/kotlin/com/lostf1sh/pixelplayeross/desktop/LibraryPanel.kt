package com.lostf1sh.pixelplayeross.desktop

import androidx.compose.foundation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import com.lostf1sh.pixelplayeross.desktop.library.*
import com.lostf1sh.pixelplayeross.desktop.playback.AudioController
import com.lostf1sh.pixelplayeross.desktop.playback.PlatformMediaSession
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.isSecondaryPressed
import java.util.Locale
import java.awt.Component

enum class LibraryTab(val title: String, val icon: String) {
    Home("Home","home"), Tracks("Tracks","library"), Albums("Albums","album"), Artists("Artists","artist"),
    Playlists("Playlists","playlist"), Liked("Liked songs","liked"), Folders("Folders","folder")
}

private data class LibraryRoute(val tab:LibraryTab,val selected:String?,val query:String)

private data class PlaybackSummary(val ready: Boolean=false, val song: DesktopSong?=null, val playing: Boolean=false)

@Composable
fun LibraryPanel(applicationScope: ApplicationScope, parent: Component, fullPlayer: Boolean, onFullPlayer: (Boolean) -> Unit, playerCollapsed:Boolean, onPlayerCollapsed:(Boolean)->Unit, audioController: AudioController? = null, onAlbumAccent: (Int?) -> Unit) {
    val appColors=MaterialTheme.colorScheme
    val queuePalette=remember(appColors){queueColorScheme(appColors)}
    val fullscreenQueuePalette=remember(appColors){queueColorScheme(fullPlayerColorScheme(appColors.primary.toArgb()))}
    var songs by remember { mutableStateOf<List<DesktopSong>>(emptyList()) }
    var folderSets by remember { mutableStateOf<Map<String,List<DesktopSong>>>(emptyMap()) }
    var folderMenu by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    val folders=folderSets.keys.toList()
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(LibraryTab.Home) }
    var selected by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf(false) }
    var folderStoreReady by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val store = remember { UserLibraryStore.defaultStore() }
    var saved by remember { mutableStateOf(UserLibraryStore.Snapshot(emptySet(),emptyList())) }
    var naming by remember { mutableStateOf<String?>(null) } // empty = create, id = rename
    var name by remember { mutableStateOf("") }
    var queueToSave by remember {mutableStateOf<List<DesktopSong>?>(null)}
    var adding by remember { mutableStateOf<DesktopSong?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }
    val pageMotion = remember(tab,selected) { Animatable(0f) }
    LaunchedEffect(pageMotion) { pageMotion.animateTo(1f,tween(200)) }
    val scope = rememberCoroutineScope()
    val scanner = remember { FolderScanner() }
    val folderStore = remember { FolderRootsStore.defaultStore() }
    val audio = audioController ?: remember { AudioController() }
    if (audioController == null) {
        DisposableEffect(audio) { onDispose { audio.close() } }
    }
    val homeCoordinator=remember(audio){HomeCoordinator(audio)}
    val home by homeCoordinator.state.collectAsState()
    DisposableEffect(homeCoordinator){onDispose{homeCoordinator.close()}}
    LaunchedEffect(songs){if(songs.isNotEmpty())homeCoordinator.registerLibrary(songs.map{it.id})}
    val mediaSession = remember(audio,parent) {
        val window = parent as? java.awt.Window
        PlatformMediaSession.create(window) { button ->
            when(button) {
                0 -> audio.resume()
                1,2 -> audio.pause()
                6 -> audio.next()
                7 -> audio.previous()
            }
        }
    }
    DisposableEffect(mediaSession) {onDispose {mediaSession?.close()}}
    LaunchedEffect(mediaSession) {
        if(mediaSession!=null) while(isActive) {
            delay(2000)
            val failure=mediaSession.failureMessage
            if(failure!=null) error="Media controls: $failure"
            else if(error?.startsWith("Media controls:")==true || error?.startsWith("Windows media controls:")==true) error=null
        }
    }
    LaunchedEffect(mediaSession) {
        if(mediaSession!=null) audio.state.map { state ->
            listOf(state.song?.id,state.song?.title,state.song?.artist,state.song?.album,
                state.song?.coverPath,state.playing,state.previous,state.next)
        }.distinctUntilChanged().collect {
            val state=audio.state.value
            mediaSession.update(state.song?.title.orEmpty(),state.song?.artist.orEmpty(),
                state.song?.album.orEmpty(),state.song?.coverPath,state.playing,state.previous,state.next)
        }
    }
    val trackListState = rememberLazyListState()
    val collectionGridState = rememberLazyGridState()
    LaunchedEffect(tab,selected) {
        trackListState.requestScrollToItem(0)
        collectionGridState.requestScrollToItem(0)
    }
    var queueOpen by remember { mutableStateOf(false) }
    // Clock updates stay inside PlayerBar; grids and grouping do not recompose every 250ms.
    val summary = remember(audio) {
        audio.state.map { PlaybackSummary(it.ready,it.song,it.playing) }.distinctUntilChanged()
    }
    val playback by summary.collectAsState(PlaybackSummary())
    fun saveQueue(items:List<DesktopSong>) {queueOpen=false;queueToSave=items;name="";naming=""}
    LaunchedEffect(playback.song?.accentArgb) { onAlbumAccent(playback.song?.accentArgb) }
    DisposableEffect(audio) { onDispose { audio.close() } }
    LaunchedEffect(store) {
        try { saved=withContext(Dispatchers.IO) { store.load() }; loaded=true }
        catch (failure: Exception) { error="Could not load saved library: ${failure.message}" }
    }
    LaunchedEffect(folderStore) {
        busy=true;restoring=true
        try {
            val restored=withContext(Dispatchers.IO) {
                val roots=folderStore.load()
                val context=currentCoroutineContext()
                val found=linkedMapOf<String,List<DesktopSong>>()
                var missing=0
                for(root in roots) {
                    context.ensureActive()
                    try {found[root]=scanner.scan(java.nio.file.Path.of(root)){context.ensureActive()}.songs}
                    catch(cancelled:CancellationException){throw cancelled}
                    catch(failure:Exception){found[root]=emptyList();missing++}
                }
                found to missing
            }
            folderStoreReady=true
            folderSets=restored.first
            songs=folderSets.values.flatten().distinctBy {it.id.lowercase(Locale.ROOT)}.sortedBy {it.title.lowercase(Locale.ROOT)}
            if(restored.second>0)message="${restored.second} saved folders could not be read. Check or remove them in Folders."
        } catch(cancelled:CancellationException){throw cancelled}
        catch(failure:Exception){error="Could not restore folders: ${failure.message}"}
        finally{busy=false;restoring=false}
    }
    fun mutate(action: () -> UserLibraryStore.Snapshot, done: () -> Unit = {}) {
        if (!loaded || saving) return
        saving=true
        scope.launch {
            try {
                saved=withContext(Dispatchers.IO) { action() }
                error=null; done()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error="Could not save library: ${failure.message}" }
            finally { saving=false }
        }
    }
    val history=remember { mutableStateListOf<LibraryRoute>() }
    fun saveRoute() { history.add(LibraryRoute(tab,selected,query)) }
    fun goBack() {
        if(history.isNotEmpty()) {
            val route=history.removeAt(history.lastIndex);tab=route.tab;selected=route.selected;query=route.query;searchOpen=query.isNotEmpty()
        } else if(selected!=null) {selected=null;query=""}
    }
    fun navigate(next: LibraryTab) { if(next!=tab || selected!=null) {saveRoute();tab=next;selected=null;query="";searchOpen=false} }
    fun openGroup(id:String) {saveRoute();selected=id;query="";searchOpen=false}
    fun openDetail(next:LibraryTab,id:String) {saveRoute();tab=next;selected=id;query="";searchOpen=false}
    fun merged(sets:Map<String,List<DesktopSong>>) = sets.values.flatten()
        .distinctBy {it.id.lowercase(Locale.ROOT)}.sortedBy {it.title.lowercase(Locale.ROOT)}
    val albums=remember(songs) { LibraryIndex.albums(songs) }
    val artists=remember(songs) { LibraryIndex.artists(songs) }
    fun queueAlbum(song:DesktopSong) {
        val album=albums.firstOrNull{group->group.songs.any{it.id==song.id}}
        if(album!=null){queueOpen=false;onFullPlayer(false);openDetail(LibraryTab.Albums,album.id)}
        else message="This album is not available in the current library."
    }
    fun queueArtist(artist:String) {
        val group=artists.firstOrNull{it.id==ArtistNames.key(artist)}
        if(group!=null){queueOpen=false;onFullPlayer(false);openDetail(LibraryTab.Artists,group.id)}
        else message="This artist is not available in the current library."
    }
    val byPath=remember(songs) { songs.associateBy { it.id } }
    val resume=remember(home.data.session(),byPath){resolveSession(home.data.session(),byPath)}
    val playlistGroups=remember(saved,songs) {
        saved.playlists.map { list -> MusicGroup(list.id,list.name,
            "${list.paths.count { it in byPath }} of ${list.paths.size} tracks available",
            list.paths.mapNotNull { byPath[it] }) }
    }
    val folderGroups=remember(folderSets) {
        folderSets.map { (path, items) -> MusicGroup(path,
            java.nio.file.Path.of(path).fileName?.toString() ?: path,
            "${countLabel(items.size,"track")} · $path",items) }.sortedBy { it.title.lowercase(Locale.ROOT) }
    }
    val groups=when(tab) {
        LibraryTab.Albums -> albums
        LibraryTab.Artists -> artists
        LibraryTab.Playlists -> playlistGroups
        LibraryTab.Folders -> folderGroups
        else -> emptyList()
    }
    val detail=groups.firstOrNull { it.id==selected }
    val inCollection=tab==LibraryTab.Albums || tab==LibraryTab.Artists || tab==LibraryTab.Playlists || tab==LibraryTab.Folders
    val showGroups=inCollection && selected==null
    val baseTracks=when {
        tab==LibraryTab.Liked -> songs.filter { saved.isLiked(it.id) }
        inCollection -> detail?.songs.orEmpty()
        else -> songs
    }
    val text=query.trim()
    fun matches(song: DesktopSong) = text.isEmpty() || song.title.contains(text,true) ||
        song.artist.contains(text,true) || song.album.contains(text,true) || song.path.contains(text,true)
    val tracks=baseTracks.filter { matches(it) }
    val visibleGroups=groups.filter { text.isEmpty() || it.title.contains(text,true) ||
        it.subtitle.contains(text,true) || it.songs.any { track -> matches(track) } }
    val title=detail?.title ?: tab.title
    val countInHeader=detail==null && (tab==LibraryTab.Tracks || tab==LibraryTab.Albums || tab==LibraryTab.Artists)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide=maxWidth>=1000.dp
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                if(wide) Column(Modifier.width(190.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("YOUR MUSIC",Modifier.padding(12.dp),style=MaterialTheme.typography.labelSmall)
                    LibraryTab.entries.forEach { item ->
                        Surface(shape=RoundedCornerShape(18.dp),color=if(tab==item)
                            MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background) {
                            Row(Modifier.fillMaxWidth().clickable { navigate(item) }.padding(14.dp),
                                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                Glyph(item.icon,MaterialTheme.colorScheme.primary)
                                Text(item.title,style=MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight().padding(start=24.dp,end=28.dp,top=8.dp),
                    verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    if(!wide) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        LibraryTab.entries.forEach { item ->
                            FilterChip(selected=tab==item,onClick={navigate(item)},label={Text(item.title)})
                        }
                    }
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        if(history.isNotEmpty() || selected!=null) IconButton(onClick={goBack()},
                            modifier=Modifier.semantics { contentDescription="Back to ${tab.title}" }) {
                            Glyph("back",LocalContentColor.current)
                        }
                        if(tab==LibraryTab.Home && detail==null) Spacer(Modifier.weight(1f)) else Column(Modifier.weight(1f)) {
                            Text(title,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.headlineMedium)
                            Text(detail?.subtitle ?: when(tab) {
                                LibraryTab.Home -> "Your music, close at hand."
                                LibraryTab.Liked -> "Your favorites, kept for next time."
                                LibraryTab.Playlists -> "Your own collections."
                                LibraryTab.Folders -> "Your music folders."
                                else -> if(showGroups) countLabel(visibleGroups.size,tab.title.dropLast(1).lowercase()) else countLabel(tracks.size,"track")
                            },maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall)
                        }
                        FilledTonalIconButton(onClick={searchOpen=!searchOpen;if(!searchOpen)query=""},
                            shape=RoundedCornerShape(50),
                            modifier=Modifier.size(width=56.dp,height=40.dp).semantics {contentDescription=if(searchOpen)"Close search" else "Open search"}) {
                            Glyph("search",LocalContentColor.current,Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(enabled=!busy && folderStoreReady,modifier=Modifier.height(40.dp),
                            contentPadding=PaddingValues(start=14.dp,end=18.dp),onClick={
                            busy=true;error=null
                            job=scope.launch {
                                try {
                                    val path=chooseMusicFolder(parent) ?: return@launch
                                    scanning=true
                                    val result=withContext(Dispatchers.IO) {
                                        val context=currentCoroutineContext()
                                        scanner.scan(path) { context.ensureActive() }
                                    }
                                    val key=path.toAbsolutePath().normalize().toString()
                                    val old=folderSets.keys.firstOrNull {it.equals(key,ignoreCase=true)}
                                    val updated=folderSets.toMutableMap().apply {if(old!=null)remove(old);put(key,result.songs)}
                                    withContext(Dispatchers.IO){folderStore.save(updated.keys)}
                                    folderSets=updated;songs=merged(updated)
                                    message=if(result.unreadableEntries>0) "Skipped ${result.unreadableEntries} unreadable entries." else null
                                } catch(cancelled: CancellationException) {message="Scan cancelled; previous library kept.";throw cancelled}
                                catch(failure: Exception) {error="Could not read folder: ${failure.message}"}
                                finally {scanning=false;busy=false}
                            }
                        }) {Glyph("folder",LocalContentColor.current,Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text("Add folder",style=MaterialTheme.typography.labelLarge)}
                    }
                    if(restoring) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Restoring music folders…",style=MaterialTheme.typography.bodySmall)
                    }
                    if(scanning) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Text("Reading tracks and covers…",Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)
                            TextButton(onClick={job?.cancel()}) {Text("Cancel")}
                        }
                    }
                    error?.let { Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall) }
                    message?.let { Text(it,style=MaterialTheme.typography.bodySmall) }
                    AnimatedVisibility(searchOpen,enter=expandVertically(tween(150))+fadeIn(tween(150)),
                        exit=shrinkVertically(tween(120))+fadeOut(tween(120))) {
                        OutlinedTextField(value=query,onValueChange={query=it},modifier=Modifier.fillMaxWidth(),
                            singleLine=true,shape=RoundedCornerShape(24.dp),placeholder={Text(if(tab==LibraryTab.Home && detail==null) "Search your library" else "Search $title")},
                            leadingIcon={Glyph("search",MaterialTheme.colorScheme.onSurfaceVariant)},
                            trailingIcon={IconButton(onClick={query="";searchOpen=false}) {Glyph("back",LocalContentColor.current)}})
                    }
                    if(tab!=LibraryTab.Home && !(showGroups && countInHeader)) Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        if(countInHeader) Spacer(Modifier.weight(1f)) else Text(
                            if(showGroups) countLabel(visibleGroups.size,"collection") else countLabel(tracks.size,"track"),
                            Modifier.weight(1f),style=MaterialTheme.typography.labelLarge)
                        if(!showGroups) FilledTonalButton(enabled=playback.ready && tracks.isNotEmpty(),
                            modifier=Modifier.height(40.dp),contentPadding=PaddingValues(start=14.dp,end=18.dp),
                            onClick={audio.play(tracks.first(),tracks)}) {
                            Glyph("play",LocalContentColor.current,Modifier.size(20.dp));Spacer(Modifier.width(8.dp))
                            Text("Play all",style=MaterialTheme.typography.labelLarge)
                        }
                        if(tab==LibraryTab.Playlists && selected==null) TextButton(enabled=loaded && !saving,
                            onClick={queueToSave=null;name="";naming=""}) {Glyph("add",LocalContentColor.current);Text("New playlist")}
                        if(detail!=null && (tab==LibraryTab.Albums || tab==LibraryTab.Playlists)) {
                            val type=if(tab==LibraryTab.Albums)"album" else "playlist"
                            val pinned=home.data.pins().any{it.type()==type && it.id()==detail.id}
                            TextButton(enabled=home.ready,onClick={homeCoordinator.togglePin(type,detail.id)}) {
                                Glyph(if(pinned)"unpin" else "pin",LocalContentColor.current)
                                Text(if(pinned)"Unpin" else "Pin to Home")
                            }
                        }
                        if(tab==LibraryTab.Playlists && detail!=null) {
                            TextButton(enabled=loaded && !saving,onClick={queueToSave=null;name=detail.title;naming=detail.id}) {Text("Rename")}
                            TextButton(enabled=loaded && !saving,onClick={deleting=detail.id}) {Text("Delete")}
                        }
                        if(tab==LibraryTab.Folders && detail!=null) TextButton(enabled=!busy,onClick={folderMenu=true}) {
                            Text("Remove folder")
                        }
                    }
                    Box {
                            DropdownMenu(expanded=folderMenu,onDismissRequest={folderMenu=false}) {
                                folders.filter { detail==null || tab!=LibraryTab.Folders || it==detail.id }.forEach { folder ->
                                    DropdownMenuItem(text={Text("Remove: $folder",maxLines=1,overflow=TextOverflow.Ellipsis)},onClick={
                                        folderMenu=false;busy=true
                                        scope.launch {
                                            try {
                                                val updated=folderSets-folder
                                                withContext(Dispatchers.IO){folderStore.save(updated.keys)}
                                                folderSets=updated;songs=merged(updated)
                                                if(selected==folder) {selected=null;query=""}
                                            } catch(cancelled:CancellationException){throw cancelled}
                                            catch(failure:Exception){error="Could not save folders: ${failure.message}"}
                                            finally{busy=false}
                                        }
                                    })
                                }
                            }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth().graphicsLayer {
                        translationX=(1f-pageMotion.value)*28.dp.toPx()
                        alpha=.60f+.40f*pageMotion.value
                    }) {
                    if(tab==LibraryTab.Home) {
                        HomeScreen(albums,playlistGroups,home,playback.song,playback.playing,playback.ready,resume,query,
                            onContinue={
                                if(audio.state.value.song!=null)audio.toggle()
                                else resume?.let{audio.restore(it.tracks,it.index,it.time)}
                            },
                            onOpen={type,id->openDetail(if(type=="album")LibraryTab.Albums else LibraryTab.Playlists,id)},
                            onPlay={group->group.songs.firstOrNull()?.let{audio.play(it,group.songs)}},
                            onPlayNext={group->audio.playNextAll(group.songs)},
                            onAppend={group->audio.appendQueue(group.songs)},
                            onPin=homeCoordinator::togglePin,onMovePin=homeCoordinator::movePin,
                            loading=restoring || (!home.ready && home.error==null))
                    } else if(showGroups) {
                        if(visibleGroups.isEmpty()) EmptyLibrary("No ${tab.title.lowercase()} yet",
                            when(tab) {
                                LibraryTab.Playlists -> "Create a playlist, then use a track's menu to add songs."
                                LibraryTab.Folders -> "Add a music folder to see it here."
                                else -> "Choose a music folder or try another search."
                            },Modifier.fillMaxSize())
                        else Box(Modifier.fillMaxSize()) {
                        LazyVerticalGrid(columns=GridCells.Adaptive(170.dp),state=collectionGridState,
                            modifier=Modifier.fillMaxSize().padding(end=10.dp),
                            contentPadding=PaddingValues(bottom=16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),
                            verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            items(visibleGroups,key={it.id}) { group ->
                                Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface) {
                                    Column(Modifier.fillMaxWidth().clickable {openGroup(group.id)}.padding(16.dp),
                                        verticalArrangement=Arrangement.spacedBy(8.dp),
                                        horizontalAlignment=Alignment.CenterHorizontally) {
                                        Artwork(112.dp,coverPath=group.songs.firstOrNull {it.coverPath!=null}?.coverPath)
                                        Text(group.title,modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.Center,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.titleSmall)
                                        Text(group.subtitle,modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.Center,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                        AutoHideGridScrollbar(collectionGridState,Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                        }
                    } else if(tracks.isEmpty()) EmptyLibrary("No tracks here yet",
                        if(tab==LibraryTab.Liked) "Tap a heart next to a track to save it here." else "Try another search or add a music folder.",Modifier.fillMaxSize())
                    else {
                        val listState=trackListState
                        Box(Modifier.fillMaxSize()) {
                            LazyColumn(state=listState,modifier=Modifier.fillMaxSize().padding(end=14.dp),
                                contentPadding=PaddingValues(bottom=16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                items(tracks,key={it.id}) { song ->
                                    TrackRow(song,playback.song?.id==song.id,playback.playing,playback.ready,
                                        saved.isLiked(song.id),loaded && !saving,
                                        onPlay={audio.play(song,tracks)},onLike={mutate({store.toggleLike(song.id)})},
                                        onAdd={adding=song},
                                        onPlayNext={audio.playNext(song)},onAppend={audio.appendQueue(listOf(song))},
                                        onAlbum={albums.firstOrNull { song in it.songs }?.let { openDetail(LibraryTab.Albums,it.id) }},
                                        onArtist={artist->artists.firstOrNull {it.id==ArtistNames.key(artist)}?.let { openDetail(LibraryTab.Artists,it.id) }},
                                        onRemove=if(tab==LibraryTab.Playlists && selected!=null) ({
                                            val id=selected;if(id!=null)mutate({store.remove(id,song.id)})
                                        }) else null)
                                }
                            }
                            AutoHideListScrollbar(listState,Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                        }
                    }
                    }
                }
            }
            AnimatedVisibility(visible=!playerCollapsed,enter=expandVertically(tween(150))+fadeIn(tween(150)),
                exit=shrinkVertically(tween(130))+fadeOut(tween(130))) {
            PlayerBar(audio,
                liked=saved.isLiked(playback.song?.id),
                editable=loaded && !saving,
                onLike={playback.song?.let { song -> mutate({store.toggleLike(song.id)}) }},
                onAdd={adding=playback.song}, onQueue={queueOpen=true},onExpand={onFullPlayer(true)},
                onCollapse={onPlayerCollapsed(true)})
            }
        }
    }
    if(playerCollapsed && !fullPlayer)FloatingPlayer(applicationScope,audio){onPlayerCollapsed(false)}
    val playlistDialogs: @Composable () -> Unit = {
    if(naming!=null) MotionAlertDialog(onDismissRequest={if(!saving){naming=null;queueToSave=null}},
        title={Text(if(queueToSave!=null)"Save queue as playlist" else if(naming=="") "New playlist" else "Rename playlist")},
        text={Column {
            OutlinedTextField(name,{name=it},singleLine=true,label={Text("Playlist name")})
            error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        }},
        confirmButton={TextButton(enabled=!saving && name.isNotBlank(),onClick={
            val id=naming ?: return@TextButton
            val paths=queueToSave?.map{it.id}
            mutate({if(id.isEmpty()) {
                if(paths!=null)store.createFromTracks(name,paths) else store.create(name)
            } else store.rename(id,name)},{naming=null;queueToSave=null})
        }) {Text("Save")}},dismissButton={TextButton(enabled=!saving,onClick={naming=null;queueToSave=null}){Text("Cancel")}})
    if(adding!=null && naming==null) MotionAlertDialog(onDismissRequest={if(!saving)adding=null},
        title={Text("Add to playlist")},text={Column {
            if(saved.playlists.isEmpty()) Text("Create a playlist first.")
            LazyColumn(Modifier.heightIn(max=280.dp)) {
                items(saved.playlists,key={it.id}) { list ->
                    TextButton(enabled=!saving && adding!!.id !in list.paths,onClick={
                        val track=adding ?: return@TextButton
                        mutate({store.add(list.id,track.id)},{adding=null})
                    }) {Text(list.name + if(adding!!.id in list.paths) " · Added" else "")}
                }
            }
            error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        }},confirmButton={TextButton(enabled=!saving,onClick={queueToSave=null;name="";naming=""}){Text("New playlist")}},
        dismissButton={TextButton(enabled=!saving,onClick={adding=null}){Text("Close")}})
    if(deleting!=null) MotionAlertDialog(onDismissRequest={if(!saving)deleting=null},title={Text("Delete playlist?")},
        text={Text("The playlist is removed. Your music files stay on disk.")},
        confirmButton={TextButton(enabled=!saving,onClick={
            val id=deleting ?: return@TextButton
            mutate({store.delete(id)},{deleting=null;selected=null})
        }) {Text("Delete")}},dismissButton={TextButton(enabled=!saving,onClick={deleting=null}){Text("Cancel")}})
    }
    // The full player is its own window. Keep it alive while it fades out, and make it transparent so the
    // content fades over the main window instead of popping in and out.
    var playerWindowShown by remember {mutableStateOf(false)}
    var playerVisible by remember {mutableStateOf(false)}
    LaunchedEffect(fullPlayer) {
        if(fullPlayer) {playerWindowShown=true;playerVisible=true}
        else if(playerWindowShown) {playerVisible=false;delay(260);playerWindowShown=false}
    }
    if(playerWindowShown) {
        // Borderless maximized avoids exclusive full-screen capture restrictions on Windows.
        val playerWindowState=rememberWindowState(placement=WindowPlacement.Maximized)
        with(applicationScope) { Window(onCloseRequest={queueOpen=false;adding=null;naming=null;queueToSave=null;onFullPlayer(false)},state=playerWindowState,
            title="Umimuko Music · Now Playing",icon=painterResource("branding/app-icon.png"),undecorated=true,transparent=true,
            onPreviewKeyEvent={event ->
                if(event.type==KeyEventType.KeyDown && event.key==Key.Escape) {
                    queueOpen=false;adding=null;naming=null;queueToSave=null;onFullPlayer(false);true
                } else false
            }) {
            // Compose the heavy content while it is still invisible and start the fade only after a few frames;
            // otherwise a slow first frame swallows the animation and the player just pops in.
            val fade=remember {Animatable(0f)}
            LaunchedEffect(playerVisible) {
                if(playerVisible) {
                    withFrameNanos {};withFrameNanos {};delay(80)
                    fade.animateTo(1f,tween(280))
                } else fade.animateTo(0f,tween(200))
            }
            MaterialTheme(colorScheme=fullPlayerColorScheme(playback.song?.accentArgb),typography=AppTypography) {
                Box(Modifier.fillMaxSize().graphicsLayer {alpha=fade.value}) {
                    FullPlayerScreen(audio,liked=saved.isLiked(playback.song?.id),editable=loaded && !saving,
                        onLike={playback.song?.let {song->mutate({store.toggleLike(song.id)})}},
                        onAdd={adding=playback.song},onQueue={queueOpen=true},
                        onExit={queueOpen=false;adding=null;naming=null;queueToSave=null;onFullPlayer(false)})
                }
                if(queueOpen) QueueDialog(audio,onDismiss={queueOpen=false},palette=fullscreenQueuePalette,
                    onSaveQueue=if(loaded && !saving)::saveQueue else null,onOpenAlbum=::queueAlbum,onOpenArtist=::queueArtist)
                playlistDialogs()
            }
        } }
    }
    if(!fullPlayer) {
        if(queueOpen) QueueDialog(audio,onDismiss={queueOpen=false},palette=queuePalette,
            onSaveQueue=if(loaded && !saving)::saveQueue else null,onOpenAlbum=::queueAlbum,onOpenArtist=::queueArtist)
        playlistDialogs()
    }

}

@Composable
private fun MotionAlertDialog(onDismissRequest:()->Unit,title:@Composable ()->Unit,
    text:@Composable ()->Unit,confirmButton:@Composable ()->Unit,dismissButton:@Composable ()->Unit) {
    Dialog(onDismissRequest=onDismissRequest) {
        DialogMotion {
            Surface(shape=RoundedCornerShape(28.dp),tonalElevation=6.dp,color=MaterialTheme.colorScheme.surface) {
                Column(Modifier.widthIn(min=320.dp,max=520.dp).padding(24.dp),
                    verticalArrangement=Arrangement.spacedBy(18.dp)) {
                    title()
                    text()
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End,
                        verticalAlignment=Alignment.CenterVertically) {
                        dismissButton()
                        confirmButton()
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLibrary(title:String,subtitle:String,modifier:Modifier) {
    Box(modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
        Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Artwork(64.dp);Text(title,style=MaterialTheme.typography.titleLarge)
            Text(subtitle,style=MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun ArtistAlbumLine(artist:String,album:String,fallback:String) {
    val color=MaterialTheme.colorScheme.onSurfaceVariant
    val text=buildAnnotatedString {
        if(artist.isNotBlank()) append(artist)
        if(album.isNotBlank()) {
            if(length>0) append("     ·     ")
            pushStyle(SpanStyle(color=color.copy(alpha=.62f)))
            append(album)
            pop()
        }
        if(length==0) append(fallback)
    }
    Text(text,maxLines=1,overflow=TextOverflow.Ellipsis,
        style=MaterialTheme.typography.labelSmall.copy(fontWeight=FontWeight.Normal),color=color)
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun TrackRow(song:DesktopSong,active:Boolean,playing:Boolean,ready:Boolean,liked:Boolean,editable:Boolean,
    onPlay:()->Unit,onLike:()->Unit,onAdd:()->Unit,onPlayNext:()->Unit,onAppend:()->Unit,onAlbum:()->Unit,onArtist:(String)->Unit,onRemove:(()->Unit)?) {
    var menu by remember {mutableStateOf(false)}
    Surface(shape=RoundedCornerShape(16.dp),color=if(active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().onPointerEvent(PointerEventType.Press) { event ->
            if(event.buttons.isSecondaryPressed) {menu=true;event.changes.forEach {it.consume()} }
        }.clickable(enabled=ready,onClick=onPlay).padding(10.dp),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Artwork(44.dp,active,song.coverPath)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Text(song.title,maxLines=1,overflow=TextOverflow.Ellipsis,
                    style=MaterialTheme.typography.bodyLarge.copy(fontWeight=FontWeight.SemiBold),
                    color=if(active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                ArtistAlbumLine(ArtistNames.split(song.artist).joinToString(" · "),song.album,
                    "${song.format} · Local audio")
                if(song.durationMs>0)Text(trackClock(song.durationMs),maxLines=1,style=MaterialTheme.typography.labelSmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.8f))
            }
            if(active)Glyph(if(playing)"levels" else "pause",MaterialTheme.colorScheme.primary)
            Row(verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onLike,enabled=editable,modifier=Modifier.semantics {
                contentDescription=if(liked) "Unlike ${song.title}" else "Like ${song.title}"
            }) {Glyph(if(liked)"heart" else "liked",if(liked)MaterialTheme.colorScheme.primary else LocalContentColor.current)}
            Box {
                IconButton(onClick={menu=true},enabled=editable,modifier=Modifier.semantics {contentDescription="Track options"}) {
                    Glyph("more",LocalContentColor.current)
                }
                DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                    DropdownMenuItem(text={Text("Play next")},leadingIcon={Glyph("next",LocalContentColor.current)},onClick={menu=false;onPlayNext()})
                    DropdownMenuItem(text={Text("Add to queue")},leadingIcon={Glyph("queue",LocalContentColor.current)},onClick={menu=false;onAppend()})
                    DropdownMenuItem(text={Text("Add to playlist")},leadingIcon={Glyph("playlist_add",LocalContentColor.current)},onClick={menu=false;onAdd()})
                    DropdownMenuItem(text={Text("Go to album: ${song.album.ifBlank { "Unknown album" }}")},
                        leadingIcon={Glyph("album",LocalContentColor.current)},onClick={menu=false;onAlbum()})
                    ArtistNames.split(song.artist).ifEmpty {listOf("Unknown artist")}.forEach { artist ->
                        DropdownMenuItem(text={Text("Go to artist: $artist")},
                            leadingIcon={Glyph("artist",LocalContentColor.current)},onClick={menu=false;onArtist(artist)})
                    }
                    if(onRemove!=null) DropdownMenuItem(text={Text("Remove from playlist")},onClick={menu=false;onRemove()})
                }
            }
            }
        }
    }
}
