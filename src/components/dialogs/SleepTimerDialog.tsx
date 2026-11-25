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

  const [customMinutes, setCustomMinutes] = useState("20");

  if (activeDialog !== "sleepTimer") return null;

  const handleSet = async (minutes: number | null) => {
    await setSleepTimer(minutes);
    openDialog(null);
  };

  return (
    <DialogShell
      title="Sleep Timer"
      subtitle="Automatically pause playback after a duration"
      onClose={() => openDialog(null)}
      maxWidth="max-w-md"
    >
      <div className="space-y-5">
        {remainingMs !== null && (
          <div
            className="p-4 rounded-2xl flex items-center justify-between"
            style={{
              backgroundColor: "var(--accent-container)",
              color: "var(--on-accent-container)",
            }}
          >
            <div>
              <div className="text-xs font-medium">Timer Active</div>
              <div className="text-lg font-bold tabular-nums">
                {formatDuration(remainingMs)} remaining
              </div>
            </div>
            <button
              type="button"
              onClick={() => void handleSet(null)}
              className="px-3.5 py-1.5 rounded-full text-xs font-medium cursor-pointer"
              style={{
                backgroundColor: "var(--danger-container)",
                color: "var(--danger)",
              }}
            >
              Cancel
            </button>
          </div>
        )}

        <div className="grid grid-cols-3 gap-2.5">
          {PRESET_MINUTES.map((mins) => (
            <button
              key={mins}
              type="button"
              onClick={() => void handleSet(mins)}
              className="py-3 rounded-2xl text-sm font-medium transition-colors hover:opacity-90 cursor-pointer"
              style={{
                backgroundColor: "var(--surface-1)",
                border: "1px solid var(--outline)",
              }}
            >
              {mins} min
            </button>
          ))}
        </div>

        <form
          onSubmit={(e) => {
            e.preventDefault();
            const mins = parseInt(customMinutes, 10);
            if (Number.isFinite(mins) && mins > 0) {
              void handleSet(mins);
            }
          }}
          className="flex items-center gap-2"
        >
          <input
            type="number"
            min={1}
            max={480}
            value={customMinutes}
            onChange={(e) => setCustomMinutes(e.target.value)}
            className="flex-1 px-4 py-2 rounded-full text-sm outline-none"
            style={{
              backgroundColor: "var(--surface-1)",
              border: "1px solid var(--outline)",
            }}
          />
          <button
            type="submit"
            className="px-4 py-2 rounded-full text-sm font-medium cursor-pointer"
            style={{
              backgroundColor: "var(--accent)",
              color: "var(--on-accent)",
            }}
          >
            Start Custom
          </button>
        </form>
      </div>
    </DialogShell>
  );
}
