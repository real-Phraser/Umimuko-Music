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
import { useState } from "react";
import { usePlayerStore } from "../store/usePlayerStore";
import type { Track } from "../types/ipc";
import { formatDuration } from "../utils/format";
import { Artwork } from "./Artwork";
import { Icon } from "./Icon";

interface SortableQueueItemProps {
  id: string;
  track: Track;
  index: number;
  isCurrent: boolean;
  selected: boolean;
  onToggleSelect: (index: number) => void;
  onPlay: (trackId: string) => void;
}

function SortableQueueItem({
  id,
  track,
  index,
  isCurrent,
  selected,
  onToggleSelect,
  onPlay,
}: SortableQueueItemProps) {
  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({ id });

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    backgroundColor: isCurrent
      ? "var(--accent-container)"
      : selected
        ? "var(--surface-3)"
        : "transparent",
    opacity: isDragging ? 0.6 : 1,
  };

  return (
    <div
      ref={setNodeRef}
      style={style}
      className="flex items-center gap-2.5 px-2.5 py-2 rounded-2xl group"
    >
      <input
        type="checkbox"
        checked={selected}
        onChange={() => onToggleSelect(index)}
        className="accent-[var(--accent)] cursor-pointer shrink-0"
        aria-label={`Select ${track.title}`}
      />

      <button
        type="button"
        {...attributes}
        {...listeners}
        className="p-1 rounded-lg cursor-grab active:cursor-grabbing shrink-0"
        style={{ color: "var(--text-muted)" }}
        title="Drag to reorder"
      >
        <Icon name="drag" size={16} />
      </button>

      <button
        type="button"
        onClick={() => onPlay(track.id)}
        className="flex items-center gap-2.5 min-w-0 flex-1 text-left cursor-pointer"
      >
        <Artwork
          artworkPath={track.artworkPath}
          accentColor={track.accentColor}
          alt={track.title}
          size="xs"
          rounded="rounded-lg"
        />
        <div className="min-w-0 flex-1">
          <div
            className="text-xs font-medium truncate"
            style={{ color: isCurrent ? "var(--accent)" : "var(--text-primary)" }}
          >
            {track.title}
          </div>
          <div className="text-[11px] truncate" style={{ color: "var(--text-secondary)" }}>
            {track.artist}
          </div>
        </div>
        <span className="text-[11px] tabular-nums shrink-0" style={{ color: "var(--text-muted)" }}>
          {formatDuration(track.durationMs)}
        </span>
      </button>
    </div>
  );
}

export function QueueDrawer() {
  const queueDrawerOpen = usePlayerStore((s) => s.queueDrawerOpen);
  const setQueueDrawerOpen = usePlayerStore((s) => s.setQueueDrawerOpen);
  const queue = usePlayerStore((s) => s.queue);
  const reorderQueue = usePlayerStore((s) => s.reorderQueue);
  const removeFromQueue = usePlayerStore((s) => s.removeFromQueue);
  const playTrack = usePlayerStore((s) => s.playTrack);

  const [selectedIndices, setSelectedIndices] = useState<number[]>([]);

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 4 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates })
  );

  if (!queueDrawerOpen) return null;

  const itemIds = queue.items.map((t, idx) => `${t.id}__${idx}`);

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

  return (
    <aside
      className="w-80 shrink-0 h-full border-l flex flex-col adaptive-transition"
      style={{
        backgroundColor: "var(--surface-1)",
        borderColor: "var(--outline)",
      }}
    >
      <div
        className="px-4 py-3.5 border-b flex items-center justify-between"
        style={{ borderColor: "var(--outline)" }}
      >
        <div>
          <h2 className="text-sm font-semibold">Up Next</h2>
          <p className="text-[11px]" style={{ color: "var(--text-muted)" }}>
            {queue.items.length} tracks in queue
          </p>
        </div>
        <button
          type="button"
          onClick={() => setQueueDrawerOpen(false)}
          className="p-1.5 rounded-full cursor-pointer"
          style={{ color: "var(--text-secondary)" }}
          title="Close Queue"
        >
          <Icon name="close" size={18} />
        </button>
      </div>

      {selectedIndices.length > 0 && (
        <div
          className="px-3 py-2 border-b flex items-center justify-between text-xs"
          style={{
            backgroundColor: "var(--surface-2)",
            borderColor: "var(--outline)",
          }}
        >
          <span>{selectedIndices.length} selected</span>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => setSelectedIndices([])}
              className="px-2 py-1 rounded-full cursor-pointer"
              style={{ color: "var(--text-secondary)" }}
            >
              Clear
            </button>
            <button
              type="button"
              onClick={() => void handleRemoveSelected()}
              className="px-2.5 py-1 rounded-full font-medium flex items-center gap-1 cursor-pointer"
              style={{
                backgroundColor: "var(--danger-container)",
                color: "var(--danger)",
              }}
            >
              <Icon name="remove" size={14} />
              <span>Remove</span>
            </button>
          </div>
        </div>
      )}

      <div className="flex-1 overflow-y-auto p-2 space-y-1">
        {queue.items.length === 0 ? (
          <div
            className="py-12 text-center text-xs"
            style={{ color: "var(--text-muted)" }}
          >
            Queue is empty.
          </div>
        ) : (
          <DndContext
            sensors={sensors}
            collisionDetection={closestCenter}
            onDragEnd={handleDragEnd}
          >
            <SortableContext items={itemIds} strategy={verticalListSortingStrategy}>
              {queue.items.map((track, idx) => (
                <SortableQueueItem
                  key={itemIds[idx]}
                  id={itemIds[idx]}
                  track={track}
                  index={idx}
                  isCurrent={idx === queue.currentIndex}
                  selected={selectedIndices.includes(idx)}
                  onToggleSelect={toggleSelect}
                  onPlay={(id) => void playTrack(id)}
                />
              ))}
            </SortableContext>
          </DndContext>
        )}
      </div>
    </aside>
  );
}
