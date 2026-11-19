package com.lostf1sh.pixelplayeross.desktop.lyrics

import kotlin.test.*

class LrcParserTest {

    @Test
    fun parsesStandardLrcWithCentiseconds() {
        val lrc = """
            [ti:Test Title]
            [ar:Test Artist]
            [00:04.50]Line one begins
            [00:12.80]Line two continues
            [01:05.00]One minute five seconds
        """.trimIndent()

        val parsed = LrcParser.parse(lrc)
        assertTrue(parsed.isSynced)
        assertEquals(3, parsed.lines.size)

        assertEquals(4500L, parsed.lines[0].timestampMs)
        assertEquals("Line one begins", parsed.lines[0].text)

        assertEquals(12800L, parsed.lines[1].timestampMs)
        assertEquals("Line two continues", parsed.lines[1].text)

        assertEquals(65000L, parsed.lines[2].timestampMs)
        assertEquals("One minute five seconds", parsed.lines[2].text)
    }

    @Test
    fun parsesThreeDigitMilliseconds() {
        val lrc = """
            [00:02.123]High precision timestamp
            [00:05.999]Almost six seconds
        """.trimIndent()

        val parsed = LrcParser.parse(lrc)
        assertTrue(parsed.isSynced)
        assertEquals(2, parsed.lines.size)
        assertEquals(2123L, parsed.lines[0].timestampMs)
        assertEquals(5999L, parsed.lines[1].timestampMs)
    }

    @Test
    fun parsesMultiTimestampLinesAndSortsChronologically() {
        val lrc = """
            [00:30.00][00:10.00]Repeated chorus line
            [00:20.00]Verse line
        """.trimIndent()

        val parsed = LrcParser.parse(lrc)
        assertTrue(parsed.isSynced)
        assertEquals(3, parsed.lines.size)

        assertEquals(10000L, parsed.lines[0].timestampMs)
        assertEquals("Repeated chorus line", parsed.lines[0].text)

        assertEquals(20000L, parsed.lines[1].timestampMs)
        assertEquals("Verse line", parsed.lines[1].text)

        assertEquals(30000L, parsed.lines[2].timestampMs)
        assertEquals("Repeated chorus line", parsed.lines[2].text)
    }

    @Test
    fun appliesOffsetTagCorrectly() {
        val lrc = """
            [offset:+500]
            [00:01.00]One second plus offset
        """.trimIndent()

        val parsed = LrcParser.parse(lrc)
        assertTrue(parsed.isSynced)
        assertEquals(1, parsed.lines.size)
        assertEquals(1500L, parsed.lines[0].timestampMs)
    }

    @Test
    fun findsActiveLineIndexAccurately() {
        val lrc = """
            [00:05.00]Line 0
            [00:10.00]Line 1
            [00:20.00]Line 2
            [00:35.00]Line 3
        """.trimIndent()

        val parsed = LrcParser.parse(lrc)

        assertEquals(-1, parsed.findActiveLineIndex(0L))
        assertEquals(-1, parsed.findActiveLineIndex(4999L))
        assertEquals(0, parsed.findActiveLineIndex(5000L))
        assertEquals(0, parsed.findActiveLineIndex(7500L))
        assertEquals(1, parsed.findActiveLineIndex(10000L))
        assertEquals(1, parsed.findActiveLineIndex(19999L))
        assertEquals(2, parsed.findActiveLineIndex(20000L))
        assertEquals(2, parsed.findActiveLineIndex(34999L))
        assertEquals(3, parsed.findActiveLineIndex(35000L))
        assertEquals(3, parsed.findActiveLineIndex(99999L))
    }

    @Test
    fun fallsBackToPlainLyricsWhenNoTimestampsPresent() {
        val plain = """
            First verse line
            Second verse line
            Third verse line
        """.trimIndent()

        val parsed = LrcParser.parse(plain)
        assertFalse(parsed.isSynced)
        assertEquals(3, parsed.lines.size)
        assertEquals("First verse line", parsed.lines[0].text)
    }
}
