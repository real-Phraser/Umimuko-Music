package com.lostf1sh.pixelplayeross.desktop.metadata

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

data class EditableTags(
    val title: String,
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val year: Int = 0,
    val genre: String = "",
    val trackNumber: Int = 0,
    val discNumber: Int = 0,
    val lyrics: String = ""
)

sealed class TagWriteResult {
    data class Success(val updatedTags: EditableTags) : TagWriteResult()
    data class Error(val message: String, val cause: Throwable? = null) : TagWriteResult()
}

object TrackTagService {

    init {
        java.util.logging.Logger.getLogger("org.jaudiotagger").level = java.util.logging.Level.OFF
    }

    fun readTags(path: Path): EditableTags? {
        val file = path.toFile()
        if (!file.isFile || !file.canRead()) return null
        return try {
            val audio = AudioFileIO.read(file)
            val tag = audio.tag ?: return EditableTags(
                title = file.nameWithoutExtension
            )
            EditableTags(
                title = tag.getFirst(FieldKey.TITLE).trim().ifBlank { file.nameWithoutExtension },
                artist = tag.getFirst(FieldKey.ARTIST).trim(),
                album = tag.getFirst(FieldKey.ALBUM).trim(),
                albumArtist = tag.getFirst(FieldKey.ALBUM_ARTIST).trim(),
                year = parseNumber(tag.getFirst(FieldKey.YEAR)),
                genre = tag.getFirst(FieldKey.GENRE).trim(),
                trackNumber = parseNumber(tag.getFirst(FieldKey.TRACK)),
                discNumber = parseNumber(tag.getFirst(FieldKey.DISC_NO)),
                lyrics = try { tag.getFirst(FieldKey.LYRICS).trim() } catch (_: Throwable) { "" }
            )
        } catch (e: Exception) {
            null
        }
    }

    fun writeTags(path: Path, newTags: EditableTags): TagWriteResult {
        val file = path.toFile()
        if (!file.isFile) return TagWriteResult.Error("Audio file does not exist: $path")
        if (!file.canWrite()) return TagWriteResult.Error("Audio file is not writable: $path")

        // Create safety backup copy in temporary location
        val backup = Files.createTempFile("tag-backup-", ".tmp")
        var backupSucceeded = false
        try {
            Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING)
            backupSucceeded = true

            val audio = AudioFileIO.read(file)
            val tag: Tag = audio.tagOrCreateAndSetDefault

            tag.setField(FieldKey.TITLE, newTags.title.trim())
            tag.setField(FieldKey.ARTIST, newTags.artist.trim())
            tag.setField(FieldKey.ALBUM, newTags.album.trim())
            tag.setField(FieldKey.ALBUM_ARTIST, newTags.albumArtist.trim())
            if (newTags.year > 0) {
                tag.setField(FieldKey.YEAR, newTags.year.toString())
            } else {
                try { tag.deleteField(FieldKey.YEAR) } catch (_: Throwable) {}
            }
            tag.setField(FieldKey.GENRE, newTags.genre.trim())
            if (newTags.trackNumber > 0) {
                tag.setField(FieldKey.TRACK, newTags.trackNumber.toString())
            } else {
                try { tag.deleteField(FieldKey.TRACK) } catch (_: Throwable) {}
            }
            if (newTags.discNumber > 0) {
                tag.setField(FieldKey.DISC_NO, newTags.discNumber.toString())
            } else {
                try { tag.deleteField(FieldKey.DISC_NO) } catch (_: Throwable) {}
            }
            if (newTags.lyrics.isNotBlank()) {
                tag.setField(FieldKey.LYRICS, newTags.lyrics.trim())
            } else {
                try { tag.deleteField(FieldKey.LYRICS) } catch (_: Throwable) {}
            }

            audio.commit()
            return TagWriteResult.Success(newTags)
        } catch (e: Exception) {
            // Restore from backup on failure only if backup succeeded and has content
            if (backupSucceeded && Files.exists(backup) && Files.size(backup) > 0L) {
                runCatching {
                    Files.copy(backup, path, StandardCopyOption.REPLACE_EXISTING)
                }
            }
            return TagWriteResult.Error("Failed updating metadata tags: ${e.message}", e)
        } finally {
            runCatching { Files.deleteIfExists(backup) }
        }
    }

    fun embedArtwork(path: Path, imageBytes: ByteArray, mimeType: String = "image/jpeg"): Boolean {
        val file = path.toFile()
        if (!file.isFile || !file.canWrite() || imageBytes.isEmpty()) return false
        val backup = Files.createTempFile("artwork-backup-", ".tmp")
        var backupSucceeded = false
        return try {
            Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING)
            backupSucceeded = true
            val audio = AudioFileIO.read(file)
            val tag = audio.tagOrCreateAndSetDefault
            val artwork = ArtworkFactory.getNew()
            artwork.binaryData = imageBytes
            artwork.mimeType = mimeType
            artwork.pictureType = 3 // Front Cover
            tag.deleteArtworkField()
            tag.setField(artwork)
            audio.commit()
            true
        } catch (e: Exception) {
            if (backupSucceeded && Files.exists(backup) && Files.size(backup) > 0L) {
                runCatching {
                    Files.copy(backup, path, StandardCopyOption.REPLACE_EXISTING)
                }
            }
            false
        } finally {
            runCatching { Files.deleteIfExists(backup) }
        }
    }

    private fun parseNumber(raw: String?): Int {
        if (raw == null) return 0
        return try {
            raw.trim().split("/", limit = 2)[0].toInt()
        } catch (_: Exception) {
            0
        }
    }
}
