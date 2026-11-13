package com.lostf1sh.pixelplayeross.desktop

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/** Google Material Symbols Rounded, bundled unmodified SVGs; see resources/icons/material-rounded. */
@Composable
fun Glyph(kind: String, color: Color, modifier: Modifier = Modifier) {
    require(kind in symbolNames) { "Unknown Material Symbol: $kind" }
    Icon(
        painter = painterResource("icons/material-rounded/$kind.svg"),
        contentDescription = null, // Labels belong to the containing controls, avoiding duplicate announcements.
        tint = color,
        modifier = modifier.size(24.dp),
    )
}

private val symbolNames = setOf(
    "play", "pause", "next", "previous", "search", "music", "levels",
    "folder", "library", "volume", "muted", "settings",
    "home", "pin", "unpin", "drag", "remove", "up", "down", "bubble", "restore", "close", "info",
    "album", "artist", "playlist", "playlist_add", "liked", "heart", "add", "back", "more", "queue", "expand", "collapse", "shuffle", "repeat", "repeat_one",
)
