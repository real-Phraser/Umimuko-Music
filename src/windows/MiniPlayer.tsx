import { LogicalSize } from "@tauri-apps/api/dpi";
import { getCurrentWebviewWindow } from "@tauri-apps/api/webviewWindow";
import { useEffect, useRef, useState } from "react";
import { api, isTauriRuntime, resolveArtworkUrl } from "../api/tauri";
import { Icon } from "../components/Icon";
import { usePlayerStore } from "../store/usePlayerStore";
import { useSettingsStore } from "../store/useSettingsStore";
import { ThemeProvider } from "../theme/ThemeProvider";

function splitArtists(raw: string): string[] {
  const cleaned = raw.trim();
  if (!cleaned) return [];
  return cleaned
    .split(/\s*(?:,|&|;|\/|\bfeat\.?\b|\bft\.?\b)\s*/i)
    .map((s) => s.trim())
    .filter(Boolean);
}

function MiniBouncingText({
  text,
  className = "",
  style,
}: {
  text: string;
  className?: string;
  style?: React.CSSProperties;
}) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const textRef = useRef<HTMLSpanElement | null>(null);
  const [overflowPx, setOverflowPx] = useState(0);
  const [offsetPx, setOffsetPx] = useState(0);

  useEffect(() => {
    const measure = () => {
      const container = containerRef.current;
      const span = textRef.current;
      if (!container || !span) return;
      setOverflowPx(Math.max(0, span.scrollWidth - container.clientWidth));
      setOffsetPx(0);
    };
    measure();
    window.addEventListener("resize", measure);
    return () => window.removeEventListener("resize", measure);
  }, [text]);

  useEffect(() => {
    if (overflowPx <= 1) return;
    let cancelled = false;
    let rafId = 0;
    const durationMs = Math.min(30_000, Math.max(1000, (overflowPx / 24) * 1000));

    const sleep = (ms: number) =>
      new Promise<void>((resolve) => {
        window.setTimeout(resolve, ms);
      });

    const animateTo = (from: number, to: number, dur: number) =>
      new Promise<void>((resolve) => {
        const start = performance.now();
        const step = (now: number) => {
          if (cancelled) return;
          const t = Math.min(1, (now - start) / dur);
          setOffsetPx(from + (to - from) * t);
          if (t < 1) {
            rafId = requestAnimationFrame(step);
          } else {
            resolve();
          }
        };
        rafId = requestAnimationFrame(step);
      });

    void (async () => {
      await sleep(1400);
      while (!cancelled) {
        await animateTo(0, overflowPx, durationMs);
        if (cancelled) break;
        await sleep(1100);
        if (cancelled) break;
        await animateTo(overflowPx, 0, durationMs);
        if (cancelled) break;
        await sleep(1400);
      }
    })();

    return () => {
      cancelled = true;
      cancelAnimationFrame(rafId);
    };
  }, [overflowPx]);

  return (
    <div ref={containerRef} className="w-full overflow-hidden flex justify-start">
      <span
        ref={textRef}
        className={`inline-block whitespace-nowrap ${className}`}
        style={{
          ...style,
          transform: overflowPx > 1 ? `translate3d(${-offsetPx}px, 0, 0)` : undefined,
        }}
      >
        {text}
      </span>
    </div>
  );
}

export function MiniPlayer() {
  const initPlayer = usePlayerStore((s) => s.init);
  const initSettings = useSettingsStore((s) => s.init);

  const playback = usePlayerStore((s) => s.playback);
  const queue = usePlayerStore((s) => s.queue);
  const togglePlayPause = usePlayerStore((s) => s.togglePlayPause);
  const nextTrack = usePlayerStore((s) => s.nextTrack);
  const prevTrack = usePlayerStore((s) => s.prevTrack);

  useEffect(() => {
    // Ensure outer window surface outside the rounded pill stays transparent
    document.documentElement.style.backgroundColor = "transparent";
    document.body.style.backgroundColor = "transparent";
    const rootEl = document.getElementById("root");
    if (rootEl) rootEl.style.backgroundColor = "transparent";

    if (isTauriRuntime()) {
      void getCurrentWebviewWindow()
        .setSize(new LogicalSize(448, 80))
        .catch(() => {});
    }

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
  const coverUrl = resolveArtworkUrl(track?.artworkPath);
  const artistText = track
    ? (splitArtists(track.artist).join(" · ") || track.artist)
    : "";

  return (
    <ThemeProvider>
      <div className="h-screen w-screen p-[6px] flex items-center justify-center bg-transparent select-none">
        {/* FloatingPlayer.kt: Surface(color = colors.surfaceVariant, shape = RoundedCornerShape(50), border = 1.dp outline@16%) */}
        <div
          className="w-full h-full px-3 rounded-full flex items-center gap-[10px] overflow-hidden adaptive-transition"
          style={{
            backgroundColor: "var(--surface-2)",
            border: "1px solid color-mix(in srgb, var(--outline-strong) 45%, transparent)",
            color: "var(--text-primary)",
          }}
        >
          {/* Draggable Track Info */}
          <div
            data-tauri-drag-region
            onMouseDown={(e) => {
              if (e.button === 0 && isTauriRuntime()) {
                void getCurrentWebviewWindow().startDragging().catch(() => {});
              }
            }}
            className="flex-1 min-w-0 flex items-center gap-[10px] cursor-grab active:cursor-grabbing"
            title="Drag mini player"
          >
            {/* Artwork(44.dp, song != null, song?.coverPath) -> rounded 44/4 = 11px */}
            <div
              data-tauri-drag-region
              className="w-11 h-11 shrink-0 rounded-[11px] overflow-hidden flex items-center justify-center pointer-events-none"
              style={{
                backgroundColor: track
                  ? "rgba(var(--accent-rgb), 0.18)"
                  : "var(--surface-3)",
              }}
            >
              {coverUrl ? (
                <img
                  src={coverUrl}
                  alt={track?.title ?? "Mini Player"}
                  className="w-full h-full object-cover"
                  draggable={false}
                />
              ) : (
                <Icon
                  name="music"
                  size={24}
                  style={{
                    color: track ? "var(--accent)" : "var(--text-secondary)",
                  }}
                />
              )}
            </div>

            <div
              data-tauri-drag-region
              className="min-w-0 flex-1 flex flex-col gap-1 pointer-events-none"
            >
              <MiniBouncingText
                text={track?.title ?? "Nothing playing"}
                className="text-sm leading-5 font-semibold"
              />
              {artistText && (
                <MiniBouncingText
                  text={artistText}
                  className="text-[11px] leading-4 font-normal"
                  style={{ color: "var(--text-secondary)" }}
                />
              )}
            </div>
          </div>

          {/* Transport + Restore: 36dp BubbleButtons (22dp glyph) + 40dp Circle Play/Pause (24dp glyph) */}
          <div className="flex items-center gap-[2px] shrink-0">
            <button
              type="button"
              disabled={!hasPrev}
              onClick={() => void prevTrack()}
              className="w-9 h-9 rounded-full flex items-center justify-center hover:bg-[var(--surface-variant)] disabled:opacity-38 cursor-pointer"
              title="Previous track"
              aria-label="Previous track"
            >
              <Icon name="previous" size={22} />
            </button>

            <button
              type="button"
              disabled={!track}
              onClick={() => void togglePlayPause()}
              className="w-10 h-10 rounded-full flex items-center justify-center disabled:opacity-38 cursor-pointer transition-transform active:scale-95"
              style={{
                backgroundColor: "var(--accent)",
                color: "var(--on-accent)",
              }}
              title={isPlaying ? "Pause" : "Play"}
              aria-label={isPlaying ? "Pause" : "Play"}
            >
              <Icon name={isPlaying ? "pause" : "play"} size={24} />
            </button>

            <button
              type="button"
              disabled={!hasNext}
              onClick={() => void nextTrack()}
              className="w-9 h-9 rounded-full flex items-center justify-center hover:bg-[var(--surface-variant)] disabled:opacity-38 cursor-pointer"
              title="Next track"
              aria-label="Next track"
            >
              <Icon name="next" size={22} />
            </button>

            <button
              type="button"
              onClick={() => void api.toggleMiniPlayer(false)}
              className="w-9 h-9 rounded-full flex items-center justify-center hover:bg-[var(--surface-variant)] cursor-pointer"
              title="Restore player bar"
              aria-label="Restore player bar"
            >
              <Icon name="restore" size={22} />
            </button>
          </div>
        </div>
      </div>
    </ThemeProvider>
  );
}
