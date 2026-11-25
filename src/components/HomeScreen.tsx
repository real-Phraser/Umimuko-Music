import { open as openFileDialog } from "@tauri-apps/plugin-dialog";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useLibraryStore } from "../store/useLibraryStore";
import { usePlayerStore } from "../store/usePlayerStore";
import { useSettingsStore } from "../store/useSettingsStore";
import type { Track } from "../types/ipc";
import { Artwork } from "./Artwork";
import { Icon } from "./Icon";

interface ShelfCollection {
  id: string;
  type: "album" | "playlist";
  title: string;
  subtitle: string;
  artworkPath: string | null;
  accentColor: string | null;
  trackIds: string[];
  pinned: boolean;
  pinTrackId: string | null;
}

function ShelfCard({
  item,
  onOpen,
}: {
  item: ShelfCollection;
  onOpen: () => void;
}) {
  const [menuOpen, setMenuOpen] = useState(false);
  const playTrack = usePlayerStore((s) => s.playTrack);
  const enqueueTracks = usePlayerStore((s) => s.enqueueTracks);
  const togglePin = useLibraryStore((s) => s.togglePin);

  return (
    <div
      onClick={onOpen}
      onContextMenu={(e) => {
        e.preventDefault();
        setMenuOpen(true);
      }}
      className="group relative w-[176px] shrink-0 rounded-[20px] p-3 flex flex-col items-center gap-[7px] cursor-pointer transition-colors hover:brightness-105"
      style={{ backgroundColor: "var(--surface-1)" }}
    >
      <div className="relative w-full h-[124px] flex items-center justify-center">
        <Artwork
          artworkPath={item.artworkPath}
          accentColor={item.accentColor}
          alt={item.title}
          size="lg"
          rounded="rounded-[31px]"
          className="!w-[124px] !h-[124px]"
        />

        {/* Top-right more menu (HomeScreen.kt line 282: 28dp) */}
        <button
          type="button"
          onClick={(e) => {
            e.stopPropagation();
            setMenuOpen((v) => !v);
          }}
          className="absolute right-0 top-0 w-7 h-7 rounded-full flex items-center justify-center hover:bg-[var(--surface-variant)] cursor-pointer"
          style={{ color: "var(--text-primary)" }}
          aria-label="Options"
          title="Options"
        >
          <Icon name="more" size={20} />
        </button>

        {/* Bottom-right circular accent Play button (HomeScreen.kt line 285: 32dp) */}
        <button
          type="button"
          disabled={item.trackIds.length === 0}
          onClick={(e) => {
            e.stopPropagation();
            if (item.trackIds.length > 0) {
              void playTrack(item.trackIds[0], item.trackIds);
            }
          }}
          className="absolute right-0 bottom-0 w-8 h-8 rounded-full flex items-center justify-center shadow-md transition-transform hover:scale-105 active:scale-95 disabled:opacity-40 cursor-pointer"
          style={{
            backgroundColor: "var(--accent)",
            color: "var(--on-accent)",
          }}
          aria-label={`Play ${item.title}`}
          title="Play"
        >
          <Icon name="play" size={20} />
        </button>

        {menuOpen && (
          <>
            <div
              className="fixed inset-0 z-30"
              onClick={(e) => {
                e.stopPropagation();
                setMenuOpen(false);
              }}
            />
            <div
              onClick={(e) => e.stopPropagation()}
              className="absolute right-0 top-8 z-40 w-48 py-1.5 rounded-xl shadow-2xl border"
              style={{
                backgroundColor: "var(--surface-3)",
                borderColor: "var(--outline)",
                boxShadow: "var(--shadow-elevated)",
              }}
            >
              <button
                type="button"
                onClick={() => {
                  setMenuOpen(false);
                  onOpen();
                }}
                className="w-full px-3.5 py-2.5 text-left text-[13px] leading-[18px] font-medium hover:bg-[var(--surface-variant)] cursor-pointer transition-colors"
              >
                Open {item.type}
              </button>
              <button
                type="button"
                disabled={item.trackIds.length === 0}
                onClick={() => {
                  void enqueueTracks(item.trackIds, true);
                  setMenuOpen(false);
                }}
                className="w-full px-3.5 py-2.5 text-left text-[13px] leading-[18px] font-medium hover:bg-[var(--surface-variant)] disabled:opacity-38 cursor-pointer transition-colors"
              >
                Play next
              </button>
              <button
                type="button"
                disabled={item.trackIds.length === 0}
                onClick={() => {
                  void enqueueTracks(item.trackIds, false);
                  setMenuOpen(false);
                }}
                className="w-full px-3.5 py-2.5 text-left text-[13px] leading-[18px] font-medium hover:bg-[var(--surface-variant)] disabled:opacity-38 cursor-pointer transition-colors"
              >
                Add to queue
              </button>
              {item.pinTrackId && (
                <button
                  type="button"
                  onClick={() => {
                    void togglePin(item.pinTrackId!);
                    setMenuOpen(false);
                  }}
                  className="w-full px-3.5 py-2.5 text-left text-[13px] leading-[18px] font-medium flex items-center gap-3 hover:bg-[var(--surface-variant)] cursor-pointer transition-colors"
                >
                  <Icon name={item.pinned ? "unpin" : "pin"} size={18} />
                  <span>{item.pinned ? "Unpin from Home" : "Pin to Home"}</span>
                </button>
              )}
            </div>
          </>
        )}
      </div>

      <div className="w-full text-[14px] leading-[20px] font-semibold text-center truncate">
        {item.title}
      </div>
      <div
        className="w-full text-[12px] leading-[16px] text-center truncate"
        style={{ color: "var(--text-secondary)" }}
      >
        {item.subtitle}
      </div>
      {item.pinned && (
        <div
          className="text-[11px] leading-[16px] font-medium uppercase tracking-[0.5px] text-center"
          style={{ color: "var(--accent)" }}
        >
          PINNED
        </div>
      )}
    </div>
  );
}

function HomeShelf({
  title,
  subtitle,
  items,
  onOpenCollection,
}: {
  title: string;
  subtitle: string;
  items: ShelfCollection[];
  onOpenCollection: (item: ShelfCollection) => void;
}) {
  const rowRef = useRef<HTMLDivElement>(null);
  const [canScrollLeft, setCanScrollLeft] = useState(false);
  const [canScrollRight, setCanScrollRight] = useState(false);

  const updateScrollState = useCallback(() => {
    const el = rowRef.current;
    if (!el) return;
    setCanScrollLeft(el.scrollLeft > 2);
    setCanScrollRight(el.scrollLeft + el.clientWidth < el.scrollWidth - 2);
  }, []);

  useEffect(() => {
    updateScrollState();
    const el = rowRef.current;
    if (!el) return;
    const ro = new ResizeObserver(updateScrollState);
    ro.observe(el);
    return () => ro.disconnect();
  }, [items.length, updateScrollState]);

  const scrollRow = (dir: -1 | 1) => {
    const el = rowRef.current;
    if (!el) return;
    el.scrollBy({
      left: dir * Math.max(360, el.clientWidth * 0.85),
      behavior: "smooth",
    });
  };

  if (items.length === 0) return null;

  return (
    <section className="flex flex-col gap-3">
      <div className="flex items-center gap-[6px]">
        <div className="flex-1 min-w-0 flex flex-col gap-[3px]">
          <h2 className="text-[22px] leading-[28px] font-semibold">{title}</h2>
          <p
            className="text-[12px] leading-[16px]"
            style={{ color: "var(--text-secondary)" }}
          >
            {subtitle}
          </p>
        </div>

        {(canScrollLeft || canScrollRight) && (
          <div className="flex items-center gap-[6px] shrink-0">
            <button
              type="button"
              disabled={!canScrollLeft}
              onClick={() => scrollRow(-1)}
              aria-label={`Scroll ${title} left`}
              className="w-9 h-9 rounded-full flex items-center justify-center transition-opacity hover:opacity-90 disabled:opacity-38 cursor-pointer disabled:cursor-default"
              style={{
                backgroundColor: "var(--accent-container)",
                color: "var(--on-accent-container)",
              }}
            >
              <Icon name="back" size={20} />
            </button>
            <button
              type="button"
              disabled={!canScrollRight}
              onClick={() => scrollRow(1)}
              aria-label={`Scroll ${title} right`}
              className="w-9 h-9 rounded-full flex items-center justify-center transition-opacity hover:opacity-90 disabled:opacity-38 cursor-pointer disabled:cursor-default"
              style={{
                backgroundColor: "var(--accent-container)",
                color: "var(--on-accent-container)",
              }}
            >
              <Icon name="back" size={20} className="rotate-180" />
            </button>
          </div>
        )}
      </div>

      <div
        ref={rowRef}
        onScroll={updateScrollState}
        className="flex items-stretch gap-3 overflow-x-auto no-scrollbar pb-1"
      >
        {items.map((item) => (
          <ShelfCard
            key={item.id}
            item={item}
            onOpen={() => onOpenCollection(item)}
          />
        ))}
      </div>
    </section>
  );
}

interface HomeScreenProps {
  canGoBack?: boolean;
  onBack?: () => void;
}

export function HomeScreen({ canGoBack = false, onBack }: HomeScreenProps) {
  const tracks = useLibraryStore((s) => s.tracks);
  const playlists = useLibraryStore((s) => s.playlists);
  const pinnedTrackIds = useLibraryStore((s) => s.pinnedTrackIds);
  const scanProgress = useLibraryStore((s) => s.scanProgress);

  const setActiveNav = useLibraryStore((s) => s.setActiveNav);
  const setLibraryTab = useLibraryStore((s) => s.setLibraryTab);
  const setSelectedPlaylistId = useLibraryStore((s) => s.setSelectedPlaylistId);
  const setSearchQuery = useLibraryStore((s) => s.setSearchQuery);
  const scanFolders = useLibraryStore((s) => s.scanFolders);
  const addFolder = useSettingsStore((s) => s.addFolder);

  const currentTrack = usePlayerStore((s) => s.playback.currentTrack);
  const status = usePlayerStore((s) => s.playback.status);
  const togglePlayPause = usePlayerStore((s) => s.togglePlayPause);
  const playTrack = usePlayerStore((s) => s.playTrack);

  const [searchOpen, setSearchOpen] = useState(false);
  const [localQuery, setLocalQuery] = useState("");

  const isPlaying = status === "playing";

  const continuationTrack = useMemo(() => {
    if (currentTrack) return currentTrack;
    const recent = [...tracks]
      .filter((t) => t.lastPlayedAt !== null)
      .sort((a, b) => (b.lastPlayedAt ?? 0) - (a.lastPlayedAt ?? 0));
    return recent[0] ?? tracks[0] ?? null;
  }, [currentTrack, tracks]);

  // Group tracks into albums and playlists for shelves (HomeScreen.kt lines 46-59)
  const sections = useMemo(() => {
    const pinnedSet = new Set(pinnedTrackIds);
    const map = new Map<
      string,
      {
        id: string;
        title: string;
        subtitle: string;
        artworkPath: string | null;
        accentColor: string | null;
        tracks: Track[];
        pinned: boolean;
        lastPlayedAt: number;
        latestAddedAt: number;
      }
    >();

    for (const t of tracks) {
      const albumTitle = t.album || "Unknown Album";
      const artistName = t.albumArtist || t.artist || "Unknown Artist";
      const key = `${albumTitle}__${artistName}`;
      const existing = map.get(key);
      const isPin = pinnedSet.has(t.id);
      if (existing) {
        existing.tracks.push(t);
        if (isPin) existing.pinned = true;
        if (!existing.artworkPath && t.artworkPath) {
          existing.artworkPath = t.artworkPath;
        }
        if (!existing.accentColor && t.accentColor) {
          existing.accentColor = t.accentColor;
        }
        if ((t.lastPlayedAt ?? 0) > existing.lastPlayedAt) {
          existing.lastPlayedAt = t.lastPlayedAt ?? 0;
        }
        if (t.addedAt > existing.latestAddedAt) {
          existing.latestAddedAt = t.addedAt;
        }
      } else {
        map.set(key, {
          id: key,
          title: albumTitle,
          subtitle: artistName,
          artworkPath: t.artworkPath,
          accentColor: t.accentColor,
          tracks: [t],
          pinned: isPin,
          lastPlayedAt: t.lastPlayedAt ?? 0,
          latestAddedAt: t.addedAt,
        });
      }
    }

    const q = localQuery.trim().toLowerCase();
    const matchesQuery = (
      title: string,
      subtitle: string,
      groupTracks: Track[]
    ) => {
      if (!q) return true;
      return (
        title.toLowerCase().includes(q) ||
        subtitle.toLowerCase().includes(q) ||
        groupTracks.some(
          (t) =>
            t.title.toLowerCase().includes(q) ||
            t.artist.toLowerCase().includes(q)
        )
      );
    };

    const allAlbums = [...map.values()].filter((g) =>
      matchesQuery(g.title, g.subtitle, g.tracks)
    );

    const toAlbumShelfItem = (
      g: (typeof allAlbums)[number]
    ): ShelfCollection => ({
      id: `album:${g.id}`,
      type: "album",
      title: g.title,
      subtitle: g.subtitle,
      artworkPath: g.artworkPath,
      accentColor: g.accentColor,
      trackIds: g.tracks.map((t) => t.id),
      pinned: g.pinned,
      pinTrackId: g.tracks[0]?.id ?? null,
    });

    const trackById = new Map(tracks.map((t) => [t.id, t]));
    const pinnedPlaylists: ShelfCollection[] = playlists
      .filter((pl) => pl.pinned)
      .map((pl) => {
        const plTracks = pl.trackIds
          .map((id) => trackById.get(id))
          .filter((t): t is Track => Boolean(t));
        return {
          id: `playlist:${pl.id}`,
          type: "playlist" as const,
          title: pl.name,
          subtitle: `${plTracks.length} ${plTracks.length === 1 ? "track" : "tracks"}`,
          artworkPath: plTracks.find((t) => t.artworkPath)?.artworkPath ?? null,
          accentColor: plTracks.find((t) => t.accentColor)?.accentColor ?? null,
          trackIds: pl.trackIds,
          pinned: true,
          pinTrackId: null,
        };
      })
      .filter((item) => {
        const plTracks = item.trackIds
          .map((id) => trackById.get(id))
          .filter((t): t is Track => Boolean(t));
        return matchesQuery(item.title, item.subtitle, plTracks);
      });

    return {
      totalAlbums: map.size,
      pinned: [
        ...allAlbums.filter((g) => g.pinned).map(toAlbumShelfItem),
        ...pinnedPlaylists,
      ],
      recent: allAlbums
        .filter((g) => g.lastPlayedAt > 0)
        .sort((a, b) => b.lastPlayedAt - a.lastPlayedAt)
        .slice(0, 40)
        .map(toAlbumShelfItem),
      added: [...allAlbums]
        .sort(
          (a, b) =>
            b.latestAddedAt - a.latestAddedAt ||
            a.title.localeCompare(b.title)
        )
        .slice(0, 40)
        .map(toAlbumShelfItem),
    };
  }, [tracks, playlists, pinnedTrackIds, localQuery]);

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

  const handleOpenCollection = (item: ShelfCollection) => {
    if (item.type === "playlist") {
      const plId = item.id.replace(/^playlist:/, "");
      setSelectedPlaylistId(plId);
      setLibraryTab("playlists");
      setActiveNav("library");
      return;
    }
    setSearchQuery(item.title);
    setLibraryTab("songs");
    setActiveNav("library");
  };

  return (
    <div className="flex-1 min-h-0 flex flex-col gap-[10px]">
      {/* Content Header (LibraryPanel.kt lines 431-491) */}
      <div className="flex items-center shrink-0">
        <div className="flex items-center min-w-0 flex-1">
          <button
            type="button"
            onClick={() => {
              if (searchOpen) {
                setSearchOpen(false);
                setLocalQuery("");
              } else if (canGoBack && onBack) {
                onBack();
              }
            }}
            aria-label="Back"
            className="w-10 h-10 rounded-full flex items-center justify-center transition-opacity hover:bg-[var(--surface-variant)] cursor-pointer"
            style={{ color: "var(--text-primary)" }}
          >
            <Icon name="back" size={24} />
          </button>
        </div>

        <div className="flex items-center gap-2 shrink-0">
          <button
            type="button"
            onClick={() => {
              if (searchOpen) setLocalQuery("");
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
            value={localQuery}
            onChange={(e) => setLocalQuery(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Escape") {
                setLocalQuery("");
                setSearchOpen(false);
              }
            }}
            placeholder="Search your library"
            className="bg-transparent text-sm outline-none flex-1 placeholder:text-[var(--text-muted)]"
          />
          <button
            type="button"
            onClick={() => {
              setLocalQuery("");
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

      {/* Scrollable Home Content (HomeScreen.kt lines 113-170) */}
      <div className="flex-1 min-h-0 overflow-y-auto pr-3 pb-6 flex flex-col gap-6">
        {/* PLAYING NOW / CONTINUE LISTENING hero card (HomeScreen.kt lines 116-137) */}
        {!localQuery.trim() && continuationTrack && (
          <section
            className="w-full rounded-[24px] p-5 flex items-center gap-[18px] adaptive-transition shrink-0"
            style={{
              background:
                "linear-gradient(90deg, color-mix(in srgb, var(--accent) 11%, var(--surface-1)) 0%, var(--surface-1) 100%)",
            }}
          >
            <Artwork
              artworkPath={continuationTrack.artworkPath}
              accentColor={continuationTrack.accentColor}
              alt={continuationTrack.title}
              size="lg"
              rounded="rounded-[20px]"
              className="!w-20 !h-20"
            />

            <div className="min-w-0 flex-1 flex flex-col gap-[5px]">
              <div
                className="text-[11px] leading-[16px] font-medium uppercase tracking-[0.5px]"
                style={{ color: "var(--accent)" }}
              >
                {currentTrack && isPlaying ? "PLAYING NOW" : "CONTINUE LISTENING"}
              </div>
              <div className="text-[22px] leading-[28px] font-bold truncate">
                {continuationTrack.title}
              </div>
              <div
                className="text-[14px] leading-[20px] truncate"
                style={{ color: "var(--text-secondary)" }}
              >
                {continuationTrack.artist}
              </div>
              {continuationTrack.album && (
                <div
                  className="text-[12px] leading-[16px] truncate opacity-70"
                  style={{ color: "var(--text-secondary)" }}
                >
                  {continuationTrack.album}
                </div>
              )}
            </div>

            <button
              type="button"
              onClick={() => {
                if (currentTrack) {
                  void togglePlayPause();
                } else {
                  void playTrack(continuationTrack.id);
                }
              }}
              aria-label={currentTrack && isPlaying ? "Pause" : "Play"}
              className="w-[52px] h-[52px] rounded-full flex items-center justify-center shrink-0 shadow-md transition-transform hover:scale-105 active:scale-95 cursor-pointer"
              style={{
                backgroundColor: "var(--accent)",
                color: "var(--on-accent)",
              }}
            >
              <Icon name={currentTrack && isPlaying ? "pause" : "play"} size={28} />
            </button>
          </section>
        )}

        {/* Empty Library Card (HomeScreen.kt lines 140-149) */}
        {tracks.length === 0 && (
          <section
            className="w-full rounded-[24px] p-8 flex flex-col gap-[10px]"
            style={{ backgroundColor: "var(--surface-1)" }}
          >
            <Icon name="music" size={36} style={{ color: "var(--accent)" }} />
            <h2 className="text-[24px] leading-[32px] font-normal">
              Make yourself at home
            </h2>
            <p
              className="text-[14px] leading-[20px]"
              style={{ color: "var(--text-secondary)" }}
            >
              Add a music folder above. Your albums, recent listening and pinned
              collections will appear here.
            </p>
          </section>
        )}

        {/* Shelves */}
        <HomeShelf
          title="Pinned"
          subtitle="Your favorites, within reach"
          items={sections.pinned}
          onOpenCollection={handleOpenCollection}
        />

        <HomeShelf
          title="Recently played"
          subtitle="Pick up an album again"
          items={sections.recent}
          onOpenCollection={handleOpenCollection}
        />

        <HomeShelf
          title="Recently added"
          subtitle="The latest albums in your library"
          items={sections.added}
          onOpenCollection={handleOpenCollection}
        />

        {/* Pin hint (HomeScreen.kt lines 159-165) */}
        {sections.totalAlbums > 0 &&
          sections.pinned.length === 0 &&
          !localQuery.trim() && (
            <div className="w-full py-1 flex items-center gap-[10px]">
              <Icon name="pin" size={24} style={{ color: "var(--accent)" }} />
              <span
                className="text-[12px] leading-[16px]"
                style={{ color: "var(--text-secondary)" }}
              >
                Open an album or playlist and choose Pin to Home.
              </span>
            </div>
          )}

        {/* Search no results (HomeScreen.kt lines 166-168) */}
        {sections.totalAlbums > 0 &&
          sections.added.length === 0 &&
          sections.pinned.length === 0 &&
          sections.recent.length === 0 && (
            <p className="text-[14px] leading-[20px]">
              No collections match your search.
            </p>
          )}
      </div>
    </div>
  );
}
