import { useMemo, useRef } from "react";
import {
  createTuringAnalytics,
  type TuringAnalytics,
  type TuringAnalyticsContext,
  type TuringAnalyticsSink,
} from "../core/analytics";
import {
  debugSink,
  googleAnalyticsSink,
  type GoogleAnalyticsSinkOptions,
} from "../core/analytics-sinks";
import { useOptionalTuringContext } from "../core/use-turing-context";

/**
 * T463 (Block Z) — React entry point for the canonical analytics bus.
 *
 * Returns a **memoized** {@link TuringAnalytics} bus with a GA4/GTM sink
 * auto-attached (auto-detecting `window.gtag` / `window.dataLayer`, so a
 * GTM-tagged or Adobe EDS host lights up with zero extra config). Pass the
 * returned `analytics` to {@link useTuringChat} / {@link useTuringSearch} (their
 * `analytics` option) so conversion, abandonment, funnel-step and search events
 * flow to GA4. The `site` from the surrounding `<TuringProvider>` is seeded into
 * the bus context automatically.
 *
 * @example
 * ```tsx
 * const { analytics } = useTuringAnalytics();              // GA4 auto-detected
 * const chat = useTuringChat({ site: "store", analytics });
 * const search = useTuringSearch(undefined, { analytics });
 * ```
 *
 * @author Alexandre Oliveira
 * @since 2026.3.6
 */
export interface UseTuringAnalyticsOptions {
  /** Extra sinks beyond the auto-configured GA4 + optional debug sink. */
  readonly sinks?: ReadonlyArray<TuringAnalyticsSink>;
  /**
   * Attach the GA4/GTM sink. `true` (default) auto-detects; pass options to
   * force a `measurementId` / transport; `false` to omit it (use `sinks`).
   */
  readonly googleAnalytics?: boolean | GoogleAnalyticsSinkOptions;
  /** Attach a console {@link debugSink}. Defaults to `false`. */
  readonly debug?: boolean;
  /** Seed context merged into every event (cohort/campaign dims, etc.). */
  readonly context?: TuringAnalyticsContext;
}

export interface UseTuringAnalyticsReturn {
  /** The memoized bus — stable across renders; pass to the chat/search hooks. */
  readonly analytics: TuringAnalytics;
  /** Convenience bound emitter (stable). */
  readonly emit: TuringAnalytics["emit"];
}

export function useTuringAnalytics(
  options: UseTuringAnalyticsOptions = {},
): UseTuringAnalyticsReturn {
  const ctx = useOptionalTuringContext();
  const site = ctx?.config?.site;

  // Read options from a ref so the bus is created exactly once — a new options
  // object literal each render must not rebuild the bus (it would drop the
  // accumulated A/B context and re-arm sinks).
  const optionsRef = useRef(options);

  const analytics = useMemo<TuringAnalytics>(() => {
    const opts = optionsRef.current;
    const sinks: TuringAnalyticsSink[] = [];
    if (opts.googleAnalytics !== false) {
      sinks.push(
        googleAnalyticsSink(
          typeof opts.googleAnalytics === "object" ? opts.googleAnalytics : {},
        ),
      );
    }
    if (opts.debug) sinks.push(debugSink());
    if (opts.sinks) sinks.push(...opts.sinks);
    return createTuringAnalytics({
      sinks,
      context: { site, ...opts.context },
    });
  }, [site]);

  return { analytics, emit: analytics.emit };
}
