import { useEffect, useState } from "react";
import { api } from "../../api/tauri";
import { useLibraryStore } from "../../store/useLibraryStore";
import type { DuplicateGroup } from "../../types/ipc";
import { formatBytes, formatDuration } from "../../utils/format";
import { Artwork } from "../Artwork";
import { Icon } from "../Icon";
import { DialogShell } from "./DialogShell";

export function DuplicatesDialog() {
  const activeDialog = useLibraryStore((s) => s.activeDialog);
  const openDialog = useLibraryStore((s) => s.openDialog);
  const deleteTrack = useLibraryStore((s) => s.deleteTrack);

  const [groups, setGroups] = useState<DuplicateGroup[]>([]);
  const [loading, setLoading] = useState(false);

  const loadDuplicates = async () => {
    setLoading(true);
    try {
      const res = await api.findDuplicates();
      setGroups(res);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (activeDialog === "duplicates") {
      void loadDuplicates();
    }
  }, [activeDialog]);

  if (activeDialog !== "duplicates") return null;

  const handleRemove = async (trackId: string) => {
    await deleteTrack(trackId, false);
    await loadDuplicates();
  };

  return (
    <DialogShell
      title="Duplicate Detector"
      subtitle="Finds exact file matches and metadata + duration duplicates"
      onClose={() => openDialog(null)}
    >
      {loading ? (
        <div className="py-12 text-center text-sm" style={{ color: "var(--text-secondary)" }}>
          Scanning for duplicates…
        </div>
      ) : groups.length === 0 ? (
        <div className="py-12 text-center space-y-2">
          <Icon name="library" size={32} className="mx-auto opacity-45" />
          <div className="text-sm font-medium">No duplicates found</div>
          <div className="text-xs" style={{ color: "var(--text-secondary)" }}>
            Your library is clean.
          </div>
        </div>
      ) : (
        <div className="space-y-4">
          {groups.map((group) => (
            <div
              key={group.id}
              className="p-4 rounded-2xl space-y-2.5"
              style={{
                backgroundColor: "var(--surface-1)",
                border: "1px solid var(--outline)",
              }}
            >
              <div className="flex items-center justify-between text-xs">
                <span className="font-semibold">
                  {group.tracks[0]?.title} — {group.tracks[0]?.artist}
                </span>
                <span
                  className="px-2.5 py-0.5 rounded-full"
                  style={{
                    backgroundColor: "var(--accent-container)",
                    color: "var(--on-accent-container)",
                  }}
                >
                  {group.reason === "exact_hash" ? "Exact Hash" : "Metadata + Duration"}
                </span>
              </div>

              <div className="space-y-2">
                {group.tracks.map((track) => (
                  <div
                    key={track.id}
                    className="flex items-center justify-between gap-3 p-2.5 rounded-xl"
                    style={{ backgroundColor: "var(--surface-2)" }}
                  >
                    <div className="flex items-center gap-3 min-w-0">
                      <Artwork
                        artworkPath={track.artworkPath}
                        accentColor={track.accentColor}
                        alt={track.title}
                        size="xs"
                        rounded="rounded-lg"
                      />
                      <div className="min-w-0">
                        <div className="text-xs font-medium truncate">{track.path}</div>
                        <div className="text-[11px]" style={{ color: "var(--text-muted)" }}>
                          {formatDuration(track.durationMs)} • {formatBytes(track.fileSize)}
                        </div>
                      </div>
                    </div>

                    <button
                      type="button"
                      onClick={() => void handleRemove(track.id)}
                      className="px-3 py-1.5 rounded-full text-xs font-medium shrink-0 cursor-pointer"
                      style={{
                        backgroundColor: "var(--danger-container)",
                        color: "var(--danger)",
                      }}
                    >
                      Remove
                    </button>
                  </div>
                ))}
              </div>
            </div>
          ))}
        </div>
      )}
    </DialogShell>
  );
}
