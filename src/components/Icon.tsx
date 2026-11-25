import type { CSSProperties } from "react";

const iconModules = import.meta.glob("../assets/icons/material-rounded/*.svg", {
  eager: true,
  query: "?url",
  import: "default",
}) as Record<string, string>;

const ICON_MAP: Record<string, string> = {};
for (const [path, url] of Object.entries(iconModules)) {
  const filename = path.split("/").pop()?.replace(/\.svg$/, "");
  if (filename) {
    ICON_MAP[filename] = url;
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
  const url = ICON_MAP[name];
  if (!url) return null;

  return (
    <span
      aria-hidden="true"
      className={`inline-block shrink-0 select-none ${className}`}
      style={{
        width: size,
        height: size,
        backgroundColor: "currentColor",
        maskImage: `url(${url})`,
        WebkitMaskImage: `url(${url})`,
        maskRepeat: "no-repeat",
        WebkitMaskRepeat: "no-repeat",
        maskPosition: "center",
        WebkitMaskPosition: "center",
        maskSize: "contain",
        WebkitMaskSize: "contain",
        ...style,
      }}
    />
  );
}
