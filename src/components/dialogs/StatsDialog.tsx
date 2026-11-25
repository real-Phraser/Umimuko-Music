import { useEffect, useState } from "react";
import { api } from "../../api/tauri";
import { useLibraryStore } from "../../store/useLibraryStore";
import type { ListeningStats } from "../../types/ipc";
import { formatPlayTimeLong } from "../../utils/format";
import { Artwork } from "../Artwork";
import { DialogShell } from "./DialogShell";

export function StatsDialog() {
  const activeDialog = useLibraryStore((s) => s.activeDialog);
  const openDialog = useLibraryStore((s) => s.openDialog);

  const [stats, setStats] = useState<ListeningStats | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (activeDialog !== "stats") return;
    setLoading(true);
    api
      .getListeningStats()
      .then(setStats)
      .finally(() => setLoading(false));
  }, [activeDialog]);

  if (activeDialog !== "stats") return null;

  return (
    <DialogShell
      title="Listening Stats"
      subtitle="Your playback history & top favorites"
      icon="queue"
      maxWidth="max-w-[740px]"
      onClose={() => openDialog(null)}
    >
      {loading || !stats ? (
        <div className="py-12 text-center text-sm" style={{ color: "var(--text-secondary)" }}>
          Loading statistics…
        </div>
      ) : (
        <div className="space-y-6">
          {/* Summary Cards */}
          <div className="grid grid-cols-3 gap-3">
            {[
              { label: "Total Listening", value: formatPlayTimeLong(stats.totalPlayTimeMs) },
              { label: "Total Plays", value: String(stats.totalPlays) },
              { label: "Library Size", value: `${stats.tracksCount} tracks` },
            ].map((card) => (
              <div
                key={card.label}
                className="p-4 rounded-2xl"
                style={{
                  backgroundColor: "var(--surface-1)",
                  border: "1px solid var(--outline)",
                }}
              >
                <div className="text-xs" style={{ color: "var(--text-secondary)" }}>
                  {card.label}
                </div>
                <div className="text-xl font-bold mt-1" style={{ color: "var(--accent)" }}>
                  {card.value}
                </div>
              </div>
            ))}
          </div>

          {/* Top Tracks & Top Artists */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div
              className="p-4 rounded-2xl space-y-3"
              style={{
                backgroundColor: "var(--surface-1)",
                border: "1px solid var(--outline)",
              }}
            >
              <h3 className="text-sm font-semibold">Top Tracks</h3>
              <div className="space-y-2.5">
                {stats.topTracks.map((item, idx) => (
                  <div key={item.track.id} className="flex items-center gap-3">
                    <span
                      className="text-xs font-semibold w-4"
                      style={{ color: "var(--text-muted)" }}
                    >
                      {idx + 1}
                    </span>
                    <Artwork
                      artworkPath={item.track.artworkPath}
                      accentColor={item.track.accentColor}
                      alt={item.track.title}
                      size="xs"
                      rounded="rounded-lg"
                    />
                    <div className="min-w-0 flex-1">
                      <div className="text-xs font-medium truncate">{item.track.title}</div>
                      <div className="text-[11px] truncate" style={{ color: "var(--text-secondary)" }}>
                        {item.track.artist}
                      </div>
                    </div>
                    <span className="text-xs font-medium tabular-nums" style={{ color: "var(--accent)" }}>
                      {item.playCount}×
                    </span>
                  </div>
                ))}
              </div>
            </div>

            <div
              className="p-4 rounded-2xl space-y-3"
              style={{
                backgroundColor: "var(--surface-1)",
                border: "1px solid var(--outline)",
              }}
            >
              <h3 className="text-sm font-semibold">Top Artists</h3>
              <div className="space-y-2.5">
                {stats.topArtists.map((item, idx) => (
                  <div key={item.artist} className="flex items-center justify-between gap-3">
                    <div className="flex items-center gap-2.5 min-w-0">
                      <span
                        className="text-xs font-semibold w-4"
                        style={{ color: "var(--text-muted)" }}
                      >
                        {idx + 1}
                      </span>
                      <span className="text-xs font-medium truncate">{item.artist}</span>
                    </div>
                    <span className="text-xs tabular-nums" style={{ color: "var(--text-secondary)" }}>
                      {item.playCount} plays • {formatPlayTimeLong(item.totalMs)}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      )}
    </DialogShell>
  );
}
