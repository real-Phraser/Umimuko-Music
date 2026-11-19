package com.lostf1sh.pixelplayeross.desktop.library

import com.lostf1sh.pixelplayeross.desktop.stats.ListeningStatsStore

enum class SortField(val displayName: String) {
    TITLE("Title"),
    ARTIST("Artist"),
    ALBUM("Album"),
    DATE_ADDED("Date Added"),
    DURATION("Duration"),
    TRACK_NUMBER("Track Number"),
    PLAY_COUNT("Play Count"),
    YEAR("Release Year")
}

enum class SortOrder(val displayName: String) {
    ASCENDING("Ascending"),
    DESCENDING("Descending")
}

data class SortOption(
    val field: SortField = SortField.TITLE,
    val order: SortOrder = SortOrder.ASCENDING
)

object TrackSortingSystem {

    fun sortTracks(
        tracks: List<DesktopSong>,
        option: SortOption,
        statsStore: ListeningStatsStore = ListeningStatsStore.defaultStore()
    ): List<DesktopSong> {
        val comparator = getComparator(option.field, option.order, statsStore)
        return tracks.sortedWith(comparator)
    }

    fun getComparator(
        field: SortField,
        order: SortOrder,
        statsStore: ListeningStatsStore = ListeningStatsStore.defaultStore()
    ): Comparator<DesktopSong> {
        val stringOrder = if (order == SortOrder.DESCENDING) {
            nullsLast(String.CASE_INSENSITIVE_ORDER.reversed())
        } else {
            nullsLast(String.CASE_INSENSITIVE_ORDER)
        }

        return when (field) {
            SortField.TITLE -> compareBy<DesktopSong, String?>(stringOrder) { it.title.ifBlank { null } }

            SortField.ARTIST -> compareBy<DesktopSong, String?>(stringOrder) {
                it.artist.ifBlank { null }
            }.thenBy(nullsLast(String.CASE_INSENSITIVE_ORDER)) { it.album.ifBlank { null } }
                .thenBy { it.discNumber }
                .thenBy { it.trackNumber }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }

            SortField.ALBUM -> compareBy<DesktopSong, String?>(stringOrder) {
                it.album.ifBlank { null }
            }.thenBy { it.discNumber }
                .thenBy { it.trackNumber }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }

            SortField.DATE_ADDED -> if (order == SortOrder.DESCENDING) {
                compareByDescending<DesktopSong> { it.dateAdded }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            } else {
                compareBy<DesktopSong> { it.dateAdded }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            }

            SortField.DURATION -> if (order == SortOrder.DESCENDING) {
                compareByDescending<DesktopSong> { it.durationMs }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            } else {
                compareBy<DesktopSong> { it.durationMs }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            }

            SortField.TRACK_NUMBER -> if (order == SortOrder.DESCENDING) {
                compareByDescending<DesktopSong> { it.discNumber }
                    .thenByDescending { it.trackNumber }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            } else {
                compareBy<DesktopSong> { it.discNumber }
                    .thenBy { it.trackNumber }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            }

            SortField.PLAY_COUNT -> if (order == SortOrder.DESCENDING) {
                compareByDescending<DesktopSong> { statsStore.getPlayCount(it.id) }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            } else {
                compareBy<DesktopSong> { statsStore.getPlayCount(it.id) }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            }

            SortField.YEAR -> {
                val yearComparator: Comparator<Int> = if (order == SortOrder.DESCENDING) {
                    reverseOrder()
                } else {
                    naturalOrder()
                }
                compareBy<DesktopSong, Int?>(nullsLast(yearComparator)) { if (it.year > 0) it.year else null }
                    .thenBy(nullsLast(String.CASE_INSENSITIVE_ORDER)) { it.album.ifBlank { null } }
                    .thenBy { it.trackNumber }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            }
        }
    }
}
