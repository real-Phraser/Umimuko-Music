import { useEffect, useState, type ReactNode } from "react";
import { usePlayerStore } from "../store/usePlayerStore";
import { useSettingsStore } from "../store/useSettingsStore";

const DEFAULT_DARK_ACCENT = "#f29b8c";
const DEFAULT_LIGHT_ACCENT = "#2166c2";

function hexToRgb(hex: string): [number, number, number] | null {
  const cleaned = hex.trim().replace(/^#/, "");
  if (cleaned.length === 3) {
    const r = parseInt(cleaned[0] + cleaned[0], 16);
    const g = parseInt(cleaned[1] + cleaned[1], 16);
    const b = parseInt(cleaned[2] + cleaned[2], 16);
    return Number.isNaN(r) ? null : [r, g, b];
  }
  if (cleaned.length === 6) {
    const r = parseInt(cleaned.slice(0, 2), 16);
    const g = parseInt(cleaned.slice(2, 4), 16);
    const b = parseInt(cleaned.slice(4, 6), 16);
    return Number.isNaN(r) ? null : [r, g, b];
  }
  return null;
}

function rgbToHsv(r: number, g: number, b: number): [number, number, number] {
  const rn = r / 255;
  const gn = g / 255;
  const bn = b / 255;
  const max = Math.max(rn, gn, bn);
  const min = Math.min(rn, gn, bn);
  const d = max - min;
  let h = 0;
  if (d !== 0) {
    if (max === rn) h = ((gn - bn) / d + (gn < bn ? 6 : 0)) / 6;
    else if (max === gn) h = ((bn - rn) / d + 2) / 6;
    else h = ((rn - gn) / d + 4) / 6;
  }
  const s = max === 0 ? 0 : d / max;
  return [h, s, max];
}

function hsvToRgb(h: number, s: number, v: number): [number, number, number] {
  const i = Math.floor(h * 6);
  const f = h * 6 - i;
  const p = v * (1 - s);
  const q = v * (1 - f * s);
  const t = v * (1 - (1 - f) * s);
  let rn = 0;
  let gn = 0;
  let bn = 0;
  switch (i % 6) {
    case 0:
      rn = v; gn = t; bn = p;
      break;
    case 1:
      rn = q; gn = v; bn = p;
      break;
    case 2:
      rn = p; gn = v; bn = t;
      break;
    case 3:
      rn = p; gn = q; bn = v;
      break;
    case 4:
      rn = t; gn = p; bn = v;
      break;
    case 5:
      rn = v; gn = p; bn = q;
      break;
  }
  return [Math.round(rn * 255), Math.round(gn * 255), Math.round(bn * 255)];
}

function rgbToHex(r: number, g: number, b: number): string {
  const toHex = (n: number) => Math.max(0, Math.min(255, n)).toString(16).padStart(2, "0");
  return `#${toHex(r)}${toHex(g)}${toHex(b)}`;
}

function relativeLuminance([r, g, b]: [number, number, number]): number {
  const norm = [r, g, b].map((c) => {
    const s = c / 255;
    return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
  });
  return 0.2126 * norm[0] + 0.7152 * norm[1] + 0.0722 * norm[2];
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const theme = useSettingsStore((s) => s.settings.theme);
  const adaptiveAccent = useSettingsStore((s) => s.settings.adaptiveAccent);
  const trackAccent = usePlayerStore((s) => s.playback.currentTrack?.accentColor ?? null);
  const [resolvedMode, setResolvedMode] = useState<"dark" | "light">("dark");

  // Resolve light/dark mode
  useEffect(() => {
    const root = document.documentElement;
    const apply = (mode: "dark" | "light") => {
      root.dataset.theme = mode;
      setResolvedMode(mode);
    };

    if (theme === "system") {
      const mq = window.matchMedia("(prefers-color-scheme: light)");
      apply(mq.matches ? "light" : "dark");
      const listener = (e: MediaQueryListEvent) => apply(e.matches ? "light" : "dark");
      mq.addEventListener("change", listener);
      return () => mq.removeEventListener("change", listener);
    }
    apply(theme);
  }, [theme]);

  // Apply album-art-derived tonal palette matching AlbumTheme.kt & WindowTheme.kt
  useEffect(() => {
    const root = document.documentElement;
    const isDark = resolvedMode === "dark";
    const hasCustomAccent = Boolean(adaptiveAccent && trackAccent);
    const seedHex = hasCustomAccent
      ? trackAccent!
      : isDark
        ? DEFAULT_DARK_ACCENT
        : DEFAULT_LIGHT_ACCENT;

    const seedRgb = hexToRgb(seedHex) ?? (isDark ? [242, 155, 140] : [33, 102, 194]);
    const [h, s] = rgbToHsv(seedRgb[0], seedRgb[1], seedRgb[2]);

    // Primary accent derived from album hue (AlbumTheme.kt tonal curve)
    const primaryRgb = hasCustomAccent
      ? hsvToRgb(h, isDark ? Math.min(s, 0.48) : Math.min(s, 0.72), isDark ? 0.96 : 0.44)
      : seedRgb;
    const primaryHex = rgbToHex(primaryRgb[0], primaryRgb[1], primaryRgb[2]);
    const primaryLum = relativeLuminance(primaryRgb);

    // Tonal container (active sidebar pill / playing row)
    const containerRgb: [number, number, number] = hasCustomAccent
      ? hsvToRgb(h, Math.min(s, 0.30), isDark ? 0.24 : 0.89)
      : isDark
        ? [58, 43, 40]
        : [227, 222, 219];
    const containerHex = rgbToHex(containerRgb[0], containerRgb[1], containerRgb[2]);
    const containerLum = relativeLuminance(containerRgb);

    // Subtle warm surface harmonization when adaptive accent is active
    const neutralSat = hasCustomAccent ? Math.min(s, 0.14) : 0.08;
    const warmHue = hasCustomAccent ? h : 0.055; // warm brown-stone hue #141211 / #FBF9F7
    const s0Rgb = hasCustomAccent
      ? hsvToRgb(warmHue, neutralSat, isDark ? 0.078 : 0.985)
      : isDark
        ? [20, 18, 17]
        : [251, 249, 247];
    const s1Rgb = hasCustomAccent
      ? hsvToRgb(warmHue, neutralSat, isDark ? 0.114 : 0.955)
      : isDark
        ? [29, 25, 24]
        : [243, 240, 238];
    const s2Rgb = hasCustomAccent
      ? hsvToRgb(warmHue, neutralSat, isDark ? 0.153 : 0.915)
      : isDark
        ? [38, 33, 31]
        : [234, 229, 226];
    const s3Rgb = hasCustomAccent
      ? hsvToRgb(warmHue, neutralSat, isDark ? 0.192 : 0.875)
      : isDark
        ? [48, 42, 39]
        : [224, 218, 214];

    root.style.setProperty("--accent", primaryHex);
    root.style.setProperty("--accent-rgb", `${primaryRgb[0]}, ${primaryRgb[1]}, ${primaryRgb[2]}`);
    root.style.setProperty("--on-accent", primaryLum > 0.38 ? "#1c1715" : "#ffffff");
    root.style.setProperty("--accent-container", containerHex);
    root.style.setProperty(
      "--on-accent-container",
      containerLum > 0.4 ? "#1c1917" : "#f5efea"
    );

    root.style.setProperty("--surface-0", rgbToHex(s0Rgb[0], s0Rgb[1], s0Rgb[2]));
    root.style.setProperty("--surface-1", rgbToHex(s1Rgb[0], s1Rgb[1], s1Rgb[2]));
    root.style.setProperty("--surface-2", rgbToHex(s2Rgb[0], s2Rgb[1], s2Rgb[2]));
    root.style.setProperty("--surface-3", rgbToHex(s3Rgb[0], s3Rgb[1], s3Rgb[2]));
    root.style.setProperty("--player-glass", rgbToHex(s1Rgb[0], s1Rgb[1], s1Rgb[2]));
  }, [adaptiveAccent, trackAccent, resolvedMode]);

  return <>{children}</>;
}
