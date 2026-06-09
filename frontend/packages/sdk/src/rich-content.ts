/**
 * Rich-content segmentation for assistant replies.
 *
 * The AI Agent can answer with fenced ```html blocks meant to be rendered as a
 * LIVE PREVIEW (sandboxed iframe) rather than shown as source — UI mockups,
 * demos, games (Pong), animations, or Chart.js charts. This pure, framework-
 * agnostic splitter turns an assistant message into an ordered list of
 * segments so a renderer can map each to the right widget: markdown prose
 * through a markdown renderer, ```html through a sandboxed iframe.
 *
 * Ported from the admin console's inline `splitContent` so the admin, the
 * vanilla-SDK consumers, and React-SDK consumers all segment identically.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */

/** One segment of an assistant reply. */
export type TurRichSegment =
  | { type: "markdown"; text: string }
  | { type: "html"; code: string };

// Fenced ```html block. The trailing newline after the language tag is
// optional-tolerant; the body is captured lazily up to the closing fence.
const HTML_BLOCK = /```html\r?\n([\s\S]*?)```/g;

/**
 * Splits {@code content} into ordered markdown / html segments. A reply with no
 * ```html block yields a single {@code markdown} segment, so callers can always
 * map over the result uniformly. Trailing newline immediately before a closing
 * fence is trimmed from the captured html.
 */
export function splitRichContent(content: string): TurRichSegment[] {
  if (!content) return [];
  const segments: TurRichSegment[] = [];
  let lastIndex = 0;
  for (const match of content.matchAll(HTML_BLOCK)) {
    const index = match.index ?? 0;
    const before = content.slice(lastIndex, index);
    if (before) segments.push({ type: "markdown", text: before });
    segments.push({ type: "html", code: match[1].replace(/\r?\n$/, "") });
    lastIndex = index + match[0].length;
  }
  const after = content.slice(lastIndex);
  if (after) segments.push({ type: "markdown", text: after });
  return segments;
}

/** True when {@code content} contains at least one renderable ```html block. */
export function hasHtmlBlock(content: string): boolean {
  return /```html\r?\n[\s\S]*?```/.test(content ?? "");
}
