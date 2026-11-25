import { useState } from "react";
import { api } from "../api/tauri";
import { useLibraryStore } from "../store/useLibraryStore";
import { usePlayerStore } from "../store/usePlayerStore";
import { formatDuration } from "../utils/format";
import { Artwork } from "./Artwork";
import { Icon } from "./Icon";

export function PlayerBar() {
  const playback = usePlayerStore((s) => s.playback);
  const queueDrawerOpen = usePlayerStore((s) => s.queueDrawerOpen);
  const fullPlayerOpen = usePlayerStore((s) => s.fullPlayerOpen);

  const togglePlayPause = usePlayerStore((s) => s.togglePlayPause);
  const nextTrack = usePlayerStore((s) => s.nextTrack);
  const prevTrack = usePlayerStore((s) => s.prevTrack);
  const seek = usePlayerStore((s) => s.seek);
  const setVolume = usePlayerStore((s) => s.setVolume);
  const toggleMute = usePlayerStore((s) => s.toggleMute);
  const toggleShuffle = usePlayerStore((s) => s.toggleShuffle);
  const cycleRepeat = usePlayerStore((s) => s.cycleRepeat);
  const setQueueDrawerOpen = usePlayerStore((s) => s.setQueueDrawerOpen);
  const setFullPlayerOpen = usePlayerStore((s) => s.setFullPlayerOpen);

  const playlists = useLibraryStore((s) => s.playlists);
  const addTrackToPlaylist = useLibraryStore((s) => s.addTrackToPlaylist);
  const toggleLike = useLibraryStore((s) => s.toggleLike);

  const [playlistMenuOpen, setPlaylistMenuOpen] = useState(false);

  const track = playback.currentTrack;
  const isPlaying = playback.status === "playing";
  const duration = playback.durationMs || track?.durationMs || 0;
  const progressPct =
    duration > 0 ? Math.min(100, (playback.positionMs / duration) * 100) : 0;
  const effectiveVolume = playback.muted ? 0 : playback.volume;
  const volumePct = Math.round(effectiveVolume * 100);

  return (
    <footer
      className="h-[82px] shrink-0 px-5 flex items-center justify-between gap-4 adaptive-transition"
      style={{
        backgroundColor: "var(--surface-1)",
        borderTop: "1px solid var(--outline)",
      }}
    >
      {/* Left: 44x44 Artwork + Title + Artist • Album */}
      <div className="flex items-center gap-3 min-w-0 w-[28%]">
        <button
          type="button"
          onClick={() => track && setFullPlayerOpen(true)}
          disabled={!track}
          className="flex items-center gap-3 min-w-0 text-left cursor-pointer disabled:cursor-default"
        >
          <Artwork
            artworkPath={track?.artworkPath}
            accentColor={track?.accentColor}
            alt={track?.title ?? "No track"}
            size="sm"
            rounded="rounded-lg"
          />
          <div className="min-w-0">
            <div className="text-sm font-semibold truncate">
              {track?.title ?? "Pick something to play"}
            </div>
            <div
              className="text-xs truncate mt-0.5"
              style={{ color: "var(--text-secondary)" }}
            >
              {track ? `${track.artist}  •  ${track.album}` : "Your music, your space"}
            </div>
          </div>
        </button>
      </div>

      {/* Center: Shuffle, Prev, circular accent Play/Pause, Next, Repeat over Seek Bar */}
      <div className="flex-1 max-w-[520px] flex flex-col items-center gap-1.5">
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={() => void toggleShuffle()}
            className="p-1.5 rounded-full transition-colors cursor-pointer"
            style={{
              color: playback.shuffle ? "var(--accent)" : "var(--text-primary)",
            }}
            title={playback.shuffle ? "Shuffle on" : "Shuffle off"}
          >
            <Icon name="shuffle" size={18} />
          </button>

          <button
            type="button"
            onClick={() => void prevTrack()}
            className="p-1.5 rounded-full transition-colors cursor-pointer"
            style={{ color: "var(--text-primary)" }}
            title="Previous"
          >
            <Icon name="previous" size={20} />
          </button>

          <button
            type="button"
            onClick={() => void togglePlayPause()}
            className="w-10 h-10 rounded-full flex items-center justify-center shadow-md transition-transform hover:scale-105 active:scale-95 cursor-pointer"
            style={{
              backgroundColor: "var(--accent)",
              color: "var(--on-accent)",
            }}
            title={isPlaying ? "Pause" : "Play"}
          >
            <Icon name={isPlaying ? "pause" : "play"} size={22} />
          </button>

          <button
            type="button"
            onClick={() => void nextTrack()}
            className="p-1.5 rounded-full transition-colors cursor-pointer"
            style={{ color: "var(--text-primary)" }}
            title="Next"
          >
            <Icon name="next" size={20} />
          </button>

          <button
            type="button"
            onClick={() => void cycleRepeat()}
            className="p-1.5 rounded-full transition-colors cursor-pointer"
            style={{
              color:
                playback.repeat !== "off" ? "var(--accent)" : "var(--text-primary)",
            }}
            title={`Repeat: ${playback.repeat}`}
          >
            <Icon
              name={playback.repeat === "one" ? "repeat_one" : "repeat"}
              size={18}
            />
          </button>
        </div>

        <div className="w-full flex items-center gap-2.5">
          <span
            className="text-[11px] tabular-nums w-9 text-right"
            style={{ color: "var(--text-secondary)" }}
          >
            {formatDuration(playback.positionMs)}
          </span>
          <div className="relative flex-1 flex items-center">
            <input
              type="range"
              min={0}
              max={Math.max(1, duration)}
              value={Math.min(playback.positionMs, duration)}
              onChange={(e) => void seek(Number(e.target.value))}
              className="umimuko-slider w-full"
              style={{
                background: `linear-gradient(90deg, var(--accent) ${progressPct}%, var(--surface-4) ${progressPct}%)`,
                height: 3,
                borderRadius: 999,
              }}
            />
          </div>
          <span
            className="text-[11px] tabular-nums w-9"
            style={{ color: "var(--text-secondary)" }}
          >
            {formatDuration(duration)}
          </span>
        </div>
      </div>

      {/* Right: Heart, Add-to-playlist, Queue, Mini-player, Fullscreen, Volume icon + slider + % */}
      <div className="flex items-center justify-end gap-1.5 w-[30%]">
        <button
          type="button"
          disabled={!track}
          onClick={() => track && void toggleLike(track.id)}
          className="p-1.5 rounded-full cursor-pointer disabled:opacity-40"
          style={{
            color: track?.liked ? "var(--accent)" : "var(--text-primary)",
          }}
          title={track?.liked ? "Unlike current track" : "Like current track"}
        >
          <Icon name={track?.liked ? "liked" : "heart"} size={18} />
        </button>

        <div className="relative">
          <button
            type="button"
            disabled={!track}
            onClick={() => setPlaylistMenuOpen((v) => !v)}
            className="p-1.5 rounded-full cursor-pointer disabled:opacity-40"
            style={{ color: "var(--text-primary)" }}
            title="Add current track to playlist"
          >
            <Icon name="playlist_add" size={18} />
          </button>

          {playlistMenuOpen && track && (
            <>
              <div
                className="fixed inset-0 z-30"
                onClick={() => setPlaylistMenuOpen(false)}
              />
              <div
                className="absolute right-0 bottom-10 z-40 w-48 py-1.5 rounded-2xl shadow-xl text-xs"
                style={{
                  backgroundColor: "var(--surface-3)",
                  border: "1px solid var(--outline)",
                }}
              >
                <div
                  className="px-3.5 py-1 text-[10px] uppercase tracking-wider"
                  style={{ color: "var(--text-muted)" }}
                >
                  Add to playlist
                </div>
                {playlists.length === 0 ? (
                  <div
                    className="px-3.5 py-2"
                    style={{ color: "var(--text-secondary)" }}
                  >
                    No playlists yet
                  </div>
                ) : (
                  playlists.map((pl) => (
                    <button
                      key={pl.id}
                      type="button"
                      onClick={() => {
                        void addTrackToPlaylist(pl.id, track.id);
                        setPlaylistMenuOpen(false);
                      }}
                      className="w-full px-3.5 py-1.5 text-left truncate hover:opacity-80 cursor-pointer"
                    >
                      {pl.name}
                    </button>
                  ))
                )}
              </div>
            </>
          )}
        </div>

        <button
          type="button"
          onClick={() => setQueueDrawerOpen(!queueDrawerOpen)}
          className="p-1.5 rounded-full cursor-pointer"
          style={{
            color: queueDrawerOpen ? "var(--accent)" : "var(--text-primary)",
          }}
          title="Open playback queue"
        >
          <Icon name="queue" size={18} />
        </button>

        <button
          type="button"
          onClick={() => void api.toggleMiniPlayer(true)}
          className="p-1.5 rounded-full cursor-pointer"
          style={{ color: "var(--text-primary)" }}
          title="Collapse to floating player"
        >
          <Icon name="bubble" size={18} />
        </button>

        <button
          type="button"
          disabled={!track}
          onClick={() => setFullPlayerOpen(!fullPlayerOpen)}
          className="p-1.5 rounded-full cursor-pointer disabled:opacity-40"
          style={{
            color: fullPlayerOpen ? "var(--accent)" : "var(--text-primary)",
          }}
          title="Open full-screen player"
        >
          <Icon name="expand" size={18} />
        </button>

        <button
          type="button"
          onClick={() => void toggleMute()}
          className="p-1.5 ml-1 rounded-full cursor-pointer"
          style={{ color: "var(--text-secondary)" }}
          title={playback.muted ? "Unmute" : "Mute"}
        >
          <Icon
            name={playback.muted || playback.volume === 0 ? "muted" : "volume"}
            size={18}
          />
        </button>

        <input
          type="range"
          min={0}
          max={1}
          step={0.01}
          value={effectiveVolume}
          onChange={(e) => void setVolume(Number(e.target.value))}
          className="umimuko-slider w-20"
          style={{
            background: `linear-gradient(90deg, var(--accent) ${volumePct}%, var(--surface-4) ${volumePct}%)`,
            height: 3,
            borderRadius: 999,
          }}
          aria-label="Volume"
        />

        <span
          className="text-[11px] tabular-nums w-8 text-right"
          style={{ color: "var(--text-secondary)" }}
        >
          {volumePct}%
        </span>
      </div>
    </footer>
  );
}
