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
import com.lostf1sh.pixelplayeross.desktop.library.PlatformPaths
import com.lostf1sh.pixelplayeross.desktop.playback.AudioController
import com.lostf1sh.pixelplayeross.desktop.playback.RepeatMode
import com.lostf1sh.pixelplayeross.desktop.playback.WindowsMediaSession

fun main() {
    System.setProperty("apple.awt.application.name", "Umimuko Music")
    if (PlatformPaths.isMac()) {
        System.setProperty("apple.awt.application.appearance", "system")
        System.setProperty("apple.laf.useScreenMenuBar", "true")
    }
    if (PlatformPaths.isWindows()) {
        WindowsMediaSession.configureProcess()
    }
    IntroState.active.value=StartupPrefs.introEnabled
    IntroState.heroPending.value=StartupPrefs.introEnabled
    application {
    val applicationScope=this
    // Open maximized on Windows; on macOS open floating at the last size, respecting the menu bar.
    val defaultMaximized = !PlatformPaths.isMac() && StartupPrefs.windowMaximized
    val state=rememberWindowState(
        placement=if(defaultMaximized) WindowPlacement.Maximized else WindowPlacement.Floating,
        size=DpSize(StartupPrefs.windowWidth.dp,StartupPrefs.windowHeight.dp))
    LaunchedEffect(state) {
        launch {
            snapshotFlow {state.placement}.collect {
                if (!PlatformPaths.isMac()) {
                    StartupPrefs.windowMaximized=it==WindowPlacement.Maximized
                }
            }
        }
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
        if (PlatformPaths.isMac()) {
            window.rootPane.putClientProperty("apple.awt.fullscreenable", true)
        }

        // On macOS, ensure window placement respects the macOS menu bar insets
        LaunchedEffect(window) {
            if (PlatformPaths.isMac()) {
                window.rootPane.putClientProperty("apple.awt.fullscreenable", true)
                val insets = java.awt.Toolkit.getDefaultToolkit().getScreenInsets(window.graphicsConfiguration)
                if (window.y < insets.top) {
                    window.setLocation(window.x, insets.top)
                }
                window.addComponentListener(object : java.awt.event.ComponentAdapter() {
                    override fun componentMoved(e: java.awt.event.ComponentEvent) {
                        val curInsets = java.awt.Toolkit.getDefaultToolkit().getScreenInsets(window.graphicsConfiguration)
                        if (window.isShowing && window.y < curInsets.top) {
                            window.setLocation(window.x, curInsets.top)
                        }
                    }
                })
            }
        }

        DesktopApp(applicationScope,window,state,fullPlayer) { fullPlayer=it }
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
private fun FrameWindowScope.DesktopApp(
    applicationScope: ApplicationScope,
    parent: java.awt.Component,
    windowState: WindowState,
    fullPlayer: Boolean,
    onFullPlayer: (Boolean) -> Unit
) {
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    var followSystem by remember { mutableStateOf(StartupPrefs.followSystemTheme) }
    var dark by remember { mutableStateOf(if (followSystem) systemDark else StartupPrefs.darkMode) }
    LaunchedEffect(systemDark, followSystem) {
        if (followSystem) {
            dark = systemDark
        }
    }
    LaunchedEffect(dark) {
        if (!followSystem) {
            StartupPrefs.darkMode = dark
        }
    }
    LaunchedEffect(followSystem) {
        StartupPrefs.followSystemTheme = followSystem
    }

    var dynamic by remember { mutableStateOf(true) }
    var settingsOpen by remember { mutableStateOf(false) }
    var playerCollapsed by remember { mutableStateOf(false) }
    var introEnabled by remember { mutableStateOf(StartupPrefs.introEnabled) }
    var showIntro by remember { mutableStateOf(IntroState.active.value) }
    var albumAccent by remember { mutableStateOf<Int?>(null) }
    val colors = remember(dark, dynamic, albumAccent) { albumColorScheme(dark, if (dynamic) albumAccent else null) }
    val audio = remember { AudioController() }
    DisposableEffect(audio) { onDispose { audio.close() } }

    fun toggleFullscreen() {
        windowState.placement = if (windowState.placement == WindowPlacement.Fullscreen) {
            WindowPlacement.Floating
        } else {
            WindowPlacement.Fullscreen
        }
    }

    fun minimizeWindow() {
        (parent as? java.awt.Frame)?.let { frame ->
            frame.extendedState = java.awt.Frame.ICONIFIED
        } ?: run {
            windowState.isMinimized = true
        }
    }

    fun zoomWindow() {
        (parent as? java.awt.Frame)?.let { frame ->
            val isMax = (frame.extendedState and java.awt.Frame.MAXIMIZED_BOTH) == java.awt.Frame.MAXIMIZED_BOTH
            frame.extendedState = if (isMax) java.awt.Frame.NORMAL else java.awt.Frame.MAXIMIZED_BOTH
        } ?: run {
            windowState.placement = if (windowState.placement == WindowPlacement.Maximized) {
                WindowPlacement.Floating
            } else {
                WindowPlacement.Maximized
            }
        }
    }

    fun closeWindow() {
        val win = parent as? java.awt.Window
        if (win != null) {
            win.dispatchEvent(java.awt.event.WindowEvent(win, java.awt.event.WindowEvent.WINDOW_CLOSING))
        } else {
            applicationScope.exitApplication()
        }
    }

    // Keep the native title bar in step with the theme (dark/light and, on Windows 11, the exact colors).
    LaunchedEffect(dark, colors.background, colors.onBackground) {
        val window = parent as? java.awt.Window ?: return@LaunchedEffect
        // Apply right away; at startup the native window can take a few frames to exist, so retry briefly.
        var tries = 0
        while (!WindowTheme.apply(window, dark, colors.background, colors.onBackground) && tries++ < 60) kotlinx.coroutines.delay(16)
    }

    if (PlatformPaths.isMac()) {
        DisposableEffect(Unit) {
            if (java.awt.Desktop.isDesktopSupported()) {
                val desktop = java.awt.Desktop.getDesktop()
                try {
                    if (desktop.isSupported(java.awt.Desktop.Action.APP_PREFERENCES)) {
                        desktop.setPreferencesHandler { settingsOpen = true }
                    }
                } catch (e: Throwable) {}
            }
            onDispose {
                if (java.awt.Desktop.isDesktopSupported()) {
                    val desktop = java.awt.Desktop.getDesktop()
                    try {
                        if (desktop.isSupported(java.awt.Desktop.Action.APP_PREFERENCES)) {
                            desktop.setPreferencesHandler(null)
                        }
                    } catch (e: Throwable) {}
                }
            }
        }

        val playbackState by audio.state.collectAsState()
        val isPlaying = playbackState.playing
        val hasSong = playbackState.song != null

        MenuBar {
            Menu("File") {
                Item("Settings...", shortcut = KeyShortcut(Key.Comma, meta = true), onClick = { settingsOpen = true })
                Separator()
                Item("Close Window", shortcut = KeyShortcut(Key.W, meta = true), onClick = { closeWindow() })
                Separator()
                Item("Quit Umimuko Music", shortcut = KeyShortcut(Key.Q, meta = true), onClick = { applicationScope.exitApplication() })
            }
            Menu("Edit") {
                Item("Undo", shortcut = KeyShortcut(Key.Z, meta = true), onClick = {})
                Item("Redo", shortcut = KeyShortcut(Key.Z, meta = true, shift = true), onClick = {})
                Separator()
                Item("Cut", shortcut = KeyShortcut(Key.X, meta = true), onClick = {
                    val focused = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner as? javax.swing.JComponent
                    focused?.actionMap?.get(javax.swing.text.DefaultEditorKit.cutAction)?.actionPerformed(
                        java.awt.event.ActionEvent(focused, java.awt.event.ActionEvent.ACTION_PERFORMED, "cut")
                    )
                })
                Item("Copy", shortcut = KeyShortcut(Key.C, meta = true), onClick = {
                    val focused = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner as? javax.swing.JComponent
                    focused?.actionMap?.get(javax.swing.text.DefaultEditorKit.copyAction)?.actionPerformed(
                        java.awt.event.ActionEvent(focused, java.awt.event.ActionEvent.ACTION_PERFORMED, "copy")
                    )
                })
                Item("Paste", shortcut = KeyShortcut(Key.V, meta = true), onClick = {
                    val focused = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner as? javax.swing.JComponent
                    focused?.actionMap?.get(javax.swing.text.DefaultEditorKit.pasteAction)?.actionPerformed(
                        java.awt.event.ActionEvent(focused, java.awt.event.ActionEvent.ACTION_PERFORMED, "paste")
                    )
                })
                Item("Select All", shortcut = KeyShortcut(Key.A, meta = true), onClick = {
                    val focused = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner as? javax.swing.JComponent
                    focused?.actionMap?.get(javax.swing.text.DefaultEditorKit.selectAllAction)?.actionPerformed(
                        java.awt.event.ActionEvent(focused, java.awt.event.ActionEvent.ACTION_PERFORMED, "selectAll")
                    )
                })
            }
            Menu("View") {
                val isFullscreen = windowState.placement == WindowPlacement.Fullscreen
                Item(
                    if (isFullscreen) "Exit Full Screen" else "Enter Full Screen",
                    shortcut = KeyShortcut(Key.F, meta = true, ctrl = true),
                    onClick = { toggleFullscreen() }
                )
                Item(if (fullPlayer) "Exit Full Screen Player" else "Full Screen Player", onClick = { onFullPlayer(!fullPlayer) })
                CheckboxItem("Floating Mini Player", checked = playerCollapsed, onCheckedChange = { playerCollapsed = it })
                Separator()
                CheckboxItem("Follow System Theme", checked = followSystem, onCheckedChange = {
                    followSystem = it
                    if (it) dark = systemDark
                })
                CheckboxItem("Dark Mode", checked = dark, onCheckedChange = {
                    followSystem = false
                    dark = it
                })
                CheckboxItem("Dynamic Album Colors", checked = dynamic, onCheckedChange = { dynamic = it })
                CheckboxItem("Startup Animation", checked = introEnabled, onCheckedChange = {
                    introEnabled = it
                    StartupPrefs.introEnabled = it
                })
            }
            Menu("Controls") {
                Item(
                    if (isPlaying) "Pause" else "Play",
                    enabled = hasSong,
                    shortcut = KeyShortcut(Key.Spacebar),
                    onClick = { audio.toggle() }
                )
                Item(
                    "Next Track",
                    enabled = playbackState.next,
                    shortcut = KeyShortcut(Key.DirectionRight, meta = true),
                    onClick = { audio.next() }
                )
                Item(
                    "Previous Track",
                    enabled = playbackState.previous,
                    shortcut = KeyShortcut(Key.DirectionLeft, meta = true),
                    onClick = { audio.previous() }
                )
                Separator()
                Item(
                    "Volume Up",
                    shortcut = KeyShortcut(Key.DirectionUp, meta = true),
                    onClick = { audio.volume(playbackState.volume + 5) }
                )
                Item(
                    "Volume Down",
                    shortcut = KeyShortcut(Key.DirectionDown, meta = true),
                    onClick = { audio.volume(playbackState.volume - 5) }
                )
                Separator()
                CheckboxItem(
                    "Shuffle",
                    checked = playbackState.shuffled,
                    onCheckedChange = { audio.toggleShuffle() }
                )
                CheckboxItem(
                    "Repeat",
                    checked = playbackState.repeat != RepeatMode.OFF,
                    onCheckedChange = { audio.cycleRepeat() }
                )
            }
            Menu("Window") {
                Item("Minimize", shortcut = KeyShortcut(Key.M, meta = true), onClick = { minimizeWindow() })
                Item("Zoom", onClick = { zoomWindow() })
                Separator()
                Item("Bring All to Front", onClick = {
                    (parent as? java.awt.Window)?.toFront()
                })
            }
            Menu("Help") {
                Item("GitHub Repository", onClick = { openLink("https://github.com/real-Phraser") })
                Item("Telegram Channel", onClick = { openLink("https://t.me/theCodePhraser") })
            }
        }
    }

    MaterialTheme(colorScheme=colors,typography=AppTypography) {
        Surface(Modifier.fillMaxSize().onPreviewKeyEvent {event->
            if(event.type==KeyEventType.KeyDown) {
                if(fullPlayer && event.key==Key.Escape) {
                    onFullPlayer(false);true
                } else if(PlatformPaths.isMac() && event.isMetaPressed) {
                    when {
                        event.isCtrlPressed && event.key==Key.F -> { toggleFullscreen(); true }
                        !event.isCtrlPressed && !event.isAltPressed && !event.isShiftPressed && event.key==Key.M -> { minimizeWindow(); true }
                        !event.isCtrlPressed && !event.isAltPressed && !event.isShiftPressed && event.key==Key.W -> { closeWindow(); true }
                        !event.isCtrlPressed && !event.isAltPressed && !event.isShiftPressed && event.key==Key.Comma -> { settingsOpen=true; true }
                        !event.isCtrlPressed && !event.isAltPressed && !event.isShiftPressed && event.key==Key.Q -> { applicationScope.exitApplication(); true }
                        else -> false
                    }
                } else false
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
                            DropdownMenuItem(text={Text("Follow system theme")},
                                trailingIcon={Switch(checked=followSystem,onCheckedChange={
                                    followSystem=it
                                    if(it) dark=systemDark
                                })},
                                onClick={
                                    followSystem=!followSystem
                                    if(followSystem) dark=systemDark
                                })
                            DropdownMenuItem(text={Text("Dark mode")},
                                trailingIcon={Switch(checked=dark,onCheckedChange={
                                    followSystem=false
                                    dark=it
                                })},
                                onClick={
                                    followSystem=false
                                    dark=!dark
                                })
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
                LibraryPanel(applicationScope,parent,fullPlayer,onFullPlayer,playerCollapsed,{playerCollapsed=it},audio) {albumAccent=it}
            }
            if(showIntro) StartupIntro {showIntro=false;IntroState.active.value=false}
            }
        }
    }
}
