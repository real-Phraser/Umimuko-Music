import { convertFileSrc, invoke } from "@tauri-apps/api/core";
import { listen, type UnlistenFn } from "@tauri-apps/api/event";
import { getCurrentWebviewWindow } from "@tauri-apps/api/webviewWindow";
import type {
  AppSettings,
  CrossfadeConfig,
  DuplicateGroup,
  EqualizerConfig,
  EqualizerPreset,
  LibraryScanProgress,
  ListeningStats,
  PlaybackState,
  PlaybackTickPayload,
  Playlist,
  QueueSnapshot,
  RepeatMode,
  SpicyLyrics,
  TagUpdateInput,
  Track,
} from "../types/ipc";

export function isTauriRuntime(): boolean {
  return typeof window !== "undefined" && "__TAURI_INTERNALS__" in window;
}

export function getWindowLabel(): "main" | "mini-player" {
  if (typeof window !== "undefined") {
    const params = new URLSearchParams(window.location.search);
    if (params.get("window") === "mini-player") {
      return "mini-player";
    }
  }
  if (isTauriRuntime()) {
    try {
      const label = getCurrentWebviewWindow().label;
      if (label === "mini-player") return "mini-player";
    } catch {
      // Fallback to main
    }
  }
  return "main";
}

export function resolveArtworkUrl(path: string | null | undefined): string | null {
  if (!path) return null;
  if (
    path.startsWith("http://") ||
    path.startsWith("https://") ||
    path.startsWith("data:") ||
    path.startsWith("blob:") ||
    path.startsWith("asset:")
  ) {
    return path;
  }
  if (isTauriRuntime()) {
    try {
      return convertFileSrc(path);
    } catch {
      return null;
    }
  }
  return null;
}

// ---------------------------------------------------------------------------
// Built-in EQ presets
// ---------------------------------------------------------------------------

export const BUILT_IN_EQ_PRESETS: EqualizerPreset[] = [
  { id: "flat", name: "Flat", preampDb: 0, bands: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0], builtIn: true },
  { id: "bass-boost", name: "Bass Boost", preampDb: -1.5, bands: [6, 5, 4, 2, 0, 0, 0, 1, 2, 2], builtIn: true },
  { id: "vocal", name: "Vocal Clarity", preampDb: 0, bands: [-2, -1, 0, 2, 4, 5, 4, 2, 0, -1], builtIn: true },
  { id: "electronic", name: "Electronic", preampDb: -1, bands: [4.5, 3.5, 1.5, 0, -1.5, 1, 2.5, 4, 4.5, 5], builtIn: true },
  { id: "acoustic", name: "Acoustic", preampDb: 0, bands: [2, 2, 1, 0, 1, 2, 3, 2.5, 2, 1.5], builtIn: true },
  { id: "rock", name: "Rock", preampDb: -1, bands: [4, 3, 1.5, -1, -1.5, 0.5, 2.5, 4, 4.5, 4], builtIn: true },
  { id: "late-night", name: "Late Night", preampDb: 1, bands: [-3, -2, 0, 1.5, 2, 2, 1.5, 0, -2, -3], builtIn: true },
];

export const DEFAULT_APP_SETTINGS: AppSettings = {
  theme: "dark",
  adaptiveAccent: true,
  folders: [],
  spicyLyricsApiKey: "",
  crossfade: {
    enabled: false,
    durationMs: 3000,
    gapless: true,
  },
  equalizer: {
    enabled: false,
    preampDb: 0,
    bands: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
    presetName: "Flat",
  },
  keybindings: {
    playPause: "Space",
    nextTrack: "ArrowRight",
    prevTrack: "ArrowLeft",
    volumeUp: "ArrowUp",
    volumeDown: "ArrowDown",
    toggleMute: "KeyM",
    toggleLyrics: "KeyL",
    toggleQueue: "KeyQ",
    focusSearch: "KeyF",
  },
  minimizeToTray: false,
  showMiniPlayerOnClose: false,
};

// ---------------------------------------------------------------------------
// Browser Mock Engine (for plain `vite` dev mode)
// ---------------------------------------------------------------------------

type Listener<T> = (payload: T) => void;

class MockEmitter {
  private listeners = new Map<string, Set<Listener<unknown>>>();

  on<T>(event: string, cb: Listener<T>): UnlistenFn {
    let set = this.listeners.get(event);
    if (!set) {
      set = new Set();
      this.listeners.set(event, set);
    }
    set.add(cb as Listener<unknown>);
    return () => {
      set?.delete(cb as Listener<unknown>);
    };
  }

  emit<T>(event: string, payload: T) {
    const set = this.listeners.get(event);
    if (!set) return;
    for (const cb of set) {
      cb(payload);
    }
  }
}

const mockEmitter = new MockEmitter();

const now = Date.now();
const MOCK_TRACKS: Track[] = [
  {
    id: "trk-1",
    path: "/Users/Music/Yorushika/Plagiarism/01-Thoughtcrime.flac",
    title: "Thoughtcrime",
    artist: "Yorushika",
    album: "Plagiarism",
    albumArtist: "Yorushika",
    genre: "J-Rock",
    year: 2020,
    trackNum: 1,
    durationMs: 252000,
    artworkPath: null,
    accentColor: "#6384ff",
    addedAt: now - 86400_000 * 14,
    fileSize: 31_457_280,
    modifiedAt: now - 86400_000 * 14,
    liked: true,
    playCount: 42,
    lastPlayedAt: now - 3600_000 * 2,
  },
  {
    id: "trk-2",
    path: "/Users/Music/Porter Robinson/Nurture/02-Look at the Sky.flac",
    title: "Look at the Sky",
    artist: "Porter Robinson",
    album: "Nurture",
    albumArtist: "Porter Robinson",
    genre: "Electronic",
    year: 2021,
    trackNum: 2,
    durationMs: 310000,
    artworkPath: null,
    accentColor: "#4ade80",
    addedAt: now - 86400_000 * 10,
    fileSize: 38_100_000,
    modifiedAt: now - 86400_000 * 10,
    liked: true,
    playCount: 37,
    lastPlayedAt: now - 3600_000 * 5,
  },
  {
    id: "trk-3",
    path: "/Users/Music/Daft Punk/Random Access Memories/08-Get Lucky.flac",
    title: "Instant Crush (feat. Julian Casablancas)",
    artist: "Daft Punk",
    album: "Random Access Memories",
    albumArtist: "Daft Punk",
    genre: "Synthpop",
    year: 2013,
    trackNum: 5,
    durationMs: 337000,
    artworkPath: null,
    accentColor: "#f59e0b",
    addedAt: now - 86400_000 * 8,
    fileSize: 42_500_000,
    modifiedAt: now - 86400_000 * 8,
    liked: false,
    playCount: 28,
    lastPlayedAt: now - 86400_000,
  },
  {
    id: "trk-4",
    path: "/Users/Music/Nujabes/Modal Soul/01-Feather.mp3",
    title: "Feather (feat. Cise Starr & Akin)",
    artist: "Nujabes",
    album: "Modal Soul",
    albumArtist: "Nujabes",
    genre: "Jazz Hop",
    year: 2005,
    trackNum: 1,
    durationMs: 175000,
    artworkPath: null,
    accentColor: "#ec4899",
    addedAt: now - 86400_000 * 5,
    fileSize: 7_200_000,
    modifiedAt: now - 86400_000 * 5,
    liked: true,
    playCount: 54,
    lastPlayedAt: now - 3600_000 * 9,
  },
  {
    id: "trk-5",
    path: "/Users/Music/Kenshi Yonezu/STRAY SHEEP/03-Lemon.flac",
    title: "Kanden",
    artist: "Kenshi Yonezu",
    album: "STRAY SHEEP",
    albumArtist: "Kenshi Yonezu",
    genre: "J-Pop",
    year: 2020,
    trackNum: 3,
    durationMs: 263000,
    artworkPath: null,
    accentColor: "#38bdf8",
    addedAt: now - 86400_000 * 2,
    fileSize: 29_400_000,
    modifiedAt: now - 86400_000 * 2,
    liked: false,
    playCount: 15,
    lastPlayedAt: now - 86400_000 * 2,
  },
  {
    id: "trk-6",
    path: "/Users/Music/Nujabes/Modal Soul/01-Feather-copy.mp3",
    title: "Feather (feat. Cise Starr & Akin)",
    artist: "Nujabes",
    album: "Modal Soul",
    albumArtist: "Nujabes",
    genre: "Jazz Hop",
    year: 2005,
    trackNum: 1,
    durationMs: 175000,
    artworkPath: null,
    accentColor: "#ec4899",
    addedAt: now - 86400_000,
    fileSize: 7_200_000,
    modifiedAt: now - 86400_000,
    liked: false,
    playCount: 2,
    lastPlayedAt: null,
  },
];

let mockTracks: Track[] = [...MOCK_TRACKS];
let mockPlaylists: Playlist[] = [
  {
    id: "pl-1",
    name: "Late Night Focus",
    trackIds: ["trk-1", "trk-2", "trk-4"],
    createdAt: now - 86400_000 * 12,
    updatedAt: now - 86400_000 * 2,
    pinned: true,
  },
  {
    id: "pl-2",
    name: "Weekend Drive",
    trackIds: ["trk-3", "trk-5"],
    createdAt: now - 86400_000 * 6,
    updatedAt: now - 86400_000,
    pinned: true,
  },
];
let mockPinnedTrackIds: string[] = ["trk-1", "trk-2", "trk-4"];
let mockSettings: AppSettings = {
  ...DEFAULT_APP_SETTINGS,
  folders: ["/Users/Music"],
};
let mockEqPresets: EqualizerPreset[] = [...BUILT_IN_EQ_PRESETS];
let mockQueue: QueueSnapshot = {
  items: [...mockTracks.slice(0, 5)],
  currentIndex: 0,
  shuffle: false,
  repeat: "off",
};
let mockPlayback: PlaybackState = {
  status: "paused",
  currentTrackId: mockQueue.items[0]?.id ?? null,
  currentTrack: mockQueue.items[0] ?? null,
  positionMs: 14500,
  durationMs: mockQueue.items[0]?.durationMs ?? 0,
  volume: 0.8,
  muted: false,
  shuffle: false,
  repeat: "off",
  sleepTimerRemainingMs: null,
};

let mockTickTimer: number | null = null;
function ensureMockTicker() {
  if (mockTickTimer !== null || typeof window === "undefined") return;
  mockTickTimer = window.setInterval(() => {
    if (mockPlayback.status === "playing" && mockPlayback.currentTrack) {
      mockPlayback.positionMs += 250;
      if (mockPlayback.positionMs >= mockPlayback.durationMs) {
        if (mockPlayback.repeat === "one") {
          mockPlayback.positionMs = 0;
        } else if (mockQueue.currentIndex < mockQueue.items.length - 1) {
          mockQueue.currentIndex += 1;
          const next = mockQueue.items[mockQueue.currentIndex];
          mockPlayback.currentTrack = next;
          mockPlayback.currentTrackId = next.id;
          mockPlayback.positionMs = 0;
          mockPlayback.durationMs = next.durationMs;
          mockEmitter.emit("queue:updated", { ...mockQueue });
          mockEmitter.emit("playback:state", { ...mockPlayback });
        } else if (mockPlayback.repeat === "all" && mockQueue.items.length > 0) {
          mockQueue.currentIndex = 0;
          const next = mockQueue.items[0];
          mockPlayback.currentTrack = next;
          mockPlayback.currentTrackId = next.id;
          mockPlayback.positionMs = 0;
          mockPlayback.durationMs = next.durationMs;
          mockEmitter.emit("queue:updated", { ...mockQueue });
          mockEmitter.emit("playback:state", { ...mockPlayback });
        } else {
          mockPlayback.status = "stopped";
          mockPlayback.positionMs = 0;
          mockEmitter.emit("playback:state", { ...mockPlayback });
        }
      }
      mockEmitter.emit<PlaybackTickPayload>("playback:tick", {
        positionMs: mockPlayback.positionMs,
        durationMs: mockPlayback.durationMs,
      });
    }
    if (mockPlayback.sleepTimerRemainingMs !== null) {
      mockPlayback.sleepTimerRemainingMs = Math.max(0, mockPlayback.sleepTimerRemainingMs - 250);
      if (mockPlayback.sleepTimerRemainingMs === 0) {
        mockPlayback.sleepTimerRemainingMs = null;
        mockPlayback.status = "paused";
        mockEmitter.emit("playback:state", { ...mockPlayback });
      }
    }
  }, 250);
}

function buildMockLyrics(trackId: string): SpicyLyrics {
  const track = mockTracks.find((t) => t.id === trackId) ?? mockTracks[0];
  const sampleLines = [
    {
      startMs: 4000,
      endMs: 9200,
      text: "Midnight neon reflects across the quiet glass",
      romanized: "Shinya no neon ga shizuka na garasu ni utsuru",
      words: [
        { text: "Midnight ", startMs: 4000, endMs: 4900 },
        { text: "neon ", startMs: 4900, endMs: 5700 },
        { text: "reflects ", startMs: 5700, endMs: 6700 },
        { text: "across ", startMs: 6700, endMs: 7500 },
        { text: "the ", startMs: 7500, endMs: 7900 },
        { text: "quiet ", startMs: 7900, endMs: 8550 },
        { text: "glass", startMs: 8550, endMs: 9200 },
      ],
    },
    {
      startMs: 9600,
      endMs: 14800,
      text: "Every chord remembers where the summer passed",
      words: [
        { text: "Every ", startMs: 9600, endMs: 10300 },
        { text: "chord ", startMs: 10300, endMs: 11100 },
        { text: "remembers ", startMs: 11100, endMs: 12400 },
        { text: "where ", startMs: 12400, endMs: 12950 },
        { text: "the ", startMs: 12950, endMs: 13300 },
        { text: "summer ", startMs: 13300, endMs: 14100 },
        { text: "passed", startMs: 14100, endMs: 14800 },
      ],
    },
    {
      startMs: 15200,
      endMs: 21000,
      text: "Hold the rhythm steady till the morning light arrives",
      words: [
        { text: "Hold ", startMs: 15200, endMs: 15800 },
        { text: "the ", startMs: 15800, endMs: 16200 },
        { text: "rhythm ", startMs: 16200, endMs: 17100 },
        { text: "steady ", startMs: 17100, endMs: 18000 },
        { text: "till ", startMs: 18000, endMs: 18500 },
        { text: "the ", startMs: 18500, endMs: 18900 },
        { text: "morning ", startMs: 18900, endMs: 19700 },
        { text: "light ", startMs: 19700, endMs: 20300 },
        { text: "arrives", startMs: 20300, endMs: 21000 },
      ],
    },
    {
      startMs: 21500,
      endMs: 27000,
      text: `Spinning ${track?.title ?? "melodies"} in stereo tonight`,
      words: [
        { text: "Spinning ", startMs: 21500, endMs: 22400 },
        { text: `${track?.title ?? "melodies"} `, startMs: 22400, endMs: 24100 },
        { text: "in ", startMs: 24100, endMs: 24600 },
        { text: "stereo ", startMs: 24600, endMs: 25900 },
        { text: "tonight", startMs: 25900, endMs: 27000 },
      ],
    },
    {
      startMs: 28000,
      endMs: 34000,
      text: "•••",
      isInstrumental: true,
    },
    {
      startMs: 34500,
      endMs: 41000,
      text: "Let the bassline carry everything we left unsaid",
      words: [
        { text: "Let ", startMs: 34500, endMs: 35100 },
        { text: "the ", startMs: 35100, endMs: 35500 },
        { text: "bassline ", startMs: 35500, endMs: 36700 },
        { text: "carry ", startMs: 36700, endMs: 37700 },
        { text: "everything ", startMs: 37700, endMs: 39100 },
        { text: "we ", startMs: 39100, endMs: 39600 },
        { text: "left ", startMs: 39600, endMs: 40300 },
        { text: "unsaid", startMs: 40300, endMs: 41000 },
      ],
    },
  ];
  return {
    trackId,
    type: "syllable",
    source: "SpicyLyrics (Preview)",
    lines: sampleLines,
  };
}

// ---------------------------------------------------------------------------
// Unified IPC API with Tauri + Browser Mock fallback
// ---------------------------------------------------------------------------

async function callIpc<T>(cmd: string, args?: Record<string, unknown>, fallback?: () => T | Promise<T>): Promise<T> {
  if (isTauriRuntime()) {
    try {
      return await invoke<T>(cmd, args);
    } catch (err) {
      if (fallback) {
        console.warn(`[IPC fallback] ${cmd}:`, err);
        return await fallback();
      }
      throw err;
    }
  }
  ensureMockTicker();
  if (!fallback) {
    throw new Error(`No mock fallback for ${cmd}`);
  }
  return await fallback();
}

export const api = {
  // Library
  getTracks: () =>
    callIpc<Track[]>("get_tracks", undefined, () => [...mockTracks]),

  scanFolders: (folders?: string[]) =>
    callIpc<Track[]>("scan_folders", { folders }, async () => {
      mockEmitter.emit<LibraryScanProgress>("library:progress", {
        scanning: true,
        scanned: 0,
        total: mockTracks.length,
        currentFile: "Scanning library...",
      });
      await new Promise((r) => setTimeout(r, 350));
      mockEmitter.emit<LibraryScanProgress>("library:progress", {
        scanning: false,
        scanned: mockTracks.length,
        total: mockTracks.length,
      });
      return [...mockTracks];
    }),

  addFolder: (path: string) =>
    callIpc<string[]>("add_folder", { path }, () => {
      if (!mockSettings.folders.includes(path)) {
        mockSettings = { ...mockSettings, folders: [...mockSettings.folders, path] };
      }
      return mockSettings.folders;
    }),

  removeFolder: (path: string) =>
    callIpc<string[]>("remove_folder", { path }, () => {
      mockSettings = {
        ...mockSettings,
        folders: mockSettings.folders.filter((f) => f !== path),
      };
      return mockSettings.folders;
    }),

  toggleLikeTrack: (trackId: string, liked: boolean) =>
    callIpc<Track>("toggle_like_track", { trackId, liked }, () => {
      mockTracks = mockTracks.map((t) => (t.id === trackId ? { ...t, liked } : t));
      const updated = mockTracks.find((t) => t.id === trackId)!;
      if (mockPlayback.currentTrackId === trackId) {
        mockPlayback = { ...mockPlayback, currentTrack: updated };
        mockEmitter.emit("playback:state", { ...mockPlayback });
      }
      return updated;
    }),

  updateTrackTags: (input: TagUpdateInput) =>
    callIpc<Track>("update_track_tags", { input }, () => {
      mockTracks = mockTracks.map((t) =>
        t.id === input.trackId
          ? {
              ...t,
              title: input.title,
              artist: input.artist,
              album: input.album,
              albumArtist: input.albumArtist,
              genre: input.genre,
              year: input.year,
              trackNum: input.trackNum,
            }
          : t
      );
      const updated = mockTracks.find((t) => t.id === input.trackId)!;
      mockQueue = {
        ...mockQueue,
        items: mockQueue.items.map((t) => (t.id === updated.id ? updated : t)),
      };
      if (mockPlayback.currentTrackId === updated.id) {
        mockPlayback = { ...mockPlayback, currentTrack: updated };
        mockEmitter.emit("playback:state", { ...mockPlayback });
      }
      mockEmitter.emit("queue:updated", { ...mockQueue });
      return updated;
    }),

  deleteTrack: (trackId: string, deleteFile = false) =>
    callIpc<void>("delete_track", { trackId, deleteFile }, () => {
      mockTracks = mockTracks.filter((t) => t.id !== trackId);
      mockQueue = {
        ...mockQueue,
        items: mockQueue.items.filter((t) => t.id !== trackId),
      };
      mockEmitter.emit("queue:updated", { ...mockQueue });
    }),

  // Home / Pins / Playlists
  getPinnedTrackIds: () =>
    callIpc<string[]>("get_pinned_track_ids", undefined, () => [...mockPinnedTrackIds]),

  togglePinTrack: (trackId: string) =>
    callIpc<string[]>("toggle_pin_track", { trackId }, () => {
      mockPinnedTrackIds = mockPinnedTrackIds.includes(trackId)
        ? mockPinnedTrackIds.filter((id) => id !== trackId)
        : [trackId, ...mockPinnedTrackIds];
      return [...mockPinnedTrackIds];
    }),

  getPlaylists: () =>
    callIpc<Playlist[]>("get_playlists", undefined, () => [...mockPlaylists]),

  createPlaylist: (name: string, trackIds: string[] = []) =>
    callIpc<Playlist>("create_playlist", { name, trackIds }, () => {
      const pl: Playlist = {
        id: `pl-${Date.now()}`,
        name,
        trackIds,
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };
      mockPlaylists = [...mockPlaylists, pl];
      return pl;
    }),

  addTrackToPlaylist: (playlistId: string, trackId: string) =>
    callIpc<Playlist>("add_track_to_playlist", { playlistId, trackId }, () => {
      mockPlaylists = mockPlaylists.map((pl) =>
        pl.id === playlistId && !pl.trackIds.includes(trackId)
          ? { ...pl, trackIds: [...pl.trackIds, trackId], updatedAt: Date.now() }
          : pl
      );
      return mockPlaylists.find((p) => p.id === playlistId)!;
    }),

  deletePlaylist: (playlistId: string) =>
    callIpc<void>("delete_playlist", { playlistId }, () => {
      mockPlaylists = mockPlaylists.filter((p) => p.id !== playlistId);
    }),

  // Playback & Queue
  getPlaybackState: () =>
    callIpc<PlaybackState>("get_playback_state", undefined, () => ({ ...mockPlayback })),

  getQueue: () =>
    callIpc<QueueSnapshot>("get_queue", undefined, () => ({ ...mockQueue })),

  playTrack: (trackId: string, queueTrackIds?: string[]) =>
    callIpc<PlaybackState>("play_track", { trackId, queueTrackIds }, () => {
      const track = mockTracks.find((t) => t.id === trackId);
      if (!track) return { ...mockPlayback };
      if (queueTrackIds && queueTrackIds.length > 0) {
        const items = queueTrackIds
          .map((id) => mockTracks.find((t) => t.id === id))
          .filter((t): t is Track => Boolean(t));
        const idx = Math.max(0, items.findIndex((t) => t.id === trackId));
        mockQueue = { ...mockQueue, items, currentIndex: idx };
      } else {
        const existingIdx = mockQueue.items.findIndex((t) => t.id === trackId);
        if (existingIdx >= 0) {
          mockQueue = { ...mockQueue, currentIndex: existingIdx };
        } else {
          mockQueue = {
            ...mockQueue,
            items: [track, ...mockQueue.items],
            currentIndex: 0,
          };
        }
      }
      mockPlayback = {
        ...mockPlayback,
        status: "playing",
        currentTrackId: track.id,
        currentTrack: track,
        positionMs: 0,
        durationMs: track.durationMs,
      };
      mockEmitter.emit("queue:updated", { ...mockQueue });
      mockEmitter.emit("playback:state", { ...mockPlayback });
      return { ...mockPlayback };
    }),

  togglePlayPause: () =>
    callIpc<PlaybackState>("toggle_play_pause", undefined, () => {
      if (!mockPlayback.currentTrack && mockQueue.items.length > 0) {
        const first = mockQueue.items[0];
        mockPlayback.currentTrack = first;
        mockPlayback.currentTrackId = first.id;
        mockPlayback.durationMs = first.durationMs;
      }
      mockPlayback = {
        ...mockPlayback,
        status: mockPlayback.status === "playing" ? "paused" : "playing",
      };
      mockEmitter.emit("playback:state", { ...mockPlayback });
      return { ...mockPlayback };
    }),

  nextTrack: () =>
    callIpc<PlaybackState>("next_track", undefined, () => {
      if (mockQueue.items.length === 0) return { ...mockPlayback };
      const nextIdx = mockPlayback.shuffle
        ? Math.floor(Math.random() * mockQueue.items.length)
        : (mockQueue.currentIndex + 1) % mockQueue.items.length;
      const track = mockQueue.items[nextIdx];
      mockQueue = { ...mockQueue, currentIndex: nextIdx };
      mockPlayback = {
        ...mockPlayback,
        status: "playing",
        currentTrackId: track.id,
        currentTrack: track,
        positionMs: 0,
        durationMs: track.durationMs,
      };
      mockEmitter.emit("queue:updated", { ...mockQueue });
      mockEmitter.emit("playback:state", { ...mockPlayback });
      return { ...mockPlayback };
    }),

  prevTrack: () =>
    callIpc<PlaybackState>("prev_track", undefined, () => {
      if (mockQueue.items.length === 0) return { ...mockPlayback };
      if (mockPlayback.positionMs > 3000) {
        mockPlayback = { ...mockPlayback, positionMs: 0 };
        mockEmitter.emit("playback:tick", {
          positionMs: 0,
          durationMs: mockPlayback.durationMs,
        });
        return { ...mockPlayback };
      }
      const prevIdx =
        (mockQueue.currentIndex - 1 + mockQueue.items.length) % mockQueue.items.length;
      const track = mockQueue.items[prevIdx];
      mockQueue = { ...mockQueue, currentIndex: prevIdx };
      mockPlayback = {
        ...mockPlayback,
        status: "playing",
        currentTrackId: track.id,
        currentTrack: track,
        positionMs: 0,
        durationMs: track.durationMs,
      };
      mockEmitter.emit("queue:updated", { ...mockQueue });
      mockEmitter.emit("playback:state", { ...mockPlayback });
      return { ...mockPlayback };
    }),

  seek: (positionMs: number) =>
    callIpc<void>("seek", { positionMs }, () => {
      mockPlayback = {
        ...mockPlayback,
        positionMs: Math.max(0, Math.min(positionMs, mockPlayback.durationMs)),
      };
      mockEmitter.emit<PlaybackTickPayload>("playback:tick", {
        positionMs: mockPlayback.positionMs,
        durationMs: mockPlayback.durationMs,
      });
    }),

  setVolume: (volume: number) =>
    callIpc<void>("set_volume", { volume }, () => {
      mockPlayback = { ...mockPlayback, volume: Math.max(0, Math.min(1, volume)) };
      mockEmitter.emit("playback:state", { ...mockPlayback });
    }),

  setMuted: (muted: boolean) =>
    callIpc<void>("set_muted", { muted }, () => {
      mockPlayback = { ...mockPlayback, muted };
      mockEmitter.emit("playback:state", { ...mockPlayback });
    }),

  setShuffle: (shuffle: boolean) =>
    callIpc<void>("set_shuffle", { shuffle }, () => {
      mockPlayback = { ...mockPlayback, shuffle };
      mockQueue = { ...mockQueue, shuffle };
      mockEmitter.emit("playback:state", { ...mockPlayback });
      mockEmitter.emit("queue:updated", { ...mockQueue });
    }),

  setRepeat: (repeat: RepeatMode) =>
    callIpc<void>("set_repeat", { repeat }, () => {
      mockPlayback = { ...mockPlayback, repeat };
      mockQueue = { ...mockQueue, repeat };
      mockEmitter.emit("playback:state", { ...mockPlayback });
      mockEmitter.emit("queue:updated", { ...mockQueue });
    }),

  reorderQueue: (fromIndex: number, toIndex: number) =>
    callIpc<QueueSnapshot>("reorder_queue", { fromIndex, toIndex }, () => {
      const items = [...mockQueue.items];
      const [moved] = items.splice(fromIndex, 1);
      if (!moved) return { ...mockQueue };
      items.splice(toIndex, 0, moved);
      const currentId = mockPlayback.currentTrackId;
      const newCurrentIndex = currentId
        ? items.findIndex((t) => t.id === currentId)
        : mockQueue.currentIndex;
      mockQueue = { ...mockQueue, items, currentIndex: Math.max(0, newCurrentIndex) };
      mockEmitter.emit("queue:updated", { ...mockQueue });
      return { ...mockQueue };
    }),

  removeFromQueue: (indices: number[]) =>
    callIpc<QueueSnapshot>("remove_from_queue", { indices }, () => {
      const removeSet = new Set(indices);
      const items = mockQueue.items.filter((_, idx) => !removeSet.has(idx));
      const currentId = mockPlayback.currentTrackId;
      const newCurrentIndex = currentId ? items.findIndex((t) => t.id === currentId) : 0;
      mockQueue = { ...mockQueue, items, currentIndex: Math.max(0, newCurrentIndex) };
      mockEmitter.emit("queue:updated", { ...mockQueue });
      return { ...mockQueue };
    }),

  enqueueTracks: (trackIds: string[], playNext = false) =>
    callIpc<QueueSnapshot>("enqueue_tracks", { trackIds, playNext }, () => {
      const toAdd = trackIds
        .map((id) => mockTracks.find((t) => t.id === id))
        .filter((t): t is Track => Boolean(t));
      const items = [...mockQueue.items];
      if (playNext) {
        items.splice(mockQueue.currentIndex + 1, 0, ...toAdd);
      } else {
        items.push(...toAdd);
      }
      mockQueue = { ...mockQueue, items };
      mockEmitter.emit("queue:updated", { ...mockQueue });
      return { ...mockQueue };
    }),

  setSleepTimer: (minutes: number | null) =>
    callIpc<void>("set_sleep_timer", { minutes }, () => {
      mockPlayback = {
        ...mockPlayback,
        sleepTimerRemainingMs: minutes && minutes > 0 ? minutes * 60_000 : null,
      };
      mockEmitter.emit("playback:state", { ...mockPlayback });
    }),

  // Lyrics
  getLyrics: (trackId: string) =>
    callIpc<SpicyLyrics>("get_lyrics", { trackId }, () => buildMockLyrics(trackId)),

  refreshLyrics: (trackId: string) =>
    callIpc<SpicyLyrics>("refresh_lyrics", { trackId }, () => buildMockLyrics(trackId)),

  // Equalizer & Crossfade
  setEqualizerConfig: (config: EqualizerConfig) =>
    callIpc<void>("set_equalizer_config", { config }, () => {
      mockSettings = { ...mockSettings, equalizer: config };
    }),

  getEqPresets: () =>
    callIpc<EqualizerPreset[]>("get_eq_presets", undefined, () => [...mockEqPresets]),

  saveEqPreset: (preset: Omit<EqualizerPreset, "id">) =>
    callIpc<EqualizerPreset>("save_eq_preset", { preset }, () => {
      const created: EqualizerPreset = { ...preset, id: `eq-${Date.now()}`, builtIn: false };
      mockEqPresets = [...mockEqPresets, created];
      return created;
    }),

  setCrossfadeConfig: (config: CrossfadeConfig) =>
    callIpc<void>("set_crossfade_config", { config }, () => {
      mockSettings = { ...mockSettings, crossfade: config };
    }),

  // Duplicates & Stats
  findDuplicates: () =>
    callIpc<DuplicateGroup[]>("find_duplicates", undefined, () => {
      const nujabes = mockTracks.filter((t) => t.title.startsWith("Feather"));
      if (nujabes.length > 1) {
        return [{ id: "dup-1", reason: "metadata_duration", tracks: nujabes }];
      }
      return [];
    }),

  getListeningStats: () =>
    callIpc<ListeningStats>("get_listening_stats", undefined, () => {
      const sorted = [...mockTracks].sort((a, b) => b.playCount - a.playCount);
      const totalPlays = sorted.reduce((acc, t) => acc + t.playCount, 0);
      const totalPlayTimeMs = sorted.reduce((acc, t) => acc + t.playCount * t.durationMs, 0);
      const artistMap = new Map<string, { playCount: number; totalMs: number }>();
      for (const t of sorted) {
        const cur = artistMap.get(t.artist) ?? { playCount: 0, totalMs: 0 };
        cur.playCount += t.playCount;
        cur.totalMs += t.playCount * t.durationMs;
        artistMap.set(t.artist, cur);
      }
      return {
        totalPlayTimeMs,
        totalPlays,
        tracksCount: mockTracks.length,
        topTracks: sorted.slice(0, 5).map((track) => ({
          track,
          playCount: track.playCount,
          totalMs: track.playCount * track.durationMs,
        })),
        topArtists: [...artistMap.entries()]
          .map(([artist, v]) => ({ artist, ...v }))
          .sort((a, b) => b.playCount - a.playCount)
          .slice(0, 5),
        topAlbums: sorted.slice(0, 4).map((t) => ({
          album: t.album,
          artist: t.albumArtist || t.artist,
          artworkPath: t.artworkPath,
          accentColor: t.accentColor,
          playCount: t.playCount,
        })),
        recentPlays: sorted
          .filter((t) => t.lastPlayedAt)
          .slice(0, 6)
          .map((track) => ({ track, playedAt: track.lastPlayedAt! })),
      };
    }),

  // Settings & Windows
  getSettings: () =>
    callIpc<AppSettings>("get_settings", undefined, () => ({ ...mockSettings })),

  saveSettings: (settings: AppSettings) =>
    callIpc<AppSettings>("save_settings", { settings }, () => {
      mockSettings = { ...settings };
      return { ...mockSettings };
    }),

  toggleMiniPlayer: (open?: boolean) =>
    callIpc<void>("toggle_mini_player", { open }, () => {
      // No-op in browser mode; in Tauri backend shows/hides `mini-player` window
    }),

  // Events
  onPlaybackTick: async (cb: Listener<PlaybackTickPayload>): Promise<UnlistenFn> => {
    if (isTauriRuntime()) {
      return listen<PlaybackTickPayload>("playback:tick", (e) => cb(e.payload));
    }
    ensureMockTicker();
    return mockEmitter.on("playback:tick", cb);
  },

  onPlaybackState: async (cb: Listener<PlaybackState>): Promise<UnlistenFn> => {
    if (isTauriRuntime()) {
      return listen<PlaybackState>("playback:state", (e) => cb(e.payload));
    }
    ensureMockTicker();
    return mockEmitter.on("playback:state", cb);
  },

  onQueueUpdated: async (cb: Listener<QueueSnapshot>): Promise<UnlistenFn> => {
    if (isTauriRuntime()) {
      return listen<QueueSnapshot>("queue:updated", (e) => cb(e.payload));
    }
    ensureMockTicker();
    return mockEmitter.on("queue:updated", cb);
  },

  onLibraryProgress: async (cb: Listener<LibraryScanProgress>): Promise<UnlistenFn> => {
    if (isTauriRuntime()) {
      return listen<LibraryScanProgress>("library:progress", (e) => cb(e.payload));
    }
    ensureMockTicker();
    return mockEmitter.on("library:progress", cb);
  },
};
