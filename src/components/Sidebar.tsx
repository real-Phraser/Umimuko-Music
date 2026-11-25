import { useLibraryStore, type LibraryTab, type NavSection } from "../store/useLibraryStore";
import { Icon, type IconName } from "./Icon";

interface NavItem {
  id: string;
  label: string;
  icon: IconName;
  nav: NavSection;
  tab?: LibraryTab;
}

const NAV_ITEMS: NavItem[] = [
  { id: "home", label: "Home", icon: "home", nav: "home" },
  { id: "songs", label: "Tracks", icon: "library", nav: "library", tab: "songs" },
  { id: "albums", label: "Albums", icon: "album", nav: "library", tab: "albums" },
  { id: "artists", label: "Artists", icon: "artist", nav: "library", tab: "artists" },
  { id: "playlists", label: "Playlists", icon: "playlist", nav: "library", tab: "playlists" },
  { id: "liked", label: "Liked songs", icon: "liked", nav: "liked" },
  { id: "folders", label: "Folders", icon: "folder", nav: "library", tab: "folders" },
];

export function Sidebar() {
  const activeNav = useLibraryStore((s) => s.activeNav);
  const libraryTab = useLibraryStore((s) => s.libraryTab);
  const scanProgress = useLibraryStore((s) => s.scanProgress);

  const setActiveNav = useLibraryStore((s) => s.setActiveNav);
  const setLibraryTab = useLibraryStore((s) => s.setLibraryTab);
  const setSelectedPlaylistId = useLibraryStore((s) => s.setSelectedPlaylistId);

  const handleSelect = (item: NavItem) => {
    setActiveNav(item.nav);
    if (item.tab) {
      setLibraryTab(item.tab);
    }
    setSelectedPlaylistId(null);
  };

  const isActiveItem = (item: NavItem) => {
    if (item.nav === "home") return activeNav === "home";
    if (item.nav === "liked") return activeNav === "liked";
    return activeNav === "library" && libraryTab === item.tab;
  };

  return (
    <aside
      className="w-48 shrink-0 flex flex-col h-full px-3.5 py-2 select-none adaptive-transition"
      style={{ backgroundColor: "var(--surface-0)" }}
    >
      <div
        className="px-3 py-2 text-[11px] font-semibold uppercase tracking-wider"
        style={{ color: "var(--text-muted)" }}
      >
        YOUR MUSIC
      </div>

      <nav className="mt-1 space-y-1.5 flex-1 overflow-y-auto">
        {NAV_ITEMS.map((item) => {
          const active = isActiveItem(item);
          return (
            <button
              key={item.id}
              type="button"
              onClick={() => handleSelect(item)}
              className="w-full flex items-center gap-3 px-3.5 py-3 rounded-full text-[13px] font-medium transition-colors cursor-pointer"
              style={{
                backgroundColor: active ? "var(--accent-container)" : "transparent",
                color: active ? "var(--on-accent-container)" : "var(--text-primary)",
              }}
            >
              <Icon
                name={item.icon}
                size={19}
                style={{ color: "var(--accent)" }}
              />
              <span className="truncate">{item.label}</span>
            </button>
          );
        })}

        {scanProgress.scanning && (
          <div
            className="mt-4 mx-1 p-2.5 rounded-2xl text-xs"
            style={{
              backgroundColor: "var(--surface-1)",
              border: "1px solid var(--outline)",
            }}
          >
            <div className="font-medium">Scanning…</div>
            <div className="mt-0.5 truncate" style={{ color: "var(--text-muted)" }}>
              {scanProgress.scanned} / {scanProgress.total || "?"}
            </div>
          </div>
        )}
      </nav>
    </aside>
  );
}
