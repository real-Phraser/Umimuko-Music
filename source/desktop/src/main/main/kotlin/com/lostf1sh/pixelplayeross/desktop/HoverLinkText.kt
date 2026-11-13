package com.lostf1sh.pixelplayeross.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow

/** A text link with no rectangular hover/press indication. */
@Composable
fun HoverLinkText(text:String,enabled:Boolean,style:TextStyle,onClick:()->Unit,modifier:Modifier=Modifier) {
    val interaction=remember{MutableInteractionSource()}
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    Text(text,modifier.clickable(interactionSource=interaction,indication=null,enabled=enabled,role=Role.Button,onClick=onClick)
        .pointerHoverIcon(if(enabled)PointerIcon.Hand else PointerIcon.Default),
        style=style,maxLines=1,overflow=TextOverflow.Ellipsis,
        textDecoration=if(enabled && (hovered || focused))TextDecoration.Underline else TextDecoration.None)
}
