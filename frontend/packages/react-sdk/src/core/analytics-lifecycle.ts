/**
 * T460 (Block Z) — client-side abandonment & engagement watcher.
 *
 * The server can only *infer* abandonment from a session TTL; the browser
 * actually sees the visitor leave. This watcher fires a single
 * `turing_chat_abandoned` when a started conversation (≥1 user message, no
 * conversion) ends — on tab hide (`visibilitychange→hidden`), page unload
 * (`pagehide`/`beforeunload`), or an idle timeout. On unload the GA4 sink uses
 * `navigator.sendBeacon` (via gtag `transport_type: 'beacon'`) so the event
 * survives the teardown.
 *
 * Pure lifecycle plumbing — the *decision* (is the conversation active and
 * unconverted? has it already fired?) lives in the caller's `fire` callback, so
 * this module is trivially testable and SSR-safe.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.6
 */

/** Why an abandonment fired — also emitted as the event's `reason` param. */
export type AbandonmentReason = "hidden" | "unload" | "idle";

export interface AbandonmentOptions {
  /**
   * Idle timeout in ms after the last {@link AbandonmentWatcher.ping}. `0`
   * (default) disables the idle trigger — only hide/unload count.
   */
  readonly idleMs?: number;
  /** Fire on `visibilitychange → hidden`. Defaults to `true`. */
  readonly onHidden?: boolean;
  /** Fire on page unload (`pagehide` / `beforeunload`). Defaults to `true`. */
  readonly onUnload?: boolean;
}

export interface AbandonmentWatcher {
  /** Reset the idle timer — call on each user activity (e.g. a message sent). */
  ping(): void;
  /** Remove every listener and clear the idle timer. Idempotent. */
  stop(): void;
}

const NOOP_WATCHER: AbandonmentWatcher = { ping() {}, stop() {} };

/**
 * Installs the lifecycle listeners and returns a handle. `fire(reason)` is
 * invoked at most as often as the events occur — the caller is responsible for
 * the once-only / active / unconverted guards. Returns a no-op watcher outside
 * a browser (SSR), so callers don't need their own environment check.
 */
export function createAbandonmentWatcher(
  fire: (reason: AbandonmentReason) => void,
  options: AbandonmentOptions = {},
): AbandonmentWatcher {
  const { idleMs = 0, onHidden = true, onUnload = true } = options;

  const hasDocument = typeof document !== "undefined";
  const hasWindow = typeof globalThis.addEventListener === "function";
  if (!hasDocument && !hasWindow) return NOOP_WATCHER;

  let idleTimer: ReturnType<typeof setTimeout> | undefined;
  let stopped = false;

  function clearIdle(): void {
    if (idleTimer) {
      clearTimeout(idleTimer);
      idleTimer = undefined;
    }
  }

  function armIdle(): void {
    if (idleMs <= 0 || stopped) return;
    clearIdle();
    idleTimer = setTimeout(() => fire("idle"), idleMs);
  }

  const onVisibility = (): void => {
    if (document.visibilityState === "hidden") fire("hidden");
  };
  const onPageHide = (): void => fire("unload");

  if (onHidden && hasDocument) {
    document.addEventListener("visibilitychange", onVisibility);
  }
  if (onUnload && hasWindow) {
    globalThis.addEventListener("pagehide", onPageHide);
    globalThis.addEventListener("beforeunload", onPageHide);
  }
  armIdle();

  return {
    ping() {
      armIdle();
    },
    stop() {
      stopped = true;
      clearIdle();
      if (onHidden && hasDocument) {
        document.removeEventListener("visibilitychange", onVisibility);
      }
      if (onUnload && hasWindow) {
        globalThis.removeEventListener("pagehide", onPageHide);
        globalThis.removeEventListener("beforeunload", onPageHide);
      }
    },
  };
}
