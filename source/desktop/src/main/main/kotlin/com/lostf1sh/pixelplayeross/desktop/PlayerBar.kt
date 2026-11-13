package com.lostf1sh.pixelplayeross.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lostf1sh.pixelplayeross.desktop.playback.AudioController
import com.lostf1sh.pixelplayeross.desktop.playback.RepeatMode
import com.lostf1sh.pixelplayeross.desktop.library.ArtistNames

fun trackClock(ms: Long): String {
    val seconds=ms.coerceAtLeast(0)/1000
    return "%d:%02d".format(seconds/60,seconds%60)
}

/**
 * Three-zone bottom bar (Material 3 media layout): track on the left, transport and progress in the
 * middle, secondary actions and a single-row volume control on the right.
 */
@Composable
fun PlayerBar(audio:AudioController, liked:Boolean, editable:Boolean,
    onLike:()->Unit,onAdd:()->Unit,onQueue:()->Unit,onExpand:()->Unit,onCollapse:()->Unit={}) {
    val state by audio.state.collectAsState()
    var preview by remember(state.song?.id) {mutableStateOf<Float?>(null)}
    var volumePreview by remember {mutableStateOf<Float?>(null)}
    val progress=if(state.duration>0)(state.time.toDouble()/state.duration).toFloat().coerceIn(0f,1f) else 0f
    val colors=MaterialTheme.colorScheme
    Surface(color=colors.surfaceVariant,tonalElevation=4.dp) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact=maxWidth<900.dp
            Column(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp)) {
                state.error?.let {Text(it,color=colors.error,maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall)}
                Row(Modifier.fillMaxWidth().height(72.dp),verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    // Left: cover and track text.
                    Row(Modifier.weight(1f),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.clickable(interactionSource=remember{MutableInteractionSource()},indication=null,
                            enabled=state.song!=null,onClick=onExpand).pointerHoverIcon(if(state.song!=null)PointerIcon.Hand else PointerIcon.Default)) {
                            Artwork(56.dp,state.song!=null,state.song?.coverPath)
                        }
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                            HoverLinkText(state.song?.title ?: if(state.ready)"Pick something to play" else "Audio unavailable",
                                enabled=state.song!=null,onClick=onExpand,
                                style=MaterialTheme.typography.titleSmall.copy(fontWeight=FontWeight.SemiBold))
                            ArtistAlbumLine(state.song?.let {ArtistNames.split(it.artist).joinToString(" · ")}.orEmpty(),
                                state.song?.album.orEmpty(),state.song?.format ?: "Your music, your space")
                        }
                    }
                    // Center: transport above progress.
                    Column(Modifier.weight(1.5f).widthIn(max=560.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                            SmallPlayerButton("shuffle",if(state.shuffled) "Shuffle on" else "Shuffle off",state.ready,audio::toggleShuffle,active=state.shuffled)
                            SmallPlayerButton("previous","Previous track",state.ready && state.previous,audio::previous,size=40.dp)
                            FilledIconButton(onClick=audio::toggle,enabled=state.ready && state.song!=null,
                                shape=CircleShape,modifier=Modifier.size(48.dp).semantics {
                                    contentDescription=if(state.playing)"Pause" else "Play"
                                }) {Glyph(if(state.playing)"pause" else "play",LocalContentColor.current)}
                            SmallPlayerButton("next","Next track",state.ready && state.next,audio::next,size=40.dp)
                            SmallPlayerButton(if(state.repeat==RepeatMode.ONE) "repeat_one" else "repeat","Repeat ${state.repeat}",
                                state.ready,audio::cycleRepeat,active=state.repeat!=RepeatMode.OFF)
                        }
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text(trackClock(preview?.let {(it*state.duration).toLong()} ?: state.time),
                                Modifier.width(38.dp),style=MaterialTheme.typography.labelSmall,textAlign=TextAlign.End)
                            key(state.song?.id) {
                                CompactProgress(progress,state.ready && state.seekable && state.duration>0,"Playback position",
                                    Modifier.weight(1f).height(24.dp),onPreview={preview=it},onCommit=audio::seek)
                            }
                            Text(trackClock(state.duration),Modifier.width(38.dp),style=MaterialTheme.typography.labelSmall)
                        }
                    }
                    // Right: actions, then volume on one row.
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(2.dp)) {
                        SmallPlayerButton(if(liked)"heart" else "liked",if(liked)"Unlike current track" else "Like current track",
                            editable && state.song!=null,onLike)
                        SmallPlayerButton("playlist_add","Add current track to playlist",editable && state.song!=null,onAdd)
                        SmallPlayerButton("queue","Open playback queue",true,onQueue)
                        SmallPlayerButton("bubble","Collapse to floating player",true,onCollapse)
                        SmallPlayerButton("expand","Open full-screen player",state.song!=null,onExpand)
                        Spacer(Modifier.width(8.dp))
                        Glyph(if(state.volume==0)"muted" else "volume",colors.onSurfaceVariant,Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        CompactProgress(state.volume/100f,state.ready,"Volume",
                            Modifier.width(if(compact)72.dp else 104.dp).height(24.dp),
                            onPreview={volumePreview=it},onCommit={audio.volume((it*100).toInt())})
                        if(!compact) Text("${((volumePreview ?: (state.volume/100f))*100).toInt()}%",
                            Modifier.width(38.dp),style=MaterialTheme.typography.labelSmall,textAlign=TextAlign.End)
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallPlayerButton(icon:String,label:String,enabled:Boolean,onClick:()->Unit,active:Boolean=false,size:Dp=36.dp) {
    IconButton(onClick=onClick,enabled=enabled,modifier=Modifier.size(size).semantics {contentDescription=label}) {
        val color=if((icon=="heart" || active) && enabled)MaterialTheme.colorScheme.primary else LocalContentColor.current
        if(icon=="shuffle" || icon=="repeat" || icon=="repeat_one") ActiveControlGlyph(icon,color,active)
        else Glyph(icon,color)
    }
}
