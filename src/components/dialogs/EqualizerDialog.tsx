import { useEffect, useRef, useState } from "react";
import { useLibraryStore } from "../../store/useLibraryStore";
import { useSettingsStore } from "../../store/useSettingsStore";
import { EQ_FREQUENCIES } from "../../types/ipc";
import { DialogShell } from "./DialogShell";

const BAND_LABELS = [
  "60Hz",
  "170Hz",
  "310Hz",
  "600Hz",
  "1kHz",
  "3kHz",
  "6kHz",
  "12kHz",
  "14kHz",
  "16kHz",
] as const;

const MIN_GAIN_DB = -12;
const MAX_GAIN_DB = 12;
const MIN_PREAMP_DB = -15;
const MAX_PREAMP_DB = 15;
const MAX_CROSSFADE_S = 12;

function VerticalEqSlider({
  label,
  value,
  min,
  max,
  enabled,
  onChange,
}: {
  label: string;
  value: number;
  min: number;
  max: number;
  enabled: boolean;
  onChange: (val: number) => void;
}) {
  const trackRef = useRef<HTMLDivElement | null>(null);
  const [dragging, setDragging] = useState(false);

  const ratio = Math.min(1, Math.max(0, (value - min) / (max - min)));
  const zeroRatio = Math.min(1, Math.max(0, (0 - min) / (max - min)));

  const updateFromClientY = (clientY: number) => {
    const el = trackRef.current;
    if (!el) return;
    const rect = el.getBoundingClientRect();
    const frac = Math.min(1, Math.max(0, 1 - (clientY - rect.top) / Math.max(1, rect.height)));
    const raw = min + frac * (max - min);
    const snapped = Math.round(raw * 2) / 2;
    onChange(Math.min(max, Math.max(min, snapped)));
  };

  const fillBottomPct = Math.min(ratio, zeroRatio) * 100;
  const fillHeightPct = Math.abs(ratio - zeroRatio) * 100;

  return (
    <div className="w-[52px] flex flex-col items-center select-none">
      <span
        className="text-[11px] tabular-nums font-medium"
        style={{
          color: enabled ? "var(--text-primary)" : "var(--text-muted)",
        }}
      >
        {value >= 0 ? `+${value.toFixed(1)}` : value.toFixed(1)}
      </span>

      <div
        ref={trackRef}
        role="slider"
        tabIndex={enabled ? 0 : -1}
        aria-label={`${label} gain`}
        aria-valuemin={min}
        aria-valuemax={max}
        aria-valuenow={value}
        aria-disabled={!enabled}
        onPointerDown={(e) => {
          if (!enabled) return;
          e.currentTarget.setPointerCapture(e.pointerId);
          setDragging(true);
          updateFromClientY(e.clientY);
        }}
        onPointerMove={(e) => {
          if (!dragging || !enabled) return;
          updateFromClientY(e.clientY);
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
        onDoubleClick={() => enabled && onChange(0)}
        onKeyDown={(e) => {
          if (!enabled) return;
          if (e.key === "ArrowUp") {
            e.preventDefault();
            onChange(Math.min(max, value + 0.5));
          } else if (e.key === "ArrowDown") {
            e.preventDefault();
            onChange(Math.max(min, value - 0.5));
          }
        }}
        className={`relative h-[130px] w-9 my-1.5 flex items-center justify-center outline-none ${
          enabled ? "cursor-pointer" : "cursor-default opacity-45"
        }`}
      >
        {/* Rail */}
        <div
          className="relative w-1.5 h-full rounded-full"
          style={{ backgroundColor: "var(--surface-3)" }}
        >
          {/* Active range from 0dB center */}
          <div
            className="absolute inset-x-0 rounded-full"
            style={{
              bottom: `${fillBottomPct}%`,
              height: `${fillHeightPct}%`,
              backgroundColor: "var(--accent)",
            }}
          />
        </div>

        {/* Thumb */}
        <div
          className="absolute left-1/2 -translate-x-1/2 translate-y-1/2 w-4 h-4 rounded-full shadow-md pointer-events-none transition-transform"
          style={{
            bottom: `${ratio * 100}%`,
            backgroundColor: "var(--accent)",
            transform: `translate(-50%, 50%) scale(${dragging ? 1.18 : 1})`,
          }}
        />
      </div>

      <span
        className="text-[11px] font-medium"
        style={{
          color: enabled ? "var(--text-secondary)" : "var(--text-muted)",
        }}
      >
        {label}
      </span>
    </div>
  );
}

export function EqualizerDialog() {
  const activeDialog = useLibraryStore((s) => s.activeDialog);
  const openDialog = useLibraryStore((s) => s.openDialog);
  const equalizer = useSettingsStore((s) => s.settings.equalizer);
  const crossfade = useSettingsStore((s) => s.settings.crossfade);
  const eqPresets = useSettingsStore((s) => s.eqPresets);
  const updateEqualizer = useSettingsStore((s) => s.updateEqualizer);
  const applyEqPreset = useSettingsStore((s) => s.applyEqPreset);
  const saveEqPreset = useSettingsStore((s) => s.saveEqPreset);
  const updateCrossfade = useSettingsStore((s) => s.updateCrossfade);

  const [presetMenuOpen, setPresetMenuOpen] = useState(false);
  const [saveDialogOpen, setSaveDialogOpen] = useState(false);
  const [newPresetName, setNewPresetName] = useState("");
  const [curvePref, setCurvePref] = useState<"equal_power" | "linear">(() => {
    try {
      return localStorage.getItem("umimuko_crossfade_curve") === "linear"
        ? "linear"
        : "equal_power";
    } catch {
      return "equal_power";
    }
  });

  useEffect(() => {
    if (activeDialog !== "equalizer") {
      setPresetMenuOpen(false);
      setSaveDialogOpen(false);
    }
  }, [activeDialog]);

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
    const name = newPresetName.trim();
    if (!name) return;
    await saveEqPreset(name);
    setNewPresetName("");
    setSaveDialogOpen(false);
  };

  const crossfadeSeconds = crossfade.enabled ? crossfade.durationMs / 1000 : 0;
  const isGapless = !crossfade.enabled || crossfadeSeconds <= 0.05;

  return (
    <>
      <DialogShell
        title="Audio DSP & Equalizer"
        subtitle="10-band hardware EQ, playback speed, and crossfade engine"
        maxWidth="max-w-[840px]"
        onClose={() => openDialog(null)}
        headerRight={
          <label className="flex items-center gap-2 cursor-pointer mr-1">
            <span
              className="text-xs font-medium"
              style={{
                color: equalizer.enabled ? "var(--accent)" : "var(--text-secondary)",
              }}
            >
              {equalizer.enabled ? "EQ On" : "EQ Off"}
            </span>
            <button
              type="button"
              role="switch"
              aria-checked={equalizer.enabled}
              onClick={() =>
                void updateEqualizer({ ...equalizer, enabled: !equalizer.enabled })
              }
              className="w-11 h-6 rounded-full p-0.5 transition-colors cursor-pointer"
              style={{
                backgroundColor: equalizer.enabled ? "var(--accent)" : "var(--surface-3)",
              }}
            >
              <span
                className="block w-5 h-5 rounded-full transition-transform"
                style={{
                  backgroundColor: equalizer.enabled
                    ? "var(--on-accent)"
                    : "var(--text-secondary)",
                  transform: equalizer.enabled ? "translateX(20px)" : "translateX(0px)",
                }}
              />
            </button>
          </label>
        }
      >
        <div
          className="space-y-5"
          onClick={() => {
            if (presetMenuOpen) setPresetMenuOpen(false);
          }}
        >
          {/* Presets Bar */}
          <div className="flex flex-wrap items-center gap-3">
            <span className="text-sm font-medium">Preset:</span>

            <div className="relative">
              <button
                type="button"
                disabled={!equalizer.enabled}
                onClick={(e) => {
                  e.stopPropagation();
                  setPresetMenuOpen((v) => !v);
                }}
                className="px-4 py-2 rounded-full text-sm font-medium cursor-pointer disabled:opacity-45"
                style={{
                  border: "1px solid var(--outline-strong)",
                  color: "var(--accent)",
                }}
              >
                {equalizer.presetName || "Custom"}
              </button>

              {presetMenuOpen && (
                <div
                  onClick={(e) => e.stopPropagation()}
                  className="absolute left-0 top-11 z-30 w-56 py-1.5 rounded-2xl shadow-2xl border max-h-64 overflow-y-auto"
                  style={{
                    backgroundColor: "var(--surface-3)",
                    borderColor: "var(--outline-strong)",
                  }}
                >
                  {eqPresets.map((preset) => {
                    const isCurrent = preset.name === equalizer.presetName;
                    return (
                      <button
                        key={preset.id}
                        type="button"
                        onClick={() => {
                          void applyEqPreset(preset);
                          setPresetMenuOpen(false);
                        }}
                        className="w-full px-3.5 py-2 text-left text-xs hover:bg-[var(--surface-variant)] cursor-pointer"
                        style={{ fontWeight: isCurrent ? 700 : 400 }}
                      >
                        {preset.name}
                        {preset.builtIn ? " (Built-in)" : " (Custom)"}
                      </button>
                    );
                  })}
                </div>
              )}
            </div>

            <button
              type="button"
              disabled={!equalizer.enabled}
              onClick={() => {
                setNewPresetName("");
                setSaveDialogOpen(true);
              }}
              className="px-4 py-2 rounded-full text-sm font-medium cursor-pointer disabled:opacity-45"
              style={{
                backgroundColor: "var(--accent)",
                color: "var(--on-accent)",
              }}
            >
              Save Preset
            </button>

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
              className="ml-auto px-4 py-2 rounded-full text-xs font-medium cursor-pointer hover:bg-[var(--surface-variant)]"
              style={{
                border: "1px solid var(--outline-strong)",
                color: "var(--text-secondary)",
              }}
            >
              Reset
            </button>
          </div>

          {/* Frequency Bands (-12dB to +12dB) */}
          <div className="space-y-3">
            <h3 className="text-base font-semibold">
              Frequency Bands (-12dB to +12dB)
            </h3>

            <div
              className="py-4 px-2 rounded-[14px] flex items-center justify-evenly overflow-x-auto"
              style={{
                backgroundColor: "var(--surface-2)",
              }}
            >
              <VerticalEqSlider
                label="Preamp"
                value={equalizer.preampDb}
                min={MIN_PREAMP_DB}
                max={MAX_PREAMP_DB}
                enabled={equalizer.enabled}
                onChange={(val) =>
                  void updateEqualizer({
                    ...equalizer,
                    preampDb: val,
                    presetName: null,
                  })
                }
              />

              <div
                className="w-px h-[180px] mx-1 shrink-0"
                style={{ backgroundColor: "var(--outline-strong)" }}
              />

              {EQ_FREQUENCIES.map((freq, idx) => (
                <VerticalEqSlider
                  key={freq}
                  label={BAND_LABELS[idx] ?? `${freq}Hz`}
                  value={equalizer.bands[idx] ?? 0}
                  min={MIN_GAIN_DB}
                  max={MAX_GAIN_DB}
                  enabled={equalizer.enabled}
                  onChange={(val) => handleBandChange(idx, val)}
                />
              ))}
            </div>
          </div>

          <div className="h-px" style={{ backgroundColor: "var(--outline)" }} />

          {/* Crossfade & Gapless */}
          <div className="space-y-2.5">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div className="flex items-center gap-1.5 text-base font-semibold">
                <span>Crossfade Duration:</span>
                <span className="font-bold" style={{ color: "var(--accent)" }}>
                  {isGapless ? "Gapless (0s)" : `${crossfadeSeconds.toFixed(1)}s`}
                </span>
              </div>

              <div className="flex items-center gap-2">
                <span className="text-xs font-medium" style={{ color: "var(--text-secondary)" }}>
                  Curve:
                </span>
                {(
                  [
                    { id: "equal_power", label: "Equal-Power" },
                    { id: "linear", label: "Linear" },
                  ] as const
                ).map((chip) => {
                  const selected = curvePref === chip.id;
                  return (
                    <button
                      key={chip.id}
                      type="button"
                      onClick={() => {
                        setCurvePref(chip.id);
                        try {
                          localStorage.setItem("umimuko_crossfade_curve", chip.id);
                        } catch {
                          // ignore
                        }
                      }}
                      className="h-8 px-3 rounded-lg text-sm font-medium cursor-pointer transition-colors"
                      style={{
                        backgroundColor: selected
                          ? "var(--accent-container)"
                          : "transparent",
                        color: selected
                          ? "var(--on-accent-container)"
                          : "var(--text-secondary)",
                        border: selected ? "none" : "1px solid var(--outline-strong)",
                      }}
                    >
                      {chip.label}
                    </button>
                  );
                })}
              </div>
            </div>

            <input
              type="range"
              min={0}
              max={MAX_CROSSFADE_S}
              step={0.5}
              value={crossfadeSeconds}
              onChange={(e) => {
                const secs = Number(e.target.value);
                void updateCrossfade({
                  enabled: secs > 0.05,
                  durationMs: Math.round(secs * 1000),
                  gapless: secs <= 0.05,
                });
              }}
              className="umimuko-slider w-full"
              style={{
                background: `linear-gradient(90deg, var(--accent) ${(crossfadeSeconds / MAX_CROSSFADE_S) * 100}%, var(--surface-3) ${(crossfadeSeconds / MAX_CROSSFADE_S) * 100}%)`,
                height: 4,
                borderRadius: 999,
              }}
            />
          </div>

          <div className="flex justify-end pt-2">
            <button
              type="button"
              onClick={() => openDialog(null)}
              className="px-5 py-2 rounded-full text-sm font-medium cursor-pointer"
              style={{
                backgroundColor: "var(--accent-container)",
                color: "var(--on-accent-container)",
              }}
            >
              Close
            </button>
          </div>
        </div>
      </DialogShell>

      {saveDialogOpen && (
        <div
          className="fixed inset-0 z-60 flex items-center justify-center p-4 bg-black/50"
          onMouseDown={(e) => {
            if (e.target === e.currentTarget) setSaveDialogOpen(false);
          }}
        >
          <form
            onSubmit={handleSavePreset}
            className="w-full max-w-sm rounded-[24px] p-6 space-y-4 shadow-2xl"
            style={{
              backgroundColor: "var(--surface-2)",
              border: "1px solid var(--outline)",
              color: "var(--text-primary)",
            }}
          >
            <h3 className="text-lg font-bold">Save Equalizer Preset</h3>
            <label className="block space-y-1.5">
              <span className="text-xs font-medium" style={{ color: "var(--text-secondary)" }}>
                Preset Name
              </span>
              <input
                type="text"
                autoFocus
                value={newPresetName}
                onChange={(e) => setNewPresetName(e.target.value)}
                placeholder="My EQ Preset"
                className="w-full px-3.5 py-2.5 rounded-xl text-sm outline-none"
                style={{
                  backgroundColor: "var(--surface-1)",
                  border: "1px solid var(--outline-strong)",
                }}
              />
            </label>
            <div className="flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setSaveDialogOpen(false)}
                className="px-4 py-2 rounded-full text-sm cursor-pointer hover:bg-[var(--surface-variant)]"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={!newPresetName.trim()}
                className="px-5 py-2 rounded-full text-sm font-medium cursor-pointer disabled:opacity-45"
                style={{
                  backgroundColor: "var(--accent)",
                  color: "var(--on-accent)",
                }}
              >
                Save
              </button>
            </div>
          </form>
        </div>
      )}
    </>
  );
}
