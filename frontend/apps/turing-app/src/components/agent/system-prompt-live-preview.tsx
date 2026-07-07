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
  type TurSystemPromptOrigin,
  type TurSystemPromptPreview,
  type TurSystemPromptSegment,
  type TurSystemPromptTool,
} from "@/models/agent/system-prompt.model";
import { cn } from "@/lib/utils";
import { IconClipboard, IconClock, IconCoin, IconDatabase, IconSitemap, IconTool } from "@tabler/icons-react";
import { toast } from "@viglet/viglet-design-system";
import { useMemo } from "react";
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
  /**
   * Render the article's own masthead (H1 title + description lede). The console
   * page has no page header, so it needs one; the bento page already supplies a
   * hero with the same title AND uses this description as its subtitle, so it
   * passes `false` to avoid duplicating both. Defaults to true.
   */
  readonly showHeader?: boolean;
}

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

export function SystemPromptLivePreview({ preview, isLoading, flowId, onFlowChange, showHeader = true }: Props) {
  const { t } = useTranslation();

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

      {/* Control bar: flow selector + copy */}
      <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
        {preview && preview.flows.length > 0 ? (
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
        <div className="prose prose-sm max-w-none dark:prose-invert prose-headings:font-semibold prose-headings:tracking-tight prose-pre:border prose-pre:bg-muted/50 prose-pre:text-foreground prose-code:text-foreground sm:prose-base">
          <ReactMarkdown remarkPlugins={[remarkGfm]} rehypePlugins={[rehypeHighlight]}>
            {preserveSoftBreaks(segment.content)}
          </ReactMarkdown>
        </div>
      ) : (
        <p className="text-sm italic text-muted-foreground/70">{emptyLabel}</p>
      )}

      {segment.note && <p className="mt-2 text-xs text-muted-foreground">{segment.note}</p>}
    </section>
  );
}
