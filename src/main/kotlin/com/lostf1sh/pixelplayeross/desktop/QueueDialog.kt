package com.lostf1sh.pixelplayeross.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
    var dragHeight by remember {mutableIntStateOf(0)}
    var dropTarget by remember {mutableStateOf<Long?>(null)}
    var headerMenu by remember {mutableStateOf(false)}
    // Right-click a row to start selecting; right-click or click more rows to add/remove them.
    var selected by remember {mutableStateOf(emptySet<Long>())}
    val selecting=selected.isNotEmpty()
    fun toggle(key:Long) {selected=if(key in selected) selected-key else selected+key}
    val latestKeys by rememberUpdatedState(queue.keys)
    val latestSelecting by rememberUpdatedState(selecting)
    val density=LocalDensity.current
    val dragHandleMaxX=with(density) {40.dp.toPx()}
    val autoScrollEdgePx=with(density) {40.dp.toPx()}
    val autoScrollStepPx=with(density) {10.dp.toPx()}
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
    LaunchedEffect(queue.keys) {
        selected=selected.intersect(queue.keys.toSet())
        if(dragging!=null && dragging !in queue.keys) {
            dragging=null
            dropTarget=null
        }
    }
    LaunchedEffect(dragging) {
        if(dragging!=null)while(isActive) {
            val info=listState.layoutInfo
            val delta=when {dragCenter<info.viewportStartOffset+autoScrollEdgePx -> -autoScrollStepPx
                dragCenter>info.viewportEndOffset-autoScrollEdgePx -> autoScrollStepPx;else -> 0f}
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
            } else Box(Modifier.fillMaxWidth().weight(1f,fill=false).pointerInput(Unit) {
                // Hoist drag handling above LazyColumn so item recycling or translation never cancels/corrupts the gesture.
                awaitEachGesture {
                    val down=awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
                    if(latestSelecting || currentEvent.buttons.isSecondaryPressed) return@awaitEachGesture
                    if(down.position.x>dragHandleMaxX) return@awaitEachGesture
                    val item=listState.layoutInfo.visibleItemsInfo.firstOrNull {
                        it.key is Long && down.position.y>=it.offset && down.position.y<=it.offset+it.size
                    } ?: return@awaitEachGesture
                    val key=item.key as Long
                    val touchSlop=viewConfiguration.touchSlop
                    val dragOffsetInItem=down.position.y-item.offset
                    var totalDy=0f
                    var started=false
                    var prevY=down.position.y
                    try {
                        while(true) {
                            val event=awaitPointerEvent(PointerEventPass.Initial)
                            val change=event.changes.firstOrNull {it.id==down.id} ?: break
                            if(!change.pressed) {
                                if(started) {
                                    val target=dropTarget
                                    if(target!=null && target!=key && key in latestKeys) audio.moveQueue(key,target)
                                }
                                break
                            }
                            val dy=change.position.y-prevY
                            prevY=change.position.y
                            if(!started) {
                                totalDy+=dy
                                if(kotlin.math.abs(totalDy)>=touchSlop) {
                                    started=true
                                    dragging=key
                                    dragHeight=item.size
                                    dropTarget=key
                                }
                            }
                            if(started) {
                                change.consume()
                                val viewportHeight=(listState.layoutInfo.viewportEndOffset-listState.layoutInfo.viewportStartOffset).toFloat()
                                val halfHeight=dragHeight/2f
                                val rawCenter=change.position.y-dragOffsetInItem+halfHeight
                                dragCenter=if(viewportHeight>dragHeight) rawCenter.coerceIn(halfHeight,viewportHeight-halfHeight) else rawCenter
                                updateTarget()
                            }
                        }
                    } finally {
                        dragging=null
                        dropTarget=null
                    }
                }
            }) {
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
                                QueueTrackRow(
                                    song=song,
                                    key=key,
                                    absolute=absolute,
                                    total=total,
                                    current=current,
                                    played=played,
                                    selected=key in selected,
                                    selecting=selecting,
                                    isDropTarget=key==dropTarget && dragging!=null && dragging!=key,
                                    isDraggingOverlay=false,
                                    isPlaceholder=dragging==key,
                                    colors=colors,
                                    modifier=Modifier.fillMaxWidth(),
                                    onToggle={toggle(key)},
                                    onJump={audio.jumpToKey(key)},
                                    onMoveBy={delta->audio.moveQueueBy(key,delta)},
                                    onOpenAlbum={onOpenAlbum(song)},
                                    onOpenArtist=onOpenArtist,
                                    onRemove={audio.removeQueue(setOf(key))}
                                )
                            }
                        }
                    }
                }
                val dragKey=dragging
                val dragIndex=if(dragKey!=null) queue.keys.indexOf(dragKey) else -1
                if(dragKey!=null && dragIndex in queue.tracks.indices) {
                    val dragY=dragCenter-dragHeight/2f
                    QueueTrackRow(
                        song=queue.tracks[dragIndex],
                        key=dragKey,
                        absolute=dragIndex,
                        total=total,
                        current=dragIndex==queue.index,
                        played=queue.index>0 && dragIndex<queue.index,
                        selected=dragKey in selected,
                        selecting=selecting,
                        isDropTarget=false,
                        isDraggingOverlay=true,
                        isPlaceholder=false,
                        colors=colors,
                        modifier=Modifier.fillMaxWidth().padding(end=8.dp).zIndex(1f).graphicsLayer {
                            translationY=dragY
                            alpha=.92f
                        },
                        onToggle={},
                        onJump={},
                        onMoveBy={},
                        onOpenAlbum={},
                        onOpenArtist={},
                        onRemove={}
                    )
                }
                AutoHideListScrollbar(listState,Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun QueueTrackRow(
    song:DesktopSong,
    key:Long,
    absolute:Int,
    total:Int,
    current:Boolean,
    played:Boolean,
    selected:Boolean,
    selecting:Boolean,
    isDropTarget:Boolean,
    isDraggingOverlay:Boolean,
    isPlaceholder:Boolean,
    colors:ColorScheme,
    modifier:Modifier=Modifier,
    onToggle:()->Unit,
    onJump:()->Unit,
    onMoveBy:(Int)->Unit,
    onOpenAlbum:()->Unit,
    onOpenArtist:(String)->Unit,
    onRemove:()->Unit
) {
    var menu by remember(key) {mutableStateOf(false)}
    val artists=remember(song.artist) {ArtistNames.split(song.artist)}
    val itemColor=when {
        selected -> colors.surfaceContainerHighest
        current -> colors.primaryContainer
        isDropTarget -> colors.secondaryContainer
        isDraggingOverlay -> colors.surfaceContainerHigh
        else -> Color.Transparent
    }
    Surface(
        shape=RoundedCornerShape(16.dp),
        color=itemColor,
        shadowElevation=if(isDraggingOverlay) 6.dp else 0.dp,
        contentColor=if(current && !selected) colors.onPrimaryContainer else colors.onSurface,
        modifier=modifier.graphicsLayer {
            alpha=when {
                isPlaceholder -> 0f
                isDraggingOverlay -> .92f
                played -> .6f
                else -> 1f
            }
        }
    ) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).queueContextMenu(onToggle)
                .clickable(enabled=!isDraggingOverlay) {if(selecting) onToggle() else onJump()}
                .padding(start=4.dp,end=4.dp,top=6.dp,bottom=6.dp),
            verticalAlignment=Alignment.CenterVertically
        ) {
            if(selecting) Checkbox(
                checked=selected,
                onCheckedChange={onToggle()},
                modifier=Modifier.size(32.dp).semantics {contentDescription="Select ${song.title}"}
            ) else Box(
                Modifier.size(32.dp).semantics {contentDescription="Drag ${song.title}"},
                contentAlignment=Alignment.Center
            ) {
                Glyph("drag",colors.onSurfaceVariant,Modifier.size(20.dp))
            }
            Spacer(Modifier.width(4.dp))
            Artwork(44.dp,current,song.coverPath)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    song.title,
                    maxLines=1,
                    overflow=TextOverflow.Ellipsis,
                    style=MaterialTheme.typography.bodyMedium.copy(fontWeight=FontWeight.SemiBold)
                )
                Text(
                    artists.joinToString(" · ").ifBlank {"Unknown artist"},
                    maxLines=1,
                    overflow=TextOverflow.Ellipsis,
                    style=MaterialTheme.typography.labelSmall,
                    color=if(current) colors.onPrimaryContainer.copy(alpha=.8f) else colors.onSurfaceVariant
                )
            }
            if(current) {
                Spacer(Modifier.width(8.dp))
                Glyph("levels",colors.primary,Modifier.size(18.dp))
            }
            if(!selecting && !isDraggingOverlay) Box {
                IconButton(onClick={menu=true},modifier=Modifier.size(36.dp).semantics {contentDescription="Queue actions"}) {
                    Glyph("more",LocalContentColor.current,Modifier.size(20.dp))
                }
                DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                    DropdownMenuItem(
                        text={Text("Move up")},
                        leadingIcon={Glyph("up",LocalContentColor.current)},
                        enabled=absolute>0,
                        onClick={menu=false;onMoveBy(-1)}
                    )
                    DropdownMenuItem(
                        text={Text("Move down")},
                        leadingIcon={Glyph("down",LocalContentColor.current)},
                        enabled=absolute<total-1,
                        onClick={menu=false;onMoveBy(1)}
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text={Text("Go to album: ${song.album.ifBlank {"Unknown album"}}",maxLines=1,overflow=TextOverflow.Ellipsis)},
                        leadingIcon={Glyph("album",LocalContentColor.current)},
                        onClick={menu=false;onOpenAlbum()}
                    )
                    artists.forEach {artist->
                        DropdownMenuItem(
                            text={Text("Go to artist: $artist",maxLines=1,overflow=TextOverflow.Ellipsis)},
                            leadingIcon={Glyph("artist",LocalContentColor.current)},
                            onClick={menu=false;onOpenArtist(artist)}
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text={Text("Remove from queue")},
                        leadingIcon={Glyph("remove",LocalContentColor.current)},
                        onClick={menu=false;onRemove()}
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.queueContextMenu(open:()->Unit)=onPointerEvent(PointerEventType.Press) {event->
    if(event.buttons.isSecondaryPressed){open();event.changes.forEach{it.consume()}}
}
