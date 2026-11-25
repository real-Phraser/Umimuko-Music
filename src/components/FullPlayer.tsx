import { useEffect, useState } from "react";
import { resolveArtworkUrl } from "../api/tauri";
import appIconUrl from "../assets/branding/window-icon.png";
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
  const queue = usePlayerStore((s) => s.queue);

  const setFullPlayerOpen = usePlayerStore((s) => s.setFullPlayerOpen);
  const setFullPlayerTab = usePlayerStore((s) => s.setFullPlayerTab);
  const setQueueDrawerOpen = usePlayerStore((s) => s.setQueueDrawerOpen);
  const togglePlayPause = usePlayerStore((s) => s.togglePlayPause);
  const nextTrack = usePlayerStore((s) => s.nextTrack);
  const prevTrack = usePlayerStore((s) => s.prevTrack);
  const seek = usePlayerStore((s) => s.seek);
  const setVolume = usePlayerStore((s) => s.setVolume);
  const toggleMute = usePlayerStore((s) => s.toggleMute);
  const toggleShuffle = usePlayerStore((s) => s.toggleShuffle);
  const cycleRepeat = usePlayerStore((s) => s.cycleRepeat);

  const playlists = useLibraryStore((s) => s.playlists);
  const toggleLike = useLibraryStore((s) => s.toggleLike);
  const addTrackToPlaylist = useLibraryStore((s) => s.addTrackToPlaylist);
  const createPlaylist = useLibraryStore((s) => s.createPlaylist);
  const openDialog = useLibraryStore((s) => s.openDialog);

  const [showLyrics, setShowLyrics] = useState(false);
  const [playlistMenuOpen, setPlaylistMenuOpen] = useState(false);
  const [newPlaylistName, setNewPlaylistName] = useState("");

  useEffect(() => {
    if (!fullPlayerOpen) return;
    setShowLyrics(fullPlayerTab === "lyrics");
  }, [fullPlayerOpen, fullPlayerTab]);

  useEffect(() => {
    if (!fullPlayerOpen) return;
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        if (playlistMenuOpen) {
          setPlaylistMenuOpen(false);
        } else if (
          !usePlayerStore.getState().queueDrawerOpen &&
          !useLibraryStore.getState().activeDialog
        ) {
          setFullPlayerOpen(false);
        }
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [fullPlayerOpen, playlistMenuOpen, setFullPlayerOpen]);

  if (!fullPlayerOpen) return null;

  const track = playback.currentTrack;
  const isPlaying = playback.status === "playing";
  const duration = playback.durationMs || track?.durationMs || 0;
  const progressPct =
    duration > 0 ? Math.min(100, (playback.positionMs / duration) * 100) : 0;
  const volumePct = Math.round((playback.muted ? 0 : playback.volume) * 100);
  const coverUrl = resolveArtworkUrl(track?.artworkPath);
  const accent = track?.accentColor || "var(--accent)";
  const hasQueue = queue.items.length > 0;

  const handleToggleLyrics = () => {
    const next = !showLyrics;
    setShowLyrics(next);
    setFullPlayerTab(next ? "lyrics" : "artwork");
  };

  const renderMetadata = () => (
    <div className="w-full max-w-[680px] mx-auto flex flex-col items-center text-center gap-1">
      <h2 className="w-full text-2xl sm:text-[26px] font-bold truncate leading-tight">
        {track?.title ?? "Pick something to play"}
      </h2>
      <p className="w-full text-base truncate" style={{ color: "var(--text-primary)" }}>
        {track ? track.artist || "Unknown artist" : ""}
      </p>
      {track?.album && (
        <p
          className="w-full text-sm truncate"
          style={{ color: "var(--text-secondary)" }}
        >
          {track.album}
        </p>
      )}
    </div>
  );

  const renderScrubberAndControls = () => (
    <div className="w-full max-w-[720px] mx-auto flex flex-col">
      {/* Seek Bar */}
      <div className="flex items-center gap-2.5">
        <span
          className="w-11 text-right text-xs tabular-nums shrink-0"
          style={{ color: "var(--text-primary)" }}
        >
          {formatDuration(playback.positionMs)}
        </span>
        <input
          type="range"
          min={0}
          max={Math.max(1, duration)}
          value={Math.min(playback.positionMs, duration)}
          disabled={!track || duration <= 0}
          onChange={(e) => void seek(Number(e.target.value))}
          className="umimuko-slider flex-1"
          style={{
            background: `linear-gradient(90deg, var(--accent) ${progressPct}%, rgba(255,255,255,0.16) ${progressPct}%)`,
            height: 4,
            borderRadius: 999,
          }}
        />
        <span
          className="w-11 text-xs tabular-nums shrink-0"
          style={{ color: "var(--text-primary)" }}
        >
          {formatDuration(duration)}
        </span>
      </div>

      {/* Transport Row */}
      <div className="h-[72px] mt-1 flex items-center justify-between gap-2">
        {/* Left: Shuffle, Repeat, Heart */}
        <div className="flex items-center">
          <button
            type="button"
            disabled={!track}
            onClick={() => void toggleShuffle()}
            className="relative w-11 h-11 rounded-full flex items-center justify-center hover:bg-white/10 disabled:opacity-40 cursor-pointer"
            style={{
              color: playback.shuffle ? "var(--accent)" : "var(--text-primary)",
            }}
            title={playback.shuffle ? "Shuffle on" : "Shuffle off"}
          >
            <Icon name="shuffle" size={20} />
            {playback.shuffle && (
              <span
                className="absolute bottom-1.5 w-1 h-1 rounded-full"
                style={{ backgroundColor: "var(--accent)" }}
              />
            )}
          </button>

          <button
            type="button"
            disabled={!track}
            onClick={() => void cycleRepeat()}
            className="relative w-11 h-11 rounded-full flex items-center justify-center hover:bg-white/10 disabled:opacity-40 cursor-pointer"
            style={{
              color:
                playback.repeat !== "off" ? "var(--accent)" : "var(--text-primary)",
            }}
            title={`Repeat ${playback.repeat}`}
          >
            <Icon
              name={playback.repeat === "one" ? "repeat_one" : "repeat"}
              size={20}
            />
            {playback.repeat !== "off" && (
              <span
                className="absolute bottom-1.5 w-1 h-1 rounded-full"
                style={{ backgroundColor: "var(--accent)" }}
              />
            )}
          </button>

          <button
            type="button"
            disabled={!track}
            onClick={() => track && void toggleLike(track.id)}
            className="w-11 h-11 rounded-full flex items-center justify-center hover:bg-white/10 disabled:opacity-40 cursor-pointer"
            style={{
              color: track?.liked ? "var(--accent)" : "var(--text-primary)",
            }}
            title={track?.liked ? "Unlike" : "Like"}
          >
            <Icon name={track?.liked ? "heart" : "liked"} size={20} />
          </button>
        </div>

        {/* Center: Prev, Play/Pause, Next */}
        <div className="flex items-center">
          <button
            type="button"
            disabled={!hasQueue}
            onClick={() => void prevTrack()}
            className="w-11 h-11 rounded-full flex items-center justify-center hover:bg-white/10 disabled:opacity-40 cursor-pointer"
            title="Previous track"
          >
            <Icon name="previous" size={24} />
          </button>

          <button
            type="button"
            disabled={!track}
            onClick={() => void togglePlayPause()}
            className="mx-4 w-16 h-16 rounded-full flex items-center justify-center shadow-xl disabled:opacity-40 cursor-pointer transition-transform active:scale-95"
            style={{
              backgroundColor: "var(--accent)",
              color: "var(--on-accent)",
            }}
            title={isPlaying ? "Pause" : "Play"}
          >
            <Icon name={isPlaying ? "pause" : "play"} size={30} />
          </button>

          <button
            type="button"
            disabled={!hasQueue}
            onClick={() => void nextTrack()}
            className="w-11 h-11 rounded-full flex items-center justify-center hover:bg-white/10 disabled:opacity-40 cursor-pointer"
            title="Next track"
          >
            <Icon name="next" size={24} />
          </button>
        </div>

        {/* Right: Add to playlist, Sleep timer, Queue */}
        <div className="flex items-center relative">
          <div className="relative">
            <button
              type="button"
              disabled={!track}
              onClick={() => setPlaylistMenuOpen((v) => !v)}
              className="w-11 h-11 rounded-full flex items-center justify-center hover:bg-white/10 disabled:opacity-40 cursor-pointer"
              title="Add to playlist"
            >
              <Icon name="playlist_add" size={20} />
            </button>

            {playlistMenuOpen && track && (
              <div
                className="absolute right-0 bottom-12 z-30 w-60 p-2 rounded-2xl shadow-2xl border"
                style={{
                  backgroundColor: "var(--surface-2)",
                  borderColor: "var(--outline-strong)",
                }}
              >
                <div
                  className="px-2.5 py-1.5 text-[11px] font-semibold uppercase tracking-wider"
                  style={{ color: "var(--text-secondary)" }}
                >
                  Add to playlist
                </div>
                <div className="max-h-44 overflow-y-auto space-y-0.5">
                  {playlists.map((pl) => (
                    <button
                      key={pl.id}
                      type="button"
                      onClick={() => {
                        void addTrackToPlaylist(pl.id, track.id);
                        setPlaylistMenuOpen(false);
                      }}
                      className="w-full px-2.5 py-2 rounded-xl text-left text-xs hover:bg-[var(--surface-variant)] truncate cursor-pointer"
                    >
                      {pl.name}
                    </button>
                  ))}
                </div>
                <form
                  onSubmit={(e) => {
                    e.preventDefault();
                    const trimmed = newPlaylistName.trim();
                    if (!trimmed) return;
                    void createPlaylist(trimmed, [track.id]).then(() => {
                      setNewPlaylistName("");
                      setPlaylistMenuOpen(false);
                    });
                  }}
                  className="mt-1.5 pt-1.5 border-t flex items-center gap-1.5"
                  style={{ borderColor: "var(--outline)" }}
                >
                  <input
                    type="text"
                    value={newPlaylistName}
                    onChange={(e) => setNewPlaylistName(e.target.value)}
                    placeholder="New playlist…"
                    className="flex-1 min-w-0 px-2.5 py-1.5 rounded-lg text-xs outline-none"
                    style={{
                      backgroundColor: "var(--surface-1)",
                      border: "1px solid var(--outline)",
                    }}
                  />
                  <button
                    type="submit"
                    className="px-2.5 py-1.5 rounded-lg text-xs font-medium cursor-pointer"
                    style={{
                      backgroundColor: "var(--accent)",
                      color: "var(--on-accent)",
                    }}
                  >
                    Add
                  </button>
                </form>
              </div>
            )}
          </div>

          {playback.sleepTimerRemainingMs !== null ? (
            <button
              type="button"
              onClick={() => openDialog("sleepTimer")}
              className="mx-1 px-2.5 py-1.5 rounded-xl flex items-center gap-1.5 text-xs font-bold tabular-nums cursor-pointer"
              style={{
                backgroundColor: "var(--accent-container)",
                color: "var(--on-accent-container)",
              }}
              title="Sleep timer"
            >
              <Icon name="timer" size={16} style={{ color: "var(--accent)" }} />
              <span>{formatDuration(playback.sleepTimerRemainingMs)}</span>
            </button>
          ) : (
            <button
              type="button"
              onClick={() => openDialog("sleepTimer")}
              className="w-11 h-11 rounded-full flex items-center justify-center hover:bg-white/10 cursor-pointer"
              title="Sleep timer"
            >
              <Icon name="timer" size={20} />
            </button>
          )}

          <button
            type="button"
            onClick={() => setQueueDrawerOpen(true)}
            className="w-11 h-11 rounded-full flex items-center justify-center hover:bg-white/10 cursor-pointer"
            title="Playback queue"
          >
            <Icon name="queue" size={20} />
          </button>
        </div>
      </div>

      {/* Centered Volume Row */}
      <div className="mt-1 flex items-center justify-center gap-2.5">
        <button
          type="button"
          onClick={() => void toggleMute()}
          className="w-9 flex justify-end cursor-pointer"
          style={{ color: "var(--text-secondary)" }}
          title={playback.muted ? "Unmute" : "Mute"}
        >
          <Icon name={volumePct === 0 ? "muted" : "volume"} size={18} />
        </button>
        <input
          type="range"
          min={0}
          max={1}
          step={0.01}
          value={playback.muted ? 0 : playback.volume}
          onChange={(e) => void setVolume(Number(e.target.value))}
          className="umimuko-slider w-[140px]"
          style={{
            background: `linear-gradient(90deg, var(--accent) ${volumePct}%, rgba(255,255,255,0.16) ${volumePct}%)`,
            height: 4,
            borderRadius: 999,
          }}
          aria-label="Volume"
        />
        <span
          className="w-9 text-xs tabular-nums"
          style={{ color: "var(--text-primary)" }}
        >
          {volumePct}%
        </span>
      </div>
    </div>
  );

  return (
    <div
      className="fixed inset-0 z-40 flex flex-col overflow-hidden adaptive-transition"
      style={{ backgroundColor: "var(--surface-0)" }}
      onClick={() => {
        if (playlistMenuOpen) setPlaylistMenuOpen(false);
      }}
    >
      {/* Ambient Background Layers */}
      {coverUrl && (
        <div
          className="absolute inset-0 pointer-events-none opacity-25 scale-150 blur-[90px]"
          style={{
            backgroundImage: `url(${coverUrl})`,
            backgroundSize: "cover",
            backgroundPosition: "center",
          }}
        />
      )}
      <div
        className="absolute inset-0 pointer-events-none"
        style={{
          background: `radial-gradient(ellipse 80% 60% at 20% 20%, ${accent}26, transparent 70%), radial-gradient(ellipse 75% 55% at 80% 78%, ${accent}1f, transparent 70%), linear-gradient(180deg, rgba(16,18,24,0.65) 0%, rgba(16,18,24,0.82) 100%)`,
        }}
      />

      {/* Foreground Content */}
      <div className="relative z-10 flex-1 min-h-0 flex flex-col p-7 gap-4">
        {/* Top Header Bar */}
        <header className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <img
              src={appIconUrl}
              alt="Umimuko Music"
              className="w-9 h-9 rounded-xl object-contain"
              draggable={false}
            />
            <span
              className="text-xs font-bold tracking-wider uppercase"
              style={{ color: "var(--accent)" }}
            >
              NOW PLAYING
            </span>
          </div>

          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={handleToggleLyrics}
              className="w-10 h-10 rounded-full flex items-center justify-center hover:bg-white/10 cursor-pointer"
              style={{
                color: showLyrics ? "var(--accent)" : "var(--text-primary)",
              }}
              title="Toggle lyrics"
            >
              <Icon name="lyrics" size={20} />
            </button>

            <button
              type="button"
              onClick={() => setFullPlayerOpen(false)}
              className="px-3 py-2 rounded-full flex items-center gap-2 text-sm font-medium hover:bg-white/10 cursor-pointer"
              style={{ color: "var(--accent)" }}
            >
              <Icon name="collapse" size={18} />
              <span>Back</span>
            </button>
          </div>
        </header>

        {/* Main View: Split Lyrics vs Centered Now Playing */}
        {showLyrics ? (
          <div className="flex-1 min-h-0 grid grid-cols-1 lg:grid-cols-12 gap-8 items-center">
            {/* Left Pane on Desktop (hidden on narrow, replaced by bottom controls) */}
            <div className="hidden lg:flex lg:col-span-5 h-full flex-col items-center justify-center gap-3.5">
              <div className="w-[min(36vw,340px)] aspect-square shadow-2xl rounded-3xl">
                <Artwork
                  artworkPath={track?.artworkPath}
                  accentColor={track?.accentColor}
                  alt={track?.title ?? "Album art"}
                  size="full"
                  rounded="rounded-3xl"
                />
              </div>
              {renderMetadata()}
              {renderScrubberAndControls()}
            </div>

            {/* Right Pane: SpicyLyrics */}
            <div className="lg:col-span-7 h-full min-h-0 flex flex-col">
              <div className="flex-1 min-h-0">
                <SpicyLyricsView />
              </div>
              <div className="lg:hidden pt-3">{renderScrubberAndControls()}</div>
            </div>
          </div>
        ) : (
          <div className="flex-1 min-h-0 flex flex-col items-center justify-between gap-4">
            <div className="flex-1 min-h-0 w-full flex items-center justify-center">
              <div className="w-[min(46vh,420px)] max-w-full aspect-square shadow-2xl rounded-3xl">
                <Artwork
                  artworkPath={track?.artworkPath}
                  accentColor={track?.accentColor}
                  alt={track?.title ?? "Album art"}
                  size="full"
                  rounded="rounded-3xl"
                />
              </div>
            </div>

            {renderMetadata()}
            {renderScrubberAndControls()}
          </div>
        )}
      </div>
    </div>
  );
}
