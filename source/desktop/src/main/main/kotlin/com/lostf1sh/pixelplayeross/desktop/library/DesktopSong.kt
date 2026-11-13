package com.lostf1sh.pixelplayeross.desktop.library

/** Desktop track model with read-only local metadata and cached artwork. */
data class DesktopSong(
    val id: String,
    val title: String,
    val path: String,
    val format: String,
    val sizeBytes: Long,
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0,
    val coverPath: String? = null,
    val accentArgb: Int? = null,
    val albumArtist: String = "",
    val discNumber: Int = 0,
    val trackNumber: Int = 0,
)

data class ScanResult(val songs: List<DesktopSong>, val unreadableEntries: Int)
