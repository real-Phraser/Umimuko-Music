package com.lostf1sh.pixelplayeross.desktop.library
import kotlin.test.Test
import kotlin.test.assertEquals

class LibraryIndexTest {
    private fun track(id:String,artist:String,album:String="Album",albumArtist:String="")=
        DesktopSong(id,id,"/music/$id/song.flac","FLAC",1,artist,album,albumArtist=albumArtist)
    @Test fun collaboratorsAppearInBothArtistPages() {
        val groups=LibraryIndex.artists(listOf(track("1","A / B ، a"),track("2","b")))
        assertEquals(2,groups.size)
        assertEquals(1,groups.first {it.title=="A"}.songs.size)
        assertEquals(2,groups.first {it.title=="B"}.songs.size)
    }
    @Test fun ampersandSeparatesCollaboratorsAndDeduplicatesNames() {
        val groups=LibraryIndex.artists(listOf(track("1","A & B ＆ a")))
        assertEquals(listOf("A","B"),groups.map { it.title })
    }
    @Test fun sameAlbumArtistJoinsDiscsButFallbackSeparatesFolders() {
        assertEquals(1,LibraryIndex.albums(listOf(track("1","A",albumArtist="Various"),track("2","B",albumArtist="Various"))).size)
        assertEquals(2,LibraryIndex.albums(listOf(track("1","A"),track("2","A"))).size)
    }
}
