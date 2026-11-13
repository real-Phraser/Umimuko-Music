package com.lostf1sh.pixelplayeross.desktop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.prefs.Preferences
import com.lostf1sh.pixelplayeross.desktop.library.PlatformPaths

/** Material 3 "emphasized decelerate": quick start, long soft landing. */
val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

/** True while the startup animation covers the window, so Home waits to animate in until it is gone. */
object IntroState {
    /** True while the startup cover is on screen. */
    val active = mutableStateOf(false)
    /** True from launch until Home has finished its "now playing" moment (or decided to skip it). */
    val heroPending = mutableStateOf(false)
}

/** Tiny persisted settings; java.util.prefs needs no files of our own. */
object StartupPrefs {
    private val node: Preferences = Preferences.userRoot().node("umimuko-music")
    var introEnabled: Boolean
        get() = try { node.getBoolean("startupAnimation", true) } catch (e: Exception) { true }
        set(value) { try { node.putBoolean("startupAnimation", value) } catch (e: Exception) {} }
    var followSystemTheme: Boolean
        get() = try { node.getBoolean("followSystemTheme", true) } catch (e: Exception) { true }
        set(value) { try { node.putBoolean("followSystemTheme", value) } catch (e: Exception) {} }
    var darkMode: Boolean
        get() = try { node.getBoolean("darkMode", true) } catch (e: Exception) { true }
        set(value) { try { node.putBoolean("darkMode", value) } catch (e: Exception) {} }
    var windowMaximized: Boolean
        get() = try { node.getBoolean("windowMaximized", !PlatformPaths.isMac()) } catch (e: Exception) { !PlatformPaths.isMac() }
        set(value) { try { node.putBoolean("windowMaximized", value) } catch (e: Exception) {} }
    var windowWidth: Int
        get() = try { node.getInt("windowWidth", 1180) } catch (e: Exception) { 1180 }
        set(value) { try { node.putInt("windowWidth", value) } catch (e: Exception) {} }
    var windowHeight: Int
        get() = try { node.getInt("windowHeight", 800) } catch (e: Exception) { 800 }
        set(value) { try { node.putInt("windowHeight", value) } catch (e: Exception) {} }
}

/**
 * Fade + small rise, started [delayMs] after the startup sequence ends. Runs once per [key] (state survives lazy-list scrolling),
 * and only touches the draw layer, so it never causes relayout.
 */
@Composable
fun Modifier.entrance(key: Any, delayMs: Long, animate: Boolean = true, durationMs: Int = 420, rise: Dp = 18.dp): Modifier {
    var played by rememberSaveable(key) { mutableStateOf(!animate) }
    val progress = remember(key) { Animatable(if (played) 1f else 0f) }
    LaunchedEffect(key) {
        if (!played) {
            snapshotFlow { IntroState.active.value || IntroState.heroPending.value }.first { !it }
            delay(delayMs.coerceIn(0L, 700L))
            progress.animateTo(1f, tween(durationMs, easing = EmphasizedDecelerate))
            played = true
        }
    }
    return this.graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = (1f - p) * rise.toPx()
    }
}

/**
 * Startup animation, about one second: a soft glow opens, the app mark settles with a small bounce,
 * the name fades in, then the cover dissolves into the window. Only draw-layer properties animate.
 */
@Composable
fun StartupIntro(onFinished: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val glow = remember { Animatable(0f) }
    val mark = remember { Animatable(0f) }
    val markScale = remember { Animatable(.72f) }
    val title = remember { Animatable(0f) }
    val cover = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        coroutineScope {
            launch { glow.animateTo(1f, tween(700, easing = EmphasizedDecelerate)) }
            launch { mark.animateTo(1f, tween(240)) }
            launch { markScale.animateTo(1f, spring(dampingRatio = .55f, stiffness = Spring.StiffnessMediumLow)) }
            launch { delay(220); title.animateTo(1f, tween(420, easing = EmphasizedDecelerate)) }
        }
        delay(120)
        cover.animateTo(0f, tween(260))
        onFinished()
    }
    Box(
        Modifier.fillMaxSize().graphicsLayer { alpha = cover.value }.background(colors.background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(280.dp).graphicsLayer {
                        val s = .6f + .4f * glow.value
                        scaleX = s; scaleY = s; alpha = glow.value
                    }.background(Brush.radialGradient(listOf(colors.primary.copy(alpha = .28f), Color.Transparent)))
                )
                Box(Modifier.graphicsLayer {
                    alpha = mark.value; scaleX = markScale.value; scaleY = markScale.value
                }) { AppIcon(104.dp, colors.onBackground) }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Umimuko Music", style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.graphicsLayer {
                    alpha = title.value; translationY = (1f - title.value) * 10.dp.toPx()
                }
            )
        }
    }
}
