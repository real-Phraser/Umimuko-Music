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
}

export function TrackRow({ track, queueIds, showAlbum = true }: TrackRowProps) {
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
      className="group relative flex items-center gap-3.5 px-3.5 py-2.5 rounded-2xl transition-colors cursor-pointer"
      style={{
        backgroundColor: isActive ? "var(--accent-container)" : "var(--surface-1)",
      }}
    >
      {/* Artwork */}
      <Artwork
        artworkPath={track.artworkPath}
        accentColor={track.accentColor}
        alt={track.title}
        size="sm"
        rounded="rounded-lg"
      />

      {/* Title, Artist • Album, Duration (matching v0.26.1 TrackRow) */}
      <div className="min-w-0 flex-1">
        <div
          className="text-sm font-semibold truncate flex items-center gap-2"
          style={{ color: isActive ? "var(--accent)" : "var(--text-primary)" }}
        >
          <span className="truncate">{track.title}</span>
          {isPlaying && <Icon name="levels" size={14} style={{ color: "var(--accent)" }} />}
        </div>
        <div
          className="text-xs truncate mt-0.5"
          style={{ color: "var(--text-secondary)" }}
        >
          {track.artist}
          {showAlbum && track.album ? `  •  ${track.album}` : ""}
        </div>
        <div
          className="text-[11px] tabular-nums mt-0.5"
          style={{ color: "var(--text-muted)" }}
        >
          {formatDuration(track.durationMs)}
        </div>
      </div>

      {/* Right actions: Heart + More menu */}
      <div
        className="flex items-center gap-1 shrink-0"
        onClick={(e) => e.stopPropagation()}
      >
        <button
          type="button"
          onClick={() => void toggleLike(track.id)}
          className="p-2 rounded-full transition-opacity hover:opacity-80 cursor-pointer"
          style={{
            color: track.liked ? "var(--accent)" : "var(--text-muted)",
          }}
          title={track.liked ? "Unlike" : "Like"}
        >
          <Icon name={track.liked ? "heart" : "liked"} size={18} />
        </button>

        <div className="relative">
          <button
            type="button"
            onClick={() => setMenuOpen((v) => !v)}
            className="p-2 rounded-full hover:opacity-80 cursor-pointer"
            style={{ color: "var(--text-secondary)" }}
            title="Track options"
          >
            <Icon name="more" size={18} />
          </button>

          {menuOpen && (
            <>
              <div
                className="fixed inset-0 z-30"
                onClick={() => setMenuOpen(false)}
              />
              <div
                className="absolute right-0 top-9 z-40 w-48 py-1.5 rounded-2xl shadow-xl text-xs"
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
                  className="w-full px-3.5 py-2 text-left flex items-center gap-2.5 hover:opacity-80 cursor-pointer"
                >
                  <Icon name="queue" size={15} />
                  <span>Play next</span>
                </button>
                <button
                  type="button"
                  onClick={() => {
                    void enqueueTracks([track.id], false);
                    setMenuOpen(false);
                  }}
                  className="w-full px-3.5 py-2 text-left flex items-center gap-2.5 hover:opacity-80 cursor-pointer"
                >
                  <Icon name="playlist_add" size={15} />
                  <span>Add to queue</span>
                </button>
                <button
                  type="button"
                  onClick={() => {
                    void togglePin(track.id);
                    setMenuOpen(false);
                  }}
                  className="w-full px-3.5 py-2 text-left flex items-center gap-2.5 hover:opacity-80 cursor-pointer"
                >
                  <Icon name={isPinned ? "unpin" : "pin"} size={15} />
                  <span>{isPinned ? "Unpin from Home" : "Pin to Home"}</span>
                </button>
                <button
                  type="button"
                  onClick={() => {
                    openTagEditor(track);
                    setMenuOpen(false);
                  }}
                  className="w-full px-3.5 py-2 text-left flex items-center gap-2.5 hover:opacity-80 cursor-pointer"
                >
                  <Icon name="info" size={15} />
                  <span>Edit tags</span>
                </button>
                {playlists.length > 0 && (
                  <div
                    className="mt-1 pt-1 border-t"
                    style={{ borderColor: "var(--outline)" }}
                  >
                    <div
                      className="px-3.5 py-1 text-[10px] uppercase tracking-wider"
                      style={{ color: "var(--text-muted)" }}
                    >
                      Add to playlist
                    </div>
                    {playlists.map((pl) => (
                      <button
                        key={pl.id}
                        type="button"
                        onClick={() => {
                          void addTrackToPlaylist(pl.id, track.id);
                          setMenuOpen(false);
                        }}
                        className="w-full px-3.5 py-1.5 text-left truncate hover:opacity-80 cursor-pointer"
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
