"use client"
import { Badge } from "@/components/ui/badge";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  SYSTEM_PROMPT_FLOW_NONE,
  type TurSystemPromptCapturedTurn,
  type TurSystemPromptInertRegion,
  type TurSystemPromptMessage,
  type TurSystemPromptMessageOrigin,
  type TurSystemPromptOrigin,
  type TurSystemPromptPreview,
  type TurSystemPromptReplay,
  type TurSystemPromptSegment,
  type TurSystemPromptTool,
} from "@/models/agent/system-prompt.model";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";
import {
  IconAlertTriangle,
  IconBolt,
  IconClipboard,
  IconClock,
  IconCoin,
  IconDatabase,
  IconHistory,
  IconMessages,
  IconPlus,
  IconRoute,
  IconSitemap,
  IconStack2,
  IconTool,
  IconX,
} from "@tabler/icons-react";
import { toast } from "@viglet/viglet-design-system";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import rehypeHighlight from "rehype-highlight";
import "@/app/console/chat/chat-highlight.css";

/**
 * "Live Preview" — the assembled system message rendered as one flowing,
 * Medium-style article. Instead of a scrolling text box per fragment (which
 * made the full prompt impossible to read top-to-bottom), every segment is
 * rendered as formatted Markdown in a single continuous column, in the exact
 * order the model receives it.
 *
 * Provenance is surfaced on interaction: hovering a passage tints it with its
 * origin color and floats a label naming the part (Persona, Agent prompt, MCP,
 * chat flow, RAG …). A persistent legend + faint left accent keep the color
 * coding legible before the operator hovers. A chat-flow selector re-assembles
 * the article per flow, and a copy button grabs the verbatim "what the model
 * sees" (prompt + tool schemas).
 *
 * @since 2026.3.1
 */

interface Props {
  readonly preview: TurSystemPromptPreview | undefined;
  readonly isLoading: boolean;
  /** Current flow-selector state (undefined → backend default). */
  readonly flowId: string | undefined;
  readonly onFlowChange: (value: string) => void;
  /** T611 — the picked flow node (undefined = entry node). */
  readonly nodeId?: string | undefined;
  readonly onNodeChange?: (nodeId: string | undefined) => void;
  /** T611 — serialized simulated collected values ({} JSON), or undefined. */
  readonly onVarsChange?: (vars: string | undefined) => void;
  /**
   * T612 — current "replay a real conversation" id (undefined = not replaying).
   * When `onReplayChange` is supplied the control bar shows a replay input; while
   * a conversation is being replayed the synthetic flow/node/vars pickers are
   * hidden (the replay derives them from the conversation's real state).
   */
  readonly replayConversationId?: string | undefined;
  readonly onReplayChange?: (conversationId: string | undefined) => void;
  /**
   * T618 — the captured past turn to show verbatim (undefined = T612
   * current-state replay). The picker is populated from
   * `preview.replay.availableTurns`.
   */
  readonly replayTurnIndex?: number | undefined;
  readonly onReplayTurnChange?: (turnIndex: number | undefined) => void;
  /**
   * Render the article's own masthead (H1 title + description lede). The console
   * page has no page header, so it needs one; the bento page already supplies a
   * hero with the same title AND uses this description as its subtitle, so it
   * passes `false` to avoid duplicating both. Defaults to true.
   */
  readonly showHeader?: boolean;
}

/** Node-picker sentinel meaning "the flow's entry node" (clears nodeId). */
const NODE_ENTRY = "__entry__";

/** T618 — turn-picker sentinel meaning "current state (reconstructed)". */
const TURN_CURRENT = "__current__";

/** Tailwind accent classes per origin — accent bar, hover tint, badge, legend dot. */
const ORIGIN_STYLE: Record<
  TurSystemPromptOrigin,
  { bar: string; hover: string; badge: string; dot: string }
> = {
  PERSONA: {
    bar: "bg-violet-500",
    hover: "group-hover:bg-violet-500/6",
    badge: "bg-violet-500/10 text-violet-600 border-violet-500/20 dark:text-violet-300",
    dot: "bg-violet-500",
  },
  AGENT: {
    bar: "bg-blue-500",
    hover: "group-hover:bg-blue-500/6",
    badge: "bg-blue-500/10 text-blue-600 border-blue-500/20 dark:text-blue-300",
    dot: "bg-blue-500",
  },
  MCP: {
    bar: "bg-cyan-500",
    hover: "group-hover:bg-cyan-500/6",
    badge: "bg-cyan-500/10 text-cyan-600 border-cyan-500/20 dark:text-cyan-300",
    dot: "bg-cyan-500",
  },
  FLOW: {
    bar: "bg-amber-500",
    hover: "group-hover:bg-amber-500/6",
    badge: "bg-amber-500/10 text-amber-600 border-amber-500/20 dark:text-amber-300",
    dot: "bg-amber-500",
  },
  FEW_SHOT: {
    bar: "bg-slate-400",
    hover: "group-hover:bg-slate-500/6",
    badge: "bg-slate-500/10 text-slate-600 border-slate-500/20 dark:text-slate-300",
    dot: "bg-slate-400",
  },
  RAG: {
    bar: "bg-emerald-500",
    hover: "group-hover:bg-emerald-500/6",
    badge: "bg-emerald-500/10 text-emerald-600 border-emerald-500/20 dark:text-emerald-300",
    dot: "bg-emerald-500",
  },
  // T618 — a verbatim captured past turn (the whole system message as one block).
  CAPTURED: {
    bar: "bg-indigo-500",
    hover: "group-hover:bg-indigo-500/6",
    badge: "bg-indigo-500/10 text-indigo-600 border-indigo-500/20 dark:text-indigo-300",
    dot: "bg-indigo-500",
  },
};

/** Origins in the fixed order they may appear, for the legend. */
const LEGEND_ORDER: TurSystemPromptOrigin[] = [
  "PERSONA",
  "AGENT",
  "FLOW",
  "MCP",
  "FEW_SHOT",
  "RAG",
];

/** T617 — accent classes per history-prefix message origin (dot + left bar). */
const MESSAGE_ORIGIN_STYLE: Record<
  TurSystemPromptMessageOrigin,
  { bar: string; dot: string; badge: string }
> = {
  MEMORY_SUMMARY: {
    bar: "bg-fuchsia-500",
    dot: "bg-fuchsia-500",
    badge:
      "bg-fuchsia-500/10 text-fuchsia-600 border-fuchsia-500/20 dark:text-fuchsia-300",
  },
  MEMORY_RELEVANCE: {
    bar: "bg-teal-500",
    dot: "bg-teal-500",
    badge: "bg-teal-500/10 text-teal-600 border-teal-500/20 dark:text-teal-300",
  },
  // T618 — a message from a verbatim captured past turn.
  CAPTURED: {
    bar: "bg-indigo-500",
    dot: "bg-indigo-500",
    badge: "bg-indigo-500/10 text-indigo-600 border-indigo-500/20 dark:text-indigo-300",
  },
};

export function SystemPromptLivePreview({
  preview,
  isLoading,
  flowId,
  onFlowChange,
  nodeId,
  onNodeChange,
  onVarsChange,
  replayConversationId,
  onReplayChange,
  replayTurnIndex,
  onReplayTurnChange,
  showHeader = true,
}: Props) {
  const { t } = useTranslation();
  // T612 — while replaying a real conversation the synthetic flow/node/vars
  // pickers don't apply (the backend derives them from the conversation state).
  const replaying = Boolean(replayConversationId?.trim());

  function originLabel(origin: TurSystemPromptOrigin): string {
    return t(`aiAgent.systemPrompt.origin.${origin}`, { defaultValue: origin });
  }

  const flowValue = flowId ?? preview?.selectedFlowId ?? SYSTEM_PROMPT_FLOW_NONE;

  const toolsHeading = t("aiAgent.systemPrompt.preview.toolsHeading", {
    defaultValue: "Tools available to the model",
  });
  const toolsNote = t("aiAgent.systemPrompt.preview.toolsNote", {
    defaultValue: "Provided to the model as function/tool schemas (not literal prompt text).",
  });

  // The verbatim system message the model receives for the previewed turn:
  // the assembled prompt plus the tool definitions sent alongside it. Used by
  // the copy button so the operator can grab the bottom-line "what the model
  // sees" in one click.
  const fullText = useMemo(() => {
    if (!preview) return "";
    return preview.assembledText + buildToolsMarkdown(preview.tools, toolsHeading, toolsNote);
  }, [preview, toolsHeading, toolsNote]);

  // Only advertise origins that actually appear in this preview, so the legend
  // doesn't imply a passage exists when it doesn't.
  const presentOrigins = useMemo(() => {
    if (!preview) return [];
    const present = new Set(preview.segments.map((s) => s.origin));
    return LEGEND_ORDER.filter((o) => present.has(o));
  }, [preview]);

  async function copyFull() {
    if (!fullText) return;
    try {
      await navigator.clipboard.writeText(fullText);
      toast.success(t("aiAgent.systemPrompt.preview.copied", { defaultValue: "Copied to clipboard" }));
    } catch {
      toast.error(t("aiAgent.systemPrompt.preview.copyFailed", { defaultValue: "Could not copy" }));
    }
  }

  return (
    <div className="mx-auto w-full max-w-5xl">
      {/* Article masthead — dropped entirely when a page hero already supplies
          the title and uses this description as its subtitle (bento). */}
      {showHeader && (
        <header className="mb-6">
          <h1 className="text-2xl font-semibold tracking-tight text-foreground">
            {t("aiAgent.systemPrompt.preview.title", { defaultValue: "Live Preview" })}
          </h1>
          <p className="mt-2 text-sm leading-relaxed text-muted-foreground">
            {t("aiAgent.systemPrompt.preview.description", {
              defaultValue:
                "Everything below is concatenated into one system message before it reaches the model. Each block is tagged with where it comes from.",
            })}
          </p>
        </header>
      )}

      {/* T612 — replay a real conversation: enter a conversation id to rebuild
          the assembled prompt from its persisted state (ground truth), or clear
          it to return to the synthesized preview. */}
      {onReplayChange && (
        <ReplayInput
          value={replayConversationId}
          onChange={onReplayChange}
          availableTurns={preview?.replay?.availableTurns ?? []}
          turnIndex={replayTurnIndex}
          onTurnChange={onReplayTurnChange}
        />
      )}

      {/* Control bar: flow selector + copy */}
      <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
        {!replaying && preview && preview.flows.length > 0 ? (
          <div className="flex flex-wrap items-center gap-2">
            <IconSitemap className="size-4 text-muted-foreground" />
            <span className="text-sm font-medium">
              {t("aiAgent.systemPrompt.preview.flowLabel", { defaultValue: "Preview with chat flow" })}
            </span>
            <Select value={flowValue} onValueChange={onFlowChange}>
              <SelectTrigger className="w-full sm:w-72">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={SYSTEM_PROMPT_FLOW_NONE}>
                  {t("aiAgent.systemPrompt.preview.flowNone", { defaultValue: "No flow (plain turn)" })}
                </SelectItem>
                {preview.flows.map((flow) => (
                  <SelectItem key={flow.id} value={flow.id}>
                    {flow.name}
                    {!flow.enabled && ` (${t("aiAgent.systemPrompt.preview.flowDisabled", { defaultValue: "disabled" })})`}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {/* T611 — node picker: preview any node's turn, not just the entry. */}
            {onNodeChange && preview.flowNodes.length > 0 && (
              <>
                <IconRoute className="size-4 text-muted-foreground" />
                <Select
                  value={nodeId ?? NODE_ENTRY}
                  onValueChange={(v) => onNodeChange(v === NODE_ENTRY ? undefined : v)}
                >
                  <SelectTrigger className="w-full sm:w-64">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value={NODE_ENTRY}>
                      {t("aiAgent.systemPrompt.preview.nodeEntry", { defaultValue: "Entry node (default)" })}
                    </SelectItem>
                    {preview.flowNodes.map((node) => (
                      <SelectItem key={node.id} value={node.id}>
                        {node.label}
                        <span className="text-muted-foreground"> · {node.type}</span>
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </>
            )}
          </div>
        ) : (
          <span />
        )}
        {!isLoading && preview && fullText && (
          <button
            type="button"
            onClick={copyFull}
            className="inline-flex items-center gap-1.5 rounded-md border px-2 py-1 text-xs text-muted-foreground hover:text-foreground hover:bg-accent/50 transition-colors"
          >
            <IconClipboard className="size-3.5" />
            {t("aiAgent.systemPrompt.preview.copy", { defaultValue: "Copy full prompt" })}
          </button>
        )}
      </div>

      {/* T612 — the replay banner: what real conversation this prompt was rebuilt
          from (flow / cursor node / resolved persona), shown in place of the
          synthetic pickers. */}
      {!isLoading && preview?.replay && <ReplayBanner replay={preview.replay} />}

      {/* T611 — simulated collected values: seed slots to preview a real
          mid-conversation turn (the "Already collected" line reflects them).
          Suppressed while replaying (the real slots come from the conversation). */}
      {!replaying && onVarsChange && preview && preview.flowNodes.length > 0 && (
        <SimulatedVariablesEditor key={flowValue} onVarsChange={onVarsChange} />
      )}

      {/* Legend — persistent color key so the origin coding reads even without hover */}
      {!isLoading && presentOrigins.length > 0 && (
        <div className="mb-6 flex flex-wrap items-center gap-x-4 gap-y-1.5 rounded-lg border bg-muted/30 px-3 py-2">
          {presentOrigins.map((origin) => (
            <span key={origin} className="inline-flex items-center gap-1.5 text-xs text-muted-foreground">
              <span className={cn("size-2 rounded-full", ORIGIN_STYLE[origin].dot)} />
              {originLabel(origin)}
            </span>
          ))}
          <span className="ml-auto hidden text-xs text-muted-foreground/70 sm:inline">
            {t("aiAgent.systemPrompt.preview.hoverHint", {
              defaultValue: "Hover a passage to see where it comes from",
            })}
          </span>
        </div>
      )}

      {/* T608 — per-turn token accounting: grand total, the cacheable-prefix
          vs per-turn split (what T614's ordering saves), and an optional cost. */}
      {!isLoading && preview && preview.totalTokens > 0 && (
        <MetricsBar preview={preview} />
      )}

      {isLoading && (
        <p className="text-sm text-muted-foreground">{t("common.loading", { defaultValue: "Loading…" })}</p>
      )}

      {/* The assembled system message as one flowing article */}
      {!isLoading && preview && (
        <article>
          {preview.segments.map((segment, idx) => (
            <SegmentPassage
              key={`${segment.origin}-${idx}`}
              segment={segment}
              totalTokens={preview.totalTokens}
              label={originLabel(segment.origin)}
              runtimeLabel={t("aiAgent.systemPrompt.preview.runtimeOnly", { defaultValue: "runtime only" })}
              emptyLabel={t("aiAgent.systemPrompt.preview.noFixedText", {
                defaultValue: "No fixed text — depends on the live conversation.",
              })}
              tokLabel={t("aiAgent.systemPrompt.preview.tokens", { defaultValue: "tok" })}
            />
          ))}

          {/* Tool definitions the model receives alongside the prompt. Not part
              of the literal system text, so they render as a closing appendix
              (mirrors what the model is actually handed). */}
          {preview.tools.length > 0 && (
            <section className="group relative mt-6 rounded-xl px-5 py-3 transition-colors duration-200 group-hover:bg-cyan-500/6">
              <span className="absolute left-0 top-3 bottom-3 w-1 rounded-full bg-cyan-500 opacity-30 transition-opacity group-hover:opacity-100" />
              <div className="mb-2 flex items-center gap-2">
                <IconTool className="size-4 text-cyan-600 dark:text-cyan-300" />
                <span className="text-sm font-semibold text-foreground">{toolsHeading}</span>
              </div>
              <p className="mb-3 text-xs text-muted-foreground">{toolsNote}</p>
              <ul className="space-y-1.5">
                {preview.tools.map((tool, idx) => (
                  <li key={`${tool.name}-${idx}`} className="text-sm leading-relaxed">
                    <code className="rounded bg-muted/60 px-1 py-0.5 font-mono text-[0.85em] text-foreground">
                      {tool.name}
                    </code>
                    <span className="text-muted-foreground"> ({tool.source})</span>
                    {tool.description && <span className="text-muted-foreground">: {tool.description}</span>}
                  </li>
                ))}
              </ul>
            </section>
          )}
        </article>
      )}

      {/* T617 — the whole message list: the system message above, then the
          history-prefix turns (T115 memory summary + T30 relevance) the runtime
          prepends, then the client's recent window. Mirrors the same
          TurMessageAssemblyPipeline the runtime uses, so the operator inspects the
          entire list, not just the system message. */}
      {!isLoading && preview && preview.messages.length > 0 && (
        <MessageList messages={preview.messages} />
      )}

      {/* T612 — the tool calls the model actually made on the replayed
          conversation (from the T427 trace): cross-read "what the prompt said"
          against "what the model did". Only in replay mode. */}
      {!isLoading && preview?.replay && preview.replay.toolCalls.length > 0 && (
        <ToolCallTrace calls={preview.replay.toolCalls} />
      )}
    </div>
  );
}

/**
 * T612 — the "replay a real conversation" input. The operator pastes a
 * conversation id (the value the SDK persists in the session cookie); the page
 * swaps the synthesized preview for one rebuilt from that conversation's real
 * persisted state. A clear button returns to the synthesized preview.
 */
function ReplayInput({
  value,
  onChange,
  availableTurns,
  turnIndex,
  onTurnChange,
}: Readonly<{
  value: string | undefined;
  onChange: (conversationId: string | undefined) => void;
  /** T618 — captured turns for the current conversation (newest first). */
  availableTurns: TurSystemPromptCapturedTurn[];
  /** T618 — the selected captured turn (undefined = current-state replay). */
  turnIndex: number | undefined;
  onTurnChange?: (turnIndex: number | undefined) => void;
}>) {
  const { t } = useTranslation();
  const [draft, setDraft] = useState(value ?? "");
  const active = Boolean(value?.trim());

  function apply() {
    const trimmed = draft.trim();
    onChange(trimmed ? trimmed : undefined);
  }

  function clear() {
    setDraft("");
    onChange(undefined);
  }

  // T618 — the turn picker: "current" (T612 reconstruction) + one entry per
  // captured past turn (T618 verbatim). Only shown once a conversation is being
  // replayed and it has captured turns.
  const showTurnPicker = active && onTurnChange && availableTurns.length > 0;

  return (
    <div className="mb-4 flex flex-wrap items-center gap-2 rounded-lg border border-dashed bg-muted/20 px-3 py-2">
      <IconHistory className="size-4 text-muted-foreground" />
      <span className="text-sm font-medium">
        {t("aiAgent.systemPrompt.preview.replayLabel", {
          defaultValue: "Replay a real conversation",
        })}
      </span>
      <Input
        value={draft}
        onChange={(e) => setDraft(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === "Enter") apply();
        }}
        placeholder={t("aiAgent.systemPrompt.preview.replayPlaceholder", {
          defaultValue: "conversation id",
        })}
        className="h-8 w-full text-xs sm:w-72"
      />
      <button
        type="button"
        onClick={apply}
        disabled={!draft.trim()}
        className="inline-flex items-center gap-1.5 rounded-md border px-2 py-1 text-xs text-muted-foreground hover:bg-accent/50 hover:text-foreground disabled:opacity-50"
      >
        {t("aiAgent.systemPrompt.preview.replayApply", { defaultValue: "Replay" })}
      </button>
      {showTurnPicker && (
        <div className="flex items-center gap-1.5">
          <span className="text-xs text-muted-foreground">
            {t("aiAgent.systemPrompt.preview.replayTurnLabel", { defaultValue: "Turn" })}
          </span>
          <Select
            value={turnIndex == null ? TURN_CURRENT : String(turnIndex)}
            onValueChange={(v) =>
              onTurnChange(v === TURN_CURRENT ? undefined : Number(v))
            }
          >
            <SelectTrigger className="h-8 w-full text-xs sm:w-56">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={TURN_CURRENT}>
                {t("aiAgent.systemPrompt.preview.replayTurnCurrent", {
                  defaultValue: "Current state (reconstructed)",
                })}
              </SelectItem>
              {availableTurns.map((turn) => (
                <SelectItem key={turn.turnIndex} value={String(turn.turnIndex)}>
                  {t("aiAgent.systemPrompt.preview.replayTurnOption", {
                    defaultValue: "Turn {{n}}{{when}} (verbatim)",
                    n: turn.turnIndex,
                    when: turn.capturedAt
                      ? ` · ${new Date(turn.capturedAt).toLocaleString()}`
                      : "",
                  })}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      )}
      {active && (
        <button
          type="button"
          onClick={clear}
          className="inline-flex items-center gap-1 rounded-md border px-2 py-1 text-xs text-muted-foreground hover:bg-accent/50 hover:text-foreground"
        >
          <IconX className="size-3.5" />
          {t("aiAgent.systemPrompt.preview.replayClear", { defaultValue: "Exit replay" })}
        </button>
      )}
    </div>
  );
}

/**
 * T612 — the provenance banner for a replayed preview: names the source
 * conversation and the real flow / cursor node / resolved persona the prompt was
 * rebuilt at, so the operator knows this is ground truth, not a synthesized turn.
 * When the id had no persisted state it says so (the preview fell back to a plain
 * turn).
 */
function ReplayBanner({ replay }: Readonly<{ replay: TurSystemPromptReplay }>) {
  const { t } = useTranslation();
  return (
    <div
      className={cn(
        "mb-4 rounded-lg border px-3 py-2.5 text-sm",
        replay.resolved
          ? "border-indigo-500/30 bg-indigo-500/5"
          : "border-amber-500/40 bg-amber-500/5",
      )}
    >
      <div className="flex items-center gap-2 font-medium text-foreground">
        {replay.resolved ? (
          <IconHistory className="size-4 text-indigo-600 dark:text-indigo-300" />
        ) : (
          <IconAlertTriangle className="size-4 text-amber-600 dark:text-amber-300" />
        )}
        {replay.verbatim
          ? t("aiAgent.systemPrompt.preview.replayVerbatim", {
              defaultValue:
                "Turn {{n}} loaded verbatim — the exact prompt sent at that turn",
              n: replay.turnIndex ?? 0,
            })
          : replay.resolved
            ? t("aiAgent.systemPrompt.preview.replayResolved", {
                defaultValue: "Replayed from a real conversation",
              })
            : t("aiAgent.systemPrompt.preview.replayUnresolved", {
                defaultValue:
                  "No persisted state for this conversation — showing a plain turn",
              })}
      </div>
      <div className="mt-1.5 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-muted-foreground">
        <span>
          <span className="text-muted-foreground/70">
            {t("aiAgent.systemPrompt.preview.replayConversation", {
              defaultValue: "Conversation",
            })}
            :{" "}
          </span>
          <code className="rounded bg-muted/60 px-1 py-0.5 font-mono text-foreground">
            {replay.conversationId}
          </code>
        </span>
        {replay.flowName && (
          <span className="inline-flex items-center gap-1">
            <IconSitemap className="size-3.5" />
            {replay.flowName}
            {replay.nodeId && (
              <>
                {" "}
                <span className="text-muted-foreground/70">→</span> {replay.nodeId}
              </>
            )}
          </span>
        )}
        {replay.personaName && (
          <span className="inline-flex items-center gap-1">
            <span className="size-2 rounded-full bg-violet-500" />
            {replay.personaName}
          </span>
        )}
      </div>
    </div>
  );
}

/**
 * T612 — the tool calls the model actually made on the replayed conversation
 * (the T427 trace), oldest first: name, redacted args digest, ok/error status
 * and duration. This is "what the model did", to be read against "what the
 * prompt told it" above.
 */
function ToolCallTrace({
  calls,
}: Readonly<{ calls: TurSystemPromptReplay["toolCalls"] }>) {
  const { t } = useTranslation();
  return (
    <section className="mt-8 rounded-xl border bg-muted/20 p-4">
      <div className="mb-1 flex items-center gap-2">
        <IconBolt className="size-4 text-muted-foreground" />
        <span className="text-sm font-semibold text-foreground">
          {t("aiAgent.systemPrompt.preview.toolTraceHeading", {
            defaultValue: "Tool calls on this conversation",
          })}
        </span>
      </div>
      <p className="mb-4 text-xs text-muted-foreground">
        {t("aiAgent.systemPrompt.preview.toolTraceNote", {
          defaultValue:
            "What the model actually called during this conversation (recent, capped) — read against the prompt above.",
        })}
      </p>
      <ol className="space-y-1.5">
        {calls.map((call, idx) => (
          <li
            key={`${call.name}-${idx}`}
            className="flex flex-wrap items-center gap-2 rounded-lg border bg-background/60 px-3 py-1.5 text-sm"
          >
            <span
              className={cn(
                "inline-flex size-2 shrink-0 rounded-full",
                call.status === "ok" ? "bg-emerald-500" : "bg-red-500",
              )}
            />
            <code className="rounded bg-muted/60 px-1 py-0.5 font-mono text-[0.85em] text-foreground">
              {call.name}
            </code>
            {call.argsSummary && (
              <span className="truncate text-xs text-muted-foreground">
                {call.argsSummary}
              </span>
            )}
            <span className="ml-auto inline-flex items-center gap-2 text-[10px] text-muted-foreground">
              <span
                className={cn(
                  "rounded-full border px-1.5 py-0.5 uppercase",
                  call.status === "ok"
                    ? "border-emerald-500/30 text-emerald-600 dark:text-emerald-300"
                    : "border-red-500/30 text-red-600 dark:text-red-300",
                )}
              >
                {call.status}
              </span>
              {call.durationMs > 0 && <span>{call.durationMs} ms</span>}
            </span>
          </li>
        ))}
      </ol>
    </section>
  );
}

/**
 * T617 — renders the ordered message list the model actually receives: a compact
 * reference to the assembled system message (the article above), each
 * history-prefix message with its role + origin provenance, and a trailing marker
 * for the client's recent conversation window. Informational (runtimeOnly)
 * messages render their explanatory note in place of a body, exactly like the
 * few-shot / RAG system segments.
 */
function MessageList({
  messages,
}: Readonly<{ messages: TurSystemPromptMessage[] }>) {
  const { t } = useTranslation();
  return (
    <section className="mt-8 rounded-xl border bg-muted/20 p-4">
      <div className="mb-1 flex items-center gap-2">
        <IconMessages className="size-4 text-muted-foreground" />
        <span className="text-sm font-semibold text-foreground">
          {t("aiAgent.systemPrompt.preview.messageListHeading", {
            defaultValue: "Message list",
          })}
        </span>
      </div>
      <p className="mb-4 text-xs text-muted-foreground">
        {t("aiAgent.systemPrompt.preview.messageListNote", {
          defaultValue:
            "The full ordered list of messages sent to the model: the system message, the history-prefix turns prepended from memory, then the recent conversation.",
        })}
      </p>

      <ol className="space-y-2">
        {/* The assembled system message — shown above as the article. */}
        <li className="flex items-start gap-3 rounded-lg border border-dashed bg-background/40 px-3 py-2">
          <span className="mt-0.5 inline-flex items-center rounded-full border bg-background px-2 py-0.5 text-[10px] font-medium uppercase text-muted-foreground">
            {t("aiAgent.systemPrompt.preview.roleSystem", { defaultValue: "system" })}
          </span>
          <p className="text-xs leading-relaxed text-muted-foreground">
            {t("aiAgent.systemPrompt.preview.systemMessageRef", {
              defaultValue: "The assembled system message shown above.",
            })}
          </p>
        </li>

        {messages.map((message, idx) => (
          <MessageRow key={`${message.origin}-${idx}`} message={message} />
        ))}

        {/* The client's recent conversation window closes the list. */}
        <li className="flex items-start gap-3 rounded-lg border border-dashed bg-background/40 px-3 py-2">
          <IconStack2 className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
          <p className="text-xs leading-relaxed text-muted-foreground">
            {t("aiAgent.systemPrompt.preview.recentWindowRef", {
              defaultValue:
                "…then the client's recent conversation window (the latest user/assistant turns).",
            })}
          </p>
        </li>
      </ol>
    </section>
  );
}

/** One history-prefix message row: role + origin badges, body or placeholder note. */
function MessageRow({ message }: Readonly<{ message: TurSystemPromptMessage }>) {
  const { t } = useTranslation();
  const style = MESSAGE_ORIGIN_STYLE[message.origin];
  const originLabel = t(`aiAgent.systemPrompt.preview.messageOrigin.${message.origin}`, {
    defaultValue: message.label,
  });
  const tokLabel = t("aiAgent.systemPrompt.preview.tokens", { defaultValue: "tok" });
  return (
    <li
      className={cn(
        "relative rounded-lg border bg-background/60 px-3 py-2 pl-4",
        message.runtimeOnly && "opacity-70",
      )}
    >
      <span className={cn("absolute left-0 top-2 bottom-2 w-1 rounded-full", style.bar)} />
      <div className="mb-1 flex flex-wrap items-center gap-1.5">
        <span className="inline-flex items-center rounded-full border bg-background px-2 py-0.5 text-[10px] font-medium uppercase text-muted-foreground">
          {message.role}
        </span>
        <Badge
          variant="outline"
          className={cn("bg-background text-[10px] uppercase", style.badge)}
        >
          {originLabel}
        </Badge>
        {message.runtimeOnly && (
          <span className="inline-flex items-center gap-1 rounded-full border border-amber-500/30 bg-background px-2 py-0.5 text-[10px] font-medium text-amber-600 dark:text-amber-300">
            <IconClock className="size-3" />
            {t("aiAgent.systemPrompt.preview.runtimeOnly", { defaultValue: "runtime only" })}
          </span>
        )}
        {message.tokens > 0 && (
          <span className="inline-flex items-center rounded-md border bg-background px-1.5 py-0.5 text-[10px] font-medium text-muted-foreground">
            {message.tokens} {tokLabel}
          </span>
        )}
      </div>
      {message.content ? (
        <div className={PROSE_CLASSES}>
          <ReactMarkdown remarkPlugins={[remarkGfm]} rehypePlugins={[rehypeHighlight]}>
            {preserveSoftBreaks(message.content)}
          </ReactMarkdown>
        </div>
      ) : (
        message.note && (
          <p className="text-xs italic leading-relaxed text-muted-foreground/80">
            {t(message.note, { defaultValue: message.note })}
          </p>
        )
      )}
    </li>
  );
}

/**
 * T611 — a key/value editor for the collected slot values fed into the previewed
 * turn. Serializes non-empty rows to a JSON object and hands it up via
 * `onVarsChange`, so the flow addendum's "Already collected" line + next-step
 * hint reflect a real mid-conversation state. Remounted (keyed by flow) so the
 * simulated state resets when the operator switches flows.
 */
function SimulatedVariablesEditor({
  onVarsChange,
}: Readonly<{ onVarsChange: (vars: string | undefined) => void }>) {
  const { t } = useTranslation();
  const [rows, setRows] = useState<Array<{ key: string; value: string }>>([]);

  function push(next: Array<{ key: string; value: string }>) {
    setRows(next);
    const obj: Record<string, string> = {};
    for (const r of next) {
      const k = r.key.trim();
      if (k) obj[k] = r.value;
    }
    onVarsChange(Object.keys(obj).length > 0 ? JSON.stringify(obj) : undefined);
  }

  return (
    <div className="mb-4 rounded-lg border bg-muted/20 px-3 py-2">
      <div className="mb-1.5 flex items-center gap-1.5 text-xs font-medium text-muted-foreground">
        <IconRoute className="size-3.5" />
        {t("aiAgent.systemPrompt.preview.simulatedValues", {
          defaultValue: "Simulated collected values",
        })}
      </div>
      <div className="space-y-1.5">
        {rows.map((row, idx) => (
          <div key={idx} className="flex items-center gap-2">
            <Input
              value={row.key}
              onChange={(e) =>
                push(rows.map((r, i) => (i === idx ? { ...r, key: e.target.value } : r)))
              }
              placeholder={t("aiAgent.systemPrompt.preview.slotName", { defaultValue: "slot name" })}
              className="h-8 w-40 text-xs"
            />
            <Input
              value={row.value}
              onChange={(e) =>
                push(rows.map((r, i) => (i === idx ? { ...r, value: e.target.value } : r)))
              }
              placeholder={t("aiAgent.systemPrompt.preview.slotValue", { defaultValue: "value" })}
              className="h-8 flex-1 text-xs"
            />
            <button
              type="button"
              onClick={() => push(rows.filter((_, i) => i !== idx))}
              className="rounded-md p-1 text-muted-foreground hover:bg-accent/50 hover:text-foreground"
              aria-label={t("forms.common.remove")}
            >
              <IconX className="size-4" />
            </button>
          </div>
        ))}
      </div>
      <button
        type="button"
        onClick={() => push([...rows, { key: "", value: "" }])}
        className="mt-2 inline-flex items-center gap-1 rounded-md border px-2 py-1 text-xs text-muted-foreground hover:bg-accent/50 hover:text-foreground"
      >
        <IconPlus className="size-3.5" />
        {t("aiAgent.systemPrompt.preview.addValue", { defaultValue: "Add value" })}
      </button>
    </div>
  );
}

/**
 * T608 — per-turn prompt metrics strip. Reads the token counts the T613/T614
 * pipeline emits (no re-parsing of the assembled string): the grand total, the
 * STABLE cacheable-prefix vs PER_TURN split (the budget T614's ordering can
 * cache across turns), and — when the resolved LLM instance has a price — a
 * best-effort per-turn cost. All numbers are approximate (chars/4) by design.
 */
function MetricsBar({ preview }: Readonly<{ preview: TurSystemPromptPreview }>) {
  const { t } = useTranslation();
  const perTurn = Math.max(0, preview.totalTokens - preview.stableTokens);
  const stableShare =
    preview.totalTokens > 0 ? Math.round((preview.stableTokens / preview.totalTokens) * 100) : 0;
  return (
    <div className="mb-6 flex flex-wrap items-center gap-x-5 gap-y-2 rounded-lg border bg-muted/30 px-3 py-2 text-xs">
      <span className="inline-flex items-center gap-1.5 font-medium text-foreground">
        <IconDatabase className="size-3.5 text-muted-foreground" />
        {t("aiAgent.systemPrompt.preview.totalTokens", {
          defaultValue: "{{count}} tokens total",
          count: preview.totalTokens,
        })}
      </span>
      <span className="inline-flex items-center gap-1.5 text-muted-foreground">
        <span className="size-2 rounded-full bg-emerald-500" />
        {t("aiAgent.systemPrompt.preview.stableTokens", {
          defaultValue: "{{count}} cacheable prefix ({{share}}%)",
          count: preview.stableTokens,
          share: stableShare,
        })}
      </span>
      <span className="inline-flex items-center gap-1.5 text-muted-foreground">
        <span className="size-2 rounded-full bg-amber-500" />
        {t("aiAgent.systemPrompt.preview.perTurnTokens", {
          defaultValue: "{{count}} per turn",
          count: perTurn,
        })}
      </span>
      {preview.estimatedCostUsd > 0 && (
        <span className="inline-flex items-center gap-1.5 text-muted-foreground">
          <IconCoin className="size-3.5 text-muted-foreground" />
          {t("aiAgent.systemPrompt.preview.estimatedCost", {
            defaultValue: "≈ {{cost}}/turn",
            cost: formatCost(preview.estimatedCostUsd),
          })}
          {preview.costModelName && (
            <span className="text-muted-foreground/70">({preview.costModelName})</span>
          )}
        </span>
      )}
      <span className="ml-auto hidden text-muted-foreground/70 sm:inline">
        {t("aiAgent.systemPrompt.preview.tokensApprox", { defaultValue: "approximate" })}
      </span>
    </div>
  );
}

/** Formats a small USD cost with enough precision to stay non-zero when tiny. */
function formatCost(cost: number): string {
  if (cost >= 0.01) {
    return `$${cost.toFixed(4)}`;
  }
  if (cost > 0) {
    // Sub-cent — keep the first two significant figures (e.g. $0.00032).
    return `$${cost.toPrecision(2)}`;
  }
  return "$0";
}

/**
 * Prompt segments are authored one instruction per line, but CommonMark
 * collapses a single newline into a space — so a runtime addendum like the
 * chat-flow step (a `## ACTIVE FLOW STEP` heading, then `Goal:` / `Collect:` /
 * `Next step preview:` / the question, each on its own line) would otherwise
 * render as one mashed-together paragraph.
 *
 * Convert soft breaks into Markdown hard breaks (two trailing spaces) so the
 * rendered article keeps the author's line structure. Fenced code blocks are
 * left byte-for-byte intact so their contents still render verbatim, and
 * blank-line paragraph breaks are untouched. The text is shown as-is because
 * this preview exists to show exactly what the model receives.
 */
function preserveSoftBreaks(markdown: string): string {
  // Split on fenced code blocks; odd-indexed parts are the fences themselves.
  const parts = markdown.replace(/\r\n/g, "\n").split(/(```[\s\S]*?```)/g);
  return parts
    .map((part, index) =>
      index % 2 === 1
        ? part
        // Add a hard break to any line followed by a *single* newline.
        : part.replace(/([^\n])\n(?!\n)/g, "$1  \n"),
    )
    .join("");
}

/** Appends the tool definitions as a Markdown section to the copied prompt. */
function buildToolsMarkdown(
  tools: TurSystemPromptTool[],
  heading: string,
  note: string,
): string {
  if (!tools || tools.length === 0) {
    return "";
  }
  const lines = tools.map(
    (tool) => `- **${tool.name}** _(${tool.source})_${tool.description ? `: ${tool.description}` : ""}`,
  );
  return `\n\n---\n\n## ${heading}\n\n_${note}_\n\n${lines.join("\n")}`;
}

/**
 * One passage of the flowing article. Renders the segment's Markdown inline
 * (no inner scroll box) and reveals its provenance on hover: an origin tint, a
 * brightening left accent, and a floating label naming the part.
 */
function SegmentPassage({
  segment,
  totalTokens,
  label,
  runtimeLabel,
  emptyLabel,
  tokLabel,
}: Readonly<{
  segment: TurSystemPromptSegment;
  totalTokens: number;
  label: string;
  runtimeLabel: string;
  emptyLabel: string;
  tokLabel: string;
}>) {
  const { t } = useTranslation();
  const style = ORIGIN_STYLE[segment.origin];
  // Per-segment token accounting (T608): count + share-of-total, shown in the
  // provenance label. Skipped for informational/runtime-only segments (0 tokens).
  const share =
    segment.tokens > 0 && totalTokens > 0
      ? Math.round((segment.tokens / totalTokens) * 100)
      : null;
  return (
    <section
      className={cn(
        "group relative rounded-xl px-5 py-2 transition-colors duration-200",
        style.hover,
        !segment.included && "opacity-60",
      )}
    >
      {/* Persistent (faint) → hover (solid) origin accent bar */}
      <span
        className={cn(
          "absolute left-0 top-2 bottom-2 w-1 rounded-full opacity-25 transition-opacity duration-200 group-hover:opacity-100",
          style.bar,
        )}
      />

      {/* Floating provenance label — fades in on hover, names the part */}
      <div className="pointer-events-none absolute -top-2.5 left-4 z-20 flex -translate-y-1 items-center gap-1.5 opacity-0 transition-all duration-200 group-hover:translate-y-0 group-hover:opacity-100">
        <Badge
          variant="outline"
          className={cn("bg-background text-[10px] uppercase shadow-sm", style.badge)}
        >
          {label}
        </Badge>
        {segment.title && (
          <span className="rounded-md border bg-background px-1.5 py-0.5 text-[10px] font-medium text-foreground shadow-sm">
            {segment.title}
          </span>
        )}
        {segment.runtimeOnly && (
          <span className="inline-flex items-center gap-1 rounded-full border border-amber-500/30 bg-background px-2 py-0.5 text-[10px] font-medium text-amber-600 shadow-sm dark:text-amber-300">
            <IconClock className="size-3" />
            {runtimeLabel}
          </span>
        )}
        {share !== null && (
          <span className="inline-flex items-center gap-1 rounded-md border bg-background px-1.5 py-0.5 text-[10px] font-medium text-muted-foreground shadow-sm">
            {segment.tokens} {tokLabel} · {share}%
          </span>
        )}
      </div>

      {segment.content ? (
        <SegmentBody segment={segment} />
      ) : (
        <p className="text-sm italic text-muted-foreground/70">{emptyLabel}</p>
      )}

      {segment.note && <p className="mt-2 text-xs text-muted-foreground">{t(segment.note, { defaultValue: segment.note })}</p>}
    </section>
  );
}

const PROSE_CLASSES =
  "prose prose-sm max-w-none dark:prose-invert prose-headings:font-semibold prose-headings:tracking-tight prose-pre:border prose-pre:bg-muted/50 prose-pre:text-foreground prose-code:text-foreground sm:prose-base";

/**
 * Renders a segment's markdown body, dimming the T609 "inert this turn" regions
 * (welcome / first-interaction / routing scaffolding a flow neutralizes) and
 * tagging each with its token cost. When no regions are inert it renders the
 * body as a single markdown block (the common case).
 */
function SegmentBody({ segment }: Readonly<{ segment: TurSystemPromptSegment }>) {
  const { t } = useTranslation();
  const slices = useMemo(
    () => sliceInert(segment.content, segment.inertRegions ?? []),
    [segment.content, segment.inertRegions],
  );

  if (slices.length === 1 && !slices[0].inert) {
    return (
      <div className={PROSE_CLASSES}>
        <ReactMarkdown remarkPlugins={[remarkGfm]} rehypePlugins={[rehypeHighlight]}>
          {preserveSoftBreaks(segment.content)}
        </ReactMarkdown>
      </div>
    );
  }

  const inertLabel = t("aiAgent.systemPrompt.preview.inertThisTurn", {
    defaultValue: "inert this turn",
  });
  const tokLabel = t("aiAgent.systemPrompt.preview.tokens", { defaultValue: "tok" });

  return (
    <div className="space-y-1">
      {slices.map((slice, idx) =>
        slice.inert ? (
          <div
            key={idx}
            className="relative rounded-md border border-dashed border-muted-foreground/30 bg-muted/20 px-3 py-1.5 opacity-50"
            title={inertLabel}
          >
            <div className="mb-1 flex items-center gap-1.5">
              <span className="rounded bg-muted px-1.5 py-0.5 text-[10px] font-medium uppercase tracking-wide text-muted-foreground line-through decoration-muted-foreground/50">
                {inertLabel}
              </span>
              <span className="text-[10px] text-muted-foreground">
                ~{slice.tokens} {tokLabel}
              </span>
            </div>
            <div className={PROSE_CLASSES}>
              <ReactMarkdown remarkPlugins={[remarkGfm]} rehypePlugins={[rehypeHighlight]}>
                {preserveSoftBreaks(slice.text)}
              </ReactMarkdown>
            </div>
          </div>
        ) : (
          <div key={idx} className={PROSE_CLASSES}>
            <ReactMarkdown remarkPlugins={[remarkGfm]} rehypePlugins={[rehypeHighlight]}>
              {preserveSoftBreaks(slice.text)}
            </ReactMarkdown>
          </div>
        ),
      )}
    </div>
  );
}

interface InertSlice {
  text: string;
  inert: boolean;
  tokens: number;
}

/**
 * Splits a segment's content into ordered live / inert slices by the region
 * offsets, clamped and de-overlapped so the slices always tile the content.
 * Regions are heading-delimited sections, so each inert slice is self-contained
 * markdown (a heading + its body) and renders cleanly on its own.
 */
function sliceInert(content: string, regions: TurSystemPromptInertRegion[]): InertSlice[] {
  if (!regions.length) {
    return [{ text: content, inert: false, tokens: 0 }];
  }
  const sorted = [...regions].sort((a, b) => a.start - b.start);
  const slices: InertSlice[] = [];
  let cursor = 0;
  for (const r of sorted) {
    const start = Math.max(cursor, Math.min(r.start, content.length));
    const end = Math.max(start, Math.min(r.end, content.length));
    if (start > cursor) {
      slices.push({ text: content.slice(cursor, start), inert: false, tokens: 0 });
    }
    if (end > start) {
      slices.push({ text: content.slice(start, end), inert: true, tokens: r.tokens });
    }
    cursor = end;
  }
  if (cursor < content.length) {
    slices.push({ text: content.slice(cursor), inert: false, tokens: 0 });
  }
  return slices.filter((s) => s.text.length > 0);
}
