import { useEffect, useMemo, useRef, useState } from "react";
import { resolveArtworkUrl } from "../api/tauri";
import appIconUrl from "../assets/branding/app-icon.png";
import { useLibraryStore } from "../store/useLibraryStore";
import { usePlayerStore } from "../store/usePlayerStore";
import {
  getFixedPresetPalette,
  resolveFullPlayerAmbientColors,
  useThemePresetId,
} from "../theme/ThemeProvider";
import { formatDuration } from "../utils/format";
import { Icon } from "./Icon";
import { SpicyLyricsView } from "./lyrics/SpicyLyricsView";

function hexToRgb(hex: string | null | undefined): [number, number, number] | null {
  if (!hex) return null;
  const cleaned = hex.trim().replace(/^#/, "");
  if (cleaned.length === 3) {
    const r = parseInt(cleaned[0] + cleaned[0], 16);
    const g = parseInt(cleaned[1] + cleaned[1], 16);
    const b = parseInt(cleaned[2] + cleaned[2], 16);
    return Number.isNaN(r) ? null : [r, g, b];
  }
  if (cleaned.length === 6) {
    const r = parseInt(cleaned.slice(0, 2), 16);
    const g = parseInt(cleaned.slice(2, 4), 16);
    const b = parseInt(cleaned.slice(4, 6), 16);
    return Number.isNaN(r) ? null : [r, g, b];
  }
  return null;
}

function rgbToHsv(r: number, g: number, b: number): [number, number, number] {
  const rn = r / 255;
  const gn = g / 255;
  const bn = b / 255;
  const max = Math.max(rn, gn, bn);
  const min = Math.min(rn, gn, bn);
  const d = max - min;
  let h = 0;
  if (d !== 0) {
    if (max === rn) h = ((gn - bn) / d + (gn < bn ? 6 : 0)) / 6;
    else if (max === gn) h = ((bn - rn) / d + 2) / 6;
    else h = ((rn - gn) / d + 4) / 6;
  }
  return [h, max === 0 ? 0 : d / max, max];
}

function hsvToRgb(h: number, s: number, v: number): [number, number, number] {
  const i = Math.floor(h * 6);
  const f = h * 6 - i;
  const p = v * (1 - s);
  const q = v * (1 - f * s);
  const t = v * (1 - (1 - f) * s);
  let rn = 0;
  let gn = 0;
  let bn = 0;
  switch (i % 6) {
    case 0:
      rn = v; gn = t; bn = p;
      break;
    case 1:
      rn = q; gn = v; bn = p;
      break;
    case 2:
      rn = p; gn = v; bn = t;
      break;
    case 3:
      rn = p; gn = q; bn = v;
      break;
    case 4:
      rn = t; gn = p; bn = v;
      break;
    case 5:
      rn = v; gn = p; bn = q;
      break;
  }
  return [Math.round(rn * 255), Math.round(gn * 255), Math.round(bn * 255)];
}

function lerpRgb(
  a: [number, number, number],
  b: [number, number, number],
  t: number
): [number, number, number] {
  return [
    Math.round(a[0] + (b[0] - a[0]) * t),
    Math.round(a[1] + (b[1] - a[1]) * t),
    Math.round(a[2] + (b[2] - a[2]) * t),
  ];
}

function rgbStr([r, g, b]: [number, number, number], alpha = 1): string {
  return alpha >= 1 ? `rgb(${r}, ${g}, ${b})` : `rgba(${r}, ${g}, ${b}, ${alpha})`;
}

function relativeLuminance([r, g, b]: [number, number, number]): number {
  const norm = [r, g, b].map((c) => {
    const s = c / 255;
    return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
  });
  return 0.2126 * norm[0] + 0.7152 * norm[1] + 0.0722 * norm[2];
}

/** AlbumTheme.kt fullPlayerColorScheme + ThemeManager.kt FullPlayerAmbientColors */
function computeFullPlayerPalette(
  accentHex: string | null | undefined,
  presetId: string
) {
  const fixed = getFixedPresetPalette(presetId);
  const ambientColors = resolveFullPlayerAmbientColors(accentHex, presetId);
  const isDark = fixed ? fixed.mode === "dark" : true;

  const seedRgb = hexToRgb(accentHex) ?? [33, 102, 194]; // 0xFF2166C2
  const [h, s] = rgbToHsv(seedRgb[0], seedRgb[1], seedRgb[2]);

  const originalPrimary = hsvToRgb(h, Math.min(s, 0.45), 0.98);
  const originalContainer = hsvToRgb(h, Math.min(s, 0.32), 0.24);
  const neutralSat = Math.min(s, 0.18);
  const originalVariant = hsvToRgb(h, neutralSat, 0.17);

  // fullPlayerColorScheme: quieter muted ice-blue/accent blend (#B6C4DA -> primary 38%)
  const fpPrimary: [number, number, number] = fixed
    ? hexToRgb(fixed.primary)!
    : lerpRgb([182, 196, 218], originalPrimary, 0.38);
  const fpOnPrimary: [number, number, number] = fixed
    ? hexToRgb(fixed.onPrimary)!
    : relativeLuminance(fpPrimary) > 0.38
      ? [15, 17, 23]
      : [255, 255, 255];
  const fpContainer: [number, number, number] = fixed
    ? hexToRgb(fixed.container)!
    : lerpRgb([42, 48, 58], originalContainer, 0.3);
  const fpOnContainer: [number, number, number] = fixed
    ? hexToRgb(fixed.onContainer)!
    : relativeLuminance(fpContainer) > 0.4
      ? [20, 23, 30]
      : [245, 247, 250];

  const deepBg = hexToRgb(ambientColors.deepBackground) ?? [15, 17, 23];
  const glowBase = hexToRgb(ambientColors.glowBase) ?? [20, 23, 30];
  const glowAccent = hexToRgb(ambientColors.glowAccent) ?? seedRgb;
  const variantRgb = fixed ? (hexToRgb(fixed.variant) ?? glowBase) : originalVariant;

  const primaryGlowMin = lerpRgb(glowBase, glowAccent, 0.22);
  const primaryGlowMax = lerpRgb(glowBase, glowAccent, 0.38);

  const primaryWash = lerpRgb(glowBase, glowAccent, 0.32);
  const secondaryWash = lerpRgb(glowBase, fpPrimary, 0.28);
  const tertiaryWash = lerpRgb(deepBg, variantRgb, 0.26);
  const bridgeWash = lerpRgb(glowBase, glowAccent, 0.24);

  return {
    isDynamic: !fixed,
    isDark,
    fpPrimary: rgbStr(fpPrimary),
    fpOnPrimary: rgbStr(fpOnPrimary),
    fpContainer: rgbStr(fpContainer),
    fpContainerAlpha85: rgbStr(fpContainer, 0.85),
    fpOnContainer: rgbStr(fpOnContainer),
    onBackground: fixed ? fixed.onSurface : "#F5F7FA",
    onSurfaceVariant: fixed ? fixed.onSurfaceVariant : "rgba(245, 247, 250, 0.68)",
    trackBg: isDark ? "rgba(245, 247, 250, 0.2)" : "rgba(32, 33, 36, 0.18)",
    deepBg: rgbStr(deepBg),
    glowBase: rgbStr(glowBase),
    primaryGlowMin: rgbStr(primaryGlowMin, isDark ? 0.72 : 0.42),
    primaryGlowMax: rgbStr(primaryGlowMax, isDark ? 0.92 : 0.6),
    primaryGlowMaxMid: rgbStr(primaryGlowMax, isDark ? 0.44 : 0.25),
    scrim: `linear-gradient(180deg, ${rgbStr(deepBg, 0.52)} 0%, ${rgbStr(deepBg, 0.36)} 50%, ${rgbStr(deepBg, 0.58)} 100%)`,
    diagStart: rgbStr(lerpRgb(deepBg, glowBase, 0.75)),
    diagEnd: rgbStr(lerpRgb(deepBg, glowAccent, 0.16)),
    primaryWash: rgbStr(primaryWash, isDark ? 0.62 : 0.38),
    primaryWashMid: rgbStr(primaryWash, isDark ? 0.26 : 0.16),
    secondaryWash: rgbStr(secondaryWash, isDark ? 0.54 : 0.34),
    secondaryWashMid: rgbStr(secondaryWash, isDark ? 0.22 : 0.14),
    tertiaryWash: rgbStr(tertiaryWash, isDark ? 0.48 : 0.3),
    tertiaryWashMid: rgbStr(tertiaryWash, isDark ? 0.19 : 0.12),
    bridgeWash: rgbStr(bridgeWash, isDark ? 0.46 : 0.28),
    bridgeWashMid: rgbStr(bridgeWash, isDark ? 0.18 : 0.11),
    variantBg: rgbStr(variantRgb),
    activeArtBg: rgbStr(fpPrimary, 0.18),
  };
}

/** Port of BouncingText.kt: centers short text, smoothly bounces overflowing text back and forth. */
function BouncingText({
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
      const delta = Math.max(0, span.scrollWidth - container.clientWidth);
      setOverflowPx(delta);
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
    <div
      ref={containerRef}
      className={`w-full overflow-hidden flex ${
        overflowPx <= 1 ? "justify-center" : "justify-start"
      }`}
    >
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

/** Port of CompactProgress.kt: 3px line with no thumb at rest, 8px + thumb when pressed/dragged. */
function CompactProgress({
  value,
  enabled,
  label,
  accentColor,
  trackColor = "rgba(245, 247, 250, 0.2)",
  onPreview,
  onCommit,
  className = "",
}: {
  value: number;
  enabled: boolean;
  label: string;
  accentColor: string;
  trackColor?: string;
  onPreview?: (fraction: number | null) => void;
  onCommit: (fraction: number) => void;
  className?: string;
}) {
  const trackRef = useRef<HTMLDivElement | null>(null);
  const [pressed, setPressed] = useState(false);
  const [local, setLocal] = useState(value);

  const calcFraction = (clientX: number) => {
    const el = trackRef.current;
    if (!el) return 0;
    const rect = el.getBoundingClientRect();
    const pad = 8;
    const usable = Math.max(1, rect.width - pad * 2);
    return Math.min(1, Math.max(0, (clientX - rect.left - pad) / usable));
  };

  const displayFraction = Math.min(1, Math.max(0, pressed ? local : value));
  const thickness = pressed ? 8 : 3;

  return (
    <div
      ref={trackRef}
      role="slider"
      tabIndex={enabled ? 0 : -1}
      aria-label={label}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={Math.round(displayFraction * 100)}
      aria-disabled={!enabled}
      onPointerDown={(e) => {
        if (!enabled) return;
        e.currentTarget.setPointerCapture(e.pointerId);
        const f = calcFraction(e.clientX);
        setPressed(true);
        setLocal(f);
        onPreview?.(f);
      }}
      onPointerMove={(e) => {
        if (!pressed || !enabled) return;
        const f = calcFraction(e.clientX);
        setLocal(f);
        onPreview?.(f);
      }}
      onPointerUp={(e) => {
        if (!pressed) return;
        const f = calcFraction(e.clientX);
        setPressed(false);
        onPreview?.(null);
        onCommit(f);
        try {
          e.currentTarget.releasePointerCapture(e.pointerId);
        } catch {
          // ignore
        }
      }}
      onPointerCancel={() => {
        if (!pressed) return;
        setPressed(false);
        onPreview?.(null);
      }}
      onKeyDown={(e) => {
        if (!enabled) return;
        if (e.key === "ArrowLeft") {
          e.preventDefault();
          onCommit(Math.max(0, value - 0.01));
        } else if (e.key === "ArrowRight") {
          e.preventDefault();
          onCommit(Math.min(1, value + 0.01));
        } else if (e.key === "Home") {
          e.preventDefault();
          onCommit(0);
        } else if (e.key === "End") {
          e.preventDefault();
          onCommit(1);
        }
      }}
      className={`relative h-8 px-2 flex items-center select-none outline-none ${
        enabled ? "cursor-pointer" : "cursor-default opacity-35"
      } ${className}`}
    >
      <div
        className="relative w-full rounded-full transition-[height] duration-150"
        style={{
          height: thickness,
          backgroundColor: trackColor,
        }}
      >
        <div
          className="absolute inset-y-0 left-0 rounded-full"
          style={{
            width: `${(displayFraction * 100).toFixed(3)}%`,
            backgroundColor: accentColor,
          }}
        />
        <div
          className="absolute top-1/2 -translate-y-1/2 -translate-x-1/2 rounded-full transition-transform duration-150 pointer-events-none"
          style={{
            left: `${(displayFraction * 100).toFixed(3)}%`,
            width: 14,
            height: 14,
            backgroundColor: accentColor,
            transform: `translate(-50%, -50%) scale(${pressed ? 1 : 0})`,
          }}
        />
      </div>
    </div>
  );
}

export function FullPlayer() {
  const fullPlayerOpen = usePlayerStore((s) => s.fullPlayerOpen);
  const fullPlayerTab = usePlayerStore((s) => s.fullPlayerTab);
  const playback = usePlayerStore((s) => s.playback);
  const queue = usePlayerStore((s) => s.queue);

  const setFullPlayerOpen = usePlayerStore((s) => s.setFullPlayerOpen);
  const setFullPlayerTab = usePlayerStore((s) => s.setFullPlayerTab);
  const setQueueDrawerOpen = usePlayerStore((s) => s.setQueueDrawerOpen);
  const togglePlayPause = usePlayerStore((s) => s.togglePlayPause);
  const nextTrack = usePlayerStore((s) => s.nextTrack);
  const prevTrack = usePlayerStore((s) => s.prevTrack);
  const seek = usePlayerStore((s) => s.seek);
  const setVolume = usePlayerStore((s) => s.setVolume);
  const toggleMute = usePlayerStore((s) => s.toggleMute);
  const toggleShuffle = usePlayerStore((s) => s.toggleShuffle);
  const cycleRepeat = usePlayerStore((s) => s.cycleRepeat);

  const playlists = useLibraryStore((s) => s.playlists);
  const toggleLike = useLibraryStore((s) => s.toggleLike);
  const addTrackToPlaylist = useLibraryStore((s) => s.addTrackToPlaylist);
  const createPlaylist = useLibraryStore((s) => s.createPlaylist);
  const openDialog = useLibraryStore((s) => s.openDialog);

  const [showLyrics, setShowLyrics] = useState(false);
  const [previewFraction, setPreviewFraction] = useState<number | null>(null);
  const [playlistMenuOpen, setPlaylistMenuOpen] = useState(false);
  const [newPlaylistName, setNewPlaylistName] = useState("");
  const [isWide, setIsWide] = useState(
    typeof window !== "undefined" ? window.innerWidth >= 840 : true
  );

  useEffect(() => {
    const onResize = () => setIsWide(window.innerWidth >= 840);
    window.addEventListener("resize", onResize);
    return () => window.removeEventListener("resize", onResize);
  }, []);

  useEffect(() => {
    if (!fullPlayerOpen) return;
    setShowLyrics(fullPlayerTab === "lyrics");
    setPreviewFraction(null);
  }, [fullPlayerOpen, fullPlayerTab]);

  useEffect(() => {
    setPreviewFraction(null);
  }, [playback.currentTrackId]);

  useEffect(() => {
    if (!fullPlayerOpen) return;
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        if (playlistMenuOpen) {
          setPlaylistMenuOpen(false);
        } else if (
          !usePlayerStore.getState().queueDrawerOpen &&
          !useLibraryStore.getState().activeDialog
        ) {
          setFullPlayerOpen(false);
        }
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [fullPlayerOpen, playlistMenuOpen, setFullPlayerOpen]);

  const presetId = useThemePresetId();
  const track = playback.currentTrack;
  const palette = useMemo(
    () => computeFullPlayerPalette(track?.accentColor, presetId),
    [track?.accentColor, presetId]
  );

  if (!fullPlayerOpen) return null;

  const isPlaying = playback.status === "playing";
  const duration = playback.durationMs || track?.durationMs || 0;
  const currentPosMs =
    previewFraction !== null ? Math.round(previewFraction * duration) : playback.positionMs;
  const seekFraction = duration > 0 ? playback.positionMs / duration : 0;
  const effectiveVolume = playback.muted ? 0 : playback.volume;
  const volumePct = Math.round(effectiveVolume * 100);
  const coverUrl = resolveArtworkUrl(track?.artworkPath);
  const hasQueue = queue.items.length > 0;
  const artistDisplay = track ? track.artist || "Unknown artist" : "";

  const handleToggleLyrics = () => {
    const next = !showLyrics;
    setShowLyrics(next);
    setFullPlayerTab(next ? "lyrics" : "artwork");
  };

  const renderArtwork = (sizeClass: string) => (
    <div
      className={`aspect-square shrink-0 overflow-hidden flex items-center justify-center ${sizeClass}`}
      style={{
        borderRadius: "25%",
        backgroundColor: track ? palette.activeArtBg : palette.variantBg,
      }}
    >
      {coverUrl ? (
        <img
          src={coverUrl}
          alt={track?.title ?? "Album artwork"}
          className="w-full h-full object-cover"
          draggable={false}
        />
      ) : (
        <Icon
          name="music"
          size={44}
          style={{ color: track ? palette.fpPrimary : palette.onSurfaceVariant }}
        />
      )}
    </div>
  );

  const renderMetadata = () => (
    <div className="w-full max-w-[680px] mx-auto flex flex-col items-center gap-1">
      <BouncingText
        text={track?.title ?? "Pick something to play"}
        className="text-[28px] leading-[36px] font-bold"
        style={{ color: palette.onBackground }}
      />
      <BouncingText
        text={artistDisplay}
        className="text-base leading-6 font-normal"
        style={{ color: palette.onBackground }}
      />
      {track?.album && (
        <BouncingText
          text={track.album}
          className="text-sm leading-5 font-normal"
          style={{ color: palette.onSurfaceVariant }}
        />
      )}
    </div>
  );

  const renderScrubberAndControls = () => (
    <div className="w-full flex flex-col">
      {/* Seek Bar */}
      <div className="flex items-center gap-2.5">
        <span
          className="w-[44px] text-right text-[11px] font-medium tabular-nums shrink-0"
          style={{ color: palette.onBackground }}
        >
          {formatDuration(currentPosMs)}
        </span>
        <CompactProgress
          value={seekFraction}
          enabled={Boolean(track) && duration > 0}
          label="Playback position"
          accentColor={palette.fpPrimary}
          trackColor={palette.trackBg}
          onPreview={setPreviewFraction}
          onCommit={(f) => void seek(Math.round(f * duration))}
          className="flex-1"
        />
        <span
          className="w-[44px] text-[11px] font-medium tabular-nums shrink-0"
          style={{ color: palette.onBackground }}
        >
          {formatDuration(duration)}
        </span>
      </div>

      {/* Transport Row: Box { CenterStart, Center, CenterEnd } */}
      <div className="relative h-[72px] w-full flex items-center justify-between">
        {/* Left: Shuffle, Repeat, Like */}
        <div className="flex items-center">
          <button
            type="button"
            disabled={!track}
            onClick={() => void toggleShuffle()}
            className="w-12 h-12 rounded-full flex items-center justify-center hover:bg-white/10 disabled:opacity-38 cursor-pointer"
            style={{
              color: playback.shuffle ? palette.fpPrimary : palette.onBackground,
            }}
            title={playback.shuffle ? "Shuffle on" : "Shuffle off"}
          >
            <span className="relative w-9 h-9 flex items-center justify-center">
              <Icon name="shuffle" size={24} />
              {playback.shuffle && (
                <span
                  className="absolute bottom-[1px] w-1 h-1 rounded-full"
                  style={{ backgroundColor: palette.fpPrimary }}
                />
              )}
            </span>
          </button>

          <button
            type="button"
            disabled={!track}
            onClick={() => void cycleRepeat()}
            className="w-12 h-12 rounded-full flex items-center justify-center hover:bg-white/10 disabled:opacity-38 cursor-pointer"
            style={{
              color:
                playback.repeat !== "off" ? palette.fpPrimary : palette.onBackground,
            }}
            title={`Repeat ${playback.repeat}`}
          >
            <span className="relative w-9 h-9 flex items-center justify-center">
              <Icon
                name={playback.repeat === "one" ? "repeat_one" : "repeat"}
                size={24}
              />
              {playback.repeat !== "off" && (
                <span
                  className="absolute bottom-[1px] w-1 h-1 rounded-full"
                  style={{ backgroundColor: palette.fpPrimary }}
                />
              )}
            </span>
          </button>

          <button
            type="button"
            disabled={!track}
            onClick={() => track && void toggleLike(track.id)}
            className="w-12 h-12 rounded-full flex items-center justify-center hover:bg-white/10 disabled:opacity-38 cursor-pointer"
            style={{
              color: track?.liked ? palette.fpPrimary : palette.onBackground,
            }}
            title={track?.liked ? "Unlike" : "Like"}
          >
            <Icon name={track?.liked ? "heart" : "liked"} size={24} />
          </button>
        </div>

        {/* Dead-Center: Previous, Play/Pause (64dp), Next */}
        <div className="absolute left-1/2 -translate-x-1/2 flex items-center">
          <button
            type="button"
            disabled={!hasQueue}
            onClick={() => void prevTrack()}
            className="w-12 h-12 rounded-full flex items-center justify-center hover:bg-white/10 disabled:opacity-38 cursor-pointer"
            style={{ color: palette.onBackground }}
            title="Previous track"
          >
            <Icon name="previous" size={24} />
          </button>

          <button
            type="button"
            disabled={!track}
            onClick={() => void togglePlayPause()}
            className="mx-4 w-16 h-16 rounded-full flex items-center justify-center disabled:opacity-38 cursor-pointer transition-transform active:scale-95"
            style={{
              backgroundColor: palette.fpPrimary,
              color: palette.fpOnPrimary,
            }}
            title={isPlaying ? "Pause" : "Play"}
          >
            <Icon name={isPlaying ? "pause" : "play"} size={32} />
          </button>

          <button
            type="button"
            disabled={!hasQueue}
            onClick={() => void nextTrack()}
            className="w-12 h-12 rounded-full flex items-center justify-center hover:bg-white/10 disabled:opacity-38 cursor-pointer"
            style={{ color: palette.onBackground }}
            title="Next track"
          >
            <Icon name="next" size={24} />
          </button>
        </div>

        {/* Right: Add to playlist, Sleep timer, Queue */}
        <div className="flex items-center relative">
          <div className="relative">
            <button
              type="button"
              disabled={!track}
              onClick={(e) => {
                e.stopPropagation();
                setPlaylistMenuOpen((v) => !v);
              }}
              className="w-12 h-12 rounded-full flex items-center justify-center hover:bg-white/10 disabled:opacity-38 cursor-pointer"
              style={{ color: palette.onBackground }}
              title="Add to playlist"
            >
              <Icon name="playlist_add" size={24} />
            </button>

            {playlistMenuOpen && track && (
              <div
                onClick={(e) => e.stopPropagation()}
                className="absolute right-0 bottom-14 z-30 w-60 p-2 rounded-2xl shadow-2xl border"
                style={{
                  backgroundColor: "var(--surface-2)",
                  borderColor: "var(--outline-strong)",
                  color: "var(--text-primary)",
                }}
              >
                <div
                  className="px-2.5 py-1.5 text-[11px] font-semibold uppercase tracking-wider"
                  style={{ color: "var(--text-secondary)" }}
                >
                  Add to playlist
                </div>
                <div className="max-h-44 overflow-y-auto space-y-0.5">
                  {playlists.map((pl) => (
                    <button
                      key={pl.id}
                      type="button"
                      onClick={() => {
                        void addTrackToPlaylist(pl.id, track.id);
                        setPlaylistMenuOpen(false);
                      }}
                      className="w-full px-2.5 py-2 rounded-xl text-left text-xs hover:bg-[var(--surface-variant)] truncate cursor-pointer"
                    >
                      {pl.name}
                    </button>
                  ))}
                </div>
                <form
                  onSubmit={(e) => {
                    e.preventDefault();
                    const trimmed = newPlaylistName.trim();
                    if (!trimmed) return;
                    void createPlaylist(trimmed, [track.id]).then(() => {
                      setNewPlaylistName("");
                      setPlaylistMenuOpen(false);
                    });
                  }}
                  className="mt-1.5 pt-1.5 border-t flex items-center gap-1.5"
                  style={{ borderColor: "var(--outline)" }}
                >
                  <input
                    type="text"
                    value={newPlaylistName}
                    onChange={(e) => setNewPlaylistName(e.target.value)}
                    placeholder="New playlist…"
                    className="flex-1 min-w-0 px-2.5 py-1.5 rounded-lg text-xs outline-none"
                    style={{
                      backgroundColor: "var(--surface-1)",
                      border: "1px solid var(--outline)",
                    }}
                  />
                  <button
                    type="submit"
                    className="px-2.5 py-1.5 rounded-lg text-xs font-medium cursor-pointer"
                    style={{
                      backgroundColor: "var(--accent)",
                      color: "var(--on-accent)",
                    }}
                  >
                    Add
                  </button>
                </form>
              </div>
            )}
          </div>

          {playback.sleepTimerRemainingMs !== null ? (
            <button
              type="button"
              onClick={() => openDialog("sleepTimer")}
              className="mx-1 px-2 py-1.5 rounded-[10px] flex items-center gap-1 text-xs font-bold tabular-nums cursor-pointer"
              style={{
                backgroundColor: palette.fpContainerAlpha85,
                color: palette.fpOnContainer,
              }}
              title="Sleep timer"
            >
              <Icon name="timer" size={16} style={{ color: palette.fpPrimary }} />
              <span>{formatDuration(playback.sleepTimerRemainingMs)}</span>
            </button>
          ) : (
            <button
              type="button"
              onClick={() => openDialog("sleepTimer")}
              className="w-12 h-12 rounded-full flex items-center justify-center hover:bg-white/10 cursor-pointer"
              style={{ color: palette.onBackground }}
              title="Sleep timer"
            >
              <Icon name="timer" size={24} />
            </button>
          )}

          <button
            type="button"
            onClick={() => setQueueDrawerOpen(true)}
            className="w-12 h-12 rounded-full flex items-center justify-center hover:bg-white/10 cursor-pointer"
            style={{ color: palette.onBackground }}
            title="Playback queue"
          >
            <Icon name="queue" size={24} />
          </button>
        </div>
      </div>

      {/* Centered Volume Row */}
      <div className="pt-1 flex items-center justify-center">
        <button
          type="button"
          onClick={() => void toggleMute()}
          className="w-[44px] flex items-center justify-end cursor-pointer"
          style={{ color: palette.onSurfaceVariant }}
          title={playback.muted ? "Unmute" : "Mute"}
        >
          <Icon name={volumePct === 0 ? "muted" : "volume"} size={18} />
        </button>
        <div className="w-[10px]" />
        <CompactProgress
          value={effectiveVolume}
          enabled={Boolean(track) || true}
          label="Volume"
          accentColor={palette.fpPrimary}
          trackColor={palette.trackBg}
          onCommit={(f) => void setVolume(f)}
          className="w-[140px] shrink-0"
        />
        <div className="w-[10px]" />
        <span
          className="w-[44px] text-[11px] font-medium tabular-nums"
          style={{ color: palette.onBackground }}
        >
          {volumePct}%
        </span>
      </div>
    </div>
  );

  return (
    <div
      className="fixed inset-0 z-40 flex flex-col overflow-hidden select-none"
      style={
        {
          backgroundColor: palette.deepBg,
          color: palette.onBackground,
          "--fp-accent": palette.fpPrimary,
          "--fp-on-accent": palette.fpOnPrimary,
        } as React.CSSProperties
      }
      onClick={() => {
        if (playlistMenuOpen) setPlaylistMenuOpen(false);
      }}
    >
      <style>{`
        @keyframes fp-ambient-pulse {
          0% {
            opacity: 0.35;
            transform: scale(0.95);
          }
          100% {
            opacity: 0.95;
            transform: scale(1.08);
          }
        }
        @keyframes fp-ambient-drift-a {
          0% {
            opacity: 0.78;
            transform: translate3d(-2%, -2%, 0) scale(1);
          }
          100% {
            opacity: 1;
            transform: translate3d(2.5%, 2%, 0) scale(1.06);
          }
        }
        @keyframes fp-ambient-drift-b {
          0% {
            opacity: 0.72;
            transform: translate3d(2%, 1.5%, 0) scale(1.04);
          }
          100% {
            opacity: 0.96;
            transform: translate3d(-2.5%, -2%, 0) scale(0.98);
          }
        }
      `}</style>

      {/* Multi-stop diagonal base wash */}
      <div
        className="absolute inset-0 pointer-events-none"
        style={{
          background: `linear-gradient(135deg, ${palette.diagStart} 0%, ${palette.deepBg} 50%, ${palette.diagEnd} 100%)`,
        }}
      />

      {/* Blurred, zoomed album cover texture + protective scrim (dynamic preset only) */}
      {palette.isDynamic && coverUrl && (
        <>
          <div
            className="absolute inset-0 pointer-events-none scale-[1.68] blur-[96px] opacity-[0.28]"
            style={{
              backgroundImage: `url("${coverUrl}")`,
              backgroundSize: "cover",
              backgroundPosition: "center",
            }}
          />
          <div
            className="absolute inset-0 pointer-events-none"
            style={{ background: palette.scrim }}
          />
        </>
      )}

      {/* Animated radial ambient glow (1-to-1 port of FullPlayerScreen.kt ambientPulse) */}
      <div
        className="absolute inset-0 pointer-events-none"
        style={{
          background: `radial-gradient(circle at 50% 45%, ${palette.primaryGlowMin} 0%, transparent 72%)`,
        }}
      />
      <div
        className="absolute inset-0 pointer-events-none"
        style={{
          background: `radial-gradient(circle at 50% 45%, ${palette.primaryGlowMax} 0%, ${palette.primaryGlowMaxMid} 42%, transparent 75%)`,
          animation: "fp-ambient-pulse 4s cubic-bezier(0.4, 0, 0.2, 1) infinite alternate",
          willChange: "transform, opacity",
        }}
      />

      {/* Overlapping drifting elliptical ambient color washes */}
      <div
        className="absolute inset-0 pointer-events-none"
        style={{
          background: [
            `radial-gradient(ellipse 92% 48% at 22% 24%, ${palette.primaryWash} 0%, ${palette.primaryWashMid} 45%, transparent 100%)`,
            `radial-gradient(ellipse 88% 52% at 78% 74%, ${palette.secondaryWash} 0%, ${palette.secondaryWashMid} 45%, transparent 100%)`,
          ].join(", "),
          animation: "fp-ambient-drift-a 9s ease-in-out infinite alternate",
          willChange: "transform, opacity",
        }}
      />
      <div
        className="absolute inset-0 pointer-events-none"
        style={{
          background: [
            `radial-gradient(ellipse 75% 44% at 72% 22%, ${palette.tertiaryWash} 0%, ${palette.tertiaryWashMid} 45%, transparent 100%)`,
            `radial-gradient(ellipse 90% 42% at 30% 76%, ${palette.bridgeWash} 0%, ${palette.bridgeWashMid} 45%, transparent 100%)`,
          ].join(", "),
          animation: "fp-ambient-drift-b 12s ease-in-out infinite alternate",
          willChange: "transform, opacity",
        }}
      />

      {/* Foreground Content (padding = 28dp, spacedBy = 16dp) */}
      <div className="relative z-10 flex-1 min-h-0 flex flex-col p-[28px] gap-4">
        {/* Header Bar */}
        <header className="flex items-center w-full shrink-0">
          <img
            src={appIconUrl}
            alt="Umimuko Music"
            className="w-9 h-9 object-contain shrink-0"
            draggable={false}
          />
          <div className="w-3" />
          <span
            className="text-sm font-bold tracking-[0.1px]"
            style={{ color: palette.fpPrimary }}
          >
            NOW PLAYING
          </span>
          <div className="flex-1" />

          <button
            type="button"
            onClick={handleToggleLyrics}
            className="w-10 h-10 rounded-full flex items-center justify-center hover:bg-white/10 cursor-pointer"
            style={{
              color: showLyrics ? palette.fpPrimary : palette.onBackground,
            }}
            title="Toggle lyrics"
            aria-label="Toggle lyrics"
          >
            <Icon name="lyrics" size={24} />
          </button>

          <div className="w-2" />

          <button
            type="button"
            onClick={() => setFullPlayerOpen(false)}
            className="px-3 py-2 rounded-full flex items-center gap-2 text-sm font-medium hover:bg-white/10 cursor-pointer"
            style={{ color: palette.fpPrimary }}
          >
            <Icon name="collapse" size={24} />
            <span>Back</span>
          </button>
        </header>

        {/* Body */}
        {showLyrics && isWide ? (
          <div className="flex-1 min-h-0 flex items-center gap-9">
            <div className="flex-[1.1] min-w-0 h-full flex flex-col items-center justify-center">
              <div className="flex-1 min-h-0 w-full flex items-center justify-center">
                {renderArtwork("w-[min(34vw,42vh,360px)] min-w-[100px]")}
              </div>
              <div className="h-[14px] shrink-0" />
              {renderMetadata()}
              <div className="h-[14px] shrink-0" />
              {renderScrubberAndControls()}
            </div>

            <div className="flex-[1.3] min-w-0 h-full px-2">
              <SpicyLyricsView
                onBackgroundColor={palette.onBackground}
                accentColor={palette.fpPrimary}
              />
            </div>
          </div>
        ) : showLyrics && !isWide ? (
          <div className="flex-1 min-h-0 flex flex-col gap-3">
            <div className="flex-1 min-h-0 px-1">
              <SpicyLyricsView
                onBackgroundColor={palette.onBackground}
                accentColor={palette.fpPrimary}
              />
            </div>
            {renderScrubberAndControls()}
          </div>
        ) : (
          <div className="flex-1 min-h-0 flex flex-col items-center justify-between gap-4">
            <div className="flex-1 min-h-0 w-full flex items-center justify-center">
              {renderArtwork("w-[min(48vh,50vw,440px)] min-w-[100px]")}
            </div>

            {renderMetadata()}

            <div className="w-full max-w-[720px] mx-auto">
              {renderScrubberAndControls()}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
