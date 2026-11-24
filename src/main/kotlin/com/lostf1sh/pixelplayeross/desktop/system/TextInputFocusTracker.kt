package com.lostf1sh.pixelplayeross.desktop.system

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged

/**
 * Tracks whether any text input inside the current window holds focus so
 * preview-phase global shortcuts (such as Spacebar) do not hijack typing.
 */
class TextInputFocusTracker {
    private var focusedCount by mutableIntStateOf(0)
    val isTextInputActive: Boolean get() = focusedCount > 0

    fun setFocused(focused: Boolean, wasFocused: Boolean): Boolean {
        if (focused == wasFocused) return wasFocused
        if (focused) {
            focusedCount++
        } else {
            focusedCount = (focusedCount - 1).coerceAtLeast(0)
        }
        return focused
    }
}

val LocalTextInputFocusTracker = staticCompositionLocalOf<TextInputFocusTracker?> { null }

@Composable
fun Modifier.trackTextInputFocus(): Modifier {
    val tracker = LocalTextInputFocusTracker.current ?: return this
    var wasFocused by remember { mutableStateOf(false) }
    DisposableEffect(tracker) {
        onDispose {
            wasFocused = tracker.setFocused(false, wasFocused)
        }
    }
    return this.onFocusChanged { state ->
        wasFocused = tracker.setFocused(state.isFocused, wasFocused)
    }
}
