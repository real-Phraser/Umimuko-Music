package com.lostf1sh.pixelplayeross.desktop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.*
import com.lostf1sh.pixelplayeross.desktop.library.DesktopSong
import com.lostf1sh.pixelplayeross.desktop.library.ArtistNames
import com.lostf1sh.pixelplayeross.desktop.playback.AudioController
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private data class BubbleState(val song:DesktopSong?,val ready:Boolean,val playing:Boolean,val previous:Boolean,val next:Boolean)

@Composable
fun FloatingPlayer(applicationScope:ApplicationScope,audio:AudioController,onRestore:()->Unit) {
    val colors=MaterialTheme.colorScheme
    val typography=MaterialTheme.typography
    val state=rememberWindowState(width=448.dp,height=80.dp,position=WindowPosition(Alignment.BottomEnd))
    val summary=remember(audio){audio.state.map {BubbleState(it.song,it.ready,it.playing,it.previous,it.next)}.distinctUntilChanged()}
    val initial=audio.state.value
    val playback by summary.collectAsState(BubbleState(initial.song,initial.ready,initial.playing,initial.previous,initial.next))
    with(applicationScope) {
        Window(onCloseRequest=onRestore,state=state,title="Umimuko Music · Mini player",
            undecorated=true,transparent=true,resizable=false,alwaysOnTop=true,focusable=false,
            icon=painterResource("branding/app-icon.png")) {
            MaterialTheme(colorScheme=colors,typography=typography) {
                val appear=remember{Animatable(0f)}
                LaunchedEffect(Unit){appear.animateTo(1f,tween(140))}
                MiniPlayerSurface(playback.song,playback.ready,playback.playing,playback.previous,playback.next,
                    audio::previous,audio::toggle,audio::next,onRestore,
                    modifier=Modifier.fillMaxSize().padding(6.dp).graphicsLayer {alpha=appear.value},
                    dragArea={modifier,content->WindowDraggableArea(modifier.semantics{contentDescription="Drag mini player"}){content()}})
            }
        }
    }
}

@Composable
fun MiniPlayerSurface(song:DesktopSong?,ready:Boolean,playing:Boolean,previous:Boolean,next:Boolean,
    onPrevious:()->Unit,onToggle:()->Unit,onNext:()->Unit,onRestore:()->Unit,modifier:Modifier=Modifier,
    dragArea:@Composable (Modifier,@Composable ()->Unit)->Unit={area,content->Box(area){content()}}) {
    val colors=MaterialTheme.colorScheme
    Surface(modifier,color=colors.surfaceVariant,shape=RoundedCornerShape(50),
        border=BorderStroke(1.dp,colors.outline.copy(alpha=.16f)),shadowElevation=0.dp) {
        Row(Modifier.padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            dragArea(Modifier.weight(1f)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Artwork(44.dp,song!=null,song?.coverPath)
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        BouncingText(song?.title ?: "Nothing playing",MaterialTheme.typography.bodyMedium.copy(fontWeight=FontWeight.SemiBold))
                        BouncingText(song?.let{ArtistNames.split(it.artist).joinToString(" · ")}.orEmpty(),
                            MaterialTheme.typography.labelSmall.copy(fontWeight=FontWeight.Normal,color=colors.onSurfaceVariant))
                    }
                }
            }
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(2.dp)) {
                BubbleButton("previous","Previous track",ready && previous,onPrevious)
                FilledIconButton(onClick=onToggle,enabled=ready && song!=null,
                    shape=CircleShape,modifier=Modifier.size(40.dp).semantics {contentDescription=if(playing)"Pause" else "Play"}) {
                    Glyph(if(playing)"pause" else "play",LocalContentColor.current)
                }
                BubbleButton("next","Next track",ready && next,onNext)
                BubbleButton("restore","Restore player bar",true,onRestore)
            }
        }
    }
}

@Composable
private fun BubbleButton(icon:String,label:String,enabled:Boolean,onClick:()->Unit) {
    IconButton(onClick=onClick,enabled=enabled,modifier=Modifier.size(36.dp).semantics {contentDescription=label}) {
        Glyph(icon,LocalContentColor.current,Modifier.size(22.dp))
    }
}
