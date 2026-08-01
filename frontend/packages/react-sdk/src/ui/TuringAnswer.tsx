import { useState, type FormEvent, type ReactNode } from "react";
import {
  TuringMarkdown,
  citationMarkdownComponents,
  rehypeCitationAnchors,
} from "@viglet/turing-react-ui";
import type { TurAnswerCitation } from "../core/types";
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
  answer?: string;
  loading?: string;
  error?: string;
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
}

export interface TuringAnswerProps extends UseTuringAnswerOptions {
  /** Render a search input that calls `ask()` on submit. Default `true`. */
  showInput?: boolean;
  placeholder?: string;
  /** Render-override for the answer body (default: markdown + `[n]` anchors). */
  renderAnswer?: (args: { answer: string; citations: TurAnswerCitation[] }) => ReactNode;
  labels?: TuringAnswerLabels;
  className?: string;
  classNames?: TuringAnswerClassNames;
}

export function TuringAnswer({
  showInput = true,
  placeholder,
  renderAnswer,
  labels,
  className,
  classNames,
  ...answerOptions
}: Readonly<TuringAnswerProps>) {
  const { answer, sources, citations, followups, status, error, isStreaming, ask } =
    useTuringAnswer(answerOptions);
  const [draft, setDraft] = useState("");

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    const query = draft.trim();
    if (!query) return;
    void ask(query);
  };

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

      {error && (
        <div className={classNames?.error} role="alert">
          {error}
        </div>
      )}

      {isStreaming && !answer && (
        <div className={classNames?.loading}>{labels?.loading ?? "…"}</div>
      )}

      {answer && <div className={classNames?.answer}>{answerBody}</div>}

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
              onClick={() => void ask(question)}
            >
              {question}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
