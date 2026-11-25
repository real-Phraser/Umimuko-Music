import type { CSSProperties } from "react";

const iconModules = import.meta.glob("../assets/icons/material-rounded/*.svg", {
  eager: true,
  query: "?raw",
  import: "default",
}) as Record<string, string>;

interface ParsedIcon {
  viewBox: string;
  body: string;
}

const ICON_MAP: Record<string, ParsedIcon> = {};
for (const [path, raw] of Object.entries(iconModules)) {
  const filename = path.split("/").pop()?.replace(/\.svg$/, "");
  if (!filename) continue;
  const viewBoxMatch = raw.match(/viewBox="([^"]+)"/);
  const bodyMatch = raw.match(/<svg[^>]*>([\s\S]*?)<\/svg>/i);
  if (bodyMatch) {
    ICON_MAP[filename] = {
      viewBox: viewBoxMatch?.[1] ?? "0 -960 960 960",
      body: bodyMatch[1],
    };
  }
}

export type IconName =
  | "add"
  | "album"
  | "artist"
  | "back"
  | "bubble"
  | "close"
  | "collapse"
  | "down"
  | "drag"
  | "expand"
  | "folder"
  | "heart"
  | "home"
  | "info"
  | "keyboard"
  | "levels"
  | "library"
  | "liked"
  | "lyrics"
  | "more"
  | "music"
  | "muted"
  | "next"
  | "pause"
  | "pin"
  | "play"
  | "playlist"
  | "playlist_add"
  | "previous"
  | "queue"
  | "remove"
  | "repeat"
  | "repeat_one"
  | "restore"
  | "search"
  | "settings"
  | "shuffle"
  | "timer"
  | "unpin"
  | "up"
  | "volume";

interface IconProps {
  name: IconName;
  size?: number;
  className?: string;
  style?: CSSProperties;
}

export function Icon({ name, size = 20, className = "", style }: IconProps) {
  const icon = ICON_MAP[name];
  if (!icon) return null;

  return (
    <svg
      aria-hidden="true"
      viewBox={icon.viewBox}
      width={size}
      height={size}
      fill="currentColor"
      className={`inline-block shrink-0 select-none ${className}`}
      style={style}
      dangerouslySetInnerHTML={{ __html: icon.body }}
    />
  );
}
