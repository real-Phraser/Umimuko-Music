import { open as openFileDialog } from "@tauri-apps/plugin-dialog";
import { useState } from "react";
import { isTauriRuntime } from "../../api/tauri";
import { useLibraryStore } from "../../store/useLibraryStore";
import { useSettingsStore } from "../../store/useSettingsStore";
import type { KeybindingsConfig, ThemeMode } from "../../types/ipc";
import { Icon } from "../Icon";
import { DialogShell } from "./DialogShell";

const KEYBINDING_LABELS: Array<{ key: keyof KeybindingsConfig; label: string }> = [
  { key: "playPause", label: "Play / Pause" },
  { key: "nextTrack", label: "Next Track" },
  { key: "prevTrack", label: "Previous Track" },
  { key: "volumeUp", label: "Volume Up" },
  { key: "volumeDown", label: "Volume Down" },
  { key: "toggleMute", label: "Mute" },
  { key: "toggleLyrics", label: "Toggle Lyrics" },
  { key: "toggleQueue", label: "Toggle Queue" },
  { key: "focusSearch", label: "Focus Search" },
];

export function SettingsDialog() {
  const activeDialog = useLibraryStore((s) => s.activeDialog);
  const openDialog = useLibraryStore((s) => s.openDialog);
  const scanFolders = useLibraryStore((s) => s.scanFolders);

  const settings = useSettingsStore((s) => s.settings);
  const saveSettings = useSettingsStore((s) => s.saveSettings);
  const setTheme = useSettingsStore((s) => s.setTheme);
  const setAdaptiveAccent = useSettingsStore((s) => s.setAdaptiveAccent);
  const addFolder = useSettingsStore((s) => s.addFolder);
  const removeFolder = useSettingsStore((s) => s.removeFolder);
  const updateCrossfade = useSettingsStore((s) => s.updateCrossfade);

  const [folderInput, setFolderInput] = useState("");
  const [capturingKey, setCapturingKey] = useState<keyof KeybindingsConfig | null>(null);

  if (activeDialog !== "settings") return null;

  const handleBrowseFolder = async () => {
    if (isTauriRuntime()) {
      const selected = await openFileDialog({ directory: true, multiple: false });
      if (typeof selected === "string" && selected) {
        await addFolder(selected);
        await scanFolders();
      }
      return;
    }
    const trimmed = folderInput.trim();
    if (trimmed) {
      await addFolder(trimmed);
      setFolderInput("");
      await scanFolders();
    }
  };

  return (
    <DialogShell
      title="Settings"
      subtitle="Appearance, music folders, crossfade, SpicyLyrics, and keybindings"
      onClose={() => openDialog(null)}
    >
      <div className="space-y-6">
        {/* Appearance */}
        <section className="space-y-3">
          <h3 className="text-sm font-semibold">Appearance</h3>
          <div className="flex flex-wrap items-center gap-2">
            {(["dark", "light", "system"] as ThemeMode[]).map((mode) => {
              const active = settings.theme === mode;
              return (
                <button
                  key={mode}
                  type="button"
                  onClick={() => void setTheme(mode)}
                  className="px-4 py-1.5 rounded-full text-xs font-medium capitalize cursor-pointer"
                  style={{
                    backgroundColor: active ? "var(--accent)" : "var(--surface-1)",
                    color: active ? "var(--on-accent)" : "var(--text-secondary)",
                    border: "1px solid var(--outline)",
                  }}
                >
                  {mode}
                </button>
              );
            })}
          </div>

          <label className="flex items-center gap-2.5 cursor-pointer">
            <input
              type="checkbox"
              checked={settings.adaptiveAccent}
              onChange={(e) => void setAdaptiveAccent(e.target.checked)}
              className="w-4 h-4 accent-[var(--accent)] cursor-pointer"
            />
            <span className="text-sm">Album-adaptive accent color</span>
          </label>
        </section>

        {/* Music Folders */}
        <section className="space-y-3">
          <div className="flex items-center justify-between">
            <h3 className="text-sm font-semibold">Music Folders</h3>
            <button
              type="button"
              onClick={() => void scanFolders()}
              className="text-xs font-medium cursor-pointer"
              style={{ color: "var(--accent)" }}
            >
              Rescan Now
            </button>
          </div>

          <div className="space-y-2">
            {settings.folders.map((folder) => (
              <div
                key={folder}
                className="flex items-center justify-between px-3.5 py-2 rounded-xl text-xs"
                style={{
                  backgroundColor: "var(--surface-1)",
                  border: "1px solid var(--outline)",
                }}
              >
                <span className="truncate">{folder}</span>
                <button
                  type="button"
                  onClick={() => void removeFolder(folder)}
                  className="p-1 rounded-full hover:opacity-80 cursor-pointer"
                  style={{ color: "var(--danger)" }}
                  title="Remove folder"
                >
                  <Icon name="remove" size={15} />
                </button>
              </div>
            ))}
          </div>

          <div className="flex items-center gap-2">
            {!isTauriRuntime() && (
              <input
                type="text"
                value={folderInput}
                onChange={(e) => setFolderInput(e.target.value)}
                placeholder="/path/to/music…"
                className="flex-1 px-3.5 py-2 rounded-full text-xs outline-none"
                style={{
                  backgroundColor: "var(--surface-1)",
                  border: "1px solid var(--outline)",
                }}
              />
            )}
            <button
              type="button"
              onClick={() => void handleBrowseFolder()}
              className="px-4 py-2 rounded-full text-xs font-medium flex items-center gap-1.5 cursor-pointer"
              style={{
                backgroundColor: "var(--accent)",
                color: "var(--on-accent)",
              }}
            >
              <Icon name="add" size={15} />
              <span>Add Folder</span>
            </button>
          </div>
        </section>

        {/* Crossfade & Gapless */}
        <section className="space-y-3">
          <h3 className="text-sm font-semibold">Playback & Crossfade</h3>
          <div
            className="p-4 rounded-2xl space-y-3"
            style={{
              backgroundColor: "var(--surface-1)",
              border: "1px solid var(--outline)",
            }}
          >
            <label className="flex items-center gap-2.5 cursor-pointer">
              <input
                type="checkbox"
                checked={settings.crossfade.gapless}
                onChange={(e) =>
                  void updateCrossfade({
                    ...settings.crossfade,
                    gapless: e.target.checked,
                  })
                }
                className="w-4 h-4 accent-[var(--accent)] cursor-pointer"
              />
              <span className="text-sm">Gapless playback</span>
            </label>

            <label className="flex items-center gap-2.5 cursor-pointer">
              <input
                type="checkbox"
                checked={settings.crossfade.enabled}
                onChange={(e) =>
                  void updateCrossfade({
                    ...settings.crossfade,
                    enabled: e.target.checked,
                  })
                }
                className="w-4 h-4 accent-[var(--accent)] cursor-pointer"
              />
              <span className="text-sm">Crossfade between tracks</span>
            </label>

            {settings.crossfade.enabled && (
              <div className="flex items-center gap-3 pt-1">
                <span className="text-xs w-20" style={{ color: "var(--text-secondary)" }}>
                  Duration
                </span>
                <input
                  type="range"
                  min={500}
                  max={10000}
                  step={500}
                  value={settings.crossfade.durationMs}
                  onChange={(e) =>
                    void updateCrossfade({
                      ...settings.crossfade,
                      durationMs: Number(e.target.value),
                    })
                  }
                  className="umimuko-slider flex-1"
                />
                <span className="text-xs tabular-nums w-12 text-right">
                  {(settings.crossfade.durationMs / 1000).toFixed(1)}s
                </span>
              </div>
            )}
          </div>
        </section>

        {/* SpicyLyrics API Key */}
        <section className="space-y-2">
          <h3 className="text-sm font-semibold">SpicyLyrics</h3>
          <input
            type="password"
            value={settings.spicyLyricsApiKey}
            onChange={(e) =>
              void saveSettings({
                ...settings,
                spicyLyricsApiKey: e.target.value,
              })
            }
            placeholder="Optional SpicyLyrics API key…"
            className="w-full px-3.5 py-2 rounded-xl text-sm outline-none"
            style={{
              backgroundColor: "var(--surface-1)",
              border: "1px solid var(--outline)",
            }}
          />
        </section>

        {/* Keybindings */}
        <section className="space-y-2.5">
          <h3 className="text-sm font-semibold flex items-center gap-2">
            <Icon name="keyboard" size={17} />
            <span>Keybindings</span>
          </h3>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
            {KEYBINDING_LABELS.map((item) => {
              const isCapturing = capturingKey === item.key;
              return (
                <div
                  key={item.key}
                  className="flex items-center justify-between px-3 py-2 rounded-xl text-xs"
                  style={{
                    backgroundColor: "var(--surface-1)",
                    border: "1px solid var(--outline)",
                  }}
                >
                  <span>{item.label}</span>
                  <button
                    type="button"
                    onClick={() => setCapturingKey(item.key)}
                    onKeyDown={(e) => {
                      if (!isCapturing) return;
                      e.preventDefault();
                      void saveSettings({
                        ...settings,
                        keybindings: {
                          ...settings.keybindings,
                          [item.key]: e.code,
                        },
                      });
                      setCapturingKey(null);
                    }}
                    className="px-2.5 py-1 rounded-lg font-mono text-[11px] cursor-pointer"
                    style={{
                      backgroundColor: isCapturing
                        ? "var(--accent)"
                        : "var(--surface-3)",
                      color: isCapturing ? "var(--on-accent)" : "var(--text-primary)",
                    }}
                  >
                    {isCapturing ? "Press key…" : settings.keybindings[item.key]}
                  </button>
                </div>
              );
            })}
          </div>
        </section>
      </div>
    </DialogShell>
  );
}
