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
  const setFullPlayerTab = usePlayerStore((s) => s.setFullPlayerTab);

  const toggleLike = useLibraryStore((s) => s.toggleLike);
  const openDialog = useLibraryStore((s) => s.openDialog);

  const track = playback.currentTrack;
  const isPlaying = playback.status === "playing";
  const duration = playback.durationMs || track?.durationMs || 0;
  const progressPct =
    duration > 0 ? Math.min(100, (playback.positionMs / duration) * 100) : 0;

  return (
    <footer
      className="h-22 shrink-0 px-4 border-t flex items-center justify-between gap-4 backdrop-blur-xl adaptive-transition"
      style={{
        backgroundColor: "var(--player-glass)",
        borderColor: "var(--outline)",
      }}
    >
      {/* Left: Track Info */}
      <div className="flex items-center gap-3 min-w-0 w-72">
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
            size="md"
            rounded="rounded-xl"
          />
          <div className="min-w-0">
            <div className="text-sm font-medium truncate">
              {track?.title ?? "Nothing playing"}
            </div>
            <div
              className="text-xs truncate"
              style={{ color: "var(--text-secondary)" }}
            >
              {track ? `${track.artist} • ${track.album}` : "Select a song to start"}
            </div>
          </div>
        </button>

        {track && (
          <button
            type="button"
            onClick={() => void toggleLike(track.id)}
            className="p-1.5 rounded-full shrink-0 cursor-pointer"
            style={{ color: track.liked ? "var(--accent)" : "var(--text-muted)" }}
            title={track.liked ? "Unlike" : "Like"}
          >
            <Icon name={track.liked ? "liked" : "heart"} size={18} />
          </button>
        )}
      </div>

      {/* Center: Transport + Seek Bar */}
      <div className="flex-1 max-w-xl flex flex-col items-center gap-1.5">
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={() => void toggleShuffle()}
            className="p-1.5 rounded-full transition-colors cursor-pointer"
            style={{
              color: playback.shuffle ? "var(--accent)" : "var(--text-secondary)",
            }}
            title="Shuffle"
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
            <Icon name="previous" size={22} />
          </button>

          <button
            type="button"
            onClick={() => void togglePlayPause()}
            className="w-10 h-10 rounded-full flex items-center justify-center shadow-md transition-transform active:scale-95 cursor-pointer"
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
            <Icon name="next" size={22} />
          </button>

          <button
            type="button"
            onClick={() => void cycleRepeat()}
            className="p-1.5 rounded-full transition-colors cursor-pointer"
            style={{
              color:
                playback.repeat !== "off" ? "var(--accent)" : "var(--text-secondary)",
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
            className="text-[11px] tabular-nums w-10 text-right"
            style={{ color: "var(--text-muted)" }}
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
                height: 4,
                borderRadius: 999,
              }}
            />
          </div>
          <span
            className="text-[11px] tabular-nums w-10"
            style={{ color: "var(--text-muted)" }}
          >
            {formatDuration(duration)}
          </span>
        </div>
      </div>

      {/* Right: Volume & Utility Controls */}
      <div className="flex items-center justify-end gap-1.5 w-72">
        <button
          type="button"
          onClick={() => void toggleMute()}
          className="p-1.5 rounded-full cursor-pointer"
          style={{ color: "var(--text-secondary)" }}
          title={playback.muted ? "Unmute" : "Mute"}
        >
          <Icon name={playback.muted || playback.volume === 0 ? "muted" : "volume"} size={18} />
        </button>

        <input
          type="range"
          min={0}
          max={1}
          step={0.01}
          value={playback.muted ? 0 : playback.volume}
          onChange={(e) => void setVolume(Number(e.target.value))}
          className="umimuko-slider w-20 mr-1"
          aria-label="Volume"
        />

        <button
          type="button"
          onClick={() => openDialog("sleepTimer")}
          className="relative p-1.5 rounded-full cursor-pointer"
          style={{
            color:
              playback.sleepTimerRemainingMs !== null
                ? "var(--accent)"
                : "var(--text-secondary)",
          }}
          title="Sleep Timer"
        >
          <Icon name="timer" size={18} />
        </button>

        <button
          type="button"
          onClick={() => openDialog("equalizer")}
          className="p-1.5 rounded-full cursor-pointer"
          style={{ color: "var(--text-secondary)" }}
          title="Equalizer"
        >
          <Icon name="levels" size={18} />
        </button>

        <button
          type="button"
          onClick={() => {
            setFullPlayerTab("lyrics");
            setFullPlayerOpen(!fullPlayerOpen);
          }}
          className="p-1.5 rounded-full cursor-pointer"
          style={{
            color: fullPlayerOpen ? "var(--accent)" : "var(--text-secondary)",
          }}
          title="Lyrics & Full Player"
        >
          <Icon name="lyrics" size={18} />
        </button>

        <button
          type="button"
          onClick={() => setQueueDrawerOpen(!queueDrawerOpen)}
          className="p-1.5 rounded-full cursor-pointer"
          style={{
            color: queueDrawerOpen ? "var(--accent)" : "var(--text-secondary)",
          }}
          title="Queue"
        >
          <Icon name="queue" size={18} />
        </button>

        <button
          type="button"
          onClick={() => void api.toggleMiniPlayer(true)}
          className="p-1.5 rounded-full cursor-pointer"
          style={{ color: "var(--text-secondary)" }}
          title="Open Mini Player"
        >
          <Icon name="bubble" size={18} />
        </button>

        <button
          type="button"
          onClick={() => setFullPlayerOpen(!fullPlayerOpen)}
          className="p-1.5 rounded-full cursor-pointer"
          style={{ color: "var(--text-secondary)" }}
          title="Expand Player"
        >
          <Icon name="expand" size={18} />
        </button>
      </div>
    </footer>
  );
}
