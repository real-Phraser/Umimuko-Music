import { useState } from "react";
import { useLibraryStore } from "../../store/useLibraryStore";
import { useSettingsStore } from "../../store/useSettingsStore";
import { EQ_FREQUENCIES } from "../../types/ipc";
import { DialogShell } from "./DialogShell";

function formatFreq(hz: number): string {
  return hz >= 1000 ? `${hz / 1000}k` : `${hz}`;
}

export function EqualizerDialog() {
  const activeDialog = useLibraryStore((s) => s.activeDialog);
  const openDialog = useLibraryStore((s) => s.openDialog);
  const equalizer = useSettingsStore((s) => s.settings.equalizer);
  const eqPresets = useSettingsStore((s) => s.eqPresets);
  const updateEqualizer = useSettingsStore((s) => s.updateEqualizer);
  const applyEqPreset = useSettingsStore((s) => s.applyEqPreset);
  const saveEqPreset = useSettingsStore((s) => s.saveEqPreset);

  const [presetInput, setPresetInput] = useState("");

  if (activeDialog !== "equalizer") return null;

  const handleBandChange = (idx: number, gainDb: number) => {
    const nextBands = [...equalizer.bands];
    nextBands[idx] = gainDb;
    void updateEqualizer({
      ...equalizer,
      bands: nextBands,
      presetName: null,
    });
  };

  const handleSavePreset = async (e: React.FormEvent) => {
    e.preventDefault();
    const name = presetInput.trim();
    if (!name) return;
    await saveEqPreset(name);
    setPresetInput("");
  };

  return (
    <DialogShell
      title="10-Band Equalizer"
      subtitle="Biquad peaking EQ with preamp gain"
      onClose={() => openDialog(null)}
    >
      <div className="space-y-6">
        {/* Top Controls */}
        <div className="flex flex-wrap items-center justify-between gap-3">
          <label className="flex items-center gap-2.5 cursor-pointer">
            <input
              type="checkbox"
              checked={equalizer.enabled}
              onChange={(e) =>
                void updateEqualizer({ ...equalizer, enabled: e.target.checked })
              }
              className="w-4 h-4 accent-[var(--accent)] cursor-pointer"
            />
            <span className="text-sm font-medium">Enable Equalizer</span>
          </label>

          <div className="flex flex-wrap items-center gap-2">
            <select
              value={equalizer.presetName ?? ""}
              onChange={(e) => {
                const preset = eqPresets.find((p) => p.name === e.target.value);
                if (preset) void applyEqPreset(preset);
              }}
              className="px-3.5 py-1.5 rounded-full text-xs font-medium outline-none cursor-pointer"
              style={{
                backgroundColor: "var(--surface-3)",
                border: "1px solid var(--outline)",
              }}
            >
              <option value="" disabled>
                Custom
              </option>
              {eqPresets.map((p) => (
                <option key={p.id} value={p.name}>
                  {p.name}
                </option>
              ))}
            </select>

            <button
              type="button"
              onClick={() =>
                void updateEqualizer({
                  ...equalizer,
                  preampDb: 0,
                  bands: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
                  presetName: "Flat",
                })
              }
              className="px-3.5 py-1.5 rounded-full text-xs font-medium cursor-pointer"
              style={{
                backgroundColor: "var(--surface-3)",
                color: "var(--text-secondary)",
              }}
            >
              Reset
            </button>
          </div>
        </div>

        {/* Preamp */}
        <div
          className="p-4 rounded-2xl flex items-center gap-4"
          style={{
            backgroundColor: "var(--surface-1)",
            border: "1px solid var(--outline)",
          }}
        >
          <span className="text-xs font-semibold w-20 shrink-0">Preamp</span>
          <input
            type="range"
            min={-12}
            max={12}
            step={0.5}
            value={equalizer.preampDb}
            onChange={(e) =>
              void updateEqualizer({
                ...equalizer,
                preampDb: Number(e.target.value),
                presetName: null,
              })
            }
            className="umimuko-slider flex-1"
          />
          <span className="text-xs tabular-nums w-14 text-right">
            {equalizer.preampDb > 0 ? `+${equalizer.preampDb}` : equalizer.preampDb} dB
          </span>
        </div>

        {/* 10 Bands */}
        <div
          className="p-4 rounded-2xl grid grid-cols-5 sm:grid-cols-10 gap-3"
          style={{
            backgroundColor: "var(--surface-1)",
            border: "1px solid var(--outline)",
          }}
        >
          {EQ_FREQUENCIES.map((freq, idx) => {
            const gain = equalizer.bands[idx] ?? 0;
            return (
              <div key={freq} className="flex flex-col items-center gap-2">
                <span className="text-[11px] tabular-nums font-medium">
                  {gain > 0 ? `+${gain}` : gain}
                </span>
                <div className="h-32 flex items-center justify-center">
                  <input
                    type="range"
                    min={-12}
                    max={12}
                    step={0.5}
                    value={gain}
                    onChange={(e) => handleBandChange(idx, Number(e.target.value))}
                    className="umimuko-slider w-28 -rotate-90"
                    aria-label={`${freq} Hz`}
                  />
                </div>
                <span
                  className="text-[11px] font-medium"
                  style={{ color: "var(--text-secondary)" }}
                >
                  {formatFreq(freq)}
                </span>
              </div>
            );
          })}
        </div>

        {/* Save Custom Preset */}
        <form onSubmit={handleSavePreset} className="flex items-center gap-2">
          <input
            type="text"
            value={presetInput}
            onChange={(e) => setPresetInput(e.target.value)}
            placeholder="Save current curve as preset…"
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
            Save Preset
          </button>
        </form>
      </div>
    </DialogShell>
  );
}
