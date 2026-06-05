import { useAgentEvalGate, useRunAgentEval } from "@/api/queries/agent-eval.queries";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { SectionCard } from "@/components/ui/section-card";
import type {
  TurAgentEvalGateFinding,
  TurAgentEvalGateStatus,
} from "@/models/agent/agent-eval.model";
import { IconShieldCheck } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

interface ChatFlowEvalGatePanelProps {
  agentId: string;
}

type Variant = "rose" | "amber" | "emerald";

const STATUS_VARIANT: Record<TurAgentEvalGateStatus, Variant> = {
  GREEN: "emerald",
  NEVER_RUN: "amber",
  RED: "rose",
  REGRESSED: "rose",
  NOT_CONFIGURED: "amber",
};

/**
 * T287 / §XV.3 — pre-publish eval gate, surfaced in the same editor sidebar
 * as the T94 lint panel. Shows the agent's latest golden-set run status and a
 * one-click "Run gate" action. Hidden entirely when no golden set is
 * configured (NOT_CONFIGURED) so the editor stays uncluttered for agents that
 * don't use Agent CI.
 *
 * @since 2026.3.1
 */
export function ChatFlowEvalGatePanel({
  agentId,
}: Readonly<ChatFlowEvalGatePanelProps>) {
  const { t } = useTranslation();
  const { data: gate } = useAgentEvalGate(agentId);
  const runMutation = useRunAgentEval();

  // Not configured (no enabled golden set) → render nothing.
  if (!gate || gate.status === "NOT_CONFIGURED") {
    return null;
  }

  const variant = STATUS_VARIANT[gate.status];
  const report = gate.lastReport;

  return (
    <SectionCard variant={variant}>
      <SectionCard.Header
        icon={IconShieldCheck}
        title={t("agentEval.gate.title", { defaultValue: "Eval gate" })}
        description={t("agentEval.gate.description", {
          defaultValue:
            "Replays this agent's golden set to prove a prompt/flow change didn't regress before you publish.",
        })}
      />
      <SectionCard.Content>
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div className="flex flex-wrap items-center gap-2">
            <Badge
              variant={
                gate.status === "GREEN"
                  ? "secondary"
                  : gate.status === "NEVER_RUN"
                    ? "outline"
                    : "destructive"
              }
              className="text-[10px] uppercase"
            >
              {gate.status}
            </Badge>
            {gate.blocking && (
              <Badge variant="outline" className="text-[10px] uppercase">
                {t("agentEval.gate.blocking", { defaultValue: "blocking" })}
              </Badge>
            )}
            {report && report.caseCount > 0 && (
              <span className="text-xs text-muted-foreground">
                {report.passedCount}/{report.caseCount}{" "}
                {t("agentEval.gate.passed", { defaultValue: "passed" })}
                {" · "}
                {Math.round(report.score * 100)}%
              </span>
            )}
          </div>
          <Button
            size="sm"
            variant="outline"
            disabled={runMutation.isPending}
            onClick={() => runMutation.mutate({ agentId })}
          >
            {runMutation.isPending
              ? t("agentEval.gate.running", { defaultValue: "Running…" })
              : t("agentEval.gate.run", { defaultValue: "Run gate" })}
          </Button>
        </div>

        {runMutation.data?.error && (
          <p className="mt-3 text-xs text-rose-600 dark:text-rose-400">
            {runMutation.data.error}
          </p>
        )}

        {gate.findings.length > 0 && (
          <ul className="mt-3 space-y-3">
            {gate.findings.map((finding, idx) => (
              <FindingRow key={`${finding.code}-${idx}`} finding={finding} />
            ))}
          </ul>
        )}
      </SectionCard.Content>
    </SectionCard>
  );
}

function FindingRow({
  finding,
}: Readonly<{ finding: TurAgentEvalGateFinding }>) {
  const isError = finding.severity === "ERROR";
  return (
    <li className="rounded-md border border-border/60 bg-background/60 p-3 space-y-2">
      <div className="flex flex-wrap items-center gap-2">
        <Badge
          variant={isError ? "destructive" : "secondary"}
          className="text-[10px] uppercase"
        >
          {finding.severity}
        </Badge>
        <Badge variant="outline" className="font-mono text-[10px]">
          {finding.code}
        </Badge>
      </div>
      <div className="text-sm">{finding.message}</div>
      <p className="text-xs text-muted-foreground">{finding.hint}</p>
    </li>
  );
}
