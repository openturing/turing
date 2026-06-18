import { useCallback, useEffect, useRef, useState } from "react";
import { fetchSpellCheck } from "../core/api";
import { useTuringContext } from "../core/use-turing-context";
import type { TurSpellCheck } from "../core/types";

export interface UseTuringSpellCheckOptions {
  /**
   * Query to check. When set, the hook auto-checks on mount and whenever the
   * query changes. Omit to drive checks imperatively via
   * {@link UseTuringSpellCheckReturn.check}.
   */
  readonly query?: string;
  /** Locale path segment (e.g. {@code "en"}); falls back to the provider locale. */
  readonly locale?: string;
  /** Disables the auto-check when {@code false}. Defaults to {@code true}. */
  readonly enabled?: boolean;
}

export interface UseTuringSpellCheckReturn {
  /** The spell-check result, or {@code null} before the first check. */
  readonly spellCheck: TurSpellCheck | null;
  /** Convenience: the suggested correction text, or {@code null} when none. */
  readonly suggestion: string | null;
  /** Whether a check is in flight. */
  readonly loading: boolean;
  /** Error message from the last failed check, or {@code null}. */
  readonly error: string | null;
  /** Imperatively run a spell-check for a given query. */
  readonly check: (query: string) => Promise<void>;
}

/**
 * T402 — fetches "did you mean" corrections for a query (T402's
 * {@code GET /sn/{site}/{locale}/spell-check}). Lets a search box surface a
 * correction prompt without parsing the full search bean.
 *
 * @example
 * ```tsx
 * const { suggestion } = useTuringSpellCheck({ query, locale: "en" });
 * {suggestion && <button onClick={() => search(suggestion)}>Did you mean {suggestion}?</button>}
 * ```
 *
 * @since 2026.3.4
 */
export function useTuringSpellCheck(
  options: UseTuringSpellCheckOptions = {},
): UseTuringSpellCheckReturn {
  const { config } = useTuringContext();
  const { query, locale, enabled = true } = options;
  const resolvedLocale = locale ?? config.locale ?? "en";

  const [spellCheck, setSpellCheck] = useState<TurSpellCheck | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const requestIdRef = useRef(0);

  const check = useCallback(
    async (q: string): Promise<void> => {
      if (!q) {
        setSpellCheck(null);
        return;
      }
      const requestId = ++requestIdRef.current;
      setLoading(true);
      setError(null);
      try {
        const res = await fetchSpellCheck(config.site, resolvedLocale, q);
        if (requestId !== requestIdRef.current) return;
        setSpellCheck(res ?? null);
        setLoading(false);
      } catch (err) {
        if (requestId !== requestIdRef.current) return;
        setError(err instanceof Error ? err.message : "Spell-check failed");
        setSpellCheck(null);
        setLoading(false);
      }
    },
    [config.site, resolvedLocale],
  );

  useEffect(() => {
    if (!enabled || !query) return;
    void check(query);
  }, [enabled, query, check]);

  const suggestion =
    spellCheck?.correctedText && spellCheck.corrected?.text
      ? spellCheck.corrected.text
      : null;

  return { spellCheck, suggestion, loading, error, check };
}
