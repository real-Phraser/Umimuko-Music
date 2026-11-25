export type RepeatMode = "off" | "all" | "one";
export type PlaybackStatus = "stopped" | "playing" | "paused";
export type ThemeMode = "dark" | "light" | "system";

export interface Track {
  id: string;
  path: string;
  title: string;
  artist: string;
  album: string;
  albumArtist: string;
  genre: string;
  year: number | null;
  trackNum: number | null;
  durationMs: number;
  artworkPath: string | null;
  accentColor: string | null;
  addedAt: number;
  fileSize: number;
  modifiedAt: number;
  liked: boolean;
  playCount: number;
  lastPlayedAt: number | null;
}

export interface PlaybackState {
  status: PlaybackStatus;
  currentTrackId: string | null;
  currentTrack: Track | null;
  positionMs: number;
  durationMs: number;
  volume: number;
  muted: boolean;
  shuffle: boolean;
  repeat: RepeatMode;
  sleepTimerRemainingMs: number | null;
}

export interface QueueSnapshot {
  items: Track[];
  currentIndex: number;
  shuffle: boolean;
  repeat: RepeatMode;
}

export const EQ_FREQUENCIES = [
  60, 170, 310, 600, 1000, 3000, 6000, 12000, 14000, 16000,
] as const;

export interface EqualizerConfig {
  enabled: boolean;
  preampDb: number;
  bands: number[]; // 10 bands in dB (-12 to +12)
  presetName: string | null;
}

export interface EqualizerPreset {
  id: string;
  name: string;
  preampDb: number;
  bands: number[];
  builtIn?: boolean;
}

export interface CrossfadeConfig {
  enabled: boolean;
  durationMs: number;
  gapless: boolean;
}

export interface Playlist {
  id: string;
  name: string;
  trackIds: string[];
  createdAt: number;
  updatedAt: number;
  pinned?: boolean;
}

export interface ListeningStats {
  totalPlayTimeMs: number;
  totalPlays: number;
  tracksCount: number;
  topTracks: Array<{
    track: Track;
    playCount: number;
    totalMs: number;
  }>;
  topArtists: Array<{
    artist: string;
    playCount: number;
    totalMs: number;
  }>;
  topAlbums: Array<{
    album: string;
    artist: string;
    artworkPath: string | null;
    accentColor: string | null;
    playCount: number;
  }>;
  recentPlays: Array<{
    track: Track;
    playedAt: number;
  }>;
}

export interface LyricWord {
  text: string;
  startMs: number;
  endMs: number;
  isBackground?: boolean;
}

export interface LyricLine {
  startMs: number;
  endMs: number;
  text: string;
  words?: LyricWord[];
  romanized?: string;
  isInstrumental?: boolean;
}

export interface SpicyLyrics {
  trackId: string;
  type: "syllable" | "line" | "plain" | "none";
  source: string;
  lines: LyricLine[];
}

export interface DuplicateGroup {
  id: string;
  reason: "exact_hash" | "metadata_duration";
  tracks: Track[];
}

export interface KeybindingsConfig {
  playPause: string;
  nextTrack: string;
  prevTrack: string;
  volumeUp: string;
  volumeDown: string;
  toggleMute: string;
  toggleLyrics: string;
  toggleQueue: string;
  focusSearch: string;
}

export interface AppSettings {
  theme: ThemeMode;
  adaptiveAccent: boolean;
  folders: string[];
  spicyLyricsApiKey: string;
  crossfade: CrossfadeConfig;
  equalizer: EqualizerConfig;
  keybindings: KeybindingsConfig;
  minimizeToTray: boolean;
  showMiniPlayerOnClose: boolean;
}

export interface LibraryScanProgress {
  scanning: boolean;
  scanned: number;
  total: number;
  currentFile?: string;
}

export interface TagUpdateInput {
  trackId: string;
  title: string;
  artist: string;
  album: string;
  albumArtist: string;
  genre: string;
  year: number | null;
  trackNum: number | null;
}

export interface PlaybackTickPayload {
  positionMs: number;
  durationMs: number;
}
