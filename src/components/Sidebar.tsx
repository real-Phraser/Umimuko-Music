import appIconUrl from "../assets/branding/app-icon.png";
import { useLibraryStore, type LibraryTab, type NavSection } from "../store/useLibraryStore";
import { Icon, type IconName } from "./Icon";

const PRIMARY_NAV: Array<{ id: NavSection; label: string; icon: IconName }> = [
  { id: "home", label: "Home", icon: "home" },
  { id: "search", label: "Search", icon: "search" },
  { id: "liked", label: "Liked Songs", icon: "liked" },
];

const LIBRARY_TABS: Array<{ id: LibraryTab; label: string; icon: IconName }> = [
  { id: "songs", label: "Songs", icon: "music" },
  { id: "albums", label: "Albums", icon: "album" },
  { id: "artists", label: "Artists", icon: "artist" },
  { id: "playlists", label: "Playlists", icon: "playlist" },
  { id: "folders", label: "Folders", icon: "folder" },
];

export function Sidebar() {
  const activeNav = useLibraryStore((s) => s.activeNav);
  const libraryTab = useLibraryStore((s) => s.libraryTab);
  const scanProgress = useLibraryStore((s) => s.scanProgress);
  const tracksCount = useLibraryStore((s) => s.tracks.length);
  const likedCount = useLibraryStore((s) => s.tracks.filter((t) => t.liked).length);

  const setActiveNav = useLibraryStore((s) => s.setActiveNav);
  const setLibraryTab = useLibraryStore((s) => s.setLibraryTab);
  const setSelectedPlaylistId = useLibraryStore((s) => s.setSelectedPlaylistId);
  const openDialog = useLibraryStore((s) => s.openDialog);
  const scanFolders = useLibraryStore((s) => s.scanFolders);

  const handleLibraryTabClick = (tab: LibraryTab) => {
    setActiveNav("library");
    setLibraryTab(tab);
    setSelectedPlaylistId(null);
  };

  return (
    <aside
      className="w-60 shrink-0 flex flex-col h-full border-r adaptive-transition"
      style={{
        backgroundColor: "var(--surface-1)",
        borderColor: "var(--outline)",
      }}
    >
      {/* App Brand */}
      <div className="px-4 pt-5 pb-4 flex items-center gap-3">
        <img
          src={appIconUrl}
          alt="Umimuko Music"
          className="w-9 h-9 rounded-xl shadow-sm object-cover"
          draggable={false}
        />
        <div className="min-w-0">
          <div className="text-sm font-semibold tracking-tight truncate">
            Umimuko Music
          </div>
          <div className="text-[11px]" style={{ color: "var(--text-muted)" }}>
            v0.26.1 • {tracksCount} tracks
          </div>
        </div>
      </div>

      {/* Primary Nav */}
      <nav className="px-2.5 space-y-1">
        {PRIMARY_NAV.map((item) => {
          const active = activeNav === item.id;
          return (
            <button
              key={item.id}
              type="button"
              onClick={() => setActiveNav(item.id)}
              className="w-full flex items-center justify-between px-3 py-2.5 rounded-full text-sm font-medium transition-colors cursor-pointer"
              style={{
                backgroundColor: active ? "var(--accent-container)" : "transparent",
                color: active ? "var(--on-accent-container)" : "var(--text-secondary)",
              }}
            >
              <span className="flex items-center gap-3">
                <Icon
                  name={item.icon}
                  size={19}
                  style={{ color: active ? "var(--accent)" : undefined }}
                />
                <span>{item.label}</span>
              </span>
              {item.id === "liked" && likedCount > 0 && (
                <span
                  className="text-[11px] px-2 py-0.5 rounded-full"
                  style={{
                    backgroundColor: "var(--surface-3)",
                    color: "var(--text-secondary)",
                  }}
                >
                  {likedCount}
                </span>
              )}
            </button>
          );
        })}
      </nav>

      {/* Library Section */}
      <div className="mt-5 px-2.5 flex-1 overflow-y-auto">
        <div
          className="px-3 mb-1.5 text-[11px] font-semibold uppercase tracking-wider flex items-center justify-between"
          style={{ color: "var(--text-muted)" }}
        >
          <span>Library</span>
          <button
            type="button"
            onClick={() => void scanFolders()}
            disabled={scanProgress.scanning}
            title="Rescan music folders"
            className="p-1 rounded-full hover:opacity-80 disabled:opacity-40 cursor-pointer"
            style={{ color: "var(--text-secondary)" }}
          >
            <Icon
              name="restore"
              size={14}
              className={scanProgress.scanning ? "animate-spin" : ""}
            />
          </button>
        </div>

        <div className="space-y-1">
          {LIBRARY_TABS.map((tab) => {
            const active = activeNav === "library" && libraryTab === tab.id;
            return (
              <button
                key={tab.id}
                type="button"
                onClick={() => handleLibraryTabClick(tab.id)}
                className="w-full flex items-center gap-3 px-3 py-2 rounded-full text-sm font-medium transition-colors cursor-pointer"
                style={{
                  backgroundColor: active ? "var(--accent-container)" : "transparent",
                  color: active ? "var(--on-accent-container)" : "var(--text-secondary)",
                }}
              >
                <Icon
                  name={tab.icon}
                  size={18}
                  style={{ color: active ? "var(--accent)" : undefined }}
                />
                <span>{tab.label}</span>
              </button>
            );
          })}
        </div>

        {scanProgress.scanning && (
          <div
            className="mt-3 mx-2 p-2.5 rounded-xl text-xs"
            style={{
              backgroundColor: "var(--surface-2)",
              border: "1px solid var(--outline)",
            }}
          >
            <div className="font-medium">Scanning library…</div>
            <div className="mt-0.5 truncate" style={{ color: "var(--text-muted)" }}>
              {scanProgress.scanned} / {scanProgress.total || "?"}
            </div>
          </div>
        )}
      </div>

      {/* Tools & Settings */}
      <div
        className="p-2.5 border-t space-y-1"
        style={{ borderColor: "var(--outline)" }}
      >
        <button
          type="button"
          onClick={() => openDialog("stats")}
          className="w-full flex items-center gap-3 px-3 py-2 rounded-full text-sm transition-colors hover:opacity-90 cursor-pointer"
          style={{ color: "var(--text-secondary)" }}
        >
          <Icon name="info" size={18} />
          <span>Listening Stats</span>
        </button>
        <button
          type="button"
          onClick={() => openDialog("duplicates")}
          className="w-full flex items-center gap-3 px-3 py-2 rounded-full text-sm transition-colors hover:opacity-90 cursor-pointer"
          style={{ color: "var(--text-secondary)" }}
        >
          <Icon name="library" size={18} />
          <span>Duplicates</span>
        </button>
        <button
          type="button"
          onClick={() => openDialog("equalizer")}
          className="w-full flex items-center gap-3 px-3 py-2 rounded-full text-sm transition-colors hover:opacity-90 cursor-pointer"
          style={{ color: "var(--text-secondary)" }}
        >
          <Icon name="levels" size={18} />
          <span>Equalizer</span>
        </button>
        <button
          type="button"
          onClick={() => openDialog("settings")}
          className="w-full flex items-center gap-3 px-3 py-2 rounded-full text-sm transition-colors hover:opacity-90 cursor-pointer"
          style={{ color: "var(--text-secondary)" }}
        >
          <Icon name="settings" size={18} />
          <span>Settings</span>
        </button>
      </div>
    </aside>
  );
}
