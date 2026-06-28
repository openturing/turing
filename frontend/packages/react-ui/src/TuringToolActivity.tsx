import { type ReactNode } from "react";

/**
 * One tool-call lifecycle entry — the structural mirror of the SDK's
 * `TurChatToolCall` (T436), redeclared here so `@viglet/turing-react-ui` stays
 * zero-runtime-dependency (it must not import `@viglet/turing-sdk`). Entries are
 * expected pre-merged by `callId` (each carries its latest phase): a `start`
 * with no `end` yet renders as running; an `end` renders as finished with its
 * `status`/`durationMs`.
 */
export interface TuringToolCall {
  callId: string;
  name: string;
  phase: "start" | "end";
  argsSummary?: string | null;
  status?: string | null;
  durationMs?: number | null;
}

/** Per-slot class names — the component ships ZERO visual styling of its own. */
export interface TuringToolActivityClassNames {
  /** The wrapping container. */
  container?: string;
  /** The "Tools" heading row. */
  heading?: string;
  /** The list of tool rows. */
  list?: string;
  /** Each tool row. */
  item?: string;
  /** Appended to a row while the tool is still running. */
  itemRunning?: string;
  /** Appended to a row that finished successfully. */
  itemDone?: string;
  /** Appended to a row that finished with an error. */
  itemError?: string;
  /** The tool name. */
  name?: string;
  /** The status text/badge (carries `data-status`). */
  status?: string;
  /** The duration text. */
  duration?: string;
  /** The redacted argument digest (rendered only when present). */
  args?: string;
}

/** Human-readable strings — pass localized values from the host app. */
export interface TuringToolActivityLabels {
  /** Heading above the rows. Default `"Tools"`. */
  heading?: string;
  /** Verb shown while a tool runs (`"{verb} {name}…"`). Default `"Calling"`. */
  calling?: string;
  /** Status text for a running tool (screen-reader + badge). Default `"running"`. */
  running?: string;
  /** Status text for a tool that finished ok. Default `"done"`. */
  done?: string;
  /** Status text for a tool that errored. Default `"failed"`. */
  error?: string;
}

/** Optional icon nodes; when omitted rows show text only (no icon dep). */
export interface TuringToolActivityIcons {
  /** Leading icon on every row (generic tool glyph). */
  tool?: ReactNode;
  /** Shown on a running row (e.g. a spinner). */
  running?: ReactNode;
  /** Shown on a successfully-finished row. */
  done?: ReactNode;
  /** Shown on an errored row. */
  error?: ReactNode;
}

export interface TuringToolActivityProps {
  /** The tool calls to render (merged by `callId`; running + finished). */
  toolCalls: ReadonlyArray<TuringToolCall>;
  /**
   * When `false`, finished tool rows are hidden once the turn's text arrives —
   * only still-running tools show. Default `true` (keep the full activity log).
   */
  showFinished?: boolean;
  /** Convenience alias for {@link TuringToolActivityClassNames.container}. */
  className?: string;
  classNames?: TuringToolActivityClassNames;
  labels?: TuringToolActivityLabels;
  icons?: TuringToolActivityIcons;
}

type ToolState = "running" | "done" | "error";

function stateOf(call: TuringToolCall): ToolState {
  if (call.phase !== "end") return "running";
  return call.status === "error" ? "error" : "done";
}

/**
 * Headless renderer for live tool-call activity (T437). Given the tool calls a
 * turn made — surfaced by the SDK's `tool_call` events (T436) on
 * `ChatMessage.toolCalls` — it renders one row per call showing the tool name,
 * a running/done/error state, and the call duration once finished, so the chat
 * can show "Calling `search_knowledge_base`…" while the answer is prepared.
 *
 * <p>Like the other `@viglet/turing-react-ui` primitives it ships no styling —
 * skin every slot via `className`/`classNames`, localize via `labels`, and plug
 * in `icons` from whatever set the host uses. The admin console and the public
 * SN AI-mode chat share this one implementation. Same skinning contract as
 * {@link TuringThinkingDots} / {@link TuringSourceChips}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
export function TuringToolActivity({
  toolCalls,
  showFinished = true,
  className,
  classNames,
  labels,
  icons,
}: Readonly<TuringToolActivityProps>) {
  const callingLabel = labels?.calling ?? "Calling";
  const headingLabel = labels?.heading ?? "Tools";
  const runningLabel = labels?.running ?? "running";
  const doneLabel = labels?.done ?? "done";
  const errorLabel = labels?.error ?? "failed";

  const visible = showFinished
    ? toolCalls
    : toolCalls.filter((c) => stateOf(c) === "running");

  if (visible.length === 0) return null;

  const statusLabel = (state: ToolState): string => {
    if (state === "running") return runningLabel;
    if (state === "error") return errorLabel;
    return doneLabel;
  };

  const stateIcon = (state: ToolState): ReactNode => {
    if (state === "running") return icons?.running ?? icons?.tool;
    if (state === "error") return icons?.error ?? icons?.tool;
    return icons?.done ?? icons?.tool;
  };

  return (
    <div className={className ?? classNames?.container} data-turing-tool-activity="">
      <div className={classNames?.heading}>{headingLabel}</div>
      <ul className={classNames?.list}>
        {visible.map((call) => {
          const state = stateOf(call);
          const itemClass = [
            classNames?.item,
            state === "running" ? classNames?.itemRunning : undefined,
            state === "done" ? classNames?.itemDone : undefined,
            state === "error" ? classNames?.itemError : undefined,
          ]
            .filter(Boolean)
            .join(" ");
          return (
            <li
              key={call.callId}
              className={itemClass || undefined}
              data-status={state}
              aria-busy={state === "running" || undefined}
            >
              {stateIcon(state)}
              <span className={classNames?.name}>
                {state === "running" ? `${callingLabel} ${call.name}…` : call.name}
              </span>
              <span
                className={classNames?.status}
                data-status={state}
                aria-label={statusLabel(state)}
              >
                {statusLabel(state)}
              </span>
              {state !== "running" && typeof call.durationMs === "number" ? (
                <span className={classNames?.duration}>{`${call.durationMs}ms`}</span>
              ) : null}
              {call.argsSummary ? (
                <span className={classNames?.args}>{call.argsSummary}</span>
              ) : null}
            </li>
          );
        })}
      </ul>
    </div>
  );
}
