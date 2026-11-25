import { useMemo, useState } from "react";
import {
  useLibraryStore,
  type LibraryTab,
  type SortField,
} from "../store/useLibraryStore";
import { usePlayerStore } from "../store/usePlayerStore";
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

const TABS: Array<{ id: LibraryTab; label: string }> = [
  { id: "songs", label: "Songs" },
  { id: "albums", label: "Albums" },
  { id: "artists", label: "Artists" },
  { id: "playlists", label: "Playlists" },
  { id: "folders", label: "Folders" },
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

  const setLibraryTab = useLibraryStore((s) => s.setLibraryTab);
  const setSelectedPlaylistId = useLibraryStore((s) => s.setSelectedPlaylistId);
  const setSearchQuery = useLibraryStore((s) => s.setSearchQuery);
  const setSort = useLibraryStore((s) => s.setSort);
  const createPlaylist = useLibraryStore((s) => s.createPlaylist);
  const deletePlaylist = useLibraryStore((s) => s.deletePlaylist);
  const playTrack = usePlayerStore((s) => s.playTrack);

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
    const map = new Map<
      string,
      { album: string; artist: string; artworkPath: string | null; accentColor: string | null; tracks: Track[] }
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
    return [...map.values()].sort((a, b) => a.album.localeCompare(b.album));
  }, [tracks]);

  const artists = useMemo(() => {
    const map = new Map<string, Track[]>();
    for (const t of tracks) {
      const name = t.artist || "Unknown Artist";
      const list = map.get(name) ?? [];
      list.push(t);
      map.set(name, list);
    }
    return [...map.entries()]
      .map(([artist, items]) => ({ artist, tracks: items }))
      .sort((a, b) => a.artist.localeCompare(b.artist));
  }, [tracks]);

  const folders = useMemo(() => {
    const map = new Map<string, Track[]>();
    for (const t of tracks) {
      const dir = parentDir(t.path);
      const list = map.get(dir) ?? [];
      list.push(t);
      map.set(dir, list);
    }
    return [...map.entries()]
      .map(([dir, items]) => ({ dir, tracks: items }))
      .sort((a, b) => a.dir.localeCompare(b.dir));
  }, [tracks]);

  const activePlaylist = useMemo(
    () => playlists.find((p) => p.id === selectedPlaylistId) ?? null,
    [playlists, selectedPlaylistId]
  );

  const handleCreatePlaylist = async (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = newPlaylistName.trim();
    if (!trimmed) return;
    const pl = await createPlaylist(trimmed);
    setNewPlaylistName("");
    setSelectedPlaylistId(pl.id);
  };

  const title =
    activeNav === "liked"
      ? "Liked Songs"
      : activeNav === "search"
        ? "Search Library"
        : selectedFilter
          ? selectedFilter.value
          : "Library";

  const showSongsList =
    activeNav === "liked" ||
    activeNav === "search" ||
    libraryTab === "songs" ||
    selectedFilter !== null;

  return (
    <div className="p-6 space-y-5">
      {/* Header & Search */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          {selectedFilter && (
            <button
              type="button"
              onClick={() => setSelectedFilter(null)}
              className="p-2 rounded-full cursor-pointer"
              style={{ backgroundColor: "var(--surface-2)" }}
              title="Back"
            >
              <Icon name="back" size={18} />
            </button>
          )}
          <div>
            <h1 className="text-2xl font-bold tracking-tight truncate">{title}</h1>
            <p className="text-xs mt-0.5" style={{ color: "var(--text-secondary)" }}>
              {filteredAndSortedTracks.length} songs
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2.5">
          <div
            className="flex items-center gap-2 px-3.5 py-2 rounded-full w-full md:w-72"
            style={{
              backgroundColor: "var(--surface-2)",
              border: "1px solid var(--outline)",
            }}
          >
            <Icon name="search" size={17} style={{ color: "var(--text-muted)" }} />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Search title, artist, album…"
              className="bg-transparent text-sm outline-none w-full placeholder:text-[var(--text-muted)]"
            />
            {searchQuery && (
              <button
                type="button"
                onClick={() => setSearchQuery("")}
                className="cursor-pointer"
                style={{ color: "var(--text-muted)" }}
              >
                <Icon name="close" size={15} />
              </button>
            )}
          </div>
        </div>
      </div>

      {/* Tabs when in Library */}
      {activeNav === "library" && !selectedFilter && (
        <div className="flex flex-wrap items-center gap-2">
          {TABS.map((tab) => {
            const active = libraryTab === tab.id;
            return (
              <button
                key={tab.id}
                type="button"
                onClick={() => {
                  setLibraryTab(tab.id);
                  setSelectedPlaylistId(null);
                }}
                className="px-4 py-1.5 rounded-full text-sm font-medium transition-colors cursor-pointer"
                style={{
                  backgroundColor: active ? "var(--accent)" : "var(--surface-2)",
                  color: active ? "var(--on-accent)" : "var(--text-secondary)",
                }}
              >
                {tab.label}
              </button>
            );
          })}
        </div>
      )}

      {/* Songs / Liked / Search / Filtered view */}
      {showSongsList && (
        <div className="space-y-3">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div className="flex items-center gap-1.5">
              {SORT_OPTIONS.map((opt) => {
                const active = sortBy === opt.field;
                return (
                  <button
                    key={opt.field}
                    type="button"
                    onClick={() => setSort(opt.field)}
                    className="px-3 py-1 rounded-full text-xs font-medium flex items-center gap-1 cursor-pointer"
                    style={{
                      backgroundColor: active
                        ? "var(--accent-container)"
                        : "var(--surface-2)",
                      color: active ? "var(--on-accent-container)" : "var(--text-secondary)",
                    }}
                  >
                    <span>{opt.label}</span>
                    {active && (
                      <Icon name={sortDir === "asc" ? "up" : "down"} size={14} />
                    )}
                  </button>
                );
              })}
            </div>

            {filteredAndSortedTracks.length > 0 && (
              <button
                type="button"
                onClick={() =>
                  void playTrack(
                    filteredAndSortedTracks[0].id,
                    filteredAndSortedTracks.map((t) => t.id)
                  )
                }
                className="px-4 py-1.5 rounded-full text-xs font-medium flex items-center gap-1.5 cursor-pointer"
                style={{
                  backgroundColor: "var(--accent)",
                  color: "var(--on-accent)",
                }}
              >
                <Icon name="play" size={16} />
                <span>Play All</span>
              </button>
            )}
          </div>

          <div
            className="rounded-3xl p-2 space-y-0.5"
            style={{
              backgroundColor: "var(--surface-1)",
              border: "1px solid var(--outline)",
            }}
          >
            {filteredAndSortedTracks.length === 0 ? (
              <div
                className="py-12 text-center text-sm"
                style={{ color: "var(--text-muted)" }}
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
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
          {albums.map((alb) => (
            <button
              key={`${alb.album}-${alb.artist}`}
              type="button"
              onClick={() => setSelectedFilter({ kind: "album", value: alb.album })}
              className="group text-left p-3 rounded-2xl transition-transform hover:scale-[1.015] cursor-pointer"
              style={{
                backgroundColor: "var(--surface-2)",
                border: "1px solid var(--outline)",
              }}
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
              <div className="text-sm font-medium truncate">{alb.album}</div>
              <div className="text-xs truncate" style={{ color: "var(--text-secondary)" }}>
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
              style={{
                backgroundColor: "var(--surface-2)",
                border: "1px solid var(--outline)",
              }}
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
                  {art.tracks.length} songs
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
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <button
                    type="button"
                    onClick={() => setSelectedPlaylistId(null)}
                    className="p-2 rounded-full cursor-pointer"
                    style={{ backgroundColor: "var(--surface-2)" }}
                  >
                    <Icon name="back" size={18} />
                  </button>
                  <div>
                    <h2 className="text-lg font-semibold">{activePlaylist.name}</h2>
                    <div className="text-xs" style={{ color: "var(--text-secondary)" }}>
                      {activePlaylist.trackIds.length} tracks
                    </div>
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  {activePlaylist.trackIds.length > 0 && (
                    <button
                      type="button"
                      onClick={() =>
                        void playTrack(
                          activePlaylist.trackIds[0],
                          activePlaylist.trackIds
                        )
                      }
                      className="px-4 py-2 rounded-full text-xs font-medium flex items-center gap-1.5 cursor-pointer"
                      style={{
                        backgroundColor: "var(--accent)",
                        color: "var(--on-accent)",
                      }}
                    >
                      <Icon name="play" size={16} />
                      <span>Play</span>
                    </button>
                  )}
                  <button
                    type="button"
                    onClick={() => void deletePlaylist(activePlaylist.id)}
                    className="px-3 py-2 rounded-full text-xs font-medium cursor-pointer"
                    style={{
                      backgroundColor: "var(--danger-container)",
                      color: "var(--danger)",
                    }}
                  >
                    Delete
                  </button>
                </div>
              </div>

              <div
                className="rounded-3xl p-2 space-y-0.5"
                style={{
                  backgroundColor: "var(--surface-1)",
                  border: "1px solid var(--outline)",
                }}
              >
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
                    backgroundColor: "var(--surface-2)",
                    border: "1px solid var(--outline)",
                  }}
                />
                <button
                  type="submit"
                  className="px-4 py-2 rounded-full text-sm font-medium flex items-center gap-1.5 cursor-pointer"
                  style={{
                    backgroundColor: "var(--accent)",
                    color: "var(--on-accent)",
                  }}
                >
                  <Icon name="add" size={17} />
                  <span>Create</span>
                </button>
              </form>

              <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-3">
                {playlists.map((pl) => (
                  <button
                    key={pl.id}
                    type="button"
                    onClick={() => setSelectedPlaylistId(pl.id)}
                    className="flex items-center gap-3.5 p-3.5 rounded-2xl text-left transition-colors hover:opacity-90 cursor-pointer"
                    style={{
                      backgroundColor: "var(--surface-2)",
                      border: "1px solid var(--outline)",
                    }}
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
        <div className="space-y-2.5">
          {folders.map((folder) => (
            <button
              key={folder.dir}
              type="button"
              onClick={() => setSelectedFilter({ kind: "folder", value: folder.dir })}
              className="w-full flex items-center justify-between p-3.5 rounded-2xl text-left transition-colors hover:opacity-90 cursor-pointer"
              style={{
                backgroundColor: "var(--surface-2)",
                border: "1px solid var(--outline)",
              }}
            >
              <div className="flex items-center gap-3 min-w-0">
                <Icon name="folder" size={20} style={{ color: "var(--accent)" }} />
                <span className="text-sm font-medium truncate">{folder.dir}</span>
              </div>
              <span className="text-xs shrink-0" style={{ color: "var(--text-secondary)" }}>
                {folder.tracks.length} tracks
              </span>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
