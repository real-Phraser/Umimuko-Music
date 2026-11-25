import { useState } from "react";
import { useLibraryStore } from "../store/useLibraryStore";
import { usePlayerStore } from "../store/usePlayerStore";
import type { Track } from "../types/ipc";
import { formatDuration } from "../utils/format";
import { Artwork } from "./Artwork";
import { Icon } from "./Icon";

interface TrackRowProps {
  track: Track;
  index: number;
  queueIds?: string[];
  showAlbum?: boolean;
  onOpenAlbum?: (album: string) => void;
  onOpenArtist?: (artist: string) => void;
}

export function TrackRow({
  track,
  queueIds,
  showAlbum = true,
  onOpenAlbum,
  onOpenArtist,
}: TrackRowProps) {
  const [menuOpen, setMenuOpen] = useState(false);

  const currentTrackId = usePlayerStore((s) => s.playback.currentTrackId);
  const status = usePlayerStore((s) => s.playback.status);
  const playTrack = usePlayerStore((s) => s.playTrack);
  const togglePlayPause = usePlayerStore((s) => s.togglePlayPause);
  const enqueueTracks = usePlayerStore((s) => s.enqueueTracks);

  const pinnedTrackIds = useLibraryStore((s) => s.pinnedTrackIds);
  const playlists = useLibraryStore((s) => s.playlists);
  const toggleLike = useLibraryStore((s) => s.toggleLike);
  const togglePin = useLibraryStore((s) => s.togglePin);
  const addTrackToPlaylist = useLibraryStore((s) => s.addTrackToPlaylist);
  const openTagEditor = useLibraryStore((s) => s.openTagEditor);

  const isActive = currentTrackId === track.id;
  const isPlaying = isActive && status === "playing";
  const isPinned = pinnedTrackIds.includes(track.id);

  const handlePlay = () => {
    if (isActive) {
      void togglePlayPause();
    } else {
      void playTrack(track.id, queueIds);
    }
  };

  return (
    <div
      onClick={handlePlay}
      onContextMenu={(e) => {
        e.preventDefault();
        setMenuOpen(true);
      }}
      className="group relative flex items-center gap-3 p-[10px] rounded-[16px] transition-colors cursor-pointer hover:brightness-105"
      style={{
        backgroundColor: isActive ? "var(--accent-container)" : "var(--surface-1)",
      }}
    >
      {/* 44dp Artwork with 11dp radius (Artwork.kt) */}
      <Artwork
        artworkPath={track.artworkPath}
        accentColor={track.accentColor}
        alt={track.title}
        size="sm"
        rounded="rounded-[11px]"
        className="!w-[44px] !h-[44px]"
      />

      {/* Title, Artist · Album, Duration (LibraryPanel.kt lines 880-888) */}
      <div className="min-w-0 flex-1 flex flex-col gap-[2px]">
        <div
          className="text-[16px] leading-[24px] font-semibold truncate"
          style={{ color: isActive ? "var(--accent)" : "var(--text-primary)" }}
        >
          {track.title}
        </div>
        <div
          className="text-[11px] leading-[16px] font-normal truncate"
          style={{ color: "var(--text-secondary)" }}
        >
          <span>{track.artist || "Local audio"}</span>
          {showAlbum && track.album && (
            <>
              <span className="mx-2 opacity-62">·</span>
              <span className="opacity-62">{track.album}</span>
            </>
          )}
        </div>
        {track.durationMs > 0 && (
          <div
            className="text-[11px] leading-[16px] tabular-nums opacity-80"
            style={{ color: "var(--text-secondary)" }}
          >
            {formatDuration(track.durationMs)}
          </div>
        )}
      </div>

      {/* Active playback glyph (LibraryPanel.kt line 889) */}
      {isActive && (
        <Icon
          name={isPlaying ? "levels" : "pause"}
          size={24}
          style={{ color: "var(--accent)" }}
        />
      )}

      {/* Right actions: Heart + More menu (LibraryPanel.kt lines 890-912) */}
      <div
        className="flex items-center shrink-0"
        onClick={(e) => e.stopPropagation()}
      >
        <button
          type="button"
          onClick={() => void toggleLike(track.id)}
          className="w-10 h-10 rounded-full flex items-center justify-center transition-opacity hover:bg-[var(--surface-variant)] cursor-pointer"
          style={{
            color: track.liked ? "var(--accent)" : "var(--text-primary)",
          }}
          aria-label={track.liked ? `Unlike ${track.title}` : `Like ${track.title}`}
          title={track.liked ? "Unlike" : "Like"}
        >
          <Icon name={track.liked ? "heart" : "liked"} size={24} />
        </button>

        <div className="relative">
          <button
            type="button"
            onClick={() => setMenuOpen((v) => !v)}
            className="w-10 h-10 rounded-full flex items-center justify-center hover:bg-[var(--surface-variant)] cursor-pointer"
            style={{ color: "var(--text-primary)" }}
            aria-label="Track options"
            title="Track options"
          >
            <Icon name="more" size={24} />
          </button>

          {menuOpen && (
            <>
              <div
                className="fixed inset-0 z-30"
                onClick={() => setMenuOpen(false)}
              />
              <div
                className="absolute right-0 top-10 z-40 w-56 py-1.5 rounded-2xl shadow-xl text-xs"
                style={{
                  backgroundColor: "var(--surface-3)",
                  border: "1px solid var(--outline)",
                }}
              >
                <button
                  type="button"
                  onClick={() => {
                    void enqueueTracks([track.id], true);
                    setMenuOpen(false);
                  }}
                  className="w-full px-3.5 py-2 text-left flex items-center gap-2.5 hover:bg-[var(--surface-variant)] cursor-pointer"
                >
                  <Icon name="next" size={18} />
                  <span>Play next</span>
                </button>
                <button
                  type="button"
                  onClick={() => {
                    void enqueueTracks([track.id], false);
                    setMenuOpen(false);
                  }}
                  className="w-full px-3.5 py-2 text-left flex items-center gap-2.5 hover:bg-[var(--surface-variant)] cursor-pointer"
                >
                  <Icon name="queue" size={18} />
                  <span>Add to queue</span>
                </button>
                <button
                  type="button"
                  onClick={() => {
                    void togglePin(track.id);
                    setMenuOpen(false);
                  }}
                  className="w-full px-3.5 py-2 text-left flex items-center gap-2.5 hover:bg-[var(--surface-variant)] cursor-pointer"
                >
                  <Icon name={isPinned ? "unpin" : "pin"} size={18} />
                  <span>{isPinned ? "Unpin from Home" : "Pin to Home"}</span>
                </button>
                <button
                  type="button"
                  onClick={() => {
                    openTagEditor(track);
                    setMenuOpen(false);
                  }}
                  className="w-full px-3.5 py-2 text-left flex items-center gap-2.5 hover:bg-[var(--surface-variant)] cursor-pointer"
                >
                  <Icon name="info" size={18} />
                  <span>Edit tags</span>
                </button>
                {onOpenAlbum && (
                  <button
                    type="button"
                    onClick={() => {
                      onOpenAlbum(track.album || "Unknown Album");
                      setMenuOpen(false);
                    }}
                    className="w-full px-3.5 py-2 text-left flex items-center gap-2.5 hover:bg-[var(--surface-variant)] cursor-pointer"
                  >
                    <Icon name="album" size={18} />
                    <span className="truncate">
                      Go to album: {track.album || "Unknown album"}
                    </span>
                  </button>
                )}
                {onOpenArtist && (
                  <button
                    type="button"
                    onClick={() => {
                      onOpenArtist(track.artist || "Unknown Artist");
                      setMenuOpen(false);
                    }}
                    className="w-full px-3.5 py-2 text-left flex items-center gap-2.5 hover:bg-[var(--surface-variant)] cursor-pointer"
                  >
                    <Icon name="artist" size={18} />
                    <span className="truncate">
                      Go to artist: {track.artist || "Unknown artist"}
                    </span>
                  </button>
                )}
                {playlists.length > 0 && (
                  <div
                    className="mt-1 pt-1 border-t"
                    style={{ borderColor: "var(--outline)" }}
                  >
                    <div
                      className="px-3.5 py-1 text-[10px] uppercase tracking-wider flex items-center gap-2"
                      style={{ color: "var(--text-muted)" }}
                    >
                      <Icon name="playlist_add" size={14} />
                      <span>Add to playlist</span>
                    </div>
                    {playlists.map((pl) => (
                      <button
                        key={pl.id}
                        type="button"
                        onClick={() => {
                          void addTrackToPlaylist(pl.id, track.id);
                          setMenuOpen(false);
                        }}
                        className="w-full px-3.5 py-1.5 text-left truncate hover:bg-[var(--surface-variant)] cursor-pointer"
                      >
                        {pl.name}
                      </button>
                    ))}
                  </div>
                )}
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
