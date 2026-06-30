import { LandingPage } from "@/pages/landing";
import { FeaturePage } from "@/pages/feature-page";
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

export default function App({ path = "/" }: Readonly<{ path?: string }>) {
  const slug = matchRoute(path);
  if (slug) return <FeaturePage detail={FEATURE_DETAILS[slug]} />;
  return <LandingPage />;
}
