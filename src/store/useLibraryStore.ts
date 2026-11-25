import { create } from "zustand";
import { api } from "../api/tauri";
import type {
  LibraryScanProgress,
  Playlist,
  TagUpdateInput,
  Track,
} from "../types/ipc";

export type NavSection = "home" | "library" | "liked" | "search";
export type LibraryTab = "songs" | "albums" | "artists" | "playlists" | "folders";
export type SortField = "title" | "artist" | "album" | "addedAt" | "durationMs";
export type DialogType =
  | null
  | "equalizer"
  | "settings"
  | "stats"
  | "duplicates"
  | "sleepTimer";

interface LibraryStore {
  tracks: Track[];
  playlists: Playlist[];
  pinnedTrackIds: string[];
  scanProgress: LibraryScanProgress;
  loading: boolean;

  activeNav: NavSection;
  libraryTab: LibraryTab;
  selectedPlaylistId: string | null;
  searchQuery: string;
  sortBy: SortField;
  sortDir: "asc" | "desc";

  activeDialog: DialogType;
  tagEditorTrack: Track | null;

  init: () => Promise<() => void>;
  refreshLibrary: () => Promise<void>;
  scanFolders: (folders?: string[]) => Promise<void>;
  toggleLike: (trackId: string) => Promise<void>;
  togglePin: (trackId: string) => Promise<void>;
  updateTags: (input: TagUpdateInput) => Promise<void>;
  deleteTrack: (trackId: string, deleteFile?: boolean) => Promise<void>;
  createPlaylist: (name: string, trackIds?: string[]) => Promise<Playlist>;
  addTrackToPlaylist: (playlistId: string, trackId: string) => Promise<void>;
  deletePlaylist: (playlistId: string) => Promise<void>;

  setActiveNav: (nav: NavSection) => void;
  setLibraryTab: (tab: LibraryTab) => void;
  setSelectedPlaylistId: (id: string | null) => void;
  setSearchQuery: (query: string) => void;
  setSort: (field: SortField) => void;
  openDialog: (dialog: DialogType) => void;
  openTagEditor: (track: Track | null) => void;
}

export const useLibraryStore = create<LibraryStore>((set, get) => ({
  tracks: [],
  playlists: [],
  pinnedTrackIds: [],
  scanProgress: { scanning: false, scanned: 0, total: 0 },
  loading: false,

  activeNav: "home",
  libraryTab: "songs",
  selectedPlaylistId: null,
  searchQuery: "",
  sortBy: "addedAt",
  sortDir: "desc",

  activeDialog: null,
  tagEditorTrack: null,

  init: async () => {
    set({ loading: true });
    try {
      const [tracks, playlists, pinnedTrackIds] = await Promise.all([
        api.getTracks(),
        api.getPlaylists(),
        api.getPinnedTrackIds(),
      ]);
      set({ tracks, playlists, pinnedTrackIds, loading: false });
    } catch {
      set({ loading: false });
    }

    const unlistenProgress = await api.onLibraryProgress((scanProgress) => {
      set({ scanProgress });
      if (!scanProgress.scanning) {
        void get().refreshLibrary();
      }
    });

    return () => {
      unlistenProgress();
    };
  },

  refreshLibrary: async () => {
    const [tracks, playlists, pinnedTrackIds] = await Promise.all([
      api.getTracks(),
      api.getPlaylists(),
      api.getPinnedTrackIds(),
    ]);
    set({ tracks, playlists, pinnedTrackIds });
  },

  scanFolders: async (folders) => {
    set({
      scanProgress: { scanning: true, scanned: 0, total: get().tracks.length },
    });
    const tracks = await api.scanFolders(folders);
    set({
      tracks,
      scanProgress: { scanning: false, scanned: tracks.length, total: tracks.length },
    });
  },

  toggleLike: async (trackId) => {
    const target = get().tracks.find((t) => t.id === trackId);
    if (!target) return;
    const updated = await api.toggleLikeTrack(trackId, !target.liked);
    set((s) => ({
      tracks: s.tracks.map((t) => (t.id === trackId ? updated : t)),
    }));
  },

  togglePin: async (trackId) => {
    const pinnedTrackIds = await api.togglePinTrack(trackId);
    set({ pinnedTrackIds });
  },

  updateTags: async (input) => {
    const updated = await api.updateTrackTags(input);
    set((s) => ({
      tracks: s.tracks.map((t) => (t.id === updated.id ? updated : t)),
      tagEditorTrack: null,
    }));
  },

  deleteTrack: async (trackId, deleteFile = false) => {
    await api.deleteTrack(trackId, deleteFile);
    set((s) => ({
      tracks: s.tracks.filter((t) => t.id !== trackId),
      pinnedTrackIds: s.pinnedTrackIds.filter((id) => id !== trackId),
    }));
  },

  createPlaylist: async (name, trackIds = []) => {
    const created = await api.createPlaylist(name, trackIds);
    set((s) => ({ playlists: [...s.playlists, created] }));
    return created;
  },

  addTrackToPlaylist: async (playlistId, trackId) => {
    const updated = await api.addTrackToPlaylist(playlistId, trackId);
    set((s) => ({
      playlists: s.playlists.map((p) => (p.id === playlistId ? updated : p)),
    }));
  },

  deletePlaylist: async (playlistId) => {
    await api.deletePlaylist(playlistId);
    set((s) => ({
      playlists: s.playlists.filter((p) => p.id !== playlistId),
      selectedPlaylistId: s.selectedPlaylistId === playlistId ? null : s.selectedPlaylistId,
    }));
  },

  setActiveNav: (activeNav) => set({ activeNav }),
  setLibraryTab: (libraryTab) => set({ libraryTab }),
  setSelectedPlaylistId: (selectedPlaylistId) => set({ selectedPlaylistId }),
  setSearchQuery: (searchQuery) => set({ searchQuery }),
  setSort: (field) =>
    set((s) => ({
      sortBy: field,
      sortDir: s.sortBy === field && s.sortDir === "asc" ? "desc" : "asc",
    })),
  openDialog: (activeDialog) => set({ activeDialog }),
  openTagEditor: (tagEditorTrack) => set({ tagEditorTrack }),
}));
