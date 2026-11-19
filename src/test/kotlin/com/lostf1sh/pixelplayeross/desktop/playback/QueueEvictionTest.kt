package com.lostf1sh.pixelplayeross.desktop.playback

import com.lostf1sh.pixelplayeross.desktop.library.DesktopSong
import kotlin.test.*

class QueueEvictionTest {

    @Test
    fun testEditableQueueKeyRemoval() {
        val queue = EditableQueue<String>()
        queue.replace(listOf("song-a", "song-b", "song-c", "song-b"), 1, false)

        assertEquals(4, queue.size())
        assertEquals("song-b", queue.current())

        // Find keys for "song-b"
        val keysToRemove = queue.entries().filter { it.value() == "song-b" }.map { it.key() }.toSet()
        assertEquals(2, keysToRemove.size)

        val changed = queue.remove(keysToRemove)
        assertTrue(changed)
        assertEquals(2, queue.size())
        assertEquals(listOf("song-a", "song-c"), queue.tracks())
    }

    @Test
    fun testEvictAllRemainingSongs() {
        val queue = EditableQueue<String>()
        queue.replace(listOf("song-1", "song-2"), 0, false)

        val allKeys = queue.entries().map { it.key() }.toSet()
        queue.remove(allKeys)

        assertEquals(0, queue.size())
        assertNull(queue.current())
    }
}
