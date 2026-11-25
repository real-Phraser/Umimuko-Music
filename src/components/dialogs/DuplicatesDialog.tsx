import { useEffect, useState } from "react";
import { api } from "../../api/tauri";
import { useLibraryStore } from "../../store/useLibraryStore";
import { usePlayerStore } from "../../store/usePlayerStore";
import type { DuplicateGroup } from "../../types/ipc";
import { formatBytes } from "../../utils/format";
import { DialogShell } from "./DialogShell";

function fileExt(path: string): string {
  return path.split(".").pop()?.toUpperCase() || "AUDIO";
}

export function DuplicatesDialog() {
  const activeDialog = useLibraryStore((s) => s.activeDialog);
  const openDialog = useLibraryStore((s) => s.openDialog);
  const deleteTrack = useLibraryStore((s) => s.deleteTrack);
  const tracksCount = useLibraryStore((s) => s.tracks.length);
  const currentPlayingTrack = usePlayerStore((s) => s.playback.currentTrack);

  const [groups, setGroups] = useState<DuplicateGroup[]>([]);
  const [isScanning, setIsScanning] = useState(false);
  const [scanCompleted, setScanCompleted] = useState(false);
  const [activeTrackAlert, setActiveTrackAlert] = useState<string | null>(null);

  const isCurrentlyPlaying = (id: string, path: string): boolean => {
    if (!currentPlayingTrack) return false;
    return currentPlayingTrack.id === id || currentPlayingTrack.path === path;
  };

  const runScan = async () => {
    setIsScanning(true);
    setScanCompleted(false);
    try {
      const res = await api.findDuplicates();
      setGroups(res);
      setScanCompleted(true);
    } finally {
      setIsScanning(false);
    }
  };

  useEffect(() => {
    if (activeDialog === "duplicates") {
      setScanCompleted(false);
      setGroups([]);
      setActiveTrackAlert(null);
      void runScan();
    }
  }, [activeDialog]);

  if (activeDialog !== "duplicates") return null;

  const handleDeleteCandidate = async (trackId: string, title: string, path: string) => {
    if (isCurrentlyPlaying(trackId, path)) {
      setActiveTrackAlert(`Cannot delete '${title}' because it is currently playing.`);
      return;
    }
    await deleteTrack(trackId, false);
    await runScan();
  };

  const handleResolveAll = async () => {
    let skipped = 0;
    for (const group of groups) {
      const candidates = group.tracks.slice(1);
      for (const cand of candidates) {
        if (isCurrentlyPlaying(cand.id, cand.path)) {
          skipped += 1;
          continue;
        }
        await deleteTrack(cand.id, false);
      }
    }
    if (skipped > 0) {
      setActiveTrackAlert(
        `Skipped deleting ${skipped} duplicate file(s) currently being played.`
      );
    }
    await runScan();
  };

  return (
    <>
      <DialogShell
        title="Duplicate Track Detection"
        subtitle="Multi-tier scan: SHA-256 binary hash, duration heuristics, and normalized metadata"
        maxWidth="max-w-[840px]"
        onClose={() => openDialog(null)}
        headerRight={
          <button
            type="button"
            disabled={isScanning || tracksCount < 2}
            onClick={() => void runScan()}
            className="px-4 py-2 rounded-full text-xs font-semibold cursor-pointer disabled:opacity-45"
            style={{
              backgroundColor: "var(--accent)",
              color: "var(--on-accent)",
            }}
          >
            {isScanning ? "Scanning..." : scanCompleted ? "Rescan" : "Scan Library"}
          </button>
        }
      >
        {isScanning ? (
          <div className="py-16 flex flex-col items-center justify-center gap-4">
            <div
              className="w-8 h-8 rounded-full border-2 border-t-transparent animate-spin"
              style={{ borderColor: "var(--accent)", borderTopColor: "transparent" }}
            />
            <span className="text-sm" style={{ color: "var(--text-secondary)" }}>
              Scanning library for duplicate tracks...
            </span>
          </div>
        ) : !scanCompleted ? (
          <div className="py-16 flex flex-col items-center justify-center gap-2 text-center">
            <div className="text-base font-medium">
              Library has {tracksCount} tracks.
            </div>
            <div className="text-sm" style={{ color: "var(--text-secondary)" }}>
              Click &apos;Scan Library&apos; above to analyze for duplicates.
            </div>
          </div>
        ) : groups.length === 0 ? (
          <div className="py-16 flex items-center justify-center">
            <span className="text-base font-medium" style={{ color: "var(--accent)" }}>
              No duplicates found! Your library is clean.
            </span>
          </div>
        ) : (
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <span className="text-sm font-semibold">
                Found {groups.length} duplicate group(s)
              </span>
              <button
                type="button"
                onClick={() => void handleResolveAll()}
                className="px-3 py-1.5 rounded-full text-xs font-medium hover:bg-[var(--surface-variant)] cursor-pointer"
                style={{ color: "var(--accent)" }}
              >
                Resolve All (Keep Best Quality)
              </button>
            </div>

            <div className="space-y-4">
              {groups.map((group) => {
                const keeper = group.tracks[0];
                const candidates = group.tracks.slice(1);
                if (!keeper) return null;

                return (
                  <div
                    key={group.id}
                    className="p-4 rounded-[14px] space-y-2.5"
                    style={{ backgroundColor: "var(--surface-2)" }}
                  >
                    <div className="flex items-center justify-between gap-3">
                      <span className="text-base font-bold truncate">{keeper.title}</span>
                      <span
                        className="px-2 py-0.5 rounded-[6px] text-[11px] font-medium shrink-0"
                        style={{
                          backgroundColor: "var(--accent-container)",
                          color: "var(--accent)",
                        }}
                      >
                        {group.reason === "exact_hash"
                          ? "Exact Binary Hash"
                          : "Metadata & Duration"}
                      </span>
                    </div>

                    {/* Recommended keeper */}
                    <div
                      className="p-2 rounded-lg flex items-center gap-2"
                      style={{
                        backgroundColor: "rgba(var(--accent-rgb), 0.08)",
                      }}
                    >
                      <span
                        className="text-[11px] font-bold shrink-0"
                        style={{ color: "var(--accent)" }}
                      >
                        KEEP
                      </span>
                      <div className="min-w-0 flex-1">
                        <div className="text-xs font-medium">
                          {keeper.artist} · {fileExt(keeper.path)} ·{" "}
                          {formatBytes(keeper.fileSize)}
                        </div>
                        <div
                          className="text-[11px] truncate"
                          style={{ color: "var(--text-secondary)" }}
                        >
                          {keeper.path}
                        </div>
                      </div>
                    </div>

                    {/* Duplicate candidates */}
                    {candidates.map((cand) => {
                      const playing = isCurrentlyPlaying(cand.id, cand.path);
                      return (
                        <div
                          key={cand.id}
                          className="p-2 rounded-lg flex items-center gap-2"
                          style={{ backgroundColor: "var(--surface-1)" }}
                        >
                          <div className="min-w-0 flex-1">
                            <div className="text-xs">
                              {cand.artist} · {fileExt(cand.path)} ·{" "}
                              {formatBytes(cand.fileSize)}
                            </div>
                            <div
                              className="text-[11px] truncate opacity-80"
                              style={{ color: "var(--text-secondary)" }}
                            >
                              {cand.path}
                            </div>
                          </div>

                          {playing ? (
                            <span
                              className="px-2 py-1 rounded text-[11px] font-bold shrink-0"
                              style={{
                                backgroundColor: "var(--accent-container)",
                                color: "var(--accent)",
                              }}
                            >
                              Playing
                            </span>
                          ) : (
                            <button
                              type="button"
                              onClick={() =>
                                void handleDeleteCandidate(cand.id, cand.title, cand.path)
                              }
                              className="h-8 px-2.5 rounded-full text-[11px] font-medium shrink-0 cursor-pointer"
                              style={{
                                border: "1px solid var(--danger)",
                                color: "var(--danger)",
                              }}
                            >
                              Delete File
                            </button>
                          )}
                        </div>
                      );
                    })}
                  </div>
                );
              })}
            </div>

            <div className="flex justify-end pt-2">
              <button
                type="button"
                onClick={() => openDialog(null)}
                className="px-5 py-2 rounded-full text-sm font-medium cursor-pointer"
                style={{
                  backgroundColor: "var(--accent-container)",
                  color: "var(--on-accent-container)",
                }}
              >
                Done
              </button>
            </div>
          </div>
        )}
      </DialogShell>

      {activeTrackAlert && (
        <div
          className="fixed inset-0 z-60 flex items-center justify-center p-4 bg-black/50"
          onMouseDown={(e) => {
            if (e.target === e.currentTarget) setActiveTrackAlert(null);
          }}
        >
          <div
            className="w-full max-w-sm rounded-[24px] p-6 space-y-4 shadow-2xl"
            style={{
              backgroundColor: "var(--surface-2)",
              border: "1px solid var(--outline)",
              color: "var(--text-primary)",
            }}
          >
            <h3 className="text-lg font-bold">Active Track Protection</h3>
            <p className="text-sm" style={{ color: "var(--text-secondary)" }}>
              {activeTrackAlert}
            </p>
            <div className="flex justify-end">
              <button
                type="button"
                onClick={() => setActiveTrackAlert(null)}
                className="px-4 py-2 rounded-full text-sm font-medium cursor-pointer"
                style={{ color: "var(--accent)" }}
              >
                OK
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
