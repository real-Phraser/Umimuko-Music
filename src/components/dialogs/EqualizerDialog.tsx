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
  const [saveOpen, setSaveOpen] = useState(false);

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
    setSaveOpen(false);
  };

  return (
    <DialogShell
      title="Audio DSP & Equalizer"
      subtitle="10-band hardware EQ, preamp gain, and preset curves"
      icon="levels"
      maxWidth="max-w-[780px]"
      onClose={() => openDialog(null)}
      headerRight={
        <label className="flex items-center gap-2 cursor-pointer mr-1">
          <span
            className="text-xs font-semibold"
            style={{
              color: equalizer.enabled ? "var(--accent)" : "var(--text-secondary)",
            }}
          >
            {equalizer.enabled ? "EQ On" : "EQ Off"}
          </span>
          <input
            type="checkbox"
            checked={equalizer.enabled}
            onChange={(e) =>
              void updateEqualizer({ ...equalizer, enabled: e.target.checked })
            }
            className="w-4 h-4 accent-[var(--accent)] cursor-pointer"
          />
        </label>
      }
    >
      <div className="space-y-5">
        {/* Preset Bar */}
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex flex-wrap items-center gap-2.5">
            <span
              className="text-xs font-semibold"
              style={{ color: "var(--text-secondary)" }}
            >
              Preset:
            </span>
            <select
              value={equalizer.presetName ?? ""}
              disabled={!equalizer.enabled}
              onChange={(e) => {
                const preset = eqPresets.find((p) => p.name === e.target.value);
                if (preset) void applyEqPreset(preset);
              }}
              className="px-3.5 py-1.5 rounded-full text-xs font-medium outline-none cursor-pointer disabled:opacity-50"
              style={{
                backgroundColor: "var(--surface-1)",
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
              disabled={!equalizer.enabled}
              onClick={() => setSaveOpen((v) => !v)}
              className="px-3.5 py-1.5 rounded-full text-xs font-medium cursor-pointer disabled:opacity-50"
              style={{
                backgroundColor: "var(--accent)",
                color: "var(--on-accent)",
              }}
            >
              Save Preset
            </button>
          </div>

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

        {saveOpen && (
          <form onSubmit={handleSavePreset} className="flex items-center gap-2">
            <input
              type="text"
              value={presetInput}
              onChange={(e) => setPresetInput(e.target.value)}
              placeholder="Preset name…"
              className="flex-1 px-4 py-2 rounded-full text-sm outline-none"
              style={{
                backgroundColor: "var(--surface-1)",
                border: "1px solid var(--outline)",
              }}
            />
            <button
              type="submit"
              className="px-4 py-2 rounded-full text-xs font-medium cursor-pointer"
              style={{
                backgroundColor: "var(--accent)",
                color: "var(--on-accent)",
              }}
            >
              Save
            </button>
          </form>
        )}

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
            disabled={!equalizer.enabled}
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

        {/* 10-Band Vertical Sliders */}
        <div
          className="p-4 rounded-2xl grid grid-cols-5 sm:grid-cols-10 gap-3"
          style={{
            backgroundColor: "var(--surface-1)",
            border: "1px solid var(--outline)",
            opacity: equalizer.enabled ? 1 : 0.55,
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
                    disabled={!equalizer.enabled}
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
      </div>
    </DialogShell>
  );
}
