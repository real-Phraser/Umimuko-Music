import { create } from "zustand";
import { api, BUILT_IN_EQ_PRESETS, DEFAULT_APP_SETTINGS } from "../api/tauri";
import type {
  AppSettings,
  CrossfadeConfig,
  EqualizerConfig,
  EqualizerPreset,
  ThemeMode,
} from "../types/ipc";

interface SettingsStore {
  settings: AppSettings;
  eqPresets: EqualizerPreset[];
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
}

export const useSettingsStore = create<SettingsStore>((set, get) => ({
  settings: DEFAULT_APP_SETTINGS,
  eqPresets: BUILT_IN_EQ_PRESETS,
  loaded: false,

  init: async () => {
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
}));
