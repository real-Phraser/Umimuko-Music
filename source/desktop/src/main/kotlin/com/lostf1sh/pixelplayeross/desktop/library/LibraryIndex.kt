package com.lostf1sh.pixelplayeross.desktop.library

import java.nio.file.Path
import java.util.Locale

/** "1 track" / "2 tracks". */
fun countLabel(count: Int, singular: String): String = if (count == 1) "1 $singular" else "$count ${singular}s"

data class MusicGroup(val id: String, val title: String, val subtitle: String, val songs: List<DesktopSong>)

object LibraryIndex {
    fun artists(songs: List<DesktopSong>): List<MusicGroup> {
        val names = linkedMapOf<String,String>()
        val groups = linkedMapOf<String,MutableList<DesktopSong>>()
        for (song in songs) {
            val artists = ArtistNames.split(song.artist).ifEmpty { listOf("Unknown artist") }
            for (artist in artists) {
                val id = ArtistNames.key(artist)
                names.putIfAbsent(id, artist)
                groups.getOrPut(id) { mutableListOf() }.add(song)
            }
        }
        return groups.map { (id,tracks) -> MusicGroup(id,names.getValue(id),countLabel(tracks.size,"track"),tracks) }
            .sortedBy { it.title.lowercase(Locale.ROOT) }
    }
    fun albums(songs: List<DesktopSong>): List<MusicGroup> = songs.groupBy { song ->
        // Album-artist tag joins multi-disc folders; folder fallback avoids merging unrelated same-title albums.
        val owner = song.albumArtist.ifBlank { Path.of(song.path).parent?.toString().orEmpty() }
        ArtistNames.key(song.album) + "\u0000" + ArtistNames.key(owner)
    }.map { (id,tracks) ->
        val first=tracks.first()
        val artists=tracks.flatMap { ArtistNames.split(it.artist) }.distinctBy { ArtistNames.key(it) }
        MusicGroup(id,first.album.ifBlank { "Unknown album" },
            first.albumArtist.ifBlank { if(artists.size==1) artists.first() else "Various artists" },tracks)
        }.map { group -> group.copy(songs=group.songs.sortedWith(compareBy<DesktopSong> { it.discNumber.takeIf { n -> n > 0 } ?: Int.MAX_VALUE }
            .thenBy { it.trackNumber.takeIf { n -> n > 0 } ?: Int.MAX_VALUE }.thenBy { it.title.lowercase(Locale.ROOT) })) }
        .sortedBy { it.title.lowercase(Locale.ROOT) }
}
