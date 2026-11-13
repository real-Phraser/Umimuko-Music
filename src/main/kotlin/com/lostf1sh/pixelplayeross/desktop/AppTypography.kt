package com.lostf1sh.pixelplayeross.desktop

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

private val Rubik = FontFamily(
    Font("fonts/rubik/Rubik-Regular.ttf", weight = FontWeight.Normal),
    Font("fonts/rubik/Rubik-Medium.ttf", weight = FontWeight.Medium),
    Font("fonts/rubik/Rubik-SemiBold.ttf", weight = FontWeight.SemiBold),
    Font("fonts/rubik/Rubik-Bold.ttf", weight = FontWeight.Bold),
)

val AppTypography: Typography = Typography().let { base ->
    base.copy(
        displayLarge=base.displayLarge.copy(fontFamily=Rubik),
        displayMedium=base.displayMedium.copy(fontFamily=Rubik),
        displaySmall=base.displaySmall.copy(fontFamily=Rubik),
        headlineLarge=base.headlineLarge.copy(fontFamily=Rubik),
        headlineMedium=base.headlineMedium.copy(fontFamily=Rubik),
        headlineSmall=base.headlineSmall.copy(fontFamily=Rubik),
        titleLarge=base.titleLarge.copy(fontFamily=Rubik),
        titleMedium=base.titleMedium.copy(fontFamily=Rubik),
        titleSmall=base.titleSmall.copy(fontFamily=Rubik),
        bodyLarge=base.bodyLarge.copy(fontFamily=Rubik),
        bodyMedium=base.bodyMedium.copy(fontFamily=Rubik),
        bodySmall=base.bodySmall.copy(fontFamily=Rubik),
        labelLarge=base.labelLarge.copy(fontFamily=Rubik),
        labelMedium=base.labelMedium.copy(fontFamily=Rubik),
        labelSmall=base.labelSmall.copy(fontFamily=Rubik),
    )
}
