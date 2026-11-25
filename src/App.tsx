import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import appIconUrl from "./assets/branding/app-icon.png";
import { getWindowLabel } from "./api/tauri";
import { FullPlayer } from "./components/FullPlayer";
import { HomeScreen } from "./components/HomeScreen";
import { Icon } from "./components/Icon";
import { LibraryView } from "./components/LibraryView";
import { PlayerBar } from "./components/PlayerBar";
import { QueueDrawer } from "./components/QueueDrawer";
import { Sidebar, SIDEBAR_NAV_ITEMS, type NavItem } from "./components/Sidebar";
import { StartupIntro, getStartupIntroEnabled } from "./components/StartupIntro";
import { DuplicatesDialog } from "./components/dialogs/DuplicatesDialog";
import { EqualizerDialog } from "./components/dialogs/EqualizerDialog";
import { SettingsDialog } from "./components/dialogs/SettingsDialog";
import { SleepTimerDialog } from "./components/dialogs/SleepTimerDialog";
import { StatsDialog } from "./components/dialogs/StatsDialog";
import { TagEditorDialog } from "./components/dialogs/TagEditorDialog";
import {
  useLibraryStore,
  type LibraryTab,
  type NavSection,
} from "./store/useLibraryStore";
import { usePlayerStore } from "./store/usePlayerStore";
import { useSettingsStore } from "./store/useSettingsStore";
import { ThemeProvider } from "./theme/ThemeProvider";
import { MiniPlayer } from "./windows/MiniPlayer";

interface NavSnapshot {
  activeNav: NavSection;
  libraryTab: LibraryTab;
  selectedPlaylistId: string | null;
  searchQuery: string;
}

function MainWindow() {
  const initPlayer = usePlayerStore((s) => s.init);
  const initLibrary = useLibraryStore((s) => s.init);
  const initSettings = useSettingsStore((s) => s.init);

  const activeNav = useLibraryStore((s) => s.activeNav);
  const libraryTab = useLibraryStore((s) => s.libraryTab);
  const selectedPlaylistId = useLibraryStore((s) => s.selectedPlaylistId);
  const searchQuery = useLibraryStore((s) => s.searchQuery);

  const setActiveNav = useLibraryStore((s) => s.setActiveNav);
  const setLibraryTab = useLibraryStore((s) => s.setLibraryTab);
  const setSelectedPlaylistId = useLibraryStore((s) => s.setSelectedPlaylistId);
  const setSearchQuery = useLibraryStore((s) => s.setSearchQuery);
  const openDialog = useLibraryStore((s) => s.openDialog);

  const keybindings = useSettingsStore((s) => s.settings.keybindings);
  const togglePlayPause = usePlayerStore((s) => s.togglePlayPause);
  const nextTrack = usePlayerStore((s) => s.nextTrack);
  const prevTrack = usePlayerStore((s) => s.prevTrack);
  const toggleMute = usePlayerStore((s) => s.toggleMute);
  const setVolume = usePlayerStore((s) => s.setVolume);
  const setFullPlayerOpen = usePlayerStore((s) => s.setFullPlayerOpen);
  const setQueueDrawerOpen = usePlayerStore((s) => s.setQueueDrawerOpen);

  const [showIntro, setShowIntro] = useState(() => getStartupIntroEnabled());

  // Track navigation history matching LibraryPanel.kt's history stack
  const [history, setHistory] = useState<NavSnapshot[]>([]);
  const prevSnapshotRef = useRef<NavSnapshot>({
    activeNav,
    libraryTab,
    selectedPlaylistId,
    searchQuery,
  });
  const goingBackRef = useRef(false);

  useEffect(() => {
    const prev = prevSnapshotRef.current;
    const sameRoute =
      prev.activeNav === activeNav &&
      prev.libraryTab === libraryTab &&
      prev.selectedPlaylistId === selectedPlaylistId;
    if (!sameRoute) {
      if (!goingBackRef.current) {
        setHistory((h) => [...h.slice(-24), prev]);
      }
      goingBackRef.current = false;
    }
    prevSnapshotRef.current = {
      activeNav,
      libraryTab,
      selectedPlaylistId,
      searchQuery,
    };
  }, [activeNav, libraryTab, selectedPlaylistId, searchQuery]);

  const handleGoBack = useCallback(() => {
    if (history.length > 0) {
      const target = history[history.length - 1];
      goingBackRef.current = true;
      setHistory((h) => h.slice(0, -1));
      setActiveNav(target.activeNav);
      setLibraryTab(target.libraryTab);
      setSelectedPlaylistId(target.selectedPlaylistId);
      setSearchQuery(target.searchQuery);
      return;
    }
    if (selectedPlaylistId) {
      setSelectedPlaylistId(null);
      return;
    }
    if (activeNav !== "home") {
      setActiveNav("home");
    }
  }, [
    history,
    selectedPlaylistId,
    activeNav,
    setActiveNav,
    setLibraryTab,
    setSelectedPlaylistId,
    setSearchQuery,
  ]);

  const handleSelectChip = (item: NavItem) => {
    setActiveNav(item.nav);
    if (item.tab) setLibraryTab(item.tab);
    setSelectedPlaylistId(null);
    setSearchQuery("");
  };

  useEffect(() => {
    void initSettings();
    let unlistenPlayer: (() => void) | undefined;
    let unlistenLibrary: (() => void) | undefined;

    void initPlayer().then((fn) => {
      unlistenPlayer = fn;
    });
    void initLibrary().then((fn) => {
      unlistenLibrary = fn;
    });

    return () => {
      unlistenPlayer?.();
      unlistenLibrary?.();
    };
  }, [initPlayer, initLibrary, initSettings]);

  // Global keyboard shortcuts (ignored when typing in inputs)
  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      const target = e.target as HTMLElement | null;
      if (
        target &&
        (target.tagName === "INPUT" ||
          target.tagName === "TEXTAREA" ||
          target.tagName === "SELECT" ||
          target.isContentEditable)
      ) {
        return;
      }

      if (e.code === keybindings.playPause) {
        e.preventDefault();
        void togglePlayPause();
      } else if (e.code === keybindings.nextTrack) {
        e.preventDefault();
        void nextTrack();
      } else if (e.code === keybindings.prevTrack) {
        e.preventDefault();
        void prevTrack();
      } else if (e.code === keybindings.toggleMute) {
        e.preventDefault();
        void toggleMute();
      } else if (e.code === keybindings.volumeUp) {
        e.preventDefault();
        const cur = usePlayerStore.getState().playback.volume;
        void setVolume(Math.min(1, cur + 0.05));
      } else if (e.code === keybindings.volumeDown) {
        e.preventDefault();
        const cur = usePlayerStore.getState().playback.volume;
        void setVolume(Math.max(0, cur - 0.05));
      } else if (e.code === keybindings.toggleLyrics) {
        e.preventDefault();
        const open = usePlayerStore.getState().fullPlayerOpen;
        setFullPlayerOpen(!open);
      } else if (e.code === keybindings.toggleQueue) {
        e.preventDefault();
        const open = usePlayerStore.getState().queueDrawerOpen;
        setQueueDrawerOpen(!open);
      } else if (e.code === keybindings.focusSearch) {
        e.preventDefault();
        setActiveNav("search");
      }
    };

    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [
    keybindings,
    togglePlayPause,
    nextTrack,
    prevTrack,
    toggleMute,
    setVolume,
    setFullPlayerOpen,
    setQueueDrawerOpen,
    setActiveNav,
  ]);

  return (
    <ThemeProvider>
      <div
        className="h-screen w-screen flex flex-col overflow-hidden adaptive-transition"
        style={{ backgroundColor: "var(--surface-0)", color: "var(--text-primary)" }}
      >
        {/* Top Header Bar across full width (Main.kt lines 633-643) */}
        <header className="shrink-0 px-7 py-[14px] flex items-center select-none">
          <div className="flex items-center gap-3">
            <img
              src={appIconUrl}
              alt="Umimuko Music"
              className="w-[42px] h-[42px] object-contain shrink-0 app-brand-icon"
              draggable={false}
            />
            <span className="text-[22px] leading-[28px] font-normal">
              Umimuko Music
            </span>
          </div>

          <div className="flex-1" />

          <button
            type="button"
            onClick={() => openDialog("settings")}
            aria-label="Settings"
            title="Settings"
            className="w-10 h-10 rounded-full flex items-center justify-center transition-opacity hover:opacity-90 cursor-pointer"
            style={{
              backgroundColor: "var(--accent-container)",
              color: "var(--on-accent-container)",
            }}
          >
            <Icon name="settings" size={22} />
          </button>
        </header>

        {/* Main Body: Left Sidebar + Content Column (LibraryPanel.kt lines 369-385) */}
        <div className="flex-1 min-h-0 flex">
          <Sidebar />

          <main className="flex-1 min-w-0 min-h-0 flex flex-col overflow-hidden pl-6 pr-7 pt-2">
            {/* Compact horizontal chip strip on narrow viewports (LibraryPanel.kt lines 386-430) */}
            <div className="flex md:hidden items-center gap-1.5 overflow-x-auto no-scrollbar pb-2.5 shrink-0">
              {SIDEBAR_NAV_ITEMS.map((item) => {
                const selected =
                  item.nav === "home"
                    ? activeNav === "home"
                    : item.nav === "liked"
                      ? activeNav === "liked"
                      : activeNav === "library" && libraryTab === item.tab;
                return (
                  <button
                    key={item.id}
                    type="button"
                    onClick={() => handleSelectChip(item)}
                    className="h-8 px-4 rounded-[8px] text-[14px] leading-[20px] shrink-0 transition-colors cursor-pointer"
                    style={{
                      backgroundColor: selected
                        ? "var(--accent-container)"
                        : "transparent",
                      color: selected
                        ? "var(--on-accent-container)"
                        : "var(--text-primary)",
                      border: selected ? "none" : "1px solid var(--outline)",
                      fontWeight: selected ? 600 : 500,
                    }}
                  >
                    {item.label}
                  </button>
                );
              })}
            </div>

            {activeNav === "home" ? (
              <HomeScreen canGoBack={history.length > 0} onBack={handleGoBack} />
            ) : (
              <LibraryView canGoBack onBack={handleGoBack} />
            )}
          </main>
        </div>

        <PlayerBar />
        <QueueDrawer />
        <FullPlayer />

        {/* Dialogs */}
        <EqualizerDialog />
        <TagEditorDialog />
        <DuplicatesDialog />
        <StatsDialog />
        <SleepTimerDialog />
        <SettingsDialog />

        {showIntro && <StartupIntro onFinished={() => setShowIntro(false)} />}
      </div>
    </ThemeProvider>
  );
}

export function App() {
  const windowLabel = useMemo(() => getWindowLabel(), []);
  return windowLabel === "mini-player" ? <MiniPlayer /> : <MainWindow />;
}

export default App;
