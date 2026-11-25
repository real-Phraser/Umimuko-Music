import { useEffect, useMemo, useRef, useState } from "react";
import { useLibraryStore } from "../../store/useLibraryStore";
import { usePlayerStore } from "../../store/usePlayerStore";
import type { LyricLine } from "../../types/ipc";
import { Icon } from "../Icon";

function clamp01(value: number): number {
  return value < 0 ? 0 : value > 1 ? 1 : value;
}

export function SpicyLyricsView() {
  const lyrics = usePlayerStore((s) => s.lyrics);
  const lyricsLoading = usePlayerStore((s) => s.lyricsLoading);
  const currentTrackId = usePlayerStore((s) => s.playback.currentTrackId);
  const status = usePlayerStore((s) => s.playback.status);
  const positionMs = usePlayerStore((s) => s.playback.positionMs);
  const seek = usePlayerStore((s) => s.seek);
  const refreshLyrics = usePlayerStore((s) => s.refreshLyrics);
  const openDialog = useLibraryStore((s) => s.openDialog);

  const [interpolatedMs, setInterpolatedMs] = useState(positionMs);
  const anchorRef = useRef({ pos: positionMs, time: performance.now() });
  const activeLineRef = useRef<HTMLButtonElement | null>(null);
  const containerRef = useRef<HTMLDivElement | null>(null);
  const userScrolledAtRef = useRef(0);

  // Sync anchor whenever store positionMs updates
  useEffect(() => {
    anchorRef.current = { pos: positionMs, time: performance.now() };
    setInterpolatedMs(positionMs);
  }, [positionMs]);

  // 60fps requestAnimationFrame interpolation for GPU-composited karaoke sweep
  useEffect(() => {
    if (status !== "playing") return;
    let rafId = 0;

    const tick = (now: number) => {
      const elapsed = now - anchorRef.current.time;
      setInterpolatedMs(anchorRef.current.pos + elapsed);
      rafId = requestAnimationFrame(tick);
    };

    rafId = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(rafId);
  }, [status]);

  const activeLineIndex = useMemo(() => {
    if (!lyrics || lyrics.lines.length === 0 || lyrics.type === "plain") return -1;
    let idx = -1;
    for (let i = 0; i < lyrics.lines.length; i += 1) {
      if (interpolatedMs >= lyrics.lines[i].startMs) {
        idx = i;
      } else {
        break;
      }
    }
    return idx;
  }, [lyrics, interpolatedMs]);

  // Auto-scroll active line into center unless user recently scrolled manually
  useEffect(() => {
    if (activeLineIndex < 0) return;
    if (performance.now() - userScrolledAtRef.current < 4000) return;
    activeLineRef.current?.scrollIntoView({
      behavior: "smooth",
      block: "center",
    });
  }, [activeLineIndex]);

  if (lyricsLoading) {
    return (
      <div className="h-full flex flex-col items-center justify-center gap-3 opacity-75">
        <Icon name="lyrics" size={30} />
        <div className="text-xs font-medium" style={{ color: "var(--text-secondary)" }}>
          Fetching Spicy Lyrics…
        </div>
      </div>
    );
  }

  if (!lyrics || lyrics.type === "none" || lyrics.lines.length === 0) {
    return (
      <div className="h-full flex flex-col items-center justify-center gap-3 text-center px-6">
        <Icon name="lyrics" size={36} className="opacity-40" />
        <div className="text-sm font-semibold">No lyrics available for this track</div>
        <p className="text-xs max-w-xs" style={{ color: "var(--text-secondary)" }}>
          Lyrics are fetched from embedded tags, local .lrc files, or SpicyLyrics.
        </p>
        <div className="flex items-center gap-2 mt-1">
          {currentTrackId && (
            <button
              type="button"
              onClick={() => void refreshLyrics(currentTrackId)}
              className="px-4 py-1.5 rounded-full text-xs font-medium cursor-pointer"
              style={{
                backgroundColor: "var(--accent-container)",
                color: "var(--on-accent-container)",
              }}
            >
              Retry Fetch
            </button>
          )}
          <button
            type="button"
            onClick={() => openDialog("settings")}
            className="px-4 py-1.5 rounded-full text-xs font-medium cursor-pointer"
            style={{
              backgroundColor: "var(--surface-variant)",
              color: "var(--text-primary)",
            }}
          >
            Lyrics Settings
          </button>
        </div>
      </div>
    );
  }

  const renderLine = (line: LyricLine, idx: number) => {
    const isActive = idx === activeLineIndex;
    const isPast = activeLineIndex >= 0 && idx < activeLineIndex;

    if (line.isInstrumental) {
      return (
        <div
          key={`${line.startMs}-${idx}`}
          className="py-3 px-3 text-2xl font-bold tracking-[0.25em] transition-opacity duration-300"
          style={{ opacity: isActive ? 0.95 : 0.3, color: "var(--accent)" }}
        >
          •••
        </div>
      );
    }

    const hasWords = lyrics.type === "syllable" && line.words && line.words.length > 0;

    return (
      <button
        key={`${line.startMs}-${idx}`}
        ref={isActive ? activeLineRef : undefined}
        type="button"
        onClick={() => {
          if (lyrics.type !== "plain") {
            void seek(line.startMs);
          }
        }}
        className="w-full text-left py-2.5 px-3 rounded-2xl transition-transform duration-300 origin-left cursor-pointer"
        style={{
          transform: isActive ? "scale(1.025)" : "scale(1)",
          opacity: lyrics.type === "plain" ? 0.9 : isActive ? 1 : isPast ? 0.38 : 0.52,
          willChange: "transform, opacity",
        }}
      >
        <div className="text-2xl sm:text-3xl font-bold leading-snug tracking-tight">
          {hasWords ? (
            line.words!.map((word, wIdx) => {
              const duration = Math.max(1, word.endMs - word.startMs);
              const progress = clamp01((interpolatedMs - word.startMs) / duration);
              const pct = (progress * 100).toFixed(1);
              const isWordActive = progress > 0 && progress < 1;

              return (
                <span
                  key={`${word.startMs}-${wIdx}`}
                  className="inline-block whitespace-pre-wrap"
                  style={{
                    backgroundImage: `linear-gradient(90deg, var(--accent) 0%, var(--accent) ${pct}%, var(--text-primary) ${pct}%, var(--text-primary) 100%)`,
                    WebkitBackgroundClip: "text",
                    backgroundClip: "text",
                    WebkitTextFillColor: "transparent",
                    transform: isWordActive ? "translateY(-1.5px)" : "translateY(0px)",
                    transition: "transform 120ms ease-out",
                    willChange: "transform",
                  }}
                >
                  {word.text}
                </span>
              );
            })
          ) : (
            <span style={{ color: isActive ? "var(--accent)" : "var(--text-primary)" }}>
              {line.text}
            </span>
          )}
        </div>

        {line.romanized && (
          <div
            className="text-sm mt-1 font-normal"
            style={{ color: "var(--text-secondary)" }}
          >
            {line.romanized}
          </div>
        )}
      </button>
    );
  };

  return (
    <div className="h-full flex flex-col">
      <div
        ref={containerRef}
        onWheel={() => {
          userScrolledAtRef.current = performance.now();
        }}
        className="flex-1 overflow-y-auto px-2 py-12 space-y-2"
        style={{
          maskImage:
            "linear-gradient(to bottom, transparent 0%, black 10%, black 90%, transparent 100%)",
          WebkitMaskImage:
            "linear-gradient(to bottom, transparent 0%, black 10%, black 90%, transparent 100%)",
        }}
      >
        {lyrics.lines.map(renderLine)}
      </div>

      <div className="flex items-center justify-between px-3 pt-2 opacity-70">
        <span className="text-[11px] font-medium" style={{ color: "var(--text-secondary)" }}>
          {lyrics.source} •{" "}
          {lyrics.type === "syllable"
            ? "Word-Synced"
            : lyrics.type === "line"
              ? "Line-Synced"
              : "Plain"}
        </span>
        {currentTrackId && (
          <button
            type="button"
            onClick={() => void refreshLyrics(currentTrackId)}
            className="p-1.5 rounded-full hover:bg-[var(--surface-variant)] cursor-pointer"
            style={{ color: "var(--text-secondary)" }}
            title="Refresh lyrics"
          >
            <Icon name="restore" size={15} />
          </button>
        )}
      </div>
    </div>
  );
}
