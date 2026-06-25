"use client"
import { Badge } from "@/components/ui/badge";
import { SectionCard } from "@/components/ui/section-card";
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
import { IconClipboard, IconClock, IconEye, IconSitemap, IconTool } from "@tabler/icons-react";
import { toast } from "@viglet/viglet-design-system";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";

/**
 * "Live Preview" breakdown — each fragment of the assembled system prompt with
 * a color-coded origin badge, plus a chat-flow selector so the operator can
 * see how the prompt changes per flow. Also lists the tool/function schemas the
 * model receives alongside the prompt and offers a copy-the-full-prompt button
 * (the verbatim "what the model sees", tools included).
 *
 * @since 2026.3.1
 */

interface Props {
  readonly preview: TurSystemPromptPreview | undefined;
  readonly isLoading: boolean;
  /** Current flow-selector state (undefined → backend default). */
  readonly flowId: string | undefined;
  readonly onFlowChange: (value: string) => void;
}

/** Tailwind accent classes per origin — left border + badge tint. */
const ORIGIN_STYLE: Record<TurSystemPromptOrigin, { border: string; badge: string }> = {
  PERSONA: { border: "border-l-violet-500", badge: "bg-violet-500/10 text-violet-600 border-violet-500/20 dark:text-violet-300" },
  AGENT: { border: "border-l-blue-500", badge: "bg-blue-500/10 text-blue-600 border-blue-500/20 dark:text-blue-300" },
  MCP: { border: "border-l-cyan-500", badge: "bg-cyan-500/10 text-cyan-600 border-cyan-500/20 dark:text-cyan-300" },
  FLOW: { border: "border-l-amber-500", badge: "bg-amber-500/10 text-amber-600 border-amber-500/20 dark:text-amber-300" },
  FEW_SHOT: { border: "border-l-slate-400", badge: "bg-slate-500/10 text-slate-600 border-slate-500/20 dark:text-slate-300" },
  RAG: { border: "border-l-emerald-500", badge: "bg-emerald-500/10 text-emerald-600 border-emerald-500/20 dark:text-emerald-300" },
};

export function SystemPromptLivePreview({ preview, isLoading, flowId, onFlowChange }: Props) {
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
    <SectionCard variant="blue">
      <SectionCard.Header
        icon={IconEye}
        title={t("aiAgent.systemPrompt.preview.title", { defaultValue: "Live Preview" })}
        description={t("aiAgent.systemPrompt.preview.description", {
          defaultValue:
            "Everything below is concatenated into one system message before it reaches the model. Each block is tagged with where it comes from.",
        })}
      />
      <SectionCard.Content>
        <div className="flex flex-wrap items-center justify-between gap-2 mb-4">
          {/* Chat-flow selector */}
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

        {isLoading && (
          <p className="text-sm text-muted-foreground">{t("common.loading", { defaultValue: "Loading…" })}</p>
        )}

        {!isLoading && preview && (
          <ol className="space-y-3">
            {preview.segments.map((segment, idx) => (
              <SegmentRow
                key={`${segment.origin}-${idx}`}
                segment={segment}
                label={originLabel(segment.origin)}
                runtimeLabel={t("aiAgent.systemPrompt.preview.runtimeOnly", { defaultValue: "runtime only" })}
                emptyLabel={t("aiAgent.systemPrompt.preview.noFixedText", {
                  defaultValue: "No fixed text — depends on the live conversation.",
                })}
              />
            ))}
          </ol>
        )}

        {/* Tool definitions the model receives alongside the prompt. Not part
            of the literal system text, so they render as their own block below
            the segments (mirrors what the model is actually handed). */}
        {!isLoading && preview && preview.tools.length > 0 && (
          <div className="mt-4 rounded-md border border-l-4 border-l-cyan-500 bg-background/60 p-3 space-y-2">
            <div className="flex items-center gap-2">
              <IconTool className="size-4 text-cyan-600 dark:text-cyan-300" />
              <span className="text-xs font-medium text-foreground">{toolsHeading}</span>
            </div>
            <p className="text-xs text-muted-foreground">{toolsNote}</p>
            <ul className="space-y-1">
              {preview.tools.map((tool, idx) => (
                <li key={`${tool.name}-${idx}`} className="text-xs">
                  <code className="font-mono text-foreground">{tool.name}</code>
                  <span className="text-muted-foreground"> ({tool.source})</span>
                  {tool.description && (
                    <span className="text-muted-foreground">: {tool.description}</span>
                  )}
                </li>
              ))}
            </ul>
          </div>
        )}
      </SectionCard.Content>
    </SectionCard>
  );
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

function SegmentRow({
  segment,
  label,
  runtimeLabel,
  emptyLabel,
}: Readonly<{
  segment: TurSystemPromptSegment;
  label: string;
  runtimeLabel: string;
  emptyLabel: string;
}>) {
  const style = ORIGIN_STYLE[segment.origin];
  return (
    <li
      className={cn(
        "rounded-md border border-l-4 bg-background/60 p-3 space-y-2",
        style.border,
        !segment.included && "opacity-70",
      )}
    >
      <div className="flex flex-wrap items-center gap-2">
        <Badge variant="outline" className={cn("text-[10px] uppercase", style.badge)}>
          {label}
        </Badge>
        {segment.title && <span className="text-xs font-medium text-foreground">{segment.title}</span>}
        {segment.runtimeOnly && (
          <span className="inline-flex items-center gap-1 rounded-full border border-amber-500/30 bg-amber-500/5 px-2 py-0.5 text-[10px] font-medium text-amber-600 dark:text-amber-300">
            <IconClock className="size-3" />
            {runtimeLabel}
          </span>
        )}
      </div>
      {segment.note && <p className="text-xs text-muted-foreground">{segment.note}</p>}
      {segment.content ? (
        <pre className="max-h-60 overflow-auto rounded bg-muted/40 p-2 text-xs leading-relaxed whitespace-pre-wrap wrap-break-word font-mono">
          {segment.content}
        </pre>
      ) : (
        <p className="text-xs italic text-muted-foreground/70">{emptyLabel}</p>
      )}
    </li>
  );
}
