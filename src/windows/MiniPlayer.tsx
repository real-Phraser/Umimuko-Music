import { useEffect } from "react";
import { api } from "../api/tauri";
import { Artwork } from "../components/Artwork";
import { Icon } from "../components/Icon";
import { usePlayerStore } from "../store/usePlayerStore";
import { useSettingsStore } from "../store/useSettingsStore";
import { ThemeProvider } from "../theme/ThemeProvider";
import { formatDuration } from "../utils/format";

export function MiniPlayer() {
  const initPlayer = usePlayerStore((s) => s.init);
  const initSettings = useSettingsStore((s) => s.init);

  const playback = usePlayerStore((s) => s.playback);
  const togglePlayPause = usePlayerStore((s) => s.togglePlayPause);
  const nextTrack = usePlayerStore((s) => s.nextTrack);
  const prevTrack = usePlayerStore((s) => s.prevTrack);
  const seek = usePlayerStore((s) => s.seek);

  useEffect(() => {
    void initSettings();
    let cleanup: (() => void) | undefined;
    void initPlayer().then((fn) => {
      cleanup = fn;
    });
    return () => {
      cleanup?.();
    };
  }, [initPlayer, initSettings]);

  const track = playback.currentTrack;
  const isPlaying = playback.status === "playing";
  const duration = playback.durationMs || track?.durationMs || 0;
  const progressPct =
    duration > 0 ? Math.min(100, (playback.positionMs / duration) * 100) : 0;

  return (
    <ThemeProvider>
      <div
        className="h-screen w-screen p-3 flex flex-col justify-between rounded-2xl overflow-hidden adaptive-transition"
        style={{
          background:
            "linear-gradient(145deg, var(--accent-container), var(--surface-1) 62%)",
          border: "1px solid var(--outline-strong)",
        }}
      >
        {/* Draggable Top Row */}
        <div
          data-tauri-drag-region
          className="flex items-center justify-between gap-3"
        >
          <div data-tauri-drag-region className="flex items-center gap-3 min-w-0 flex-1">
            <Artwork
              artworkPath={track?.artworkPath}
              accentColor={track?.accentColor}
              alt={track?.title ?? "Mini Player"}
              size="md"
              rounded="rounded-xl"
            />
            <div data-tauri-drag-region className="min-w-0">
              <div className="text-sm font-semibold truncate">
                {track?.title ?? "Nothing playing"}
              </div>
              <div
                className="text-xs truncate"
                style={{ color: "var(--text-secondary)" }}
              >
                {track?.artist ?? "Umimuko Music"}
              </div>
            </div>
          </div>

          <button
            type="button"
            onClick={() => void api.toggleMiniPlayer(false)}
            className="p-1.5 rounded-full shrink-0 cursor-pointer"
            style={{
              backgroundColor: "var(--surface-3)",
              color: "var(--text-secondary)",
            }}
            title="Close Mini Player"
          >
            <Icon name="close" size={15} />
          </button>
        </div>

        {/* Bottom Controls + Progress */}
        <div className="space-y-2">
          <div className="flex items-center gap-2">
            <span
              className="text-[10px] tabular-nums w-8 text-right"
              style={{ color: "var(--text-muted)" }}
            >
              {formatDuration(playback.positionMs)}
            </span>
            <input
              type="range"
              min={0}
              max={Math.max(1, duration)}
              value={Math.min(playback.positionMs, duration)}
              onChange={(e) => void seek(Number(e.target.value))}
              className="umimuko-slider flex-1"
              style={{
                background: `linear-gradient(90deg, var(--accent) ${progressPct}%, var(--surface-4) ${progressPct}%)`,
                height: 3,
                borderRadius: 999,
              }}
            />
            <span
              className="text-[10px] tabular-nums w-8"
              style={{ color: "var(--text-muted)" }}
            >
              {formatDuration(duration)}
            </span>
          </div>

          <div className="flex items-center justify-center gap-4">
            <button
              type="button"
              onClick={() => void prevTrack()}
              className="p-1.5 rounded-full cursor-pointer"
              title="Previous"
            >
              <Icon name="previous" size={20} />
            </button>
            <button
              type="button"
              onClick={() => void togglePlayPause()}
              className="w-9 h-9 rounded-full flex items-center justify-center shadow cursor-pointer"
              style={{
                backgroundColor: "var(--accent)",
                color: "var(--on-accent)",
              }}
              title={isPlaying ? "Pause" : "Play"}
            >
              <Icon name={isPlaying ? "pause" : "play"} size={20} />
            </button>
            <button
              type="button"
              onClick={() => void nextTrack()}
              className="p-1.5 rounded-full cursor-pointer"
              title="Next"
            >
              <Icon name="next" size={20} />
            </button>
          </div>
        </div>
      </div>
    </ThemeProvider>
  );
}
