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

export function TrackRow({ track, index, queueIds, showAlbum = true }: TrackRowProps) {
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
      onDoubleClick={handlePlay}
      className="group relative flex items-center gap-3 px-3 py-2 rounded-2xl transition-colors"
      style={{
        backgroundColor: isActive ? "var(--accent-container)" : "transparent",
      }}
    >
      {/* Index / Play toggle */}
      <button
        type="button"
        onClick={handlePlay}
        className="w-7 h-7 shrink-0 flex items-center justify-center rounded-full text-xs font-medium cursor-pointer"
        style={{ color: isActive ? "var(--accent)" : "var(--text-muted)" }}
        title={isPlaying ? "Pause" : "Play"}
      >
        <span className="group-hover:hidden">
          {isPlaying ? <Icon name="levels" size={16} /> : index + 1}
        </span>
        <span className="hidden group-hover:inline-flex">
          <Icon name={isPlaying ? "pause" : "play"} size={18} />
        </span>
      </button>

      {/* Artwork & Title */}
      <div className="flex items-center gap-3 min-w-0 flex-1">
        <Artwork
          artworkPath={track.artworkPath}
          accentColor={track.accentColor}
          alt={track.title}
          size="xs"
          rounded="rounded-lg"
        />
        <div className="min-w-0 flex-1">
          <div
            className="text-sm font-medium truncate"
            style={{ color: isActive ? "var(--accent)" : "var(--text-primary)" }}
          >
            {track.title}
          </div>
          <div className="text-xs truncate" style={{ color: "var(--text-secondary)" }}>
            {track.artist}
          </div>
        </div>
      </div>

      {/* Album */}
      {showAlbum && (
        <div
          className="hidden md:block w-44 text-xs truncate"
          style={{ color: "var(--text-secondary)" }}
        >
          {track.album}
        </div>
      )}

      {/* Actions */}
      <div className="flex items-center gap-1">
        <button
          type="button"
          onClick={() => void toggleLike(track.id)}
          className="p-1.5 rounded-full transition-opacity cursor-pointer"
          style={{
            color: track.liked ? "var(--accent)" : "var(--text-muted)",
            opacity: track.liked ? 1 : undefined,
          }}
          title={track.liked ? "Unlike" : "Like"}
        >
          <Icon name={track.liked ? "liked" : "heart"} size={17} />
        </button>

        <button
          type="button"
          onClick={() => void togglePin(track.id)}
          className="hidden sm:inline-flex p-1.5 rounded-full opacity-0 group-hover:opacity-100 transition-opacity cursor-pointer"
          style={{
            color: isPinned ? "var(--accent)" : "var(--text-muted)",
            opacity: isPinned ? 1 : undefined,
          }}
          title={isPinned ? "Unpin from Home" : "Pin to Home"}
        >
          <Icon name={isPinned ? "unpin" : "pin"} size={16} />
        </button>

        <div
          className="w-12 text-right text-xs tabular-nums"
          style={{ color: "var(--text-muted)" }}
        >
          {formatDuration(track.durationMs)}
        </div>

        <div className="relative">
          <button
            type="button"
            onClick={() => setMenuOpen((v) => !v)}
            className="p-1.5 rounded-full opacity-60 group-hover:opacity-100 cursor-pointer"
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
                className="absolute right-0 top-8 z-40 w-48 py-1.5 rounded-2xl shadow-xl text-xs"
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
                  <span>Play Next</span>
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
                  <span>Add to Queue</span>
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
                  <span>Edit Tags</span>
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
                      Add to Playlist
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
