/**
 * Module-level SSE multiplexer for the {@code /chat/workspace/stream} endpoint
 * (T113).
 *
 * <p>Lives in the framework-agnostic {@code @viglet/turing-sdk} package — this
 * module re-exports it so the React hook imports a stable
 * `../core/workspace-sse-multiplexer` path. The multiplexer's connection
 * registry is a module singleton in the SDK, so all subscribers (here and any
 * vanilla consumer) share it.
 *
 * @since 2026.3.1
 */
export { subscribeWorkspaceSse } from "@viglet/turing-sdk";
export type {
  WorkspaceSseSubscriber,
  WorkspaceSseSubscription,
  WorkspaceSseScope,
} from "@viglet/turing-sdk";
