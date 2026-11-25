import { open as openFileDialog } from "@tauri-apps/plugin-dialog";
import { useMemo, useState } from "react";
import { useLibraryStore, type SortField } from "../store/useLibraryStore";
import { usePlayerStore } from "../store/usePlayerStore";
import { useSettingsStore } from "../store/useSettingsStore";
import type { Track } from "../types/ipc";
import { Artwork } from "./Artwork";
import { Icon } from "./Icon";
import { TrackRow } from "./TrackRow";

const SORT_OPTIONS: Array<{ field: SortField; label: string }> = [
  { field: "title", label: "Title" },
  { field: "artist", label: "Artist" },
  { field: "album", label: "Album" },
  { field: "addedAt", label: "Added" },
  { field: "durationMs", label: "Duration" },
];

function parentDir(path: string): string {
  const normalized = path.replace(/\\/g, "/");
  const idx = normalized.lastIndexOf("/");
  return idx > 0 ? normalized.slice(0, idx) : "/";
}

export function LibraryView() {
  const activeNav = useLibraryStore((s) => s.activeNav);
  const libraryTab = useLibraryStore((s) => s.libraryTab);
  const tracks = useLibraryStore((s) => s.tracks);
  const playlists = useLibraryStore((s) => s.playlists);
  const selectedPlaylistId = useLibraryStore((s) => s.selectedPlaylistId);
  const searchQuery = useLibraryStore((s) => s.searchQuery);
  const sortBy = useLibraryStore((s) => s.sortBy);
  const sortDir = useLibraryStore((s) => s.sortDir);
  const scanProgress = useLibraryStore((s) => s.scanProgress);

  const setActiveNav = useLibraryStore((s) => s.setActiveNav);
  const setSelectedPlaylistId = useLibraryStore((s) => s.setSelectedPlaylistId);
  const setSearchQuery = useLibraryStore((s) => s.setSearchQuery);
  const setSort = useLibraryStore((s) => s.setSort);
  const createPlaylist = useLibraryStore((s) => s.createPlaylist);
  const deletePlaylist = useLibraryStore((s) => s.deletePlaylist);
  const openDialog = useLibraryStore((s) => s.openDialog);
  const scanFolders = useLibraryStore((s) => s.scanFolders);
  const addFolder = useSettingsStore((s) => s.addFolder);
  const playTrack = usePlayerStore((s) => s.playTrack);

  const [searchOpen, setSearchOpen] = useState(activeNav === "search");
  const [sortMenuOpen, setSortMenuOpen] = useState(false);
  const [newPlaylistName, setNewPlaylistName] = useState("");
  const [selectedFilter, setSelectedFilter] = useState<{
    kind: "album" | "artist" | "folder";
    value: string;
  } | null>(null);

  const filteredAndSortedTracks = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    let list = tracks.filter((t) => {
      if (activeNav === "liked" && !t.liked) return false;
      if (selectedFilter) {
        if (selectedFilter.kind === "album" && t.album !== selectedFilter.value) {
          return false;
        }
        if (selectedFilter.kind === "artist" && t.artist !== selectedFilter.value) {
          return false;
        }
        if (
          selectedFilter.kind === "folder" &&
          parentDir(t.path) !== selectedFilter.value
        ) {
          return false;
        }
      }
      if (!q) return true;
      return (
        t.title.toLowerCase().includes(q) ||
        t.artist.toLowerCase().includes(q) ||
        t.album.toLowerCase().includes(q) ||
        t.genre.toLowerCase().includes(q)
      );
    });

    list = [...list].sort((a, b) => {
      let cmp = 0;
      if (sortBy === "addedAt" || sortBy === "durationMs") {
        cmp = a[sortBy] - b[sortBy];
      } else {
        cmp = a[sortBy].localeCompare(b[sortBy]);
      }
      return sortDir === "asc" ? cmp : -cmp;
    });
    return list;
  }, [tracks, activeNav, selectedFilter, searchQuery, sortBy, sortDir]);

  const albums = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    const map = new Map<
      string,
      {
        album: string;
        artist: string;
        artworkPath: string | null;
        accentColor: string | null;
        tracks: Track[];
      }
    >();
    for (const t of tracks) {
      const key = `${t.album}__${t.albumArtist || t.artist}`;
      const existing = map.get(key);
      if (existing) {
        existing.tracks.push(t);
      } else {
        map.set(key, {
          album: t.album || "Unknown Album",
          artist: t.albumArtist || t.artist,
          artworkPath: t.artworkPath,
          accentColor: t.accentColor,
          tracks: [t],
        });
      }
    }
    return [...map.values()]
      .filter(
        (a) =>
          !q ||
          a.album.toLowerCase().includes(q) ||
          a.artist.toLowerCase().includes(q)
      )
      .sort((a, b) => a.album.localeCompare(b.album));
  }, [tracks, searchQuery]);

  const artists = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    const map = new Map<string, Track[]>();
    for (const t of tracks) {
      const name = t.artist || "Unknown Artist";
      const list = map.get(name) ?? [];
      list.push(t);
      map.set(name, list);
    }
    return [...map.entries()]
      .map(([artist, items]) => ({ artist, tracks: items }))
      .filter((a) => !q || a.artist.toLowerCase().includes(q))
      .sort((a, b) => a.artist.localeCompare(b.artist));
  }, [tracks, searchQuery]);

  const folders = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    const map = new Map<string, Track[]>();
    for (const t of tracks) {
      const dir = parentDir(t.path);
      const list = map.get(dir) ?? [];
      list.push(t);
      map.set(dir, list);
    }
    return [...map.entries()]
      .map(([dir, items]) => ({ dir, tracks: items }))
      .filter((f) => !q || f.dir.toLowerCase().includes(q))
      .sort((a, b) => a.dir.localeCompare(b.dir));
  }, [tracks, searchQuery]);

  const activePlaylist = useMemo(
    () => playlists.find((p) => p.id === selectedPlaylistId) ?? null,
    [playlists, selectedPlaylistId]
  );

  const handleAddFolder = async () => {
    try {
      const selected = await openFileDialog({
        directory: true,
        multiple: false,
      });
      if (typeof selected === "string" && selected.trim()) {
        await addFolder(selected);
        await scanFolders([selected]);
      }
    } catch {
      // ignore cancelled dialog
    }
  };

  const handleCreatePlaylist = async (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = newPlaylistName.trim();
    if (!trimmed) return;
    const pl = await createPlaylist(trimmed);
    setNewPlaylistName("");
    setSelectedPlaylistId(pl.id);
  };

  const handleBack = () => {
    if (selectedFilter) {
      setSelectedFilter(null);
      return;
    }
    if (selectedPlaylistId) {
      setSelectedPlaylistId(null);
      return;
    }
    setActiveNav("home");
  };

  // Header title & subtitle matching v0.26.1
  let title = "Tracks";
  let subtitle = "All songs in your library.";
  if (activeNav === "liked") {
    title = "Liked songs";
    subtitle = "Your favorites, kept for next time.";
  } else if (activeNav === "search") {
    title = "Search";
    subtitle = "Find tracks across your library.";
  } else if (selectedFilter) {
    title = selectedFilter.value;
    subtitle = `${filteredAndSortedTracks.length} tracks`;
  } else if (activePlaylist) {
    title = activePlaylist.name;
    subtitle = `${activePlaylist.trackIds.length} tracks`;
  } else if (libraryTab === "albums") {
    title = "Albums";
    subtitle = `${albums.length} albums`;
  } else if (libraryTab === "artists") {
    title = "Artists";
    subtitle = `${artists.length} artists`;
  } else if (libraryTab === "playlists") {
    title = "Playlists";
    subtitle = "Your custom mixes and collections.";
  } else if (libraryTab === "folders") {
    title = "Folders";
    subtitle = "Music directories on this device.";
  }

  const showSongsList =
    activeNav === "liked" ||
    activeNav === "search" ||
    libraryTab === "songs" ||
    selectedFilter !== null;

  const activeSortLabel =
    SORT_OPTIONS.find((o) => o.field === sortBy)?.label ?? "Title";

  return (
    <div className="space-y-4">
      {/* Content Header: <- + Title/Subtitle on left; circular Search + pill Add folder on right */}
      <div className="flex items-center justify-between gap-4">
        <div className="flex items-center gap-3 min-w-0">
          <button
            type="button"
            onClick={handleBack}
            aria-label="Back"
            className="w-9 h-9 rounded-full flex items-center justify-center shrink-0 transition-opacity hover:opacity-80 cursor-pointer"
            style={{ color: "var(--text-primary)" }}
          >
            <Icon name="back" size={20} />
          </button>
          <div className="min-w-0">
            <h1 className="text-2xl font-semibold tracking-tight truncate">
              {title}
            </h1>
            <p
              className="text-xs truncate mt-0.5"
              style={{ color: "var(--text-secondary)" }}
            >
              {subtitle}
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2 shrink-0">
          <button
            type="button"
            onClick={() => {
              if (searchOpen) setSearchQuery("");
              setSearchOpen((v) => !v);
            }}
            aria-label={searchOpen ? "Close search" : "Open search"}
            className="w-12 h-10 rounded-full flex items-center justify-center transition-colors hover:opacity-90 cursor-pointer"
            style={{
              backgroundColor: "var(--surface-2)",
              color: "var(--text-primary)",
            }}
          >
            <Icon name="search" size={19} />
          </button>

          <button
            type="button"
            disabled={scanProgress.scanning}
            onClick={() => void handleAddFolder()}
            className="h-10 px-4 rounded-full text-sm font-medium flex items-center gap-2 transition-opacity hover:opacity-90 disabled:opacity-50 cursor-pointer"
            style={{
              backgroundColor: "var(--accent)",
              color: "var(--on-accent)",
            }}
          >
            <Icon name="folder" size={18} />
            <span>Add folder</span>
          </button>
        </div>
      </div>

      {/* Expandable Search Bar */}
      {searchOpen && (
        <div
          className="flex items-center gap-2.5 px-4 py-2.5 rounded-full"
          style={{
            backgroundColor: "var(--surface-1)",
            border: "1px solid var(--outline-strong)",
          }}
        >
          <Icon name="search" size={18} style={{ color: "var(--text-muted)" }} />
          <input
            type="text"
            autoFocus
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder={`Search ${title}`}
            className="bg-transparent text-sm outline-none flex-1 placeholder:text-[var(--text-muted)]"
          />
          {searchQuery && (
            <button
              type="button"
              onClick={() => setSearchQuery("")}
              className="cursor-pointer"
              style={{ color: "var(--text-muted)" }}
            >
              <Icon name="close" size={16} />
            </button>
          )}
        </div>
      )}

      {/* Songs / Liked / Search / Filtered View */}
      {showSongsList && (
        <div className="space-y-3">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div className="text-sm font-semibold">
              {filteredAndSortedTracks.length}{" "}
              {filteredAndSortedTracks.length === 1 ? "track" : "tracks"}
            </div>

            <div className="flex items-center gap-2">
              {/* Sort Dropdown */}
              <div className="relative">
                <button
                  type="button"
                  onClick={() => setSortMenuOpen((v) => !v)}
                  className="h-9 px-3.5 rounded-full text-xs font-medium flex items-center gap-1.5 cursor-pointer"
                  style={{
                    backgroundColor: "transparent",
                    border: "1px solid var(--outline-strong)",
                    color: "var(--text-primary)",
                  }}
                >
                  <span>
                    Sort: {activeSortLabel} ({sortDir === "asc" ? "↑" : "↓"})
                  </span>
                </button>
                {sortMenuOpen && (
                  <>
                    <div
                      className="fixed inset-0 z-30"
                      onClick={() => setSortMenuOpen(false)}
                    />
                    <div
                      className="absolute right-0 top-10 z-40 w-40 py-1.5 rounded-2xl shadow-xl text-xs"
                      style={{
                        backgroundColor: "var(--surface-3)",
                        border: "1px solid var(--outline)",
                      }}
                    >
                      {SORT_OPTIONS.map((opt) => (
                        <button
                          key={opt.field}
                          type="button"
                          onClick={() => {
                            setSort(opt.field);
                            setSortMenuOpen(false);
                          }}
                          className="w-full px-3.5 py-2 text-left flex items-center justify-between hover:opacity-80 cursor-pointer"
                        >
                          <span>{opt.label}</span>
                          {sortBy === opt.field && (
                            <span>{sortDir === "asc" ? "↑" : "↓"}</span>
                          )}
                        </button>
                      ))}
                    </div>
                  </>
                )}
              </div>

              {activeNav === "library" &&
                libraryTab === "songs" &&
                !selectedFilter && (
                  <button
                    type="button"
                    onClick={() => openDialog("duplicates")}
                    className="h-9 px-3.5 rounded-full text-xs font-medium flex items-center gap-1.5 cursor-pointer"
                    style={{
                      backgroundColor: "transparent",
                      border: "1px solid var(--outline-strong)",
                      color: "var(--text-primary)",
                    }}
                  >
                    <Icon name="search" size={14} />
                    <span>Duplicates</span>
                  </button>
                )}

              {filteredAndSortedTracks.length > 0 && (
                <button
                  type="button"
                  onClick={() =>
                    void playTrack(
                      filteredAndSortedTracks[0].id,
                      filteredAndSortedTracks.map((t) => t.id)
                    )
                  }
                  className="h-9 px-4 rounded-full text-xs font-medium flex items-center gap-1.5 cursor-pointer"
                  style={{
                    backgroundColor: "var(--surface-2)",
                    color: "var(--text-primary)",
                  }}
                >
                  <Icon name="play" size={16} />
                  <span>Play all</span>
                </button>
              )}
            </div>
          </div>

          <div className="space-y-2">
            {filteredAndSortedTracks.length === 0 ? (
              <div
                className="py-16 rounded-3xl text-center text-sm"
                style={{
                  backgroundColor: "var(--surface-1)",
                  color: "var(--text-muted)",
                }}
              >
                No matching tracks found.
              </div>
            ) : (
              filteredAndSortedTracks.map((track, idx) => (
                <TrackRow
                  key={track.id}
                  track={track}
                  index={idx}
                  queueIds={filteredAndSortedTracks.map((t) => t.id)}
                />
              ))
            )}
          </div>
        </div>
      )}

      {/* Albums Grid */}
      {activeNav === "library" && !selectedFilter && libraryTab === "albums" && (
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-3.5">
          {albums.map((alb) => (
            <button
              key={`${alb.album}-${alb.artist}`}
              type="button"
              onClick={() => setSelectedFilter({ kind: "album", value: alb.album })}
              className="group text-left p-3 rounded-[20px] transition-colors hover:opacity-95 cursor-pointer"
              style={{ backgroundColor: "var(--surface-1)" }}
            >
              <div className="aspect-square w-full mb-2.5">
                <Artwork
                  artworkPath={alb.artworkPath}
                  accentColor={alb.accentColor}
                  alt={alb.album}
                  size="full"
                  rounded="rounded-xl"
                />
              </div>
              <div className="text-sm font-semibold truncate">{alb.album}</div>
              <div
                className="text-xs truncate mt-0.5"
                style={{ color: "var(--text-secondary)" }}
              >
                {alb.artist} • {alb.tracks.length} tracks
              </div>
            </button>
          ))}
        </div>
      )}

      {/* Artists Grid */}
      {activeNav === "library" && !selectedFilter && libraryTab === "artists" && (
        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-3">
          {artists.map((art) => (
            <button
              key={art.artist}
              type="button"
              onClick={() => setSelectedFilter({ kind: "artist", value: art.artist })}
              className="flex items-center gap-3.5 p-3.5 rounded-2xl text-left transition-colors hover:opacity-90 cursor-pointer"
              style={{ backgroundColor: "var(--surface-1)" }}
            >
              <div
                className="w-12 h-12 rounded-full flex items-center justify-center shrink-0"
                style={{
                  backgroundColor: "var(--accent-container)",
                  color: "var(--accent)",
                }}
              >
                <Icon name="artist" size={22} />
              </div>
              <div className="min-w-0">
                <div className="text-sm font-semibold truncate">{art.artist}</div>
                <div className="text-xs" style={{ color: "var(--text-secondary)" }}>
                  {art.tracks.length} tracks
                </div>
              </div>
            </button>
          ))}
        </div>
      )}

      {/* Playlists */}
      {activeNav === "library" && !selectedFilter && libraryTab === "playlists" && (
        <div className="space-y-4">
          {activePlaylist ? (
            <div className="space-y-3">
              <div className="flex items-center justify-end gap-2">
                {activePlaylist.trackIds.length > 0 && (
                  <button
                    type="button"
                    onClick={() =>
                      void playTrack(
                        activePlaylist.trackIds[0],
                        activePlaylist.trackIds
                      )
                    }
                    className="h-9 px-4 rounded-full text-xs font-medium flex items-center gap-1.5 cursor-pointer"
                    style={{
                      backgroundColor: "var(--surface-2)",
                      color: "var(--text-primary)",
                    }}
                  >
                    <Icon name="play" size={16} />
                    <span>Play all</span>
                  </button>
                )}
                <button
                  type="button"
                  onClick={() => void deletePlaylist(activePlaylist.id)}
                  className="h-9 px-4 rounded-full text-xs font-medium cursor-pointer"
                  style={{
                    backgroundColor: "var(--danger-container)",
                    color: "var(--danger)",
                  }}
                >
                  Delete
                </button>
              </div>

              <div className="space-y-2">
                {activePlaylist.trackIds
                  .map((id) => tracks.find((t) => t.id === id))
                  .filter((t): t is Track => Boolean(t))
                  .map((track, idx) => (
                    <TrackRow
                      key={track.id}
                      track={track}
                      index={idx}
                      queueIds={activePlaylist.trackIds}
                    />
                  ))}
              </div>
            </div>
          ) : (
            <>
              <form onSubmit={handleCreatePlaylist} className="flex items-center gap-2">
                <input
                  type="text"
                  value={newPlaylistName}
                  onChange={(e) => setNewPlaylistName(e.target.value)}
                  placeholder="New playlist name…"
                  className="px-4 py-2 rounded-full text-sm outline-none w-64"
                  style={{
                    backgroundColor: "var(--surface-1)",
                    border: "1px solid var(--outline)",
                  }}
                />
                <button
                  type="submit"
                  className="px-4 py-2 rounded-full text-sm font-medium flex items-center gap-1.5 cursor-pointer"
                  style={{
                    backgroundColor: "var(--surface-2)",
                    color: "var(--text-primary)",
                  }}
                >
                  <Icon name="add" size={17} />
                  <span>New playlist</span>
                </button>
              </form>

              <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-3">
                {playlists.map((pl) => (
                  <button
                    key={pl.id}
                    type="button"
                    onClick={() => setSelectedPlaylistId(pl.id)}
                    className="flex items-center gap-3.5 p-3.5 rounded-2xl text-left transition-colors hover:opacity-90 cursor-pointer"
                    style={{ backgroundColor: "var(--surface-1)" }}
                  >
                    <div
                      className="w-12 h-12 rounded-xl flex items-center justify-center shrink-0"
                      style={{
                        backgroundColor: "var(--accent-container)",
                        color: "var(--accent)",
                      }}
                    >
                      <Icon name="playlist" size={22} />
                    </div>
                    <div className="min-w-0">
                      <div className="text-sm font-semibold truncate">{pl.name}</div>
                      <div
                        className="text-xs"
                        style={{ color: "var(--text-secondary)" }}
                      >
                        {pl.trackIds.length} tracks
                      </div>
                    </div>
                  </button>
                ))}
              </div>
            </>
          )}
        </div>
      )}

      {/* Folders */}
      {activeNav === "library" && !selectedFilter && libraryTab === "folders" && (
        <div className="space-y-2">
          {folders.map((folder) => (
            <button
              key={folder.dir}
              type="button"
              onClick={() => setSelectedFilter({ kind: "folder", value: folder.dir })}
              className="w-full flex items-center justify-between p-3.5 rounded-2xl text-left transition-colors hover:opacity-90 cursor-pointer"
              style={{ backgroundColor: "var(--surface-1)" }}
            >
              <div className="flex items-center gap-3 min-w-0">
                <Icon
                  name="folder"
                  size={20}
                  style={{ color: "var(--accent)" }}
                />
                <span className="text-sm font-medium truncate">{folder.dir}</span>
              </div>
              <span
                className="text-xs shrink-0 ml-4"
                style={{ color: "var(--text-secondary)" }}
              >
                {folder.tracks.length} tracks
              </span>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
