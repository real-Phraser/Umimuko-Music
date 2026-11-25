import { useEffect, useState } from "react";
import { resolveArtworkUrl } from "../../api/tauri";
import { useLibraryStore } from "../../store/useLibraryStore";
import { Icon } from "../Icon";
import { DialogShell } from "./DialogShell";

function M3OutlinedField({
  label,
  value,
  onChange,
  numeric = false,
  className = "",
}: {
  label: string;
  value: string;
  onChange: (next: string) => void;
  numeric?: boolean;
  className?: string;
}) {
  return (
    <label className={`block space-y-1 ${className}`}>
      <span className="text-xs font-medium" style={{ color: "var(--text-secondary)" }}>
        {label}
      </span>
      <input
        type="text"
        inputMode={numeric ? "numeric" : "text"}
        value={value}
        onChange={(e) =>
          onChange(numeric ? e.target.value.replace(/\D/g, "") : e.target.value)
        }
        className="w-full px-3.5 py-2.5 rounded-xl text-sm outline-none border border-[var(--outline-strong)] focus:border-[var(--accent)] transition-colors"
        style={{ backgroundColor: "var(--surface-0)" }}
      />
    </label>
  );
}

export function TagEditorDialog() {
  const track = useLibraryStore((s) => s.tagEditorTrack);
  const openTagEditor = useLibraryStore((s) => s.openTagEditor);
  const updateTags = useLibraryStore((s) => s.updateTags);

  const [title, setTitle] = useState("");
  const [artist, setArtist] = useState("");
  const [album, setAlbum] = useState("");
  const [albumArtist, setAlbumArtist] = useState("");
  const [genre, setGenre] = useState("");
  const [year, setYear] = useState("");
  const [trackNum, setTrackNum] = useState("");
  const [saving, setSaving] = useState(false);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [isError, setIsError] = useState(false);

  useEffect(() => {
    if (!track) return;
    setTitle(track.title);
    setArtist(track.artist);
    setAlbum(track.album);
    setAlbumArtist(track.albumArtist);
    setGenre(track.genre);
    setYear(track.year !== null && track.year > 0 ? String(track.year) : "");
    setTrackNum(track.trackNum !== null && track.trackNum > 0 ? String(track.trackNum) : "");
    setStatusMessage(null);
    setIsError(false);
  }, [track]);

  if (!track) return null;

  const ext = track.path.split(".").pop()?.toUpperCase() || "AUDIO";
  const coverUrl = resolveArtworkUrl(track.artworkPath);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim()) return;
    setSaving(true);
    setStatusMessage("Writing ID3 / metadata tags safely...");
    setIsError(false);
    try {
      await updateTags({
        trackId: track.id,
        title: title.trim(),
        artist: artist.trim() || "Unknown Artist",
        album: album.trim() || "Unknown Album",
        albumArtist: albumArtist.trim(),
        genre: genre.trim(),
        year: year.trim() ? Number(year) : null,
        trackNum: trackNum.trim() ? Number(trackNum) : null,
      });
    } catch {
      setStatusMessage("Failed writing tags to file. Please check file permissions.");
      setIsError(true);
    } finally {
      setSaving(false);
    }
  };

  return (
    <DialogShell
      title="Edit Track Tags"
      subtitle={track.path}
      onClose={() => openTagEditor(null)}
      maxWidth="max-w-[760px]"
      headerRight={
        <span
          className="px-2.5 py-1 rounded-[8px] text-xs font-medium"
          style={{
            backgroundColor: "var(--accent-container)",
            color: "var(--accent)",
          }}
        >
          {ext}
        </span>
      }
    >
      <form onSubmit={handleSubmit} className="space-y-4">
        {/* Cover Artwork Header matching TagEditorDialog.kt */}
        <div className="flex items-center gap-5">
          <div
            className="w-[100px] h-[100px] shrink-0 overflow-hidden flex items-center justify-center"
            style={{
              borderRadius: "25%",
              backgroundColor: "rgba(var(--accent-rgb), 0.18)",
            }}
          >
            {coverUrl ? (
              <img
                src={coverUrl}
                alt={track.title}
                className="w-full h-full object-cover"
                draggable={false}
              />
            ) : (
              <Icon name="music" size={32} style={{ color: "var(--accent)" }} />
            )}
          </div>
          <div className="min-w-0 flex-1 space-y-1">
            <div className="text-sm font-semibold">Cover Artwork</div>
            <div className="text-xs truncate" style={{ color: "var(--text-secondary)" }}>
              {track.artist} · {track.album}
            </div>
          </div>
        </div>

        {statusMessage && (
          <div
            className="text-xs font-medium"
            style={{ color: isError ? "var(--danger)" : "var(--accent)" }}
          >
            {statusMessage}
          </div>
        )}

        <div className="h-px" style={{ backgroundColor: "var(--outline)" }} />

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <M3OutlinedField
            label="Title"
            value={title}
            onChange={setTitle}
            className="sm:col-span-2"
          />
          <M3OutlinedField label="Artist" value={artist} onChange={setArtist} />
          <M3OutlinedField
            label="Album Artist"
            value={albumArtist}
            onChange={setAlbumArtist}
          />
          <M3OutlinedField
            label="Album"
            value={album}
            onChange={setAlbum}
            className="sm:col-span-2"
          />
          <M3OutlinedField
            label="Release Year"
            value={year}
            onChange={setYear}
            numeric
          />
          <M3OutlinedField label="Genre" value={genre} onChange={setGenre} />
          <M3OutlinedField
            label="Track #"
            value={trackNum}
            onChange={setTrackNum}
            numeric
          />
        </div>

        <div className="pt-2 flex justify-end gap-3">
          <button
            type="button"
            disabled={saving}
            onClick={() => openTagEditor(null)}
            className="h-10 px-4 rounded-full text-sm font-medium cursor-pointer hover:bg-[var(--surface-variant)] transition-colors"
            style={{ color: "var(--accent)" }}
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={saving || !title.trim()}
            className="h-10 px-5 rounded-full text-sm font-medium cursor-pointer disabled:opacity-45 transition-opacity hover:opacity-90"
            style={{
              backgroundColor: "var(--accent)",
              color: "var(--on-accent)",
            }}
          >
            {saving ? "Saving..." : "Save Changes"}
          </button>
        </div>
      </form>
    </DialogShell>
  );
}
