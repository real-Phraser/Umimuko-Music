import { create } from "zustand";

interface AppState {
  ready: boolean;
}

const useAppStore = create<AppState>(() => ({
  ready: true,
}));

export function App() {
  const ready = useAppStore((s) => s.ready);

  return (
    <main className="flex min-h-screen items-center justify-center bg-zinc-950 text-zinc-100">
      <div className="text-center">
        <h1 className="text-2xl font-semibold tracking-tight">Umimuko Music</h1>
        <p className="mt-2 text-sm text-zinc-400">
          {ready ? "Tauri v2 + React + Tailwind v4 scaffold ready" : "Initializing..."}
        </p>
      </div>
    </main>
  );
}

export default App;
