import { useState } from "react";
import { useLibraryStore } from "../../store/useLibraryStore";
import { usePlayerStore } from "../../store/usePlayerStore";
import { formatDuration } from "../../utils/format";
import { DialogShell } from "./DialogShell";

const PRESET_MINUTES = [15, 30, 45, 60, 90];

export function SleepTimerDialog() {
  const activeDialog = useLibraryStore((s) => s.activeDialog);
  const openDialog = useLibraryStore((s) => s.openDialog);
  const remainingMs = usePlayerStore((s) => s.playback.sleepTimerRemainingMs);
  const setSleepTimer = usePlayerStore((s) => s.setSleepTimer);

  const [selectedMinutes, setSelectedMinutes] = useState<number>(30);

  if (activeDialog !== "sleepTimer") return null;

  const handleSet = async (minutes: number | null) => {
    await setSleepTimer(minutes);
    openDialog(null);
  };

  return (
    <DialogShell
      title="Sleep Timer"
      icon="timer"
      onClose={() => openDialog(null)}
      maxWidth="max-w-[480px]"
    >
      {remainingMs !== null ? (
        <div className="py-2 flex flex-col items-center gap-5">
          <div
            className="w-40 h-40 rounded-full flex flex-col items-center justify-center border-2"
            style={{
              backgroundColor: "var(--accent-container)",
              borderColor: "var(--accent)",
              color: "var(--on-accent-container)",
            }}
          >
            <span className="text-[11px] uppercase tracking-wider font-semibold opacity-75">
              Remaining
            </span>
            <span className="text-3xl font-bold tabular-nums mt-1">
              {formatDuration(remainingMs)}
            </span>
          </div>

          <div className="flex items-center gap-2.5">
            <button
              type="button"
              onClick={() => void setSleepTimer(Math.ceil(remainingMs / 60000) + 5)}
              className="px-4 py-2 rounded-full text-xs font-medium cursor-pointer"
              style={{
                backgroundColor: "var(--surface-3)",
                color: "var(--text-primary)",
              }}
            >
              +5 min
            </button>
            <button
              type="button"
              onClick={() => void setSleepTimer(Math.ceil(remainingMs / 60000) + 10)}
              className="px-4 py-2 rounded-full text-xs font-medium cursor-pointer"
              style={{
                backgroundColor: "var(--surface-3)",
                color: "var(--text-primary)",
              }}
            >
              +10 min
            </button>
            <button
              type="button"
              onClick={() => void handleSet(null)}
              className="px-4 py-2 rounded-full text-xs font-medium cursor-pointer"
              style={{
                backgroundColor: "var(--danger-container)",
                color: "var(--danger)",
              }}
            >
              Cancel Timer
            </button>
          </div>
        </div>
      ) : (
        <div className="space-y-5">
          <div>
            <div
              className="text-xs font-semibold mb-2.5"
              style={{ color: "var(--text-secondary)" }}
            >
              Quick Presets
            </div>
            <div className="grid grid-cols-3 gap-2">
              {PRESET_MINUTES.map((mins) => {
                const active = selectedMinutes === mins;
                return (
                  <button
                    key={mins}
                    type="button"
                    onClick={() => setSelectedMinutes(mins)}
                    className="py-2.5 rounded-2xl text-sm font-medium transition-colors cursor-pointer"
                    style={{
                      backgroundColor: active
                        ? "var(--accent-container)"
                        : "var(--surface-1)",
                      color: active
                        ? "var(--on-accent-container)"
                        : "var(--text-primary)",
                      border: `1px solid ${active ? "var(--accent)" : "var(--outline)"}`,
                    }}
                  >
                    {mins} min
                  </button>
                );
              })}
            </div>
          </div>

          <div
            className="p-4 rounded-2xl space-y-2.5"
            style={{
              backgroundColor: "var(--surface-1)",
              border: "1px solid var(--outline)",
            }}
          >
            <div className="flex items-center justify-between text-xs">
              <span style={{ color: "var(--text-secondary)" }}>Custom Duration</span>
              <span className="font-bold tabular-nums">{selectedMinutes} min</span>
            </div>
            <input
              type="range"
              min={5}
              max={180}
              step={5}
              value={selectedMinutes}
              onChange={(e) => setSelectedMinutes(Number(e.target.value))}
              className="umimuko-slider w-full"
            />
          </div>

          <div className="flex justify-end gap-2">
            <button
              type="button"
              onClick={() => openDialog(null)}
              className="px-4 py-2 rounded-full text-sm cursor-pointer"
              style={{ backgroundColor: "var(--surface-3)" }}
            >
              Cancel
            </button>
            <button
              type="button"
              onClick={() => void handleSet(selectedMinutes)}
              className="px-5 py-2 rounded-full text-sm font-medium cursor-pointer"
              style={{
                backgroundColor: "var(--accent)",
                color: "var(--on-accent)",
              }}
            >
              Start Timer
            </button>
          </div>
        </div>
      )}
    </DialogShell>
  );
}
