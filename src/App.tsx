import { useEffect, useMemo } from "react";
import { getWindowLabel } from "./api/tauri";
import { FullPlayer } from "./components/FullPlayer";
import { HomeScreen } from "./components/HomeScreen";
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
      <div className="h-screen w-screen flex flex-col overflow-hidden">
        <div className="flex-1 min-h-0 flex">
          <Sidebar />
          <main className="flex-1 min-w-0 overflow-y-auto">
            {activeNav === "home" ? <HomeScreen /> : <LibraryView />}
          </main>
          <QueueDrawer />
        </div>

        <PlayerBar />
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
