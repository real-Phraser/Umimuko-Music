package com.lostf1sh.pixelplayeross.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
private fun transientVisibility(scrolling:Boolean):Boolean {
    var visible by remember {mutableStateOf(false)}
    LaunchedEffect(scrolling) {
        if(scrolling) visible=true else {delay(900);visible=false}
    }
    return visible
}

@Composable
private fun activeStyle():ScrollbarStyle {
    val accent=MaterialTheme.colorScheme.primary
    return ScrollbarStyle(minimalHeight=28.dp,thickness=6.dp,shape=RoundedCornerShape(4.dp),
        hoverDurationMillis=100,unhoverColor=accent.copy(alpha=.78f),hoverColor=accent)
}

private fun Modifier.keepVisibleWhilePressed(onHeld:(Boolean)->Unit)=pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
        onHeld(true)
        try {
            var pressed:Boolean
            do {
                pressed=awaitPointerEvent(PointerEventPass.Initial).changes.any {it.pressed}
            } while(pressed)
        } finally {onHeld(false)}
    }
}

@Composable
fun AutoHideListScrollbar(state:LazyListState,modifier:Modifier=Modifier) {
    val interaction=remember {MutableInteractionSource()}
    val dragged by interaction.collectIsDraggedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    var held by remember {mutableStateOf(false)}
    val visible=transientVisibility(state.isScrollInProgress || dragged || hovered || held)
    val scrollable=state.layoutInfo.totalItemsCount>state.layoutInfo.visibleItemsInfo.size
    val style=activeStyle()
    AnimatedVisibility(visible=visible && scrollable,modifier=modifier,
        enter=fadeIn(tween(100)),exit=fadeOut(tween(220))) {
        VerticalScrollbar(rememberScrollbarAdapter(state),Modifier.width(8.dp).fillMaxHeight()
            .keepVisibleWhilePressed {held=it},
            style=style,interactionSource=interaction)
    }
}

@Composable
fun AutoHideGridScrollbar(state:LazyGridState,modifier:Modifier=Modifier) {
    val interaction=remember {MutableInteractionSource()}
    val dragged by interaction.collectIsDraggedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    var held by remember {mutableStateOf(false)}
    val visible=transientVisibility(state.isScrollInProgress || dragged || hovered || held)
    val scrollable=state.layoutInfo.totalItemsCount>state.layoutInfo.visibleItemsInfo.size
    val style=activeStyle()
    AnimatedVisibility(visible=visible && scrollable,modifier=modifier,
        enter=fadeIn(tween(100)),exit=fadeOut(tween(220))) {
        VerticalScrollbar(rememberScrollbarAdapter(state),Modifier.width(8.dp).fillMaxHeight()
            .keepVisibleWhilePressed {held=it},
            style=style,interactionSource=interaction)
    }
}
