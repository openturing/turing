/**
 * Turing `sandbox:` virtual-scheme resolver.
 *
 * Sandboxed artifacts (code-interpreter charts/files, agent workspace files)
 * are surfaced to the LLM as markdown whose URL uses the `sandbox:` scheme —
 * e.g. `sandbox:/api/v2/code-interpreter/{session}/chart.png`. Turing adopts
 * OpenAI's own Code Interpreter convention on purpose: OpenAI-family models
 * echo a `sandbox:` URL verbatim (it matches their training) instead of
 * corrupting a bare relative path, which is the recurring "image won't render"
 * cause. The scheme carries no host, so the CLIENT decides how to resolve it:
 *
 * - same-origin (the admin console): strip `sandbox:` → a relative `/api/...`
 *   path the browser loads against the current origin;
 * - embedded/cross-origin (an EDS block, a `<script>` widget): pass the Turing
 *   `baseUrl` so the path becomes an absolute URL against the Turing host.
 *
 * The resolver is intentionally tolerant of the model's improvisation: it
 * collapses a missing, single, or accidentally-doubled (`sandbox:sandbox:/…`)
 * prefix and any number of leading slashes, and leaves non-sandbox URLs
 * (absolute `http(s)://`, `data:`, `mailto:`) untouched.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */

/** One-or-more leading `sandbox:` prefixes plus any following slashes. */
const SANDBOX_PREFIX = /^(?:\s*sandbox:\/*)+/i;

/**
 * The scheme+host+port of {@link baseUrl}, discarding any path. Turing artifact
 * URLs are rooted at the DOMAIN (`/api/v2/code-interpreter/…`), so prepending a
 * base that itself carries a path (e.g. a `TURING_API_URL` of
 * `http://host:2700/api`) would double the segment → `http://host:2700/api/api/v2/…`.
 * Resolving against the origin avoids that. Falls back to the trimmed input when
 * {@link baseUrl} isn't a parseable absolute URL (e.g. already an origin or a
 * bare host).
 */
function originOf(baseUrl: string): string {
  try {
    return new URL(baseUrl).origin;
  } catch {
    return baseUrl.replace(/\/+$/, "");
  }
}

/**
 * Resolves a (possibly `sandbox:`-prefixed) artifact URL to one a browser can
 * load.
 *
 * @param url     the raw URL from the assistant's markdown (may be
 *                `sandbox:/api/...`, a bare `/api/...` path, or an absolute URL).
 * @param baseUrl optional Turing host base (e.g. `https://turing.example.com`).
 *                When set and the resolved value is an absolute path, the base
 *                is prepended so the artifact loads cross-origin. Trailing
 *                slashes on the base are trimmed.
 * @returns the resolved URL. Non-sandbox absolute URLs and non-path values are
 *          returned unchanged (aside from trimming).
 */
export function resolveSandboxUrl(url: string, baseUrl = ""): string {
  if (!url) return url;
  let path = url.trim();
  if (SANDBOX_PREFIX.test(path)) {
    // sandbox:/api/... → /api/... ; sandbox://api/... → /api/... ;
    // sandbox:sandbox:/api/... → /api/...
    path = path.replace(SANDBOX_PREFIX, "/");
  } else if (!path.startsWith("/")) {
    // Not a sandbox URL and not an absolute path — leave it as-is
    // (http(s)://, data:, mailto:, anchors, etc.).
    return path;
  }
  if (baseUrl && path.startsWith("/")) {
    return originOf(baseUrl) + path;
  }
  return path;
}

/** True when {@link url} uses the Turing `sandbox:` virtual scheme. */
export function isSandboxUrl(url: string): boolean {
  return SANDBOX_PREFIX.test(url ?? "");
}

/**
 * Markdown link/image target: `](URL)`, optionally `sandbox:`-prefixed,
 * pointing at a Turing API artifact path (`/api/...`). The capture is the
 * leading `/api/...` path (sans scheme); it stops at whitespace or the closing
 * `)`, so the signed query string (`?sig=…&exp=…`) is preserved.
 */
const MARKDOWN_ARTIFACT_LINK = /\]\(\s*(?:sandbox:\/*)?(\/api\/[^\s)]+)\)/gi;

/**
 * Rewrites Turing artifact URLs inside a markdown/text blob to absolute URLs
 * against {@link baseUrl}. Built for a cross-origin Backend-for-Frontend (e.g.
 * a Next.js route that proxies the agent chat): the assistant's reply carries
 * same-origin-relative artifact paths (`![chart](/api/v2/code-interpreter/…)`)
 * — or a stray `sandbox:` prefix — which a browser on a DIFFERENT origin would
 * resolve against the wrong host. Run the assistant text through this with the
 * Turing base URL before handing it to the browser and the images load.
 *
 * <p>Only markdown link/image targets ({@code ](…)}) that point at
 * {@code /api/…} are touched, so already-absolute URLs and ordinary prose are
 * left intact. No-op when {@link baseUrl} is empty (same-origin consumers don't
 * need it).
 *
 * @param text    assistant markdown (may contain zero or more artifact links)
 * @param baseUrl Turing host base, e.g. {@code https://turing.example.com}
 *                (trailing slashes trimmed)
 */
export function absolutizeArtifactUrls(text: string, baseUrl = ""): string {
  if (!text || !baseUrl) return text;
  const origin = originOf(baseUrl);
  return text.replace(MARKDOWN_ARTIFACT_LINK, (_match, path: string) => `](${origin}${path})`);
}
