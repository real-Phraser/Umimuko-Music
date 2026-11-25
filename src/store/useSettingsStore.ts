import { invoke } from "@tauri-apps/api/core";
import { create } from "zustand";
import { api, BUILT_IN_EQ_PRESETS, DEFAULT_APP_SETTINGS, isTauriRuntime } from "../api/tauri";
import type {
  AppSettings,
  CrossfadeConfig,
  EqualizerConfig,
  EqualizerPreset,
  KeyCombination,
  ShortcutAction,
  ShortcutBindings,
  ThemeMode,
} from "../types/ipc";

const KEYBINDINGS_STORAGE_KEY = "umimuko_keybindings_v1";
const PLAYBACK_RATE_STORAGE_KEY = "umimuko_playback_rate";

function clampPlaybackRate(rate: number): number {
  if (!Number.isFinite(rate)) return 1.0;
  return Math.min(2.0, Math.max(0.5, Math.round(rate * 100) / 100));
}

function loadPlaybackRate(): number {
  try {
    const raw = localStorage.getItem(PLAYBACK_RATE_STORAGE_KEY);
    if (!raw) return 1.0;
    return clampPlaybackRate(Number(raw));
  } catch {
    return 1.0;
  }
}

export const SHORTCUT_ACTIONS: ShortcutAction[] = [
  "play_pause",
  "next_track",
  "prev_track",
  "seek_forward",
  "seek_backward",
  "volume_up",
  "volume_down",
  "mute",
  "toggle_fullscreen",
  "toggle_full_player",
  "toggle_lyrics",
];

export type ShortcutCategory = "Playback" | "Audio" | "View";

export const SHORTCUT_ACTION_META: Record<
  ShortcutAction,
  { title: string; category: ShortcutCategory }
> = {
  play_pause: { title: "Play / Pause", category: "Playback" },
  next_track: { title: "Next Track", category: "Playback" },
  prev_track: { title: "Previous Track", category: "Playback" },
  seek_forward: { title: "Seek Forward (+5s)", category: "Playback" },
  seek_backward: { title: "Seek Backward (-5s)", category: "Playback" },
  volume_up: { title: "Volume Up (+5%)", category: "Audio" },
  volume_down: { title: "Volume Down (-5%)", category: "Audio" },
  mute: { title: "Mute / Unmute", category: "Audio" },
  toggle_fullscreen: { title: "Toggle Full Screen Window", category: "View" },
  toggle_full_player: { title: "Toggle Fullscreen Player", category: "View" },
  toggle_lyrics: { title: "Toggle Synced Lyrics", category: "View" },
};

export function isMacPlatform(): boolean {
  if (typeof navigator === "undefined") return false;
  return (
    /Mac|iPod|iPhone|iPad/i.test(navigator.platform) ||
    /Mac OS X/i.test(navigator.userAgent)
  );
}

export function getDefaultShortcutBindings(
  isMac: boolean = isMacPlatform()
): ShortcutBindings {
  if (isMac) {
    return {
      play_pause: { code: "Space" },
      next_track: { code: "ArrowRight", meta: true },
      prev_track: { code: "ArrowLeft", meta: true },
      seek_forward: { code: "ArrowRight", shift: true },
      seek_backward: { code: "ArrowLeft", shift: true },
      volume_up: { code: "ArrowUp", meta: true },
      volume_down: { code: "ArrowDown", meta: true },
      mute: { code: "KeyM" },
      toggle_fullscreen: { code: "KeyF", ctrl: true, meta: true },
      toggle_full_player: { code: "KeyF" },
      toggle_lyrics: { code: "KeyL" },
    };
  }
  return {
    play_pause: { code: "Space" },
    next_track: { code: "ArrowRight", ctrl: true },
    prev_track: { code: "ArrowLeft", ctrl: true },
    seek_forward: { code: "ArrowRight", shift: true },
    seek_backward: { code: "ArrowLeft", shift: true },
    volume_up: { code: "ArrowUp", ctrl: true },
    volume_down: { code: "ArrowDown", ctrl: true },
    mute: { code: "KeyM" },
    toggle_fullscreen: { code: "F11" },
    toggle_full_player: { code: "KeyF" },
    toggle_lyrics: { code: "KeyL" },
  };
}

export function matchesKeyCombination(
  e: KeyboardEvent,
  combo: KeyCombination
): boolean {
  if (e.code !== combo.code) return false;
  if (e.ctrlKey !== Boolean(combo.ctrl)) return false;
  if (e.metaKey !== Boolean(combo.meta)) return false;
  if (e.altKey !== Boolean(combo.alt)) return false;
  if (e.shiftKey !== Boolean(combo.shift)) return false;
  return true;
}

export function isSameKeyCombination(
  a: KeyCombination,
  b: KeyCombination
): boolean {
  return (
    a.code === b.code &&
    Boolean(a.ctrl) === Boolean(b.ctrl) &&
    Boolean(a.meta) === Boolean(b.meta) &&
    Boolean(a.alt) === Boolean(b.alt) &&
    Boolean(a.shift) === Boolean(b.shift)
  );
}

export function findShortcutConflict(
  bindings: ShortcutBindings,
  combo: KeyCombination,
  excludingAction?: ShortcutAction
): ShortcutAction | null {
  for (const action of SHORTCUT_ACTIONS) {
    if (action === excludingAction) continue;
    const existing = bindings[action];
    if (existing && isSameKeyCombination(existing, combo)) {
      return action;
    }
  }
  return null;
}

function isValidKeyCombination(val: unknown): val is KeyCombination {
  return (
    typeof val === "object" &&
    val !== null &&
    typeof (val as KeyCombination).code === "string" &&
    (val as KeyCombination).code.length > 0
  );
}

function loadShortcutBindings(): ShortcutBindings {
  const defaults = getDefaultShortcutBindings();
  try {
    const raw = localStorage.getItem(KEYBINDINGS_STORAGE_KEY);
    if (!raw) {
      localStorage.setItem(KEYBINDINGS_STORAGE_KEY, JSON.stringify(defaults));
      return defaults;
    }
    const parsed = JSON.parse(raw) as Record<string, unknown>;
    if (!parsed || typeof parsed !== "object") {
      localStorage.setItem(KEYBINDINGS_STORAGE_KEY, JSON.stringify(defaults));
      return defaults;
    }
    // Migrate/overwrite if any of the 11 ShortcutAction keys is missing or invalid
    const hasAllKeys = SHORTCUT_ACTIONS.every(
      (action) =>
        Object.prototype.hasOwnProperty.call(parsed, action) &&
        (parsed[action] === null || isValidKeyCombination(parsed[action]))
    );
    if (!hasAllKeys) {
      localStorage.setItem(KEYBINDINGS_STORAGE_KEY, JSON.stringify(defaults));
      return defaults;
    }
    const result = {} as ShortcutBindings;
    for (const action of SHORTCUT_ACTIONS) {
      const val = parsed[action];
      result[action] = val === null ? null : {
        code: (val as KeyCombination).code,
        ctrl: Boolean((val as KeyCombination).ctrl),
        meta: Boolean((val as KeyCombination).meta),
        alt: Boolean((val as KeyCombination).alt),
        shift: Boolean((val as KeyCombination).shift),
      };
    }
    return result;
  } catch {
    return defaults;
  }
}

function persistShortcutBindings(bindings: ShortcutBindings): void {
  try {
    localStorage.setItem(KEYBINDINGS_STORAGE_KEY, JSON.stringify(bindings));
  } catch {
    // Ignore storage errors
  }
}

interface SettingsStore {
  settings: AppSettings;
  shortcutBindings: ShortcutBindings;
  eqPresets: EqualizerPreset[];
  playbackRate: number;
  loaded: boolean;

  init: () => Promise<void>;
  saveSettings: (next: AppSettings) => Promise<void>;
  setTheme: (theme: ThemeMode) => Promise<void>;
  setAdaptiveAccent: (adaptiveAccent: boolean) => Promise<void>;
  addFolder: (path: string) => Promise<void>;
  removeFolder: (path: string) => Promise<void>;
  updateEqualizer: (config: EqualizerConfig) => Promise<void>;
  applyEqPreset: (preset: EqualizerPreset) => Promise<void>;
  saveEqPreset: (name: string) => Promise<void>;
  updateCrossfade: (config: CrossfadeConfig) => Promise<void>;
  setPlaybackRate: (rate: number) => Promise<void>;
  setShortcutBinding: (action: ShortcutAction, combo: KeyCombination) => ShortcutAction | null;
  resetShortcutAction: (action: ShortcutAction) => void;
  resetAllShortcuts: () => void;
}

export const useSettingsStore = create<SettingsStore>((set, get) => ({
  settings: DEFAULT_APP_SETTINGS,
  shortcutBindings: loadShortcutBindings(),
  eqPresets: BUILT_IN_EQ_PRESETS,
  playbackRate: loadPlaybackRate(),
  loaded: false,

  init: async () => {
    const shortcutBindings = loadShortcutBindings();
    set({ shortcutBindings });
    try {
      const [settings, eqPresets] = await Promise.all([
        api.getSettings(),
        api.getEqPresets(),
      ]);
      set({ settings, eqPresets, loaded: true });
    } catch {
      set({ loaded: true });
    }
  },

  saveSettings: async (next) => {
    set({ settings: next });
    const saved = await api.saveSettings(next);
    set({ settings: saved });
  },

  setTheme: async (theme) => {
    const next = { ...get().settings, theme };
    await get().saveSettings(next);
  },

  setAdaptiveAccent: async (adaptiveAccent) => {
    const next = { ...get().settings, adaptiveAccent };
    await get().saveSettings(next);
  },

  addFolder: async (path) => {
    const folders = await api.addFolder(path);
    set((s) => ({ settings: { ...s.settings, folders } }));
  },

  removeFolder: async (path) => {
    const folders = await api.removeFolder(path);
    set((s) => ({ settings: { ...s.settings, folders } }));
  },

  updateEqualizer: async (config) => {
    const next = { ...get().settings, equalizer: config };
    set({ settings: next });
    await api.setEqualizerConfig(config);
    await api.saveSettings(next);
  },

  applyEqPreset: async (preset) => {
    const config: EqualizerConfig = {
      enabled: true,
      preampDb: preset.preampDb,
      bands: [...preset.bands],
      presetName: preset.name,
    };
    await get().updateEqualizer(config);
  },

  saveEqPreset: async (name) => {
    const eq = get().settings.equalizer;
    const created = await api.saveEqPreset({
      name,
      preampDb: eq.preampDb,
      bands: [...eq.bands],
    });
    set((s) => ({
      eqPresets: [...s.eqPresets, created],
      settings: {
        ...s.settings,
        equalizer: { ...s.settings.equalizer, presetName: created.name },
      },
    }));
  },

  updateCrossfade: async (config) => {
    const next = { ...get().settings, crossfade: config };
    set({ settings: next });
    await api.setCrossfadeConfig(config);
    await api.saveSettings(next);
  },

  setPlaybackRate: async (rate) => {
    const clamped = clampPlaybackRate(rate);
    set({ playbackRate: clamped });
    try {
      localStorage.setItem(PLAYBACK_RATE_STORAGE_KEY, String(clamped));
    } catch {
      // Ignore storage errors
    }
    if (isTauriRuntime()) {
      try {
        await invoke("set_playback_rate", { rate: clamped });
      } catch {
        // Backend command may not be exposed yet
      }
    }
  },

  setShortcutBinding: (action, combo) => {
    const current = get().shortcutBindings;
    const conflict = findShortcutConflict(current, combo, action);
    const next: ShortcutBindings = { ...current };
    if (conflict) {
      next[conflict] = null;
    }
    next[action] = {
      code: combo.code,
      ctrl: Boolean(combo.ctrl),
      meta: Boolean(combo.meta),
      alt: Boolean(combo.alt),
      shift: Boolean(combo.shift),
    };
    persistShortcutBindings(next);
    set({ shortcutBindings: next });
    return conflict;
  },

  resetShortcutAction: (action) => {
    const defaults = getDefaultShortcutBindings();
    const next: ShortcutBindings = {
      ...get().shortcutBindings,
      [action]: defaults[action],
    };
    persistShortcutBindings(next);
    set({ shortcutBindings: next });
  },

  resetAllShortcuts: () => {
    const defaults = getDefaultShortcutBindings();
    persistShortcutBindings(defaults);
    set({ shortcutBindings: defaults });
  },
}));
