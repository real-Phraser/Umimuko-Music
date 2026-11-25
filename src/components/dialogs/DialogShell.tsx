import { useEffect, type ReactNode } from "react";
import { Icon, type IconName } from "../Icon";

interface DialogShellProps {
  title: string;
  subtitle?: string;
  icon?: IconName;
  headerRight?: ReactNode;
  onClose: () => void;
  maxWidth?: string;
  children: ReactNode;
}

export function DialogShell({
  title,
  subtitle,
  icon,
  headerRight,
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
      className="fixed inset-0 z-50 flex items-center justify-center p-6 bg-black/55 backdrop-blur-[2px]"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div
        className={`w-full ${maxWidth} max-h-[90vh] flex flex-col rounded-[20px] overflow-hidden adaptive-transition`}
        style={{
          backgroundColor: "var(--surface-1)",
          color: "var(--text-primary)",
          border: "1px solid var(--outline)",
          boxShadow: "var(--shadow-elevated)",
        }}
      >
        <div
          className="px-6 pt-6 pb-4 border-b flex items-center justify-between gap-4"
          style={{ borderColor: "var(--outline)" }}
        >
          <div className="flex items-center gap-3 min-w-0">
            {icon && (
              <div
                className="w-9 h-9 rounded-full flex items-center justify-center shrink-0"
                style={{
                  backgroundColor: "var(--accent-container)",
                  color: "var(--on-accent-container)",
                }}
              >
                <Icon name={icon} size={20} />
              </div>
            )}
            <div className="min-w-0">
              <h2 className="text-2xl leading-8 font-bold truncate">{title}</h2>
              {subtitle && (
                <p
                  className="text-xs mt-0.5 truncate"
                  style={{ color: "var(--text-secondary)" }}
                >
                  {subtitle}
                </p>
              )}
            </div>
          </div>

          <div className="flex items-center gap-2 shrink-0">
            {headerRight}
            <button
              type="button"
              onClick={onClose}
              className="w-9 h-9 rounded-full flex items-center justify-center hover:bg-[var(--surface-variant)] cursor-pointer"
              style={{ color: "var(--text-primary)" }}
              title="Close"
              aria-label="Close"
            >
              <Icon name="close" size={20} />
            </button>
          </div>
        </div>

        <div className="p-6 overflow-y-auto">{children}</div>
      </div>
    </div>
  );
}
