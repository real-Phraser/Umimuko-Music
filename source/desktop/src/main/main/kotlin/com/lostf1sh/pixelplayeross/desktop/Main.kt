package com.lostf1sh.pixelplayeross.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.key.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import java.awt.Dimension
import com.lostf1sh.pixelplayeross.desktop.playback.WindowsMediaSession

fun main() {
    WindowsMediaSession.configureProcess()
    IntroState.active.value=StartupPrefs.introEnabled
    IntroState.heroPending.value=StartupPrefs.introEnabled
    application {
    val applicationScope=this
    // Open maximized (the first time and whenever it was maximized when closed); otherwise at the last size.
    val state=rememberWindowState(
        placement=if(StartupPrefs.windowMaximized) WindowPlacement.Maximized else WindowPlacement.Floating,
        size=DpSize(StartupPrefs.windowWidth.dp,StartupPrefs.windowHeight.dp))
    LaunchedEffect(state) {
        launch {snapshotFlow {state.placement}.collect {StartupPrefs.windowMaximized=it==WindowPlacement.Maximized}}
        launch {
            snapshotFlow {state.size}.collect {size->
                if(state.placement==WindowPlacement.Floating && size.width>=760.dp && size.height>=600.dp) {
                    StartupPrefs.windowWidth=size.width.value.toInt();StartupPrefs.windowHeight=size.height.value.toInt()
                }
            }
        }
    }
    var fullPlayer by remember {mutableStateOf(false)}
    Window(onCloseRequest=::exitApplication,title="Umimuko Music",state=state,
        icon=painterResource("branding/app-icon.png")) {
        window.minimumSize=Dimension(760,600)
        DesktopApp(applicationScope,window,fullPlayer) { fullPlayer=it }
    }

}
}

@Composable
fun AppIcon(size:Dp=42.dp,tint:Color=Color.White) {
    // The mark is a white, transparent image; tint it for the surface it sits on (white by default).
    Image(painterResource("branding/app-icon.png"),contentDescription="Umimuko Music",
        modifier=Modifier.size(size),colorFilter=ColorFilter.tint(tint))
}

@Composable
private fun SocialLink(icon:String,label:String,url:String) {
    IconButton(onClick={openLink(url)},modifier=Modifier.size(40.dp).semantics {contentDescription=label}) {
        Image(painterResource(icon),contentDescription=null,modifier=Modifier.size(22.dp),
            colorFilter=ColorFilter.tint(MaterialTheme.colorScheme.onSurface))
    }
}

private fun openLink(url:String) {
    Thread {
        try {
            if(java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().browse(java.net.URI(url))
        } catch(ex:Exception) {}
    }.start()
}

@Composable
private fun DesktopApp(applicationScope:ApplicationScope,parent:java.awt.Component,fullPlayer:Boolean,onFullPlayer:(Boolean)->Unit) {
    var dark by remember {mutableStateOf(StartupPrefs.darkMode)}
    LaunchedEffect(dark) {StartupPrefs.darkMode=dark}
    var dynamic by remember {mutableStateOf(true)}
    var settingsOpen by remember {mutableStateOf(false)}
    var playerCollapsed by remember {mutableStateOf(false)}
    var introEnabled by remember {mutableStateOf(StartupPrefs.introEnabled)}
    var showIntro by remember {mutableStateOf(IntroState.active.value)}
    var albumAccent by remember {mutableStateOf<Int?>(null)}
    val colors=remember(dark,dynamic,albumAccent) {albumColorScheme(dark,if(dynamic)albumAccent else null)}
    // Keep the native title bar in step with the theme (dark/light and, on Windows 11, the exact colors).
    LaunchedEffect(dark,colors.background,colors.onBackground) {
        val window=parent as? java.awt.Window ?: return@LaunchedEffect
        // Apply right away; at startup the native window can take a few frames to exist, so retry briefly.
        var tries=0
        while(!WindowTheme.apply(window,dark,colors.background,colors.onBackground) && tries++<60) kotlinx.coroutines.delay(16)
    }
    MaterialTheme(colorScheme=colors,typography=AppTypography) {
        Surface(Modifier.fillMaxSize().onPreviewKeyEvent {event->
            if(fullPlayer && event.type==KeyEventType.KeyDown && event.key==Key.Escape) {
                onFullPlayer(false);true
            } else false
        },color=colors.background) {
            Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal=28.dp,vertical=14.dp),
                    verticalAlignment=Alignment.CenterVertically) {
                    AppIcon(tint=MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(12.dp))
                    Text("Umimuko Music",style=MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.weight(1f))
                    Box {
                        FilledTonalIconButton(onClick={settingsOpen=true},shape=CircleShape,
                            modifier=Modifier.size(40.dp).semantics {contentDescription="Settings"}) {
                            Glyph("settings",MaterialTheme.colorScheme.onSurface,Modifier.size(22.dp))
                        }
                        DropdownMenu(expanded=settingsOpen,onDismissRequest={settingsOpen=false}) {
                            DropdownMenuItem(text={Text("Dark mode")},
                                trailingIcon={Switch(checked=dark,onCheckedChange={dark=it})},
                                onClick={dark=!dark})
                            DropdownMenuItem(text={Text("Album colors")},
                                trailingIcon={Switch(checked=dynamic,onCheckedChange={dynamic=it})},
                                onClick={dynamic=!dynamic})
                            DropdownMenuItem(text={Text("Startup animation")},
                                trailingIcon={Switch(checked=introEnabled,onCheckedChange={introEnabled=it;StartupPrefs.introEnabled=it})},
                                onClick={introEnabled=!introEnabled;StartupPrefs.introEnabled=introEnabled})
                            DropdownMenuItem(text={Text("Floating mini player")},
                                trailingIcon={Switch(checked=playerCollapsed,onCheckedChange={playerCollapsed=it})},
                                onClick={playerCollapsed=!playerCollapsed})
                            HorizontalDivider(Modifier.padding(vertical=4.dp))
                            Column(Modifier.fillMaxWidth().padding(start=16.dp,end=16.dp,top=4.dp,bottom=8.dp),
                                horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Text("Made by Phraser",style=MaterialTheme.typography.labelMedium,
                                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                                    SocialLink("branding/telegram.png","Telegram","https://t.me/theCodePhraser")
                                    SocialLink("branding/github.png","GitHub","https://github.com/real-Phraser")
                                }
                            }
                        }
                    }
                }
                LibraryPanel(applicationScope,parent,fullPlayer,onFullPlayer,playerCollapsed,{playerCollapsed=it}) {albumAccent=it}
            }
            if(showIntro) StartupIntro {showIntro=false;IntroState.active.value=false}
            }
        }
    }
}
