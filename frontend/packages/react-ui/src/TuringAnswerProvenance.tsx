import type { ReactNode } from "react";

/**
 * T839 / §LXII.8 — the "why this answer" auditable provenance panel. One
 * expandable disclosure per answer surfacing what the answer engine did: the
 * knowledge-base mode, the rerank strategy, the source count, and — per cited
 * source — the T389 `rankingExplanation` (lexical / semantic / fused rank, RRF
 * score) already attached to hybrid results. Positions directly against black-box
 * answer engines; the feature a regulated buyer asks for first.
 *
 * <p>Headless and zero-dependency like the rest of `@viglet/turing-react-ui`: it
 * renders a native `<details>` disclosure with every slot class-nameable and ships
 * no styling. The data types mirror the SDK's `TurAnswerTrace` / `TurAnswerCitation`
 * structurally so the package never imports `@viglet/turing-sdk`.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */

/** Structural mirror of the SDK's `TurAnswerTrace`. */
export interface TuringAnswerProvenanceTrace {
  sourceCount?: number;
  answerLength?: number;
  kbMode?: string | null;
  rerankStrategy?: string | null;
  rerankEnabled?: boolean;
}

/** The per-source rank breakdown the panel lists (subset of `TurAnswerCitation`). */
export interface TuringAnswerProvenanceCitation {
  rank: number;
  title?: string | null;
  sourceId?: string | null;
  rankingExplanation?: Record<string, unknown> | null;
}

export interface TuringAnswerProvenanceClassNames {
  root?: string;
  summary?: string;
  body?: string;
  row?: string;
  label?: string;
  value?: string;
  sources?: string;
  source?: string;
  sourceTitle?: string;
  explanation?: string;
  explanationItem?: string;
}

export interface TuringAnswerProvenanceLabels {
  /** Disclosure summary. Default "Why this answer". */
  title?: string;
  kbMode?: string;
  rerank?: string;
  rerankOff?: string;
  sourceCount?: string;
}

export interface TuringAnswerProvenanceProps {
  trace?: TuringAnswerProvenanceTrace | null;
  citations?: ReadonlyArray<TuringAnswerProvenanceCitation>;
  /** Start expanded. Default `false`. */
  defaultOpen?: boolean;
  className?: string;
  classNames?: TuringAnswerProvenanceClassNames;
  labels?: TuringAnswerProvenanceLabels;
}

function hasExplanation(citation: TuringAnswerProvenanceCitation): boolean {
  return !!citation.rankingExplanation && Object.keys(citation.rankingExplanation).length > 0;
}

export function TuringAnswerProvenance({
  trace,
  citations = [],
  defaultOpen = false,
  className,
  classNames,
  labels,
}: Readonly<TuringAnswerProvenanceProps>) {
  const explained = citations.filter(hasExplanation);
  // Nothing to audit → render nothing (the answer surface stays clean).
  if (!trace && explained.length === 0) {
    return null;
  }

  const row = (label: string, value: ReactNode) => (
    <div className={classNames?.row}>
      <span className={classNames?.label}>{label}</span>
      <span className={classNames?.value}>{value}</span>
    </div>
  );

  return (
    <details
      className={className ?? classNames?.root}
      data-turing-answer-provenance=""
      open={defaultOpen || undefined}
    >
      <summary className={classNames?.summary}>{labels?.title ?? "Why this answer"}</summary>
      <div className={classNames?.body}>
        {trace?.kbMode && row(labels?.kbMode ?? "Knowledge base", trace.kbMode)}
        {row(
          labels?.rerank ?? "Rerank",
          trace?.rerankEnabled ? (trace.rerankStrategy ?? "on") : (labels?.rerankOff ?? "off"),
        )}
        {typeof trace?.sourceCount === "number" &&
          row(labels?.sourceCount ?? "Sources", String(trace.sourceCount))}

        {explained.length > 0 && (
          <ul className={classNames?.sources}>
            {explained.map((citation) => (
              <li key={citation.rank} className={classNames?.source}>
                <span className={classNames?.sourceTitle}>
                  [{citation.rank}] {citation.title ?? citation.sourceId ?? ""}
                </span>
                <dl className={classNames?.explanation}>
                  {Object.entries(citation.rankingExplanation ?? {}).map(([key, value]) => (
                    <div key={key} className={classNames?.explanationItem}>
                      <dt className={classNames?.label}>{key}</dt>
                      <dd className={classNames?.value}>{String(value)}</dd>
                    </div>
                  ))}
                </dl>
              </li>
            ))}
          </ul>
        )}
      </div>
    </details>
  );
}
