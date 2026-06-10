import type { ComponentType, ReactNode } from "react";
import {
  TuringHtmlSandbox,
  type TuringHtmlSandboxProps,
} from "./TuringHtmlSandbox";

/**
 * One ordered segment of an assistant reply.
 *
 * <p>Shape-compatible with {@code TurRichSegment} from {@code @viglet/turing-sdk}.
 * It is re-declared here (not imported) so {@code @viglet/turing-react-ui} keeps
 * ZERO runtime dependencies — it must stay installable cross-repo via a bare
 * {@code file:} link (viglet.com) where a {@code workspace:*} dep would not
 * resolve. The {@link splitRichContent} default below mirrors the SDK splitter;
 * both are covered by tests to prevent drift. Once the SDK is published to npm
 * (roadmap T303), this package can depend on it and drop the local copy.</p>
 */
export type TuringRichSegment =
  | { type: "markdown"; text: string }
  | { type: "html"; code: string }
  | { type: "d2"; code: string };

// Fenced ```html / ```d2 block — see the SDK's rich-content.ts for the canonical
// version. Trailing newline before the closing fence is trimmed from the body.
const SPECIAL_BLOCK = /```(html|d2)\r?\n([\s\S]*?)```/g;

/**
 * Splits {@code content} into ordered markdown / html / d2 segments. A reply
 * with no special block yields a single {@code markdown} segment, so callers
 * can always map over the result uniformly. This is the built-in default for
 * {@link TuringRichContent}; pass {@link TuringRichContentProps.split} to reuse
 * the SDK's {@code splitRichContent} instead (single source of truth).
 */
export function splitRichContent(content: string): TuringRichSegment[] {
  if (!content) return [];
  const segments: TuringRichSegment[] = [];
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

/** Props the host's markdown renderer receives — one prose segment's text. */
export interface TuringMarkdownSegmentProps {
  text: string;
}

/** Props the host's d2 renderer receives — one ```d2 block's raw source. */
export interface TuringD2Props {
  code: string;
}

/** Props an html renderer override receives — one ```html block's fragment. */
export interface TuringHtmlSegmentProps {
  code: string;
}

/** Per-slot class names for the rich-content wrapper. */
export interface TuringRichContentClassNames {
  /** Wrapper around the whole ordered segment list. */
  root?: string;
}

export interface TuringRichContentProps {
  /** The raw assistant reply. Split into ordered markdown / html / d2 segments. */
  content: string;
  /**
   * Renders one markdown prose segment. REQUIRED — the library ships no markdown
   * dependency, so the host plugs in its own {@code react-markdown} (with its
   * own remark/rehype plugins, {@code urlTransform}, classNames).
   */
  markdown: ComponentType<TuringMarkdownSegmentProps>;
  /**
   * Override the ```html renderer. Defaults to {@link TuringHtmlSandbox} skinned
   * via {@link htmlSandbox}. Provide this to reuse an app's existing sandbox
   * skin adapter (e.g. the admin's {@code SandboxPlayer}).
   */
  html?: ComponentType<TuringHtmlSegmentProps>;
  /**
   * Skin / labels / icons / height for the default {@link TuringHtmlSandbox}.
   * Ignored when {@link html} is provided. {@code code} is supplied per segment.
   */
  htmlSandbox?: Omit<TuringHtmlSandboxProps, "code">;
  /**
   * Renders one ```d2 diagram block. Optional — until {@code TuringD2Diagram}
   * (roadmap T297) lands, omit it and the ```d2 block falls back to the
   * {@link markdown} renderer (shown as a fenced code block), so nothing is lost.
   */
  d2?: ComponentType<TuringD2Props>;
  /**
   * Override the splitter. Defaults to the built-in {@link splitRichContent}.
   * Pass the SDK's {@code splitRichContent} to keep one source of truth.
   */
  split?: (content: string) => TuringRichSegment[];
  className?: string;
  classNames?: TuringRichContentClassNames;
}

/**
 * Headless renderer for a rich assistant reply: splits the text into ordered
 * markdown / ```html / ```d2 segments and dispatches each to the right widget —
 * prose to the injected {@link TuringRichContentProps.markdown} component,
 * ```html to a sandboxed {@link TuringHtmlSandbox} (or a {@link html} override),
 * ```d2 to the optional {@link d2} renderer.
 *
 * <p>Replaces the per-app split-and-map duplication (the admin console's
 * {@code splitContent} + {@code AssistantContent}, and viglet.com's inline
 * {@code splitRichContent().map(...)}). Both apps now share this dispatcher
 * while keeping their own markdown setup, html skin, and look.</p>
 *
 * <p><b>Design-agnostic by construction:</b> it renders structure only and
 * carries no styling or markdown/icon dependency of its own. Skin via
 * {@code className}/{@code classNames}, plug markdown/html/d2 renderers in.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
export function TuringRichContent({
  content,
  markdown: Markdown,
  html: Html,
  htmlSandbox,
  d2: D2,
  split = splitRichContent,
  className,
  classNames,
}: Readonly<TuringRichContentProps>) {
  const segments = split(content);

  const nodes: ReactNode[] = segments.map((seg, i) => {
    if (seg.type === "html") {
      return Html ? (
        <Html key={i} code={seg.code} />
      ) : (
        <TuringHtmlSandbox key={i} code={seg.code} {...htmlSandbox} />
      );
    }
    if (seg.type === "d2") {
      // No d2 renderer yet → render the block losslessly as a markdown code
      // fence, so the diagram source is still shown until T297 lands.
      return D2 ? (
        <D2 key={i} code={seg.code} />
      ) : (
        <Markdown key={i} text={`\`\`\`d2\n${seg.code}\n\`\`\``} />
      );
    }
    return <Markdown key={i} text={seg.text} />;
  });

  const wrapperClass = className ?? classNames?.root;
  if (!wrapperClass) return <>{nodes}</>;
  return <div className={wrapperClass}>{nodes}</div>;
}
