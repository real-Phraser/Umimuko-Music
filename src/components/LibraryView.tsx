import { open as openFileDialog } from "@tauri-apps/plugin-dialog";
import { useEffect, useMemo, useState } from "react";
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
  { field: "addedAt", label: "Date Added" },
  { field: "durationMs", label: "Duration" },
];

function parentDir(path: string): string {
  const normalized = path.replace(/\\/g, "/");
  const idx = normalized.lastIndexOf("/");
  return idx > 0 ? normalized.slice(0, idx) : "/";
}

function folderLeafName(dir: string): string {
  const cleaned = dir.replace(/\\/g, "/").replace(/\/+$/, "");
  const idx = cleaned.lastIndexOf("/");
  return idx >= 0 ? cleaned.slice(idx + 1) || dir : dir;
}

function countLabel(n: number, singular: string): string {
  return `${n} ${n === 1 ? singular : `${singular}s`}`;
}

interface LibraryViewProps {
  canGoBack?: boolean;
  onBack?: () => void;
}

export function LibraryView({ canGoBack = true, onBack }: LibraryViewProps) {
  const activeNav = useLibraryStore((s) => s.activeNav);
  const libraryTab = useLibraryStore((s) => s.libraryTab);
  const tracks = useLibraryStore((s) => s.tracks);
  const playlists = useLibraryStore((s) => s.playlists);
  const pinnedTrackIds = useLibraryStore((s) => s.pinnedTrackIds);
  const selectedPlaylistId = useLibraryStore((s) => s.selectedPlaylistId);
  const searchQuery = useLibraryStore((s) => s.searchQuery);
  const sortBy = useLibraryStore((s) => s.sortBy);
  const sortDir = useLibraryStore((s) => s.sortDir);
  const scanProgress = useLibraryStore((s) => s.scanProgress);

  const setActiveNav = useLibraryStore((s) => s.setActiveNav);
  const setLibraryTab = useLibraryStore((s) => s.setLibraryTab);
  const setSelectedPlaylistId = useLibraryStore((s) => s.setSelectedPlaylistId);
  const setSearchQuery = useLibraryStore((s) => s.setSearchQuery);
  const setSort = useLibraryStore((s) => s.setSort);
  const createPlaylist = useLibraryStore((s) => s.createPlaylist);
  const deletePlaylist = useLibraryStore((s) => s.deletePlaylist);
  const togglePin = useLibraryStore((s) => s.togglePin);
  const openDialog = useLibraryStore((s) => s.openDialog);
  const scanFolders = useLibraryStore((s) => s.scanFolders);
  const addFolder = useSettingsStore((s) => s.addFolder);
  const removeFolder = useSettingsStore((s) => s.removeFolder);
  const playTrack = usePlayerStore((s) => s.playTrack);

  const [searchOpen, setSearchOpen] = useState(activeNav === "search");
  const [sortMenuOpen, setSortMenuOpen] = useState(false);
  const [newPlaylistModal, setNewPlaylistModal] = useState(false);
  const [deletePlaylistConfirm, setDeletePlaylistConfirm] = useState(false);
  const [newPlaylistName, setNewPlaylistName] = useState("");
  const [selectedFilter, setSelectedFilter] = useState<{
    kind: "album" | "artist" | "folder";
    value: string;
  } | null>(null);

  // Clear local sub-filter when switching tabs at top level
  useEffect(() => {
    setSelectedFilter(null);
  }, [activeNav, libraryTab]);

  useEffect(() => {
    if (activeNav === "search") {
      setSearchOpen(true);
    }
  }, [activeNav]);

  const activePlaylist = useMemo(
    () => playlists.find((p) => p.id === selectedPlaylistId) ?? null,
    [playlists, selectedPlaylistId]
  );

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
        t.path.toLowerCase().includes(q) ||
        t.genre.toLowerCase().includes(q)
      );
    });

    list = [...list].sort((a, b) => {
      let cmp = 0;
      if (sortBy === "addedAt" || sortBy === "durationMs") {
        cmp = a[sortBy] - b[sortBy];
      } else {
        cmp = a[sortBy].localeCompare(b[sortBy], undefined, { sensitivity: "base" });
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
        id: string;
        title: string;
        subtitle: string;
        artworkPath: string | null;
        accentColor: string | null;
        tracks: Track[];
      }
    >();
    for (const t of tracks) {
      const albumTitle = t.album || "Unknown Album";
      const artistName = t.albumArtist || t.artist || "Unknown Artist";
      const key = `${albumTitle}__${artistName}`;
      const existing = map.get(key);
      if (existing) {
        existing.tracks.push(t);
        if (!existing.artworkPath && t.artworkPath) {
          existing.artworkPath = t.artworkPath;
        }
      } else {
        map.set(key, {
          id: albumTitle,
          title: albumTitle,
          subtitle: artistName,
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
          a.title.toLowerCase().includes(q) ||
          a.subtitle.toLowerCase().includes(q) ||
          a.tracks.some((t) => t.title.toLowerCase().includes(q))
      )
      .sort((a, b) => a.title.localeCompare(b.title));
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
      .map(([artist, items]) => ({
        id: artist,
        title: artist,
        subtitle: countLabel(items.length, "track"),
        artworkPath: items.find((t) => t.artworkPath)?.artworkPath ?? null,
        accentColor: items.find((t) => t.accentColor)?.accentColor ?? null,
        tracks: items,
      }))
      .filter(
        (a) =>
          !q ||
          a.title.toLowerCase().includes(q) ||
          a.tracks.some((t) => t.title.toLowerCase().includes(q))
      )
      .sort((a, b) => a.title.localeCompare(b.title));
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
      .map(([dir, items]) => ({
        id: dir,
        title: folderLeafName(dir),
        subtitle: `${countLabel(items.length, "track")} · ${dir}`,
        artworkPath: items.find((t) => t.artworkPath)?.artworkPath ?? null,
        accentColor: items.find((t) => t.accentColor)?.accentColor ?? null,
        tracks: items,
      }))
      .filter(
        (f) =>
          !q ||
          f.title.toLowerCase().includes(q) ||
          f.id.toLowerCase().includes(q) ||
          f.tracks.some((t) => t.title.toLowerCase().includes(q))
      )
      .sort((a, b) => a.title.localeCompare(b.title));
  }, [tracks, searchQuery]);

  const playlistGroups = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    const trackById = new Map(tracks.map((t) => [t.id, t]));
    return playlists
      .map((pl) => {
        const items = pl.trackIds
          .map((id) => trackById.get(id))
          .filter((t): t is Track => Boolean(t));
        return {
          id: pl.id,
          title: pl.name,
          subtitle: `${items.length} of ${pl.trackIds.length} tracks available`,
          artworkPath: items.find((t) => t.artworkPath)?.artworkPath ?? null,
          accentColor: items.find((t) => t.accentColor)?.accentColor ?? null,
          tracks: items,
        };
      })
      .filter(
        (g) =>
          !q ||
          g.title.toLowerCase().includes(q) ||
          g.tracks.some((t) => t.title.toLowerCase().includes(q))
      );
  }, [playlists, tracks, searchQuery]);

  const activePlaylistTracks = useMemo(() => {
    if (!activePlaylist) return [];
    const trackById = new Map(tracks.map((t) => [t.id, t]));
    const q = searchQuery.trim().toLowerCase();
    return activePlaylist.trackIds
      .map((id) => trackById.get(id))
      .filter((t): t is Track => Boolean(t))
      .filter(
        (t) =>
          !q ||
          t.title.toLowerCase().includes(q) ||
          t.artist.toLowerCase().includes(q) ||
          t.album.toLowerCase().includes(q)
      );
  }, [activePlaylist, tracks, searchQuery]);

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
    setNewPlaylistModal(false);
    setSelectedPlaylistId(pl.id);
  };

  const handleBack = () => {
    if (selectedFilter) {
      setSelectedFilter(null);
      setSearchQuery("");
      return;
    }
    if (selectedPlaylistId) {
      setSelectedPlaylistId(null);
      setSearchQuery("");
      return;
    }
    if (onBack) {
      onBack();
      return;
    }
    setActiveNav("home");
  };

  const inCollection =
    activeNav === "library" &&
    (libraryTab === "albums" ||
      libraryTab === "artists" ||
      libraryTab === "playlists" ||
      libraryTab === "folders");

  const showGroups =
    inCollection && selectedFilter === null && activePlaylist === null;

  const visibleGroups =
    libraryTab === "albums"
      ? albums
      : libraryTab === "artists"
        ? artists
        : libraryTab === "playlists"
          ? playlistGroups
          : libraryTab === "folders"
            ? folders
            : [];

  const countInHeader =
    selectedFilter === null &&
    activePlaylist === null &&
    (activeNav === "library" &&
      (libraryTab === "songs" ||
        libraryTab === "albums" ||
        libraryTab === "artists"));

  const displayedTracks = activePlaylist
    ? activePlaylistTracks
    : filteredAndSortedTracks;

  // Header title & subtitle matching LibraryPanel.kt lines 363-445
  let title = "Tracks";
  let subtitle = countLabel(displayedTracks.length, "track");
  if (activeNav === "liked") {
    title = "Liked songs";
    subtitle = "Your favorites, kept for next time.";
  } else if (activeNav === "search") {
    title = "Search";
    subtitle = countLabel(displayedTracks.length, "track");
  } else if (selectedFilter) {
    if (selectedFilter.kind === "folder") {
      title = folderLeafName(selectedFilter.value);
      subtitle = `${countLabel(displayedTracks.length, "track")} · ${selectedFilter.value}`;
    } else {
      title = selectedFilter.value;
      subtitle = countLabel(displayedTracks.length, "track");
    }
  } else if (activePlaylist) {
    title = activePlaylist.name;
    subtitle = `${activePlaylistTracks.length} of ${activePlaylist.trackIds.length} tracks available`;
  } else if (libraryTab === "albums") {
    title = "Albums";
    subtitle = countLabel(albums.length, "album");
  } else if (libraryTab === "artists") {
    title = "Artists";
    subtitle = countLabel(artists.length, "artist");
  } else if (libraryTab === "playlists") {
    title = "Playlists";
    subtitle = "Your own collections.";
  } else if (libraryTab === "folders") {
    title = "Folders";
    subtitle = "Your music folders.";
  }

  const activeSortLabel =
    SORT_OPTIONS.find((o) => o.field === sortBy)?.label ?? "Title";

  const showActionBar = !(showGroups && countInHeader);

  const albumPinTrackId =
    selectedFilter?.kind === "album" ? displayedTracks[0]?.id ?? null : null;
  const isAlbumPinned = albumPinTrackId
    ? pinnedTrackIds.includes(albumPinTrackId)
    : false;

  const handleOpenAlbumFromRow = (albumName: string) => {
    setActiveNav("library");
    setLibraryTab("albums");
    setSelectedPlaylistId(null);
    setSelectedFilter({ kind: "album", value: albumName || "Unknown Album" });
  };

  const handleOpenArtistFromRow = (artistName: string) => {
    setActiveNav("library");
    setLibraryTab("artists");
    setSelectedPlaylistId(null);
    setSelectedFilter({ kind: "artist", value: artistName || "Unknown Artist" });
  };

  return (
    <div className="flex-1 min-h-0 flex flex-col gap-[10px]">
      {/* Content Header (LibraryPanel.kt lines 431-491) */}
      <div className="flex items-center gap-2 shrink-0">
        {canGoBack && (
          <button
            type="button"
            onClick={handleBack}
            aria-label={`Back to ${title}`}
            className="w-10 h-10 rounded-full flex items-center justify-center shrink-0 transition-opacity hover:bg-[var(--surface-variant)] cursor-pointer"
            style={{ color: "var(--text-primary)" }}
          >
            <Icon name="back" size={24} />
          </button>
        )}

        <div className="min-w-0 flex-1">
          <h1 className="text-[28px] leading-[36px] font-normal truncate">
            {title}
          </h1>
          <p
            className="text-[12px] leading-[16px] truncate"
            style={{ color: "var(--text-secondary)" }}
          >
            {subtitle}
          </p>
        </div>

        <div className="flex items-center gap-2 shrink-0">
          <button
            type="button"
            onClick={() => {
              if (searchOpen) setSearchQuery("");
              setSearchOpen((v) => !v);
            }}
            aria-label={searchOpen ? "Close search" : "Open search"}
            className="w-[56px] h-[40px] rounded-full flex items-center justify-center transition-opacity hover:opacity-90 cursor-pointer"
            style={{
              backgroundColor: "var(--accent-container)",
              color: "var(--on-accent-container)",
            }}
          >
            <Icon name="search" size={20} />
          </button>

          <button
            type="button"
            disabled={scanProgress.scanning}
            onClick={() => void handleAddFolder()}
            className="h-10 pl-[14px] pr-[18px] rounded-full text-[14px] leading-[20px] font-medium flex items-center gap-2 transition-opacity hover:opacity-90 disabled:opacity-40 cursor-pointer"
            style={{
              backgroundColor: "var(--accent)",
              color: "var(--on-accent)",
            }}
          >
            <Icon name="folder" size={20} />
            <span>Add folder</span>
          </button>
        </div>
      </div>

      {/* Expandable Search Bar (LibraryPanel.kt lines 505-523) */}
      {searchOpen && (
        <div
          className="shrink-0 flex items-center gap-3 px-4 py-3 rounded-[24px] border border-[var(--outline-strong)] focus-within:border-[var(--accent)] transition-colors"
          style={{
            backgroundColor: "var(--surface-0)",
          }}
        >
          <Icon name="search" size={20} style={{ color: "var(--text-secondary)" }} />
          <input
            type="text"
            autoFocus
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Escape") {
                setSearchQuery("");
                setSearchOpen(false);
              }
            }}
            placeholder={`Search ${title}`}
            className="bg-transparent text-sm outline-none flex-1 placeholder:text-[var(--text-muted)]"
          />
          <button
            type="button"
            onClick={() => {
              setSearchQuery("");
              setSearchOpen(false);
            }}
            aria-label="Close search"
            className="w-7 h-7 rounded-full flex items-center justify-center hover:bg-[var(--surface-variant)] cursor-pointer"
            style={{ color: "var(--text-primary)" }}
          >
            <Icon name="back" size={20} />
          </button>
        </div>
      )}

      {/* Action / Sort / Play All Row (LibraryPanel.kt lines 524-586) */}
      {showActionBar && (
        <div className="shrink-0 flex flex-wrap items-center gap-2">
          {countInHeader ? (
            <div className="flex-1" />
          ) : (
            <div className="flex-1 text-[14px] leading-[20px] font-medium">
              {showGroups
                ? countLabel(visibleGroups.length, "collection")
                : countLabel(displayedTracks.length, "track")}
            </div>
          )}

          {!showGroups && (
            <>
              <div className="relative">
                <button
                  type="button"
                  onClick={() => setSortMenuOpen((v) => !v)}
                  className="h-10 px-3 rounded-full text-[12px] leading-[16px] font-medium flex items-center cursor-pointer hover:bg-[var(--surface-variant)]"
                  style={{
                    backgroundColor: "transparent",
                    border: "1px solid var(--outline-strong)",
                    color: "var(--accent)",
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
                      className="absolute right-0 top-11 z-40 w-48 py-1.5 rounded-xl shadow-2xl border"
                      style={{
                        backgroundColor: "var(--surface-3)",
                        borderColor: "var(--outline)",
                        boxShadow: "var(--shadow-elevated)",
                      }}
                    >
                      {SORT_OPTIONS.map((opt) => {
                        const isSelected = sortBy === opt.field;
                        return (
                          <button
                            key={opt.field}
                            type="button"
                            onClick={() => {
                              setSort(opt.field);
                              setSortMenuOpen(false);
                            }}
                            className="w-full px-3.5 py-2.5 text-left text-[13px] leading-[18px] flex items-center justify-between hover:bg-[var(--surface-variant)] cursor-pointer transition-colors"
                            style={{
                              fontWeight: isSelected ? 600 : 500,
                              color: isSelected ? "var(--accent)" : "var(--text-primary)",
                            }}
                          >
                            <span>{opt.label}</span>
                            {isSelected && (
                              <span>{sortDir === "asc" ? "↑" : "↓"}</span>
                            )}
                          </button>
                        );
                      })}
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
                    className="h-10 px-3 rounded-full text-[12px] leading-[16px] font-medium flex items-center gap-1.5 cursor-pointer hover:bg-[var(--surface-variant)]"
                    style={{
                      backgroundColor: "transparent",
                      border: "1px solid var(--outline-strong)",
                      color: "var(--accent)",
                    }}
                  >
                    <Icon name="search" size={18} />
                    <span>Duplicates</span>
                  </button>
                )}

              <button
                type="button"
                disabled={displayedTracks.length === 0}
                onClick={() =>
                  void playTrack(
                    displayedTracks[0].id,
                    displayedTracks.map((t) => t.id)
                  )
                }
                className="h-10 pl-[14px] pr-[18px] rounded-full text-[14px] leading-[20px] font-medium flex items-center gap-2 transition-opacity hover:opacity-90 disabled:opacity-40 cursor-pointer"
                style={{
                  backgroundColor: "var(--accent-container)",
                  color: "var(--on-accent-container)",
                }}
              >
                <Icon name="play" size={20} />
                <span>Play all</span>
              </button>
            </>
          )}

          {activeNav === "library" &&
            libraryTab === "playlists" &&
            !activePlaylist && (
              <button
                type="button"
                onClick={() => {
                  setNewPlaylistName("");
                  setNewPlaylistModal(true);
                }}
                className="h-10 px-3 rounded-full text-[14px] leading-[20px] font-medium flex items-center gap-1.5 hover:bg-[var(--surface-variant)] cursor-pointer"
                style={{ color: "var(--accent)" }}
              >
                <Icon name="add" size={20} />
                <span>New playlist</span>
              </button>
            )}

          {albumPinTrackId && (
            <button
              type="button"
              onClick={() => void togglePin(albumPinTrackId)}
              className="h-10 px-3 rounded-full text-[14px] leading-[20px] font-medium flex items-center gap-1.5 hover:bg-[var(--surface-variant)] cursor-pointer"
              style={{ color: "var(--accent)" }}
            >
              <Icon name={isAlbumPinned ? "unpin" : "pin"} size={20} />
              <span>{isAlbumPinned ? "Unpin" : "Pin to Home"}</span>
            </button>
          )}

          {activePlaylist && (
            <button
              type="button"
              onClick={() => setDeletePlaylistConfirm(true)}
              className="h-10 px-3 rounded-full text-[14px] leading-[20px] font-medium hover:bg-[var(--surface-variant)] cursor-pointer"
              style={{ color: "var(--accent)" }}
            >
              Delete
            </button>
          )}

          {selectedFilter?.kind === "folder" && (
            <button
              type="button"
              onClick={async () => {
                await removeFolder(selectedFilter.value);
                setSelectedFilter(null);
                await scanFolders();
              }}
              className="h-10 px-3 rounded-full text-[14px] leading-[20px] font-medium hover:bg-[var(--surface-variant)] cursor-pointer"
              style={{ color: "var(--accent)" }}
            >
              Remove folder
            </button>
          )}
        </div>
      )}

      {/* Scrollable Grid or Track List (LibraryPanel.kt lines 597-663) */}
      <div className="flex-1 min-h-0 overflow-y-auto pr-3 pb-6">
        {showGroups ? (
          visibleGroups.length === 0 ? (
            <div className="h-full min-h-[280px] flex flex-col items-center justify-center gap-3 text-center">
              <Artwork
                artworkPath={null}
                alt="Empty"
                size="lg"
                rounded="rounded-[16px]"
                className="!w-16 !h-16"
              />
              <div className="text-[22px] leading-[28px] font-normal">
                No {libraryTab} yet
              </div>
              <div
                className="text-[12px] leading-[16px]"
                style={{ color: "var(--text-secondary)" }}
              >
                {libraryTab === "playlists"
                  ? "Create a playlist, then use a track's menu to add songs."
                  : libraryTab === "folders"
                    ? "Add a music folder to see it here."
                    : "Choose a music folder or try another search."}
              </div>
            </div>
          ) : (
            <div
              className="grid gap-3"
              style={{
                gridTemplateColumns: "repeat(auto-fill, minmax(170px, 1fr))",
              }}
            >
              {visibleGroups.map((group) => (
                <button
                  key={group.id}
                  type="button"
                  onClick={() => {
                    if (libraryTab === "playlists") {
                      setSelectedPlaylistId(group.id);
                    } else if (libraryTab === "albums") {
                      setSelectedFilter({ kind: "album", value: group.id });
                    } else if (libraryTab === "artists") {
                      setSelectedFilter({ kind: "artist", value: group.id });
                    } else if (libraryTab === "folders") {
                      setSelectedFilter({ kind: "folder", value: group.id });
                    }
                  }}
                  className="text-center p-4 rounded-[20px] flex flex-col items-center gap-2 transition-colors hover:brightness-105 cursor-pointer"
                  style={{ backgroundColor: "var(--surface-1)" }}
                >
                  <Artwork
                    artworkPath={group.artworkPath}
                    accentColor={group.accentColor}
                    alt={group.title}
                    size="lg"
                    rounded="rounded-[28px]"
                    className="!w-[112px] !h-[112px]"
                  />
                  <div className="w-full text-[14px] leading-[20px] font-medium truncate">
                    {group.title}
                  </div>
                  <div
                    className="w-full text-[11px] leading-[16px] truncate"
                    style={{ color: "var(--text-secondary)" }}
                  >
                    {group.subtitle}
                  </div>
                </button>
              ))}
            </div>
          )
        ) : displayedTracks.length === 0 ? (
          <div className="h-full min-h-[280px] flex flex-col items-center justify-center gap-3 text-center">
            <Artwork
              artworkPath={null}
              alt="No tracks"
              size="lg"
              rounded="rounded-[16px]"
              className="!w-16 !h-16"
            />
            <div className="text-[22px] leading-[28px] font-normal">
              No tracks here yet
            </div>
            <div
              className="text-[12px] leading-[16px]"
              style={{ color: "var(--text-secondary)" }}
            >
              {activeNav === "liked"
                ? "Tap a heart next to a track to save it here."
                : "Try another search or add a music folder."}
            </div>
          </div>
        ) : (
          <div className="flex flex-col gap-1">
            {displayedTracks.map((track, idx) => (
              <TrackRow
                key={track.id}
                track={track}
                index={idx}
                queueIds={displayedTracks.map((t) => t.id)}
                onOpenAlbum={handleOpenAlbumFromRow}
                onOpenArtist={handleOpenArtistFromRow}
              />
            ))}
          </div>
        )}
      </div>

      {/* New Playlist Modal (LibraryPanel.kt lines 695-707) */}
      {newPlaylistModal && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center p-6 bg-black/55 backdrop-blur-[2px]"
          onMouseDown={(e) => {
            if (e.target === e.currentTarget) setNewPlaylistModal(false);
          }}
        >
          <form
            onSubmit={(e) => void handleCreatePlaylist(e)}
            className="w-full max-w-[400px] p-6 rounded-[28px] flex flex-col gap-[18px] shadow-2xl"
            style={{
              backgroundColor: "var(--surface-2)",
              border: "1px solid var(--outline)",
            }}
          >
            <h2 className="text-[22px] leading-[28px] font-normal">
              New playlist
            </h2>
            <label className="block space-y-1">
              <span className="text-xs font-medium" style={{ color: "var(--text-secondary)" }}>
                Playlist name
              </span>
              <input
                type="text"
                autoFocus
                value={newPlaylistName}
                onChange={(e) => setNewPlaylistName(e.target.value)}
                placeholder="Playlist name"
                className="w-full px-3.5 py-2.5 rounded-xl text-sm outline-none border border-[var(--outline-strong)] focus:border-[var(--accent)] transition-colors"
                style={{
                  backgroundColor: "var(--surface-1)",
                }}
              />
            </label>
            <div className="flex items-center justify-end gap-2">
              <button
                type="button"
                onClick={() => setNewPlaylistModal(false)}
                className="h-10 px-4 rounded-full text-sm font-medium hover:bg-[var(--surface-variant)] cursor-pointer transition-colors"
                style={{ color: "var(--accent)" }}
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={!newPlaylistName.trim()}
                className="h-10 px-4 rounded-full text-sm font-medium hover:bg-[var(--surface-variant)] disabled:opacity-40 cursor-pointer transition-colors"
                style={{ color: "var(--accent)" }}
              >
                Save
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Delete Playlist Confirmation (LibraryPanel.kt lines 722-727) */}
      {deletePlaylistConfirm && activePlaylist && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center p-6 bg-black/55 backdrop-blur-[2px]"
          onMouseDown={(e) => {
            if (e.target === e.currentTarget) setDeletePlaylistConfirm(false);
          }}
        >
          <div
            className="w-full max-w-[400px] p-6 rounded-[28px] flex flex-col gap-[18px] shadow-2xl"
            style={{
              backgroundColor: "var(--surface-2)",
              border: "1px solid var(--outline)",
            }}
          >
            <h2 className="text-[22px] leading-[28px] font-normal">
              Delete playlist?
            </h2>
            <p
              className="text-[14px] leading-[20px]"
              style={{ color: "var(--text-secondary)" }}
            >
              The playlist is removed. Your music files stay on disk.
            </p>
            <div className="flex items-center justify-end gap-2">
              <button
                type="button"
                onClick={() => setDeletePlaylistConfirm(false)}
                className="h-10 px-4 rounded-full text-sm font-medium hover:bg-[var(--surface-variant)] cursor-pointer transition-colors"
                style={{ color: "var(--accent)" }}
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={async () => {
                  await deletePlaylist(activePlaylist.id);
                  setDeletePlaylistConfirm(false);
                }}
                className="h-10 px-4 rounded-full text-sm font-medium hover:bg-[var(--danger-container)] cursor-pointer transition-colors"
                style={{ color: "var(--danger)" }}
              >
                Delete
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
