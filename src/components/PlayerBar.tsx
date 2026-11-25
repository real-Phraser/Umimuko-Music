import { useCallback, useRef, useState } from "react";
import { api } from "../api/tauri";
import { useLibraryStore } from "../store/useLibraryStore";
import { usePlayerStore } from "../store/usePlayerStore";
import { formatDuration } from "../utils/format";
import { Artwork } from "./Artwork";
import { Icon } from "./Icon";

/** Port of CompactProgress.kt: thin 3px track at rest, thickens with thumb on press/hover. */
function CompactProgress({
  value,
  enabled,
  label,
  className = "",
  onPreview,
  onCommit,
}: {
  value: number;
  enabled: boolean;
  label: string;
  className?: string;
  onPreview?: (fraction: number | null) => void;
  onCommit: (fraction: number) => void;
}) {
  const trackRef = useRef<HTMLDivElement>(null);
  const [dragging, setDragging] = useState(false);
  const [hovered, setHovered] = useState(false);
  const [localFraction, setLocalFraction] = useState(0);

  const computeFraction = useCallback((clientX: number) => {
    const el = trackRef.current;
    if (!el) return 0;
    const rect = el.getBoundingClientRect();
    const pad = 8;
    const usable = Math.max(1, rect.width - pad * 2);
    return Math.max(0, Math.min(1, (clientX - rect.left - pad) / usable));
  }, []);

  const active = dragging;
  const fraction = Math.max(0, Math.min(1, active ? localFraction : value));
  const pct = fraction * 100;
  const trackHeight = active ? 8 : hovered ? 5 : 3;
  const showThumb = active || hovered;

  return (
    <div
      ref={trackRef}
      role="slider"
      tabIndex={enabled ? 0 : -1}
      aria-label={label}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={Math.round(fraction * 100)}
      onPointerEnter={() => setHovered(true)}
      onPointerLeave={() => setHovered(false)}
      onPointerDown={(e) => {
        if (!enabled) return;
        e.currentTarget.setPointerCapture(e.pointerId);
        const f = computeFraction(e.clientX);
        setDragging(true);
        setLocalFraction(f);
        onPreview?.(f);
      }}
      onPointerMove={(e) => {
        if (!dragging) return;
        const f = computeFraction(e.clientX);
        setLocalFraction(f);
        onPreview?.(f);
      }}
      onPointerUp={(e) => {
        if (!dragging) return;
        const f = computeFraction(e.clientX);
        setDragging(false);
        onPreview?.(null);
        onCommit(f);
        if (e.currentTarget.hasPointerCapture(e.pointerId)) {
          e.currentTarget.releasePointerCapture(e.pointerId);
        }
      }}
      onPointerCancel={() => {
        setDragging(false);
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
      className={`relative h-6 px-2 flex items-center select-none outline-none ${
        enabled ? "cursor-pointer" : "opacity-35 cursor-default"
      } ${className}`}
    >
      <div
        className="relative w-full rounded-full transition-[height] duration-150"
        style={{
          height: trackHeight,
          backgroundColor: "color-mix(in srgb, var(--bar-secondary) 22%, transparent)",
        }}
      >
        <div
          className="h-full rounded-full"
          style={{
            width: `${pct}%`,
            backgroundColor: "var(--accent)",
          }}
        />
        {showThumb && enabled && (
          <div
            className="absolute top-1/2 -translate-y-1/2 -translate-x-1/2 rounded-full shadow-sm transition-transform"
            style={{
              left: `${pct}%`,
              width: active ? 14 : 11,
              height: active ? 14 : 11,
              backgroundColor: "var(--accent)",
            }}
          />
        )}
      </div>
    </div>
  );
}

function formatSleepRemaining(remainingMs: number): string {
  const totalSec = Math.max(0, Math.ceil(remainingMs / 1000));
  const mins = Math.floor(totalSec / 60);
  const secs = totalSec % 60;
  return `${mins}:${secs.toString().padStart(2, "0")}`;
}

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
  const openDialog = useLibraryStore((s) => s.openDialog);

  const [playlistMenuOpen, setPlaylistMenuOpen] = useState(false);
  const [seekPreview, setSeekPreview] = useState<number | null>(null);
  const [volumePreview, setVolumePreview] = useState<number | null>(null);

  const track = playback.currentTrack;
  const isPlaying = playback.status === "playing";
  const duration = playback.durationMs || track?.durationMs || 0;
  const progress =
    duration > 0 ? Math.max(0, Math.min(1, playback.positionMs / duration)) : 0;
  const displayTimeMs =
    seekPreview !== null ? Math.round(seekPreview * duration) : playback.positionMs;

  const effectiveVolume = playback.muted ? 0 : playback.volume;
  const displayedVolumePct = Math.round(
    (volumePreview !== null ? volumePreview : effectiveVolume) * 100
  );
  const sleepActive =
    playback.sleepTimerRemainingMs !== null && playback.sleepTimerRemainingMs > 0;

  return (
    <footer
      className="shrink-0 px-4 py-2 adaptive-transition select-none"
      style={{
        backgroundColor: "var(--surface-2)",
        color: "var(--bar-content)",
      }}
    >
      {/* 72dp inner row matching PlayerBar.kt line 56 */}
      <div className="h-[72px] w-full flex items-center gap-4">
        {/* Left: 56dp Artwork + Title + Artist · Album (weight 1) */}
        <div className="flex-1 min-w-0 flex items-center gap-3">
          <button
            type="button"
            onClick={() => track && setFullPlayerOpen(true)}
            disabled={!track}
            className="flex items-center gap-3 min-w-0 text-left cursor-pointer disabled:cursor-default group"
          >
            <Artwork
              artworkPath={track?.artworkPath}
              accentColor={track?.accentColor}
              alt={track?.title ?? "No track"}
              size="md"
              rounded="rounded-[14px]"
              className="!w-14 !h-14"
            />
            <div className="min-w-0 flex flex-col gap-[2px]">
              <div
                className={`text-[14px] leading-[20px] font-semibold truncate ${
                  track ? "group-hover:underline" : ""
                }`}
              >
                {track?.title ?? "Pick something to play"}
              </div>
              <div
                className="text-[11px] leading-[16px] font-normal truncate"
                style={{ color: "var(--bar-secondary)" }}
              >
                {track ? (
                  <>
                    <span>{track.artist}</span>
                    {track.album && (
                      <>
                        <span className="mx-2 opacity-62">·</span>
                        <span className="opacity-62">{track.album}</span>
                      </>
                    )}
                  </>
                ) : (
                  "Your music, your space"
                )}
              </div>
            </div>
          </button>
        </div>

        {/* Center: Transport above Progress (weight 1.5, max 560dp) */}
        <div className="flex-[1.5] max-w-[560px] min-w-0 flex flex-col items-center">
          <div className="flex items-center gap-1">
            {/* Shuffle with ActiveControlGlyph dot */}
            <button
              type="button"
              onClick={() => void toggleShuffle()}
              className="relative w-9 h-9 rounded-full flex items-center justify-center hover:bg-[var(--surface-variant)] cursor-pointer"
              style={{
                color: playback.shuffle ? "var(--bar-accent)" : "var(--bar-content)",
              }}
              aria-label={playback.shuffle ? "Shuffle on" : "Shuffle off"}
              title={playback.shuffle ? "Shuffle on" : "Shuffle off"}
            >
              <Icon name="shuffle" size={24} />
              {playback.shuffle && (
                <span
                  className="absolute bottom-[1px] w-1 h-1 rounded-full"
                  style={{ backgroundColor: "var(--accent)" }}
                />
              )}
            </button>

            <button
              type="button"
              onClick={() => void prevTrack()}
              className="w-10 h-10 rounded-full flex items-center justify-center hover:bg-[var(--surface-variant)] cursor-pointer"
              style={{ color: "var(--bar-content)" }}
              aria-label="Previous track"
              title="Previous track"
            >
              <Icon name="previous" size={24} />
            </button>

            {/* 48dp circular Play/Pause (PlayerBar.kt line 78) */}
            <button
              type="button"
              disabled={!track}
              onClick={() => void togglePlayPause()}
              className="w-12 h-12 rounded-full flex items-center justify-center shadow-md transition-transform hover:scale-105 active:scale-95 disabled:opacity-40 cursor-pointer"
              style={{
                backgroundColor: "var(--accent)",
                color: "var(--on-accent)",
              }}
              aria-label={isPlaying ? "Pause" : "Play"}
              title={isPlaying ? "Pause" : "Play"}
            >
              <Icon name={isPlaying ? "pause" : "play"} size={24} />
            </button>

            <button
              type="button"
              onClick={() => void nextTrack()}
              className="w-10 h-10 rounded-full flex items-center justify-center hover:bg-[var(--surface-variant)] cursor-pointer"
              style={{ color: "var(--bar-content)" }}
              aria-label="Next track"
              title="Next track"
            >
              <Icon name="next" size={24} />
            </button>

            {/* Repeat with ActiveControlGlyph dot */}
            <button
              type="button"
              onClick={() => void cycleRepeat()}
              className="relative w-9 h-9 rounded-full flex items-center justify-center hover:bg-[var(--surface-variant)] cursor-pointer"
              style={{
                color:
                  playback.repeat !== "off"
                    ? "var(--bar-accent)"
                    : "var(--bar-content)",
              }}
              aria-label={`Repeat ${playback.repeat}`}
              title={`Repeat ${playback.repeat}`}
            >
              <Icon
                name={playback.repeat === "one" ? "repeat_one" : "repeat"}
                size={24}
              />
              {playback.repeat !== "off" && (
                <span
                  className="absolute bottom-[1px] w-1 h-1 rounded-full"
                  style={{ backgroundColor: "var(--accent)" }}
                />
              )}
            </button>
          </div>

          <div className="w-full flex items-center gap-2">
            <span className="text-[11px] leading-[16px] tabular-nums w-[38px] text-right shrink-0">
              {formatDuration(displayTimeMs)}
            </span>
            <CompactProgress
              value={progress}
              enabled={Boolean(track && duration > 0)}
              label="Playback position"
              className="flex-1"
              onPreview={setSeekPreview}
              onCommit={(f) => void seek(Math.round(f * duration))}
            />
            <span className="text-[11px] leading-[16px] tabular-nums w-[38px] shrink-0">
              {formatDuration(duration)}
            </span>
          </div>
        </div>

        {/* Right: Actions + Single-row Volume (PlayerBar.kt lines 97-141) */}
        <div className="flex items-center justify-end gap-[2px] shrink-0">
          <button
            type="button"
            disabled={!track}
            onClick={() => track && void toggleLike(track.id)}
            className="w-9 h-9 rounded-full flex items-center justify-center hover:bg-[var(--surface-variant)] cursor-pointer disabled:opacity-40"
            style={{
              color: track?.liked ? "var(--bar-accent)" : "var(--bar-content)",
            }}
            aria-label={track?.liked ? "Unlike current track" : "Like current track"}
            title={track?.liked ? "Unlike current track" : "Like current track"}
          >
            <Icon name={track?.liked ? "heart" : "liked"} size={24} />
          </button>

          <div className="relative">
            <button
              type="button"
              disabled={!track}
              onClick={() => setPlaylistMenuOpen((v) => !v)}
              className="w-9 h-9 rounded-full flex items-center justify-center hover:bg-[var(--surface-variant)] cursor-pointer disabled:opacity-40"
              style={{ color: "var(--bar-content)" }}
              aria-label="Add current track to playlist"
              title="Add current track to playlist"
            >
              <Icon name="playlist_add" size={24} />
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
                    color: "var(--text-primary)",
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
                        className="w-full px-3.5 py-1.5 text-left truncate hover:bg-[var(--surface-variant)] cursor-pointer"
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
            onClick={() => openDialog("equalizer")}
            className="w-9 h-9 rounded-full flex items-center justify-center hover:bg-[var(--surface-variant)] cursor-pointer"
            style={{ color: "var(--bar-content)" }}
            aria-label="Audio DSP & Equalizer"
            title="Audio DSP & Equalizer"
          >
            <Icon name="levels" size={24} />
          </button>

          {sleepActive ? (
            <button
              type="button"
              onClick={() => openDialog("sleepTimer")}
              className="px-1.5 py-1 rounded-[8px] flex items-center gap-1 cursor-pointer hover:opacity-90"
              style={{
                backgroundColor: "var(--accent-container)",
                color: "var(--on-accent-container)",
              }}
              aria-label="Sleep Timer"
              title="Sleep Timer"
            >
              <Icon name="timer" size={14} style={{ color: "var(--accent)" }} />
              <span className="min-w-[44px] text-[11px] leading-[16px] font-bold tabular-nums text-center">
                {formatSleepRemaining(playback.sleepTimerRemainingMs!)}
              </span>
            </button>
          ) : (
            <button
              type="button"
              onClick={() => openDialog("sleepTimer")}
              className="w-9 h-9 rounded-full flex items-center justify-center hover:bg-[var(--surface-variant)] cursor-pointer"
              style={{ color: "var(--bar-content)" }}
              aria-label="Sleep Timer"
              title="Sleep Timer"
            >
              <Icon name="timer" size={24} />
            </button>
          )}

          <button
            type="button"
            onClick={() => setQueueDrawerOpen(!queueDrawerOpen)}
            className="w-9 h-9 rounded-full flex items-center justify-center hover:bg-[var(--surface-variant)] cursor-pointer"
            style={{
              color: queueDrawerOpen ? "var(--bar-accent)" : "var(--bar-content)",
            }}
            aria-label="Open playback queue"
            title="Open playback queue"
          >
            <Icon name="queue" size={24} />
          </button>

          <button
            type="button"
            onClick={() => void api.toggleMiniPlayer(true)}
            className="w-9 h-9 rounded-full flex items-center justify-center hover:bg-[var(--surface-variant)] cursor-pointer"
            style={{ color: "var(--bar-content)" }}
            aria-label="Collapse to floating player"
            title="Collapse to floating player"
          >
            <Icon name="bubble" size={24} />
          </button>

          <button
            type="button"
            disabled={!track}
            onClick={() => setFullPlayerOpen(!fullPlayerOpen)}
            className="w-9 h-9 rounded-full flex items-center justify-center hover:bg-[var(--surface-variant)] cursor-pointer disabled:opacity-40"
            style={{
              color: fullPlayerOpen ? "var(--bar-accent)" : "var(--bar-content)",
            }}
            aria-label="Open full-screen player"
            title="Open full-screen player"
          >
            <Icon name="expand" size={24} />
          </button>

          <button
            type="button"
            onClick={() => void toggleMute()}
            className="ml-2 w-7 h-7 rounded-full flex items-center justify-center hover:bg-[var(--surface-variant)] cursor-pointer"
            style={{ color: "var(--bar-secondary)" }}
            aria-label={playback.muted ? "Unmute" : "Mute"}
            title={playback.muted ? "Unmute" : "Mute"}
          >
            <Icon
              name={playback.muted || playback.volume === 0 ? "muted" : "volume"}
              size={20}
            />
          </button>

          <div className="ml-1.5 flex items-center">
            <CompactProgress
              value={effectiveVolume}
              enabled
              label="Volume"
              className="w-[72px] lg:w-[104px]"
              onPreview={setVolumePreview}
              onCommit={(f) => {
                if (playback.muted && f > 0) {
                  void toggleMute();
                }
                void setVolume(f);
              }}
            />
          </div>

          <span className="hidden lg:inline-block w-[38px] text-[11px] leading-[16px] tabular-nums text-right">
            {displayedVolumePct}%
          </span>
        </div>
      </div>
    </footer>
  );
}
