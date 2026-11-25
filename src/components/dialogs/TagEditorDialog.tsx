import { useEffect, useState } from "react";
import { resolveArtworkUrl } from "../../api/tauri";
import { useLibraryStore } from "../../store/useLibraryStore";
import { Icon } from "../Icon";
import { DialogShell } from "./DialogShell";

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
      maxWidth="max-w-[740px]"
      headerRight={
        <span
          className="px-2.5 py-1 rounded-lg text-xs font-medium"
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
          <label className="block space-y-1 sm:col-span-2">
            <span className="text-xs font-medium" style={{ color: "var(--text-secondary)" }}>
              Title
            </span>
            <input
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              className="w-full px-3.5 py-2.5 rounded-xl text-sm outline-none"
              style={{
                backgroundColor: "var(--surface-0)",
                border: "1px solid var(--outline-strong)",
              }}
            />
          </label>

          <label className="block space-y-1">
            <span className="text-xs font-medium" style={{ color: "var(--text-secondary)" }}>
              Artist
            </span>
            <input
              type="text"
              value={artist}
              onChange={(e) => setArtist(e.target.value)}
              className="w-full px-3.5 py-2.5 rounded-xl text-sm outline-none"
              style={{
                backgroundColor: "var(--surface-0)",
                border: "1px solid var(--outline-strong)",
              }}
            />
          </label>

          <label className="block space-y-1">
            <span className="text-xs font-medium" style={{ color: "var(--text-secondary)" }}>
              Album Artist
            </span>
            <input
              type="text"
              value={albumArtist}
              onChange={(e) => setAlbumArtist(e.target.value)}
              className="w-full px-3.5 py-2.5 rounded-xl text-sm outline-none"
              style={{
                backgroundColor: "var(--surface-0)",
                border: "1px solid var(--outline-strong)",
              }}
            />
          </label>

          <label className="block space-y-1 sm:col-span-2">
            <span className="text-xs font-medium" style={{ color: "var(--text-secondary)" }}>
              Album
            </span>
            <input
              type="text"
              value={album}
              onChange={(e) => setAlbum(e.target.value)}
              className="w-full px-3.5 py-2.5 rounded-xl text-sm outline-none"
              style={{
                backgroundColor: "var(--surface-0)",
                border: "1px solid var(--outline-strong)",
              }}
            />
          </label>

          <label className="block space-y-1">
            <span className="text-xs font-medium" style={{ color: "var(--text-secondary)" }}>
              Release Year
            </span>
            <input
              type="text"
              inputMode="numeric"
              value={year}
              onChange={(e) => setYear(e.target.value.replace(/\D/g, ""))}
              className="w-full px-3.5 py-2.5 rounded-xl text-sm outline-none"
              style={{
                backgroundColor: "var(--surface-0)",
                border: "1px solid var(--outline-strong)",
              }}
            />
          </label>

          <label className="block space-y-1">
            <span className="text-xs font-medium" style={{ color: "var(--text-secondary)" }}>
              Genre
            </span>
            <input
              type="text"
              value={genre}
              onChange={(e) => setGenre(e.target.value)}
              className="w-full px-3.5 py-2.5 rounded-xl text-sm outline-none"
              style={{
                backgroundColor: "var(--surface-0)",
                border: "1px solid var(--outline-strong)",
              }}
            />
          </label>

          <label className="block space-y-1">
            <span className="text-xs font-medium" style={{ color: "var(--text-secondary)" }}>
              Track #
            </span>
            <input
              type="text"
              inputMode="numeric"
              value={trackNum}
              onChange={(e) => setTrackNum(e.target.value.replace(/\D/g, ""))}
              className="w-full px-3.5 py-2.5 rounded-xl text-sm outline-none"
              style={{
                backgroundColor: "var(--surface-0)",
                border: "1px solid var(--outline-strong)",
              }}
            />
          </label>
        </div>

        <div className="pt-2 flex justify-end gap-3">
          <button
            type="button"
            disabled={saving}
            onClick={() => openTagEditor(null)}
            className="px-4 py-2 rounded-full text-sm cursor-pointer hover:bg-[var(--surface-variant)]"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={saving || !title.trim()}
            className="px-5 py-2 rounded-full text-sm font-medium cursor-pointer disabled:opacity-45"
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
