import { useCallback } from "react";
import { postClick } from "../core/api";
import type { ClickTrackParams } from "../core/api";
import { readTurSession } from "../core/session";
import { useSearchStore } from "../core/use-search-store";
import { useTuringContext } from "../core/use-turing-context";

/**
 * Return shape of {@link useTuringClickTracking}.
 *
 * @since 2026.2.7
 */
export interface UseTuringClickTrackingReturn {
  /**
   * Record a click on a result. Fire-and-forget — never throws, never blocks.
   *
   * @param documentId  Identifier of the clicked result (any value the
   *                    backend can later correlate, typically the document
   *                    {@code id} or {@code url}).
   * @param position    1-based rank of the clicked result within the page.
   * @param overrides   Optional override of any field auto-derived from the
   *                    current search context (term, userId, locale). Useful
   *                    for components that want to attribute the click to a
   *                    different query than the one in the store.
   */
  trackClick: (documentId: string, position: number,
    overrides?: Partial<ClickTrackParams>) => void;
}

/**
 * Wraps {@link postClick} with the current site, query, and session cookie
 * pulled from the {@link TuringProvider} context. Components only need to
 * supply the document id and its rank — the hook fills in the rest.
 *
 * @example
 * ```tsx
 * function ResultCard({ doc, index }: ResultItemProps) {
 *   const { trackClick } = useTuringClickTracking();
 *   return (
 *     <a
 *       href={doc.url}
 *       onClick={() => trackClick(doc.id ?? doc.url ?? "", index + 1)}
 *     >
 *       {doc.title}
 *     </a>
 *   );
 * }
 * ```
 *
 * @since 2026.2.7
 */
export function useTuringClickTracking(): UseTuringClickTrackingReturn {
  const { config } = useTuringContext();
  const store = useSearchStore();

  const trackClick = useCallback(
    (documentId: string, position: number, overrides?: Partial<ClickTrackParams>) => {
      if (!documentId) return;
      const term = overrides?.term ?? store?.params.q ?? "";
      const locale = overrides?.locale ?? store?.params._setlocale ?? config.locale;
      const userId = overrides?.userId ?? readTurSession() ?? undefined;
      // Fire-and-forget — `postClick` itself swallows errors.
      void postClick(config.site, {
        term,
        documentId,
        position,
        userId,
        locale,
        ...overrides,
      });
    },
    [config.site, config.locale, store?.params.q, store?.params._setlocale],
  );

  return { trackClick };
}
