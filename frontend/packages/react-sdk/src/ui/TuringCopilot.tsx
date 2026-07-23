import { Fragment, useState, type FormEvent, type ReactNode } from "react";
import type { TurCatalogCitation } from "../core/api";
import {
  useTuringCopilot,
  type CopilotMessage,
  type UseTuringCopilotOptions,
} from "../hooks/use-turing-copilot";

/**
 * Class-name hooks for skinning {@link TuringCopilot}. Every part is optional;
 * the component is otherwise design-agnostic (no bundled styles) so a host site
 * can style it to match its own look — the same skinning contract the other
 * react-sdk UI components follow.
 */
export interface TuringCopilotClassNames {
  root?: string;
  messages?: string;
  message?: string;
  userMessage?: string;
  assistantMessage?: string;
  citations?: string;
  citation?: string;
  citationLink?: string;
  form?: string;
  input?: string;
  button?: string;
  error?: string;
  empty?: string;
}

export interface TuringCopilotProps extends UseTuringCopilotOptions {
  /** Placeholder for the question input. */
  readonly placeholder?: string;
  /** Label for the send button. Default "Ask". */
  readonly sendLabel?: string;
  /** Rendered before the first message. */
  readonly emptyComponent?: () => ReactNode;
  /** Custom renderer for one cited source (overrides the default link). */
  readonly citationComponent?: (props: {
    citation: TurCatalogCitation;
  }) => ReactNode;
  readonly classNames?: TuringCopilotClassNames;
  readonly className?: string;
}

function DefaultCitation({
  citation,
  classNames,
}: {
  citation: TurCatalogCitation;
  classNames?: TuringCopilotClassNames;
}) {
  const label = `[${citation.rank}] ${citation.title ?? citation.id}`;
  return (
    <li className={classNames?.citation}>
      {citation.url ? (
        <a
          className={classNames?.citationLink}
          href={citation.url}
          target="_blank"
          rel="noreferrer"
        >
          {label}
        </a>
      ) : (
        <span>{label}</span>
      )}
    </li>
  );
}

/**
 * T792 / §LIV.3 (Block BF) — a thin, embeddable "Ask the catalog" Q&A widget for
 * the <strong>Vectorless (Structured-Data) RAG</strong> copilot. Wraps
 * {@link useTuringCopilot} and renders the multi-turn conversation with each
 * assistant answer's {@link TurCatalogCitation}[] as linked sources — the
 * copilot analogue of the vector-RAG {@code ai-mode-chat}, so an external static
 * site (e.g. a model-catalog "Ask the catalog" box) can drop it in.
 *
 * <p>Design-agnostic: no bundled CSS, fully skinnable through {@link classNames}.
 * Requires a {@code <TuringProvider>} for the site (or an explicit {@code siteName}).
 *
 * @example
 * ```tsx
 * <TuringProvider config={{ site: "model-catalog", baseURL: "https://turing-demo.viglet.org/api" }}>
 *   <TuringCopilot placeholder="Ask about the model catalog…" />
 * </TuringProvider>
 * ```
 *
 * @since 2026.3.4
 */
export function TuringCopilot({
  placeholder,
  sendLabel = "Ask",
  emptyComponent,
  citationComponent,
  classNames,
  className,
  ...copilotOptions
}: TuringCopilotProps) {
  const { messages, error, isLoading, available, send } =
    useTuringCopilot(copilotOptions);
  const [draft, setDraft] = useState("");

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    const text = draft.trim();
    if (!text || isLoading) return;
    setDraft("");
    void send(text);
  };

  return (
    <div className={className ?? classNames?.root} data-turing-copilot="">
      <div className={classNames?.messages}>
        {messages.length === 0 &&
          (emptyComponent ? (
            emptyComponent()
          ) : (
            <div className={classNames?.empty} />
          ))}
        {messages.map((m: CopilotMessage) => (
          <div
            key={m.id}
            className={`${classNames?.message ?? ""} ${
              m.role === "user"
                ? (classNames?.userMessage ?? "")
                : (classNames?.assistantMessage ?? "")
            }`.trim()}
            data-role={m.role}
          >
            {m.content && <div>{m.content}</div>}
            {m.role === "assistant" && m.citations && m.citations.length > 0 && (
              <ol className={classNames?.citations}>
                {m.citations.map((c) => (
                  <Fragment key={`${m.id}-${c.rank}`}>
                    {citationComponent ? (
                      citationComponent({ citation: c })
                    ) : (
                      <DefaultCitation citation={c} classNames={classNames} />
                    )}
                  </Fragment>
                ))}
              </ol>
            )}
          </div>
        ))}
      </div>

      {error && (
        <div className={classNames?.error} role="alert">
          {error}
        </div>
      )}

      <form className={classNames?.form} onSubmit={onSubmit}>
        <input
          className={classNames?.input}
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          placeholder={placeholder}
          disabled={available === false}
          aria-label={placeholder ?? "Ask the catalog"}
        />
        <button
          className={classNames?.button}
          type="submit"
          disabled={isLoading || available === false || draft.trim().length === 0}
        >
          {isLoading ? "…" : sendLabel}
        </button>
      </form>
    </div>
  );
}
