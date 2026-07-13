import { LandingPage } from "@/pages/landing";
import { FeaturePage } from "@/pages/feature-page";
import { MigratePage } from "@/pages/migrate";
import { ComparePage } from "@/pages/compare";
import { AemPage } from "@/pages/aem";
import { FEATURE_DETAILS } from "@/lib/site-content";

/** Standalone (non-feature) routes with their own page component. */
export const MIGRATE_PATH = "/migrate";
export const COMPARE_PATH = "/compare";
export const AEM_PATH = "/aem";

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

export default function App({ path = "/" }: Readonly<{ path?: string }>) {
  if (/^\/migrate\/?$/.test(path)) return <MigratePage />;
  if (/^\/compare\/?$/.test(path)) return <ComparePage />;
  if (/^\/aem\/?$/.test(path)) return <AemPage />;
  const slug = matchRoute(path);
  if (slug) return <FeaturePage detail={FEATURE_DETAILS[slug]} />;
  return <LandingPage />;
}
