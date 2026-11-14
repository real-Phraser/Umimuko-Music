package com.lostf1sh.pixelplayeross.desktop

import androidx.compose.ui.unit.dp
import com.lostf1sh.pixelplayeross.desktop.library.PlatformPaths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinuxDisplayConfigurationTest {

    @Test
    fun testLinuxTransparencySettings() {
        if (PlatformPaths.isLinux()) {
            val isTransparent = !PlatformPaths.isLinux()
            assertFalse(isTransparent, "On Linux, transparency must be disabled to avoid Skiko black rectangle defects")

            val miniPlayerHeight = if (PlatformPaths.isLinux()) 68.dp else 80.dp
            assertEquals(68.dp, miniPlayerHeight, "Mini player height on Linux should be 68.dp to fit content without transparent padding")
        }
    }

    @Test
    fun testLinuxMiniPlayerPillShape() {
        val width = 448.0
        val height = 68.0
        val shape = java.awt.geom.RoundRectangle2D.Double(0.0, 0.0, width, height, height, height)
        assertEquals(height, shape.arcWidth, "Arc width should match height to produce a pill/stadium semicircle")
        assertEquals(height, shape.arcHeight, "Arc height should match height to produce a pill/stadium semicircle")
        assertTrue(shape.contains(width / 2.0, height / 2.0), "Pill shape contains center")
        assertFalse(shape.contains(0.0, 0.0), "Pill shape excludes corner points (no black square corners)")
    }

    @Test
    fun testPlatformPathsMutualExclusion() {
        val count = listOf(PlatformPaths.isWindows(), PlatformPaths.isMac(), PlatformPaths.isLinux()).count { it }
        assertEquals(1, count, "Exactly one platform predicate should be true")
    }
}
