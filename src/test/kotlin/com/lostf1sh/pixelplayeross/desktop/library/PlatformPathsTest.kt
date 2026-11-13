package com.lostf1sh.pixelplayeross.desktop.library

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlatformPathsTest {
    @Test
    fun testPlatformPathsResolution() {
        val dataDir = PlatformPaths.getDataDirectory()
        val cacheDir = PlatformPaths.getCacheDirectory()
        assertNotNull(dataDir)
        assertNotNull(cacheDir)
        assertTrue(dataDir.toString().isNotBlank())
        assertTrue(cacheDir.toString().isNotBlank())
    }
}
