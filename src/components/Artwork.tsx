import { resolveArtworkUrl } from "../api/tauri";
import { Icon } from "./Icon";

interface ArtworkProps {
  artworkPath: string | null | undefined;
  accentColor?: string | null;
  alt: string;
  size?: "xs" | "sm" | "md" | "lg" | "xl" | "full";
  rounded?: string;
  className?: string;
}

const SIZE_CLASSES: Record<NonNullable<ArtworkProps["size"]>, string> = {
  xs: "w-9 h-9",
  sm: "w-11 h-11",
  md: "w-14 h-14",
  lg: "w-36 h-36",
  xl: "w-56 h-56",
  full: "w-full h-full",
};

export function Artwork({
  artworkPath,
  accentColor,
  alt,
  size = "md",
  rounded = "rounded-xl",
  className = "",
}: ArtworkProps) {
  const url = resolveArtworkUrl(artworkPath);
  const color = accentColor || "#7c6af6";

  return (
    <div
      className={`relative overflow-hidden shrink-0 flex items-center justify-center ${SIZE_CLASSES[size]} ${rounded} ${className}`}
      style={{
        background: url
          ? "var(--surface-3)"
          : `radial-gradient(circle at 25% 25%, ${color}55, transparent 70%), linear-gradient(135deg, ${color}33, var(--surface-3))`,
        border: "1px solid var(--outline)",
      }}
    >
      {url ? (
        <img
          src={url}
          alt={alt}
          loading="lazy"
          className="w-full h-full object-cover"
          draggable={false}
        />
      ) : (
        <Icon
          name="music"
          size={size === "xs" ? 16 : size === "sm" ? 20 : size === "md" ? 24 : 44}
          className="opacity-70"
          style={{ color }}
        />
      )}
    </div>
  );
}
