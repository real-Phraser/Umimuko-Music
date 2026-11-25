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
          Loading statistics...
        </div>
      ) : (
        <div className="space-y-5">
          {/* Highlights Row matching SettingsDialog.kt StatsTab */}
          <div className="grid grid-cols-3 gap-3">
            {[
              { label: "Total Plays", value: String(stats.totalPlays) },
              {
                label: "Time Listened",
                value: `${(stats.totalPlayTimeMs / 3_600_000).toFixed(1)} hrs`,
              },
              { label: "Library Size", value: `${stats.tracksCount} tracks` },
            ].map((card) => (
              <div
                key={card.label}
                className="p-4 rounded-2xl"
                style={{
                  backgroundColor: "color-mix(in srgb, var(--accent-container) 40%, transparent)",
                }}
              >
                <div className="text-xs font-medium" style={{ color: "var(--text-secondary)" }}>
                  {card.label}
                </div>
                <div
                  className="text-[28px] leading-9 font-bold mt-0.5 tabular-nums"
                  style={{ color: "var(--accent)" }}
                >
                  {card.value}
                </div>
              </div>
            ))}
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="space-y-2.5">
              <h3 className="text-base font-bold">Top Played Tracks</h3>
              {stats.topTracks.length === 0 ? (
                <div className="text-sm" style={{ color: "var(--text-secondary)" }}>
                  No listening stats recorded yet. Play some tracks to see analytics!
                </div>
              ) : (
                <div className="space-y-2">
                  {stats.topTracks.map((item, idx) => (
                    <div
                      key={item.track.id}
                      className="p-3 rounded-xl flex items-center gap-3"
                      style={{ backgroundColor: "var(--surface-2)" }}
                    >
                      <span
                        className="text-xs font-bold w-5 shrink-0"
                        style={{ color: "var(--text-secondary)" }}
                      >
                        #{idx + 1}
                      </span>
                      <Artwork
                        artworkPath={item.track.artworkPath}
                        accentColor={item.track.accentColor}
                        alt={item.track.title}
                        size="xs"
                        rounded="rounded-[9px]"
                      />
                      <div className="min-w-0 flex-1">
                        <div className="text-sm font-semibold truncate">
                          {item.track.title || "Unknown"}
                        </div>
                        <div
                          className="text-xs truncate"
                          style={{ color: "var(--text-secondary)" }}
                        >
                          {item.track.artist}
                        </div>
                      </div>
                      <span
                        className="text-xs font-medium tabular-nums shrink-0"
                        style={{ color: "var(--accent)" }}
                      >
                        {item.playCount} plays
                      </span>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <div className="space-y-2.5">
              <h3 className="text-base font-bold">Top Artists</h3>
              <div className="space-y-2">
                {stats.topArtists.map((item, idx) => (
                  <div
                    key={item.artist}
                    className="p-3 rounded-xl flex items-center justify-between gap-3"
                    style={{ backgroundColor: "var(--surface-2)" }}
                  >
                    <div className="flex items-center gap-2 min-w-0">
                      <span
                        className="text-xs font-bold w-5 shrink-0"
                        style={{ color: "var(--text-secondary)" }}
                      >
                        #{idx + 1}
                      </span>
                      <span className="text-sm font-semibold truncate">{item.artist}</span>
                    </div>
                    <span
                      className="text-xs font-medium tabular-nums shrink-0"
                      style={{ color: "var(--accent)" }}
                    >
                      {item.playCount} plays · {formatPlayTimeLong(item.totalMs)}
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
