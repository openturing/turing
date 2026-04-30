import { useEffect, useState } from "react";
import { fetchIntents } from "../core/api";
import { useTuringContext } from "../core/use-turing-context";
import type { TurIntent } from "../core/types";

export interface UseTuringIntentsReturn {
  /** Enabled intents (already sorted by `sortOrder`) */
  intents: TurIntent[];
  /** Whether the request is in flight */
  loading: boolean;
  /** Error message from the last failed fetch */
  error: string | null;
  /** Re-fetch intents */
  refresh: () => void;
}

/**
 * Loads the curated list of enabled intents for the site's AI agent.
 * Use the resulting `intents[].actions` to render quick-prompt chips below
 * a chat composer or empty state.
 *
 * @example
 * ```tsx
 * const { intents } = useTuringIntents();
 * const flatActions = intents.flatMap((i) => i.actions);
 * return flatActions.map((a) => (
 *   <button key={a.id ?? a.label} onClick={() => send(a.prompt)}>
 *     {a.label}
 *   </button>
 * ));
 * ```
 *
 * @since 2026.2.12
 */
export function useTuringIntents(): UseTuringIntentsReturn {
  const { config } = useTuringContext();
  const [intents, setIntents] = useState<TurIntent[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [tick, setTick] = useState(0);

  useEffect(() => {
    let alive = true;
    setLoading(true);
    setError(null);
    fetchIntents(config.site)
      .then((res) => {
        if (!alive) return;
        setIntents(Array.isArray(res) ? res : []);
        setLoading(false);
      })
      .catch((err) => {
        if (!alive) return;
        setError(err instanceof Error ? err.message : "Failed to load intents");
        setIntents([]);
        setLoading(false);
      });
    return () => {
      alive = false;
    };
  }, [config.site, tick]);

  return {
    intents,
    loading,
    error,
    refresh: () => setTick((t) => t + 1),
  };
}
