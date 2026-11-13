package com.lostf1sh.pixelplayeross.desktop.library

import com.lostf1sh.pixelplayeross.desktop.metadata.ArtworkStore
import com.lostf1sh.pixelplayeross.desktop.metadata.TrackMetadataReader
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.Locale

/** Scans local files and reads tags/artwork on the caller's IO worker. */
class FolderScanner {
    private val extensions = setOf("mp3", "flac", "wav", "ogg", "opus", "m4a", "aac", "aiff", "aif", "wma")

    fun scan(folder: Path, checkCancellation: () -> Unit = {}): ScanResult {
        require(Files.isDirectory(folder) && Files.isReadable(folder)) { "The selected folder is not readable." }
        val songs = mutableListOf<DesktopSong>()
        val metadata = TrackMetadataReader(ArtworkStore.defaultStore())
        var unreadable = 0
        Files.walkFileTree(folder, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                checkCancellation()
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                checkCancellation()
                val name = file.fileName.toString()
                val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                if (attrs.isRegularFile && extension in extensions) {
                    val path = file.toAbsolutePath().normalize().toString()
                    val tags = metadata.read(file)
                    checkCancellation()
                    songs += DesktopSong(path, tags.title.ifBlank { name.substringBeforeLast('.') },
                        path, extension.uppercase(Locale.ROOT), attrs.size(), tags.artist, tags.album,
                        tags.durationMs, tags.cover?.path, tags.cover?.accent, tags.albumArtist,
                        tags.discNumber, tags.trackNumber)
                }
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                checkCancellation()
                if (file == folder) throw exc
                unreadable++
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                checkCancellation()
                if (exc != null) {
                    if (dir == folder) throw exc
                    unreadable++
                }
                return FileVisitResult.CONTINUE
            }
        })
        checkCancellation()
        return ScanResult(songs.sortedWith(compareBy<DesktopSong> { it.discNumber.takeIf { n -> n > 0 } ?: Int.MAX_VALUE }
            .thenBy { it.trackNumber.takeIf { n -> n > 0 } ?: Int.MAX_VALUE }
            .thenBy { it.title.lowercase(Locale.ROOT) }.thenBy { it.path }), unreadable)
    }
}
