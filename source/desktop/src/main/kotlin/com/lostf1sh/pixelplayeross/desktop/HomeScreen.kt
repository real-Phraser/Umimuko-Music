package com.lostf1sh.pixelplayeross.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lostf1sh.pixelplayeross.desktop.library.*

data class HomeCollection(val type:String,val group:MusicGroup)
data class HomeSections(val pinned:List<HomeCollection>,val recent:List<HomeCollection>,val added:List<HomeCollection>)

fun homeSections(albums:List<MusicGroup>,playlists:List<MusicGroup>,data:HomeStore.Snapshot,query:String):HomeSections {
    val albumByTrack=buildMap<String,MusicGroup>{albums.forEach{album->album.songs.forEach{put(it.id,album)}}}
    val albumById=albums.associateBy{it.id};val playlistById=playlists.associateBy{it.id}
    fun matches(group:MusicGroup)=query.isBlank() || group.title.contains(query,true) || group.subtitle.contains(query,true) ||
        group.songs.any{it.title.contains(query,true)||it.artist.contains(query,true)}
    val pinned=data.pins().mapNotNull {pin->
        (if(pin.type()=="album")albumById[pin.id()] else playlistById[pin.id()])?.let{HomeCollection(pin.type(),it)}
    }.filter{matches(it.group)}
    val recent=data.recent().mapNotNull{albumByTrack[it]}.distinctBy{it.id}.filter(::matches).take(40).map{HomeCollection("album",it)}
    val added=albums.filter(::matches).sortedWith(compareByDescending<MusicGroup>{group->
        group.songs.maxOfOrNull{data.added()[it.id]?:0L}?:0L
    }.thenBy{it.title.lowercase()}).take(40).map{HomeCollection("album",it)}
    return HomeSections(pinned,recent,added)
}

@Composable
fun HomeScreen(albums:List<MusicGroup>,playlists:List<MusicGroup>,home:HomeState,current:DesktopSong?,playing:Boolean,
    ready:Boolean,resume:ResolvedSession?,query:String,onContinue:()->Unit,
    onOpen:(String,String)->Unit,onPlay:(MusicGroup)->Unit,onPlayNext:(MusicGroup)->Unit,onAppend:(MusicGroup)->Unit,
    onPin:(String,String)->Unit,onMovePin:(String,String,Int)->Unit,loading:Boolean=false) {
    val sections=remember(albums,playlists,home.data.added(),home.data.recent(),home.data.pins(),query) {
        homeSections(albums,playlists,home.data,query.trim())
    }
    val continuation=current ?: resume?.tracks?.getOrNull(resume.index)
    val colors=MaterialTheme.colorScheme
    val listState=rememberLazyListState()
    // Entrance animations belong to the opening of Home only. Anything composed after that (cards that
    // scroll into view, shelves further down the page) must simply appear.
    var entranceOver by remember {mutableStateOf(false)}
    val hasContent=albums.isNotEmpty()
    LaunchedEffect(hasContent) {
        if(hasContent) {
            snapshotFlow {IntroState.active.value || IntroState.heroPending.value}.first {!it}
            delay(1600)
            entranceOver=true
        }
    }
    val latestContinuation by rememberUpdatedState(continuation)
    val latestLoading by rememberUpdatedState(loading)
    var heroSong by remember {mutableStateOf<DesktopSong?>(null)}
    val heroShown=remember {Animatable(0f)}
    val heroGo=remember {Animatable(0f)}
    var heroSkip by remember {mutableStateOf(false)}
    // Once per launch, after the startup cover: show what is playing (or was playing) large for a couple of
    // seconds, then fold it into the compact card and let the shelves come in. Tap to skip.
    LaunchedEffect(Unit) {
        if(!IntroState.heroPending.value) return@LaunchedEffect
        try {
            snapshotFlow {IntroState.active.value}.first {!it}
            withTimeoutOrNull(4000L) {snapshotFlow {latestContinuation!=null || !latestLoading}.first {it}}
            val song=latestContinuation
            if(song!=null) {
                heroSong=song
                heroShown.animateTo(1f,tween(450,easing=EmphasizedDecelerate))
                withTimeoutOrNull(2400L) {snapshotFlow {heroSkip}.first {it}}
                coroutineScope {
                    launch {delay(200);IntroState.heroPending.value=false}
                    heroGo.animateTo(1f,tween(480,easing=FastOutSlowInEasing))
                }
                heroSong=null
            }
        } finally {IntroState.heroPending.value=false}
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(state=listState,modifier=Modifier.fillMaxSize().padding(end=12.dp)
            .graphicsLayer {alpha=if(IntroState.heroPending.value) 0f else 1f},
            contentPadding=PaddingValues(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
            if(query.isBlank() && continuation!=null)item("continue") {
                val gradient=Brush.horizontalGradient(listOf(lerp(colors.surface,colors.primary,.11f),colors.surface))
                Surface(shape=RoundedCornerShape(24.dp),color=colors.surface,modifier=Modifier.entrance("continue",0L)) {
                    Row(Modifier.fillMaxWidth().background(gradient).padding(20.dp),verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(18.dp)) {
                        Artwork(80.dp,true,continuation.coverPath)
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                            Text(if(current!=null && playing)"PLAYING NOW" else "CONTINUE LISTENING",
                                style=MaterialTheme.typography.labelSmall,color=colors.primary)
                            Text(continuation.title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,
                                maxLines=1,overflow=TextOverflow.Ellipsis)
                            Text(ArtistNames.split(continuation.artist).joinToString(" · "),style=MaterialTheme.typography.bodyMedium,
                                maxLines=1,overflow=TextOverflow.Ellipsis,color=colors.onSurfaceVariant)
                            if(continuation.album.isNotBlank())Text(continuation.album,style=MaterialTheme.typography.bodySmall,
                                maxLines=1,overflow=TextOverflow.Ellipsis,color=colors.onSurfaceVariant.copy(alpha=.7f))
                        }
                        FilledIconButton(onClick=onContinue,enabled=ready,modifier=Modifier.size(52.dp)) {
                            Glyph(if(current!=null && playing)"pause" else "play",LocalContentColor.current,Modifier.size(28.dp))
                        }
                    }
                }
            }
            if(home.error!=null)item("error") {Text(home.error,color=colors.error,style=MaterialTheme.typography.bodySmall)}
            if(loading && albums.isEmpty())item("skeleton") {HomeSkeleton()}
            if(albums.isEmpty() && !loading)item("empty") {
                Surface(shape=RoundedCornerShape(24.dp),color=colors.surface) {
                    Column(Modifier.fillMaxWidth().padding(32.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Glyph("music",colors.primary,Modifier.size(36.dp))
                        Text("Make yourself at home",style=MaterialTheme.typography.headlineSmall)
                        Text("Add a music folder above. Your albums, recent listening and pinned collections will appear here.",
                            style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant)
                    }
                }
            }
            if(sections.pinned.isNotEmpty())item("pinned") {
                HomeShelf("Pinned","Your favorites, within reach",sections.pinned,home,ready,onOpen,onPlay,onPlayNext,onAppend,onPin,onMovePin,1,entranceOver)
            }
            if(sections.recent.isNotEmpty())item("recent") {
                HomeShelf("Recently played","Pick up an album again",sections.recent,home,ready,onOpen,onPlay,onPlayNext,onAppend,onPin,onMovePin,2,entranceOver)
            }
            if(sections.added.isNotEmpty())item("added") {
                HomeShelf("Recently added","The latest albums in your library",sections.added,home,ready,onOpen,onPlay,onPlayNext,onAppend,onPin,onMovePin,3,entranceOver)
            }
            if(albums.isNotEmpty() && sections.pinned.isEmpty() && query.isBlank())item("pin-hint") {
                Row(Modifier.fillMaxWidth().padding(vertical=4.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),
                    verticalAlignment=Alignment.CenterVertically) {
                    Glyph("pin",colors.primary)
                    Text("Open an album or playlist and choose Pin to Home.",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                }
            }
            if(albums.isNotEmpty() && sections.added.isEmpty() && sections.pinned.isEmpty() && sections.recent.isEmpty())item("no-results") {
                Text("No collections match your search.",style=MaterialTheme.typography.bodyMedium)
            }
        }
        AutoHideListScrollbar(listState,Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        heroSong?.let {song->
            HomeHero(song,current!=null && playing,{heroShown.value},{heroGo.value}) {heroSkip=true}
        }
    }
}

/** Large "now playing" moment: fades in, holds, then scales up toward the compact card's spot and fades out. */
@Composable
private fun HomeHero(song:DesktopSong,playing:Boolean,shown:()->Float,go:()->Float,onSkip:()->Unit) {
    val colors=MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxSize().clickable(interactionSource=remember {MutableInteractionSource()},
        indication=null,onClick=onSkip),contentAlignment=Alignment.Center) {
        val shift=with(LocalDensity.current) {(70.dp-maxHeight/2).toPx()}
        val artSize=minOf(maxHeight*.5f,260.dp).coerceAtLeast(120.dp)
        Column(Modifier.widthIn(max=520.dp).padding(horizontal=24.dp).graphicsLayer {
            val appear=shown();val fold=go()
            val s=(.94f+.06f*appear)*(1f-.6f*fold)
            scaleX=s;scaleY=s;alpha=appear*(1f-fold);translationY=shift*fold
        },horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(if(playing)"PLAYING NOW" else "CONTINUE LISTENING",style=MaterialTheme.typography.labelMedium,color=colors.primary)
            Artwork(artSize,true,song.coverPath)
            Spacer(Modifier.height(6.dp))
            Text(song.title,Modifier.fillMaxWidth(),style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold,
                textAlign=TextAlign.Center,maxLines=1,overflow=TextOverflow.Ellipsis)
            Text(ArtistNames.split(song.artist).joinToString(" · ").ifBlank {"Unknown artist"},Modifier.fillMaxWidth(),
                style=MaterialTheme.typography.titleMedium,color=colors.onSurfaceVariant,textAlign=TextAlign.Center,
                maxLines=1,overflow=TextOverflow.Ellipsis)
            if(song.album.isNotBlank()) Text(song.album,Modifier.fillMaxWidth(),style=MaterialTheme.typography.bodyMedium,
                color=colors.onSurfaceVariant.copy(alpha=.7f),textAlign=TextAlign.Center,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun HomeShelf(title:String,subtitle:String,collections:List<HomeCollection>,home:HomeState,ready:Boolean,
    onOpen:(String,String)->Unit,onPlay:(MusicGroup)->Unit,onPlayNext:(MusicGroup)->Unit,onAppend:(MusicGroup)->Unit,
    onPin:(String,String)->Unit,onMovePin:(String,String,Int)->Unit,shelfIndex:Int,entranceOver:Boolean) {
    val rowState=rememberLazyListState()
    val scope=rememberCoroutineScope()
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().entrance("shelf:$title",shelfIndex*90L,animate=!entranceOver),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
                Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(rowState.canScrollBackward || rowState.canScrollForward) {
                ShelfArrow(false,"Scroll $title left",rowState.canScrollBackward) {
                    scope.launch {rowState.animateScrollBy(-rowState.layoutInfo.viewportSize.width*.85f)}
                }
                ShelfArrow(true,"Scroll $title right",rowState.canScrollForward) {
                    scope.launch {rowState.animateScrollBy(rowState.layoutInfo.viewportSize.width*.85f)}
                }
            }
        }
        LazyRow(state=rowState,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            itemsIndexed(collections,key={_,c->c.type+":"+c.group.id}) { index,item ->
                HomeCollectionCard(item,home,ready,onOpen,onPlay,onPlayNext,onAppend,onPin,onMovePin,
                    Modifier.entrance("card:$title:${item.type}:${item.group.id}",shelfIndex*90L+120L+index*45L,animate=index<10 && !entranceOver))
            }
        }
    }
}

@Composable
private fun ShelfArrow(forward:Boolean,label:String,enabled:Boolean,onClick:()->Unit) {
    FilledTonalIconButton(onClick=onClick,enabled=enabled,modifier=Modifier.size(36.dp).semantics {contentDescription=label}) {
        Glyph("back",LocalContentColor.current,Modifier.size(20.dp).rotate(if(forward)180f else 0f))
    }
}

/** Placeholder shelves shown while folders are still being restored; one shared pulse keeps it cheap. */
@Composable
private fun HomeSkeleton() {
    val pulse=rememberInfiniteTransition(label="skeleton").animateFloat(.35f,.8f,
        infiniteRepeatable(tween(900,easing=FastOutSlowInEasing),androidx.compose.animation.core.RepeatMode.Reverse),label="pulse")
    val block=MaterialTheme.colorScheme.surface
    Column(Modifier.graphicsLayer {alpha=pulse.value},verticalArrangement=Arrangement.spacedBy(28.dp)) {
        repeat(2) {
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Box(Modifier.width(160.dp).height(22.dp).background(block,RoundedCornerShape(8.dp)))
                Box(Modifier.width(240.dp).height(14.dp).background(block,RoundedCornerShape(8.dp)))
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    repeat(5) {Box(Modifier.width(176.dp).height(210.dp).background(block,RoundedCornerShape(20.dp)))}
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun HomeCollectionCard(item:HomeCollection,home:HomeState,ready:Boolean,
    onOpen:(String,String)->Unit,onPlay:(MusicGroup)->Unit,onPlayNext:(MusicGroup)->Unit,onAppend:(MusicGroup)->Unit,
    onPin:(String,String)->Unit,onMovePin:(String,String,Int)->Unit,modifier:Modifier=Modifier) {
    var menu by remember(item.type,item.group.id){mutableStateOf(false)}
    val group=item.group;val colors=MaterialTheme.colorScheme
    val pinIndex=home.data.pins().indexOfFirst{it.type()==item.type && it.id()==group.id}
    Surface(shape=RoundedCornerShape(20.dp),color=colors.surface,modifier=modifier.width(176.dp)) {
        Column(Modifier.fillMaxWidth().onPointerEvent(PointerEventType.Press){event->
            if(event.buttons.isSecondaryPressed){menu=true;event.changes.forEach{it.consume()}}
        }.clickable{onOpen(item.type,group.id)}.padding(12.dp),horizontalAlignment=Alignment.CenterHorizontally,
            verticalArrangement=Arrangement.spacedBy(7.dp)) {
            Box(Modifier.fillMaxWidth()) {
                Box(Modifier.align(Alignment.Center)){Artwork(124.dp,coverPath=group.songs.firstOrNull{it.coverPath!=null}?.coverPath)}
                IconButton(onClick={menu=true},modifier=Modifier.align(Alignment.TopEnd).size(28.dp)) {
                    Glyph("more",colors.onSurface,Modifier.size(20.dp))
                }
                FilledIconButton(onClick={onPlay(group)},enabled=ready && group.songs.isNotEmpty(),
                    modifier=Modifier.align(Alignment.BottomEnd).size(32.dp)) {Glyph("play",LocalContentColor.current,Modifier.size(20.dp))}
                DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                    DropdownMenuItem(text={Text("Open ${item.type}")},onClick={menu=false;onOpen(item.type,group.id)})
                    DropdownMenuItem(text={Text("Play next")},enabled=ready && group.songs.isNotEmpty(),onClick={menu=false;onPlayNext(group)})
                    DropdownMenuItem(text={Text("Add to queue")},enabled=ready && group.songs.isNotEmpty(),onClick={menu=false;onAppend(group)})
                    DropdownMenuItem(text={Text(if(pinIndex>=0)"Unpin from Home" else "Pin to Home")},enabled=home.ready,
                        leadingIcon={Glyph(if(pinIndex>=0)"unpin" else "pin",LocalContentColor.current)},onClick={menu=false;onPin(item.type,group.id)})
                    if(pinIndex>=0) {
                        DropdownMenuItem(text={Text("Move pin left")},enabled=pinIndex>0,onClick={menu=false;onMovePin(item.type,group.id,-1)})
                        DropdownMenuItem(text={Text("Move pin right")},enabled=pinIndex<home.data.pins().lastIndex,onClick={menu=false;onMovePin(item.type,group.id,1)})
                    }
                }
            }
            Text(group.title,modifier=Modifier.fillMaxWidth(),style=MaterialTheme.typography.titleSmall,
                fontWeight=FontWeight.SemiBold,textAlign=TextAlign.Center,maxLines=1,overflow=TextOverflow.Ellipsis)
            Text(group.subtitle,modifier=Modifier.fillMaxWidth(),style=MaterialTheme.typography.bodySmall,
                color=colors.onSurfaceVariant,textAlign=TextAlign.Center,maxLines=1,overflow=TextOverflow.Ellipsis)
            if(pinIndex>=0)Text("PINNED",style=MaterialTheme.typography.labelSmall,color=colors.primary)
        }
    }
}
