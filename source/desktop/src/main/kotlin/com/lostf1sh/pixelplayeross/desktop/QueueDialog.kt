package com.lostf1sh.pixelplayeross.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import com.lostf1sh.pixelplayeross.desktop.library.ArtistNames
import com.lostf1sh.pixelplayeross.desktop.library.DesktopSong
import com.lostf1sh.pixelplayeross.desktop.library.countLabel
import com.lostf1sh.pixelplayeross.desktop.playback.AudioController
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun QueueDialog(audio:AudioController,onDismiss:()->Unit,palette:ColorScheme,
    onSaveQueue:((List<DesktopSong>)->Unit)?=null,
    onOpenAlbum:(DesktopSong)->Unit,onOpenArtist:(String)->Unit) {
    val typography=MaterialTheme.typography
    Dialog(onDismissRequest=onDismiss) {
        // Apply the explicit application palette inside the separate dialog composition.
        MaterialTheme(colorScheme=palette,typography=typography) {
            CompositionLocalProvider(LocalContentColor provides palette.onSurface) {
                DialogMotion {QueuePanel(audio,onDismiss,onSaveQueue,onOpenAlbum,onOpenArtist)}
            }
        }
    }
}

/** One line of the queue list: either a small section label or a track at an absolute queue position. */
private sealed interface QueueRow {
    data class Label(val id:String,val text:String):QueueRow
    data class Track(val absolute:Int):QueueRow
}

/**
 * Simple queue: Played / Now playing / Next up in one scrolling list. Tap a row to play it,
 * drag the handle to reorder, use the row menu to remove it. Everything else lives in the header menu.
 */
@Composable
fun QueuePanel(audio:AudioController,onDismiss:()->Unit,onSaveQueue:((List<DesktopSong>)->Unit)?=null,
    onOpenAlbum:(DesktopSong)->Unit,onOpenArtist:(String)->Unit) {
    // Lazy item providers can outlive this composition. Capture one immutable
    // snapshot so an old count never indexes the next (shorter) keys list.
    val queue=audio.queueState.collectAsState().value
    val total=queue.tracks.size
    val rows=remember(total,queue.index) {
        buildList<QueueRow> {
            val current=queue.index
            val hasCurrent=current in 0 until total
            if(hasCurrent && current>0) {
                add(QueueRow.Label("played","Played"))
                for(i in 0 until current) add(QueueRow.Track(i))
            }
            if(hasCurrent) {
                add(QueueRow.Label("now","Now playing"))
                add(QueueRow.Track(current))
            }
            val first=if(hasCurrent) current+1 else 0
            if(first<total) {
                add(QueueRow.Label("next","Next up"))
                for(i in first until total) add(QueueRow.Track(i))
            }
        }
    }
    val listState=rememberLazyListState()
    var dragging by remember {mutableStateOf<Long?>(null)}
    var dragCenter by remember {mutableFloatStateOf(0f)}
    var dropTarget by remember {mutableStateOf<Long?>(null)}
    var headerMenu by remember {mutableStateOf(false)}
    // Right-click a row to start selecting; right-click or click more rows to add/remove them.
    var selected by remember {mutableStateOf(emptySet<Long>())}
    val selecting=selected.isNotEmpty()
    fun toggle(key:Long) {selected=if(key in selected) selected-key else selected+key}
    val latestKeys by rememberUpdatedState(queue.keys)
    fun updateTarget() {
        dropTarget=listState.layoutInfo.visibleItemsInfo.filter {it.key is Long}.minByOrNull {
            kotlin.math.abs((it.offset+it.size/2f)-dragCenter)
        }?.key as? Long
    }
    // Open on the current track so the page is immediately about what is playing now.
    LaunchedEffect(Unit) {
        val now=rows.indexOfFirst {it is QueueRow.Label && it.id=="now"}
        if(now>0) listState.scrollToItem(now)
    }
    LaunchedEffect(queue.keys) {selected=selected.intersect(queue.keys.toSet())}
    LaunchedEffect(dragging) {
        if(dragging!=null)while(isActive) {
            val info=listState.layoutInfo
            val delta=when {dragCenter<info.viewportStartOffset+36 -> -12f
                dragCenter>info.viewportEndOffset-36 -> 12f;else -> 0f}
            if(delta!=0f){listState.scrollBy(delta);updateTarget()}
            delay(16)
        }
    }
    val colors=MaterialTheme.colorScheme
    Surface(shape=RoundedCornerShape(28.dp),tonalElevation=0.dp,color=colors.surfaceContainer,contentColor=colors.onSurface) {
        Column(Modifier.width(440.dp).heightIn(max=600.dp).padding(top=20.dp,bottom=12.dp,start=8.dp,end=8.dp)) {
            if(selecting) Row(Modifier.fillMaxWidth().padding(start=16.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${selected.size} selected",style=MaterialTheme.typography.headlineSmall)
                    Text("Right-click rows to add or remove them",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                }
                TextButton(onClick={selected=queue.keys.toSet()}) {Text("Select all")}
                IconButton(onClick={audio.removeQueue(selected);selected=emptySet()},
                    modifier=Modifier.semantics {contentDescription="Remove selected tracks"}) {
                    Glyph("remove",LocalContentColor.current)
                }
                IconButton(onClick={selected=emptySet()},modifier=Modifier.semantics {contentDescription="Cancel selection"}) {
                    Glyph("close",LocalContentColor.current)
                }
            } else Row(Modifier.fillMaxWidth().padding(start=16.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Queue",style=MaterialTheme.typography.headlineSmall)
                    Text(if(total==0) "Nothing queued" else countLabel(total,"track"),
                        style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                }
                Box {
                    IconButton(onClick={headerMenu=true},modifier=Modifier.semantics {contentDescription="Queue options"}) {
                        Glyph("more",LocalContentColor.current)
                    }
                    DropdownMenu(expanded=headerMenu,onDismissRequest={headerMenu=false}) {
                        DropdownMenuItem(text={Text("Clear played")},enabled=queue.index>0,
                            onClick={headerMenu=false;audio.clearPlayed()})
                        if(onSaveQueue!=null) DropdownMenuItem(text={Text("Save as playlist")},
                            leadingIcon={Glyph("playlist_add",LocalContentColor.current)},enabled=queue.tracks.isNotEmpty(),
                            onClick={headerMenu=false;onSaveQueue(queue.tracks)})
                    }
                }
                IconButton(onClick=onDismiss,modifier=Modifier.semantics {contentDescription="Close queue"}) {
                    Glyph("close",LocalContentColor.current)
                }
            }
            Spacer(Modifier.height(8.dp))
            if(queue.tracks.isEmpty()) Box(Modifier.fillMaxWidth().height(160.dp),contentAlignment=Alignment.Center) {
                Text("Play a track to build the queue.",color=colors.onSurfaceVariant)
            } else Box(Modifier.fillMaxWidth().weight(1f,fill=false)) {
                LazyColumn(state=listState,modifier=Modifier.fillMaxWidth().padding(end=8.dp),
                    verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    items(rows,key={row-> when(row) {
                        is QueueRow.Label -> row.id
                        is QueueRow.Track -> queue.keys[row.absolute]
                    }}) { row ->
                        when(row) {
                            is QueueRow.Label -> Text(row.text,Modifier.padding(start=16.dp,top=10.dp,bottom=4.dp),
                                style=MaterialTheme.typography.labelMedium,color=colors.onSurfaceVariant)
                            is QueueRow.Track -> {
                                val absolute=row.absolute
                                val song=queue.tracks[absolute];val key=queue.keys[absolute]
                                val current=absolute==queue.index
                                val played=queue.index>0 && absolute<queue.index
                                var menu by remember(key){mutableStateOf(false)}
                                val artists=remember(song.artist){ArtistNames.split(song.artist)}
                                val itemColor=when {key in selected->colors.surfaceContainerHighest
                                    current->colors.primaryContainer
                                    key==dropTarget && dragging!=null->colors.secondaryContainer;else->Color.Transparent}
                                Surface(shape=RoundedCornerShape(16.dp),color=itemColor,
                                    contentColor=if(current && key !in selected)colors.onPrimaryContainer else colors.onSurface,
                                    modifier=Modifier.fillMaxWidth().zIndex(if(dragging==key)1f else 0f).graphicsLayer {
                                        alpha=if(dragging==key) .9f else if(played) .6f else 1f
                                        if(dragging==key) {
                                            val item=listState.layoutInfo.visibleItemsInfo.firstOrNull {it.key==key}
                                            translationY=dragCenter-(item?.let {it.offset+it.size/2f} ?: dragCenter)
                                        }
                                    }) {
                                    Row(Modifier.fillMaxWidth().queueContextMenu {toggle(key)}
                                        .clickable {if(selecting) toggle(key) else audio.jumpToKey(key)}.padding(start=4.dp,end=4.dp,top=6.dp,bottom=6.dp),
                                        verticalAlignment=Alignment.CenterVertically) {
                                        if(selecting) Checkbox(checked=key in selected,onCheckedChange={toggle(key)},
                                            modifier=Modifier.size(32.dp).semantics {contentDescription="Select ${song.title}"})
                                        else Box(Modifier.size(32.dp).semantics {contentDescription="Drag ${song.title}"}.pointerInput(key) {
                                            detectDragGestures(onDragStart={
                                                val item=listState.layoutInfo.visibleItemsInfo.firstOrNull {it.key==key}
                                                if(item!=null){dragging=key;dragCenter=item.offset+item.size/2f;dropTarget=key}
                                            },onDragCancel={dragging=null;dropTarget=null},onDragEnd={
                                                val target=dropTarget
                                                if(target!=null && target!=key && key in latestKeys)audio.moveQueue(key,target)
                                                dragging=null;dropTarget=null
                                            }) {change,amount->change.consume();dragCenter+=amount.y;updateTarget()}
                                        },contentAlignment=Alignment.Center) {
                                            Glyph("drag",colors.onSurfaceVariant,Modifier.size(20.dp))
                                        }
                                        Spacer(Modifier.width(4.dp))
                                        Artwork(44.dp,current,song.coverPath)
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(song.title,maxLines=1,overflow=TextOverflow.Ellipsis,
                                                style=MaterialTheme.typography.bodyMedium.copy(fontWeight=FontWeight.SemiBold))
                                            Text(artists.joinToString(" · ").ifBlank{"Unknown artist"},maxLines=1,overflow=TextOverflow.Ellipsis,
                                                style=MaterialTheme.typography.labelSmall,
                                                color=if(current)colors.onPrimaryContainer.copy(alpha=.8f) else colors.onSurfaceVariant)
                                        }
                                        if(current) {
                                            Spacer(Modifier.width(8.dp))
                                            Glyph("levels",colors.primary,Modifier.size(18.dp))
                                        }
                                        if(!selecting) Box {
                                            IconButton(onClick={menu=true},modifier=Modifier.size(36.dp).semantics {contentDescription="Queue actions"}) {
                                                Glyph("more",LocalContentColor.current,Modifier.size(20.dp))
                                            }
                                            DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                                                DropdownMenuItem(text={Text("Move up")},leadingIcon={Glyph("up",LocalContentColor.current)},
                                                    enabled=absolute>0,onClick={menu=false;audio.moveQueueBy(key,-1)})
                                                DropdownMenuItem(text={Text("Move down")},leadingIcon={Glyph("down",LocalContentColor.current)},
                                                    enabled=absolute<queue.tracks.lastIndex,onClick={menu=false;audio.moveQueueBy(key,1)})
                                                HorizontalDivider()
                                                DropdownMenuItem(text={Text("Go to album: ${song.album.ifBlank{"Unknown album"}}",maxLines=1,overflow=TextOverflow.Ellipsis)},
                                                    leadingIcon={Glyph("album",LocalContentColor.current)},onClick={menu=false;onOpenAlbum(song)})
                                                artists.forEach {artist->
                                                    DropdownMenuItem(text={Text("Go to artist: $artist",maxLines=1,overflow=TextOverflow.Ellipsis)},
                                                        leadingIcon={Glyph("artist",LocalContentColor.current)},onClick={menu=false;onOpenArtist(artist)})
                                                }
                                                HorizontalDivider()
                                                DropdownMenuItem(text={Text("Remove from queue")},leadingIcon={Glyph("remove",LocalContentColor.current)},
                                                    onClick={menu=false;audio.removeQueue(setOf(key))})
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                AutoHideListScrollbar(listState,Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.queueContextMenu(open:()->Unit)=onPointerEvent(PointerEventType.Press) {event->
    if(event.buttons.isSecondaryPressed){open();event.changes.forEach{it.consume()}}
}
