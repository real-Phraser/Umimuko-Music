package com.lostf1sh.pixelplayeross.desktop
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.runtime.*

@Composable
fun DialogMotion(content:@Composable ()->Unit) {
    val visibility=remember {MutableTransitionState(false).apply {targetState=true}}
    AnimatedVisibility(visibleState=visibility,
        enter=fadeIn(tween(170))+scaleIn(tween(170),initialScale=.96f)) {content()}
}
