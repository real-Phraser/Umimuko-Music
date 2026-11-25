import { useEffect, useState, type ReactNode } from "react";
import { usePlayerStore } from "../store/usePlayerStore";
import { useSettingsStore } from "../store/useSettingsStore";

type Rgb = [number, number, number];

const DEFAULT_DARK_SEED = "#9CCAFF";
const DEFAULT_LIGHT_SEED = "#2166C2";

export const THEME_PRESET_STORAGE_KEY = "umimuko_theme_preset";
export const THEME_PRESET_EVENT = "umimuko:theme-preset";

export function getStoredThemePresetId(): string {
  try {
    return localStorage.getItem(THEME_PRESET_STORAGE_KEY) || "dynamic";
  } catch {
    return "dynamic";
  }
}

export function setStoredThemePresetId(presetId: string) {
  try {
    localStorage.setItem(THEME_PRESET_STORAGE_KEY, presetId);
  } catch {
    // ignore
  }
  if (typeof window !== "undefined") {
    window.dispatchEvent(new CustomEvent(THEME_PRESET_EVENT, { detail: presetId }));
  }
}

export interface ThemePresetDefinition {
  id: string;
  displayName: string;
  description: string;
  previewAccent: string;
  previewBackground: string;
  mode: "dark" | "light";
  adaptive: boolean;
}

interface FixedPresetPalette {
  mode: "dark" | "light";
  primary: string;
  onPrimary: string;
  container: string;
  onContainer: string;
  background: string;
  surface: string;
  variant: string;
  onSurface: string;
  onSurfaceVariant: string;
  outline: string;
  outlineVariant: string;
}

const FIXED_PRESET_PALETTES: Record<string, FixedPresetPalette> = {
  oled_black: {
    mode: "dark",
    primary: "#4DA3FF",
    onPrimary: "#000000",
    container: "#162B44",
    onContainer: "#D6E4FF",
    background: "#000000",
    surface: "#0A0A0A",
    variant: "#161616",
    onSurface: "#E5E5E5",
    onSurfaceVariant: "#AAAAAA",
    outline: "#333333",
    outlineVariant: "#222222",
  },
  nord: {
    mode: "dark",
    primary: "#88C0D0",
    onPrimary: "#1D262F",
    container: "#434C5E",
    onContainer: "#ECEFF4",
    background: "#242933",
    surface: "#2E3440",
    variant: "#3B4252",
    onSurface: "#ECEFF4",
    onSurfaceVariant: "#D8DEE9",
    outline: "#4C566A",
    outlineVariant: "#3B4252",
  },
  dracula: {
    mode: "dark",
    primary: "#BD93F9",
    onPrimary: "#191A21",
    container: "#49366D",
    onContainer: "#F8F8F2",
    background: "#1E1F29",
    surface: "#282A36",
    variant: "#44475A",
    onSurface: "#F8F8F2",
    onSurfaceVariant: "#BCC5E2",
    outline: "#6272A4",
    outlineVariant: "#44475A",
  },
  rose_pine: {
    mode: "dark",
    primary: "#EBBCBA",
    onPrimary: "#191724",
    container: "#463A47",
    onContainer: "#E0DEF4",
    background: "#191724",
    surface: "#1F1D2E",
    variant: "#26233A",
    onSurface: "#E0DEF4",
    onSurfaceVariant: "#908CAA",
    outline: "#524F67",
    outlineVariant: "#393552",
  },
  synthwave: {
    mode: "dark",
    primary: "#FF71CE",
    onPrimary: "#000000",
    container: "#5A1C49",
    onContainer: "#FFD3F2",
    background: "#140B24",
    surface: "#1F1138",
    variant: "#2E1A52",
    onSurface: "#FEE7F9",
    onSurfaceVariant: "#B9A6D9",
    outline: "#6B3E9C",
    outlineVariant: "#46276B",
  },
  catppuccin: {
    mode: "dark",
    primary: "#CBA6F7",
    onPrimary: "#11111B",
    container: "#473663",
    onContainer: "#CDD6F4",
    background: "#181825",
    surface: "#1E1E2E",
    variant: "#313244",
    onSurface: "#CDD6F4",
    onSurfaceVariant: "#A6ADC8",
    outline: "#585B70",
    outlineVariant: "#45475A",
  },
  light_minimal: {
    mode: "light",
    primary: "#1A73E8",
    onPrimary: "#FFFFFF",
    container: "#D3E3FD",
    onContainer: "#041E49",
    background: "#F8F9FA",
    surface: "#FFFFFF",
    variant: "#ECEFF1",
    onSurface: "#202124",
    onSurfaceVariant: "#5F6368",
    outline: "#BDC1C6",
    outlineVariant: "#DADCE0",
  },
};

export const THEME_PRESETS: ThemePresetDefinition[] = [
  {
    id: "dynamic",
    displayName: "Dynamic Material You",
    description: "Harmonizes dynamically with the active album artwork colors",
    previewAccent: "#64B5F6",
    previewBackground: "#1E242B",
    mode: "dark",
    adaptive: true,
  },
  {
    id: "oled_black",
    displayName: "OLED Pure Black",
    description: "Pure pitch-black background with vibrant electric blue accents",
    previewAccent: "#4DA3FF",
    previewBackground: "#000000",
    mode: "dark",
    adaptive: false,
  },
  {
    id: "nord",
    displayName: "Nordic Frost",
    description: "Arctic, north-bluish clean aesthetic inspired by the polar night",
    previewAccent: "#88C0D0",
    previewBackground: "#242933",
    mode: "dark",
    adaptive: false,
  },
  {
    id: "dracula",
    displayName: "Dracula",
    description: "Dark gothic palette with bright purple, pink, and green highlights",
    previewAccent: "#BD93F9",
    previewBackground: "#1E1F29",
    mode: "dark",
    adaptive: false,
  },
  {
    id: "rose_pine",
    displayName: "Rosé Pine",
    description: "Soho chic with soft warm rose, gold, and seafoam pastels",
    previewAccent: "#EBBCBA",
    previewBackground: "#191724",
    mode: "dark",
    adaptive: false,
  },
  {
    id: "synthwave",
    displayName: "Retro Synthwave",
    description: "80s outrun neon: radioactive magenta, deep purple, and cyber cyan",
    previewAccent: "#FF71CE",
    previewBackground: "#140B24",
    mode: "dark",
    adaptive: false,
  },
  {
    id: "catppuccin",
    displayName: "Catppuccin Mocha",
    description: "Warm soothing pastel palette with lavender and mauve accents",
    previewAccent: "#CBA6F7",
    previewBackground: "#181825",
    mode: "dark",
    adaptive: false,
  },
  {
    id: "light_minimal",
    displayName: "Clean Studio Light",
    description: "Crisp, airy daytime workspace with pure white surfaces and modern cobalt blue",
    previewAccent: "#1A73E8",
    previewBackground: "#F8F9FA",
    mode: "light",
    adaptive: false,
  },
];

function hexToRgb(hex: string): Rgb | null {
  const cleaned = hex.trim().replace(/^#/, "");
  if (cleaned.length === 3) {
    const r = parseInt(cleaned[0] + cleaned[0], 16);
    const g = parseInt(cleaned[1] + cleaned[1], 16);
    const b = parseInt(cleaned[2] + cleaned[2], 16);
    return Number.isNaN(r) || Number.isNaN(g) || Number.isNaN(b) ? null : [r, g, b];
  }
  if (cleaned.length === 6) {
    const r = parseInt(cleaned.slice(0, 2), 16);
    const g = parseInt(cleaned.slice(2, 4), 16);
    const b = parseInt(cleaned.slice(4, 6), 16);
    return Number.isNaN(r) || Number.isNaN(g) || Number.isNaN(b) ? null : [r, g, b];
  }
  return null;
}

function rgbToHsb(r: number, g: number, b: number): [number, number, number] {
  const cmax = Math.max(r, g, b);
  const cmin = Math.min(r, g, b);
  const saturation = cmax !== 0 ? (cmax - cmin) / cmax : 0;
  let hue = 0;
  if (saturation !== 0) {
    const redc = (cmax - r) / (cmax - cmin);
    const greenc = (cmax - g) / (cmax - cmin);
    const bluec = (cmax - b) / (cmax - cmin);
    if (r === cmax) hue = bluec - greenc;
    else if (g === cmax) hue = 2 + redc - bluec;
    else hue = 4 + greenc - redc;
    hue /= 6;
    if (hue < 0) hue += 1;
  }
  return [hue, saturation, cmax / 255];
}

function hsbToRgb(hue: number, saturation: number, brightness: number): Rgb {
  let r = 0;
  let g = 0;
  let b = 0;
  if (saturation === 0) {
    const v = Math.trunc(brightness * 255 + 0.5);
    return [v, v, v];
  }
  const h = (hue - Math.floor(hue)) * 6;
  const f = h - Math.floor(h);
  const p = brightness * (1 - saturation);
  const q = brightness * (1 - saturation * f);
  const t = brightness * (1 - saturation * (1 - f));
  switch (Math.floor(h)) {
    case 0:
      r = Math.trunc(brightness * 255 + 0.5);
      g = Math.trunc(t * 255 + 0.5);
      b = Math.trunc(p * 255 + 0.5);
      break;
    case 1:
      r = Math.trunc(q * 255 + 0.5);
      g = Math.trunc(brightness * 255 + 0.5);
      b = Math.trunc(p * 255 + 0.5);
      break;
    case 2:
      r = Math.trunc(p * 255 + 0.5);
      g = Math.trunc(brightness * 255 + 0.5);
      b = Math.trunc(t * 255 + 0.5);
      break;
    case 3:
      r = Math.trunc(p * 255 + 0.5);
      g = Math.trunc(q * 255 + 0.5);
      b = Math.trunc(brightness * 255 + 0.5);
      break;
    case 4:
      r = Math.trunc(t * 255 + 0.5);
      g = Math.trunc(p * 255 + 0.5);
      b = Math.trunc(brightness * 255 + 0.5);
      break;
    case 5:
      r = Math.trunc(brightness * 255 + 0.5);
      g = Math.trunc(p * 255 + 0.5);
      b = Math.trunc(q * 255 + 0.5);
      break;
  }
  return [r, g, b];
}

function rgbToHex([r, g, b]: Rgb): string {
  const toHex = (n: number) =>
    Math.max(0, Math.min(255, Math.round(n))).toString(16).padStart(2, "0");
  return `#${toHex(r)}${toHex(g)}${toHex(b)}`;
}

function lerpRgb(a: Rgb, b: Rgb, fraction: number): Rgb {
  return [
    Math.round(a[0] + (b[0] - a[0]) * fraction),
    Math.round(a[1] + (b[1] - a[1]) * fraction),
    Math.round(a[2] + (b[2] - a[2]) * fraction),
  ];
}

function relativeLuminance([r, g, b]: Rgb): number {
  const norm = [r, g, b].map((c) => {
    const s = c / 255;
    return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
  });
  return 0.2126 * norm[0] + 0.7152 * norm[1] + 0.0722 * norm[2];
}

/** Exact port of AlbumTheme.kt contrastingText */
function contrastingText(bg: Rgb): Rgb {
  const luminance = relativeLuminance(bg);
  return (luminance + 0.05) / 0.05 > 1.05 / (luminance + 0.05)
    ? [0, 0, 0]
    : [255, 255, 255];
}

/** Exact port of AlbumTheme.kt contrastRatio */
function contrastRatio(fg: Rgb, bg: Rgb): number {
  const l1 = relativeLuminance(fg);
  const l2 = relativeLuminance(bg);
  return (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05);
}

/** Exact port of AlbumTheme.kt ensureContrast */
function ensureContrast(
  fg: Rgb,
  bg: Rgb,
  minRatio = 4.5,
  fallback: Rgb = contrastingText(bg)
): Rgb {
  return contrastRatio(fg, bg) >= minRatio ? fg : fallback;
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const theme = useSettingsStore((s) => s.settings.theme);
  const adaptiveAccent = useSettingsStore((s) => s.settings.adaptiveAccent);
  const trackAccent = usePlayerStore((s) => s.playback.currentTrack?.accentColor ?? null);
  const [resolvedMode, setResolvedMode] = useState<"dark" | "light">("dark");
  const [presetId, setPresetId] = useState<string>(getStoredThemePresetId);

  useEffect(() => {
    const onPresetChange = (ev: Event) => {
      const next = (ev as CustomEvent<string>).detail;
      if (typeof next === "string") setPresetId(next);
    };
    const onStorage = (ev: StorageEvent) => {
      if (ev.key === THEME_PRESET_STORAGE_KEY && ev.newValue) {
        setPresetId(ev.newValue);
      }
    };
    window.addEventListener(THEME_PRESET_EVENT, onPresetChange);
    window.addEventListener("storage", onStorage);
    return () => {
      window.removeEventListener(THEME_PRESET_EVENT, onPresetChange);
      window.removeEventListener("storage", onStorage);
    };
  }, []);

  // Resolve light/dark mode
  useEffect(() => {
    const root = document.documentElement;
    const apply = (mode: "dark" | "light") => {
      root.dataset.theme = mode;
      setResolvedMode(mode);
    };

    const fixed = FIXED_PRESET_PALETTES[presetId];
    if (fixed && !adaptiveAccent) {
      apply(fixed.mode);
      return;
    }

    if (theme === "system") {
      const mq = window.matchMedia("(prefers-color-scheme: light)");
      apply(mq.matches ? "light" : "dark");
      const listener = (e: MediaQueryListEvent) => apply(e.matches ? "light" : "dark");
      mq.addEventListener("change", listener);
      return () => mq.removeEventListener("change", listener);
    }
    apply(theme);
  }, [theme, presetId, adaptiveAccent]);

  // Apply ThemePreset.kt fixed palette or AlbumTheme.kt dynamic tonal palette
  useEffect(() => {
    const root = document.documentElement;
    const fixed = !adaptiveAccent ? FIXED_PRESET_PALETTES[presetId] : undefined;

    let isDark: boolean;
    let primary: Rgb;
    let onPrimary: Rgb;
    let container: Rgb;
    let onContainer: Rgb;
    let background: Rgb;
    let surface: Rgb;
    let variant: Rgb;
    let surface3: Rgb;
    let surface4: Rgb;
    let onSurface: Rgb;
    let onSurfaceVariant: Rgb;
    let outline: Rgb;
    let outlineVariant: Rgb;

    if (fixed) {
      isDark = fixed.mode === "dark";
      primary = hexToRgb(fixed.primary)!;
      onPrimary = hexToRgb(fixed.onPrimary)!;
      container = hexToRgb(fixed.container)!;
      onContainer = hexToRgb(fixed.onContainer)!;
      background = hexToRgb(fixed.background)!;
      surface = hexToRgb(fixed.surface)!;
      variant = hexToRgb(fixed.variant)!;
      onSurface = hexToRgb(fixed.onSurface)!;
      onSurfaceVariant = hexToRgb(fixed.onSurfaceVariant)!;
      outline = hexToRgb(fixed.outline)!;
      outlineVariant = hexToRgb(fixed.outlineVariant)!;
      surface3 = lerpRgb(variant, isDark ? [255, 255, 255] : [0, 0, 0], isDark ? 0.06 : 0.05);
      surface4 = lerpRgb(variant, isDark ? [255, 255, 255] : [0, 0, 0], isDark ? 0.12 : 0.10);
    } else {
      isDark = resolvedMode === "dark";
      const seedHex =
        adaptiveAccent && trackAccent
          ? trackAccent
          : isDark
            ? DEFAULT_DARK_SEED
            : DEFAULT_LIGHT_SEED;
      const fallbackRgb: Rgb = isDark ? [156, 202, 255] : [33, 102, 194];
      const seedRgb = hexToRgb(seedHex) ?? fallbackRgb;
      const [hue, sat] = rgbToHsb(seedRgb[0], seedRgb[1], seedRgb[2]);

      const tone = (s: number, v: number) => hsbToRgb(hue, s, v);
      const neutralSat = Math.min(sat, 0.18);

      primary = tone(
        isDark ? Math.min(sat, 0.45) : Math.min(sat, 0.75),
        isDark ? 0.98 : 0.43
      );
      background = tone(neutralSat, isDark ? 0.075 : 0.99);
      surface = tone(neutralSat, isDark ? 0.11 : 0.97);
      variant = tone(neutralSat, isDark ? 0.17 : 0.91);
      container = tone(Math.min(sat, 0.32), isDark ? 0.24 : 0.88);
      outline = tone(neutralSat, isDark ? 0.4 : 0.75);
      outlineVariant = tone(neutralSat, isDark ? 0.25 : 0.85);
      surface3 = tone(neutralSat, isDark ? 0.22 : 0.86);
      surface4 = tone(neutralSat, isDark ? 0.28 : 0.80);

      onPrimary = contrastingText(primary);
      onContainer = contrastingText(container);
      onSurface = contrastingText(surface);
      const onVariantContrast = contrastingText(variant);
      onSurfaceVariant = lerpRgb(onVariantContrast, variant, isDark ? 0.18 : 0.15);
    }

    const textMuted = lerpRgb(onSurfaceVariant, surface, 0.35);
    const barContent = ensureContrast(onSurface, variant, 4.5);
    const barSecondary = ensureContrast(onSurfaceVariant, variant, 4.5, barContent);
    const barAccent = ensureContrast(primary, variant, 3.0, barContent);

    root.style.setProperty("--accent", rgbToHex(primary));
    root.style.setProperty("--accent-rgb", `${primary[0]}, ${primary[1]}, ${primary[2]}`);
    root.style.setProperty("--on-accent", rgbToHex(onPrimary));
    root.style.setProperty("--accent-container", rgbToHex(container));
    root.style.setProperty("--on-accent-container", rgbToHex(onContainer));

    root.style.setProperty("--surface-0", rgbToHex(background));
    root.style.setProperty("--surface-1", rgbToHex(surface));
    root.style.setProperty("--surface-2", rgbToHex(variant));
    root.style.setProperty("--surface-3", rgbToHex(surface3));
    root.style.setProperty("--surface-4", rgbToHex(surface4));
    root.style.setProperty(
      "--surface-variant",
      isDark ? "rgba(255, 255, 255, 0.06)" : "rgba(0, 0, 0, 0.05)"
    );
    root.style.setProperty("--outline", rgbToHex(outlineVariant));
    root.style.setProperty("--outline-strong", rgbToHex(outline));

    root.style.setProperty("--text-primary", rgbToHex(onSurface));
    root.style.setProperty("--text-secondary", rgbToHex(onSurfaceVariant));
    root.style.setProperty("--text-muted", rgbToHex(textMuted));

    root.style.setProperty("--bar-content", rgbToHex(barContent));
    root.style.setProperty("--bar-secondary", rgbToHex(barSecondary));
    root.style.setProperty("--bar-accent", rgbToHex(barAccent));
    root.style.setProperty("--player-glass", rgbToHex(variant));
  }, [adaptiveAccent, trackAccent, resolvedMode, presetId]);

  return <>{children}</>;
}
