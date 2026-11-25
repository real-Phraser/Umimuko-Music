import { open as openFileDialog } from "@tauri-apps/plugin-dialog";
import { useEffect, useState } from "react";
import { isTauriRuntime } from "../../api/tauri";
import appIconUrl from "../../assets/branding/window-icon.png";
import { useLibraryStore } from "../../store/useLibraryStore";
import { useSettingsStore } from "../../store/useSettingsStore";
import type { KeybindingsConfig, ThemeMode } from "../../types/ipc";
import { Icon, type IconName } from "../Icon";

type SettingsTabId =
  | "audio"
  | "library"
  | "lyrics"
  | "appearance"
  | "keybindings"
  | "stats"
  | "about";

const SETTINGS_TABS: Array<{ id: SettingsTabId; title: string; icon: IconName }> = [
  { id: "audio", title: "Audio Engine", icon: "levels" },
  { id: "library", title: "Library & Folders", icon: "folder" },
  { id: "lyrics", title: "Lyrics", icon: "lyrics" },
  { id: "appearance", title: "Appearance & System", icon: "settings" },
  { id: "keybindings", title: "Keybindings", icon: "keyboard" },
  { id: "stats", title: "Listening Stats", icon: "queue" },
  { id: "about", title: "About & Credits", icon: "info" },
];

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
  const tracksCount = useLibraryStore((s) => s.tracks.length);

  const settings = useSettingsStore((s) => s.settings);
  const saveSettings = useSettingsStore((s) => s.saveSettings);
  const setTheme = useSettingsStore((s) => s.setTheme);
  const setAdaptiveAccent = useSettingsStore((s) => s.setAdaptiveAccent);
  const addFolder = useSettingsStore((s) => s.addFolder);
  const removeFolder = useSettingsStore((s) => s.removeFolder);
  const updateCrossfade = useSettingsStore((s) => s.updateCrossfade);

  const [activeTab, setActiveTab] = useState<SettingsTabId>("audio");
  const [folderInput, setFolderInput] = useState("");
  const [showApiKey, setShowApiKey] = useState(false);
  const [capturingKey, setCapturingKey] = useState<keyof KeybindingsConfig | null>(
    null
  );

  useEffect(() => {
    if (activeDialog !== "settings") return;
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape" && !capturingKey) {
        openDialog(null);
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [activeDialog, capturingKey, openDialog]);

  if (activeDialog !== "settings") return null;

  const currentTab = SETTINGS_TABS.find((t) => t.id === activeTab)!;

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
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-6 bg-black/55 backdrop-blur-[2px]"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) openDialog(null);
      }}
    >
      <div
        className="w-full max-w-[920px] h-[85vh] max-h-[680px] rounded-[24px] flex overflow-hidden shadow-2xl adaptive-transition"
        style={{
          backgroundColor: "var(--surface-2)",
          border: "1px solid var(--outline)",
          boxShadow: "var(--shadow-elevated)",
        }}
      >
        {/* Left Navigation Sidebar */}
        <aside
          className="w-56 shrink-0 flex flex-col p-4 gap-1.5 border-r"
          style={{
            backgroundColor: "var(--surface-1)",
            borderColor: "var(--outline)",
          }}
        >
          <div
            className="px-3 py-2 text-[11px] font-bold tracking-wider uppercase"
            style={{ color: "var(--accent)" }}
          >
            SETTINGS
          </div>

          {SETTINGS_TABS.map((tab) => {
            const selected = activeTab === tab.id;
            return (
              <button
                key={tab.id}
                type="button"
                onClick={() => setActiveTab(tab.id)}
                className="w-full px-3.5 py-2.5 rounded-[14px] flex items-center gap-3 text-left text-xs transition-colors cursor-pointer"
                style={{
                  backgroundColor: selected
                    ? "var(--accent-container)"
                    : "transparent",
                  color: selected
                    ? "var(--on-accent-container)"
                    : "var(--text-primary)",
                  fontWeight: selected ? 700 : 500,
                }}
              >
                <Icon
                  name={tab.icon}
                  size={18}
                  style={{
                    color: selected
                      ? "var(--on-accent-container)"
                      : "var(--text-secondary)",
                  }}
                />
                <span className="truncate">{tab.title}</span>
              </button>
            );
          })}

          <div className="flex-1" />

          <div
            className="px-3 text-[11px] opacity-60"
            style={{ color: "var(--text-secondary)" }}
          >
            Umimuko Music v0.26.1
          </div>
        </aside>

        {/* Right Tab Content Pane */}
        <div className="flex-1 min-w-0 flex flex-col p-7">
          <div className="flex items-center justify-between pb-4 border-b" style={{ borderColor: "var(--outline)" }}>
            <h2 className="text-xl font-bold">{currentTab.title}</h2>
            <button
              type="button"
              onClick={() => openDialog(null)}
              className="w-9 h-9 rounded-full flex items-center justify-center hover:bg-[var(--surface-variant)] cursor-pointer"
              style={{ color: "var(--text-secondary)" }}
              title="Close"
            >
              <Icon name="close" size={18} />
            </button>
          </div>

          <div className="flex-1 overflow-y-auto pt-5 space-y-6 pr-1">
            {activeTab === "audio" && (
              <>
                <div
                  className="p-4 rounded-2xl flex items-center justify-between gap-4"
                  style={{
                    backgroundColor: "var(--surface-1)",
                    border: "1px solid var(--outline)",
                  }}
                >
                  <div>
                    <div className="text-sm font-semibold">10-Band Equalizer</div>
                    <div className="text-xs mt-0.5" style={{ color: "var(--text-secondary)" }}>
                      Configure peaking EQ bands, preamp gain, and custom presets
                    </div>
                  </div>
                  <button
                    type="button"
                    onClick={() => openDialog("equalizer")}
                    className="px-4 py-2 rounded-full text-xs font-semibold cursor-pointer shrink-0"
                    style={{
                      backgroundColor: "var(--accent)",
                      color: "var(--on-accent)",
                    }}
                  >
                    Open Equalizer
                  </button>
                </div>

                <div
                  className="p-4 rounded-2xl space-y-3"
                  style={{
                    backgroundColor: "var(--surface-1)",
                    border: "1px solid var(--outline)",
                  }}
                >
                  <div className="text-sm font-semibold">Crossfade & Gapless</div>
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
              </>
            )}

            {activeTab === "library" && (
              <>
                <div className="flex items-center justify-between">
                  <div>
                    <div className="text-sm font-semibold">Music Folders</div>
                    <div className="text-xs" style={{ color: "var(--text-secondary)" }}>
                      {tracksCount} tracks indexed in your library
                    </div>
                  </div>
                  <button
                    type="button"
                    onClick={() => void scanFolders()}
                    className="px-3.5 py-1.5 rounded-full text-xs font-semibold cursor-pointer"
                    style={{
                      backgroundColor: "var(--accent-container)",
                      color: "var(--on-accent-container)",
                    }}
                  >
                    Rescan Now
                  </button>
                </div>

                <div className="space-y-2">
                  {settings.folders.length === 0 ? (
                    <div
                      className="p-4 rounded-2xl text-xs text-center"
                      style={{
                        backgroundColor: "var(--surface-1)",
                        color: "var(--text-secondary)",
                        border: "1px solid var(--outline)",
                      }}
                    >
                      No music folders added yet.
                    </div>
                  ) : (
                    settings.folders.map((folder) => (
                      <div
                        key={folder}
                        className="flex items-center justify-between px-3.5 py-2.5 rounded-xl text-xs"
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
                    ))
                  )}
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
                    className="px-4 py-2 rounded-full text-xs font-semibold flex items-center gap-1.5 cursor-pointer"
                    style={{
                      backgroundColor: "var(--accent)",
                      color: "var(--on-accent)",
                    }}
                  >
                    <Icon name="add" size={15} />
                    <span>Add Folder</span>
                  </button>
                </div>

                <div
                  className="p-4 rounded-2xl flex items-center justify-between gap-4"
                  style={{
                    backgroundColor: "var(--surface-1)",
                    border: "1px solid var(--outline)",
                  }}
                >
                  <div>
                    <div className="text-sm font-semibold">Duplicate Track Detection</div>
                    <div className="text-xs mt-0.5" style={{ color: "var(--text-secondary)" }}>
                      Find exact file hash or metadata + duration duplicates
                    </div>
                  </div>
                  <button
                    type="button"
                    onClick={() => openDialog("duplicates")}
                    className="px-4 py-2 rounded-full text-xs font-semibold cursor-pointer shrink-0"
                    style={{
                      backgroundColor: "var(--surface-3)",
                      color: "var(--text-primary)",
                    }}
                  >
                    Find Duplicates
                  </button>
                </div>
              </>
            )}

            {activeTab === "lyrics" && (
              <div
                className="p-4 rounded-2xl space-y-3"
                style={{
                  backgroundColor: "var(--surface-1)",
                  border: "1px solid var(--outline)",
                }}
              >
                <div>
                  <div className="text-sm font-semibold">SpicyLyrics API Key</div>
                  <div className="text-xs mt-0.5" style={{ color: "var(--text-secondary)" }}>
                    Optional API key for syllable-synced karaoke lyrics lookup. Embedded
                    tags and local .lrc files work automatically without a key.
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  <input
                    type={showApiKey ? "text" : "password"}
                    value={settings.spicyLyricsApiKey}
                    onChange={(e) =>
                      void saveSettings({
                        ...settings,
                        spicyLyricsApiKey: e.target.value,
                      })
                    }
                    placeholder="Enter SpicyLyrics API key…"
                    className="flex-1 px-3.5 py-2 rounded-xl text-sm outline-none"
                    style={{
                      backgroundColor: "var(--surface-2)",
                      border: "1px solid var(--outline)",
                    }}
                  />
                  <button
                    type="button"
                    onClick={() => setShowApiKey((v) => !v)}
                    className="px-3.5 py-2 rounded-xl text-xs font-medium cursor-pointer"
                    style={{ backgroundColor: "var(--surface-3)" }}
                  >
                    {showApiKey ? "Hide" : "Show"}
                  </button>
                </div>
              </div>
            )}

            {activeTab === "appearance" && (
              <div className="space-y-4">
                <div
                  className="p-4 rounded-2xl space-y-3"
                  style={{
                    backgroundColor: "var(--surface-1)",
                    border: "1px solid var(--outline)",
                  }}
                >
                  <div className="text-sm font-semibold">Theme</div>
                  <div className="flex flex-wrap items-center gap-2">
                    {(["dark", "light", "system"] as ThemeMode[]).map((mode) => {
                      const active = settings.theme === mode;
                      return (
                        <button
                          key={mode}
                          type="button"
                          onClick={() => void setTheme(mode)}
                          className="px-4 py-1.5 rounded-full text-xs font-semibold capitalize cursor-pointer"
                          style={{
                            backgroundColor: active
                              ? "var(--accent)"
                              : "var(--surface-2)",
                            color: active
                              ? "var(--on-accent)"
                              : "var(--text-secondary)",
                            border: "1px solid var(--outline)",
                          }}
                        >
                          {mode}
                        </button>
                      );
                    })}
                  </div>

                  <label className="flex items-center gap-2.5 cursor-pointer pt-1">
                    <input
                      type="checkbox"
                      checked={settings.adaptiveAccent}
                      onChange={(e) => void setAdaptiveAccent(e.target.checked)}
                      className="w-4 h-4 accent-[var(--accent)] cursor-pointer"
                    />
                    <span className="text-sm">Album-adaptive accent color</span>
                  </label>
                </div>

                <div
                  className="p-4 rounded-2xl space-y-2.5"
                  style={{
                    backgroundColor: "var(--surface-1)",
                    border: "1px solid var(--outline)",
                  }}
                >
                  <div className="text-sm font-semibold">System Behavior</div>
                  <label className="flex items-center gap-2.5 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={settings.minimizeToTray}
                      onChange={(e) =>
                        void saveSettings({
                          ...settings,
                          minimizeToTray: e.target.checked,
                        })
                      }
                      className="w-4 h-4 accent-[var(--accent)] cursor-pointer"
                    />
                    <span className="text-sm">Minimize to system tray</span>
                  </label>
                  <label className="flex items-center gap-2.5 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={settings.showMiniPlayerOnClose}
                      onChange={(e) =>
                        void saveSettings({
                          ...settings,
                          showMiniPlayerOnClose: e.target.checked,
                        })
                      }
                      className="w-4 h-4 accent-[var(--accent)] cursor-pointer"
                    />
                    <span className="text-sm">Show mini player when closing window</span>
                  </label>
                </div>
              </div>
            )}

            {activeTab === "keybindings" && (
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
                {KEYBINDING_LABELS.map((item) => {
                  const isCapturing = capturingKey === item.key;
                  return (
                    <div
                      key={item.key}
                      className="flex items-center justify-between px-3.5 py-2.5 rounded-xl text-xs"
                      style={{
                        backgroundColor: "var(--surface-1)",
                        border: "1px solid var(--outline)",
                      }}
                    >
                      <span className="font-medium">{item.label}</span>
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
                          color: isCapturing
                            ? "var(--on-accent)"
                            : "var(--text-primary)",
                        }}
                      >
                        {isCapturing ? "Press key…" : settings.keybindings[item.key]}
                      </button>
                    </div>
                  );
                })}
              </div>
            )}

            {activeTab === "stats" && (
              <div
                className="p-5 rounded-2xl flex items-center justify-between gap-4"
                style={{
                  backgroundColor: "var(--surface-1)",
                  border: "1px solid var(--outline)",
                }}
              >
                <div>
                  <div className="text-sm font-semibold">Listening Statistics</div>
                  <div className="text-xs mt-0.5" style={{ color: "var(--text-secondary)" }}>
                    View your total listening time, top tracks, and top artists
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => openDialog("stats")}
                  className="px-4 py-2 rounded-full text-xs font-semibold cursor-pointer shrink-0"
                  style={{
                    backgroundColor: "var(--accent)",
                    color: "var(--on-accent)",
                  }}
                >
                  Open Stats
                </button>
              </div>
            )}

            {activeTab === "about" && (
              <div
                className="p-6 rounded-2xl flex flex-col items-center text-center gap-3"
                style={{
                  backgroundColor: "var(--surface-1)",
                  border: "1px solid var(--outline)",
                }}
              >
                <img
                  src={appIconUrl}
                  alt="Umimuko Music"
                  className="w-16 h-16 rounded-2xl object-contain"
                  draggable={false}
                />
                <div>
                  <h3 className="text-lg font-bold">Umimuko Music</h3>
                  <p className="text-xs" style={{ color: "var(--text-secondary)" }}>
                    Version 0.26.1
                  </p>
                </div>
                <p className="text-xs max-w-md" style={{ color: "var(--text-secondary)" }}>
                  Desktop music player powered by a pure-Rust audio engine, 10-band
                  biquad EQ, gapless & crossfade playback, and SpicyLyrics karaoke.
                </p>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
