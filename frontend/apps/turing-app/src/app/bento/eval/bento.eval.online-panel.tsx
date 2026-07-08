import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  IconActivityHeartbeat,
  IconAlertTriangle,
  IconChartLine,
  IconFlag,
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
import {
  useOnlineEvalHistory,
  usePromoteOnlineBaseline,
  useRunOnlineEval,
} from "@/api/queries/eval-studio.queries";
import type { TurOnlineEvalSnapshot } from "@/models/eval/eval-studio.model";

/**
 * T603 / §XXXIII.18 — the Eval Studio's continuous / online-eval surface. Pick
 * an agent to see the drift timeline over its recent online-eval snapshots (each
 * a window of graded live traffic), the snapshot list with drift / baseline
 * badges, and the composed signal rates (T87 sentiment, T155 citation drift,
 * T447 failing sessions). "Run now" samples + grades live traffic on demand;
 * "Set baseline" re-establishes the healthy yardstick after a fix. Fail-open: an
 * agent with no snapshots — or a store that returned nothing — shows a hint.
 *
 * @since 2026.3.4
 */
export function BentoEvalOnlinePanel() {
  const { t } = useTranslation();
  const { data: agents } = useAiAgents();
  const [agentId, setAgentId] = useState<string>("");

  const { data: snapshots, isError } = useOnlineEvalHistory(agentId || undefined);
  const run = useRunOnlineEval(agentId || undefined);
  const promote = usePromoteOnlineBaseline(agentId || undefined);

  // Timeline reads oldest → newest so drift moves forward in time. Only graded
  // snapshots carry a mean score; signal-only snapshots plot the (inverted)
  // failing-session rate so the line still tells a quality story.
  const timeline = useMemo(
    () =>
      [...(snapshots ?? [])]
        .filter((s) => Boolean(s.id))
        .reverse()
        .map((s, index) => ({
          index: index + 1,
          quality:
            s.meanScore >= 0
              ? Math.round(s.meanScore * 100)
              : Math.round((1 - s.failingSessionRate) * 100),
          label: fmtDate(s.createdAt),
        })),
    [snapshots],
  );

  return (
    <div className="space-y-4">
      <div className="bento-tile bento-glass flex flex-wrap items-center gap-3 rounded-2xl border border-border/60 p-4">
        <IconRobot size={18} className="text-muted-foreground" />
        <label htmlFor="online-agent" className="text-sm text-muted-foreground">
          {t("evalStudio.review.agent", { defaultValue: "Agent" })}
        </label>
        <select
          id="online-agent"
          value={agentId}
          onChange={(event) => setAgentId(event.target.value)}
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
            onClick={() => run.mutate()}
            className="ml-auto inline-flex items-center gap-1.5 rounded-lg bg-gradient-to-br from-amber-500 to-rose-500 px-3.5 py-1.5 text-sm font-medium text-white shadow disabled:opacity-50"
          >
            <IconPlayerPlay size={16} />
            {run.isPending
              ? t("evalStudio.online.running", { defaultValue: "Sampling…" })
              : t("evalStudio.online.run", { defaultValue: "Run now" })}
          </button>
        )}
      </div>

      {run.data?.note && (
        <p className="text-sm text-amber-700 dark:text-amber-300">
          {t("evalStudio.online.unavailable", {
            defaultValue: "Nothing graded: {{note}}",
            note: run.data.note,
          })}
        </p>
      )}

      {isError && (
        <p className="text-sm text-rose-600 dark:text-rose-400">
          {t("common.connectionError", { resource: "online eval" })}
        </p>
      )}

      {agentId && !isError && (snapshots?.length ?? 0) === 0 && (
        <div className="bento-tile bento-glass rounded-2xl border border-border/60 p-8 text-center">
          <IconActivityHeartbeat size={28} className="mx-auto mb-3 text-muted-foreground" />
          <p className="font-medium">
            {t("evalStudio.online.empty", { defaultValue: "No online-eval snapshots yet" })}
          </p>
          <p className="mt-1 text-sm text-muted-foreground">
            {t("evalStudio.online.emptyHint", {
              defaultValue:
                "Enable online eval on the agent and run now (or wait for the nightly sweep) to sample live traffic. The first snapshot becomes the healthy baseline; later windows are checked for drift against it.",
            })}
          </p>
        </div>
      )}

      {agentId && (snapshots?.length ?? 0) > 0 && (
        <>
          {timeline.length > 1 && (
            <div className="bento-tile bento-glass rounded-2xl border border-border/60 p-4">
              <div className="mb-3 flex items-center gap-2">
                <IconChartLine size={18} className="text-muted-foreground" />
                <h3 className="text-sm font-semibold">
                  {t("evalStudio.online.timeline", { defaultValue: "Quality timeline" })}
                </h3>
              </div>
              <div className="h-56 w-full">
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart data={timeline} margin={{ top: 8, right: 12, bottom: 4, left: -16 }}>
                    <CartesianGrid strokeDasharray="3 3" className="stroke-border/50" />
                    <XAxis dataKey="index" tick={{ fontSize: 11 }} stroke="currentColor" />
                    <YAxis domain={[0, 100]} tick={{ fontSize: 11 }} stroke="currentColor" />
                    <Tooltip
                      formatter={(value) => [`${value}%`, "quality"]}
                      labelFormatter={(_l, payload) =>
                        (payload?.[0]?.payload as { label?: string })?.label ?? ""
                      }
                    />
                    <Line
                      type="monotone"
                      dataKey="quality"
                      stroke="#f43f5e"
                      strokeWidth={2}
                      dot={{ r: 3 }}
                    />
                  </LineChart>
                </ResponsiveContainer>
              </div>
            </div>
          )}

          <ul className="space-y-3">
            {(snapshots ?? [])
              .filter((s) => Boolean(s.id))
              .map((snapshot) => (
                <li key={snapshot.id}>
                  <SnapshotRow
                    snapshot={snapshot}
                    onSetBaseline={() => promote.mutate(snapshot.id as string)}
                    promoting={promote.isPending}
                  />
                </li>
              ))}
          </ul>
        </>
      )}
    </div>
  );
}

function SnapshotRow({
  snapshot,
  onSetBaseline,
  promoting,
}: Readonly<{
  snapshot: TurOnlineEvalSnapshot;
  onSetBaseline: () => void;
  promoting: boolean;
}>) {
  const { t } = useTranslation();
  return (
    <div
      className={`bento-tile bento-glass rounded-2xl border p-4 ${
        snapshot.driftDetected
          ? "border-rose-500/50 bg-rose-500/5"
          : "border-border/60"
      }`}
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span className="text-sm font-medium">{fmtDate(snapshot.createdAt)}</span>
        <div className="flex items-center gap-1.5">
          {snapshot.baseline && (
            <span className="rounded-full bg-emerald-500/15 px-2 py-0.5 text-[11px] font-medium text-emerald-700 dark:text-emerald-300">
              {t("evalStudio.online.baseline", { defaultValue: "baseline" })}
            </span>
          )}
          {snapshot.driftDetected && (
            <span className="inline-flex items-center gap-1 rounded-full bg-rose-500/15 px-2 py-0.5 text-[11px] font-medium text-rose-700 dark:text-rose-300">
              <IconAlertTriangle size={11} />
              {t("evalStudio.online.drift", { defaultValue: "drift" })}
            </span>
          )}
          {!snapshot.baseline && (
            <button
              type="button"
              disabled={promoting}
              onClick={onSetBaseline}
              className="inline-flex items-center gap-1 rounded-full border border-border/60 bg-card/60 px-2 py-0.5 text-[11px] font-medium text-muted-foreground hover:text-foreground disabled:opacity-50"
            >
              <IconFlag size={11} />
              {t("evalStudio.online.setBaseline", { defaultValue: "Set baseline" })}
            </button>
          )}
        </div>
      </div>

      {snapshot.driftDetected && snapshot.driftReason && (
        <p className="mt-1.5 text-xs text-rose-600 dark:text-rose-400">{snapshot.driftReason}</p>
      )}

      <div className="mt-3 grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-6">
        <Metric
          label={t("evalStudio.online.sampled", { defaultValue: "Sampled" })}
          value={String(snapshot.sampledSessions)}
        />
        <Metric
          label={t("evalStudio.online.graded", { defaultValue: "Graded" })}
          value={String(snapshot.gradedSessions)}
        />
        <Metric
          label={t("evalStudio.online.meanScore", { defaultValue: "Mean score" })}
          value={pct(snapshot.meanScore)}
        />
        <Metric
          label={t("evalStudio.online.passRate", { defaultValue: "Pass rate" })}
          value={pct(snapshot.passRate)}
        />
        <Metric
          label={t("evalStudio.online.negativeSentiment", { defaultValue: "Neg. sentiment" })}
          value={pct(snapshot.negativeSentimentRate)}
          alarm={snapshot.negativeSentimentRate >= 0.4}
        />
        <Metric
          label={t("evalStudio.online.failing", { defaultValue: "Failing" })}
          value={pct(snapshot.failingSessionRate)}
          alarm={snapshot.failingSessionRate >= 0.4}
        />
      </div>
    </div>
  );
}

function Metric({
  label,
  value,
  alarm,
}: Readonly<{ label: string; value: string; alarm?: boolean }>) {
  return (
    <div className="rounded-xl border border-border/60 bg-card/60 p-2.5">
      <p className="text-[11px] text-muted-foreground">{label}</p>
      <p
        className={`mt-0.5 tabular-nums text-sm font-semibold ${
          alarm ? "text-rose-600 dark:text-rose-400" : ""
        }`}
      >
        {value}
      </p>
    </div>
  );
}

function pct(rate: number): string {
  if (rate < 0) return "—";
  return `${Math.round(rate * 100)}%`;
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
