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
} from "@/models/agent/system-prompt.model";
import { cn } from "@/lib/utils";
import { IconClock, IconEye, IconSitemap } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

/**
 * "Live Preview" breakdown — each fragment of the assembled system prompt with
 * a color-coded origin badge, plus a chat-flow selector so the operator can
 * see how the prompt changes per flow. The verbatim final prompt lives in a
 * separate {@code SystemPromptFinalPrompt} card (rendered last on the page).
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
        {/* Chat-flow selector */}
        {preview && preview.flows.length > 0 && (
          <div className="flex flex-wrap items-center gap-2 mb-4">
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
        )}

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
      </SectionCard.Content>
    </SectionCard>
  );
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
