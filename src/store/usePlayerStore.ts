import { create } from "zustand";
import { api } from "../api/tauri";
import type {
  PlaybackState,
  QueueSnapshot,
  RepeatMode,
  SpicyLyrics,
} from "../types/ipc";

interface PlayerStore {
  playback: PlaybackState;
  queue: QueueSnapshot;
  lyrics: SpicyLyrics | null;
  lyricsLoading: boolean;
  fullPlayerOpen: boolean;
  fullPlayerTab: "lyrics" | "artwork";
  queueDrawerOpen: boolean;
  initialized: boolean;

  init: () => Promise<() => void>;
  playTrack: (trackId: string, queueTrackIds?: string[]) => Promise<void>;
  togglePlayPause: () => Promise<void>;
  nextTrack: () => Promise<void>;
  prevTrack: () => Promise<void>;
  seek: (positionMs: number) => Promise<void>;
  setVolume: (volume: number) => Promise<void>;
  toggleMute: () => Promise<void>;
  toggleShuffle: () => Promise<void>;
  cycleRepeat: () => Promise<void>;
  reorderQueue: (fromIndex: number, toIndex: number) => Promise<void>;
  removeFromQueue: (indices: number[]) => Promise<void>;
  enqueueTracks: (trackIds: string[], playNext?: boolean) => Promise<void>;
  setSleepTimer: (minutes: number | null) => Promise<void>;
  loadLyrics: (trackId: string) => Promise<void>;
  refreshLyrics: (trackId: string) => Promise<void>;
  setFullPlayerOpen: (open: boolean) => void;
  setFullPlayerTab: (tab: "lyrics" | "artwork") => void;
  setQueueDrawerOpen: (open: boolean) => void;
}

const INITIAL_PLAYBACK: PlaybackState = {
  status: "stopped",
  currentTrackId: null,
  currentTrack: null,
  positionMs: 0,
  durationMs: 0,
  volume: 0.8,
  muted: false,
  shuffle: false,
  repeat: "off",
  sleepTimerRemainingMs: null,
};

const INITIAL_QUEUE: QueueSnapshot = {
  items: [],
  currentIndex: -1,
  shuffle: false,
  repeat: "off",
};

export const usePlayerStore = create<PlayerStore>((set, get) => ({
  playback: INITIAL_PLAYBACK,
  queue: INITIAL_QUEUE,
  lyrics: null,
  lyricsLoading: false,
  fullPlayerOpen: false,
  fullPlayerTab: "lyrics",
  queueDrawerOpen: false,
  initialized: false,

  init: async () => {
    const [playback, queue] = await Promise.all([
      api.getPlaybackState(),
      api.getQueue(),
    ]);
    set({ playback, queue, initialized: true });
    if (playback.currentTrackId) {
      void get().loadLyrics(playback.currentTrackId);
    }

    const unlistenTick = await api.onPlaybackTick(({ positionMs, durationMs }) => {
      set((s) => ({
        playback: {
          ...s.playback,
          positionMs,
          durationMs: durationMs || s.playback.durationMs,
        },
      }));
    });

    const unlistenState = await api.onPlaybackState((nextState) => {
      const prevTrackId = get().playback.currentTrackId;
      set({ playback: nextState });
      if (nextState.currentTrackId && nextState.currentTrackId !== prevTrackId) {
        void get().loadLyrics(nextState.currentTrackId);
      }
    });

    const unlistenQueue = await api.onQueueUpdated((nextQueue) => {
      set({ queue: nextQueue });
    });

    return () => {
      unlistenTick();
      unlistenState();
      unlistenQueue();
    };
  },

  playTrack: async (trackId, queueTrackIds) => {
    const playback = await api.playTrack(trackId, queueTrackIds);
    const queue = await api.getQueue();
    set({ playback, queue });
    void get().loadLyrics(trackId);
  },

  togglePlayPause: async () => {
    const playback = await api.togglePlayPause();
    set({ playback });
  },

  nextTrack: async () => {
    const playback = await api.nextTrack();
    set({ playback });
    if (playback.currentTrackId) {
      void get().loadLyrics(playback.currentTrackId);
    }
  },

  prevTrack: async () => {
    const playback = await api.prevTrack();
    set({ playback });
    if (playback.currentTrackId) {
      void get().loadLyrics(playback.currentTrackId);
    }
  },

  seek: async (positionMs) => {
    set((s) => ({ playback: { ...s.playback, positionMs } }));
    await api.seek(positionMs);
  },

  setVolume: async (volume) => {
    set((s) => ({ playback: { ...s.playback, volume } }));
    await api.setVolume(volume);
  },

  toggleMute: async () => {
    const nextMuted = !get().playback.muted;
    set((s) => ({ playback: { ...s.playback, muted: nextMuted } }));
    await api.setMuted(nextMuted);
  },

  toggleShuffle: async () => {
    const next = !get().playback.shuffle;
    set((s) => ({ playback: { ...s.playback, shuffle: next } }));
    await api.setShuffle(next);
  },

  cycleRepeat: async () => {
    const cur = get().playback.repeat;
    const next: RepeatMode = cur === "off" ? "all" : cur === "all" ? "one" : "off";
    set((s) => ({ playback: { ...s.playback, repeat: next } }));
    await api.setRepeat(next);
  },

  reorderQueue: async (fromIndex, toIndex) => {
    const queue = await api.reorderQueue(fromIndex, toIndex);
    set({ queue });
  },

  removeFromQueue: async (indices) => {
    const queue = await api.removeFromQueue(indices);
    set({ queue });
  },

  enqueueTracks: async (trackIds, playNext = false) => {
    const queue = await api.enqueueTracks(trackIds, playNext);
    set({ queue });
  },

  setSleepTimer: async (minutes) => {
    await api.setSleepTimer(minutes);
    const playback = await api.getPlaybackState();
    set({ playback });
  },

  loadLyrics: async (trackId) => {
    set({ lyricsLoading: true });
    try {
      const lyrics = await api.getLyrics(trackId);
      if (get().playback.currentTrackId === trackId) {
        set({ lyrics, lyricsLoading: false });
      }
    } catch {
      set({ lyrics: null, lyricsLoading: false });
    }
  },

  refreshLyrics: async (trackId) => {
    set({ lyricsLoading: true });
    try {
      const lyrics = await api.refreshLyrics(trackId);
      if (get().playback.currentTrackId === trackId) {
        set({ lyrics, lyricsLoading: false });
      }
    } catch {
      set({ lyricsLoading: false });
    }
  },

  setFullPlayerOpen: (open) => set({ fullPlayerOpen: open }),
  setFullPlayerTab: (tab) => set({ fullPlayerTab: tab }),
  setQueueDrawerOpen: (open) => set({ queueDrawerOpen: open }),
}));
