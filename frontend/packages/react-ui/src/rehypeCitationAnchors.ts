/**
 * T836 / §LXII.5 — a rehype plugin that turns each `[n]` footnote marker in an
 * answer's markdown into a clickable citation anchor. It rewrites matching text
 * nodes into `<sup data-turing-citation="n">[n]</sup>` hast elements; pair it with
 * {@link citationMarkdownComponents} (a `components` map for
 * {@link TuringMarkdown}) to render those anchors as `TuringCitationAnchor`s that
 * scroll-link and highlight the matching source card.
 *
 * <p>Zero-runtime-dependency by design (like the rest of
 * `@viglet/turing-react-ui`): it walks the hast tree by hand instead of importing
 * `unist-util-visit`. Markers inside `code` / `pre` are left untouched so a code
 * sample like `arr[0]` is never mistaken for a citation.
 *
 * <p>Depends on the T832 shared footnote numbering — the anchor is only correct
 * once the `[n]` in the prose and the chip carry the same number.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */

/** Minimal structural view of a hast node (avoids an `@types/hast` dependency). */
interface HastNode {
  type: string;
  tagName?: string;
  value?: string;
  properties?: Record<string, unknown>;
  children?: HastNode[];
}

/** Matches a `[n]` citation marker (1–4 digit footnote). */
const MARKER = /\[(\d{1,4})\]/g;

export interface RehypeCitationAnchorsOptions {
  /**
   * Optional gate: return {@code false} to leave a given footnote number as plain
   * text (e.g. a number the answer cited but no chip backs). When omitted, every
   * `[n]` becomes an anchor.
   */
  isValidCitation?: (index: number) => boolean;
}

/**
 * The rehype plugin (a unified attacher). Pass it — not its result — to
 * {@link TuringMarkdown}: {@code rehypePlugins={[rehypeCitationAnchors]}} for the
 * defaults, or {@code rehypePlugins={[[rehypeCitationAnchors, { isValidCitation }]]}}
 * with options.
 */
export function rehypeCitationAnchors(options: RehypeCitationAnchorsOptions = {}) {
  const { isValidCitation } = options;

  return function transform(tree: HastNode): void {
    walk(tree, false);
  };

  function walk(node: HastNode, inCode: boolean): void {
    if (!node || !Array.isArray(node.children)) {
      return;
    }
    const insideCode = inCode || node.tagName === "code" || node.tagName === "pre";
    const rewritten: HastNode[] = [];
    for (const child of node.children) {
      if (!insideCode && child.type === "text" && typeof child.value === "string"
          && child.value.includes("[")) {
        rewritten.push(...splitTextNode(child.value));
      } else {
        walk(child, insideCode);
        rewritten.push(child);
      }
    }
    node.children = rewritten;
  }

  function splitTextNode(value: string): HastNode[] {
    const out: HastNode[] = [];
    let last = 0;
    let match: RegExpExecArray | null;
    MARKER.lastIndex = 0;
    while ((match = MARKER.exec(value)) !== null) {
      const index = Number(match[1]);
      if (isValidCitation && !isValidCitation(index)) {
        // Leave this marker as plain text: don't advance `last`, so it is
        // carried into the next text slice untouched.
        continue;
      }
      if (match.index > last) {
        out.push({ type: "text", value: value.slice(last, match.index) });
      }
      out.push(anchorElement(index));
      last = match.index + match[0].length;
    }
    if (out.length === 0) {
      return [{ type: "text", value }];
    }
    if (last < value.length) {
      out.push({ type: "text", value: value.slice(last) });
    }
    return out;
  }
}

function anchorElement(index: number): HastNode {
  return {
    type: "element",
    tagName: "sup",
    properties: {
      className: ["turing-citation-anchor"],
      dataTuringCitation: String(index),
    },
    children: [{ type: "text", value: `[${index}]` }],
  };
}
