package com.lostf1sh.pixelplayeross.desktop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

/** Only overflowing text animates (back and forth); short text stays put, or centered when [centered] is set; each frame updates its layer, not text layout. */
@Composable
fun BouncingText(text:String,style:TextStyle,modifier:Modifier=Modifier,centered:Boolean=false) {
    val measurer=rememberTextMeasurer()
    val density=LocalDensity.current
    val width=remember(text,style,measurer,density){
        measurer.measure(AnnotatedString(text),style=style,maxLines=1,softWrap=false).size.width.toFloat()
    }
    BoxWithConstraints(modifier.fillMaxWidth().clipToBounds()) {
        val available=with(density){maxWidth.toPx()}
        val overflow=(width-available).coerceAtLeast(0f)
        val position=remember{Animatable(0f)}
        LaunchedEffect(text,overflow,density.density) {
            position.snapTo(0f)
            if(overflow>1f) {
                val duration=(overflow/density.density/24f*1000f).roundToInt().coerceIn(1000,30_000)
                delay(1400)
                while(isActive) {
                    position.animateTo(overflow,tween(duration,easing=LinearEasing));delay(1100)
                    position.animateTo(0f,tween(duration,easing=LinearEasing));delay(1400)
                }
            }
        }
        Text(text,modifier=Modifier.wrapContentWidth(Alignment.Start,unbounded=true).graphicsLayer {translationX=if(centered && overflow<=1f)(available-width)/2f else -position.value},
            style=style,maxLines=1,softWrap=false,overflow=TextOverflow.Clip)
    }
}
