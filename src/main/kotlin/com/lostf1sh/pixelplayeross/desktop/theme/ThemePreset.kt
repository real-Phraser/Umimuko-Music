package com.lostf1sh.pixelplayeross.desktop.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.lostf1sh.pixelplayeross.desktop.albumColorScheme

enum class ThemePreset(
    val id: String,
    val displayName: String,
    val description: String,
    val previewAccent: Color,
    val previewBackground: Color
) {
    DYNAMIC(
        id = "dynamic",
        displayName = "Dynamic Material You",
        description = "Harmonizes dynamically with the active album artwork colors",
        previewAccent = Color(0xFF64B5F6),
        previewBackground = Color(0xFF1E242B)
    ) {
        override fun colorScheme(isDark: Boolean, coverAccent: Int?): ColorScheme {
            return albumColorScheme(isDark, coverAccent)
        }
    },

    OLED_BLACK(
        id = "oled_black",
        displayName = "OLED Pure Black",
        description = "Pure pitch-black background with vibrant electric blue accents",
        previewAccent = Color(0xFF4DA3FF),
        previewBackground = Color(0xFF000000)
    ) {
        override fun colorScheme(isDark: Boolean, coverAccent: Int?): ColorScheme {
            val bg = Color(0xFF000000)
            val surface = Color(0xFF0A0A0A)
            val surfaceVar = Color(0xFF161616)
            val primary = Color(0xFF4DA3FF)
            return darkColorScheme(
                primary = primary,
                onPrimary = Color.Black,
                primaryContainer = Color(0xFF162B44),
                onPrimaryContainer = Color(0xFFD6E4FF),
                secondary = Color(0xFF68B4FF),
                onSecondary = Color.Black,
                background = bg,
                onBackground = Color(0xFFEEEEEE),
                surface = surface,
                onSurface = Color(0xFFE5E5E5),
                surfaceVariant = surfaceVar,
                onSurfaceVariant = Color(0xFFAAAAAA),
                outline = Color(0xFF333333),
                outlineVariant = Color(0xFF222222)
            )
        }
    },

    NORD(
        id = "nord",
        displayName = "Nordic Frost",
        description = "Arctic, north-bluish clean aesthetic inspired by the polar night",
        previewAccent = Color(0xFF88C0D0),
        previewBackground = Color(0xFF242933)
    ) {
        override fun colorScheme(isDark: Boolean, coverAccent: Int?): ColorScheme {
            val bg = Color(0xFF242933)
            val surface = Color(0xFF2E3440)
            val surfaceVar = Color(0xFF3B4252)
            val primary = Color(0xFF88C0D0)
            return darkColorScheme(
                primary = primary,
                onPrimary = Color(0xFF1D262F),
                primaryContainer = Color(0xFF434C5E),
                onPrimaryContainer = Color(0xFFECEFF4),
                secondary = Color(0xFF81A1C1),
                onSecondary = Color(0xFF1D262F),
                background = bg,
                onBackground = Color(0xFFECEFF4),
                surface = surface,
                onSurface = Color(0xFFECEFF4),
                surfaceVariant = surfaceVar,
                onSurfaceVariant = Color(0xFFD8DEE9),
                outline = Color(0xFF4C566A),
                outlineVariant = Color(0xFF3B4252)
            )
        }
    },

    DRACULA(
        id = "dracula",
        displayName = "Dracula",
        description = "Dark gothic palette with bright purple, pink, and green highlights",
        previewAccent = Color(0xFFBD93F9),
        previewBackground = Color(0xFF1E1F29)
    ) {
        override fun colorScheme(isDark: Boolean, coverAccent: Int?): ColorScheme {
            val bg = Color(0xFF1E1F29)
            val surface = Color(0xFF282A36)
            val surfaceVar = Color(0xFF44475A)
            val primary = Color(0xFFBD93F9)
            return darkColorScheme(
                primary = primary,
                onPrimary = Color(0xFF191A21),
                primaryContainer = Color(0xFF49366D),
                onPrimaryContainer = Color(0xFFF8F8F2),
                secondary = Color(0xFFFF79C6),
                onSecondary = Color(0xFF191A21),
                background = bg,
                onBackground = Color(0xFFF8F8F2),
                surface = surface,
                onSurface = Color(0xFFF8F8F2),
                surfaceVariant = surfaceVar,
                onSurfaceVariant = Color(0xFF6272A4),
                outline = Color(0xFF6272A4),
                outlineVariant = Color(0xFF44475A)
            )
        }
    },

    ROSE_PINE(
        id = "rose_pine",
        displayName = "Rosé Pine",
        description = "Soho chic with soft warm rose, gold, and seafoam pastels",
        previewAccent = Color(0xFFEBBCBA),
        previewBackground = Color(0xFF191724)
    ) {
        override fun colorScheme(isDark: Boolean, coverAccent: Int?): ColorScheme {
            val bg = Color(0xFF191724)
            val surface = Color(0xFF1F1D2E)
            val surfaceVar = Color(0xFF26233A)
            val primary = Color(0xFFEBBCBA)
            return darkColorScheme(
                primary = primary,
                onPrimary = Color(0xFF191724),
                primaryContainer = Color(0xFF463A47),
                onPrimaryContainer = Color(0xFFE0DEF4),
                secondary = Color(0xFF9CCFD8),
                onSecondary = Color(0xFF191724),
                background = bg,
                onBackground = Color(0xFFE0DEF4),
                surface = surface,
                onSurface = Color(0xFFE0DEF4),
                surfaceVariant = surfaceVar,
                onSurfaceVariant = Color(0xFF908CAA),
                outline = Color(0xFF524F67),
                outlineVariant = Color(0xFF393552)
            )
        }
    },

    SYNTHWAVE(
        id = "synthwave",
        displayName = "Retro Synthwave",
        description = "80s outrun neon: radioactive magenta, deep purple, and cyber cyan",
        previewAccent = Color(0xFFFF71CE),
        previewBackground = Color(0xFF140B24)
    ) {
        override fun colorScheme(isDark: Boolean, coverAccent: Int?): ColorScheme {
            val bg = Color(0xFF140B24)
            val surface = Color(0xFF1F1138)
            val surfaceVar = Color(0xFF2E1A52)
            val primary = Color(0xFFFF71CE)
            return darkColorScheme(
                primary = primary,
                onPrimary = Color.Black,
                primaryContainer = Color(0xFF5A1C49),
                onPrimaryContainer = Color(0xFFFFD3F2),
                secondary = Color(0xFF01CDFE),
                onSecondary = Color.Black,
                background = bg,
                onBackground = Color(0xFFFEE7F9),
                surface = surface,
                onSurface = Color(0xFFFEE7F9),
                surfaceVariant = surfaceVar,
                onSurfaceVariant = Color(0xFFB9A6D9),
                outline = Color(0xFF6B3E9C),
                outlineVariant = Color(0xFF46276B)
            )
        }
    },

    CATPPUCCIN(
        id = "catppuccin",
        displayName = "Catppuccin Mocha",
        description = "Warm soothing pastel palette with lavender and mauve accents",
        previewAccent = Color(0xFFCBA6F7),
        previewBackground = Color(0xFF181825)
    ) {
        override fun colorScheme(isDark: Boolean, coverAccent: Int?): ColorScheme {
            val bg = Color(0xFF181825)
            val surface = Color(0xFF1E1E2E)
            val surfaceVar = Color(0xFF313244)
            val primary = Color(0xFFCBA6F7)
            return darkColorScheme(
                primary = primary,
                onPrimary = Color(0xFF11111B),
                primaryContainer = Color(0xFF473663),
                onPrimaryContainer = Color(0xFFCDD6F4),
                secondary = Color(0xFFF5C2E7),
                onSecondary = Color(0xFF11111B),
                background = bg,
                onBackground = Color(0xFFCDD6F4),
                surface = surface,
                onSurface = Color(0xFFCDD6F4),
                surfaceVariant = surfaceVar,
                onSurfaceVariant = Color(0xFFA6ADC8),
                outline = Color(0xFF585B70),
                outlineVariant = Color(0xFF45475A)
            )
        }
    },

    LIGHT_MINIMAL(
        id = "light_minimal",
        displayName = "Clean Studio Light",
        description = "Crisp, airy daytime workspace with pure white surfaces and modern cobalt blue",
        previewAccent = Color(0xFF1A73E8),
        previewBackground = Color(0xFFF8F9FA)
    ) {
        override fun colorScheme(isDark: Boolean, coverAccent: Int?): ColorScheme {
            val bg = Color(0xFFF8F9FA)
            val surface = Color(0xFFFFFFFF)
            val surfaceVar = Color(0xFFECEFF1)
            val primary = Color(0xFF1A73E8)
            return lightColorScheme(
                primary = primary,
                onPrimary = Color.White,
                primaryContainer = Color(0xFFD3E3FD),
                onPrimaryContainer = Color(0xFF041E49),
                secondary = Color(0xFF185ABC),
                onSecondary = Color.White,
                background = bg,
                onBackground = Color(0xFF202124),
                surface = surface,
                onSurface = Color(0xFF202124),
                surfaceVariant = surfaceVar,
                onSurfaceVariant = Color(0xFF5F6368),
                outline = Color(0xFFBDC1C6),
                outlineVariant = Color(0xFFDADCE0)
            )
        }
    };

    abstract fun colorScheme(isDark: Boolean, coverAccent: Int?): ColorScheme

    companion object {
        fun fromId(id: String): ThemePreset {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DYNAMIC
        }
    }
}
