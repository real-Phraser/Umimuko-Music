import { useMemo } from "react";
import { useLibraryStore } from "../store/useLibraryStore";
import { usePlayerStore } from "../store/usePlayerStore";
import type { Track } from "../types/ipc";
import { Artwork } from "./Artwork";
import { Icon } from "./Icon";

function TrackCard({
  track,
  queueIds,
  badge,
}: {
  track: Track;
  queueIds: string[];
  badge?: string;
}) {
  const playTrack = usePlayerStore((s) => s.playTrack);
  const currentTrackId = usePlayerStore((s) => s.playback.currentTrackId);
  const isActive = currentTrackId === track.id;

  return (
    <button
      type="button"
      onClick={() => void playTrack(track.id, queueIds)}
      className="group text-left p-3 rounded-2xl transition-all hover:scale-[1.015] cursor-pointer"
      style={{
        backgroundColor: isActive ? "var(--accent-container)" : "var(--surface-2)",
        border: `1px solid ${isActive ? "var(--accent)" : "var(--outline)"}`,
      }}
    >
      <div className="relative aspect-square w-full mb-2.5">
        <Artwork
          artworkPath={track.artworkPath}
          accentColor={track.accentColor}
          alt={track.title}
          size="full"
          rounded="rounded-xl"
        />
        <div
          className="absolute right-2 bottom-2 w-9 h-9 rounded-full flex items-center justify-center shadow-lg opacity-0 group-hover:opacity-100 transition-opacity"
          style={{
            backgroundColor: "var(--accent)",
            color: "var(--on-accent)",
          }}
        >
          <Icon name="play" size={18} />
        </div>
        {badge && (
          <span
            className="absolute left-2 top-2 px-2 py-0.5 rounded-full text-[10px] font-medium backdrop-blur-md"
            style={{
              backgroundColor: "rgba(0,0,0,0.55)",
              color: "#fff",
            }}
          >
            {badge}
          </span>
        )}
      </div>
      <div className="text-sm font-medium truncate">{track.title}</div>
      <div className="text-xs truncate mt-0.5" style={{ color: "var(--text-secondary)" }}>
        {track.artist}
      </div>
    </button>
  );
}

export function HomeScreen() {
  const tracks = useLibraryStore((s) => s.tracks);
  const pinnedTrackIds = useLibraryStore((s) => s.pinnedTrackIds);
  const playlists = useLibraryStore((s) => s.playlists);
  const setActiveNav = useLibraryStore((s) => s.setActiveNav);
  const setLibraryTab = useLibraryStore((s) => s.setLibraryTab);
  const setSelectedPlaylistId = useLibraryStore((s) => s.setSelectedPlaylistId);
  const openDialog = useLibraryStore((s) => s.openDialog);
  const playTrack = usePlayerStore((s) => s.playTrack);

  const pinnedTracks = useMemo(
    () =>
      pinnedTrackIds
        .map((id) => tracks.find((t) => t.id === id))
        .filter((t): t is Track => Boolean(t)),
    [pinnedTrackIds, tracks]
  );

  const recentlyPlayed = useMemo(
    () =>
      [...tracks]
        .filter((t) => t.lastPlayedAt !== null)
        .sort((a, b) => (b.lastPlayedAt ?? 0) - (a.lastPlayedAt ?? 0))
        .slice(0, 6),
    [tracks]
  );

  const recentlyAdded = useMemo(
    () => [...tracks].sort((a, b) => b.addedAt - a.addedAt).slice(0, 6),
    [tracks]
  );

  const handleShuffleAll = () => {
    if (tracks.length === 0) return;
    const shuffled = [...tracks].sort(() => Math.random() - 0.5);
    void playTrack(
      shuffled[0].id,
      shuffled.map((t) => t.id)
    );
  };

  return (
    <div className="p-6 space-y-8">
      {/* Hero Banner */}
      <section
        className="p-6 rounded-3xl flex flex-col sm:flex-row sm:items-center justify-between gap-4 adaptive-transition"
        style={{
          background:
            "linear-gradient(135deg, var(--accent-container), var(--surface-2) 68%)",
          border: "1px solid var(--outline)",
        }}
      >
        <div>
          <div
            className="text-xs font-semibold uppercase tracking-wider"
            style={{ color: "var(--accent)" }}
          >
            Welcome Back
          </div>
          <h1 className="text-2xl font-bold tracking-tight mt-1">
            Your Sound, Adaptive & Pure
          </h1>
          <p className="text-sm mt-1" style={{ color: "var(--text-secondary)" }}>
            {tracks.length} tracks • {playlists.length} playlists ready to play
          </p>
        </div>

        <div className="flex items-center gap-2.5 shrink-0">
          <button
            type="button"
            onClick={handleShuffleAll}
            disabled={tracks.length === 0}
            className="px-4 py-2.5 rounded-full text-sm font-medium flex items-center gap-2 shadow-sm disabled:opacity-40 cursor-pointer"
            style={{
              backgroundColor: "var(--accent)",
              color: "var(--on-accent)",
            }}
          >
            <Icon name="shuffle" size={18} />
            <span>Shuffle All</span>
          </button>
          <button
            type="button"
            onClick={() => openDialog("settings")}
            className="px-4 py-2.5 rounded-full text-sm font-medium flex items-center gap-2 cursor-pointer"
            style={{
              backgroundColor: "var(--surface-3)",
              color: "var(--text-primary)",
            }}
          >
            <Icon name="folder" size={18} />
            <span>Folders</span>
          </button>
        </div>
      </section>

      {/* Pinned Shelf */}
      {(pinnedTracks.length > 0 || playlists.some((p) => p.pinned)) && (
        <section>
          <div className="flex items-center justify-between mb-3">
            <h2 className="text-base font-semibold flex items-center gap-2">
              <Icon name="pin" size={18} style={{ color: "var(--accent)" }} />
              <span>Pinned</span>
            </h2>
          </div>

          {playlists.some((p) => p.pinned) && (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3 mb-4">
              {playlists
                .filter((p) => p.pinned)
                .map((pl) => (
                  <button
                    key={pl.id}
                    type="button"
                    onClick={() => {
                      setActiveNav("library");
                      setLibraryTab("playlists");
                      setSelectedPlaylistId(pl.id);
                    }}
                    className="flex items-center gap-3 p-3 rounded-2xl text-left transition-colors hover:opacity-90 cursor-pointer"
                    style={{
                      backgroundColor: "var(--surface-2)",
                      border: "1px solid var(--outline)",
                    }}
                  >
                    <div
                      className="w-11 h-11 rounded-xl flex items-center justify-center shrink-0"
                      style={{
                        backgroundColor: "var(--accent-container)",
                        color: "var(--accent)",
                      }}
                    >
                      <Icon name="playlist" size={22} />
                    </div>
                    <div className="min-w-0">
                      <div className="text-sm font-medium truncate">{pl.name}</div>
                      <div
                        className="text-xs"
                        style={{ color: "var(--text-secondary)" }}
                      >
                        {pl.trackIds.length} tracks
                      </div>
                    </div>
                  </button>
                ))}
            </div>
          )}

          {pinnedTracks.length > 0 && (
            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-3.5">
              {pinnedTracks.map((track) => (
                <TrackCard
                  key={track.id}
                  track={track}
                  queueIds={pinnedTracks.map((t) => t.id)}
                  badge="Pinned"
                />
              ))}
            </div>
          )}
        </section>
      )}

      {/* Recently Played */}
      {recentlyPlayed.length > 0 && (
        <section>
          <h2 className="text-base font-semibold mb-3">Recently Played</h2>
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-3.5">
            {recentlyPlayed.map((track) => (
              <TrackCard
                key={track.id}
                track={track}
                queueIds={recentlyPlayed.map((t) => t.id)}
              />
            ))}
          </div>
        </section>
      )}

      {/* Recently Added */}
      <section>
        <div className="flex items-center justify-between mb-3">
          <h2 className="text-base font-semibold">Recently Added</h2>
          <button
            type="button"
            onClick={() => {
              setActiveNav("library");
              setLibraryTab("songs");
            }}
            className="text-xs font-medium cursor-pointer"
            style={{ color: "var(--accent)" }}
          >
            View all songs →
          </button>
        </div>

        {recentlyAdded.length === 0 ? (
          <div
            className="p-10 rounded-3xl text-center"
            style={{
              backgroundColor: "var(--surface-2)",
              border: "1px dashed var(--outline-strong)",
            }}
          >
            <Icon name="music" size={34} className="mx-auto mb-2 opacity-50" />
            <div className="text-sm font-medium">No music in your library yet</div>
            <p className="text-xs mt-1" style={{ color: "var(--text-secondary)" }}>
              Add a music folder in Settings to scan your audio collection.
            </p>
          </div>
        ) : (
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-3.5">
            {recentlyAdded.map((track) => (
              <TrackCard
                key={track.id}
                track={track}
                queueIds={recentlyAdded.map((t) => t.id)}
              />
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
