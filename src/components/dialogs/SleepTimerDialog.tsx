import { useState } from "react";
import { useLibraryStore } from "../../store/useLibraryStore";
import { usePlayerStore } from "../../store/usePlayerStore";
import { formatDuration } from "../../utils/format";
import { DialogShell } from "./DialogShell";

const PRESET_MINUTES = [15, 30, 45, 60, 90] as const;

export function SleepTimerDialog() {
  const activeDialog = useLibraryStore((s) => s.activeDialog);
  const openDialog = useLibraryStore((s) => s.openDialog);
  const remainingMs = usePlayerStore((s) => s.playback.sleepTimerRemainingMs);
  const setSleepTimer = usePlayerStore((s) => s.setSleepTimer);

  const [selectedPreset, setSelectedPreset] = useState<number | null>(30);
  const [customMinutes, setCustomMinutes] = useState<number>(30);

  if (activeDialog !== "sleepTimer") return null;

  const isActive = remainingMs !== null && remainingMs > 0;
  const activeMinutes = selectedPreset ?? customMinutes;

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
      maxWidth="max-w-[520px]"
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

          <p className="text-xs text-center" style={{ color: "var(--text-secondary)" }}>
            Playback pauses automatically when the countdown finishes
          </p>

          <div className="w-full flex items-center gap-2">
            <button
              type="button"
              onClick={() => void setSleepTimer(Math.ceil(remainingMs / 60_000) + 5)}
              className="flex-1 py-2.5 rounded-[12px] text-xs font-medium cursor-pointer"
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
              className="flex-1 py-2.5 rounded-[12px] text-xs font-medium cursor-pointer"
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
            className="w-full py-2.5 rounded-[12px] text-sm font-semibold cursor-pointer"
            style={{
              backgroundColor: "var(--danger-container)",
              color: "var(--danger)",
            }}
          >
            Cancel Timer
          </button>
        </div>
      ) : (
        <div className="space-y-4">
          <div
            className="text-[11px] font-bold uppercase tracking-wider"
            style={{ color: "var(--accent)" }}
          >
            PRESETS
          </div>

          <div className="flex flex-wrap items-center gap-1.5">
            {PRESET_MINUTES.map((mins) => {
              const selected = selectedPreset === mins;
              return (
                <button
                  key={mins}
                  type="button"
                  onClick={() => {
                    setSelectedPreset(mins);
                    setCustomMinutes(mins);
                  }}
                  className="h-8 px-3 rounded-[12px] text-sm font-medium cursor-pointer transition-colors"
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
          </div>

          <div className="space-y-1.5">
            <div className="flex items-center justify-between">
              <span className="text-sm font-medium">Duration</span>
              <span className="text-sm font-bold tabular-nums" style={{ color: "var(--accent)" }}>
                {customMinutes} minutes
              </span>
            </div>
            <input
              type="range"
              min={5}
              max={120}
              step={5}
              value={customMinutes}
              onChange={(e) => {
                setCustomMinutes(Number(e.target.value));
                setSelectedPreset(null);
              }}
              className="umimuko-slider w-full"
              style={{
                background: `linear-gradient(90deg, var(--accent) ${((customMinutes - 5) / 115) * 100}%, var(--surface-3) ${((customMinutes - 5) / 115) * 100}%)`,
                height: 4,
                borderRadius: 999,
              }}
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
