/**
 * T713 / §XLV — one-time deprecation notices.
 *
 * Kept deliberately tiny: a `Set` of keys already warned about, so a
 * deprecated call inside a render loop or a facet list logs once per page
 * rather than once per call. Silent when there is no `console` (SSR, workers,
 * some EDS runtimes).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */

const warned = new Set<string>();

/**
 * Logs `message` once per `key` for the lifetime of the module.
 *
 * @internal Not part of the supported API surface; exported only so
 *   `@viglet/turing-react-sdk` can share one notice registry with this package
 *   instead of keeping a second copy (and warning twice for one call).
 */
export function warnDeprecated(key: string, message: string): void {
  if (warned.has(key)) return;
  warned.add(key);
  if (typeof console !== "undefined" && typeof console.warn === "function") {
    console.warn(`[@viglet/turing-sdk] Deprecated: ${message}`);
  }
}

/** Test seam — forget every notice so a case can assert the "once" behaviour. */
export function resetDeprecationNotices(): void {
  warned.clear();
}
