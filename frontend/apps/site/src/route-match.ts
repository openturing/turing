/**
 * T1027 — the routing rule, on its own.
 *
 * One regex against one record: pure, and read by the prerender as well as by
 * the app. It is this site's whole routing rule, and it belongs in a module
 * rather than beside the component that happens to call it.
 */
import { FEATURE_DETAILS } from "@/lib/site-content";

/**
 * Resolve a pathname to a feature slug, or null for the landing page.
 * Kept dependency-free (no react-router) so the SSG prerender and the client
 * agree on a single, trivial routing rule. Navigation between pages uses plain
 * `<a href>` full loads — every route is prerendered to its own static HTML.
 */
export function matchRoute(path: string): string | null {
  const m = /^\/features\/([a-z-]+)\/?$/.exec(path);
  return m && FEATURE_DETAILS[m[1]] ? m[1] : null;
}
