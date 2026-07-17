import { useEffect, useState } from "react";
import {
  createTuringAnalytics,
  createTuringClient,
  googleAnalyticsSink,
  type TuringAnalytics,
  type TuringClient,
} from "@viglet/turing-sdk";

// T620 — shared wiring for the hero live-demos (search + chat). The demo runs on
// the product's own zero-dependency `@viglet/turing-sdk`. It goes LIVE against the
// public demo instance when VITE_TURING_DEMO_URL is set at build time; the SN site
// name is either pinned via VITE_TURING_DEMO_SITE or, when that's unset,
// auto-discovered at runtime from the demo's public `/api/sn/names` endpoint — so a
// re-seeded demo (or one seeded under a different site name) just works without a
// rebuild. See frontend/apps/site/.env.example.
const DEMO_URL = (import.meta.env.VITE_TURING_DEMO_URL as string | undefined)?.replace(/\/+$/, "");
const SITE_OVERRIDE =
  (import.meta.env.VITE_TURING_DEMO_SITE as string | undefined)?.trim() || undefined;

/** True when a demo instance URL is configured (regardless of site resolution). */
export const demoConfigured = Boolean(DEMO_URL);

/** Demo instance origin (for resolving `sandbox:`/`/api` artifact URLs), or "". */
export const DEMO_ORIGIN = DEMO_URL ?? "";

/**
 * One shared client — the same SDK client customers embed on their sites. Created
 * whenever a demo URL is configured; `null` otherwise (in-page sample-data mode).
 */
export const demoClient: TuringClient | null = DEMO_URL
  ? createTuringClient({ baseURL: `${DEMO_URL}/api` })
  : null;

/**
 * Shared analytics bus for the live demos. The GA4 sink auto-detects the site's
 * `window.gtag` (configured in `index.html`), so every SDK search/chat event —
 * `turing_search`, `turing_chat_start`, `turing_chat_conversion`,
 * `turing_chat_abandoned`, funnel steps, result clicks — flows into GA4 with no
 * extra wiring. Constructed unconditionally (safe during SSR/prerender: the sink
 * resolves `gtag` lazily at emit time and no-ops when it's absent).
 */
export const demoAnalytics: TuringAnalytics = createTuringAnalytics({
  sinks: [googleAnalyticsSink()],
});

let namesPromise: Promise<string[]> | null = null;

/**
 * The SN site names the demo instance publishes (GET /api/sn/names, public,
 * returns `string[]`). Memoized for the session; `[]` when no instance is
 * configured or the call fails.
 */
function fetchSiteNames(): Promise<string[]> {
  if (!DEMO_URL) return Promise.resolve([]);
  namesPromise ??= fetch(`${DEMO_URL}/api/sn/names`, { headers: { Accept: "application/json" } })
    .then((r) => (r.ok ? (r.json() as Promise<unknown>) : []))
    .then((names) => (Array.isArray(names) ? names.map(String) : []))
    .catch(() => []);
  return namesPromise;
}

/**
 * Resolves the SN site name that backs a live demo, or null (→ caller falls back
 * to its bundled sample corpus so the widget never looks broken).
 *
 * - `preferred` set (e.g. a page pinning its own site like `wknd-publish`): goes
 *   live ONLY if the demo instance actually hosts that site — so a page whose seed
 *   isn't deployed yet degrades gracefully to sample data instead of erroring.
 * - otherwise: `VITE_TURING_DEMO_SITE` when set, else the first discovered site.
 */
export function resolveDemoSite(preferred?: string): Promise<string | null> {
  if (!DEMO_URL) return Promise.resolve(null);
  if (preferred) {
    return fetchSiteNames().then((names) => (names.includes(preferred) ? preferred : null));
  }
  if (SITE_OVERRIDE) return Promise.resolve(SITE_OVERRIDE);
  return fetchSiteNames().then((names) => (names.length > 0 ? names[0] : null));
}

export interface TuringDemo {
  /** Shared SDK client, or null when no demo instance is configured. */
  readonly client: TuringClient | null;
  /** Resolved SN site name (pinned or auto-discovered), or null until/if resolved. */
  readonly site: string | null;
  /** True once both a client and a site are available — safe to issue live calls. */
  readonly live: boolean;
  /** Shared analytics bus (GA4-backed) to pass to the SDK search/chat controllers. */
  readonly analytics: TuringAnalytics;
}

/**
 * React hook exposing the shared demo client + the resolved SN site. `site` starts
 * null and populates once discovery resolves; `live` flips to true only when both a
 * client and a site are ready.
 *
 * Pass `preferredSite` to pin a specific SN site (e.g. the AEM page pins
 * `wknd-publish`): the hook goes live only if the demo instance actually hosts it,
 * otherwise `site` stays null and the caller falls back to its sample corpus.
 */
export function useTuringDemo(preferredSite?: string): TuringDemo {
  // Without a preferred site we keep the original eager init from the env pin so
  // the home demo renders live immediately; a preferred site is verified first.
  const [site, setSite] = useState<string | null>(
    preferredSite ? null : (SITE_OVERRIDE ?? null)
  );

  useEffect(() => {
    let alive = true;
    resolveDemoSite(preferredSite).then((s) => {
      if (alive) setSite(s);
    });
    return () => {
      alive = false;
    };
  }, [preferredSite]);

  return {
    client: demoClient,
    site,
    live: Boolean(demoClient && site),
    analytics: demoAnalytics,
  };
}
