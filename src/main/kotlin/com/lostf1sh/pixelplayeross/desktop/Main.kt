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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import java.awt.Dimension
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import com.lostf1sh.pixelplayeross.desktop.library.PlatformPaths
import com.lostf1sh.pixelplayeross.desktop.playback.AudioController
import com.lostf1sh.pixelplayeross.desktop.playback.RepeatMode
import com.lostf1sh.pixelplayeross.desktop.playback.WindowsMediaSession
import com.lostf1sh.pixelplayeross.desktop.theme.ThemeManager
import com.lostf1sh.pixelplayeross.desktop.system.KeybindingManager
import com.lostf1sh.pixelplayeross.desktop.system.ShortcutAction
import com.lostf1sh.pixelplayeross.desktop.system.SleepTimer
import com.lostf1sh.pixelplayeross.desktop.ui.SettingsDialog

fun main() {
    System.setProperty("apple.awt.application.name", "Umimuko Music")
    if (PlatformPaths.isMac()) {
        System.setProperty("apple.awt.application.appearance", "system")
        System.setProperty("apple.laf.useScreenMenuBar", "true")
        try {
            if (java.awt.Taskbar.isTaskbarSupported()) {
                val taskbar = java.awt.Taskbar.getTaskbar()
                if (taskbar.isSupported(java.awt.Taskbar.Feature.ICON_IMAGE)) {
                    val stream = Thread.currentThread().contextClassLoader?.getResourceAsStream("branding/window-icon.png")
                        ?: Unit.javaClass.classLoader?.getResourceAsStream("branding/window-icon.png")
                    val img = stream?.use { ImageIO.read(it) }
                    if (img != null) {
                        taskbar.iconImage = img
                    }
                }
            }
        } catch (_: Throwable) {}
    }
    if (PlatformPaths.isWindows()) {
        System.setProperty("skiko.rendering.windows.direct3DSynchronousLiveResize", "true")
        WindowsMediaSession.configureProcess()
    }
    IntroState.active.value=StartupPrefs.introEnabled
    IntroState.heroPending.value=StartupPrefs.introEnabled
    application {
    val applicationScope=this
    val audio = remember { AudioController() }
    val sleepTimer = remember(audio) { SleepTimer(audio) }
    val keybindingManager = remember { KeybindingManager() }
    DisposableEffect(audio) { onDispose { audio.close() } }
    DisposableEffect(sleepTimer) { onDispose { sleepTimer.cancel() } }

    var mainWindow by remember { mutableStateOf<java.awt.Window?>(null) }
    var lastUnmutedVolume by remember { mutableStateOf(80) }

    fun toggleMute() {
        val current = audio.state.value.volume
        if (current > 0) {
            lastUnmutedVolume = current
            audio.volume(0)
        } else {
            audio.volume(lastUnmutedVolume.coerceIn(5, 100))
        }
    }

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
        @OptIn(kotlinx.coroutines.FlowPreview::class)
        launch {
            snapshotFlow { state.size }.debounce(400).collect { size ->
                if (state.placement == WindowPlacement.Floating && size.width >= 760.dp && size.height >= 600.dp) {
                    StartupPrefs.windowWidth = size.width.value.toInt()
                    StartupPrefs.windowHeight = size.height.value.toInt()
                }
            }
        }
    }
    var fullPlayer by remember {mutableStateOf(false)}

    if (isTraySupported) {
        val playbackState by audio.state.collectAsState()
        Tray(
            icon = painterResource("branding/window-icon.png"),
            tooltip = playbackState.song?.let { "Umimuko Music • ${it.title} - ${it.artist}" } ?: "Umimuko Music",
            onAction = {
                mainWindow?.let { w ->
                    w.isVisible = true
                    if ((w as? java.awt.Frame)?.extendedState == java.awt.Frame.ICONIFIED) {
                        (w as java.awt.Frame).extendedState = java.awt.Frame.NORMAL
                    }
                    w.toFront()
                    w.requestFocus()
                }
            },
            menu = {
                Item(playbackState.song?.let { "${it.title} - ${it.artist}" } ?: "Umimuko Music", enabled = false, onClick = {})
                Separator()
                Item(if (playbackState.playing) "Pause" else "Play", enabled = playbackState.song != null, onClick = { audio.toggle() })
                Item("Next", enabled = playbackState.next, onClick = { audio.next() })
                Item("Previous", enabled = playbackState.previous, onClick = { audio.previous() })
                Separator()
                Item(if (playbackState.volume == 0) "Unmute" else "Mute", onClick = { toggleMute() })
                Separator()
                val isWinVisible = mainWindow?.isVisible ?: true
                Item(if (isWinVisible) "Hide Window" else "Show Window", onClick = {
                    mainWindow?.let { w ->
                        if (w.isVisible) {
                            w.isVisible = false
                        } else {
                            w.isVisible = true
                            if ((w as? java.awt.Frame)?.extendedState == java.awt.Frame.ICONIFIED) {
                                (w as java.awt.Frame).extendedState = java.awt.Frame.NORMAL
                            }
                            w.toFront()
                            w.requestFocus()
                        }
                    }
                })
                Item("Quit Umimuko Music", onClick = { applicationScope.exitApplication() })
            }
        )
    }

    Window(
        onCloseRequest = {
            if (StartupPrefs.closeToTray && isTraySupported) {
                mainWindow?.isVisible = false
            } else {
                applicationScope.exitApplication()
            }
        },
        title = "Umimuko Music",
        state = state,
        icon = painterResource("branding/window-icon.png")
    ) {
        mainWindow = window
        window.minimumSize=Dimension(760,600)
        if (appIconImages.isNotEmpty()) {
            window.iconImages = appIconImages
        }
        val initialDark = if (StartupPrefs.followSystemTheme) androidx.compose.foundation.isSystemInDarkTheme() else StartupPrefs.darkMode
        val initialPalette = albumColorScheme(initialDark, null)
        val initialBg = java.awt.Color(
            (initialPalette.background.red * 255).toInt(),
            (initialPalette.background.green * 255).toInt(),
            (initialPalette.background.blue * 255).toInt()
        )
        window.background = initialBg
        window.rootPane.background = initialBg
        window.contentPane.background = initialBg
        window.contentPane.components.forEach { it.background = initialBg }

        if (PlatformPaths.isMac()) {
            window.rootPane.putClientProperty("apple.awt.fullscreenable", true)
        }

        // On macOS, ensure window placement respects the macOS menu bar insets
        LaunchedEffect(window) {
            mainWindow = window
            window.addWindowStateListener { e ->
                if (StartupPrefs.minimizeToTray && isTraySupported && (e.newState and java.awt.Frame.ICONIFIED) == java.awt.Frame.ICONIFIED) {
                    window.isVisible = false
                }
            }
            if (appIconImages.isNotEmpty()) {
                window.iconImages = appIconImages
            }
            if (PlatformPaths.isMac()) {
                window.rootPane.putClientProperty("apple.awt.fullscreenable", true)
                val insets = java.awt.Toolkit.getDefaultToolkit().getScreenInsets(window.graphicsConfiguration)
                val topInset = insets.top
                if (window.y < topInset) {
                    window.setLocation(window.x, topInset)
                }
                window.addComponentListener(object : java.awt.event.ComponentAdapter() {
                    override fun componentMoved(e: java.awt.event.ComponentEvent) {
                        if (window.isShowing && window.y < topInset) {
                            window.setLocation(window.x, topInset)
                        }
                    }
                })
            }
        }

        DesktopApp(
            applicationScope,
            window,
            state,
            fullPlayer,
            audio,
            sleepTimer,
            keybindingManager,
            onToggleMute = ::toggleMute
        ) { fullPlayer = it }
    }

}
}

private val appIconImages: List<java.awt.Image> by lazy {
    try {
        val stream = Thread.currentThread().contextClassLoader?.getResourceAsStream("branding/window-icon.png")
            ?: Unit.javaClass.classLoader?.getResourceAsStream("branding/window-icon.png")
            ?: Thread.currentThread().contextClassLoader?.getResourceAsStream("branding/app-icon.png")
        val base = stream?.use { ImageIO.read(it) }
        if (base != null) {
            val targetSizes = intArrayOf(16, 20, 24, 32, 40, 48, 64, 96, 128, 256)
            val list = ArrayList<java.awt.Image>(targetSizes.size + 1)
            for (size in targetSizes) {
                list.add(scaleIconImage(base, size))
            }
            list.add(base)
            list
        } else {
            emptyList()
        }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun scaleIconImage(src: BufferedImage, targetSize: Int): BufferedImage {
    var current = src
    var currentW = src.width
    var currentH = src.height

    while (currentW > targetSize * 2 || currentH > targetSize * 2) {
        val nextW = maxOf(targetSize, currentW / 2)
        val nextH = maxOf(targetSize, currentH / 2)
        val step = BufferedImage(nextW, nextH, BufferedImage.TYPE_INT_ARGB)
        val g = step.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.drawImage(current, 0, 0, nextW, nextH, null)
        } finally {
            g.dispose()
        }
        current = step
        currentW = nextW
        currentH = nextH
    }

    val finalImg = BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_ARGB)
    val g = finalImg.createGraphics()
    try {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.drawImage(current, 0, 0, targetSize, targetSize, null)
    } finally {
        g.dispose()
    }
    return finalImg
}

@Composable
fun AppIcon(size: Dp = 42.dp, tint: Color = Color.White) {
    // The mark is a white, transparent image; tint it for the surface it sits on (white by default).
    Image(
        painterResource("branding/app-icon.png"),
        contentDescription = "Umimuko Music",
        modifier = Modifier.size(size),
        colorFilter = ColorFilter.tint(tint)
    )
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
    audio: AudioController,
    sleepTimer: SleepTimer,
    keybindingManager: KeybindingManager,
    onToggleMute: () -> Unit,
    onFullPlayer: (Boolean) -> Unit
) {
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val followSystem by StartupPrefs.followSystemThemeFlow.collectAsState()
    var dark by remember { mutableStateOf(if (followSystem) systemDark else StartupPrefs.darkMode) }
    LaunchedEffect(systemDark, followSystem) {
        if (followSystem) {
            dark = systemDark
        } else {
            dark = StartupPrefs.darkMode
        }
    }
    LaunchedEffect(dark) {
        if (!followSystem) {
            StartupPrefs.darkMode = dark
        }
    }

    var folderRoots by remember { mutableStateOf<List<String>>(emptyList()) }
    var totalTracksCount by remember { mutableStateOf(0) }
    var duplicatesOpen by remember { mutableStateOf(false) }
    var addFolderAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var removeFolderAction by remember { mutableStateOf<((String) -> Unit)?>(null) }

    var dynamic by remember { mutableStateOf(true) }
    var settingsOpen by remember { mutableStateOf(false) }
    var playerCollapsed by remember { mutableStateOf(false) }
    var introEnabled by remember { mutableStateOf(StartupPrefs.introEnabled) }
    var showIntro by remember { mutableStateOf(IntroState.active.value) }
    var albumAccent by remember { mutableStateOf<Int?>(null) }
    val isWide by remember(windowState) { derivedStateOf { windowState.size.width >= 1000.dp } }
    val isCompact by remember(windowState) { derivedStateOf { windowState.size.width < 900.dp } }
    val onPlayerCollapsedChange = remember { { it: Boolean -> playerCollapsed = it } }
    val onAlbumAccentChange = remember { { it: Int? -> albumAccent = it } }
    val currentPreset by ThemeManager.currentPreset.collectAsState()
    val colors = remember(currentPreset, dark, albumAccent) { ThemeManager.resolveColorScheme(dark, albumAccent) }

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
                    StartupPrefs.followSystemTheme = it
                    if (it) dark = systemDark
                })
                CheckboxItem("Dark Mode", checked = dark, onCheckedChange = {
                    StartupPrefs.followSystemTheme = false
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
        Surface(Modifier.fillMaxSize().onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown) {
                if (fullPlayer && event.key == Key.Escape) {
                    onFullPlayer(false); true
                } else if (PlatformPaths.isMac() && event.isMetaPressed) {
                    when {
                        event.isCtrlPressed && event.key == Key.F -> { toggleFullscreen(); true }
                        !event.isCtrlPressed && !event.isAltPressed && !event.isShiftPressed && event.key == Key.M -> { minimizeWindow(); true }
                        !event.isCtrlPressed && !event.isAltPressed && !event.isShiftPressed && event.key == Key.W -> { closeWindow(); true }
                        !event.isCtrlPressed && !event.isAltPressed && !event.isShiftPressed && event.key == Key.Comma -> { settingsOpen = true; true }
                        !event.isCtrlPressed && !event.isAltPressed && !event.isShiftPressed && event.key == Key.Q -> { applicationScope.exitApplication(); true }
                        else -> false
                    }
                } else false
            } else false
        }.onKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown) {
                val action = keybindingManager.matchAction(event, isTextInputActive = false)
                if (action != null) {
                    val playback = audio.state.value
                    when (action) {
                        ShortcutAction.PLAY_PAUSE -> { audio.toggle(); true }
                        ShortcutAction.NEXT_TRACK -> { if (playback.next) audio.next(); true }
                        ShortcutAction.PREV_TRACK -> { if (playback.previous) audio.previous(); true }
                        ShortcutAction.SEEK_FORWARD -> {
                            if (playback.duration > 0) {
                                val newTime = (playback.time + 5000L).coerceAtMost(playback.duration)
                                audio.seek(newTime.toFloat() / playback.duration.toFloat())
                            }
                            true
                        }
                        ShortcutAction.SEEK_BACKWARD -> {
                            if (playback.duration > 0) {
                                val newTime = (playback.time - 5000L).coerceAtLeast(0L)
                                audio.seek(newTime.toFloat() / playback.duration.toFloat())
                            }
                            true
                        }
                        ShortcutAction.VOLUME_UP -> { audio.volume(playback.volume + 5); true }
                        ShortcutAction.VOLUME_DOWN -> { audio.volume(playback.volume - 5); true }
                        ShortcutAction.MUTE -> {
                            onToggleMute()
                            true
                        }
                        ShortcutAction.TOGGLE_FULLSCREEN -> { toggleFullscreen(); true }
                        ShortcutAction.TOGGLE_FULL_PLAYER -> { onFullPlayer(!fullPlayer); true }
                        ShortcutAction.TOGGLE_LYRICS -> {
                            if (!fullPlayer) onFullPlayer(true)
                            true
                        }
                    }
                } else false
            } else false
        }, color = colors.background) {
            Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                if (!fullPlayer || !PlatformPaths.isLinux()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal=28.dp,vertical=14.dp),
                        verticalAlignment=Alignment.CenterVertically) {
                        AppIcon(tint=MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.width(12.dp))
                        Text("Umimuko Music",style=MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.weight(1f))
                        FilledTonalIconButton(onClick={settingsOpen=true},shape=CircleShape,
                            modifier=Modifier.size(40.dp).semantics {contentDescription="Settings"}) {
                            Glyph("settings",MaterialTheme.colorScheme.onSurface,Modifier.size(22.dp))
                        }
                    }
                }
                LibraryPanel(
                    applicationScope,
                    parent,
                    fullPlayer,
                    onFullPlayer,
                    playerCollapsed,
                    onPlayerCollapsedChange,
                    audio,
                    isWide = isWide,
                    isCompact = isCompact,
                    onAlbumAccent = onAlbumAccentChange,
                    sleepTimer = sleepTimer,
                    duplicatesOpen = duplicatesOpen,
                    onDuplicatesOpenChange = { duplicatesOpen = it },
                    onLibraryStatsChanged = { roots, count ->
                        folderRoots = roots
                        totalTracksCount = count
                    },
                    onRegisterFolderActions = { add, remove ->
                        addFolderAction = add
                        removeFolderAction = remove
                    }
                )
            }
            if (settingsOpen) {
                SettingsDialog(
                    audio = audio,
                    onDismissRequest = { settingsOpen = false },
                    onOpenDuplicates = { duplicatesOpen = true },
                    folderRoots = folderRoots,
                    onRemoveFolder = { folder -> removeFolderAction?.invoke(folder) },
                    onAddFolder = { addFolderAction?.invoke() },
                    totalTracksCount = totalTracksCount,
                    keybindingManager = keybindingManager
                )
            }
            if(showIntro) StartupIntro {showIntro=false;IntroState.active.value=false}
            }
        }
    }
}
