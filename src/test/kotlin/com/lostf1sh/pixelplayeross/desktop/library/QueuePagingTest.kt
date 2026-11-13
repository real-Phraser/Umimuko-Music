package com.lostf1sh.pixelplayeross.desktop.library
import kotlin.test.Test
import com.lostf1sh.pixelplayeross.desktop.playback.QueuePagingChecks
class QueuePagingTest {
    @Test fun coversFullQueueInBoundedPages(){ QueuePagingChecks.runChecks() }
}
