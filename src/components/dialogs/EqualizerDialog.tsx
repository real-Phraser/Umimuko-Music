import { useEffect, useRef, useState } from "react";
import { useLibraryStore } from "../../store/useLibraryStore";
import { useSettingsStore } from "../../store/useSettingsStore";
import { EQ_FREQUENCIES } from "../../types/ipc";
import { Icon } from "../Icon";
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
    const rounded = Number(snapped.toFixed(4));
    onChange(Math.min(max, Math.max(min, rounded)));
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
          onChange(Math.min(max, Number((clamped + step).toFixed(4))));
        } else if (e.key === "ArrowLeft" || e.key === "ArrowDown") {
          e.preventDefault();
          onChange(Math.max(min, Number((clamped - step).toFixed(4))));
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
        className={`group relative h-[130px] w-10 my-1.5 flex items-center justify-center outline-none ${
          enabled ? "cursor-pointer" : "cursor-default opacity-45"
        }`}
      >
        {/* Rail */}
        <div
          className="relative w-1 h-full rounded-full"
          style={{ backgroundColor: "var(--surface-3)" }}
        >
          {/* 0dB center tick */}
          <div
            className="absolute left-1/2 -translate-x-1/2 translate-y-1/2 w-2 h-0.5 rounded-full opacity-50"
            style={{
              bottom: `${zeroRatio * 100}%`,
              backgroundColor: "var(--text-muted)",
            }}
          />
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

        {/* Thumb (20dp M3) */}
        <div
          className="absolute left-1/2 w-5 h-5 rounded-full shadow-md pointer-events-none transition-transform duration-100"
          style={{
            bottom: `${ratio * 100}%`,
            backgroundColor: "var(--accent)",
            transform: `translate(-50%, 50%) scale(${dragging ? 1.15 : 1})`,
            boxShadow: dragging
              ? "0 0 0 8px rgba(var(--accent-rgb), 0.18), 0 2px 6px rgba(0,0,0,0.35)"
              : "0 1px 4px rgba(0,0,0,0.35)",
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
            <M3Switch
              checked={equalizer.enabled}
              onChange={(next) => void updateEqualizer({ ...equalizer, enabled: next })}
              label={equalizer.enabled ? "Disable Equalizer" : "Enable Equalizer"}
            />
          </label>
        }
      >
        <div
          className="space-y-5"
          onClick={() => {
            if (presetMenuOpen) setPresetMenuOpen(false);
          }}
        >
          {/* Presets Bar (EqualizerDialog.kt lines 98-157) */}
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
                className="h-10 px-4 rounded-full text-sm font-medium flex items-center gap-2 cursor-pointer hover:bg-[var(--surface-variant)] disabled:opacity-45 transition-colors"
                style={{
                  border: "1px solid var(--outline-strong)",
                  color: "var(--accent)",
                }}
              >
                <span>{equalizer.presetName || "Custom"}</span>
                <Icon name="down" size={16} />
              </button>

              {presetMenuOpen && (
                <div
                  onClick={(e) => e.stopPropagation()}
                  className="absolute left-0 top-11 z-30 w-60 py-1.5 rounded-xl shadow-2xl border max-h-64 overflow-y-auto"
                  style={{
                    backgroundColor: "var(--surface-3)",
                    borderColor: "var(--outline)",
                    boxShadow: "var(--shadow-elevated)",
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
                        className="w-full px-3.5 py-2.5 text-left text-[13px] leading-[18px] hover:bg-[var(--surface-variant)] cursor-pointer transition-colors"
                        style={{
                          fontWeight: isCurrent ? 700 : 500,
                          color: isCurrent ? "var(--accent)" : "var(--text-primary)",
                        }}
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
              className="h-10 px-5 rounded-full text-sm font-medium cursor-pointer disabled:opacity-45 transition-opacity hover:opacity-90"
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
              className="ml-auto h-10 px-4 rounded-full text-sm font-medium cursor-pointer hover:bg-[var(--surface-variant)] transition-colors"
              style={{
                border: "1px solid var(--outline-strong)",
                color: "var(--accent)",
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

          {/* Crossfade & Gapless (EqualizerDialog.kt lines 247-282) */}
          <div className="space-y-2">
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
                      className="h-8 px-3 rounded-[8px] text-[13px] font-medium cursor-pointer transition-colors"
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

            <M3Slider
              label="Crossfade Duration"
              value={crossfadeSeconds}
              min={0}
              max={MAX_CROSSFADE_S}
              step={0.5}
              steps={23}
              onChange={(secs) => {
                void updateCrossfade({
                  enabled: secs > 0.05,
                  durationMs: Math.round(secs * 1000),
                  gapless: secs <= 0.05,
                });
              }}
            />
          </div>

          <div className="flex justify-end pt-2">
            <button
              type="button"
              onClick={() => openDialog(null)}
              className="h-10 px-5 rounded-full text-sm font-medium cursor-pointer transition-opacity hover:opacity-90"
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
          className="fixed inset-0 z-60 flex items-center justify-center p-4 bg-black/55 backdrop-blur-[2px]"
          onMouseDown={(e) => {
            if (e.target === e.currentTarget) setSaveDialogOpen(false);
          }}
        >
          <form
            onSubmit={handleSavePreset}
            className="w-full max-w-sm rounded-[28px] p-6 space-y-4 shadow-2xl"
            style={{
              backgroundColor: "var(--surface-2)",
              border: "1px solid var(--outline)",
              color: "var(--text-primary)",
            }}
          >
            <h3 className="text-[22px] leading-7 font-normal">Save Equalizer Preset</h3>
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
                className="w-full px-3.5 py-2.5 rounded-xl text-sm outline-none border border-[var(--outline-strong)] focus:border-[var(--accent)] transition-colors"
                style={{
                  backgroundColor: "var(--surface-1)",
                }}
              />
            </label>
            <div className="flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setSaveDialogOpen(false)}
                className="h-10 px-4 rounded-full text-sm font-medium cursor-pointer hover:bg-[var(--surface-variant)]"
                style={{ color: "var(--accent)" }}
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={!newPresetName.trim()}
                className="h-10 px-5 rounded-full text-sm font-medium cursor-pointer disabled:opacity-45"
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
