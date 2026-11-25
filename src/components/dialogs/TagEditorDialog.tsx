import { useEffect, useState } from "react";
import { useLibraryStore } from "../../store/useLibraryStore";
import { Artwork } from "../Artwork";
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

  useEffect(() => {
    if (!track) return;
    setTitle(track.title);
    setArtist(track.artist);
    setAlbum(track.album);
    setAlbumArtist(track.albumArtist);
    setGenre(track.genre);
    setYear(track.year !== null ? String(track.year) : "");
    setTrackNum(track.trackNum !== null ? String(track.trackNum) : "");
  }, [track]);

  if (!track) return null;

  const ext = track.path.split(".").pop()?.toUpperCase() || "AUDIO";

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    try {
      await updateTags({
        trackId: track.id,
        title: title.trim() || track.title,
        artist: artist.trim() || "Unknown Artist",
        album: album.trim() || "Unknown Album",
        albumArtist: albumArtist.trim(),
        genre: genre.trim(),
        year: year.trim() ? Number(year) : null,
        trackNum: trackNum.trim() ? Number(trackNum) : null,
      });
    } finally {
      setSaving(false);
    }
  };

  return (
    <DialogShell
      title="Edit Track Tags"
      subtitle={track.path}
      onClose={() => openTagEditor(null)}
      maxWidth="max-w-[680px]"
      headerRight={
        <span
          className="px-2.5 py-1 rounded-lg text-[11px] font-bold tracking-wide"
          style={{
            backgroundColor: "var(--accent-container)",
            color: "var(--on-accent-container)",
          }}
        >
          {ext}
        </span>
      }
    >
      <form onSubmit={handleSubmit} className="space-y-4">
        <div
          className="p-3.5 rounded-2xl flex items-center gap-4"
          style={{
            backgroundColor: "var(--surface-1)",
            border: "1px solid var(--outline)",
          }}
        >
          <Artwork
            artworkPath={track.artworkPath}
            accentColor={track.accentColor}
            alt={track.title}
            size="md"
            rounded="rounded-2xl"
          />
          <div className="min-w-0 flex-1">
            <div className="text-sm font-semibold truncate">{track.title}</div>
            <div className="text-xs truncate" style={{ color: "var(--text-secondary)" }}>
              {track.artist} • {track.album}
            </div>
          </div>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3.5">
          {[
            { label: "Title", value: title, set: setTitle, span: true },
            { label: "Artist", value: artist, set: setArtist },
            { label: "Album", value: album, set: setAlbum },
            { label: "Album Artist", value: albumArtist, set: setAlbumArtist },
            { label: "Genre", value: genre, set: setGenre },
          ].map((field) => (
            <label
              key={field.label}
              className={`block space-y-1 ${field.span ? "sm:col-span-2" : ""}`}
            >
              <span
                className="text-xs font-medium"
                style={{ color: "var(--text-secondary)" }}
              >
                {field.label}
              </span>
              <input
                type="text"
                value={field.value}
                onChange={(e) => field.set(e.target.value)}
                className="w-full px-3.5 py-2 rounded-xl text-sm outline-none"
                style={{
                  backgroundColor: "var(--surface-1)",
                  border: "1px solid var(--outline)",
                }}
              />
            </label>
          ))}

          <label className="block space-y-1">
            <span
              className="text-xs font-medium"
              style={{ color: "var(--text-secondary)" }}
            >
              Year
            </span>
            <input
              type="number"
              value={year}
              onChange={(e) => setYear(e.target.value)}
              className="w-full px-3.5 py-2 rounded-xl text-sm outline-none"
              style={{
                backgroundColor: "var(--surface-1)",
                border: "1px solid var(--outline)",
              }}
            />
          </label>
          <label className="block space-y-1">
            <span
              className="text-xs font-medium"
              style={{ color: "var(--text-secondary)" }}
            >
              Track #
            </span>
            <input
              type="number"
              value={trackNum}
              onChange={(e) => setTrackNum(e.target.value)}
              className="w-full px-3.5 py-2 rounded-xl text-sm outline-none"
              style={{
                backgroundColor: "var(--surface-1)",
                border: "1px solid var(--outline)",
              }}
            />
          </label>
        </div>

        <div className="pt-2 flex justify-end gap-2">
          <button
            type="button"
            onClick={() => openTagEditor(null)}
            className="px-4 py-2 rounded-full text-sm cursor-pointer"
            style={{ backgroundColor: "var(--surface-3)" }}
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={saving}
            className="px-5 py-2 rounded-full text-sm font-medium cursor-pointer disabled:opacity-50"
            style={{
              backgroundColor: "var(--accent)",
              color: "var(--on-accent)",
            }}
          >
            {saving ? "Saving…" : "Save Tags"}
          </button>
        </div>
      </form>
    </DialogShell>
  );
}
