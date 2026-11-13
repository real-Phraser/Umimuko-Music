package com.lostf1sh.pixelplayeross.desktop

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

/** Thin at rest, wider only while pressed/dragged. Hit area remains 32dp high. */
@Composable
fun CompactProgress(value:Float, enabled:Boolean, label:String, modifier:Modifier=Modifier,
    onPreview:(Float?)->Unit={}, onCommit:(Float)->Unit) {
    var pressed by remember {mutableStateOf(false)}
    var local by remember {mutableStateOf(0f)}
    val commit by rememberUpdatedState(onCommit)
    val preview by rememberUpdatedState(onPreview)
    val latestValue by rememberUpdatedState(value)
    val thickness by animateDpAsState(if(pressed) 8.dp else 3.dp,label="Progress thickness")
    val thumb by animateDpAsState(if(pressed) 7.dp else 0.dp,label="Progress thumb")
    val colors=MaterialTheme.colorScheme
    Canvas(modifier.fillMaxWidth().height(32.dp).semantics {
        contentDescription=label
        progressBarRangeInfo=ProgressBarRangeInfo(value.coerceIn(0f,1f),0f..1f)
        if(!enabled)disabled() else setProgress { target -> commit(target.coerceIn(0f,1f));true }
    }.onKeyEvent { event ->
        if(enabled && event.type==KeyEventType.KeyDown) {
            when(event.key) {
                Key.DirectionLeft -> {commit((latestValue-.01f).coerceIn(0f,1f));true}
                Key.DirectionRight -> {commit((latestValue+.01f).coerceIn(0f,1f));true}
                Key.MoveHome -> {commit(0f);true}
                Key.MoveEnd -> {commit(1f);true}
                else -> false
            }
        } else false
    }.focusable(enabled).pointerInput(enabled) {
        if(!enabled)return@pointerInput
        awaitEachGesture {
            val down=awaitFirstDown()
            fun fraction(x:Float)=((x-8.dp.toPx())/(size.width-16.dp.toPx()).coerceAtLeast(1f)).coerceIn(0f,1f)
            down.consume();pressed=true;local=fraction(down.position.x);preview(local)
            try {
                while(true) {
                    val event=awaitPointerEvent()
                    val change=event.changes.firstOrNull {it.id==down.id} ?: break
                    if(change.isConsumed)break
                    local=fraction(change.position.x)
                    change.consume();preview(local)
                    if(!change.pressed) {commit(local);break}
                }
            } finally {pressed=false;preview(null)}
        }
    }) {
        val start=8.dp.toPx()
        val end=(size.width-start).coerceAtLeast(start)
        val fraction=(if(pressed)local else value).coerceIn(0f,1f)
        val x=start+(end-start)*fraction
        val y=size.height/2
        val alpha=if(enabled)1f else .35f
        drawLine(colors.onSurfaceVariant.copy(alpha=.2f*alpha),Offset(start,y),Offset(end,y),
            thickness.toPx(),StrokeCap.Round)
        if(fraction>0)drawLine(colors.primary.copy(alpha=alpha),Offset(start,y),Offset(x,y),
            thickness.toPx(),StrokeCap.Round)
        if(thumb.value>0)drawCircle(colors.primary.copy(alpha=alpha),thumb.toPx(),Offset(x,y))
    }
}
