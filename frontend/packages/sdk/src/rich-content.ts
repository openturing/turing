/**
 * Rich-content segmentation for assistant replies.
 *
 * The AI Agent can answer with fenced blocks meant to be rendered as a richer
 * widget rather than shown as source:
 *
 * - ```html — a LIVE PREVIEW (sandboxed iframe): UI mockups, demos, games
 *   (Pong), animations, or Chart.js charts.
 * - ```d2 — a diagram (rendered by the host's D2 engine; see render.md).
 *
 * This pure, framework-agnostic splitter turns an assistant message into an
 * ordered list of segments so a renderer can map each to the right widget:
 * markdown prose through a markdown renderer, ```html through a sandboxed
 * iframe, ```d2 through a diagram renderer.
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
  | { type: "html"; code: string }
  | { type: "d2"; code: string };

// Fenced ```html / ```d2 block. The trailing newline after the language tag is
// optional-tolerant; the body is captured lazily up to the closing fence.
const SPECIAL_BLOCK = /```(html|d2)\r?\n([\s\S]*?)```/g;

/**
 * Splits {@code content} into ordered markdown / html / d2 segments. A reply
 * with no special block yields a single {@code markdown} segment, so callers
 * can always map over the result uniformly. Trailing newline immediately before
 * a closing fence is trimmed from the captured code.
 */
export function splitRichContent(content: string): TurRichSegment[] {
  if (!content) return [];
  const segments: TurRichSegment[] = [];
  let lastIndex = 0;
  for (const match of content.matchAll(SPECIAL_BLOCK)) {
    const index = match.index ?? 0;
    const before = content.slice(lastIndex, index);
    if (before) segments.push({ type: "markdown", text: before });
    const lang = match[1] as "html" | "d2";
    segments.push({ type: lang, code: match[2].replace(/\r?\n$/, "") });
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
