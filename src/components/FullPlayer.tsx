import { api } from "../api/tauri";
import { useLibraryStore } from "../store/useLibraryStore";
import { usePlayerStore } from "../store/usePlayerStore";
import { formatDuration } from "../utils/format";
import { Artwork } from "./Artwork";
import { Icon } from "./Icon";
import { SpicyLyricsView } from "./lyrics/SpicyLyricsView";

export function FullPlayer() {
  const fullPlayerOpen = usePlayerStore((s) => s.fullPlayerOpen);
  const fullPlayerTab = usePlayerStore((s) => s.fullPlayerTab);
  const playback = usePlayerStore((s) => s.playback);

  const setFullPlayerOpen = usePlayerStore((s) => s.setFullPlayerOpen);
  const setFullPlayerTab = usePlayerStore((s) => s.setFullPlayerTab);
  const setQueueDrawerOpen = usePlayerStore((s) => s.setQueueDrawerOpen);
  const togglePlayPause = usePlayerStore((s) => s.togglePlayPause);
  const nextTrack = usePlayerStore((s) => s.nextTrack);
  const prevTrack = usePlayerStore((s) => s.prevTrack);
  const seek = usePlayerStore((s) => s.seek);
  const toggleShuffle = usePlayerStore((s) => s.toggleShuffle);
  const cycleRepeat = usePlayerStore((s) => s.cycleRepeat);
  const toggleLike = useLibraryStore((s) => s.toggleLike);
  const openDialog = useLibraryStore((s) => s.openDialog);

  if (!fullPlayerOpen) return null;

  const track = playback.currentTrack;
  const isPlaying = playback.status === "playing";
  const duration = playback.durationMs || track?.durationMs || 0;
  const progressPct =
    duration > 0 ? Math.min(100, (playback.positionMs / duration) * 100) : 0;

  return (
    <div
      className="fixed inset-0 z-40 flex flex-col adaptive-transition"
      style={{
        background:
          "radial-gradient(circle at 20% 20%, var(--accent-container), var(--surface-0) 58%)",
      }}
    >
      {/* Top Bar */}
      <header className="px-6 py-4 flex items-center justify-between">
        <button
          type="button"
          onClick={() => setFullPlayerOpen(false)}
          className="p-2 rounded-full cursor-pointer"
          style={{ backgroundColor: "var(--surface-2)" }}
          title="Collapse"
        >
          <Icon name="collapse" size={20} />
        </button>

        <div
          className="flex items-center p-1 rounded-full"
          style={{
            backgroundColor: "var(--surface-2)",
            border: "1px solid var(--outline)",
          }}
        >
          <button
            type="button"
            onClick={() => setFullPlayerTab("lyrics")}
            className="px-4 py-1 rounded-full text-xs font-medium cursor-pointer"
            style={{
              backgroundColor:
                fullPlayerTab === "lyrics" ? "var(--accent)" : "transparent",
              color:
                fullPlayerTab === "lyrics"
                  ? "var(--on-accent)"
                  : "var(--text-secondary)",
            }}
          >
            Lyrics
          </button>
          <button
            type="button"
            onClick={() => setFullPlayerTab("artwork")}
            className="px-4 py-1 rounded-full text-xs font-medium cursor-pointer"
            style={{
              backgroundColor:
                fullPlayerTab === "artwork" ? "var(--accent)" : "transparent",
              color:
                fullPlayerTab === "artwork"
                  ? "var(--on-accent)"
                  : "var(--text-secondary)",
            }}
          >
            Now Playing
          </button>
        </div>

        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => openDialog("equalizer")}
            className="p-2 rounded-full cursor-pointer"
            style={{ backgroundColor: "var(--surface-2)" }}
            title="Equalizer"
          >
            <Icon name="levels" size={18} />
          </button>
          <button
            type="button"
            onClick={() => {
              setFullPlayerOpen(false);
              setQueueDrawerOpen(true);
            }}
            className="p-2 rounded-full cursor-pointer"
            style={{ backgroundColor: "var(--surface-2)" }}
            title="Open Queue"
          >
            <Icon name="queue" size={18} />
          </button>
          <button
            type="button"
            onClick={() => void api.toggleMiniPlayer(true)}
            className="p-2 rounded-full cursor-pointer"
            style={{ backgroundColor: "var(--surface-2)" }}
            title="Mini Player"
          >
            <Icon name="bubble" size={18} />
          </button>
        </div>
      </header>

      {/* Main Content */}
      <div className="flex-1 min-h-0 px-6 pb-8 grid grid-cols-1 lg:grid-cols-12 gap-8 items-center max-w-6xl w-full mx-auto">
        {/* Left: Artwork + Controls */}
        <div
          className={`flex flex-col items-center text-center ${
            fullPlayerTab === "artwork" ? "lg:col-span-12 max-w-md mx-auto w-full" : "lg:col-span-5"
          }`}
        >
          <div className="w-64 h-64 sm:w-72 sm:h-72 shadow-2xl rounded-3xl">
            <Artwork
              artworkPath={track?.artworkPath}
              accentColor={track?.accentColor}
              alt={track?.title ?? "Album art"}
              size="full"
              rounded="rounded-3xl"
            />
          </div>

          <div className="mt-6 w-full flex items-center justify-between gap-3 px-2">
            <div className="min-w-0 text-left">
              <h2 className="text-xl font-bold truncate">
                {track?.title ?? "No Track Selected"}
              </h2>
              <p className="text-sm truncate" style={{ color: "var(--text-secondary)" }}>
                {track ? `${track.artist} — ${track.album}` : "—"}
              </p>
            </div>
            {track && (
              <button
                type="button"
                onClick={() => void toggleLike(track.id)}
                className="p-2 rounded-full shrink-0 cursor-pointer"
                style={{ color: track.liked ? "var(--accent)" : "var(--text-muted)" }}
              >
                <Icon name={track.liked ? "liked" : "heart"} size={22} />
              </button>
            )}
          </div>

          {/* Seek */}
          <div className="w-full mt-5 space-y-1.5">
            <input
              type="range"
              min={0}
              max={Math.max(1, duration)}
              value={Math.min(playback.positionMs, duration)}
              onChange={(e) => void seek(Number(e.target.value))}
              className="umimuko-slider w-full"
              style={{
                background: `linear-gradient(90deg, var(--accent) ${progressPct}%, var(--surface-4) ${progressPct}%)`,
                height: 5,
                borderRadius: 999,
              }}
            />
            <div
              className="flex justify-between text-xs tabular-nums"
              style={{ color: "var(--text-muted)" }}
            >
              <span>{formatDuration(playback.positionMs)}</span>
              <span>{formatDuration(duration)}</span>
            </div>
          </div>

          {/* Transport */}
          <div className="mt-4 flex items-center justify-center gap-5">
            <button
              type="button"
              onClick={() => void toggleShuffle()}
              className="p-2 rounded-full cursor-pointer"
              style={{
                color: playback.shuffle ? "var(--accent)" : "var(--text-secondary)",
              }}
            >
              <Icon name="shuffle" size={20} />
            </button>
            <button
              type="button"
              onClick={() => void prevTrack()}
              className="p-2 rounded-full cursor-pointer"
            >
              <Icon name="previous" size={28} />
            </button>
            <button
              type="button"
              onClick={() => void togglePlayPause()}
              className="w-14 h-14 rounded-full flex items-center justify-center shadow-lg cursor-pointer"
              style={{
                backgroundColor: "var(--accent)",
                color: "var(--on-accent)",
              }}
            >
              <Icon name={isPlaying ? "pause" : "play"} size={28} />
            </button>
            <button
              type="button"
              onClick={() => void nextTrack()}
              className="p-2 rounded-full cursor-pointer"
            >
              <Icon name="next" size={28} />
            </button>
            <button
              type="button"
              onClick={() => void cycleRepeat()}
              className="p-2 rounded-full cursor-pointer"
              style={{
                color:
                  playback.repeat !== "off"
                    ? "var(--accent)"
                    : "var(--text-secondary)",
              }}
            >
              <Icon
                name={playback.repeat === "one" ? "repeat_one" : "repeat"}
                size={20}
              />
            </button>
          </div>
        </div>

        {/* Right: SpicyLyrics */}
        {fullPlayerTab === "lyrics" && (
          <div className="lg:col-span-7 h-full min-h-[320px] max-h-[72vh]">
            <SpicyLyricsView />
          </div>
        )}
      </div>
    </div>
  );
}
