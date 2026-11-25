import { useEffect, useMemo, useRef, useState } from "react";
import { useLibraryStore } from "../../store/useLibraryStore";
import { usePlayerStore } from "../../store/usePlayerStore";
import type { LyricLine, LyricWord } from "../../types/ipc";
import { Icon } from "../Icon";

const INTRO_DOTS_THRESHOLD_MS = 1500;
const INTERLUDE_GAP_THRESHOLD_MS = 5000;
const MANUAL_SCROLL_RESUME_MS = 4000;
const CLOCK_SNAP_DRIFT_MS = 350;
const CLOCK_MAX_AHEAD_MS = 600;
const ENVELOPE_ANTICIPATION_MS = 100;
const ENVELOPE_ATTACK_TOTAL_MS = 140;
const ENVELOPE_RELEASE_MS = 220;

export const ROMANIZED_STORAGE_KEY = "umimuko_show_romanized";

export function getShowRomanizedPref(): boolean {
  try {
    return localStorage.getItem(ROMANIZED_STORAGE_KEY) !== "false";
  } catch {
    return true;
  }
}

export function setShowRomanizedPref(enabled: boolean) {
  try {
    localStorage.setItem(ROMANIZED_STORAGE_KEY, enabled ? "true" : "false");
    window.dispatchEvent(new CustomEvent("umimuko:romanized-changed", { detail: enabled }));
  } catch {
    // ignore storage failures
  }
}

function clamp01(value: number): number {
  return value < 0 ? 0 : value > 1 ? 1 : value;
}

function smootherStep(t: number): number {
  const x = clamp01(t);
  return x * x * x * (x * (x * 6 - 15) + 10);
}

function singingEnvelope(startMs: number, endMs: number, nowMs: number): number {
  const attackStart = startMs - ENVELOPE_ANTICIPATION_MS;
  if (nowMs <= attackStart) return 0;
  if (nowMs >= endMs + ENVELOPE_RELEASE_MS) return 0;
  const attack = smootherStep((nowMs - attackStart) / ENVELOPE_ATTACK_TOTAL_MS);
  const release =
    nowMs <= endMs ? 1 : 1 - smootherStep((nowMs - endMs) / ENVELOPE_RELEASE_MS);
  return Math.min(attack, release);
}

function InterludeDotsRow({
  progress,
  accentColor,
  onBackgroundColor,
}: {
  progress: number;
  accentColor: string;
  onBackgroundColor: string;
}) {
  return (
    <div className="w-full px-4 py-2.5 flex items-center gap-2.5">
      {[0, 1, 2].map((dotIndex) => {
        const dotStart = dotIndex / 3;
        const dotFill = clamp01((progress - dotStart) * 3);
        const isFilling = dotFill > 0 && dotFill < 1;
        const scale = 0.85 + 0.3 * dotFill + (isFilling ? 0.05 : 0);
        const alpha = 0.32 + 0.68 * dotFill;
        return (
          <span
            key={dotIndex}
            className="w-[11px] h-[11px] rounded-full inline-block transition-transform duration-150"
            style={{
              transform: `scale(${scale.toFixed(3)})`,
              opacity: alpha,
              backgroundColor:
                dotFill > 0.1
                  ? `color-mix(in srgb, ${onBackgroundColor} ${Math.round((1 - 0.35 * dotFill) * 100)}%, ${accentColor})`
                  : onBackgroundColor,
            }}
          />
        );
      })}
    </div>
  );
}

interface WordGroup {
  tokens: LyricWord[];
  startMs: number;
  endMs: number;
  isSplitWord: boolean;
}

function groupWords(words: LyricWord[]): WordGroup[] {
  const groups: WordGroup[] = [];
  let current: LyricWord[] = [];

  for (const w of words) {
    current.push(w);
    const endsWithSpace = /\s$/.test(w.text);
    if (endsWithSpace) {
      groups.push({
        tokens: current,
        startMs: Math.min(...current.map((t) => t.startMs)),
        endMs: Math.max(...current.map((t) => t.endMs)),
        isSplitWord: current.length > 1,
      });
      current = [];
    }
  }
  if (current.length > 0) {
    groups.push({
      tokens: current,
      startMs: Math.min(...current.map((t) => t.startMs)),
      endMs: Math.max(...current.map((t) => t.endMs)),
      isSplitWord: current.length > 1,
    });
  }
  return groups;
}

function SyllableSweepLine({
  words,
  isLineActive,
  isLinePast,
  nowMs,
  litColor,
  unlitColor,
  accentColor,
  isBackground = false,
}: {
  words: LyricWord[];
  isLineActive: boolean;
  isLinePast: boolean;
  nowMs: number;
  litColor: string;
  unlitColor: string;
  accentColor: string;
  isBackground?: boolean;
}) {
  const groups = useMemo(() => groupWords(words), [words]);
  const wholeLiftPx = isBackground ? -2 : -3;
  const splitLiftPx = isBackground ? -1.5 : -2.2;

  return (
    <div
      className={`w-full flex flex-wrap items-baseline ${
        isBackground
          ? "text-[17px] leading-[24px] font-semibold"
          : "text-[28px] leading-[36px] tracking-[-0.3px] font-extrabold"
      }`}
    >
      {groups.map((group, gIdx) => {
        const env =
          isLineActive && !isLinePast
            ? singingEnvelope(group.startMs, group.endMs, nowMs)
            : 0;
        const lift = (group.isSplitWord ? splitLiftPx : wholeLiftPx) * env;
        const scale = 1 + (group.isSplitWord ? 0.03 : 0.04) * env;

        return (
          <span
            key={`${group.startMs}-${gIdx}`}
            className="inline-flex items-baseline whitespace-pre"
            style={{
              transform:
                env > 0.001
                  ? `translate3d(0, ${lift.toFixed(2)}px, 0) scale(${scale.toFixed(4)})`
                  : "translate3d(0, 0, 0) scale(1)",
              willChange: isLineActive ? "transform" : "auto",
            }}
          >
            {group.tokens.map((token, tIdx) => {
              let progress = 0;
              if (isLinePast) progress = 1;
              else if (isLineActive) {
                if (nowMs <= token.startMs) progress = 0;
                else if (nowMs >= token.endMs || token.endMs <= token.startMs) progress = 1;
                else progress = clamp01((nowMs - token.startMs) / (token.endMs - token.startMs));
              }

              const tokenEnv =
                isLineActive && !isLinePast
                  ? singingEnvelope(token.startMs, token.endMs, nowMs)
                  : 0;
              const feather = 0.2;
              const startFade = clamp01(progress * (1 + feather) - feather) * 100;
              const endFade = clamp01(progress * (1 + feather)) * 100;

              let textStyle: React.CSSProperties;
              if (progress <= 0.001) {
                textStyle = { color: unlitColor };
              } else if (progress >= 0.999) {
                textStyle = { color: litColor };
              } else {
                textStyle = {
                  backgroundImage: `linear-gradient(90deg, ${litColor} 0%, ${litColor} ${startFade.toFixed(1)}%, ${unlitColor} ${endFade.toFixed(1)}%, ${unlitColor} 100%)`,
                  WebkitBackgroundClip: "text",
                  backgroundClip: "text",
                  WebkitTextFillColor: "transparent",
                };
              }

              return (
                <span
                  key={`${token.startMs}-${tIdx}`}
                  className="inline-block whitespace-pre"
                  style={{
                    ...textStyle,
                    textShadow:
                      tokenEnv > 0.04
                        ? `0 0 14px color-mix(in srgb, ${accentColor} ${Math.round(tokenEnv * 40)}%, transparent)`
                        : "none",
                  }}
                >
                  {token.text}
                </span>
              );
            })}
          </span>
        );
      })}
    </div>
  );
}

interface SpicyLyricsViewProps {
  onBackgroundColor?: string;
  accentColor?: string;
}

export function SpicyLyricsView({
  onBackgroundColor = "#F5F7FA",
  accentColor = "var(--fp-accent, var(--accent))",
}: SpicyLyricsViewProps) {
  const lyrics = usePlayerStore((s) => s.lyrics);
  const lyricsLoading = usePlayerStore((s) => s.lyricsLoading);
  const currentTrackId = usePlayerStore((s) => s.playback.currentTrackId);
  const status = usePlayerStore((s) => s.playback.status);
  const positionMs = usePlayerStore((s) => s.playback.positionMs);
  const seek = usePlayerStore((s) => s.seek);
  const refreshLyrics = usePlayerStore((s) => s.refreshLyrics);
  const openDialog = useLibraryStore((s) => s.openDialog);
  const setFullPlayerOpen = usePlayerStore((s) => s.setFullPlayerOpen);
  const hasApiKey = useLibraryStore(() => false); // Checked via settings store below

  const [interpolatedMs, setInterpolatedMs] = useState(positionMs);
  const [userScrolled, setUserScrolled] = useState(false);
  const [hoveredIdx, setHoveredIdx] = useState<number | null>(null);
  const [showRomanized, setShowRomanized] = useState(getShowRomanizedPref);

  const latestPositionRef = useRef(positionMs);
  const smoothTimeRef = useRef(positionMs);
  const pendingCorrectionRef = useRef(0);
  const containerRef = useRef<HTMLDivElement | null>(null);
  const lineRefs = useRef<Array<HTMLButtonElement | null>>([]);
  const introRef = useRef<HTMLDivElement | null>(null);
  const resumeTimerRef = useRef<number | null>(null);
  const isProgrammaticScrollRef = useRef(false);

  void hasApiKey;

  useEffect(() => {
    const onPrefChange = (e: Event) => {
      const detail = (e as CustomEvent<boolean>).detail;
      setShowRomanized(Boolean(detail));
    };
    window.addEventListener("umimuko:romanized-changed", onPrefChange);
    return () => window.removeEventListener("umimuko:romanized-changed", onPrefChange);
  }, []);

  // Sync smooth clock when coarse audio ticks arrive
  useEffect(() => {
    latestPositionRef.current = positionMs;
    if (status !== "playing" || lyrics?.type === "plain") {
      smoothTimeRef.current = positionMs;
      pendingCorrectionRef.current = 0;
      setInterpolatedMs(positionMs);
      return;
    }
    const drift = positionMs - smoothTimeRef.current;
    if (Math.abs(drift) > CLOCK_SNAP_DRIFT_MS) {
      smoothTimeRef.current = positionMs;
      pendingCorrectionRef.current = 0;
      setInterpolatedMs(positionMs);
    } else {
      pendingCorrectionRef.current = drift;
    }
  }, [positionMs, status, lyrics?.type]);

  // 60fps/120fps smooth playback clock interpolation matching SpicyLyricsView.kt
  useEffect(() => {
    if (status !== "playing" || lyrics?.type === "plain") return;
    let rafId = 0;
    let lastTime = 0;

    const tick = (now: number) => {
      const dtMs = lastTime === 0 ? 0 : Math.min(100, Math.max(0, now - lastTime));
      lastTime = now;

      let smooth = smoothTimeRef.current + dtMs;
      const pending = pendingCorrectionRef.current;
      if (Math.abs(pending) > 0.5) {
        const step = pending * 0.25;
        smooth += step;
        pendingCorrectionRef.current = pending - step;
      }
      if (Math.abs(smooth - latestPositionRef.current) > CLOCK_MAX_AHEAD_MS) {
        smooth = latestPositionRef.current;
        pendingCorrectionRef.current = 0;
      }

      smoothTimeRef.current = Math.max(0, smooth);
      setInterpolatedMs(smoothTimeRef.current);
      rafId = requestAnimationFrame(tick);
    };

    rafId = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(rafId);
  }, [status, lyrics?.type]);

  const isStatic = lyrics?.type === "plain";

  const activeIndex = useMemo(() => {
    if (!lyrics || lyrics.lines.length === 0 || isStatic) return -1;
    let idx = -1;
    for (let i = 0; i < lyrics.lines.length; i += 1) {
      if (interpolatedMs >= lyrics.lines[i].startMs) {
        idx = i;
      } else {
        break;
      }
    }
    return idx;
  }, [lyrics, isStatic, interpolatedMs]);

  const firstLine = lyrics?.lines[0] ?? null;
  const showIntroDots =
    !isStatic &&
    firstLine !== null &&
    interpolatedMs < firstLine.startMs - INTRO_DOTS_THRESHOLD_MS;

  // Auto-scroll active line to ~32% sweet spot from top
  useEffect(() => {
    if (isStatic || userScrolled) return;
    const container = containerRef.current;
    if (!container) return;

    const targetEl =
      activeIndex >= 0
        ? lineRefs.current[activeIndex]
        : showIntroDots
          ? introRef.current
          : null;
    if (!targetEl) return;

    const sweetSpot = Math.max(80, container.clientHeight * 0.32);
    const containerRect = container.getBoundingClientRect();
    const targetRect = targetEl.getBoundingClientRect();
    const delta = targetRect.top - containerRect.top - sweetSpot;

    if (Math.abs(delta) > 2) {
      isProgrammaticScrollRef.current = true;
      container.scrollBy({ top: delta, behavior: "smooth" });
      window.setTimeout(() => {
        isProgrammaticScrollRef.current = false;
      }, 360);
    }
  }, [activeIndex, showIntroDots, userScrolled, isStatic]);

  useEffect(() => {
    return () => {
      if (resumeTimerRef.current !== null) {
        window.clearTimeout(resumeTimerRef.current);
      }
    };
  }, []);

  const markUserScroll = () => {
    if (isStatic) return;
    setUserScrolled(true);
    if (resumeTimerRef.current !== null) {
      window.clearTimeout(resumeTimerRef.current);
    }
    resumeTimerRef.current = window.setTimeout(() => {
      setUserScrolled(false);
    }, MANUAL_SCROLL_RESUME_MS);
  };

  if (lyricsLoading) {
    return (
      <div className="h-full w-full flex flex-col items-center justify-center gap-3">
        <div
          className="w-7 h-7 rounded-full border-2 border-t-transparent animate-spin"
          style={{ borderColor: accentColor, borderTopColor: "transparent" }}
        />
        <span
          className="text-xs font-medium"
          style={{ color: onBackgroundColor, opacity: 0.72 }}
        >
          Fetching Spicy Lyrics...
        </span>
      </div>
    );
  }

  if (!lyrics || lyrics.type === "none" || lyrics.lines.length === 0) {
    return (
      <div className="h-full w-full flex items-center justify-center p-6">
        <div className="flex flex-col items-center text-center gap-2.5 max-w-sm">
          <Icon
            name="lyrics"
            size={36}
            style={{ color: onBackgroundColor, opacity: 0.45 }}
          />
          <div
            className="text-base font-semibold"
            style={{ color: onBackgroundColor, opacity: 0.85 }}
          >
            No lyrics available
          </div>
          <p
            className="text-sm leading-relaxed"
            style={{ color: onBackgroundColor, opacity: 0.58 }}
          >
            No synchronized or static lyrics were found for this track. Add a Spicy Lyrics
            API key in Settings to unlock word-by-word synced lyrics.
          </p>
          <div className="flex items-center gap-2.5 mt-1">
            {currentTrackId && (
              <button
                type="button"
                onClick={() => void refreshLyrics(currentTrackId)}
                className="px-4 py-2 rounded-full text-xs font-semibold flex items-center gap-1.5 cursor-pointer transition-opacity hover:opacity-90"
                style={{
                  backgroundColor: `color-mix(in srgb, ${accentColor} 18%, transparent)`,
                  color: onBackgroundColor,
                }}
              >
                <Icon name="restore" size={16} />
                <span>Retry</span>
              </button>
            )}
            <button
              type="button"
              onClick={() => {
                setFullPlayerOpen(false);
                openDialog("settings");
              }}
              className="px-4 py-2 rounded-full text-xs font-semibold flex items-center gap-1.5 cursor-pointer transition-opacity hover:opacity-90"
              style={{
                backgroundColor: `color-mix(in srgb, ${accentColor} 18%, transparent)`,
                color: onBackgroundColor,
              }}
            >
              <Icon name="settings" size={16} />
              <span>Open Settings</span>
            </button>
          </div>
        </div>
      </div>
    );
  }

  const introProgress = firstLine
    ? clamp01(interpolatedMs / Math.max(1, firstLine.startMs))
    : 0;

  return (
    <div className="relative h-full w-full">
      <div
        ref={containerRef}
        onWheel={markUserScroll}
        onTouchMove={markUserScroll}
        onScroll={() => {
          if (!isProgrammaticScrollRef.current) {
            markUserScroll();
          }
        }}
        className="h-full w-full overflow-y-auto no-scrollbar pt-[120px] pb-[260px] flex flex-col gap-[22px]"
        style={{
          maskImage:
            "linear-gradient(to bottom, transparent 0%, black 10%, black 85%, transparent 100%)",
          WebkitMaskImage:
            "linear-gradient(to bottom, transparent 0%, black 10%, black 85%, transparent 100%)",
        }}
      >
        {/* Intro Dots */}
        {showIntroDots && (
          <div ref={introRef}>
            <InterludeDotsRow
              progress={introProgress}
              accentColor={accentColor}
              onBackgroundColor={onBackgroundColor}
            />
          </div>
        )}

        {lyrics.lines.map((line: LyricLine, idx: number) => {
          const isActive = !isStatic && idx === activeIndex;
          const isPast = !isStatic && activeIndex >= 0 && idx < activeIndex;
          const distance = activeIndex < 0 ? 0 : Math.abs(idx - activeIndex);
          const isHovered = hoveredIdx === idx;

          const distanceAlpha = isStatic
            ? 0.92
            : activeIndex < 0
              ? 0.55
              : distance === 0
                ? 1
                : distance === 1
                  ? 0.42
                  : distance === 2
                    ? 0.3
                    : 0.22;
          const targetAlpha = isStatic
            ? 0.92
            : isHovered
              ? 0.94
              : userScrolled
                ? Math.max(0.65, distanceAlpha)
                : distanceAlpha;
          const targetScale =
            isStatic || activeIndex < 0
              ? 1
              : isActive
                ? 1.04
                : isHovered
                  ? 1
                  : distance === 1
                    ? 0.97
                    : distance === 2
                      ? 0.94
                      : 0.92;
          const targetBlur =
            isStatic || isActive || isHovered || userScrolled || activeIndex < 0
              ? 0
              : distance === 1
                ? 1
                : distance === 2
                  ? 2
                  : 3;
          const offsetX = !isStatic && activeIndex >= 0 && isActive ? 4 : 0;
          const offsetY =
            isStatic || activeIndex < 0
              ? 0
              : isActive
                ? -2
                : idx < activeIndex
                  ? -3
                  : 3;

          const mainWords =
            line.words?.filter((w) => !w.isBackground) ?? [];
          const bgWords =
            line.words?.filter((w) => Boolean(w.isBackground)) ?? [];

          const nextLine = lyrics.lines[idx + 1];
          const hasLongGapAfter =
            !isStatic &&
            nextLine !== undefined &&
            nextLine.startMs - line.endMs > INTERLUDE_GAP_THRESHOLD_MS;
          const showGapDots =
            hasLongGapAfter &&
            interpolatedMs >= line.endMs &&
            interpolatedMs < nextLine.startMs;
          const gapProgress = hasLongGapAfter
            ? clamp01(
                (interpolatedMs - line.endMs) /
                  Math.max(1, nextLine.startMs - line.endMs)
              )
            : 0;

          return (
            <div key={`${line.startMs}-${idx}`} className="w-full">
              {line.isInstrumental ? (
                <div className="px-4 py-2">
                  <InterludeDotsRow
                    progress={
                      isActive
                        ? clamp01(
                            (interpolatedMs - line.startMs) /
                              Math.max(1, line.endMs - line.startMs)
                          )
                        : isPast
                          ? 1
                          : 0
                    }
                    accentColor={accentColor}
                    onBackgroundColor={onBackgroundColor}
                  />
                </div>
              ) : (
                <button
                  ref={(el) => {
                    lineRefs.current[idx] = el;
                  }}
                  type="button"
                  onMouseEnter={() => setHoveredIdx(idx)}
                  onMouseLeave={() => setHoveredIdx(null)}
                  onClick={() => {
                    if (!isStatic) {
                      setUserScrolled(false);
                      void seek(line.startMs);
                    }
                  }}
                  className="w-full text-left px-4 py-2 origin-left cursor-pointer"
                  style={{
                    opacity: targetAlpha,
                    transform: `translate3d(${offsetX}px, ${offsetY}px, 0) scale(${targetScale})`,
                    filter: targetBlur > 0 ? `blur(${targetBlur}px)` : "none",
                    transition:
                      "transform 340ms cubic-bezier(0.22, 1, 0.36, 1), opacity 300ms ease, filter 300ms ease",
                    willChange: "transform, opacity",
                  }}
                >
                  {lyrics.type === "syllable" && mainWords.length > 0 ? (
                    <SyllableSweepLine
                      words={mainWords}
                      isLineActive={isActive}
                      isLinePast={isPast}
                      nowMs={interpolatedMs}
                      litColor={onBackgroundColor}
                      unlitColor={
                        isActive
                          ? "rgba(245, 247, 250, 0.34)"
                          : "rgba(245, 247, 250, 0.72)"
                      }
                      accentColor={accentColor}
                    />
                  ) : (
                    <div
                      className="w-full text-[28px] leading-[36px] tracking-[-0.3px]"
                      style={{
                        fontWeight: isActive ? 800 : 700,
                        color: isStatic
                          ? onBackgroundColor
                          : isActive
                            ? `color-mix(in srgb, ${onBackgroundColor} 84%, ${accentColor})`
                            : onBackgroundColor,
                        textShadow: isActive
                          ? `0 0 16px color-mix(in srgb, ${accentColor} 42%, transparent)`
                          : "none",
                      }}
                    >
                      {line.text.trim() || "♪"}
                    </div>
                  )}

                  {showRomanized && line.romanized && (
                    <div
                      className="mt-[5px] text-[15px] leading-[21px] italic font-normal"
                      style={{
                        color: onBackgroundColor,
                        opacity: isActive ? 0.78 : 0.56,
                      }}
                    >
                      {line.romanized}
                    </div>
                  )}

                  {bgWords.length > 0 && (
                    <div className="mt-2">
                      <SyllableSweepLine
                        words={bgWords}
                        isLineActive={isActive}
                        isLinePast={isPast}
                        nowMs={interpolatedMs}
                        litColor="rgba(245, 247, 250, 0.72)"
                        unlitColor="rgba(245, 247, 250, 0.36)"
                        accentColor={accentColor}
                        isBackground
                      />
                    </div>
                  )}
                </button>
              )}

              {showGapDots && (
                <div className="pt-[18px] pb-[6px]">
                  <InterludeDotsRow
                    progress={gapProgress}
                    accentColor={accentColor}
                    onBackgroundColor={onBackgroundColor}
                  />
                </div>
              )}
            </div>
          );
        })}

        {/* Attribution footer matching SpicyLyricsView.kt */}
        <div className="px-3 py-5 flex items-center justify-between gap-2">
          <span
            className="text-xs"
            style={{ color: onBackgroundColor, opacity: 0.42 }}
          >
            Source: {lyrics.source || "SpicyLyrics"}
          </span>
          {currentTrackId && (
            <button
              type="button"
              onClick={() => void refreshLyrics(currentTrackId)}
              className="p-1.5 rounded-full hover:bg-white/10 cursor-pointer"
              style={{ color: onBackgroundColor, opacity: 0.55 }}
              title="Refresh lyrics"
            >
              <Icon name="restore" size={16} />
            </button>
          )}
        </div>
      </div>

      {/* "Resume Sync" pill when user manually scrolled */}
      {userScrolled && !isStatic && activeIndex >= 0 && (
        <div className="absolute bottom-[18px] left-1/2 -translate-x-1/2 z-20">
          <button
            type="button"
            onClick={() => setUserScrolled(false)}
            className="px-4 py-2 rounded-full text-xs font-semibold shadow-lg cursor-pointer transition-transform hover:scale-105"
            style={{
              backgroundColor: "var(--fp-accent, var(--accent))",
              color: "var(--fp-on-accent, var(--on-accent))",
            }}
          >
            Resume Sync
          </button>
        </div>
      )}
    </div>
  );
}
