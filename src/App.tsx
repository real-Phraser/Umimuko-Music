import { useEffect, useMemo } from "react";
import appIconUrl from "./assets/branding/app-icon.png";
import { getWindowLabel } from "./api/tauri";
import { FullPlayer } from "./components/FullPlayer";
import { HomeScreen } from "./components/HomeScreen";
import { Icon } from "./components/Icon";
import { LibraryView } from "./components/LibraryView";
import { PlayerBar } from "./components/PlayerBar";
import { QueueDrawer } from "./components/QueueDrawer";
import { Sidebar } from "./components/Sidebar";
import { DuplicatesDialog } from "./components/dialogs/DuplicatesDialog";
import { EqualizerDialog } from "./components/dialogs/EqualizerDialog";
import { SettingsDialog } from "./components/dialogs/SettingsDialog";
import { SleepTimerDialog } from "./components/dialogs/SleepTimerDialog";
import { StatsDialog } from "./components/dialogs/StatsDialog";
import { TagEditorDialog } from "./components/dialogs/TagEditorDialog";
import { useLibraryStore } from "./store/useLibraryStore";
import { usePlayerStore } from "./store/usePlayerStore";
import { useSettingsStore } from "./store/useSettingsStore";
import { ThemeProvider } from "./theme/ThemeProvider";
import { MiniPlayer } from "./windows/MiniPlayer";

function MainWindow() {
  const initPlayer = usePlayerStore((s) => s.init);
  const initLibrary = useLibraryStore((s) => s.init);
  const initSettings = useSettingsStore((s) => s.init);
  const activeNav = useLibraryStore((s) => s.activeNav);
  const setActiveNav = useLibraryStore((s) => s.setActiveNav);
  const openDialog = useLibraryStore((s) => s.openDialog);

  const keybindings = useSettingsStore((s) => s.settings.keybindings);
  const togglePlayPause = usePlayerStore((s) => s.togglePlayPause);
  const nextTrack = usePlayerStore((s) => s.nextTrack);
  const prevTrack = usePlayerStore((s) => s.prevTrack);
  const toggleMute = usePlayerStore((s) => s.toggleMute);
  const setVolume = usePlayerStore((s) => s.setVolume);
  const setFullPlayerOpen = usePlayerStore((s) => s.setFullPlayerOpen);
  const setQueueDrawerOpen = usePlayerStore((s) => s.setQueueDrawerOpen);

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
        {/* Top Header Bar across full width (v0.26.1 Main.kt) */}
        <header className="h-16 shrink-0 px-7 flex items-center justify-between select-none">
          <div className="flex items-center gap-3">
            <img
              src={appIconUrl}
              alt="Umimuko Music"
              className="w-10 h-10 object-contain shrink-0 app-brand-icon"
              draggable={false}
            />
            <span className="text-[21px] font-medium tracking-tight">
              Umimuko Music
            </span>
          </div>

          <button
            type="button"
            onClick={() => openDialog("settings")}
            aria-label="Settings"
            title="Settings"
            className="w-10 h-10 rounded-full flex items-center justify-center transition-colors hover:opacity-85 cursor-pointer"
            style={{
              backgroundColor: "var(--surface-2)",
              color: "var(--text-primary)",
            }}
          >
            <Icon name="settings" size={22} />
          </button>
        </header>

        {/* Main Body: Left Sidebar + Content Area */}
        <div className="flex-1 min-h-0 flex">
          <Sidebar />
          <main className="flex-1 min-w-0 overflow-y-auto px-6 pb-6 pt-1">
            {activeNav === "home" ? <HomeScreen /> : <LibraryView />}
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
      </div>
    </ThemeProvider>
  );
}

export function App() {
  const windowLabel = useMemo(() => getWindowLabel(), []);
  return windowLabel === "mini-player" ? <MiniPlayer /> : <MainWindow />;
}

export default App;
