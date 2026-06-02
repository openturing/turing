/**
 * Module-level SSE multiplexer for the {@code /chat/slots/stream} endpoint.
 *
 * <p>Moved to the framework-agnostic {@code @viglet/turing-sdk} package — this
 * module re-exports it so existing `../core/slots-sse-multiplexer` imports keep
 * working. The multiplexer's connection registry is a module singleton living
 * in the SDK, so all subscribers (here and any vanilla consumer) share it.
 *
 * @since 2026.3.1
 */
export { subscribeSlotsSse } from "@viglet/turing-sdk";
export type { SlotsSseSubscriber, SlotsSseSubscription, SlotsSseOptions } from "@viglet/turing-sdk";
