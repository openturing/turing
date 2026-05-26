import { useEffect, useState } from "react";
import axios from "axios";

/**
 * Polls the conversation's slot map for the {@code __scheduleAgent_pending_*}
 * markers the backend writes when parking on a {@code scheduleAgent}
 * chat-flow node (T48). Returns a stable summary the chat UI can use to
 * render the "Aguardando rotina…" banner without subscribing to the SSE
 * bus directly (agent-side SSE isn't wired in the console yet).
 *
 * <p>Disabled when {@code conversationId} is empty — costs nothing on a
 * fresh tab. The polling cadence is intentionally generous (default 2s)
 * because routine durations are seconds-to-minutes; we don't need
 * realtime here, just "did the marker disappear yet".
 *
 * @since 2026.3.1
 */

export interface ScheduleAgentWaitingState {
  /** {@code true} when at least one {@code __scheduleAgent_pending_*} marker is set. */
  waiting: boolean;
  /** Routine ids currently in flight (one per parked node). Useful for the UI label. */
  routineIds: string[];
}

interface Options {
  readonly agentId: string | undefined;
  readonly conversationId: string | undefined;
  readonly intervalMs?: number;
  readonly enabled?: boolean;
}

const PENDING_PREFIX = "__scheduleAgent_pending_";

export function useScheduleAgentWaiting({
  agentId,
  conversationId,
  intervalMs = 2000,
  enabled = true,
}: Options): ScheduleAgentWaitingState {
  const [state, setState] = useState<ScheduleAgentWaitingState>({
    waiting: false,
    routineIds: [],
  });

  useEffect(() => {
    if (!enabled || !agentId || !conversationId) {
      setState({ waiting: false, routineIds: [] });
      return;
    }
    let cancelled = false;

    const tick = async () => {
      try {
        const response = await axios.get<{ conversationId: string; slots: Record<string, string> }>(
          `/agent/${agentId}/chat-slots`,
          { params: { conversationId } },
        );
        const slots = response.data?.slots ?? {};
        const pendingRoutineIds: string[] = [];
        for (const [key, value] of Object.entries(slots)) {
          if (key.startsWith(PENDING_PREFIX) && value) {
            pendingRoutineIds.push(value);
          }
        }
        if (!cancelled) {
          setState((prev) => {
            const same =
              prev.waiting === pendingRoutineIds.length > 0 &&
              prev.routineIds.length === pendingRoutineIds.length &&
              prev.routineIds.every((id, i) => id === pendingRoutineIds[i]);
            return same
              ? prev
              : { waiting: pendingRoutineIds.length > 0, routineIds: pendingRoutineIds };
          });
        }
      } catch {
        // Slot endpoint is unauthenticated for the SDK path and authenticated
        // for the console; either way a transient failure should just leave
        // the previous state in place (no toast — banner missing for a tick
        // is far less disruptive than a flicker).
      }
    };

    void tick();
    const handle = window.setInterval(() => void tick(), intervalMs);
    return () => {
      cancelled = true;
      window.clearInterval(handle);
    };
  }, [agentId, conversationId, intervalMs, enabled]);

  return state;
}
