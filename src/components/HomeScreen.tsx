import { open as openFileDialog } from "@tauri-apps/plugin-dialog";
import { useMemo, useRef, useState } from "react";
import { useLibraryStore } from "../store/useLibraryStore";
import { usePlayerStore } from "../store/usePlayerStore";
import { useSettingsStore } from "../store/useSettingsStore";
import type { Track } from "../types/ipc";
import { Artwork } from "./Artwork";
import { Icon } from "./Icon";

interface ShelfCollection {
  id: string;
  title: string;
  subtitle: string;
  artworkPath: string | null;
  accentColor: string | null;
  trackIds: string[];
  pinned: boolean;
  pinTrackId: string;
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
      className="group relative w-[176px] shrink-0 rounded-[20px] p-3 flex flex-col items-center cursor-pointer transition-colors"
      style={{ backgroundColor: "var(--surface-1)" }}
    >
      <div className="relative w-[132px] h-[132px] mb-2.5">
        <Artwork
          artworkPath={item.artworkPath}
          accentColor={item.accentColor}
          alt={item.title}
          size="full"
          rounded="rounded-2xl"
        />

        {/* Top-right more menu */}
        <button
          type="button"
          onClick={(e) => {
            e.stopPropagation();
            setMenuOpen((v) => !v);
          }}
          className="absolute -right-2 -top-1 w-7 h-7 rounded-full flex items-center justify-center opacity-80 hover:opacity-100 cursor-pointer"
          style={{ color: "var(--text-primary)" }}
          title="Options"
        >
          <Icon name="more" size={18} />
        </button>

        {/* Bottom-right circular accent Play button */}
        <button
          type="button"
          onClick={(e) => {
            e.stopPropagation();
            if (item.trackIds.length > 0) {
              void playTrack(item.trackIds[0], item.trackIds);
            }
          }}
          className="absolute right-1.5 bottom-1.5 w-8 h-8 rounded-full flex items-center justify-center shadow-md transition-transform hover:scale-105 active:scale-95 cursor-pointer"
          style={{
            backgroundColor: "var(--accent)",
            color: "var(--on-accent)",
          }}
          title="Play"
        >
          <Icon name="play" size={18} />
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
              className="absolute right-0 top-7 z-40 w-44 py-1.5 rounded-2xl shadow-xl text-xs"
              style={{
                backgroundColor: "var(--surface-3)",
                border: "1px solid var(--outline)",
              }}
            >
              <button
                type="button"
                onClick={() => {
                  setMenuOpen(false);
                  onOpen();
                }}
                className="w-full px-3.5 py-2 text-left hover:opacity-80 cursor-pointer"
              >
                Open album
              </button>
              <button
                type="button"
                onClick={() => {
                  void enqueueTracks(item.trackIds, true);
                  setMenuOpen(false);
                }}
                className="w-full px-3.5 py-2 text-left hover:opacity-80 cursor-pointer"
              >
                Play next
              </button>
              <button
                type="button"
                onClick={() => {
                  void enqueueTracks(item.trackIds, false);
                  setMenuOpen(false);
                }}
                className="w-full px-3.5 py-2 text-left hover:opacity-80 cursor-pointer"
              >
                Add to queue
              </button>
              <button
                type="button"
                onClick={() => {
                  void togglePin(item.pinTrackId);
                  setMenuOpen(false);
                }}
                className="w-full px-3.5 py-2 text-left flex items-center gap-2 hover:opacity-80 cursor-pointer"
              >
                <Icon name={item.pinned ? "unpin" : "pin"} size={14} />
                <span>{item.pinned ? "Unpin from Home" : "Pin to Home"}</span>
              </button>
            </div>
          </>
        )}
      </div>

      <div className="w-full text-sm font-semibold text-center truncate">
        {item.title}
      </div>
      <div
        className="w-full text-xs text-center truncate mt-0.5"
        style={{ color: "var(--text-secondary)" }}
      >
        {item.subtitle}
      </div>
      {item.pinned && (
        <div
          className="mt-1 text-[10px] font-semibold uppercase tracking-wider text-center"
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
  onOpenAlbum,
}: {
  title: string;
  subtitle: string;
  items: ShelfCollection[];
  onOpenAlbum: (albumTitle: string) => void;
}) {
  const rowRef = useRef<HTMLDivElement>(null);

  const scrollRow = (dir: -1 | 1) => {
    const el = rowRef.current;
    if (!el) return;
    el.scrollBy({ left: dir * Math.max(360, el.clientWidth * 0.8), behavior: "smooth" });
  };

  if (items.length === 0) return null;

  return (
    <section className="space-y-3">
      <div className="flex items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-semibold tracking-tight">{title}</h2>
          <p className="text-xs mt-0.5" style={{ color: "var(--text-secondary)" }}>
            {subtitle}
          </p>
        </div>
        <div className="flex items-center gap-2 shrink-0">
          <button
            type="button"
            onClick={() => scrollRow(-1)}
            aria-label={`Scroll ${title} left`}
            className="w-9 h-9 rounded-full flex items-center justify-center transition-colors hover:opacity-85 cursor-pointer"
            style={{
              backgroundColor: "var(--surface-2)",
              color: "var(--text-primary)",
            }}
          >
            <Icon name="back" size={18} />
          </button>
          <button
            type="button"
            onClick={() => scrollRow(1)}
            aria-label={`Scroll ${title} right`}
            className="w-9 h-9 rounded-full flex items-center justify-center transition-colors hover:opacity-85 cursor-pointer"
            style={{
              backgroundColor: "var(--surface-2)",
              color: "var(--text-primary)",
            }}
          >
            <Icon name="back" size={18} className="rotate-180" />
          </button>
        </div>
      </div>

      <div
        ref={rowRef}
        className="flex items-stretch gap-3 overflow-x-auto no-scrollbar pb-1"
      >
        {items.map((item) => (
          <ShelfCard
            key={item.id}
            item={item}
            onOpen={() => onOpenAlbum(item.title)}
          />
        ))}
      </div>
    </section>
  );
}

export function HomeScreen() {
  const tracks = useLibraryStore((s) => s.tracks);
  const pinnedTrackIds = useLibraryStore((s) => s.pinnedTrackIds);
  const scanProgress = useLibraryStore((s) => s.scanProgress);
  const setActiveNav = useLibraryStore((s) => s.setActiveNav);
  const setLibraryTab = useLibraryStore((s) => s.setLibraryTab);
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

  // Group tracks into albums for shelves (matching HomeScreen.kt)
  const albumGroups = useMemo(() => {
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
    const all = [...map.values()].filter((g) => {
      if (!q) return true;
      return (
        g.title.toLowerCase().includes(q) ||
        g.subtitle.toLowerCase().includes(q) ||
        g.tracks.some((t) => t.title.toLowerCase().includes(q))
      );
    });

    const toShelfItem = (g: (typeof all)[number]): ShelfCollection => ({
      id: g.id,
      title: g.title,
      subtitle: g.subtitle,
      artworkPath: g.artworkPath,
      accentColor: g.accentColor,
      trackIds: g.tracks.map((t) => t.id),
      pinned: g.pinned,
      pinTrackId: g.tracks[0].id,
    });

    return {
      pinned: all.filter((g) => g.pinned).map(toShelfItem),
      recent: all
        .filter((g) => g.lastPlayedAt > 0)
        .sort((a, b) => b.lastPlayedAt - a.lastPlayedAt)
        .slice(0, 20)
        .map(toShelfItem),
      added: [...all]
        .sort((a, b) => b.latestAddedAt - a.latestAddedAt)
        .slice(0, 20)
        .map(toShelfItem),
    };
  }, [tracks, pinnedTrackIds, localQuery]);

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

  const handleOpenAlbum = (albumTitle: string) => {
    setSearchQuery(albumTitle);
    setLibraryTab("songs");
    setActiveNav("library");
  };

  return (
    <div className="space-y-6">
      {/* Content Header (v0.26.1 HomeScreen): <- on left; circular Search + pill Add folder on right */}
      <div className="flex items-center justify-between gap-4">
        <div className="flex items-center gap-3 min-w-0">
          <button
            type="button"
            onClick={() => {
              if (searchOpen) {
                setSearchOpen(false);
                setLocalQuery("");
              }
            }}
            aria-label="Back"
            className="w-9 h-9 rounded-full flex items-center justify-center transition-opacity hover:opacity-80 cursor-pointer"
            style={{ color: "var(--text-primary)" }}
          >
            <Icon name="back" size={20} />
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
            value={localQuery}
            onChange={(e) => setLocalQuery(e.target.value)}
            placeholder="Search your library"
            className="bg-transparent text-sm outline-none flex-1 placeholder:text-[var(--text-muted)]"
          />
          {localQuery && (
            <button
              type="button"
              onClick={() => setLocalQuery("")}
              className="cursor-pointer"
              style={{ color: "var(--text-muted)" }}
            >
              <Icon name="close" size={16} />
            </button>
          )}
        </div>
      )}

      {/* PLAYING NOW full-width hero card */}
      {continuationTrack ? (
        <section
          className="w-full rounded-3xl px-5 py-4 flex items-center gap-5 adaptive-transition"
          style={{
            background:
              "linear-gradient(90deg, var(--surface-2) 0%, var(--surface-1) 100%)",
          }}
        >
          <Artwork
            artworkPath={continuationTrack.artworkPath}
            accentColor={continuationTrack.accentColor}
            alt={continuationTrack.title}
            size="md"
            rounded="rounded-xl"
            className="!w-16 !h-16"
          />

          <div className="min-w-0 flex-1 space-y-0.5">
            <div
              className="text-[11px] font-semibold uppercase tracking-wider"
              style={{ color: "var(--accent)" }}
            >
              {currentTrack && isPlaying ? "PLAYING NOW" : "CONTINUE LISTENING"}
            </div>
            <div className="text-lg font-bold tracking-tight truncate">
              {continuationTrack.title}
            </div>
            <div
              className="text-xs truncate"
              style={{ color: "var(--text-secondary)" }}
            >
              {continuationTrack.artist}
            </div>
            {continuationTrack.album && (
              <div
                className="text-[11px] truncate"
                style={{ color: "var(--text-muted)" }}
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
            className="w-12 h-12 rounded-full flex items-center justify-center shrink-0 shadow-md transition-transform hover:scale-105 active:scale-95 cursor-pointer"
            style={{
              backgroundColor: "var(--accent)",
              color: "var(--on-accent)",
            }}
          >
            <Icon name={currentTrack && isPlaying ? "pause" : "play"} size={24} />
          </button>
        </section>
      ) : (
        <section
          className="w-full rounded-3xl p-8 space-y-2.5"
          style={{ backgroundColor: "var(--surface-1)" }}
        >
          <Icon name="music" size={34} style={{ color: "var(--accent)" }} />
          <h2 className="text-xl font-semibold">Make yourself at home</h2>
          <p className="text-sm" style={{ color: "var(--text-secondary)" }}>
            Add a music folder above. Your albums, recent listening and pinned
            collections will appear here.
          </p>
        </section>
      )}

      {/* Shelves */}
      <HomeShelf
        title="Pinned"
        subtitle="Your favorites, within reach"
        items={albumGroups.pinned}
        onOpenAlbum={handleOpenAlbum}
      />

      <HomeShelf
        title="Recently played"
        subtitle="Pick up an album again"
        items={albumGroups.recent}
        onOpenAlbum={handleOpenAlbum}
      />

      <HomeShelf
        title="Recently added"
        subtitle="The latest albums in your library"
        items={albumGroups.added}
        onOpenAlbum={handleOpenAlbum}
      />
    </div>
  );
}
