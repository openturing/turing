/**
 * Resolves the Turing SN site name from the current URL.
 *
 * When the SPA is used as a search template and served at `/sn/{siteName}/`,
 * the site name is extracted from the URL pathname.
 * When accessed directly (e.g. `/pages/{appName}/` or dev mode),
 * it falls back to the VITE_SN_SITE environment variable.
 */
export function resolveSiteName(): string {
  const pathname = window.location.pathname;
  const match = pathname.match(/^\/sn\/([^/]+)/);
  if (match) {
    return match[1];
  }
  return import.meta.env.VITE_SN_SITE || "default";
}
