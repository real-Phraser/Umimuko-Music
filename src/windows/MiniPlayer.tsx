import { useEffect } from "react";
import { api } from "../api/tauri";
import { Artwork } from "../components/Artwork";
import { Icon } from "../components/Icon";
import { usePlayerStore } from "../store/usePlayerStore";
import { ThemeProvider } from "../theme/ThemeProvider";
import { useSettingsStore } from "../store/useSettingsStore";

export function MiniPlayer() {
  const initPlayer = usePlayerStore((s) => s.init);
  const initSettings = useSettingsStore((s) => s.init);

  const playback = usePlayerStore((s) => s.playback);
  const queue = usePlayerStore((s) => s.queue);
  const togglePlayPause = usePlayerStore((s) => s.togglePlayPause);
  const nextTrack = usePlayerStore((s) => s.nextTrack);
  const prevTrack = usePlayerStore((s) => s.prevTrack);

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
  const hasPrev = queue.items.length > 0;
  const hasNext = queue.items.length > 0;

  return (
    <ThemeProvider>
      <div className="h-screen w-screen p-1.5 flex items-center justify-center bg-transparent">
        <div
          className="w-full h-full px-3 rounded-full flex items-center gap-2.5 overflow-hidden shadow-lg adaptive-transition"
          style={{
            backgroundColor: "var(--surface-2)",
            border: "1px solid var(--outline-strong)",
          }}
        >
          {/* Draggable Track Info */}
          <div
            data-tauri-drag-region
            className="flex-1 min-w-0 flex items-center gap-2.5 cursor-grab active:cursor-grabbing"
          >
            <Artwork
              artworkPath={track?.artworkPath}
              accentColor={track?.accentColor}
              alt={track?.title ?? "Mini Player"}
              size="sm"
              rounded="rounded-xl"
            />
            <div data-tauri-drag-region className="min-w-0 flex-1">
              <div className="text-sm font-semibold truncate leading-tight">
                {track?.title ?? "Nothing playing"}
              </div>
              <div
                className="text-[11px] truncate leading-tight mt-0.5"
                style={{ color: "var(--text-secondary)" }}
              >
                {track?.artist ?? ""}
              </div>
            </div>
          </div>

          {/* Transport + Restore */}
          <div className="flex items-center gap-0.5 shrink-0">
            <button
              type="button"
              disabled={!hasPrev}
              onClick={() => void prevTrack()}
              className="w-9 h-9 rounded-full flex items-center justify-center hover:bg-[var(--surface-variant)] disabled:opacity-40 cursor-pointer"
              title="Previous track"
            >
              <Icon name="previous" size={20} />
            </button>

            <button
              type="button"
              disabled={!track}
              onClick={() => void togglePlayPause()}
              className="w-10 h-10 rounded-full flex items-center justify-center shadow-sm disabled:opacity-40 cursor-pointer"
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
              disabled={!hasNext}
              onClick={() => void nextTrack()}
              className="w-9 h-9 rounded-full flex items-center justify-center hover:bg-[var(--surface-variant)] disabled:opacity-40 cursor-pointer"
              title="Next track"
            >
              <Icon name="next" size={20} />
            </button>

            <button
              type="button"
              onClick={() => void api.toggleMiniPlayer(false)}
              className="w-9 h-9 rounded-full flex items-center justify-center hover:bg-[var(--surface-variant)] cursor-pointer"
              title="Restore player bar"
            >
              <Icon name="restore" size={20} />
            </button>
          </div>
        </div>
      </div>
    </ThemeProvider>
  );
}
