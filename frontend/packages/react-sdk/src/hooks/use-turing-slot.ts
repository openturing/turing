import { useTuringSlots, type UseTuringSlotsOptions } from "./use-turing-slots";

export interface UseTuringSlotReturn {
  /** Current value of the slot, or {@code null} when not yet captured. */
  value: string | null;
  /** Conversation id the slots were read for. */
  conversationId: string | null;
  /** Whether the underlying request is still in flight (first load). */
  isLoading: boolean;
  /** Force a refetch of the underlying slots map. */
  refresh: () => void;
}

/**
 * Single-slot selector built on top of {@link useTuringSlots}. Most pages
 * that want to react to "the user told the chatbot their name" just need
 * one slot value — this helper trims the consumer code from a four-line
 * destructure to one.
 *
 * @example
 * ```tsx
 * const color = useTuringSlot("color", { pollInterval: 3000 });
 * if (!color.value) return null;
 * return <header style={{ background: color.value }}>...</header>;
 * ```
 *
 * @since 2026.2.7
 */
export function useTuringSlot(
  name: string,
  options?: UseTuringSlotsOptions,
): UseTuringSlotReturn {
  const { slots, conversationId, status, refresh } = useTuringSlots(options);
  const raw = slots[name];
  const value = raw && raw.length > 0 ? raw : null;
  return {
    value,
    conversationId,
    isLoading: status === "loading",
    refresh,
  };
}
