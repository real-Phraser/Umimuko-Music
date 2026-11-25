import { useEffect, useRef, useState } from "react";
import startupIntroUrl from "../assets/branding/startup-intro.webp";

const STARTUP_INTRO_PREF_KEY = "umimuko_startup_intro";

/** Total duration of all 76 frames in `startup-intro.webp` (ms), matching Skia codec playback. */
const INTRO_DURATION_MS = 2159;
/** Fade-out duration (ms), matching `cover.animateTo(0f, tween(260))` in Motion.kt. */
const FADE_OUT_MS = 260;
/** Safety fallback if the image load event does not fire promptly, so startup is never blocked. */
const LOAD_TIMEOUT_MS = 1500;

export function getStartupIntroEnabled(): boolean {
  try {
    const raw = localStorage.getItem(STARTUP_INTRO_PREF_KEY);
    return raw === null ? true : raw === "true";
  } catch {
    return true;
  }
}

export function setStartupIntroEnabled(enabled: boolean): void {
  try {
    localStorage.setItem(STARTUP_INTRO_PREF_KEY, String(enabled));
  } catch {
    // Ignore storage errors
  }
}

interface StartupIntroProps {
  onFinished: () => void;
}

/**
 * Fullscreen startup animation played from `branding/startup-intro.webp`,
 * followed by a 260ms fade-out into the main window (Motion.kt `StartupIntro`).
 */
export function StartupIntro({ onFinished }: StartupIntroProps) {
  const [fadingOut, setFadingOut] = useState(false);
  const imgRef = useRef<HTMLImageElement | null>(null);
  const activeRef = useRef(false);
  const startedRef = useRef(false);
  const finishedRef = useRef(false);
  const timersRef = useRef<number[]>([]);
  const onFinishedRef = useRef(onFinished);
  onFinishedRef.current = onFinished;

  const scheduleFadeAndFinish = (delayMs: number) => {
    if (!activeRef.current || startedRef.current || finishedRef.current) return;
    startedRef.current = true;
    const t1 = window.setTimeout(() => {
      if (!activeRef.current) return;
      setFadingOut(true);
      const t2 = window.setTimeout(() => {
        if (activeRef.current && !finishedRef.current) {
          finishedRef.current = true;
          onFinishedRef.current();
        }
      }, FADE_OUT_MS);
      timersRef.current.push(t2);
    }, delayMs);
    timersRef.current.push(t1);
  };

  useEffect(() => {
    activeRef.current = true;
    startedRef.current = false;
    finishedRef.current = false;

    if (imgRef.current?.complete) {
      scheduleFadeAndFinish(
        imgRef.current.naturalWidth > 0 ? INTRO_DURATION_MS : 0
      );
    }

    const fallbackTimer = window.setTimeout(() => {
      scheduleFadeAndFinish(0);
    }, LOAD_TIMEOUT_MS);

    return () => {
      activeRef.current = false;
      window.clearTimeout(fallbackTimer);
      for (const id of timersRef.current) {
        window.clearTimeout(id);
      }
      timersRef.current = [];
    };
  }, []);

  return (
    <div
      className="fixed inset-0 z-[9999] flex items-center justify-center bg-black select-none"
      style={{
        opacity: fadingOut ? 0 : 1,
        transition: `opacity ${FADE_OUT_MS}ms cubic-bezier(0.4, 0, 0.2, 1)`,
      }}
      onClick={(e) => e.stopPropagation()}
      aria-hidden="true"
    >
      <img
        ref={imgRef}
        src={startupIntroUrl}
        alt=""
        draggable={false}
        onLoad={() => scheduleFadeAndFinish(INTRO_DURATION_MS)}
        onError={() => scheduleFadeAndFinish(0)}
        className="w-full h-full object-contain pointer-events-none"
      />
    </div>
  );
}
