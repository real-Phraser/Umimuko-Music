import {
  closestCenter,
  DndContext,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
  type DragEndEvent,
} from "@dnd-kit/core";
import {
  SortableContext,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
} from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { useEffect, useRef, useState } from "react";
import { useLibraryStore } from "../store/useLibraryStore";
import { usePlayerStore } from "../store/usePlayerStore";
import type { Track } from "../types/ipc";
import { Artwork } from "./Artwork";
import { Icon } from "./Icon";

function splitArtists(raw: string): string[] {
  const cleaned = raw.trim();
  if (!cleaned) return [];
  return cleaned
    .split(/\s*(?:,|&|;|\/|\bfeat\.?\b|\bft\.?\b)\s*/i)
    .map((s) => s.trim())
    .filter(Boolean);
}

interface SortableQueueItemProps {
  id: string;
  track: Track;
  index: number;
  total: number;
  isCurrent: boolean;
  isPlayed: boolean;
  selected: boolean;
  selecting: boolean;
  menuOpen: boolean;
  onToggleMenu: () => void;
  onCloseMenu: () => void;
  onToggleSelect: (index: number) => void;
  onPlay: (trackId: string) => void;
  onMoveBy: (delta: number) => void;
  onRemove: () => void;
  onOpenAlbum: () => void;
  onOpenArtist: (artist: string) => void;
}

function SortableQueueItem({
  id,
  track,
  index,
  total,
  isCurrent,
  isPlayed,
  selected,
  selecting,
  menuOpen,
  onToggleMenu,
  onCloseMenu,
  onToggleSelect,
  onPlay,
  onMoveBy,
  onRemove,
  onOpenAlbum,
  onOpenArtist,
}: SortableQueueItemProps) {
  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({ id });

  const artists = splitArtists(track.artist);
  const openUpward = total > 4 && index >= total - 2;

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    backgroundColor: selected
      ? "var(--surface-4)"
      : isCurrent
        ? "var(--accent-container)"
        : "transparent",
    color:
      isCurrent && !selected
        ? "var(--on-accent-container)"
        : "var(--text-primary)",
    opacity: isDragging ? 0.92 : isPlayed && !selected ? 0.6 : 1,
    boxShadow: isDragging ? "0 8px 20px rgba(0,0,0,0.35)" : "none",
  };

  return (
    <div
      ref={setNodeRef}
      style={style}
      onContextMenu={(e) => {
        e.preventDefault();
        onToggleSelect(index);
      }}
      className="relative flex items-center px-1 py-1.5 rounded-[16px] transition-colors hover:bg-[var(--surface-variant)]"
    >
      {selecting ? (
        <div className="w-8 h-8 flex items-center justify-center shrink-0">
          <input
            type="checkbox"
            checked={selected}
            onChange={() => onToggleSelect(index)}
            className="w-4 h-4 accent-[var(--accent)] cursor-pointer"
            aria-label={`Select ${track.title}`}
          />
        </div>
      ) : (
        <button
          type="button"
          {...attributes}
          {...listeners}
          className="w-8 h-8 flex items-center justify-center rounded-lg cursor-grab active:cursor-grabbing shrink-0"
          style={{ color: "var(--text-secondary)" }}
          aria-label={`Drag ${track.title}`}
          title="Drag to reorder"
        >
          <Icon name="drag" size={20} />
        </button>
      )}

      <div className="w-1 shrink-0" />

      <button
        type="button"
        onClick={() => {
          if (selecting) {
            onToggleSelect(index);
          } else {
            onPlay(track.id);
          }
        }}
        className="flex items-center min-w-0 flex-1 text-left cursor-pointer"
      >
        <Artwork
          artworkPath={track.artworkPath}
          accentColor={track.accentColor}
          alt={track.title}
          size="sm"
          rounded="rounded-[11px]"
        />
        <div className="w-3 shrink-0" />
        <div className="min-w-0 flex-1">
          <div className="text-sm leading-5 font-semibold truncate">{track.title}</div>
          <div
            className="text-[11px] leading-4 truncate"
            style={{
              color:
                isCurrent && !selected
                  ? "var(--on-accent-container)"
                  : "var(--text-secondary)",
              opacity: isCurrent && !selected ? 0.8 : 1,
            }}
          >
            {artists.length > 0 ? artists.join(" · ") : "Unknown artist"}
          </div>
        </div>
      </button>

      {isCurrent && (
        <div className="pl-2 pr-1 shrink-0" style={{ color: "var(--accent)" }}>
          <Icon name="levels" size={18} />
        </div>
      )}

      {!selecting && (
        <div className="relative shrink-0">
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              onToggleMenu();
            }}
            className="w-9 h-9 rounded-full flex items-center justify-center hover:bg-[var(--surface-variant)] cursor-pointer"
            style={{ color: "currentColor" }}
            aria-label="Queue actions"
          >
            <Icon name="more" size={20} />
          </button>

          {menuOpen && (
            <div
              className={`absolute right-0 ${
                openUpward ? "bottom-10" : "top-10"
              } z-30 w-60 py-1.5 rounded-2xl shadow-2xl border`}
              style={{
                backgroundColor: "var(--surface-3)",
                borderColor: "var(--outline-strong)",
                color: "var(--text-primary)",
              }}
              onClick={(e) => e.stopPropagation()}
            >
              <button
                type="button"
                disabled={index <= 0}
                onClick={() => {
                  onCloseMenu();
                  onMoveBy(-1);
                }}
                className="w-full px-3.5 py-2 text-left text-xs flex items-center gap-3 hover:bg-[var(--surface-variant)] disabled:opacity-40 cursor-pointer"
              >
                <Icon name="up" size={18} />
                <span>Move up</span>
              </button>
              <button
                type="button"
                disabled={index >= total - 1}
                onClick={() => {
                  onCloseMenu();
                  onMoveBy(1);
                }}
                className="w-full px-3.5 py-2 text-left text-xs flex items-center gap-3 hover:bg-[var(--surface-variant)] disabled:opacity-40 cursor-pointer"
              >
                <Icon name="down" size={18} />
                <span>Move down</span>
              </button>
              <div className="my-1 border-t" style={{ borderColor: "var(--outline)" }} />
              <button
                type="button"
                onClick={() => {
                  onCloseMenu();
                  onOpenAlbum();
                }}
                className="w-full px-3.5 py-2 text-left text-xs flex items-center gap-3 hover:bg-[var(--surface-variant)] cursor-pointer"
              >
                <Icon name="album" size={18} />
                <span className="truncate">
                  Go to album: {track.album || "Unknown album"}
                </span>
              </button>
              {(artists.length > 0 ? artists : ["Unknown artist"]).map((artist) => (
                <button
                  key={artist}
                  type="button"
                  onClick={() => {
                    onCloseMenu();
                    onOpenArtist(artist);
                  }}
                  className="w-full px-3.5 py-2 text-left text-xs flex items-center gap-3 hover:bg-[var(--surface-variant)] cursor-pointer"
                >
                  <Icon name="artist" size={18} />
                  <span className="truncate">Go to artist: {artist}</span>
                </button>
              ))}
              <div className="my-1 border-t" style={{ borderColor: "var(--outline)" }} />
              <button
                type="button"
                onClick={() => {
                  onCloseMenu();
                  onRemove();
                }}
                className="w-full px-3.5 py-2 text-left text-xs flex items-center gap-3 hover:bg-[var(--surface-variant)] cursor-pointer"
                style={{ color: "var(--danger)" }}
              >
                <Icon name="remove" size={18} />
                <span>Remove from queue</span>
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

export function QueueDrawer() {
  const queueDrawerOpen = usePlayerStore((s) => s.queueDrawerOpen);
  const setQueueDrawerOpen = usePlayerStore((s) => s.setQueueDrawerOpen);
  const setFullPlayerOpen = usePlayerStore((s) => s.setFullPlayerOpen);
  const queue = usePlayerStore((s) => s.queue);
  const reorderQueue = usePlayerStore((s) => s.reorderQueue);
  const removeFromQueue = usePlayerStore((s) => s.removeFromQueue);
  const playTrack = usePlayerStore((s) => s.playTrack);

  const createPlaylist = useLibraryStore((s) => s.createPlaylist);
  const setActiveNav = useLibraryStore((s) => s.setActiveNav);
  const setLibraryTab = useLibraryStore((s) => s.setLibraryTab);
  const setSearchQuery = useLibraryStore((s) => s.setSearchQuery);

  const [selectedIndices, setSelectedIndices] = useState<number[]>([]);
  const [headerMenuOpen, setHeaderMenuOpen] = useState(false);
  const [rowMenuIndex, setRowMenuIndex] = useState<number | null>(null);
  const [saveModalOpen, setSaveModalOpen] = useState(false);
  const [playlistName, setPlaylistName] = useState("");
  const [savingPlaylist, setSavingPlaylist] = useState(false);
  const nowSectionRef = useRef<HTMLDivElement | null>(null);

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 4 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates })
  );

  useEffect(() => {
    if (!queueDrawerOpen) return;
    setSelectedIndices([]);
    setHeaderMenuOpen(false);
    setRowMenuIndex(null);
    setSaveModalOpen(false);

    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        if (saveModalOpen) {
          setSaveModalOpen(false);
        } else {
          setQueueDrawerOpen(false);
        }
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [queueDrawerOpen, saveModalOpen, setQueueDrawerOpen]);

  useEffect(() => {
    if (queueDrawerOpen && nowSectionRef.current) {
      nowSectionRef.current.scrollIntoView({ block: "start" });
    }
  }, [queueDrawerOpen]);

  if (!queueDrawerOpen) return null;

  const total = queue.items.length;
  const current = queue.currentIndex;
  const hasCurrent = current >= 0 && current < total;
  const itemIds = queue.items.map((t, idx) => `${t.id}__${idx}`);
  const selecting = selectedIndices.length > 0;

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event;
    if (!over || active.id === over.id) return;
    const oldIndex = itemIds.indexOf(String(active.id));
    const newIndex = itemIds.indexOf(String(over.id));
    if (oldIndex >= 0 && newIndex >= 0) {
      setSelectedIndices([]);
      void reorderQueue(oldIndex, newIndex);
    }
  };

  const toggleSelect = (idx: number) => {
    setSelectedIndices((prev) =>
      prev.includes(idx) ? prev.filter((i) => i !== idx) : [...prev, idx]
    );
  };

  const handleRemoveSelected = async () => {
    if (selectedIndices.length === 0) return;
    await removeFromQueue(selectedIndices);
    setSelectedIndices([]);
  };

  const handleClearPlayed = async () => {
    if (current <= 0) return;
    const playedIndices = Array.from({ length: current }, (_, i) => i);
    await removeFromQueue(playedIndices);
  };

  const handleConfirmSavePlaylist = async (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = playlistName.trim();
    if (!trimmed || total === 0) return;
    setSavingPlaylist(true);
    try {
      await createPlaylist(
        trimmed,
        queue.items.map((t) => t.id)
      );
      setSaveModalOpen(false);
      setPlaylistName("");
    } finally {
      setSavingPlaylist(false);
    }
  };

  const renderSectionLabel = (text: string, isNow = false) => (
    <div
      ref={isNow ? nowSectionRef : undefined}
      className="pl-4 pt-2.5 pb-1 text-xs font-medium"
      style={{ color: "var(--text-secondary)" }}
    >
      {text}
    </div>
  );

  const renderRow = (track: Track, idx: number) => {
    const isCurrent = idx === current;
    const isPlayed = hasCurrent && idx < current;
    return (
      <SortableQueueItem
        key={itemIds[idx]}
        id={itemIds[idx]}
        track={track}
        index={idx}
        total={total}
        isCurrent={isCurrent}
        isPlayed={isPlayed}
        selected={selectedIndices.includes(idx)}
        selecting={selecting}
        menuOpen={rowMenuIndex === idx}
        onToggleMenu={() =>
          setRowMenuIndex((prev) => (prev === idx ? null : idx))
        }
        onCloseMenu={() => setRowMenuIndex(null)}
        onToggleSelect={toggleSelect}
        onPlay={(id) => void playTrack(id)}
        onMoveBy={(delta) => {
          const target = idx + delta;
          if (target >= 0 && target < total) {
            void reorderQueue(idx, target);
          }
        }}
        onRemove={() => void removeFromQueue([idx])}
        onOpenAlbum={() => {
          setQueueDrawerOpen(false);
          setFullPlayerOpen(false);
          setActiveNav("library");
          setLibraryTab("albums");
          setSearchQuery(track.album);
        }}
        onOpenArtist={(artist) => {
          setQueueDrawerOpen(false);
          setFullPlayerOpen(false);
          setActiveNav("library");
          setLibraryTab("artists");
          setSearchQuery(artist);
        }}
      />
    );
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/55 backdrop-blur-[2px]"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) {
          setQueueDrawerOpen(false);
        } else {
          setHeaderMenuOpen(false);
          setRowMenuIndex(null);
        }
      }}
    >
      {/* QueueTheme.kt: surfaceContainer = lerp(surface, primary, 0.06f), width=440dp, heightIn(max=600dp) */}
      <div
        className="w-[440px] max-w-[calc(100vw-32px)] max-h-[600px] rounded-[28px] flex flex-col overflow-hidden adaptive-transition pt-5 pb-3 px-2"
        style={{
          backgroundColor: "color-mix(in srgb, var(--surface-1) 94%, var(--accent) 6%)",
          border: "1px solid var(--outline)",
          boxShadow: "var(--shadow-elevated)",
          color: "var(--text-primary)",
        }}
      >
        {/* Header */}
        {selecting ? (
          <div className="pl-4 flex items-center justify-between gap-1">
            <div className="min-w-0 flex-1">
              <h2 className="text-2xl leading-8 font-normal">
                {selectedIndices.length} selected
              </h2>
              <p className="text-xs truncate" style={{ color: "var(--text-secondary)" }}>
                Right-click rows to add or remove them
              </p>
            </div>
            <button
              type="button"
              onClick={() => setSelectedIndices(queue.items.map((_, i) => i))}
              className="px-2.5 py-1.5 rounded-full text-sm font-medium hover:bg-[var(--surface-variant)] cursor-pointer shrink-0"
              style={{ color: "var(--accent)" }}
            >
              Select all
            </button>
            <button
              type="button"
              onClick={() => void handleRemoveSelected()}
              className="w-10 h-10 rounded-full flex items-center justify-center hover:bg-[var(--surface-variant)] cursor-pointer shrink-0"
              title="Remove selected tracks"
              aria-label="Remove selected tracks"
            >
              <Icon name="remove" size={24} />
            </button>
            <button
              type="button"
              onClick={() => setSelectedIndices([])}
              className="w-10 h-10 rounded-full flex items-center justify-center hover:bg-[var(--surface-variant)] cursor-pointer shrink-0"
              title="Cancel selection"
              aria-label="Cancel selection"
            >
              <Icon name="close" size={24} />
            </button>
          </div>
        ) : (
          <div className="pl-4 flex items-center justify-between gap-1">
            <div className="min-w-0 flex-1">
              <h2 className="text-2xl leading-8 font-normal">Queue</h2>
              <p className="text-xs mt-0.5" style={{ color: "var(--text-secondary)" }}>
                {total === 0
                  ? "Nothing queued"
                  : `${total} ${total === 1 ? "track" : "tracks"}`}
              </p>
            </div>

            <div className="relative">
              <button
                type="button"
                onClick={(e) => {
                  e.stopPropagation();
                  setHeaderMenuOpen((v) => !v);
                }}
                className="w-10 h-10 rounded-full flex items-center justify-center hover:bg-[var(--surface-variant)] cursor-pointer"
                title="Queue options"
                aria-label="Queue options"
              >
                <Icon name="more" size={24} />
              </button>

              {headerMenuOpen && (
                <div
                  className="absolute right-0 top-11 z-30 w-48 py-1.5 rounded-2xl shadow-2xl border"
                  style={{
                    backgroundColor: "var(--surface-3)",
                    borderColor: "var(--outline-strong)",
                  }}
                  onClick={(e) => e.stopPropagation()}
                >
                  <button
                    type="button"
                    disabled={current <= 0}
                    onClick={() => {
                      setHeaderMenuOpen(false);
                      void handleClearPlayed();
                    }}
                    className="w-full px-3.5 py-2 text-left text-xs hover:bg-[var(--surface-variant)] disabled:opacity-40 cursor-pointer"
                  >
                    Clear played
                  </button>
                  <button
                    type="button"
                    disabled={total === 0}
                    onClick={() => {
                      setHeaderMenuOpen(false);
                      setPlaylistName(`Queue (${new Date().toLocaleDateString()})`);
                      setSaveModalOpen(true);
                    }}
                    className="w-full px-3.5 py-2 text-left text-xs flex items-center gap-2.5 hover:bg-[var(--surface-variant)] disabled:opacity-40 cursor-pointer"
                  >
                    <Icon name="playlist_add" size={18} />
                    <span>Save as playlist</span>
                  </button>
                </div>
              )}
            </div>

            <button
              type="button"
              onClick={() => setQueueDrawerOpen(false)}
              className="w-10 h-10 rounded-full flex items-center justify-center hover:bg-[var(--surface-variant)] cursor-pointer"
              title="Close queue"
              aria-label="Close queue"
            >
              <Icon name="close" size={24} />
            </button>
          </div>
        )}

        {/* List */}
        <div className="mt-2 flex-1 overflow-y-auto pr-2 space-y-0.5">
          {total === 0 ? (
            <div
              className="h-40 flex items-center justify-center text-sm"
              style={{ color: "var(--text-secondary)" }}
            >
              Play a track to build the queue.
            </div>
          ) : (
            <DndContext
              sensors={sensors}
              collisionDetection={closestCenter}
              onDragEnd={handleDragEnd}
            >
              <SortableContext items={itemIds} strategy={verticalListSortingStrategy}>
                {hasCurrent && current > 0 && (
                  <>
                    {renderSectionLabel("Played")}
                    {queue.items.slice(0, current).map((t, idx) => renderRow(t, idx))}
                  </>
                )}

                {hasCurrent && (
                  <>
                    {renderSectionLabel("Now playing", true)}
                    {renderRow(queue.items[current], current)}
                  </>
                )}

                {(hasCurrent ? current + 1 : 0) < total && (
                  <>
                    {renderSectionLabel("Next up")}
                    {queue.items
                      .slice(hasCurrent ? current + 1 : 0)
                      .map((t, offset) =>
                        renderRow(t, (hasCurrent ? current + 1 : 0) + offset)
                      )}
                  </>
                )}
              </SortableContext>
            </DndContext>
          )}
        </div>
      </div>

      {/* Save queue as playlist dialog */}
      {saveModalOpen && (
        <div
          className="fixed inset-0 z-60 flex items-center justify-center p-4 bg-black/50"
          onMouseDown={(e) => {
            if (e.target === e.currentTarget && !savingPlaylist) {
              setSaveModalOpen(false);
            }
          }}
        >
          <form
            onSubmit={handleConfirmSavePlaylist}
            className="w-full max-w-sm rounded-[24px] p-6 space-y-4 shadow-2xl"
            style={{
              backgroundColor: "var(--surface-2)",
              border: "1px solid var(--outline)",
              color: "var(--text-primary)",
            }}
          >
            <h3 className="text-lg font-bold">Save queue as playlist</h3>
            <label className="block space-y-1.5">
              <span className="text-xs font-medium" style={{ color: "var(--text-secondary)" }}>
                Playlist name
              </span>
              <input
                type="text"
                autoFocus
                value={playlistName}
                onChange={(e) => setPlaylistName(e.target.value)}
                placeholder="Playlist name"
                className="w-full px-3.5 py-2.5 rounded-xl text-sm outline-none"
                style={{
                  backgroundColor: "var(--surface-1)",
                  border: "1px solid var(--outline-strong)",
                }}
              />
            </label>
            <div className="flex justify-end gap-2 pt-1">
              <button
                type="button"
                disabled={savingPlaylist}
                onClick={() => setSaveModalOpen(false)}
                className="px-4 py-2 rounded-full text-sm cursor-pointer hover:bg-[var(--surface-variant)]"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={savingPlaylist || !playlistName.trim()}
                className="px-5 py-2 rounded-full text-sm font-medium cursor-pointer disabled:opacity-45"
                style={{
                  backgroundColor: "var(--accent)",
                  color: "var(--on-accent)",
                }}
              >
                {savingPlaylist ? "Saving…" : "Save"}
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}
