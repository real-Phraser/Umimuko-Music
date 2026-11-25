import {
  useLibraryStore,
  type LibraryTab,
  type NavSection,
} from "../store/useLibraryStore";
import { Icon, type IconName } from "./Icon";

export interface NavItem {
  id: string;
  label: string;
  icon: IconName;
  nav: NavSection;
  tab?: LibraryTab;
}

export const SIDEBAR_NAV_ITEMS: NavItem[] = [
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
  const setSearchQuery = useLibraryStore((s) => s.setSearchQuery);

  const handleSelect = (item: NavItem) => {
    setActiveNav(item.nav);
    if (item.tab) {
      setLibraryTab(item.tab);
    }
    setSelectedPlaylistId(null);
    setSearchQuery("");
  };

  const isActiveItem = (item: NavItem) => {
    if (item.nav === "home") return activeNav === "home";
    if (item.nav === "liked") return activeNav === "liked";
    return activeNav === "library" && libraryTab === item.tab;
  };

  return (
    <aside
      className="w-[190px] shrink-0 hidden md:flex flex-col h-full p-4 overflow-y-auto select-none adaptive-transition"
      style={{ backgroundColor: "var(--surface-0)" }}
    >
      <div className="flex flex-col gap-2 flex-1">
        <div
          className="p-3 text-[11px] leading-[16px] font-medium uppercase tracking-[0.5px]"
          style={{ color: "var(--text-primary)" }}
        >
          YOUR MUSIC
        </div>

        <nav className="flex flex-col gap-2">
          {SIDEBAR_NAV_ITEMS.map((item) => {
            const active = isActiveItem(item);
            return (
              <button
                key={item.id}
                type="button"
                onClick={() => handleSelect(item)}
                className="w-full flex items-center gap-3 p-[14px] rounded-[18px] text-[14px] leading-[20px] font-medium transition-colors cursor-pointer hover:bg-[var(--surface-variant)]"
                style={{
                  backgroundColor: active ? "var(--accent-container)" : "transparent",
                  color: active ? "var(--on-accent-container)" : "var(--text-primary)",
                }}
              >
                <Icon
                  name={item.icon}
                  size={24}
                  style={{ color: "var(--accent)" }}
                />
                <span className="truncate">{item.label}</span>
              </button>
            );
          })}
        </nav>

        {scanProgress.scanning && (
          <div
            className="mt-2 p-3 rounded-[16px] text-xs"
            style={{
              backgroundColor: "var(--surface-1)",
              border: "1px solid var(--outline)",
            }}
          >
            <div className="font-medium">Reading tracks…</div>
            <div className="mt-0.5 truncate" style={{ color: "var(--text-secondary)" }}>
              {scanProgress.scanned} / {scanProgress.total || "?"}
            </div>
          </div>
        )}
      </div>
    </aside>
  );
}
