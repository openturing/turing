import { Fragment, useMemo, type ReactNode } from "react";

/**
 * One per-sentence citation — the structural mirror of the SDK's
 * `TurChatCitation` (T152/T153/T154), redeclared here so
 * `@viglet/turing-react-ui` stays zero-runtime-dependency (it must not import
 * `@viglet/turing-sdk`).
 */
export interface TuringCitation {
  documentIndex: number;
  sourceId?: string | null;
  documentTitle?: string | null;
  url?: string | null;
  /** The exact span of the source Claude grounded the claim on. */
  citedText: string;
  startIndex?: number | null;
  endIndex?: number | null;
  locationType?: string;
  /** Start char offset of the cited claim in the answer text. */
  answerStart?: number | null;
  /** End char offset of the cited claim in the answer text. */
  answerEnd?: number | null;
}

/** Per-slot class names — the component ships ZERO visual styling of its own. */
export interface TuringCitedAnswerClassNames {
  /** The wrapping container. */
  container?: string;
  /** Each uncited run of answer text. */
  text?: string;
  /** Each cited span (the default `<mark>`, when no `renderMark` is supplied). */
  mark?: string;
}

/** Arguments handed to a custom {@link TuringCitedAnswerProps.renderMark}. */
export interface TuringCitedMark {
  /** Stable React key for this span. */
  key: string;
  /** The answer text of the cited span. */
  text: string;
  /** The citation(s) covering this span (≥1; multiple sources can back one claim). */
  citations: TuringCitation[];
  /** Zero-based index of this cited span among all cited spans. */
  index: number;
}

export interface TuringCitedAnswerProps {
  /** The assistant answer text (the concatenated SSE token content). */
  text: string;
  /** The citations to overlay; each should carry `answerStart`/`answerEnd`. */
  citations: ReadonlyArray<TuringCitation>;
  /**
   * Render a cited span. When omitted, a plain `<mark>` (slot `mark`) is used
   * with a `title` showing the source quote(s). The admin/host typically passes
   * this to wrap the span in a hover popover with the source + deep link.
   */
  renderMark?: (mark: TuringCitedMark) => ReactNode;
  /** Convenience alias for {@link TuringCitedAnswerClassNames.container}. */
  className?: string;
  classNames?: TuringCitedAnswerClassNames;
}

/** A run of answer text — either plain or backed by ≥1 citation. */
interface Segment {
  text: string;
  citations: TuringCitation[];
}

/**
 * Split the answer text into ordered runs at citation span boundaries. Citations
 * sharing the same `[answerStart, answerEnd)` are grouped onto one cited run;
 * spans that overlap an already-emitted run are skipped (clamped) so no answer
 * text is ever dropped or duplicated.
 */
export function segmentCitedAnswer(
  text: string,
  citations: ReadonlyArray<TuringCitation>,
): Segment[] {
  const valid = citations.filter(
    (c) =>
      typeof c.answerStart === "number" &&
      typeof c.answerEnd === "number" &&
      c.answerStart >= 0 &&
      c.answerEnd <= text.length &&
      c.answerStart < c.answerEnd,
  );
  if (valid.length === 0) {
    return text ? [{ text, citations: [] }] : [];
  }

  // Group citations by identical span, then order spans by start offset.
  const bySpan = new Map<string, { start: number; end: number; cites: TuringCitation[] }>();
  for (const c of valid) {
    const start = c.answerStart as number;
    const end = c.answerEnd as number;
    const key = `${start}:${end}`;
    const existing = bySpan.get(key);
    if (existing) existing.cites.push(c);
    else bySpan.set(key, { start, end, cites: [c] });
  }
  const spans = [...bySpan.values()].sort((a, b) => a.start - b.start);

  const segments: Segment[] = [];
  let cursor = 0;
  for (const span of spans) {
    if (span.start < cursor) continue; // overlaps a prior cited run — skip
    if (span.start > cursor) {
      segments.push({ text: text.slice(cursor, span.start), citations: [] });
    }
    segments.push({ text: text.slice(span.start, span.end), citations: span.cites });
    cursor = span.end;
  }
  if (cursor < text.length) {
    segments.push({ text: text.slice(cursor), citations: [] });
  }
  return segments;
}

/**
 * Headless citation-aware answer renderer (T154 / §X.7.c). Underlines the exact
 * answer span Claude grounded each claim on and lets the host wrap it in a
 * hover popover (the source quote + a deep link) via {@link
 * TuringCitedAnswerProps.renderMark}.
 *
 * <p>Renders the answer as text (cited spans wrapped) rather than markdown — the
 * host falls back to its markdown renderer when there are no citations. Like the
 * other `@viglet/turing-react-ui` primitives it ships no styling: skin slots via
 * `className`/`classNames`. Style the container `white-space: pre-wrap` to keep
 * the answer's line breaks. The admin console and the public SN AI-mode chat
 * share this one implementation.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
export function TuringCitedAnswer({
  text,
  citations,
  renderMark,
  className,
  classNames,
}: Readonly<TuringCitedAnswerProps>) {
  const segments = useMemo(() => segmentCitedAnswer(text, citations), [text, citations]);

  let citedIndex = 0;
  return (
    <div className={className ?? classNames?.container} data-turing-cited-answer="">
      {segments.map((segment, i) => {
        if (segment.citations.length === 0) {
          return (
            <span className={classNames?.text} key={`t-${i}`}>
              {segment.text}
            </span>
          );
        }
        const index = citedIndex++;
        const key = `c-${index}`;
        if (renderMark) {
          return (
            <Fragment key={key}>
              {renderMark({ key, text: segment.text, citations: segment.citations, index })}
            </Fragment>
          );
        }
        const title = segment.citations
          .map((c) => {
            const label = c.documentTitle || c.sourceId || "";
            return label ? `${label}: ${c.citedText}` : c.citedText;
          })
          .join("\n");
        return (
          <mark className={classNames?.mark} key={key} title={title} data-turing-citation="">
            {segment.text}
          </mark>
        );
      })}
    </div>
  );
}
