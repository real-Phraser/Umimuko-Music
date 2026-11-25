import { useEffect, type ReactNode } from "react";
import { Icon } from "../Icon";

interface DialogShellProps {
  title: string;
  subtitle?: string;
  onClose: () => void;
  maxWidth?: string;
  children: ReactNode;
}

export function DialogShell({
  title,
  subtitle,
  onClose,
  maxWidth = "max-w-2xl",
  children,
}: DialogShellProps) {
  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [onClose]);

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div
        className={`w-full ${maxWidth} max-h-[86vh] flex flex-col rounded-3xl shadow-2xl overflow-hidden adaptive-transition`}
        style={{
          backgroundColor: "var(--surface-2)",
          border: "1px solid var(--outline)",
          boxShadow: "var(--shadow-elevated)",
        }}
      >
        <div
          className="px-6 py-4 border-b flex items-center justify-between"
          style={{ borderColor: "var(--outline)" }}
        >
          <div>
            <h2 className="text-lg font-semibold">{title}</h2>
            {subtitle && (
              <p className="text-xs mt-0.5" style={{ color: "var(--text-secondary)" }}>
                {subtitle}
              </p>
            )}
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-2 rounded-full hover:opacity-80 cursor-pointer"
            style={{ backgroundColor: "var(--surface-3)" }}
            title="Close"
          >
            <Icon name="close" size={18} />
          </button>
        </div>

        <div className="p-6 overflow-y-auto">{children}</div>
      </div>
    </div>
  );
}
