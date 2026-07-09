import { useEffect, useState } from "react";
import { createTuringClient, type TuringClient } from "@viglet/turing-sdk";

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

/**
 * One shared client — the same SDK client customers embed on their sites. Created
 * whenever a demo URL is configured; `null` otherwise (in-page sample-data mode).
 */
export const demoClient: TuringClient | null = DEMO_URL
  ? createTuringClient({ baseURL: `${DEMO_URL}/api` })
  : null;

let sitePromise: Promise<string | null> | null = null;

/**
 * Resolves the SN site name that backs the live demos. Uses VITE_TURING_DEMO_SITE
 * when set; otherwise auto-discovers the first SN site published by the demo instance
 * via GET /api/sn/names (public, returns `string[]`). Memoized for the session; null
 * when no instance is configured or discovery fails / returns no sites — callers then
 * fall back to the bundled sample corpus so the widget never looks broken.
 */
export function resolveDemoSite(): Promise<string | null> {
  if (!DEMO_URL) return Promise.resolve(null);
  if (SITE_OVERRIDE) return Promise.resolve(SITE_OVERRIDE);
  if (!sitePromise) {
    sitePromise = fetch(`${DEMO_URL}/api/sn/names`, { headers: { Accept: "application/json" } })
      .then((r) => (r.ok ? (r.json() as Promise<unknown>) : []))
      .then((names) =>
        Array.isArray(names) && names.length > 0 ? String(names[0]) : null
      )
      .catch(() => null);
  }
  return sitePromise;
}

export interface TuringDemo {
  /** Shared SDK client, or null when no demo instance is configured. */
  readonly client: TuringClient | null;
  /** Resolved SN site name (pinned or auto-discovered), or null until/if resolved. */
  readonly site: string | null;
  /** True once both a client and a site are available — safe to issue live calls. */
  readonly live: boolean;
}

/**
 * React hook exposing the shared demo client + the resolved SN site. `site` starts
 * null and populates once discovery resolves; `live` flips to true only when both a
 * client and a site are ready.
 */
export function useTuringDemo(): TuringDemo {
  const [site, setSite] = useState<string | null>(SITE_OVERRIDE ?? null);

  useEffect(() => {
    let alive = true;
    resolveDemoSite().then((s) => {
      if (alive) setSite(s);
    });
    return () => {
      alive = false;
    };
  }, []);

  return { client: demoClient, site, live: Boolean(demoClient && site) };
}
