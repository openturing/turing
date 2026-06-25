"use client"
import {
  useDeepCheckSystemPrompt,
  useValidateSystemPrompt,
} from "@/api/queries/system-prompt.queries";
import { Badge } from "@/components/ui/badge";
import { SectionCard } from "@/components/ui/section-card";
import type { TurSystemPromptIssue } from "@/models/agent/system-prompt.model";
import {
  IconAlertOctagon,
  IconLoader2,
  IconShieldCheck,
  IconSparkles,
} from "@tabler/icons-react";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { GradientButton } from "../ui/gradient-button";

/**
 * System-prompt "authoring warnings" panel — the chat-flow lint pattern adapted
 * for an AI Agent's prompt. Heuristic findings load automatically; the deep
 * LLM audit is on demand via the button. Findings from both engines render in
 * one list, sorted ERROR → WARNING → INFO.
 *
 * @since 2026.3.1
 */

interface Props {
  readonly agentId: string;
}

const SEVERITY_RANK: Record<TurSystemPromptIssue["severity"], number> = {
  ERROR: 0,
  WARNING: 1,
  INFO: 2,
};

export function SystemPromptWarningsPanel({ agentId }: Props) {
  const { t } = useTranslation();
  const { data: heuristic = [], isLoading } = useValidateSystemPrompt(agentId);
  const deepMutation = useDeepCheckSystemPrompt();

  const issues = useMemo(() => {
    const deep = deepMutation.data ?? [];
    return [...heuristic, ...deep].sort(
      (a, b) => SEVERITY_RANK[a.severity] - SEVERITY_RANK[b.severity],
    );
  }, [heuristic, deepMutation.data]);

  const hasError = issues.some((i) => i.severity === "ERROR");
  const hasWarning = issues.some((i) => i.severity === "WARNING");
  const variant: "rose" | "amber" | "emerald" = hasError ? "rose" : hasWarning ? "amber" : "emerald";
  const clean = issues.length === 0 && !isLoading;

  return (
    <SectionCard variant={variant}>
      <SectionCard.Header
        icon={clean ? IconShieldCheck : IconAlertOctagon}
        title={t("aiAgent.systemPrompt.warnings.title", { defaultValue: "Conflict check" })}
        description={t("aiAgent.systemPrompt.warnings.description", {
          defaultValue:
            "Heuristic checks run automatically. Run the deep check to have the LLM look for subtler contradictions that could break a reply.",
        })}
      />
      <SectionCard.Content>
        <div className="flex flex-wrap items-center justify-between gap-2 mb-3">
          <span className="text-xs text-muted-foreground">
            {isLoading
              ? t("common.loading", { defaultValue: "Loading…" })
              : t("aiAgent.systemPrompt.warnings.count", {
                  count: issues.length,
                  defaultValue: "{{count}} issue(s) found",
                })}
          </span>
          <GradientButton
            type="button"
            variant="outline"
            size="sm"
            onClick={() => deepMutation.mutate(agentId)}
            disabled={deepMutation.isPending}
          >
            {deepMutation.isPending ? (
              <IconLoader2 className="size-4 animate-spin" />
            ) : (
              <IconSparkles className="size-4" />
            )}
            {t("aiAgent.systemPrompt.warnings.deepCheck", { defaultValue: "Deep check (LLM)" })}
          </GradientButton>
        </div>

        {clean && (
          <div className="flex items-center gap-2 rounded-md border border-emerald-500/30 bg-emerald-500/5 p-3 text-sm text-emerald-700 dark:text-emerald-300">
            <IconShieldCheck className="size-4 shrink-0" />
            {t("aiAgent.systemPrompt.warnings.clean", {
              defaultValue: "No conflicts detected. Run the deep check for a thorough LLM review.",
            })}
          </div>
        )}

        {issues.length > 0 && (
          <ul className="space-y-3">
            {issues.map((issue, idx) => (
              <IssueRow key={`${issue.code}-${issue.source}-${idx}`} issue={issue} t={t} />
            ))}
          </ul>
        )}
      </SectionCard.Content>
    </SectionCard>
  );
}

/**
 * Translates an issue by its `code`, feeding the backend's interpolation
 * params. If the result still carries an un-interpolated `{{placeholder}}`
 * (e.g. an older backend that doesn't send `params` yet), fall back to the
 * backend's own message — which already embeds the real values — so the user
 * never sees a raw `{{length}}` token.
 */
function resolveIssueText(
  t: (key: string, opts?: Record<string, unknown>) => string,
  key: string,
  params: Record<string, string> | undefined,
  fallback: string,
): string {
  const out = t(key, { ...params, defaultValue: fallback });
  return /\{\{.+?\}\}/.test(out) ? fallback : out;
}

function IssueRow({
  issue,
  t,
}: Readonly<{
  issue: TurSystemPromptIssue;
  t: (key: string, opts?: Record<string, unknown>) => string;
}>) {
  const isError = issue.severity === "ERROR";
  const isInfo = issue.severity === "INFO";
  // Prefer a code-keyed translation (with the backend's interpolation params);
  // fall back to the backend message/hint. Deep-LLM findings are already in
  // the prompt's language and have no translation key, so they fall through.
  const message = resolveIssueText(
    t,
    `aiAgent.systemPrompt.warnings.codes.${issue.code}.message`,
    issue.params,
    issue.message,
  );
  const hint = issue.hint
    ? resolveIssueText(
        t,
        `aiAgent.systemPrompt.warnings.codes.${issue.code}.hint`,
        issue.params,
        issue.hint,
      )
    : "";
  return (
    <li className="rounded-md border border-border/60 bg-background/60 p-3 space-y-2">
      <div className="flex flex-wrap items-center gap-2">
        <Badge
          variant={isError ? "destructive" : isInfo ? "outline" : "secondary"}
          className="text-[10px] uppercase"
        >
          {issue.severity}
        </Badge>
        <Badge variant="outline" className="font-mono text-[10px]">
          {issue.code}
        </Badge>
        {issue.source && (
          <span className="text-xs text-muted-foreground">
            <code className="font-mono text-foreground">{issue.source}</code>
          </span>
        )}
      </div>
      <div className="text-sm whitespace-pre-wrap break-words">{message}</div>
      {hint && <p className="text-xs text-muted-foreground">{hint}</p>}
    </li>
  );
}
