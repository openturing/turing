import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  IconAlertTriangle,
  IconChartLine,
  IconCircleCheck,
  IconCircleX,
  IconClockPlay,
  IconHistory,
  IconPlayerPlay,
  IconRobot,
} from "@tabler/icons-react";
import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

import { useAiAgents } from "@/api/queries/ai-agent.queries";
import { useEvalRunHistory, useRunEval } from "@/api/queries/eval-studio.queries";
import type {
  TurAgentEvalCaseResult,
  TurAgentEvalReport,
} from "@/models/agent/agent-eval.model";

/**
 * T599 / §XXXIII.14 — the Eval Studio's run-history surface. Pick an agent to
 * see its score/regression timeline, the append-only list of eval runs (newest
 * first, with pass/fail + regression badges), and — drilling into any run — the
 * per-case breakdown (outcome, final node, slot diffs, rubric verdict). A
 * one-click "Run gate now" replays the enabled golden sets and refreshes the
 * timeline. Fail-open: an agent with no runs shows a hint.
 *
 * @since 2026.3.4
 */
export function BentoEvalRunHistory() {
  const { t } = useTranslation();
  const { data: agents } = useAiAgents();
  const [agentId, setAgentId] = useState<string>("");
  const [selectedReportId, setSelectedReportId] = useState<string | null>(null);

  const { data: reports, isError } = useEvalRunHistory(agentId || undefined);
  const run = useRunEval(agentId || undefined);

  const selected = useMemo(
    () => (reports ?? []).find((r) => r.reportId === selectedReportId) ?? null,
    [reports, selectedReportId],
  );

  // Timeline reads oldest → newest so the line moves forward in time.
  const timeline = useMemo(
    () =>
      [...(reports ?? [])]
        .reverse()
        .map((r, index) => ({
          index: index + 1,
          score: Math.round(r.score * 100),
          label: fmtDate(r.createdAt),
        })),
    [reports],
  );

  // Once an agent's runs load, default the drill-down to the newest run.
  useEffect(() => {
    if (reports && reports.length > 0 && selectedReportId === null) {
      setSelectedReportId(reports[0].reportId);
    }
  }, [reports, selectedReportId]);

  return (
    <div className="space-y-4">
      <div className="bento-tile bento-glass flex flex-wrap items-center gap-3 rounded-2xl border border-border/60 p-4">
        <IconRobot size={18} className="text-muted-foreground" />
        <label htmlFor="history-agent" className="text-sm text-muted-foreground">
          {t("evalStudio.review.agent", { defaultValue: "Agent" })}
        </label>
        <select
          id="history-agent"
          value={agentId}
          onChange={(event) => {
            setAgentId(event.target.value);
            setSelectedReportId(null);
          }}
          className="min-w-56 rounded-lg border border-border/60 bg-card/60 px-3 py-1.5 text-sm"
        >
          <option value="">
            {t("evalStudio.review.pickAgent", { defaultValue: "Select an agent…" })}
          </option>
          {(agents ?? []).map((agent) => (
            <option key={agent.id} value={agent.id}>
              {agent.title}
            </option>
          ))}
        </select>

        {agentId && (
          <button
            type="button"
            disabled={run.isPending}
            onClick={() =>
              run.mutate(undefined, {
                onSuccess: (report) => setSelectedReportId(report.reportId),
              })
            }
            className="ml-auto inline-flex items-center gap-1.5 rounded-lg bg-gradient-to-br from-amber-500 to-rose-500 px-3.5 py-1.5 text-sm font-medium text-white shadow disabled:opacity-50"
          >
            <IconPlayerPlay size={16} />
            {run.isPending
              ? t("evalStudio.history.running", { defaultValue: "Running…" })
              : t("evalStudio.history.run", { defaultValue: "Run gate now" })}
          </button>
        )}
      </div>

      {run.isError && (
        <p className="text-sm text-rose-600 dark:text-rose-400">
          {t("evalStudio.history.runError", {
            defaultValue: "Could not run the gate — the agent may have no enabled set or usable LLM.",
          })}
        </p>
      )}

      {isError && (
        <p className="text-sm text-rose-600 dark:text-rose-400">
          {t("common.connectionError", { resource: "eval runs" })}
        </p>
      )}

      {agentId && !isError && (reports?.length ?? 0) === 0 && (
        <div className="bento-tile bento-glass rounded-2xl border border-border/60 p-8 text-center">
          <IconHistory size={28} className="mx-auto mb-3 text-muted-foreground" />
          <p className="font-medium">
            {t("evalStudio.history.empty", { defaultValue: "No eval runs yet" })}
          </p>
          <p className="mt-1 text-sm text-muted-foreground">
            {t("evalStudio.history.emptyHint", {
              defaultValue:
                "Run the gate to establish a baseline — every run is kept here with its per-case breakdown and plotted on the score timeline.",
            })}
          </p>
        </div>
      )}

      {agentId && (reports?.length ?? 0) > 0 && (
        <>
          {timeline.length > 1 && (
            <div className="bento-tile bento-glass rounded-2xl border border-border/60 p-4">
              <div className="mb-3 flex items-center gap-2">
                <IconChartLine size={18} className="text-muted-foreground" />
                <h3 className="text-sm font-semibold">
                  {t("evalStudio.history.timeline", { defaultValue: "Score timeline" })}
                </h3>
              </div>
              <div className="h-56 w-full">
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart data={timeline} margin={{ top: 8, right: 12, bottom: 4, left: -16 }}>
                    <CartesianGrid strokeDasharray="3 3" className="stroke-border/50" />
                    <XAxis dataKey="index" tick={{ fontSize: 11 }} stroke="currentColor" />
                    <YAxis domain={[0, 100]} tick={{ fontSize: 11 }} stroke="currentColor" />
                    <Tooltip
                      formatter={(value) => [`${value}%`, "score"]}
                      labelFormatter={(_l, payload) =>
                        (payload?.[0]?.payload as { label?: string })?.label ?? ""
                      }
                    />
                    <Line
                      type="monotone"
                      dataKey="score"
                      stroke="#f43f5e"
                      strokeWidth={2}
                      dot={{ r: 3 }}
                    />
                  </LineChart>
                </ResponsiveContainer>
              </div>
            </div>
          )}

          <div className="grid grid-cols-1 gap-4 lg:grid-cols-[300px_1fr]">
            <ul className="space-y-2">
              {(reports ?? []).map((report) => (
                <li key={report.reportId ?? report.createdAt}>
                  <RunListItem
                    report={report}
                    active={report.reportId === selectedReportId}
                    onSelect={() => setSelectedReportId(report.reportId)}
                  />
                </li>
              ))}
            </ul>

            {selected ? (
              <RunDetail report={selected} />
            ) : (
              <div className="bento-tile bento-glass flex items-center justify-center rounded-2xl border border-border/60 p-8 text-sm text-muted-foreground">
                {t("evalStudio.history.selectHint", {
                  defaultValue: "Select a run to see its per-case breakdown.",
                })}
              </div>
            )}
          </div>
        </>
      )}
    </div>
  );
}

function RunListItem({
  report,
  active,
  onSelect,
}: Readonly<{ report: TurAgentEvalReport; active: boolean; onSelect: () => void }>) {
  const { t } = useTranslation();
  return (
    <button
      type="button"
      onClick={onSelect}
      className={`bento-tile bento-tile-clickable w-full rounded-xl border p-3 text-left transition-colors ${
        active ? "border-primary/40 bg-primary/10" : "border-border/60 bg-card/60 hover:bg-card"
      }`}
    >
      <div className="flex items-center justify-between gap-2">
        <span className="flex items-center gap-1.5 text-sm font-medium">
          <IconClockPlay size={14} className="text-muted-foreground" />
          {fmtDate(report.createdAt)}
        </span>
        <PassBadge passed={report.passed} pendingReview={report.pendingReview} />
      </div>
      <div className="mt-1.5 flex flex-wrap items-center gap-1.5 text-xs text-muted-foreground">
        <span className="tabular-nums font-medium">{Math.round(report.score * 100)}%</span>
        <span>·</span>
        <span>
          {t("evalStudio.history.caseCount", {
            defaultValue: "{{passed}}/{{total}} cases",
            passed: report.passedCount,
            total: report.caseCount,
          })}
        </span>
        {report.baseline && (
          <span className="rounded-full bg-emerald-500/15 px-2 py-0.5 text-[11px] font-medium text-emerald-700 dark:text-emerald-300">
            {t("evalStudio.history.baseline", { defaultValue: "baseline" })}
          </span>
        )}
        {report.regressed && (
          <span className="inline-flex items-center gap-1 rounded-full bg-rose-500/15 px-2 py-0.5 text-[11px] font-medium text-rose-700 dark:text-rose-300">
            <IconAlertTriangle size={11} />
            {t("evalStudio.history.regressed", { defaultValue: "regressed" })}
          </span>
        )}
      </div>
    </button>
  );
}

function RunDetail({ report }: Readonly<{ report: TurAgentEvalReport }>) {
  const { t } = useTranslation();

  if (report.error) {
    return (
      <div className="bento-tile bento-glass rounded-2xl border border-rose-500/40 bg-rose-500/5 p-5 text-sm text-rose-600 dark:text-rose-400">
        {report.error}
      </div>
    );
  }

  return (
    <div className="bento-tile bento-glass space-y-3 rounded-2xl border border-border/60 p-5">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <p className="font-medium">{fmtDate(report.createdAt)}</p>
          <p className="text-xs text-muted-foreground">
            {t("evalStudio.history.summary", {
              defaultValue: "{{passed}}/{{total}} cases passed · score {{score}}%",
              passed: report.passedCount,
              total: report.caseCount,
              score: Math.round(report.score * 100),
            })}
          </p>
        </div>
        <PassBadge passed={report.passed} pendingReview={report.pendingReview} />
      </div>

      <ul className="space-y-2 border-t border-border/60 pt-3">
        {(report.results ?? []).map((result) => (
          <li key={result.caseId}>
            <CaseResultRow result={result} />
          </li>
        ))}
        {(report.results ?? []).length === 0 && (
          <li className="text-sm text-muted-foreground">
            {t("evalStudio.history.noCases", { defaultValue: "This run recorded no cases." })}
          </li>
        )}
      </ul>
    </div>
  );
}

function CaseResultRow({ result }: Readonly<{ result: TurAgentEvalCaseResult }>) {
  const { t } = useTranslation();
  const failingSlots = (result.slotDiffs ?? []).filter((d) => !d.match);
  return (
    <div className="rounded-xl border border-border/60 bg-card/60 p-3">
      <div className="flex items-center justify-between gap-2">
        <span className="flex items-center gap-1.5 text-sm font-medium">
          {result.passed ? (
            <IconCircleCheck size={16} className="text-emerald-600 dark:text-emerald-400" />
          ) : (
            <IconCircleX size={16} className="text-rose-600 dark:text-rose-400" />
          )}
          {result.caseName}
        </span>
        <span className="tabular-nums text-xs text-muted-foreground">
          {Math.round(result.score * 100)}%
        </span>
      </div>

      {result.error && (
        <p className="mt-1.5 text-xs text-rose-600 dark:text-rose-400">{result.error}</p>
      )}

      {(result.expectedOutcome || result.actualOutcome) && (
        <p className="mt-1.5 text-xs text-muted-foreground">
          {t("evalStudio.history.outcome", { defaultValue: "Outcome" })}:{" "}
          <span className="font-medium">{result.actualOutcome ?? "—"}</span>
          {result.expectedOutcome && result.expectedOutcome !== result.actualOutcome && (
            <>
              {" "}
              ({t("evalStudio.history.expected", { defaultValue: "expected" })}{" "}
              {result.expectedOutcome})
            </>
          )}
        </p>
      )}

      {failingSlots.length > 0 && (
        <ul className="mt-1.5 space-y-0.5">
          {failingSlots.map((diff) => (
            <li key={diff.slot} className="text-xs text-muted-foreground">
              <span className="font-mono text-rose-600 dark:text-rose-400">{diff.slot}</span>:{" "}
              {t("evalStudio.history.slotDiff", {
                defaultValue: "expected “{{expected}}”, got “{{actual}}”",
                expected: diff.expected,
                actual: diff.actual ?? "—",
              })}
            </li>
          ))}
        </ul>
      )}

      {result.rubricVerdict === "fail" && result.rubricRationale && (
        <p className="mt-1.5 rounded-lg border border-border/60 bg-muted/40 p-2 text-xs text-muted-foreground">
          {t("evalStudio.history.rubric", { defaultValue: "Rubric" })}: {result.rubricRationale}
        </p>
      )}
    </div>
  );
}

function PassBadge({
  passed,
  pendingReview,
}: Readonly<{ passed: boolean; pendingReview?: boolean }>) {
  const { t } = useTranslation();
  if (pendingReview) {
    return (
      <span className="rounded-full bg-amber-500/15 px-2 py-0.5 text-[11px] font-medium text-amber-700 dark:text-amber-300">
        {t("evalStudio.history.pendingReview", { defaultValue: "pending review" })}
      </span>
    );
  }
  return passed ? (
    <span className="rounded-full bg-emerald-500/15 px-2 py-0.5 text-[11px] font-medium text-emerald-700 dark:text-emerald-300">
      {t("evalStudio.history.pass", { defaultValue: "pass" })}
    </span>
  ) : (
    <span className="rounded-full bg-rose-500/15 px-2 py-0.5 text-[11px] font-medium text-rose-700 dark:text-rose-300">
      {t("evalStudio.history.fail", { defaultValue: "fail" })}
    </span>
  );
}

function fmtDate(iso: string | null): string {
  if (!iso) return "—";
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  return date.toLocaleString(undefined, {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}
