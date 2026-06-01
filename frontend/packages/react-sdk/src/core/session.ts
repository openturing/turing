/**
 * Cross-domain session cookie helpers.
 *
 * <p>Moved to the framework-agnostic {@code @viglet/turing-sdk} package — this
 * module re-exports them so existing `../core/session` imports keep working.
 * Single source of truth lives in the vanilla SDK.
 *
 * @since 2026.3.1
 */
export {
  getOrCreateTurSession,
  readTurSession,
  clearTurSession,
  TUR_SESSION_DEFAULT_COOKIE_NAME,
  TUR_SESSION_DEFAULT_TTL_SECONDS,
} from "@viglet/turing-sdk";
export type { TurSessionOptions } from "@viglet/turing-sdk";
