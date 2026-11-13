package com.lostf1sh.pixelplayeross.desktop

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb

/** Fill every queue surface role: default Material tint/container tokens are purple. */
fun queueColorScheme(base:ColorScheme):ColorScheme {
    val dark=base.background.luminance()<.5f
    val inverse=lerp(if(dark)Color.White else Color.Black,base.primary,.08f)
    fun surface(amount:Float)=lerp(base.surface,base.primary,amount)
    return base.copy(
        surfaceTint=base.primary,
        surfaceDim=if(dark)base.background else base.surfaceVariant,
        surfaceBright=if(dark)base.surfaceVariant else base.background,
        surfaceContainerLowest=base.background,
        surfaceContainerLow=surface(.025f),surfaceContainer=surface(.06f),
        surfaceContainerHigh=surface(.09f),surfaceContainerHighest=surface(.12f),
        secondary=base.primary,onSecondary=base.onPrimary,
        secondaryContainer=base.primaryContainer,onSecondaryContainer=base.onPrimaryContainer,
        tertiary=base.primary,onTertiary=base.onPrimary,
        tertiaryContainer=base.primaryContainer,onTertiaryContainer=base.onPrimaryContainer,
        outline=lerp(base.surface,base.onSurface,.42f),outlineVariant=surface(.22f),
        inverseSurface=inverse,inverseOnSurface=contrastingText(inverse),
        inversePrimary=albumColorScheme(!dark,base.primary.toArgb()).primary,
    )
}
