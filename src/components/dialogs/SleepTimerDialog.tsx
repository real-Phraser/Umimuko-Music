import { useRef, useState } from "react";
import { useLibraryStore } from "../../store/useLibraryStore";
import { usePlayerStore } from "../../store/usePlayerStore";
import { formatDuration } from "../../utils/format";
import { DialogShell } from "./DialogShell";

const PRESET_MINUTES = [15, 30, 45, 60] as const;

/** Compose Material 3 Switch (52x32dp pill, 2dp outline unchecked, 16dp->24dp thumb). */
function M3Switch({
  checked,
  onChange,
  label,
}: {
  checked: boolean;
  onChange: (next: boolean) => void;
  label: string;
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      onClick={() => onChange(!checked)}
      className="relative w-[52px] h-8 rounded-full transition-colors duration-150 cursor-pointer shrink-0"
      style={{
        backgroundColor: checked ? "var(--accent)" : "var(--surface-2)",
        border: checked ? "2px solid transparent" : "2px solid var(--outline-strong)",
      }}
    >
      <span
        className="absolute top-1/2 -translate-y-1/2 rounded-full transition-all duration-150"
        style={{
          width: checked ? 24 : 16,
          height: checked ? 24 : 16,
          left: checked ? 24 : 6,
          backgroundColor: checked ? "var(--on-accent)" : "var(--outline-strong)",
        }}
      />
    </button>
  );
}

/** Compose Material 3 Horizontal Slider with stepped tick dots and 20dp thumb. */
function M3Slider({
  value,
  min,
  max,
  step,
  steps = 0,
  label,
  onChange,
}: {
  value: number;
  min: number;
  max: number;
  step: number;
  steps?: number;
  label: string;
  onChange: (val: number) => void;
}) {
  const trackRef = useRef<HTMLDivElement | null>(null);
  const [dragging, setDragging] = useState(false);

  const clamped = Math.min(max, Math.max(min, value));
  const pct = ((clamped - min) / Math.max(0.0001, max - min)) * 100;

  const updateFromClientX = (clientX: number) => {
    const el = trackRef.current;
    if (!el) return;
    const rect = el.getBoundingClientRect();
    const frac = Math.min(1, Math.max(0, (clientX - rect.left) / Math.max(1, rect.width)));
    const raw = min + frac * (max - min);
    const snapped = Math.round(raw / step) * step;
    onChange(Math.min(max, Math.max(min, snapped)));
  };

  const tickCount = steps > 0 ? steps + 2 : 0;

  return (
    <div
      ref={trackRef}
      role="slider"
      tabIndex={0}
      aria-label={label}
      aria-valuemin={min}
      aria-valuemax={max}
      aria-valuenow={clamped}
      onPointerDown={(e) => {
        e.currentTarget.setPointerCapture(e.pointerId);
        setDragging(true);
        updateFromClientX(e.clientX);
      }}
      onPointerMove={(e) => {
        if (!dragging) return;
        updateFromClientX(e.clientX);
      }}
      onPointerUp={(e) => {
        if (!dragging) return;
        setDragging(false);
        try {
          e.currentTarget.releasePointerCapture(e.pointerId);
        } catch {
          // ignore
        }
      }}
      onPointerCancel={() => setDragging(false)}
      onKeyDown={(e) => {
        if (e.key === "ArrowRight" || e.key === "ArrowUp") {
          e.preventDefault();
          onChange(Math.min(max, clamped + step));
        } else if (e.key === "ArrowLeft" || e.key === "ArrowDown") {
          e.preventDefault();
          onChange(Math.max(min, clamped - step));
        }
      }}
      className="group relative h-8 w-full flex items-center select-none cursor-pointer outline-none"
    >
      <div
        className="relative w-full h-1 rounded-full overflow-hidden"
        style={{ backgroundColor: "var(--surface-3)" }}
      >
        <div
          className="absolute inset-y-0 left-0 rounded-full"
          style={{
            width: `${pct}%`,
            backgroundColor: "var(--accent)",
          }}
        />
        {tickCount > 0 &&
          Array.from({ length: tickCount }, (_, i) => {
            const tickPct = (i / (tickCount - 1)) * 100;
            const inActive = tickPct <= pct;
            return (
              <span
                key={i}
                className="absolute top-1/2 -translate-y-1/2 -translate-x-1/2 w-0.5 h-0.5 rounded-full pointer-events-none"
                style={{
                  left: `${tickPct}%`,
                  backgroundColor: inActive ? "var(--on-accent)" : "var(--text-secondary)",
                  opacity: 0.45,
                }}
              />
            );
          })}
      </div>

      <div
        className="absolute top-1/2 -translate-x-1/2 -translate-y-1/2 w-5 h-5 rounded-full shadow-md pointer-events-none transition-transform duration-100 group-hover:scale-110"
        style={{
          left: `${pct}%`,
          backgroundColor: "var(--accent)",
          transform: `translate(-50%, -50%) scale(${dragging ? 1.15 : 1})`,
          boxShadow: dragging
            ? "0 0 0 8px rgba(var(--accent-rgb), 0.18), 0 2px 6px rgba(0,0,0,0.35)"
            : "0 1px 4px rgba(0,0,0,0.35)",
        }}
      />
    </div>
  );
}

export function SleepTimerDialog() {
  const activeDialog = useLibraryStore((s) => s.activeDialog);
  const openDialog = useLibraryStore((s) => s.openDialog);
  const remainingMs = usePlayerStore((s) => s.playback.sleepTimerRemainingMs);
  const currentTrack = usePlayerStore((s) => s.playback.currentTrack);
  const positionMs = usePlayerStore((s) => s.playback.positionMs);
  const setSleepTimer = usePlayerStore((s) => s.setSleepTimer);

  const [selectedPreset, setSelectedPreset] = useState<number | null>(30);
  const [isEndOfTrack, setIsEndOfTrack] = useState(false);
  const [customMinutes, setCustomMinutes] = useState<number>(30);
  const [fadeoutEnabled, setFadeoutEnabled] = useState<boolean>(() => {
    try {
      return localStorage.getItem("umimuko_sleep_fadeout") !== "false";
    } catch {
      return true;
    }
  });

  if (activeDialog !== "sleepTimer") return null;

  const isActive = remainingMs !== null && remainingMs > 0;
  const trackRemainingMins =
    currentTrack && currentTrack.durationMs > positionMs
      ? Math.max(1, Math.ceil((currentTrack.durationMs - positionMs) / 60_000))
      : 5;
  const activeMinutes = isEndOfTrack
    ? trackRemainingMins
    : (selectedPreset ?? customMinutes);

  const handleStart = async () => {
    await setSleepTimer(activeMinutes);
    openDialog(null);
  };

  const handleCancel = async () => {
    await setSleepTimer(null);
  };

  return (
    <DialogShell
      title="Sleep Timer"
      icon="timer"
      onClose={() => openDialog(null)}
      maxWidth="max-w-[540px]"
    >
      {isActive ? (
        <div className="py-2 flex flex-col items-center gap-4">
          {/* 130dp circular countdown display matching SleepTimerDialog.kt */}
          <div
            className="w-[130px] h-[130px] rounded-full flex flex-col items-center justify-center border-2"
            style={{
              backgroundColor: "color-mix(in srgb, var(--accent-container) 50%, transparent)",
              borderColor: "var(--accent)",
            }}
          >
            <span
              className="text-[30px] leading-9 font-bold tabular-nums"
              style={{ color: "var(--accent)" }}
            >
              {formatDuration(remainingMs)}
            </span>
            <span
              className="text-xs font-medium mt-0.5"
              style={{ color: "var(--text-secondary)" }}
            >
              Remaining
            </span>
          </div>

          {fadeoutEnabled && (
            <p className="text-xs text-center" style={{ color: "var(--text-secondary)" }}>
              Volume smoothly fades out during final 30s
            </p>
          )}

          <div className="w-full flex items-center gap-2">
            <button
              type="button"
              onClick={() => void setSleepTimer(Math.ceil(remainingMs / 60_000) + 5)}
              className="flex-1 h-10 rounded-[12px] text-xs font-medium cursor-pointer transition-opacity hover:opacity-90"
              style={{
                backgroundColor: "var(--accent-container)",
                color: "var(--on-accent-container)",
              }}
            >
              +5 min
            </button>
            <button
              type="button"
              onClick={() => void setSleepTimer(Math.ceil(remainingMs / 60_000) + 15)}
              className="flex-1 h-10 rounded-[12px] text-xs font-medium cursor-pointer transition-opacity hover:opacity-90"
              style={{
                backgroundColor: "var(--accent-container)",
                color: "var(--on-accent-container)",
              }}
            >
              +15 min
            </button>
          </div>

          <button
            type="button"
            onClick={() => void handleCancel()}
            className="w-full h-10 rounded-[12px] text-sm font-semibold cursor-pointer transition-opacity hover:opacity-90"
            style={{
              backgroundColor: "var(--danger-container)",
              color: "var(--danger)",
            }}
          >
            Cancel Timer
          </button>
        </div>
      ) : (
        <div className="space-y-3.5">
          <div
            className="text-[11px] font-bold uppercase tracking-wider"
            style={{ color: "var(--accent)" }}
          >
            PRESETS
          </div>

          <div className="flex flex-wrap items-center gap-1.5">
            {PRESET_MINUTES.map((mins) => {
              const selected = selectedPreset === mins && !isEndOfTrack;
              return (
                <button
                  key={mins}
                  type="button"
                  onClick={() => {
                    setSelectedPreset(mins);
                    setIsEndOfTrack(false);
                    setCustomMinutes(mins);
                  }}
                  className="h-8 px-3 rounded-[12px] text-[13px] font-medium cursor-pointer transition-colors"
                  style={{
                    backgroundColor: selected ? "var(--accent-container)" : "transparent",
                    color: selected
                      ? "var(--on-accent-container)"
                      : "var(--text-secondary)",
                    border: selected ? "none" : "1px solid var(--outline-strong)",
                  }}
                >
                  {mins}m
                </button>
              );
            })}
            <button
              type="button"
              onClick={() => {
                setIsEndOfTrack(true);
                setSelectedPreset(null);
              }}
              className="h-8 px-3 rounded-[12px] text-[13px] font-medium cursor-pointer transition-colors"
              style={{
                backgroundColor: isEndOfTrack ? "var(--accent-container)" : "transparent",
                color: isEndOfTrack
                  ? "var(--on-accent-container)"
                  : "var(--text-secondary)",
                border: isEndOfTrack ? "none" : "1px solid var(--outline-strong)",
              }}
            >
              End of Track
            </button>
          </div>

          {!isEndOfTrack && (
            <div className="space-y-1">
              <div className="flex items-center justify-between">
                <span className="text-sm font-medium">Duration</span>
                <span
                  className="text-sm font-bold tabular-nums"
                  style={{ color: "var(--accent)" }}
                >
                  {customMinutes} minutes
                </span>
              </div>
              <M3Slider
                label="Sleep Timer Duration"
                value={customMinutes}
                min={5}
                max={120}
                step={5}
                steps={22}
                onChange={(val) => {
                  setCustomMinutes(val);
                  setSelectedPreset(null);
                  setIsEndOfTrack(false);
                }}
              />
            </div>
          )}

          <div className="h-px" style={{ backgroundColor: "var(--outline)" }} />

          <div className="flex items-center justify-between gap-3">
            <div className="min-w-0 flex-1">
              <div className="text-sm font-medium">Smooth 30s Volume Fade-Out</div>
              <div className="text-xs" style={{ color: "var(--text-secondary)" }}>
                Gently dissolves audio before stopping
              </div>
            </div>
            <M3Switch
              checked={fadeoutEnabled}
              onChange={(next) => {
                setFadeoutEnabled(next);
                try {
                  localStorage.setItem("umimuko_sleep_fadeout", String(next));
                } catch {
                  // ignore
                }
              }}
              label="Smooth 30s Volume Fade-Out"
            />
          </div>

          <div className="pt-1">
            <button
              type="button"
              onClick={() => void handleStart()}
              className="w-full h-12 rounded-[14px] text-sm font-bold cursor-pointer transition-opacity hover:opacity-95"
              style={{
                backgroundColor: "var(--accent)",
                color: "var(--on-accent)",
              }}
            >
              Start Sleep Timer
            </button>
          </div>
        </div>
      )}
    </DialogShell>
  );
}
