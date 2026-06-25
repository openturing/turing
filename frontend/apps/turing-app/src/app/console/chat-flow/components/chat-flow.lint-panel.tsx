import { useChatFlowLint } from "@/api/queries/chat-flow.queries";
import { Badge } from "@/components/ui/badge";
import { SectionCard } from "@/components/ui/section-card";
import type { TurChatFlowLintIssue } from "@/models/agent/chat-flow.model";
import { IconAlertOctagon } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

interface ChatFlowLintPanelProps {
  agentId: string;
  flowId: string;
}

/**
 * T94 / §VII.11.d — sidebar warnings panel. Lists every issue
 * {@link TurChatFlowLinterService} found on the flow being edited.
 * Rendered with the {@code rose} variant when any ERROR is present
 * (something will actively break at runtime), {@code amber} when only
 * warnings, and not rendered at all when the flow is clean — quietly
 * disappearing keeps a healthy editor uncluttered.
 *
 * @since 2026.3.1
 */
export function ChatFlowLintPanel({
  agentId,
  flowId,
}: Readonly<ChatFlowLintPanelProps>) {
  const { t } = useTranslation();
  const { data: issues } = useChatFlowLint(agentId, flowId);

  if (!issues || issues.length === 0) {
    return null;
  }

  const hasError = issues.some((i) => i.severity === "ERROR");
  const variant: "rose" | "amber" = hasError ? "rose" : "amber";

  return (
    <SectionCard variant={variant}>
      <SectionCard.Header
        icon={IconAlertOctagon}
        title={t("chatFlow.lint.title", {
          defaultValue: "Authoring warnings",
        })}
        description={t("chatFlow.lint.description", {
          count: issues.length,
          defaultValue:
            "{{count}} issue(s) detected on this flow. Fix the ERROR ones before saving — the runtime will stall on them.",
        })}
      />
      <SectionCard.Content>
        <ul className="space-y-3">
          {issues.map((issue) => (
            <LintRow
              key={`${issue.code}-${issue.nodeId ?? ""}-${issue.edgeId ?? ""}`}
              issue={issue}
              t={t}
            />
          ))}
        </ul>
      </SectionCard.Content>
    </SectionCard>
  );
}

function LintRow({
  issue,
  t,
}: Readonly<{
  issue: TurChatFlowLintIssue;
  t: (key: string, opts?: Record<string, unknown>) => string;
}>) {
  const isError = issue.severity === "ERROR";
  // Prefer a code-keyed translation (with the backend's interpolation params);
  // fall back to the English message/hint the linter already produced.
  // `slotRef` renders the slot wrapped in {{ }} so a hint can teach the
  // interpolation syntax literally (i18next does not re-scan substituted values).
  const params: Record<string, string> = {
    ...issue.params,
    ...(issue.params?.slot ? { slotRef: `{{${issue.params.slot}}}` } : {}),
  };
  const message = t(`chatFlow.lint.codes.${issue.code}.message`, {
    ...params,
    defaultValue: issue.message,
  });
  const hint = t(`chatFlow.lint.codes.${issue.code}.hint`, {
    ...params,
    defaultValue: issue.hint,
  });
  return (
    <li className="rounded-md border border-border/60 bg-background/60 p-3 space-y-2">
      <div className="flex flex-wrap items-center gap-2">
        <Badge
          variant={isError ? "destructive" : "secondary"}
          className="text-[10px] uppercase"
        >
          {issue.severity}
        </Badge>
        <Badge variant="outline" className="font-mono text-[10px]">
          {issue.code}
        </Badge>
        {issue.nodeId && (
          <span className="text-xs text-muted-foreground">
            {t("chatFlow.lint.node", { defaultValue: "node" })}{" "}
            <code className="font-mono text-foreground">{issue.nodeId}</code>
          </span>
        )}
        {issue.edgeId && (
          <span className="text-xs text-muted-foreground">
            {t("chatFlow.lint.edge", { defaultValue: "edge" })}{" "}
            <code className="font-mono text-foreground">{issue.edgeId}</code>
          </span>
        )}
      </div>
      <div className="text-sm">{message}</div>
      <p className="text-xs text-muted-foreground">{hint}</p>
    </li>
  );
}
