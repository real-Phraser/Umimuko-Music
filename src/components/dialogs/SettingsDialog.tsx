import { open as openFileDialog } from "@tauri-apps/plugin-dialog";
import { useEffect, useState } from "react";
import { api, isTauriRuntime } from "../../api/tauri";
import appIconUrl from "../../assets/branding/app-icon.png";
import githubIconUrl from "../../assets/branding/github.png";
import { useLibraryStore } from "../../store/useLibraryStore";
import { usePlayerStore } from "../../store/usePlayerStore";
import { useSettingsStore } from "../../store/useSettingsStore";
import {
  THEME_PRESETS,
  getStoredThemePresetId,
  setStoredThemePresetId,
} from "../../theme/ThemeProvider";
import type { ListeningStats } from "../../types/ipc";
import { Icon, type IconName } from "../Icon";
import { getStartupIntroEnabled, setStartupIntroEnabled } from "../StartupIntro";
import { getShowRomanizedPref, setShowRomanizedPref } from "../lyrics/SpicyLyricsView";
import { KeybindingsTab } from "./KeybindingsTab";

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
      className="w-12 h-7 rounded-full p-1 transition-colors cursor-pointer shrink-0"
      style={{
        backgroundColor: checked ? "var(--accent)" : "var(--surface-3)",
        border: checked ? "none" : "1px solid var(--outline-strong)",
      }}
    >
      <span
        className="block w-5 h-5 rounded-full transition-transform"
        style={{
          backgroundColor: checked ? "var(--on-accent)" : "var(--text-secondary)",
          transform: checked ? "translateX(20px)" : "translateX(0px)",
        }}
      />
    </button>
  );
}

export function SettingsDialog() {
  const activeDialog = useLibraryStore((s) => s.activeDialog);
  const openDialog = useLibraryStore((s) => s.openDialog);
  const scanFolders = useLibraryStore((s) => s.scanFolders);
  const tracksCount = useLibraryStore((s) => s.tracks.length);
  const currentTrackId = usePlayerStore((s) => s.playback.currentTrackId);
  const refreshLyrics = usePlayerStore((s) => s.refreshLyrics);

  const settings = useSettingsStore((s) => s.settings);
  const saveSettings = useSettingsStore((s) => s.saveSettings);
  const setTheme = useSettingsStore((s) => s.setTheme);
  const setAdaptiveAccent = useSettingsStore((s) => s.setAdaptiveAccent);
  const addFolder = useSettingsStore((s) => s.addFolder);
  const removeFolder = useSettingsStore((s) => s.removeFolder);
  const updateEqualizer = useSettingsStore((s) => s.updateEqualizer);
  const updateCrossfade = useSettingsStore((s) => s.updateCrossfade);

  const [activeTab, setActiveTab] = useState<SettingsTabId>("audio");
  const [folderInput, setFolderInput] = useState("");
  const [apiKeyInput, setApiKeyInput] = useState(settings.spicyLyricsApiKey);
  const [showApiKey, setShowApiKey] = useState(false);
  const [keyStatusMsg, setKeyStatusMsg] = useState<string | null>(null);
  const [cacheStatusMsg, setCacheStatusMsg] = useState<string | null>(null);
  const [showRomanized, setShowRomanized] = useState(getShowRomanizedPref);
  const [curvePref, setCurvePref] = useState<"equal_power" | "linear">(() => {
    try {
      return localStorage.getItem("umimuko_crossfade_curve") === "linear"
        ? "linear"
        : "equal_power";
    } catch {
      return "equal_power";
    }
  });
  const [selectedPresetId, setSelectedPresetId] = useState<string>(getStoredThemePresetId);
  const [introEnabled, setIntroEnabled] = useState<boolean>(getStartupIntroEnabled);
  const [stats, setStats] = useState<ListeningStats | null>(null);

  useEffect(() => {
    setApiKeyInput(settings.spicyLyricsApiKey);
  }, [settings.spicyLyricsApiKey]);

  useEffect(() => {
    if (activeDialog === "settings" && activeTab === "stats") {
      void api.getListeningStats().then(setStats);
    }
  }, [activeDialog, activeTab]);

  useEffect(() => {
    if (activeDialog !== "settings") return;
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        openDialog(null);
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [activeDialog, openDialog]);

  if (activeDialog !== "settings") return null;

  const currentTab = SETTINGS_TABS.find((t) => t.id === activeTab)!;
  const crossfadeSeconds = settings.crossfade.enabled
    ? settings.crossfade.durationMs / 1000
    : 0;
  const isGapless = !settings.crossfade.enabled || crossfadeSeconds <= 0.05;

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
      {/* SettingsDialog.kt: widthIn(min=780dp, max=960dp), fillMaxHeight(0.88f), RoundedCornerShape(24dp) */}
      <div
        className="w-[min(940px,calc(100vw-48px))] h-[88vh] max-h-[700px] rounded-[24px] flex overflow-hidden adaptive-transition"
        style={{
          backgroundColor: "var(--surface-1)",
          color: "var(--text-primary)",
          border: "1px solid var(--outline)",
          boxShadow: "var(--shadow-elevated)",
        }}
      >
        {/* Left Sidebar Navigation (252px so "Appearance & System" fits without truncation) */}
        <aside
          className="w-[252px] shrink-0 flex flex-col p-4 gap-1.5 border-r"
          style={{
            backgroundColor: "color-mix(in srgb, var(--surface-2) 65%, var(--surface-1))",
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
                className="w-full px-3.5 py-3 rounded-[14px] flex items-center gap-3 text-left text-sm transition-colors cursor-pointer"
                style={{
                  backgroundColor: selected ? "var(--accent-container)" : "transparent",
                  color: selected
                    ? "var(--on-accent-container)"
                    : "var(--text-primary)",
                  fontWeight: selected ? 700 : 500,
                }}
              >
                <Icon
                  name={tab.icon}
                  size={20}
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
            className="px-3 text-[11px]"
            style={{ color: "var(--text-secondary)", opacity: 0.6 }}
          >
            Umimuko Music v0.26.1
          </div>
        </aside>

        {/* Tab Content Pane (padding = 28dp) */}
        <div className="flex-1 min-w-0 flex flex-col p-7">
          {/* Header */}
          <div className="flex items-center justify-between">
            <h2 className="text-2xl leading-8 font-bold">{currentTab.title}</h2>
            <button
              type="button"
              onClick={() => openDialog(null)}
              className="w-10 h-10 rounded-full flex items-center justify-center hover:bg-[var(--surface-variant)] cursor-pointer"
              style={{ color: "var(--text-primary)" }}
              title="Close"
              aria-label="Close settings"
            >
              <Icon name="close" size={22} />
            </button>
          </div>

          <div className="my-4 h-px" style={{ backgroundColor: "var(--outline)" }} />

          <div className="flex-1 min-h-0 overflow-y-auto space-y-5 pr-1">
            {activeTab === "audio" && (
              <>
                {/* 10-Band Equalizer Card */}
                <div
                  className="p-[18px] rounded-2xl flex items-center justify-between gap-4"
                  style={{ backgroundColor: "var(--surface-2)" }}
                >
                  <div>
                    <div className="text-base font-bold">10-Band Equalizer</div>
                    <div className="text-xs mt-0.5" style={{ color: "var(--text-secondary)" }}>
                      Active preset: {settings.equalizer.presetName || "Custom"}
                    </div>
                  </div>
                  <div className="flex items-center gap-3">
                    <button
                      type="button"
                      onClick={() => openDialog("equalizer")}
                      className="px-4 py-2 rounded-full text-xs font-semibold cursor-pointer"
                      style={{
                        backgroundColor: "var(--accent-container)",
                        color: "var(--on-accent-container)",
                      }}
                    >
                      Open Equalizer
                    </button>
                    <M3Switch
                      checked={settings.equalizer.enabled}
                      onChange={(enabled) =>
                        void updateEqualizer({ ...settings.equalizer, enabled })
                      }
                      label="Toggle 10-Band Equalizer"
                    />
                  </div>
                </div>

                {/* Crossfade & Gapless Transitions */}
                <div
                  className="p-[18px] rounded-2xl space-y-2.5"
                  style={{ backgroundColor: "var(--surface-2)" }}
                >
                  <div className="flex items-center justify-between">
                    <span className="text-base font-bold">
                      Crossfade &amp; Gapless Transitions
                    </span>
                    <span className="text-base font-bold" style={{ color: "var(--accent)" }}>
                      {isGapless ? "Gapless Lookahead" : `${crossfadeSeconds.toFixed(1)}s`}
                    </span>
                  </div>

                  <input
                    type="range"
                    min={0}
                    max={12}
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
                      background: `linear-gradient(90deg, var(--accent) ${(crossfadeSeconds / 12) * 100}%, var(--surface-3) ${(crossfadeSeconds / 12) * 100}%)`,
                      height: 4,
                      borderRadius: 999,
                    }}
                  />

                  <div className="flex items-center gap-2 pt-1">
                    <span className="text-xs" style={{ color: "var(--text-secondary)" }}>
                      Transition curve:
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
                          className="h-8 px-3 rounded-lg text-sm font-medium cursor-pointer"
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
              </>
            )}

            {activeTab === "library" && (
              <>
                <div
                  className="p-[18px] rounded-2xl flex items-center justify-between gap-4"
                  style={{
                    backgroundColor: "color-mix(in srgb, var(--accent-container) 40%, transparent)",
                  }}
                >
                  <div>
                    <div className="text-base font-bold">Indexed Audio Files</div>
                    <div className="text-xs mt-0.5" style={{ color: "var(--text-secondary)" }}>
                      {tracksCount} total songs in library
                    </div>
                  </div>
                  <button
                    type="button"
                    onClick={() => openDialog("duplicates")}
                    className="px-4 py-2 rounded-full text-xs font-semibold flex items-center gap-2 cursor-pointer shrink-0"
                    style={{
                      backgroundColor: "var(--accent-container)",
                      color: "var(--on-accent-container)",
                    }}
                  >
                    <Icon name="search" size={18} />
                    <span>Find Duplicates</span>
                  </button>
                </div>

                <div className="flex flex-wrap items-center justify-between gap-3">
                  <span className="text-base font-bold">Monitored Music Folders</span>
                  <div className="flex items-center gap-2">
                    <button
                      type="button"
                      onClick={() => void scanFolders()}
                      className="px-3.5 py-2 rounded-full text-xs font-medium cursor-pointer"
                      style={{
                        backgroundColor: "var(--surface-2)",
                        color: "var(--text-primary)",
                      }}
                    >
                      Rescan Now
                    </button>
                    <button
                      type="button"
                      onClick={() => void handleBrowseFolder()}
                      className="px-4 py-2 rounded-full text-xs font-semibold flex items-center gap-1.5 cursor-pointer"
                      style={{
                        backgroundColor: "var(--accent)",
                        color: "var(--on-accent)",
                      }}
                    >
                      <Icon name="add" size={18} />
                      <span>Add Folder</span>
                    </button>
                  </div>
                </div>

                {!isTauriRuntime() && (
                  <div className="flex items-center gap-2">
                    <input
                      type="text"
                      value={folderInput}
                      onChange={(e) => setFolderInput(e.target.value)}
                      placeholder="/path/to/music..."
                      className="flex-1 px-4 py-2 rounded-full text-xs outline-none"
                      style={{
                        backgroundColor: "var(--surface-2)",
                        border: "1px solid var(--outline)",
                      }}
                    />
                  </div>
                )}

                {settings.folders.length === 0 ? (
                  <p className="text-sm" style={{ color: "var(--text-secondary)" }}>
                    No folders configured. Click &apos;Add Folder&apos; to add your music.
                  </p>
                ) : (
                  <div className="space-y-2">
                    {settings.folders.map((folder) => (
                      <div
                        key={folder}
                        className="px-3.5 py-3 rounded-xl flex items-center justify-between gap-3"
                        style={{ backgroundColor: "var(--surface-2)" }}
                      >
                        <span className="text-sm font-medium truncate">{folder}</span>
                        <button
                          type="button"
                          onClick={() => void removeFolder(folder)}
                          className="w-8 h-8 rounded-full flex items-center justify-center hover:bg-[var(--surface-variant)] cursor-pointer shrink-0"
                          style={{ color: "var(--danger)" }}
                          title="Remove folder"
                          aria-label={`Remove ${folder}`}
                        >
                          <Icon name="remove" size={20} />
                        </button>
                      </div>
                    ))}
                  </div>
                )}
              </>
            )}

            {activeTab === "lyrics" && (
              <>
                <div
                  className="p-[18px] rounded-2xl space-y-3"
                  style={{ backgroundColor: "var(--surface-2)" }}
                >
                  <div className="flex items-center justify-between">
                    <span className="text-base font-bold">Spicy Lyrics API Key</span>
                    <span
                      className="text-xs font-semibold"
                      style={{
                        color: settings.spicyLyricsApiKey.trim()
                          ? "var(--accent)"
                          : "var(--text-secondary)",
                      }}
                    >
                      {settings.spicyLyricsApiKey.trim()
                        ? "Configured"
                        : "Not configured"}
                    </span>
                  </div>

                  <p className="text-xs" style={{ color: "var(--text-secondary)" }}>
                    Enter your Spicy Lyrics API key (sl_pk_... or sl_sk_...) to fetch
                    word-by-word syllable-synced lyrics.
                  </p>

                  <div className="text-xs flex items-center gap-1.5">
                    <span style={{ color: "var(--text-secondary)" }}>Get a key at</span>
                    <a
                      href="https://developers.spicylyrics.org/"
                      target="_blank"
                      rel="noreferrer"
                      className="font-medium hover:underline"
                      style={{ color: "var(--accent)" }}
                    >
                      https://developers.spicylyrics.org/
                    </a>
                  </div>

                  <div className="relative flex items-center">
                    <input
                      type={showApiKey ? "text" : "password"}
                      value={apiKeyInput}
                      onChange={(e) => {
                        setApiKeyInput(e.target.value);
                        setKeyStatusMsg(null);
                      }}
                      placeholder="sl_pk_... or sl_sk_..."
                      className="w-full pl-3.5 pr-16 py-2.5 rounded-xl text-sm outline-none"
                      style={{
                        backgroundColor: "var(--surface-0)",
                        border: "1px solid var(--outline-strong)",
                      }}
                    />
                    <button
                      type="button"
                      onClick={() => setShowApiKey((v) => !v)}
                      className="absolute right-2 px-2.5 py-1 rounded-lg text-xs font-medium cursor-pointer hover:bg-[var(--surface-variant)]"
                      style={{ color: "var(--accent)" }}
                    >
                      {showApiKey ? "Hide" : "Show"}
                    </button>
                  </div>

                  <div className="flex items-center gap-2.5">
                    <button
                      type="button"
                      disabled={apiKeyInput.trim() === settings.spicyLyricsApiKey}
                      onClick={() => {
                        const trimmed = apiKeyInput.trim();
                        setApiKeyInput(trimmed);
                        void saveSettings({ ...settings, spicyLyricsApiKey: trimmed });
                        setKeyStatusMsg(trimmed ? "API key saved." : "API key cleared.");
                      }}
                      className="px-4 py-2 rounded-full text-xs font-semibold cursor-pointer disabled:opacity-45"
                      style={{
                        backgroundColor: "var(--accent)",
                        color: "var(--on-accent)",
                      }}
                    >
                      Save Key
                    </button>

                    {(settings.spicyLyricsApiKey || apiKeyInput) && (
                      <button
                        type="button"
                        onClick={() => {
                          setApiKeyInput("");
                          void saveSettings({ ...settings, spicyLyricsApiKey: "" });
                          setKeyStatusMsg("API key cleared.");
                        }}
                        className="px-4 py-2 rounded-full text-xs font-medium cursor-pointer"
                        style={{ border: "1px solid var(--outline-strong)" }}
                      >
                        Clear
                      </button>
                    )}

                    {keyStatusMsg && (
                      <span className="text-xs" style={{ color: "var(--accent)" }}>
                        {keyStatusMsg}
                      </span>
                    )}
                  </div>
                </div>

                <div
                  className="p-[18px] rounded-2xl flex items-center justify-between gap-4"
                  style={{ backgroundColor: "var(--surface-2)" }}
                >
                  <div className="min-w-0 flex-1">
                    <div className="text-base font-bold">Show Romanized Lyrics</div>
                    <div className="text-xs mt-0.5" style={{ color: "var(--text-secondary)" }}>
                      Display romanized pronunciation below non-Latin lyrics when available
                    </div>
                  </div>
                  <M3Switch
                    checked={showRomanized}
                    onChange={(next) => {
                      setShowRomanized(next);
                      setShowRomanizedPref(next);
                    }}
                    label="Show Romanized Lyrics"
                  />
                </div>

                <div
                  className="p-[18px] rounded-2xl flex items-center justify-between gap-4"
                  style={{ backgroundColor: "var(--surface-2)" }}
                >
                  <div className="min-w-0 flex-1">
                    <div className="text-base font-bold">Lyrics Cache</div>
                    <div
                      className="text-xs mt-0.5"
                      style={{
                        color: cacheStatusMsg ? "var(--accent)" : "var(--text-secondary)",
                      }}
                    >
                      {cacheStatusMsg ??
                        "Clear cached Spicy Lyrics data stored on disk and in memory"}
                    </div>
                  </div>
                  <button
                    type="button"
                    onClick={() => {
                      if (currentTrackId) {
                        void refreshLyrics(currentTrackId);
                      }
                      setCacheStatusMsg("Lyrics cache cleared.");
                    }}
                    className="px-4 py-2 rounded-full text-xs font-semibold cursor-pointer shrink-0"
                    style={{
                      backgroundColor: "var(--accent-container)",
                      color: "var(--on-accent-container)",
                    }}
                  >
                    Clear Lyrics Cache
                  </button>
                </div>
              </>
            )}

            {activeTab === "appearance" && (
              <>
                <div className="text-base font-bold">Curated Color Themes</div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  {THEME_PRESETS.map((preset) => {
                    const isSelected = selectedPresetId === preset.id;
                    return (
                      <button
                        key={preset.id}
                        type="button"
                        onClick={() => {
                          setSelectedPresetId(preset.id);
                          setStoredThemePresetId(preset.id);
                          void saveSettings({
                            ...settings,
                            theme: preset.mode,
                            adaptiveAccent: preset.adaptive,
                          });
                        }}
                        className="p-3.5 rounded-2xl flex items-center gap-3 text-left cursor-pointer transition-colors"
                        style={{
                          backgroundColor: isSelected
                            ? "color-mix(in srgb, var(--surface-3) 70%, var(--surface-2))"
                            : "var(--surface-2)",
                          border: isSelected
                            ? "2px solid var(--accent)"
                            : "1px solid var(--outline-strong)",
                        }}
                      >
                        <div
                          className="w-[42px] h-[42px] rounded-full shrink-0 flex items-center justify-center border"
                          style={{
                            backgroundColor: preset.previewBackground,
                            borderColor: "var(--outline-strong)",
                          }}
                        >
                          <span
                            className="w-[18px] h-[18px] rounded-full"
                            style={{ backgroundColor: preset.previewAccent }}
                          />
                        </div>
                        <div className="min-w-0 flex-1">
                          <div
                            className="text-sm truncate"
                            style={{ fontWeight: isSelected ? 700 : 600 }}
                          >
                            {preset.displayName}
                          </div>
                          <div
                            className="text-xs leading-snug mt-0.5 line-clamp-2"
                            style={{ color: "var(--text-secondary)" }}
                          >
                            {preset.description}
                          </div>
                        </div>
                      </button>
                    );
                  })}
                </div>

                <div className="h-px" style={{ backgroundColor: "var(--outline)" }} />

                <div className="flex items-center justify-between gap-4">
                  <div>
                    <div className="text-sm font-medium">Follow System Dark/Light Mode</div>
                    <div className="text-xs mt-0.5" style={{ color: "var(--text-secondary)" }}>
                      Adapts automatically to macOS/Windows system theme
                    </div>
                  </div>
                  <M3Switch
                    checked={settings.theme === "system"}
                    onChange={(follow) => {
                      if (follow) {
                        setSelectedPresetId("dynamic");
                        setStoredThemePresetId("dynamic");
                      }
                      void setTheme(follow ? "system" : "dark");
                    }}
                    label="Follow System Dark/Light Mode"
                  />
                </div>

                <div className="flex items-center justify-between gap-4">
                  <div>
                    <div className="text-sm font-medium">Dynamic Album Colors</div>
                    <div className="text-xs mt-0.5" style={{ color: "var(--text-secondary)" }}>
                      Tint surfaces and accents from the playing track&apos;s artwork
                    </div>
                  </div>
                  <M3Switch
                    checked={settings.adaptiveAccent}
                    onChange={(enabled) => {
                      if (enabled) {
                        setSelectedPresetId("dynamic");
                        setStoredThemePresetId("dynamic");
                      }
                      void setAdaptiveAccent(enabled);
                    }}
                    label="Dynamic Album Colors"
                  />
                </div>

                <div className="flex items-center justify-between gap-4">
                  <div>
                    <div className="text-sm font-medium">Startup Intro Animation</div>
                    <div className="text-xs mt-0.5" style={{ color: "var(--text-secondary)" }}>
                      Fluid greeting animation when the app launches
                    </div>
                  </div>
                  <M3Switch
                    checked={introEnabled}
                    onChange={(enabled) => {
                      setIntroEnabled(enabled);
                      setStartupIntroEnabled(enabled);
                    }}
                    label="Startup Intro Animation"
                  />
                </div>

                <div className="h-px" style={{ backgroundColor: "var(--outline)" }} />
                <div className="text-base font-bold">System Tray &amp; Window</div>

                <div className="flex items-center justify-between gap-4">
                  <div className="min-w-0 flex-1">
                    <div className="text-sm font-medium">Minimize to System Tray</div>
                    <div className="text-xs mt-0.5" style={{ color: "var(--text-secondary)" }}>
                      Hides the window from dock/taskbar into the system tray when minimized
                    </div>
                  </div>
                  <M3Switch
                    checked={settings.minimizeToTray}
                    onChange={(minimizeToTray) =>
                      void saveSettings({ ...settings, minimizeToTray })
                    }
                    label="Minimize to System Tray"
                  />
                </div>

                <div className="flex items-center justify-between gap-4">
                  <div className="min-w-0 flex-1">
                    <div className="text-sm font-medium">Close to Mini Player</div>
                    <div className="text-xs mt-0.5" style={{ color: "var(--text-secondary)" }}>
                      Keeps the music player running in the floating mini player on close
                    </div>
                  </div>
                  <M3Switch
                    checked={settings.showMiniPlayerOnClose}
                    onChange={(showMiniPlayerOnClose) =>
                      void saveSettings({ ...settings, showMiniPlayerOnClose })
                    }
                    label="Close to Mini Player"
                  />
                </div>
              </>
            )}

            {activeTab === "keybindings" && <KeybindingsTab />}

            {activeTab === "stats" && (
              <>
                <div className="grid grid-cols-2 gap-3">
                  <div
                    className="p-4 rounded-2xl"
                    style={{
                      backgroundColor: "color-mix(in srgb, var(--accent-container) 40%, transparent)",
                    }}
                  >
                    <div className="text-xs font-medium" style={{ color: "var(--text-secondary)" }}>
                      Total Plays
                    </div>
                    <div
                      className="text-[28px] leading-9 font-bold mt-0.5 tabular-nums"
                      style={{ color: "var(--accent)" }}
                    >
                      {stats?.totalPlays ?? 0}
                    </div>
                  </div>

                  <div
                    className="p-4 rounded-2xl"
                    style={{
                      backgroundColor: "color-mix(in srgb, var(--accent-container) 40%, transparent)",
                    }}
                  >
                    <div className="text-xs font-medium" style={{ color: "var(--text-secondary)" }}>
                      Time Listened
                    </div>
                    <div
                      className="text-[28px] leading-9 font-bold mt-0.5 tabular-nums"
                      style={{ color: "var(--accent)" }}
                    >
                      {((stats?.totalPlayTimeMs ?? 0) / 3_600_000).toFixed(1)} hrs
                    </div>
                  </div>
                </div>

                <div className="flex items-center justify-between pt-1">
                  <span className="text-base font-bold">Top Played Tracks</span>
                  <button
                    type="button"
                    onClick={() => openDialog("stats")}
                    className="text-xs font-semibold cursor-pointer hover:underline"
                    style={{ color: "var(--accent)" }}
                  >
                    Detailed Breakdown
                  </button>
                </div>

                {!stats || stats.topTracks.length === 0 ? (
                  <p className="text-sm" style={{ color: "var(--text-secondary)" }}>
                    No listening stats recorded yet. Play some tracks to see analytics!
                  </p>
                ) : (
                  <div className="space-y-2">
                    {stats.topTracks.slice(0, 5).map((item, idx) => (
                      <div
                        key={item.track.id}
                        className="p-3 rounded-xl flex items-center justify-between gap-3"
                        style={{ backgroundColor: "var(--surface-2)" }}
                      >
                        <span className="text-sm font-semibold truncate">
                          #{idx + 1} &nbsp;{item.track.title || "Unknown"}
                        </span>
                        <span
                          className="text-xs font-medium tabular-nums shrink-0"
                          style={{ color: "var(--accent)" }}
                        >
                          {item.playCount} plays
                        </span>
                      </div>
                    ))}
                  </div>
                )}

                <div className="text-base font-bold pt-1">Top Artists</div>
                <div className="space-y-2">
                  {(stats?.topArtists ?? []).slice(0, 5).map((item, idx) => (
                    <div
                      key={item.artist}
                      className="p-3 rounded-xl flex items-center justify-between gap-3"
                      style={{ backgroundColor: "var(--surface-2)" }}
                    >
                      <span className="text-sm font-semibold truncate">
                        #{idx + 1} &nbsp;{item.artist}
                      </span>
                      <span
                        className="text-xs font-medium tabular-nums shrink-0"
                        style={{ color: "var(--accent)" }}
                      >
                        {item.playCount} plays
                      </span>
                    </div>
                  ))}
                </div>
              </>
            )}

            {activeTab === "about" && (
              <div className="flex flex-col items-center text-center gap-4 pt-2">
                <img
                  src={appIconUrl}
                  alt="Umimuko Music"
                  className="w-16 h-16 object-contain app-brand-icon"
                  draggable={false}
                />
                <div className="space-y-1">
                  <h3 className="text-[28px] leading-9 font-bold">Umimuko Music</h3>
                  <p className="text-sm" style={{ color: "var(--text-secondary)" }}>
                    Version 0.26.1
                  </p>
                </div>

                <p className="text-sm max-w-md" style={{ color: "var(--text-secondary)" }}>
                  An ultra-fast, local-first audio player built with Rust, Tauri v2,
                  React, Symphonia, and SpicyLyrics.
                </p>

                <div className="w-full h-px my-2" style={{ backgroundColor: "var(--outline)" }} />

                <div className="space-y-1">
                  <div className="text-sm font-bold">Open Source Technologies</div>
                  <div className="text-xs" style={{ color: "var(--text-secondary)" }}>
                    Tauri v2 · Symphonia · Lofty · SQLite · React · Tailwind CSS
                  </div>
                </div>

                <a
                  href="https://github.com/real-Phraser/Umimuko-Music"
                  target="_blank"
                  rel="noreferrer"
                  className="inline-flex items-center gap-2 text-sm font-medium hover:underline"
                  style={{ color: "var(--accent)" }}
                >
                  <img
                    src={githubIconUrl}
                    alt="GitHub"
                    className="w-[18px] h-[18px] object-contain app-brand-icon"
                    draggable={false}
                  />
                  <span>github.com/real-Phraser/Umimuko-Music</span>
                </a>

                <p className="text-xs pt-2" style={{ color: "var(--text-secondary)" }}>
                  Crafted with care by Phraser &amp; Contributors
                </p>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
