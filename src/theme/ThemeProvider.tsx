import { useEffect, type ReactNode } from "react";
import { usePlayerStore } from "../store/usePlayerStore";
import { useSettingsStore } from "../store/useSettingsStore";

const DEFAULT_ACCENT = "#7c6af6";

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

  // Resolve light/dark mode
  useEffect(() => {
    const root = document.documentElement;
    const apply = (mode: "dark" | "light") => {
      root.dataset.theme = mode;
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

  // Apply album-adaptive accent color tokens
  useEffect(() => {
    const root = document.documentElement;
    const activeHex = adaptiveAccent && trackAccent ? trackAccent : DEFAULT_ACCENT;
    const rgb = hexToRgb(activeHex) ?? [124, 106, 246];
    const [r, g, b] = rgb;
    const lum = relativeLuminance(rgb);

    root.style.setProperty("--accent", activeHex);
    root.style.setProperty("--accent-rgb", `${r}, ${g}, ${b}`);
    root.style.setProperty("--on-accent", lum > 0.52 ? "#11131a" : "#ffffff");
    root.style.setProperty("--accent-container", `rgba(${r}, ${g}, ${b}, 0.2)`);
    root.style.setProperty(
      "--on-accent-container",
      root.dataset.theme === "light"
        ? `rgb(${Math.max(0, r - 90)}, ${Math.max(0, g - 90)}, ${Math.max(0, b - 90)})`
        : `rgb(${Math.min(255, r + 115)}, ${Math.min(255, g + 115)}, ${Math.min(255, b + 115)})`
    );
  }, [adaptiveAccent, trackAccent, theme]);

  return <>{children}</>;
}
