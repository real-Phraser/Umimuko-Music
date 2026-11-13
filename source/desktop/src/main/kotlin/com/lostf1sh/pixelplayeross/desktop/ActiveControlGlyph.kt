package com.lostf1sh.pixelplayeross.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** A small dot makes Shuffle and Repeat state visible independently of color. */
@Composable
fun ActiveControlGlyph(icon: String, color: Color, active: Boolean) {
    Box(Modifier.size(36.dp),contentAlignment=Alignment.Center) {
        Glyph(icon,color,Modifier.align(Alignment.Center))
        if(active) Box(Modifier.align(Alignment.BottomCenter).padding(bottom=1.dp).size(4.dp)
            .background(MaterialTheme.colorScheme.primary,CircleShape))
    }
}
