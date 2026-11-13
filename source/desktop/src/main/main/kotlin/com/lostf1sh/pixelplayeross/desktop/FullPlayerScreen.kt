package com.lostf1sh.pixelplayeross.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lostf1sh.pixelplayeross.desktop.library.ArtistNames
import com.lostf1sh.pixelplayeross.desktop.playback.AudioController
import com.lostf1sh.pixelplayeross.desktop.playback.RepeatMode

@Composable
fun FullPlayerScreen(audio:AudioController,liked:Boolean,editable:Boolean,onLike:()->Unit,
    onAdd:()->Unit,onQueue:()->Unit,onExit:()->Unit) {
    val state by audio.state.collectAsState()
    var preview by remember(state.song?.id) {mutableStateOf<Float?>(null)}
    val coverColor=Color(state.song?.accentArgb ?: 0xFF2166C2.toInt())
    val gradient=remember(coverColor) {
        // Keep the cover hue perceptible without allowing bright or saturated artwork to dominate the page.
        val tint=lerp(Color(0xFF292D35),coverColor,.16f)
        Brush.verticalGradient(listOf(Color(0xFF1B1F27),tint,Color(0xFF20242C)))
    }
    val fullColors=remember(state.song?.accentArgb) {fullPlayerColorScheme(state.song?.accentArgb)}
    MaterialTheme(colorScheme=fullColors,typography=MaterialTheme.typography) {
    val colors=MaterialTheme.colorScheme
    CompositionLocalProvider(LocalContentColor provides colors.onBackground) {
    Column(Modifier.fillMaxSize().background(gradient)
        .padding(28.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            AppIcon(36.dp)
            Spacer(Modifier.width(12.dp))
            Text("NOW PLAYING",style=MaterialTheme.typography.labelLarge)
            Spacer(Modifier.weight(1f))
            TextButton(onClick=onExit) {Glyph("collapse",LocalContentColor.current);Spacer(Modifier.width(8.dp));Text("Back")}
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
            val artSize=minOf(maxWidth*.6f,maxHeight-8.dp,420.dp).coerceAtLeast(80.dp)
            Artwork(artSize,state.song!=null,state.song?.coverPath)
        }
        Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,
            verticalArrangement=Arrangement.spacedBy(6.dp)) {
            // Long names scroll back and forth inside a fixed width instead of being cut off.
            val nameWidth=Modifier.widthIn(max=700.dp)
            BouncingText(state.song?.title ?: "Pick something to play",
                MaterialTheme.typography.headlineMedium.copy(fontWeight=FontWeight.Bold),nameWidth,centered=true)
            BouncingText(state.song?.let {ArtistNames.split(it.artist).joinToString(" · ")}?.ifBlank {"Unknown artist"} ?: "",
                MaterialTheme.typography.titleMedium.copy(fontWeight=FontWeight.Normal),nameWidth,centered=true)
            BouncingText(state.song?.album.orEmpty(),
                MaterialTheme.typography.bodyMedium.copy(color=colors.onSurfaceVariant),nameWidth,centered=true)
        }
        Column(Modifier.widthIn(max=700.dp).fillMaxWidth().align(Alignment.CenterHorizontally)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Text(trackClock(preview?.let {(it*state.duration).toLong()} ?: state.time),Modifier.width(44.dp),
                    style=MaterialTheme.typography.labelSmall,textAlign=TextAlign.End)
                key(state.song?.id) {
                    CompactProgress(if(state.duration>0)(state.time.toDouble()/state.duration).toFloat() else 0f,
                        state.ready && state.seekable && state.duration>0,"Playback position",Modifier.weight(1f),
                        onPreview={preview=it},onCommit=audio::seek)
                }
                Text(trackClock(state.duration),Modifier.width(44.dp),style=MaterialTheme.typography.labelSmall)
            }
            Box(Modifier.fillMaxWidth().height(72.dp)) {
                Row(Modifier.align(Alignment.CenterStart),verticalAlignment=Alignment.CenterVertically) {
                    FullPlayerButton("shuffle",if(state.shuffled)"Shuffle on" else "Shuffle off",state.ready,audio::toggleShuffle,active=state.shuffled)
                    FullPlayerButton(if(state.repeat==RepeatMode.ONE)"repeat_one" else "repeat","Repeat ${state.repeat}",state.ready,audio::cycleRepeat,active=state.repeat!=RepeatMode.OFF)
                    FullPlayerButton(if(liked)"heart" else "liked",if(liked)"Unlike" else "Like",editable && state.song!=null,onLike)
                }
                Row(Modifier.align(Alignment.Center),verticalAlignment=Alignment.CenterVertically) {
                    FullPlayerButton("previous","Previous track",state.previous && state.ready,audio::previous)
                    FilledIconButton(onClick=audio::toggle,enabled=state.ready && state.song!=null,
                        shape=CircleShape,modifier=Modifier.padding(horizontal=16.dp).size(64.dp).semantics {
                            contentDescription=if(state.playing)"Pause" else "Play"
                        }) {Glyph(if(state.playing)"pause" else "play",LocalContentColor.current,Modifier.size(32.dp))}
                    FullPlayerButton("next","Next track",state.next && state.ready,audio::next)
                }
                Row(Modifier.align(Alignment.CenterEnd),verticalAlignment=Alignment.CenterVertically) {
                    FullPlayerButton("playlist_add","Add to playlist",editable && state.song!=null,onAdd)
                    FullPlayerButton("queue","Playback queue",true,onQueue)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center) {
                // Equal-width side slots keep the slider itself exactly centered.
                Box(Modifier.width(44.dp),contentAlignment=Alignment.CenterEnd) {
                    Glyph(if(state.volume==0)"muted" else "volume",colors.onSurfaceVariant,Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                CompactProgress(state.volume/100f,state.ready,"Volume",Modifier.width(140.dp),onCommit={audio.volume((it*100).toInt())})
                Spacer(Modifier.width(10.dp))
                Text("${state.volume}%",Modifier.width(44.dp),style=MaterialTheme.typography.labelSmall)
            }
            state.error?.let {Text(it,color=colors.error,maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall)}
        }
    }
    }
    }
}

@Composable
private fun FullPlayerButton(icon:String,label:String,enabled:Boolean,onClick:()->Unit,active:Boolean=false) {
    IconButton(onClick=onClick,enabled=enabled,modifier=Modifier.size(48.dp).semantics {contentDescription=label}) {
        val color=if((icon=="heart" || active) && enabled)MaterialTheme.colorScheme.primary else LocalContentColor.current
        if(icon=="shuffle" || icon=="repeat" || icon=="repeat_one") ActiveControlGlyph(icon,color,active)
        else Glyph(icon,color)
    }
}
