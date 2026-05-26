import {
  useTuringRoutineWaiting,
  type UseTuringRoutineWaitingReturn,
} from "./use-turing-routine-waiting";

export interface UseTuringScheduleAgentWaitingOptions {
  /** AI Agent id whose conversation slot map should be polled. */
  readonly agentId: string | undefined;
  /**
   * Conversation id to key the slot read on. When omitted the underlying
   * slot hook reads the {@code TUR_SESSION} cookie set by
   * {@link useTuringChat}, mirroring the public SDK default.
   */
  readonly conversationId?: string;
  /**
   * Slot-polling cadence in milliseconds. Defaults to {@code 2000} — the
   * cadence the admin chat shipped with — because routine durations are
   * seconds-to-minutes and we don't need realtime here.
   */
  readonly intervalMs?: number;
  /** Disables the underlying polling. Defaults to {@code true}. */
  readonly enabled?: boolean;
}

export type UseTuringScheduleAgentWaitingReturn = UseTuringRoutineWaitingReturn;

/**
 * Admin-flavoured alias of {@link useTuringRoutineWaiting} that takes a
 * bare {@code agentId} (instead of {@code agent: { id }}) and defaults
 * {@code pollInterval} to {@code 2000 ms}. Drop-in replacement for the
 * admin chat's local {@code useScheduleAgentWaiting} hook so the admin
 * page can migrate to the SDK without changing its call sites.
 *
 * <p>Returns the same shape as the parent hook: {@code waiting} flips
 * {@code true} while at least one {@code __scheduleAgent_pending_*}
 * marker slot is set; {@code routineIds} lists the routine ids in flight
 * for the banner copy.
 *
 * @since 2026.3.1
 */
export function useTuringScheduleAgentWaiting({
  agentId,
  conversationId,
  intervalMs = 2000,
  enabled = true,
}: UseTuringScheduleAgentWaitingOptions): UseTuringScheduleAgentWaitingReturn {
  return useTuringRoutineWaiting({
    agent: agentId ? { id: agentId } : undefined,
    conversationId,
    pollInterval: intervalMs,
    enabled: enabled && Boolean(agentId),
  });
}
