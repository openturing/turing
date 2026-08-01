import { useEffect, useRef, useState, type FormEvent, type ReactNode } from "react";
import {
  TuringAnswerProvenance,
  TuringMarkdown,
  citationMarkdownComponents,
  rehypeCitationAnchors,
  type TuringAnswerProvenanceClassNames,
  type TuringAnswerProvenanceLabels,
} from "@viglet/turing-react-ui";
import type { AnswerFilters } from "../core/api";
import type { TurAnswerArtifact, TurAnswerCitation } from "../core/types";
import { useTuringAnswer, type UseTuringAnswerOptions } from "../hooks/use-turing-answer";

/**
 * T837 / §LXII.6 — the design-agnostic fused answer surface: a streaming answer
 * with inline `[n]` citation anchors (T836), source cards, and follow-up chips,
 * in one import. On the `TuringCopilot` skinning contract — every part is
 * class-nameable and the component ships ZERO bundled styles, so each surface
 * (admin search page, showcase, public-site hero) keeps its own look while
 * sharing this one implementation.
 *
 * <p>Each source card carries `id="turing-source-${rank}"`, the target the T836
 * `[n]` anchors scroll-link to.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */

export interface TuringAnswerClassNames {
  root?: string;
  form?: string;
  input?: string;
  submit?: string;
  constraints?: string;
  constraintsLabel?: string;
  constraint?: string;
  answer?: string;
  artifacts?: string;
  artifact?: string;
  loading?: string;
  error?: string;
  freshness?: string;
  reanswer?: string;
  sources?: string;
  source?: string;
  sourceRank?: string;
  sourceLink?: string;
  followups?: string;
  followup?: string;
}

export interface TuringAnswerLabels {
  submit?: string;
  loading?: string;
  sources?: string;
  followups?: string;
  /** Leads the constraint chip row. Default "Answering within:". */
  within?: string;
  /** The freshness/drift badge. Default "Some sources changed since this answer.". */
  stale?: string;
  /** The re-answer button. Default "Re-answer". */
  reanswer?: string;
}

export interface TuringAnswerProps extends UseTuringAnswerOptions {
  /** Render a search input that calls `ask()` on submit. Default `true`. */
  showInput?: boolean;
  placeholder?: string;
  /**
   * T838 — the active facet filter set (`field → values`) the answer is
   * synthesized within. Shown as removable constraint chips; changing it re-runs
   * the current question against the narrowed candidates.
   */
  filters?: AnswerFilters;
  /** Called when a constraint chip is removed, so the host can widen the set. */
  onFilterRemove?: (field: string, value: string) => void;
  /** Formats a constraint chip. Default `${field} = ${value}`. */
  formatConstraint?: (field: string, value: string) => string;
  /** Render-override for the answer body (default: markdown + `[n]` anchors). */
  renderAnswer?: (args: { answer: string; citations: TurAnswerCitation[] }) => ReactNode;
  /**
   * T841 — render a structured answer artifact (an answer-as-app comparison table
   * / spec grid). Wire this to the generative-UI components (e.g. via
   * `TuringGenerativeContent` / the `ANSWER_AS_APP_COMPONENTS` registry). When a
   * structured artifact is present the prose becomes a lead-in above it.
   */
  renderArtifact?: (artifact: TurAnswerArtifact) => ReactNode;
  /** T840 — auto re-check source freshness when the answer completes. Default `false`. */
  checkFreshnessOnAnswer?: boolean;
  /** T839 — show the "why this answer" provenance panel. Default `true`. */
  showProvenance?: boolean;
  /** Start the provenance panel expanded. Default `false`. */
  provenanceDefaultOpen?: boolean;
  provenanceClassNames?: TuringAnswerProvenanceClassNames;
  provenanceLabels?: TuringAnswerProvenanceLabels;
  labels?: TuringAnswerLabels;
  className?: string;
  classNames?: TuringAnswerClassNames;
}

/** Flattens a `field → values` filter map into ordered `[field, value]` pairs. */
function filterPairs(filters?: AnswerFilters): Array<[string, string]> {
  if (!filters) return [];
  const pairs: Array<[string, string]> = [];
  for (const [field, values] of Object.entries(filters)) {
    for (const value of values ?? []) pairs.push([field, value]);
  }
  return pairs;
}

export function TuringAnswer({
  showInput = true,
  placeholder,
  filters,
  onFilterRemove,
  formatConstraint,
  renderAnswer,
  renderArtifact,
  checkFreshnessOnAnswer = false,
  showProvenance = true,
  provenanceDefaultOpen = false,
  provenanceClassNames,
  provenanceLabels,
  labels,
  className,
  classNames,
  ...answerOptions
}: Readonly<TuringAnswerProps>) {
  const { answer, sources, citations, followups, artifacts, trace, status, error, isStreaming, staleSources, ask, checkFreshness } =
    useTuringAnswer(answerOptions);
  const [draft, setDraft] = useState("");
  // The last question asked — so a facet change (below) re-runs the same question.
  const lastQueryRef = useRef("");

  // T840 — opt-in: re-check source freshness once the answer completes.
  useEffect(() => {
    if (checkFreshnessOnAnswer && status === "success") void checkFreshness();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [status, checkFreshnessOnAnswer]);

  const constraints = filterPairs(filters);
  const filtersKey = JSON.stringify(filters ?? {});

  // T838 — turning a facet re-synthesizes the current answer within the new set.
  useEffect(() => {
    if (lastQueryRef.current) void ask(lastQueryRef.current, { filters });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filtersKey]);

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    const query = draft.trim();
    if (!query) return;
    lastQueryRef.current = query;
    void ask(query, { filters });
  };

  const askFollowup = (question: string) => {
    lastQueryRef.current = question;
    void ask(question, { filters });
  };

  const constraintLabel = (field: string, value: string) =>
    formatConstraint ? formatConstraint(field, value) : `${field} = ${value}`;

  const answerBody = renderAnswer ? (
    renderAnswer({ answer, citations })
  ) : (
    <TuringMarkdown
      rehypePlugins={[rehypeCitationAnchors]}
      components={citationMarkdownComponents({
        citations: citations.map((c) => ({
          index: c.rank,
          label: c.title ?? undefined,
          citedText: c.citedText ?? undefined,
          url: c.url ?? undefined,
        })),
      })}
    >
      {answer}
    </TuringMarkdown>
  );

  return (
    <div className={className ?? classNames?.root} data-turing-answer="">
      {showInput && (
        <form className={classNames?.form} onSubmit={onSubmit}>
          <input
            className={classNames?.input}
            value={draft}
            placeholder={placeholder}
            onChange={(e) => setDraft(e.target.value)}
          />
          <button type="submit" className={classNames?.submit}>
            {labels?.submit ?? "Ask"}
          </button>
        </form>
      )}

      {constraints.length > 0 && (
        <div className={classNames?.constraints} data-turing-answer-constraints="">
          <span className={classNames?.constraintsLabel}>
            {labels?.within ?? "Answering within:"}
          </span>
          {constraints.map(([field, value]) => (
            <button
              key={`${field}:${value}`}
              type="button"
              className={classNames?.constraint}
              onClick={() => onFilterRemove?.(field, value)}
              aria-label={`Remove constraint ${constraintLabel(field, value)}`}
            >
              {constraintLabel(field, value)} ×
            </button>
          ))}
        </div>
      )}

      {error && (
        <div className={classNames?.error} role="alert">
          {error}
        </div>
      )}

      {isStreaming && !answer && (
        <div className={classNames?.loading}>{labels?.loading ?? "…"}</div>
      )}

      {answer && <div className={classNames?.answer}>{answerBody}</div>}

      {renderArtifact && artifacts.length > 0 && (
        <div className={classNames?.artifacts} data-turing-answer-artifacts="">
          {artifacts.map((artifact, index) => (
            <div key={artifact.callId ?? `${artifact.name}-${index}`} className={classNames?.artifact}>
              {renderArtifact(artifact)}
            </div>
          ))}
        </div>
      )}

      {staleSources.length > 0 && (
        <div className={classNames?.freshness} data-turing-answer-stale="" role="status">
          {labels?.stale ?? "Some sources changed since this answer."}{" "}
          <button
            type="button"
            className={classNames?.reanswer}
            onClick={() => {
              if (lastQueryRef.current) void ask(lastQueryRef.current, { filters });
            }}
          >
            {labels?.reanswer ?? "Re-answer"}
          </button>
        </div>
      )}

      {showProvenance && answer && (
        <TuringAnswerProvenance
          trace={trace}
          citations={citations.map((c) => ({
            rank: c.rank,
            title: c.title,
            sourceId: c.sourceId,
            rankingExplanation: c.rankingExplanation,
          }))}
          defaultOpen={provenanceDefaultOpen}
          classNames={provenanceClassNames}
          labels={provenanceLabels}
        />
      )}

      {sources.length > 0 && (
        <ol className={classNames?.sources} aria-label={labels?.sources ?? "Sources"}>
          {sources.map((source) => (
            <li
              key={source.rank ?? source.sourceId}
              id={source.rank != null ? `turing-source-${source.rank}` : undefined}
              className={classNames?.source}
              data-turing-source-rank={source.rank ?? undefined}
            >
              {source.rank != null && (
                <span className={classNames?.sourceRank}>[{source.rank}]</span>
              )}{" "}
              {source.url ? (
                <a className={classNames?.sourceLink} href={source.url} target="_blank" rel="noreferrer">
                  {source.title}
                </a>
              ) : (
                source.title
              )}
            </li>
          ))}
        </ol>
      )}

      {followups.length > 0 && (
        <div className={classNames?.followups} aria-label={labels?.followups ?? "Follow-up questions"}>
          {followups.map((question) => (
            <button
              key={question}
              type="button"
              className={classNames?.followup}
              onClick={() => askFollowup(question)}
            >
              {question}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
