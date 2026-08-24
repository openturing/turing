/**
 * T1024 — the hook that remembers which grid columns a reader hid.
 *
 * Split from the menu component it pairs with: it renders nothing and touches
 * `localStorage`, and a module exporting both a component and a hook reloads
 * whole instead of preserving state.
 */
import { useCallback, useEffect, useState } from "react"

/**
 * Persists column visibility to localStorage under a stable key. Returns the
 * state suitable for `useReactTable({ state: { columnVisibility }, onColumnVisibilityChange })`,
 * plus a `reset()` callback for the reset menu entry.
 *
 * @since 2026.2.8
 */
export function usePersistedColumnVisibility(storageKey: string) {
  const [columnVisibility, setColumnVisibility] = useState<Record<string, boolean>>(() => {
    try {
      const raw = localStorage.getItem(storageKey);
      return raw ? (JSON.parse(raw) as Record<string, boolean>) : {};
    } catch {
      return {};
    }
  });

  useEffect(() => {
    try {
      if (Object.keys(columnVisibility).length === 0) {
        localStorage.removeItem(storageKey);
      } else {
        localStorage.setItem(storageKey, JSON.stringify(columnVisibility));
      }
    } catch {
      // localStorage unavailable (private mode, quota) — silently ignore
    }
  }, [storageKey, columnVisibility]);

  const reset = useCallback(() => setColumnVisibility({}), []);

  return { columnVisibility, setColumnVisibility, reset };
}
