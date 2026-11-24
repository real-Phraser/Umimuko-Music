package com.lostf1sh.pixelplayeross.desktop.lyrics

import kotlin.test.*

class SpicyLyricsParserTest {

    @Test
    fun parsesSpicyLyricsSyllableJsonWithMetadataAndBackground() {
        val json = """
            {
              "source": "spicylyrics",
              "Body": {
                "Type": "Syllable",
                "StartTime": 1.25,
                "EndTime": 12.5,
                "SongWriters": ["Alice Writer", "Bob Composer"],
                "UploadAttribution": {
                  "Uploader": {
                    "username": "spikerko_fan"
                  }
                },
                "Content": [
                  {
                    "Type": "Vocal",
                    "OppositeAligned": false,
                    "Lead": {
                      "StartTime": 1.25,
                      "EndTime": 5.0,
                      "RomanizedText": "konnichiwa sekai",
                      "Syllables": [
                        {
                          "Text": "Hel",
                          "StartTime": 1.25,
                          "EndTime": 1.80,
                          "IsPartOfWord": true,
                          "RomanizedText": "kon"
                        },
                        {
                          "Text": "lo",
                          "StartTime": 1.80,
                          "EndTime": 2.40,
                          "IsPartOfWord": false,
                          "RomanizedText": "nichiwa"
                        },
                        {
                          "Text": "world",
                          "StartTime": 2.50,
                          "EndTime": 5.0,
                          "IsPartOfWord": false,
                          "RomanizedText": "sekai"
                        }
                      ]
                    },
                    "Background": [
                      {
                        "StartTime": 3.0,
                        "EndTime": 4.5,
                        "Text": "(oh yeah)"
                      }
                    ]
                  },
                  {
                    "Type": "Vocal",
                    "OppositeAligned": true,
                    "Lead": {
                      "StartTime": 6.0,
                      "EndTime": 12.5,
                      "Text": "Duet response line"
                    }
                  }
                ]
              }
            }
        """.trimIndent()

        val parsed = SpicyLyricsParser.parse(json)
        assertEquals(SpicyLyricsType.SYLLABLE, parsed.type)
        assertEquals("spicylyrics", parsed.source)
        assertEquals(1250L, parsed.startTimeMs)
        assertEquals(12500L, parsed.endTimeMs)
        assertEquals(listOf("Alice Writer", "Bob Composer"), parsed.songWriters)
        assertEquals("spikerko_fan", parsed.uploaderName)
        assertEquals(2, parsed.lines.size)

        val first = parsed.lines[0]
        assertEquals(1250L, first.startTimeMs)
        assertEquals(5000L, first.endTimeMs)
        assertEquals("Hello world", first.text)
        assertEquals("konnichiwa sekai", first.romanizedText)
        assertFalse(first.oppositeAligned)
        assertEquals(3, first.syllables.size)
        assertTrue(first.syllables[0].isPartOfWord)
        assertEquals("Hel", first.syllables[0].text)
        assertEquals(1250L, first.syllables[0].startTimeMs)
        assertEquals(1800L, first.syllables[0].endTimeMs)
        assertEquals("kon", first.syllables[0].romanizedText)
        assertEquals(1, first.backgroundVocals.size)
        assertEquals("(oh yeah)", first.backgroundVocals[0].text)
        assertEquals(3000L, first.backgroundVocals[0].startTimeMs)

        val second = parsed.lines[1]
        assertTrue(second.oppositeAligned)
        assertEquals("Duet response line", second.text)
        assertEquals(6000L, second.startTimeMs)
        assertEquals(12500L, second.endTimeMs)
    }

    @Test
    fun parsesSpicyLyricsLineAndStaticJson() {
        val lineJson = """
            {
              "Body": {
                "Type": "Line",
                "StartTime": 2.0,
                "EndTime": 10.0,
                "Content": [
                  { "StartTime": 2.0, "EndTime": 5.5, "Text": "First line" },
                  { "StartTime": 6.0, "EndTime": 10.0, "Text": "Second line" }
                ]
              }
            }
        """.trimIndent()

        val parsedLine = SpicyLyricsParser.parse(lineJson)
        assertEquals(SpicyLyricsType.LINE, parsedLine.type)
        assertEquals(2, parsedLine.lines.size)
        assertEquals(2000L, parsedLine.lines[0].startTimeMs)
        assertEquals(5500L, parsedLine.lines[0].endTimeMs)

        val staticJson = """
            {
              "Body": {
                "Type": "Static",
                "Content": [
                  { "Text": "Static line 1" },
                  { "Text": "Static line 2" }
                ]
              }
            }
        """.trimIndent()

        val parsedStatic = SpicyLyricsParser.parse(staticJson)
        assertEquals(SpicyLyricsType.STATIC, parsedStatic.type)
        assertEquals(2, parsedStatic.lines.size)
        assertEquals(0, parsedStatic.findActiveLineIndex(5000L))
    }

    @Test
    fun parsesTtmlWithSyllablesBackgroundAndDuetAgent() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body>
                <div>
                  <p begin="00:05.000" end="00:09.000" ttm:agent="v1">
                    <span begin="00:05.000" end="00:06.000">Sin</span><span begin="00:06.000" end="00:07.000">ging</span> <span begin="00:07.200" end="00:09.000">loud</span>
                    <span ttm:role="x-roman">uta utau</span>
                    <span ttm:role="x-bg" begin="00:07.500" end="00:08.800">(echo)</span>
                  </p>
                  <p begin="00:10.000" end="00:14.000" ttm:agent="v2">Duet line</p>
                </div>
              </body>
            </tt>
        """.trimIndent()

        val parsed = SpicyLyricsParser.parse(ttml)
        assertEquals(SpicyLyricsType.SYLLABLE, parsed.type)
        assertEquals("TTML", parsed.source)
        assertEquals(2, parsed.lines.size)

        val line0 = parsed.lines[0]
        assertEquals(5000L, line0.startTimeMs)
        assertEquals(9000L, line0.endTimeMs)
        assertEquals("Singing loud", line0.text)
        assertEquals("uta utau", line0.romanizedText)
        assertFalse(line0.oppositeAligned)
        assertEquals(3, line0.syllables.size)
        assertTrue(line0.syllables[0].isPartOfWord)
        assertEquals("Sin", line0.syllables[0].text)
        assertEquals(1, line0.backgroundVocals.size)
        assertEquals("(echo)", line0.backgroundVocals[0].text)

        val line1 = parsed.lines[1]
        assertTrue(line1.oppositeAligned)
        assertEquals("Duet line", line1.text)
    }

    @Test
    fun parsesStandardLrcWithOffsetAndMultiTimestamps() {
        val lrc = """
            [ti:Test Title]
            [ar:Test Artist]
            [offset:+500]
            [00:30.00][00:10.00]Repeated chorus line
            [00:20.123]Verse line
        """.trimIndent()

        val parsed = SpicyLyricsParser.parse(lrc)
        assertEquals(SpicyLyricsType.LINE, parsed.type)
        assertEquals(3, parsed.lines.size)

        assertEquals(10500L, parsed.lines[0].startTimeMs)
        assertEquals("Repeated chorus line", parsed.lines[0].text)

        assertEquals(20623L, parsed.lines[1].startTimeMs)
        assertEquals("Verse line", parsed.lines[1].text)

        assertEquals(30500L, parsed.lines[2].startTimeMs)
        assertEquals("Repeated chorus line", parsed.lines[2].text)

        assertEquals(-1, parsed.findActiveLineIndex(0L))
        assertEquals(0, parsed.findActiveLineIndex(10500L))
        assertEquals(1, parsed.findActiveLineIndex(25000L))
        assertEquals(2, parsed.findActiveLineIndex(99999L))
    }

    @Test
    fun parsesEnhancedLrcAsSyllableType() {
        val enhanced = """
            [00:01.00]<00:01.00>Hello <00:01.50>world
        """.trimIndent()

        val parsed = SpicyLyricsParser.parse(enhanced)
        assertEquals(SpicyLyricsType.SYLLABLE, parsed.type)
        assertEquals(1, parsed.lines.size)
        assertEquals("Hello world", parsed.lines[0].text)
        assertEquals(2, parsed.lines[0].syllables.size)
        assertEquals("Hello", parsed.lines[0].syllables[0].text)
        assertEquals(1000L, parsed.lines[0].syllables[0].startTimeMs)
        assertEquals(1500L, parsed.lines[0].syllables[0].endTimeMs)
    }

    @Test
    fun fallsBackToStaticWhenNoTimestampsPresent() {
        val plain = """
            First verse line
            Second verse line
            Third verse line
        """.trimIndent()

        val parsed = SpicyLyricsParser.parse(plain)
        assertEquals(SpicyLyricsType.STATIC, parsed.type)
        assertEquals(3, parsed.lines.size)
        assertEquals("First verse line", parsed.lines[0].text)
        assertEquals(0, parsed.findActiveLineIndex(12345L))
    }
}
