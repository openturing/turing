/**
 * T653 / §XXXVII.15 — dependency-free HTML sanitisers for the few
 * `dangerouslySetInnerHTML` sinks that render backend/model-provided markup.
 *
 * We deliberately avoid pulling in DOMPurify (and a browser-DOM dependency) for
 * these narrow cases: search highlighting only needs a tiny fixed allowlist of
 * inline tags, and the approach here (escape EVERYTHING, then re-allow only the
 * exact safe tags) is provably closed — no attributes, no scripts, no arbitrary
 * elements can survive.
 */

/** Escape the five HTML-significant characters. */
function escapeHtml(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

/**
 * Sanitise search-engine highlighted field content for a `<mark>`-style render.
 * The search highlighter wraps matches in a (per-site configurable) inline tag,
 * but Solr/ES do NOT HTML-escape the surrounding field content by default — so a
 * poisoned indexed document could otherwise inject `<script>` into the
 * authenticated admin console. We escape the whole string, then re-enable ONLY a
 * small allowlist of attribute-less inline formatting tags the highlighter uses.
 */
export function sanitizeHighlight(input: string | null | undefined): string {
  if (!input) {
    return "";
  }
  const escaped = escapeHtml(input);
  // Re-allow only these exact tags (opening + closing, no attributes).
  const allowed = ["mark", "em", "strong", "b", "i", "u"];
  let out = escaped;
  for (const tag of allowed) {
    out = out
      .replace(new RegExp(`&lt;${tag}&gt;`, "gi"), `<${tag}>`)
      .replace(new RegExp(`&lt;/${tag}&gt;`, "gi"), `</${tag}>`);
  }
  return out;
}

/**
 * Best-effort strip of dangerous constructs from a semi-trusted HTML fragment
 * (e.g. the Google-grounding "search suggestions" chips returned by Gemini).
 * Removes script/style/iframe/object/embed elements, inline event-handler
 * attributes (`on*=`), and `javascript:` URLs, while leaving the chip markup +
 * inline styles intact. Not a full sanitiser — defence-in-depth over a
 * provider-sourced (not attacker-authored) fragment.
 */
export function sanitizeFragment(input: string | null | undefined): string {
  if (!input) {
    return "";
  }
  return input
    .replace(/<\s*(script|iframe|object|embed|link|meta)\b[^>]*>[\s\S]*?<\s*\/\s*\1\s*>/gi, "")
    .replace(/<\s*(script|iframe|object|embed|link|meta)\b[^>]*\/?>/gi, "")
    // Inline event handlers: on...="..." / on...='...' / on...=value
    .replace(/\son[a-z]+\s*=\s*"[^"]*"/gi, "")
    .replace(/\son[a-z]+\s*=\s*'[^']*'/gi, "")
    .replace(/\son[a-z]+\s*=\s*[^\s>]+/gi, "")
    // javascript: URLs in href/src
    .replace(/(href|src)\s*=\s*(['"])\s*javascript:[^'"]*\2/gi, "$1=$2#$2");
}
