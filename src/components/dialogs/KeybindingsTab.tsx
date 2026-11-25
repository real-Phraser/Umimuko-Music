import { useEffect, useState } from "react";
import {
  SHORTCUT_ACTIONS,
  SHORTCUT_ACTION_META,
  isMacPlatform,
  useSettingsStore,
  type ShortcutCategory,
} from "../../store/useSettingsStore";
import type { KeyCombination, ShortcutAction } from "../../types/ipc";
import { Icon } from "../Icon";

const MODIFIER_KEYS = new Set(["Shift", "Control", "Alt", "Meta", "AltGraph"]);

const KEY_CODE_LABELS: Record<string, string> = {
  ArrowRight: "→",
  ArrowLeft: "←",
  ArrowUp: "↑",
  ArrowDown: "↓",
  Enter: "↵ Enter",
  NumpadEnter: "↵ Enter",
  Escape: "Esc",
  Comma: ",",
  Period: ".",
  Slash: "/",
  Backslash: "\\",
  Minus: "-",
  Equal: "=",
  BracketLeft: "[",
  BracketRight: "]",
  Semicolon: ";",
  Quote: "'",
  Backquote: "`",
  Tab: "Tab",
  Backspace: "Backspace",
  Delete: "Delete",
};

function formatKeySymbol(code: string, isMac: boolean): string {
  if (code === "Space") {
    return isMac ? "␣ Space" : "Space";
  }
  if (code in KEY_CODE_LABELS) {
    return KEY_CODE_LABELS[code];
  }
  if (code.startsWith("Key")) {
    return code.slice(3);
  }
  if (code.startsWith("Digit")) {
    return code.slice(5);
  }
  return code;
}

export function formatKeyCombination(
  combo: KeyCombination | null | undefined,
  isMac: boolean = isMacPlatform()
): string {
  if (!combo) return "None";
  const parts: string[] = [];
  if (isMac) {
    if (combo.ctrl) parts.push("⌃");
    if (combo.alt) parts.push("⌥");
    if (combo.shift) parts.push("⇧");
    if (combo.meta) parts.push("⌘");
  } else {
    if (combo.ctrl) parts.push("Ctrl");
    if (combo.alt) parts.push("Alt");
    if (combo.shift) parts.push("Shift");
    if (combo.meta) parts.push("Win");
  }
  parts.push(formatKeySymbol(combo.code, isMac));
  return isMac ? parts.join(" ") : parts.join(" + ");
}

const CATEGORIES: ShortcutCategory[] = ["Playback", "Audio", "View"];

export function KeybindingsTab() {
  const shortcutBindings = useSettingsStore((s) => s.shortcutBindings);
  const setShortcutBinding = useSettingsStore((s) => s.setShortcutBinding);
  const resetShortcutAction = useSettingsStore((s) => s.resetShortcutAction);
  const resetAllShortcuts = useSettingsStore((s) => s.resetAllShortcuts);

  const [recordingAction, setRecordingAction] = useState<ShortcutAction | null>(
    null
  );
  const [conflictMessage, setConflictMessage] = useState<string | null>(null);

  const isMac = isMacPlatform();

  useEffect(() => {
    if (!recordingAction) return;

    const onKeyDown = (e: KeyboardEvent) => {
      e.preventDefault();
      e.stopPropagation();

      if (e.key === "Escape") {
        setRecordingAction(null);
        return;
      }

      if (MODIFIER_KEYS.has(e.key)) {
        return;
      }

      const newCombo: KeyCombination = {
        code: e.code,
        ctrl: e.ctrlKey,
        meta: e.metaKey,
        alt: e.altKey,
        shift: e.shiftKey,
      };

      const conflict = setShortcutBinding(recordingAction, newCombo);
      if (conflict) {
        setConflictMessage(
          `⚠️ Shortcut replaced from '${SHORTCUT_ACTION_META[conflict].title}'`
        );
      } else {
        setConflictMessage(null);
      }
      setRecordingAction(null);
    };

    window.addEventListener("keydown", onKeyDown, true);
    return () => window.removeEventListener("keydown", onKeyDown, true);
  }, [recordingAction, setShortcutBinding]);

  return (
    <div className="space-y-[18px]">
      <div className="flex items-center justify-between gap-3">
        <div>
          <div className="text-base font-bold">Keyboard Shortcuts</div>
          <div
            className="text-xs mt-0.5"
            style={{ color: "var(--text-secondary)" }}
          >
            Customize application hotkeys. Press Esc during recording to cancel.
          </div>
        </div>
        <button
          type="button"
          onClick={() => {
            resetAllShortcuts();
            setConflictMessage(null);
            setRecordingAction(null);
          }}
          className="px-3.5 py-2 rounded-xl text-xs font-medium cursor-pointer shrink-0"
          style={{ border: "1px solid var(--outline-strong)" }}
        >
          Reset All to Defaults
        </button>
      </div>

      {conflictMessage && (
        <div
          className="px-3.5 py-2.5 rounded-xl flex items-center justify-between gap-3 text-xs"
          style={{
            backgroundColor: "var(--danger-container)",
            border: "1px solid var(--danger)",
            color: "var(--danger)",
          }}
        >
          <span>{conflictMessage}</span>
          <button
            type="button"
            onClick={() => setConflictMessage(null)}
            className="w-6 h-6 rounded-full flex items-center justify-center cursor-pointer"
          >
            <Icon name="close" size={16} />
          </button>
        </div>
      )}

      {CATEGORIES.map((category) => {
        const actions = SHORTCUT_ACTIONS.filter(
          (a) => SHORTCUT_ACTION_META[a].category === category
        );
        return (
          <div key={category} className="space-y-2">
            <div
              className="text-[11px] font-bold uppercase tracking-wider pt-1"
              style={{ color: "var(--accent)" }}
            >
              {category}
            </div>
            <div
              className="px-4 py-2 rounded-2xl border"
              style={{
                backgroundColor: "var(--surface-2)",
                borderColor: "var(--outline)",
              }}
            >
              {actions.map((action, idx) => {
                const combo = shortcutBindings[action];
                const isRecording = recordingAction === action;
                const title = SHORTCUT_ACTION_META[action].title;

                return (
                  <div key={action}>
                    <div className="py-2 flex items-center justify-between gap-3">
                      <span className="text-sm font-medium">{title}</span>
                      <div className="flex items-center gap-2">
                        {isRecording ? (
                          <span
                            className="px-3 py-1.5 rounded-[10px] text-xs font-bold border-2"
                            style={{
                              backgroundColor: "var(--accent-container)",
                              borderColor: "var(--accent)",
                              color: "var(--on-accent-container)",
                            }}
                          >
                            Press keys... (Esc)
                          </span>
                        ) : (
                          <>
                            <span
                              className="px-2.5 py-1 rounded-[10px] font-mono text-xs font-semibold"
                              style={{
                                backgroundColor: "var(--surface-3)",
                                border: "1px solid var(--outline)",
                                color: "var(--text-secondary)",
                              }}
                            >
                              {formatKeyCombination(combo, isMac)}
                            </span>
                            <button
                              type="button"
                              onClick={() => {
                                setConflictMessage(null);
                                setRecordingAction(action);
                              }}
                              className="h-8 px-3 rounded-[10px] text-xs font-medium cursor-pointer"
                              style={{ border: "1px solid var(--outline-strong)" }}
                            >
                              Rebind
                            </button>
                            <button
                              type="button"
                              onClick={() => {
                                resetShortcutAction(action);
                                setConflictMessage(null);
                              }}
                              className="w-8 h-8 rounded-full flex items-center justify-center hover:bg-[var(--surface-variant)] cursor-pointer"
                              style={{ color: "var(--text-secondary)" }}
                              title="Reset shortcut"
                              aria-label={`Reset ${title}`}
                            >
                              <Icon name="restore" size={16} />
                            </button>
                          </>
                        )}
                      </div>
                    </div>
                    {idx < actions.length - 1 && (
                      <div
                        className="h-px my-0.5"
                        style={{ backgroundColor: "var(--outline)" }}
                      />
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        );
      })}
    </div>
  );
}
