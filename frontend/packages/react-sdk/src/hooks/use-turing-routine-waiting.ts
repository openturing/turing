import { useMemo } from "react";
import { useTuringSlots, type UseTuringSlotsOptions } from "./use-turing-slots";

/**
 * Marker-slot prefix the chat-flow engine writes when parking on a
 * {@code scheduleAgent} node (T48). One marker per node, keyed by the
 * node id and holding the routine id that's in flight. The slot
 * disappears when the routine completes (slot bus auto-resume advances
 * the flow past the node) or when the timeout edge fires.
 */
const PENDING_SLOT_PREFIX = "__scheduleAgent_pending_";

export type UseTuringRoutineWaitingOptions = UseTuringSlotsOptions;

export interface UseTuringRoutineWaitingReturn {
  /**
   * {@code true} when at least one {@code scheduleAgent} marker slot is
   * set on the conversation. Drives "Aguardando rotina…" indicators in
   * portal apps without each one re-implementing the slot subscription.
   */
  waiting: boolean;
  /** Routine ids currently in flight (one entry per parked node). */
  routineIds: readonly string[];
  /** Underlying conversation id the markers were read for, or null. */
  conversationId: string | null;
}

/**
 * Derives the "scheduleAgent waiting" indicator from the live slot bus
 * (or polling fallback in agent mode). Use to render a small banner in
 * marketplace / portal chat surfaces while a long-running routine is
 * still in flight on the parked chat-flow node.
 *
 * <p>Wraps {@link useTuringSlots} so SSE/polling, conversation-id
 * resolution, and the underlying multiplexer are shared with any other
 * slot consumer on the page.
 *
 * @example
 * ```tsx
 * const { waiting, routineIds } = useTuringRoutineWaiting({ transport: "sse" });
 * if (waiting) {
 *   return <Banner>⏳ Aguardando {routineIds.length} rotina(s)…</Banner>;
 * }
 * ```
 *
 * @since 2026.3.1
 */
export function useTuringRoutineWaiting(
  options: UseTuringRoutineWaitingOptions = {},
): UseTuringRoutineWaitingReturn {
  const { slots, conversationId } = useTuringSlots(options);

  return useMemo(() => {
    const ids: string[] = [];
    for (const [key, value] of Object.entries(slots)) {
      if (key.startsWith(PENDING_SLOT_PREFIX) && value) {
        ids.push(value);
      }
    }
    return {
      waiting: ids.length > 0,
      routineIds: ids,
      conversationId,
    };
  }, [slots, conversationId]);
}
