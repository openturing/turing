import {
  useChatAnalyticsHealth,
  useChatAnalyticsRouterDecisions,
  useChatAnalyticsScorecard,
  useChatAnalyticsSessions,
  useChatAnalyticsTimeseries,
  useChatAnalyticsToolLatency,
  useChatAnalyticsTranscript,
  useChatSlotSseChannels,
} from "@/api/queries/chat-analytics.queries";
import { useChatSlotAudit } from "@/api/queries/chat-session.queries";
import type { TurChatSlotAuditEntry } from "@/services/chat/chat-slot-audit.service";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import type {
  TurChatAnalyticsMetric,
  TurChatAnalyticsRouterDecision,
  TurChatAnalyticsRouterMethod,
  TurChatAnalyticsScorecardDimension,
  TurChatAnalyticsSession,
  TurChatAnalyticsToolLatencyRow,
  TurChatDeviceType,
  TurChatSlotSseChannel,
} from "@/models/chat-analytics/chat-analytics.model";
import {
  IconBroadcast,
  IconChartLine,
  IconFlask,
  IconGauge,
  IconMessageOff,
} from "@tabler/icons-react";
import { useState } from "react";
import {
  CartesianGrid,
  Line,
  LineChart,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

const NEUTRAL = "outline";
const SUCCESS = "default";
const DESTRUCTIVE = "destructive";
const SECONDARY = "secondary";

function formatTimestamp(value?: string | null): string {
  if (!value) return "—";
  try {
    return new Date(value).toLocaleString();
  } catch {
    return value;
  }
}

function formatDuration(ms?: number | null): string {
  if (!ms || ms <= 0) return "—";
  if (ms < 1000) return `${ms} ms`;
  const seconds = Math.round(ms / 1000);
  if (seconds < 60) return `${seconds} s`;
  const minutes = Math.floor(seconds / 60);
  const remSeconds = seconds % 60;
  return `${minutes}m ${remSeconds}s`;
}

function formatToolCalls(session: TurChatAnalyticsSession): string | number {
  const calls = session.totalToolCalls ?? 0;
  if (calls === 0) return 0;
  const errors = session.totalToolErrors ?? 0;
  return errors > 0 ? `${calls} (${errors} err)` : `${calls}`;
}

function outcomeVariant(outcome?: string | null): typeof NEUTRAL | typeof SUCCESS | typeof DESTRUCTIVE | typeof SECONDARY {
  switch (outcome) {
    case "COMPLETED":
      return SUCCESS;
    case "ABANDONED":
    case "ERROR":
      return DESTRUCTIVE;
    case "IN_PROGRESS":
      return SECONDARY;
    default:
      return NEUTRAL;
  }
}

function sentimentVariant(sentiment?: string | null) {
  switch (sentiment) {
    case "POSITIVE":
      return SUCCESS;
    case "NEGATIVE":
    case "FRUSTRATED":
      return DESTRUCTIVE;
    case "NEUTRAL":
      return NEUTRAL;
    default:
      return SECONDARY;
  }
}

function goalVariant(goal?: string | null) {
  switch (goal) {
    case "YES":
      return SUCCESS;
    case "NO":
      return DESTRUCTIVE;
    case "PARTIAL":
      return SECONDARY;
    default:
      return NEUTRAL;
  }
}

function MetaRow({ label, value }: Readonly<{ label: string; value?: string | number | null }>) {
  return (
    <div className="grid grid-cols-3 gap-2 text-sm">
      <span className="text-muted-foreground">{label}</span>
      <span className="col-span-2 font-mono break-all">{value ?? "—"}</span>
    </div>
  );
}

const ROUTER_METHOD_VARIANT: Record<TurChatAnalyticsRouterMethod, string> = {
  PROCEDURAL: SECONDARY,
  LLM: SUCCESS,
  LLM_CACHE: NEUTRAL,
  NONE: DESTRUCTIVE,
};

const ROUTER_METHOD_LABEL: Record<TurChatAnalyticsRouterMethod, string> = {
  PROCEDURAL: "Keyword",
  LLM: "LLM",
  LLM_CACHE: "LLM (cached)",
  NONE: "No match",
};

function formatScore(score: number | null): string {
  return score == null ? "—" : score.toFixed(2);
}

function RouterDecisionRow({ decision }: Readonly<{ decision: TurChatAnalyticsRouterDecision }>) {
  // Highest score sets the bar scale; guards a zero/absent best.
  const maxScore = decision.candidates.reduce(
    (max, c) => (c.score != null && c.score > max ? c.score : max),
    0,
  );
  return (
    <div className="border-border/60 rounded-md border p-3">
      <div className="flex flex-wrap items-center gap-2">
        <Badge variant={ROUTER_METHOD_VARIANT[decision.method] as never}>
          {ROUTER_METHOD_LABEL[decision.method]}
        </Badge>
        <span className="text-sm font-medium">
          {decision.winnerFlowName ?? decision.winnerFlowId ?? "no flow"}
        </span>
        {decision.procedural && decision.dominanceRatio != null && (
          <span className="text-muted-foreground text-xs">
            ratio {Number.isFinite(decision.dominanceRatio) ? decision.dominanceRatio.toFixed(2) : "∞"}×
          </span>
        )}
      </div>
      {decision.userMessage && (
        <p className="text-muted-foreground mt-1.5 line-clamp-2 text-xs italic">
          “{decision.userMessage}”
        </p>
      )}
      <div className="mt-2 space-y-1">
        {decision.candidates.map((c) => (
          <div key={c.flowId} className="flex items-center gap-2 text-xs">
            <span
              className={`w-40 shrink-0 truncate ${
                c.winner ? "text-foreground font-semibold" : "text-muted-foreground"
              }`}
              title={c.flowName ?? c.flowId}
            >
              {c.winner && "★ "}
              {c.flowName ?? c.flowId}
            </span>
            <div className="bg-muted relative h-2 flex-1 overflow-hidden rounded-full">
              <div
                className={c.winner ? "bg-primary h-full" : "bg-muted-foreground/50 h-full"}
                style={{
                  width: `${maxScore > 0 && c.score != null ? (c.score / maxScore) * 100 : 0}%`,
                }}
              />
            </div>
            <span className="w-12 shrink-0 text-right tabular-nums">{formatScore(c.score)}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

function RouterDecisionsCard({
  decisions,
}: Readonly<{ decisions: TurChatAnalyticsRouterDecision[] }>) {
  // Mirrors SentimentTrajectoryChart — renders nothing when there's no data
  // (clustered/ephemeral ring: a node restart or eviction empties it).
  if (decisions.length === 0) return null;
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Router decisions</CardTitle>
        <CardDescription>
          Which flow each turn activated, the keyword score every candidate earned, and the
          mechanism that picked the winner. In-memory and ephemeral — empty after a restart.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">
        {decisions.map((decision) => (
          <RouterDecisionRow
            key={`${decision.epochMillis}-${decision.winnerFlowId ?? "none"}`}
            decision={decision}
          />
        ))}
      </CardContent>
    </Card>
  );
}

interface DetailPanelProps {
  conversationId: string | null;
  onClose: () => void;
}

function DetailPanel({ conversationId, onClose }: Readonly<DetailPanelProps>) {
  const { data, isLoading, isError } = useChatAnalyticsTranscript(conversationId);
  // T86 — overlay the T60 slot audit trail so each transcript message
  // sits next to the slot writes that happened around that turn. Polling
  // is off for the detail panel — the conversation is historical by
  // definition and the open sheet shouldn't keep generating traffic.
  const { data: auditData } = useChatSlotAudit(conversationId, { refetchInterval: 0 });
  // T89 — the router decisions for this conversation (candidates + scores +
  // winner + method). The complaint-triage view: "why did it send me here?"
  const { data: routerDecisions } = useChatAnalyticsRouterDecisions(conversationId);

  return (
    <Sheet open={Boolean(conversationId)} onOpenChange={(open) => !open && onClose()}>
      <SheetContent side="right" className="w-full sm:max-w-3xl overflow-y-auto">
        <SheetHeader>
          <SheetTitle>Conversation detail</SheetTitle>
          <SheetDescription className="font-mono text-xs">{conversationId}</SheetDescription>
        </SheetHeader>

        {isLoading && (
          <p className="text-muted-foreground py-12 text-center text-sm">Loading transcript…</p>
        )}
        {isError && (
          <p className="text-destructive py-12 text-center text-sm">Failed to load transcript.</p>
        )}
        {!isLoading && !isError && !data && (
          <p className="text-muted-foreground py-12 text-center text-sm">
            Session not found in the analytics store (it may have been purged).
          </p>
        )}

        {data && (
          <div className="space-y-4 px-4 pb-6">
            <Card>
              <CardHeader>
                <CardTitle className="text-base">Session</CardTitle>
              </CardHeader>
              <CardContent className="space-y-1.5">
                <MetaRow label="Started"   value={formatTimestamp(data.session.startedAt)} />
                <MetaRow label="Completed" value={formatTimestamp(data.session.completedAt)} />
                <MetaRow label="Duration"  value={formatDuration(data.session.durationMs)} />
                <MetaRow label="Outcome"   value={data.session.outcome} />
                <MetaRow label="Agent"     value={data.session.agentTitle ?? data.session.agentId} />
                <MetaRow label="Persona"   value={data.session.personaName ?? data.session.personaId} />
                <MetaRow label="LLM"       value={data.session.llmInstanceId} />
                <MetaRow label="Locale"    value={data.session.locale} />
                <MetaRow label="Device"    value={data.session.deviceType} />
                <MetaRow label="Timezone"  value={data.session.timezone} />
                <MetaRow label="User"      value={data.session.userId} />
                <MetaRow label="Turns"     value={data.session.totalTurns ?? 0} />
                <MetaRow label="Tokens in"  value={data.session.totalTokensIn ?? 0} />
                <MetaRow label="Tokens out" value={data.session.totalTokensOut ?? 0} />
                <MetaRow label="Tool calls" value={formatToolCalls(data.session)} />
                <MetaRow
                  label="Tool latency"
                  value={formatDuration(data.session.totalToolLatencyMs)}
                />
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle className="text-base">AI enrichment</CardTitle>
                <CardDescription>
                  {data.session.processedAt
                    ? `Classified ${formatTimestamp(data.session.processedAt)}`
                    : "Awaiting enrichment — the scheduler runs every 5 minutes."}
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-3 text-sm">
                <div className="flex flex-wrap gap-2">
                  {data.session.intentLabel && (
                    <Badge variant={NEUTRAL}>
                      Intent: {data.session.intentLabel}
                      {typeof data.session.intentConfidence === "number" &&
                        ` · ${Math.round(data.session.intentConfidence * 100)}%`}
                    </Badge>
                  )}
                  {data.session.goalAchieved && (
                    <Badge variant={goalVariant(data.session.goalAchieved)}>
                      Goal: {data.session.goalAchieved}
                    </Badge>
                  )}
                  {data.session.sentiment && (
                    <Badge variant={sentimentVariant(data.session.sentiment)}>
                      Sentiment: {data.session.sentiment}
                    </Badge>
                  )}
                </div>
                {data.session.goalSummary && (
                  <div>
                    <p className="text-muted-foreground text-xs">Goal summary</p>
                    <p>{data.session.goalSummary}</p>
                  </div>
                )}
                {data.session.keyTerms && data.session.keyTerms.length > 0 && (
                  <div>
                    <p className="text-muted-foreground text-xs">Key terms</p>
                    <div className="flex flex-wrap gap-1 mt-1">
                      {data.session.keyTerms.map((term) => (
                        <Badge key={term} variant={NEUTRAL}>
                          {term}
                        </Badge>
                      ))}
                    </div>
                  </div>
                )}
              </CardContent>
            </Card>

            <RouterDecisionsCard decisions={routerDecisions ?? []} />

            <SentimentTrajectoryChart trajectory={data.session.sentimentTrajectory} />

            <ReplayTimeline
              messages={data.messages}
              audit={auditData?.entries ?? []}
            />
          </div>
        )}
      </SheetContent>
    </Sheet>
  );
}

/**
 * T87 / §VII.10.d — per-turn sentiment trajectory. Maps each user turn's
 * sentiment to a numeric score (POSITIVE +2 … FRUSTRATED -2) and plots a
 * line chart 1..N so an operator can pinpoint the turn where the
 * conversation soured instead of reading only the session-level rollup.
 * Renders nothing when the session has no trajectory (catalog-driven
 * classifier, or the enricher hasn't run yet).
 */
const SENTIMENT_SCORE: Record<string, number> = {
  POSITIVE: 2,
  NEUTRAL: 1,
  UNKNOWN: 0,
  NEGATIVE: -1,
  FRUSTRATED: -2,
};

const SENTIMENT_SHORT: Record<number, string> = {
  2: "Positive",
  1: "Neutral",
  0: "Unknown",
  [-1]: "Negative",
  [-2]: "Frustrated",
};

const SENTIMENT_COLOR: Record<string, string> = {
  POSITIVE: "#059669", // emerald-600
  NEUTRAL: "#64748b", // slate-500
  UNKNOWN: "#94a3b8", // slate-400
  NEGATIVE: "#dc2626", // red-600
  FRUSTRATED: "#e11d48", // rose-600
};

function SentimentTrajectoryChart({
  trajectory,
}: Readonly<{ trajectory?: string[] | null }>) {
  if (!trajectory || trajectory.length === 0) return null;

  const chartData = trajectory.map((sentiment, idx) => {
    const label = (sentiment ?? "UNKNOWN").toUpperCase();
    return {
      turn: idx + 1,
      score: SENTIMENT_SCORE[label] ?? 0,
      label,
    };
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <IconChartLine className="size-4" />
          Sentiment trajectory
        </CardTitle>
        <CardDescription>
          Per-turn sentiment across the conversation ({chartData.length} turn
          {chartData.length === 1 ? "" : "s"}) — a downward slope marks where it soured.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <div className="h-[220px] w-full">
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={chartData} margin={{ top: 8, right: 16, bottom: 0, left: 0 }}>
              <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="var(--border)" />
              <XAxis
                dataKey="turn"
                stroke="var(--muted-foreground)"
                fontSize={11}
                tick={{ fill: "var(--muted-foreground)" }}
                tickFormatter={(v) => `#${v}`}
              />
              <YAxis
                stroke="var(--muted-foreground)"
                fontSize={11}
                tick={{ fill: "var(--muted-foreground)" }}
                domain={[-2, 2]}
                ticks={[-2, -1, 0, 1, 2]}
                tickFormatter={(v) => SENTIMENT_SHORT[v] ?? ""}
                width={80}
                allowDecimals={false}
              />
              <ReferenceLine y={0} stroke="var(--border)" />
              <Tooltip
                contentStyle={{
                  background: "var(--card)",
                  border: "1px solid var(--border)",
                  borderRadius: "6px",
                  fontSize: "12px",
                }}
                formatter={(_v, _n, item) => [item?.payload?.label ?? "—", "Sentiment"]}
                labelFormatter={(v) => `Turn #${v}`}
              />
              <Line
                type="monotone"
                dataKey="score"
                stroke="#6366f1"
                strokeWidth={2}
                isAnimationActive={false}
                dot={(props) => {
                  const { cx, cy, payload } = props;
                  return (
                    <circle
                      key={`sent-dot-${payload.turn}`}
                      cx={cx}
                      cy={cy}
                      r={4}
                      fill={SENTIMENT_COLOR[payload.label] ?? SENTIMENT_COLOR.UNKNOWN}
                      stroke="var(--card)"
                      strokeWidth={1}
                    />
                  );
                }}
                name="Sentiment"
              />
            </LineChart>
          </ResponsiveContainer>
        </div>
      </CardContent>
    </Card>
  );
}

/**
 * T86 / §VII.10.c — unified conversation replay timeline. Interleaves
 * chat-memory messages with T60 slot-audit entries by timestamp so the
 * operator reads the conversation alongside the slot writes it produced.
 * Each row carries an absolute time, a source badge (user / assistant /
 * NODE / TOOL / ENDPOINT / EXTRACT), and the payload (message text or
 * the {@code (old → new)} slot delta).
 */
function ReplayTimeline({
  messages,
  audit,
}: Readonly<{
  messages: ReadonlyArray<{ timestamp?: string | null; role?: string | null; content?: string | null }>;
  audit: ReadonlyArray<TurChatSlotAuditEntry>;
}>) {
  type TimelineEvent =
    | { kind: "message"; ts: number; tsRaw: string | null; role: string; content: string }
    | { kind: "audit"; ts: number; tsRaw: string | null; entry: TurChatSlotAuditEntry };

  const parseTs = (v?: string | null): { ts: number; raw: string | null } => {
    if (!v) return { ts: 0, raw: null };
    const t = Date.parse(v);
    return { ts: Number.isNaN(t) ? 0 : t, raw: v };
  };

  const events: TimelineEvent[] = [];
  messages.forEach((m, idx) => {
    const { ts, raw } = parseTs(m.timestamp);
    // No timestamp on a message → push to the end so ordering still falls
    // back to the source order without exploding sort comparators.
    events.push({
      kind: "message",
      ts: ts || Number.MAX_SAFE_INTEGER - (messages.length - idx),
      tsRaw: raw,
      role: m.role ?? "unknown",
      content: m.content ?? "",
    });
  });
  audit.forEach((a) => {
    const { ts, raw } = parseTs(a.ts);
    events.push({ kind: "audit", ts, tsRaw: raw, entry: a });
  });
  events.sort((a, b) => a.ts - b.ts);

  if (events.length === 0) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Replay timeline</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-muted-foreground py-4 text-center text-sm">
            Nothing to replay — chat memory and slot audit are both empty for this conversation.
          </p>
        </CardContent>
      </Card>
    );
  }

  const sourceBadgeClass: Record<string, string> = {
    NODE: "bg-blue-100 text-blue-700 dark:bg-blue-950 dark:text-blue-300",
    TOOL: "bg-amber-100 text-amber-700 dark:bg-amber-950 dark:text-amber-300",
    ENDPOINT: "bg-emerald-100 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300",
    EXTRACT: "bg-violet-100 text-violet-700 dark:bg-violet-950 dark:text-violet-300",
  };
  const renderValue = (v: string | null): string => {
    if (v === null || v === undefined) return "—";
    if (v === "") return "(empty)";
    return v;
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">
          Replay timeline ({events.length} event{events.length === 1 ? "" : "s"})
        </CardTitle>
        <CardDescription>
          Messages + slot writes interleaved by timestamp so each turn sits next to the slots it wrote.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-2">
        {events.map((ev, idx) => {
          const stamp = formatTimestamp(ev.tsRaw);
          if (ev.kind === "message") {
            return (
              <div
                key={`m-${idx}`}
                className="rounded-md border bg-background/40 p-3"
              >
                <div className="flex items-center justify-between gap-2 mb-1">
                  <Badge variant={ev.role === "assistant" ? SUCCESS : NEUTRAL}>
                    {ev.role}
                  </Badge>
                  <span className="text-muted-foreground text-xs">{stamp}</span>
                </div>
                <p className="whitespace-pre-wrap text-sm">{ev.content}</p>
              </div>
            );
          }
          const e = ev.entry;
          const badgeClass = sourceBadgeClass[e.source] ?? "bg-muted text-muted-foreground";
          return (
            <div
              key={`a-${e.id}`}
              className="rounded-md border border-dashed bg-muted/10 px-3 py-2 text-xs space-y-1"
            >
              <div className="flex items-center gap-2 flex-wrap">
                <span className={`px-1.5 py-0.5 rounded text-[10px] font-medium uppercase ${badgeClass}`}>
                  slot · {e.source}
                </span>
                <span className="font-mono font-medium truncate" title={e.slotName}>
                  {e.slotName}
                </span>
                <span className="text-muted-foreground ml-auto shrink-0">{stamp}</span>
              </div>
              <div className="font-mono leading-relaxed">
                <span>{renderValue(e.oldValue)}</span>
                <span className="mx-1.5 text-muted-foreground/60">→</span>
                <span className="break-all">{renderValue(e.newValue)}</span>
              </div>
              {e.originDetail && (
                <div className="text-muted-foreground/70 truncate" title={e.originDetail}>
                  {e.originDetail}
                </div>
              )}
            </div>
          );
        })}
      </CardContent>
    </Card>
  );
}

interface MetricOption {
  readonly value: TurChatAnalyticsMetric;
  readonly label: string;
  /** How to render the raw {@code value} returned by the backend. */
  readonly format: (v: number) => string;
}

const METRIC_OPTIONS: readonly MetricOption[] = [
  { value: "sessions",                 label: "Sessions",                  format: (v) => v.toFixed(0) },
  { value: "avg_duration_ms",          label: "Avg duration",              format: formatDuration },
  { value: "avg_tokens_out",           label: "Avg tokens out",            format: (v) => v.toFixed(0) },
  { value: "goal_achievement_rate",    label: "Goal achievement",          format: (v) => `${(v * 100).toFixed(1)}%` },
  { value: "negative_sentiment_rate",  label: "Negative sentiment",        format: (v) => `${(v * 100).toFixed(1)}%` },
  { value: "tool_calls_per_session",   label: "Tool calls / session",      format: (v) => v.toFixed(2) },
  { value: "tool_errors_per_session",  label: "Tool errors / session",     format: (v) => v.toFixed(2) },
  { value: "avg_tool_latency_ms",      label: "Avg tool latency",          format: formatDuration },
  { value: "tool_error_rate_pct",      label: "Tool error rate",           format: (v) => `${v.toFixed(1)}%` },
];

function TimeseriesPanel() {
  const [metric, setMetric] = useState<TurChatAnalyticsMetric>("sessions");
  const { data: points = [], isLoading, isError } = useChatAnalyticsTimeseries({
    metric,
    interval: "hour",
  });
  const option = METRIC_OPTIONS.find((m) => m.value === metric) ?? METRIC_OPTIONS[0];
  // Pre-format X labels so the tooltip and axis share the same string and the
  // axis density is predictable across hour-vs-day buckets.
  const chartData = points.map((p) => ({
    bucket: p.bucket,
    bucketLabel: new Date(p.bucket).toLocaleString([], {
      month: "short",
      day: "numeric",
      hour: "2-digit",
    }),
    value: p.value,
  }));
  const hasData = chartData.length > 0;
  return (
    <Card>
      <CardHeader>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <CardTitle className="flex items-center gap-2 text-base">
              <IconChartLine className="size-4" />
              Trends
            </CardTitle>
            <CardDescription>
              Per-hour aggregates for the same time window as the table.
            </CardDescription>
          </div>
          <Select value={metric} onValueChange={(v) => setMetric(v as TurChatAnalyticsMetric)}>
            <SelectTrigger className="w-[260px]">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {METRIC_OPTIONS.map((m) => (
                <SelectItem key={m.value} value={m.value}>
                  {m.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </CardHeader>
      <CardContent>
        {isLoading && (
          <p className="text-muted-foreground py-12 text-center text-sm">Loading…</p>
        )}
        {isError && (
          <p className="text-destructive py-12 text-center text-sm">Failed to load timeseries.</p>
        )}
        {!isLoading && !isError && !hasData && (
          <p className="text-muted-foreground py-12 text-center text-sm">
            No data for this metric in the selected window.
          </p>
        )}
        {hasData && (
          <div className="h-[260px] w-full">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={chartData} margin={{ top: 8, right: 16, bottom: 0, left: 0 }}>
                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="var(--border)" />
                <XAxis
                  dataKey="bucketLabel"
                  stroke="var(--muted-foreground)"
                  fontSize={11}
                  tick={{ fill: "var(--muted-foreground)" }}
                />
                <YAxis
                  stroke="var(--muted-foreground)"
                  fontSize={11}
                  tick={{ fill: "var(--muted-foreground)" }}
                  tickFormatter={option.format}
                  width={70}
                  allowDecimals
                />
                <Tooltip
                  contentStyle={{
                    background: "var(--card)",
                    border: "1px solid var(--border)",
                    borderRadius: "6px",
                    fontSize: "12px",
                  }}
                  formatter={(v) => [option.format(Number(v)), option.label]}
                />
                <Line
                  type="monotone"
                  dataKey="value"
                  stroke="#3b82f6"
                  strokeWidth={2}
                  dot={false}
                  isAnimationActive={false}
                  name={option.label}
                />
              </LineChart>
            </ResponsiveContainer>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

/**
 * T88 / §VII.10.e — per-tool latency percentiles. The timeseries
 * {@code avg_tool_latency_ms} metric collapses every tool into one average,
 * so a single slow tool hides behind a sea of fast ones. This table
 * disaggregates: one row per tool, sorted by p95 descending (the outlier is
 * row 1, accented). A proportional bar on the p95 column makes the outlier
 * pop visually. Renders an empty state until at least one session has
 * captured a tool call.
 */
function formatLatencyMs(ms: number | null | undefined): string {
  if (ms === null || ms === undefined || ms <= 0) return "—";
  if (ms < 1000) return `${Math.round(ms)} ms`;
  return `${(ms / 1000).toFixed(2)} s`;
}

function ToolLatencyPanel() {
  const { data: rows = [], isLoading, isError } = useChatAnalyticsToolLatency({ limit: 50 });
  const maxP95 = rows.reduce((acc, r) => Math.max(acc, r.p95Ms ?? 0), 0);
  const hasData = rows.length > 0;
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <IconGauge className="size-4" />
          Tool latency (p95 per tool)
        </CardTitle>
        <CardDescription>
          Latency percentiles disaggregated by tool over the past 7 days — sorted by p95 so the
          outlier is on top. The trends panel only shows the average across all tools.
        </CardDescription>
      </CardHeader>
      <CardContent>
        {isLoading && (
          <p className="text-muted-foreground py-8 text-center text-sm">Loading…</p>
        )}
        {isError && (
          <p className="text-destructive py-8 text-center text-sm">Failed to load tool latency.</p>
        )}
        {!isLoading && !isError && !hasData && (
          <p className="text-muted-foreground py-8 text-center text-sm">
            No tool calls recorded yet — RAG search, custom tools, and code execution all show up
            here once a conversation invokes them.
          </p>
        )}
        {hasData && (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Tool</TableHead>
                <TableHead className="text-right">Calls</TableHead>
                <TableHead className="text-right">Err %</TableHead>
                <TableHead className="text-right">p50</TableHead>
                <TableHead className="text-right">p95</TableHead>
                <TableHead className="text-right">p99</TableHead>
                <TableHead className="text-right">Max</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((row, idx) => (
                <ToolLatencyRow key={row.tool} row={row} maxP95={maxP95} outlier={idx === 0} />
              ))}
            </TableBody>
          </Table>
        )}
      </CardContent>
    </Card>
  );
}

function ToolLatencyRow({
  row,
  maxP95,
  outlier,
}: Readonly<{ row: TurChatAnalyticsToolLatencyRow; maxP95: number; outlier: boolean }>) {
  const barPct = maxP95 > 0 ? Math.max(2, Math.round(((row.p95Ms ?? 0) / maxP95) * 100)) : 0;
  const errPct = row.errorRatePct ?? 0;
  return (
    <TableRow>
      <TableCell className="font-mono text-xs">
        <span className="flex items-center gap-2">
          {outlier && <Badge variant={DESTRUCTIVE}>p95 outlier</Badge>}
          <span className="truncate" title={row.tool}>
            {row.tool}
          </span>
        </span>
      </TableCell>
      <TableCell className="text-right tabular-nums">{row.count ?? 0}</TableCell>
      <TableCell className="text-right tabular-nums">
        {errPct > 0 ? (
          <span className="text-destructive">{errPct.toFixed(1)}%</span>
        ) : (
          <span className="text-muted-foreground">0%</span>
        )}
      </TableCell>
      <TableCell className="text-right tabular-nums">{formatLatencyMs(row.p50Ms)}</TableCell>
      <TableCell className="text-right tabular-nums">
        <span className="flex items-center justify-end gap-2">
          <span
            className="bg-gradient-to-r from-blue-600 to-indigo-600 h-1.5 rounded-full"
            style={{ width: `${barPct}px`, maxWidth: "100px" }}
          />
          <span className="font-medium">{formatLatencyMs(row.p95Ms)}</span>
        </span>
      </TableCell>
      <TableCell className="text-right tabular-nums">{formatLatencyMs(row.p99Ms)}</TableCell>
      <TableCell className="text-right tabular-nums">{formatLatencyMs(row.maxMs)}</TableCell>
    </TableRow>
  );
}

/**
 * T90 / §VII.10.g — live slot-stream SSE channel debug surface. Polls
 * {@code /slot-sse-channels} every 5s and renders the open channels + their
 * connection refcounts — the server-side counterpart to the vanilla SDK's
 * {@code _slotsSseOpenChannelCount()}. Lets an operator validate "12 portal
 * tabs, 4 channels, refcount sane" and confirm channels are reclaimed when the
 * last tab closes (no leaked subscriptions). Per-process + ephemeral: counts
 * only this node and resets on restart.
 */
function formatRelative(epochMillis: number): string {
  if (!epochMillis) return "—";
  const deltaSec = Math.max(0, Math.round((Date.now() - epochMillis) / 1000));
  if (deltaSec < 60) return `${deltaSec}s ago`;
  const min = Math.floor(deltaSec / 60);
  if (min < 60) return `${min}m ago`;
  const hours = Math.floor(min / 60);
  return `${hours}h ago`;
}

function SseChannelsPanel() {
  const { data, isLoading, isError } = useChatSlotSseChannels();
  const channels = data?.channels ?? [];
  const hasData = channels.length > 0;
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <IconBroadcast className="size-4" />
          Slot-stream SSE channels (live)
        </CardTitle>
        <CardDescription>
          Open <span className="font-mono">/chat/slots/stream</span> channels on this node and their
          connection refcount — refreshes every 5s. The browser SDK multiplexes tabs onto a single
          channel; this is the server's view. Per-process and ephemeral (resets on restart).
        </CardDescription>
      </CardHeader>
      <CardContent>
        {isLoading && (
          <p className="text-muted-foreground py-8 text-center text-sm">Loading…</p>
        )}
        {isError && (
          <p className="text-destructive py-8 text-center text-sm">
            Failed to load SSE channels.
          </p>
        )}
        {!isLoading && !isError && (
          <>
            <div className="mb-4 grid grid-cols-3 gap-3">
              <SseStat label="Open channels" value={data?.openChannels ?? 0} />
              <SseStat label="Connections" value={data?.openSubscribers ?? 0} />
              <SseStat label="Conversations" value={data?.distinctConversations ?? 0} />
            </div>
            {!hasData ? (
              <p className="text-muted-foreground py-6 text-center text-sm">
                No open slot streams right now — open a chat with a slot-filling flow to see
                channels appear here.
              </p>
            ) : (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Conversation</TableHead>
                    <TableHead>Mode</TableHead>
                    <TableHead className="text-right">Refcount</TableHead>
                    <TableHead className="text-right">Opened</TableHead>
                    <TableHead className="text-right">Last change</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {channels.map((channel) => (
                    <SseChannelRow
                      key={`${channel.conversationId}-${channel.mode}`}
                      channel={channel}
                    />
                  ))}
                </TableBody>
              </Table>
            )}
          </>
        )}
      </CardContent>
    </Card>
  );
}

function SseStat({ label, value }: Readonly<{ label: string; value: number }>) {
  return (
    <div className="rounded-md border bg-background/40 p-3 text-center">
      <div className="text-2xl font-semibold tabular-nums">{value}</div>
      <div className="text-muted-foreground text-xs">{label}</div>
    </div>
  );
}

function SseChannelRow({ channel }: Readonly<{ channel: TurChatSlotSseChannel }>) {
  return (
    <TableRow>
      <TableCell className="font-mono text-xs">
        <span className="truncate" title={channel.conversationId}>
          {channel.conversationId}
        </span>
      </TableCell>
      <TableCell>
        <Badge variant={channel.mode === "delta" ? SECONDARY : NEUTRAL}>{channel.mode}</Badge>
      </TableCell>
      <TableCell className="text-right tabular-nums font-medium">{channel.refcount}</TableCell>
      <TableCell className="text-muted-foreground text-right text-xs">
        {formatRelative(channel.firstOpenedEpochMillis)}
      </TableCell>
      <TableCell className="text-muted-foreground text-right text-xs">
        {formatRelative(channel.lastChangeEpochMillis)}
      </TableCell>
    </TableRow>
  );
}

interface DimensionOption {
  readonly value: TurChatAnalyticsScorecardDimension;
  readonly label: string;
}

const DIMENSION_OPTIONS: readonly DimensionOption[] = [
  { value: "agentId",              label: "Agent" },
  { value: "personaId",            label: "Persona" },
  { value: "experimentKey",        label: "Experiment key" },
  { value: "variantLabel",         label: "A/B variant" },
  { value: "parentConversationId", label: "Parent conversation" },
  // T74 — visitor cohort dimensions.
  { value: "deviceType",           label: "Device (mobile/desktop)" },
  { value: "locale",               label: "Locale" },
  { value: "timezone",             label: "Timezone" },
];

// T74 — device cohort filter values. "" = no filter (all devices).
const DEVICE_FILTER_OPTIONS: readonly { value: TurChatDeviceType | "all"; label: string }[] = [
  { value: "all",     label: "All devices" },
  { value: "mobile",  label: "Mobile" },
  { value: "desktop", label: "Desktop" },
  { value: "tablet",  label: "Tablet" },
  { value: "bot",     label: "Bot" },
  { value: "unknown", label: "Unknown" },
];

function ScorecardPanel() {
  const [dimension, setDimension] = useState<TurChatAnalyticsScorecardDimension>("variantLabel");
  // T74 cohort filters — narrow the population so a variant can be judged
  // within a sub-population ("does variant X win on mobile / in pt-BR?").
  const [deviceFilter, setDeviceFilter] = useState<TurChatDeviceType | "all">("all");
  const [localeFilter, setLocaleFilter] = useState("");
  const [timezoneFilter, setTimezoneFilter] = useState("");
  const { data: rows = [], isLoading, isError } = useChatAnalyticsScorecard({
    dimension,
    limit: 20,
    deviceType: deviceFilter === "all" ? "" : deviceFilter,
    locale: localeFilter.trim() || undefined,
    timezone: timezoneFilter.trim() || undefined,
  });
  const isExperimentDim = dimension === "experimentKey" || dimension === "variantLabel";
  const isParentConvDim = dimension === "parentConversationId";
  const hasCohortFilter =
    deviceFilter !== "all" || localeFilter.trim() !== "" || timezoneFilter.trim() !== "";
  let description: string;
  if (isExperimentDim) {
    description =
      "A/B variants compared head-to-head — add a cohort filter to ask whether a variant wins in a sub-population.";
  } else if (isParentConvDim) {
    description = "Orchestrator agents grouped by parent conversation — each row is one orchestrator + its sub-task sessions (T109/T110).";
  } else {
    description = "Per-bucket session counts + AI-enriched signals. Pick a dimension to pivot.";
  }
  return (
    <Card>
      <CardHeader className="space-y-3">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <CardTitle className="flex items-center gap-2 text-base">
              <IconFlask className="size-4" />
              Scorecard
            </CardTitle>
            <CardDescription>{description}</CardDescription>
          </div>
          <Select
            value={dimension}
            onValueChange={(v) => setDimension(v as TurChatAnalyticsScorecardDimension)}
          >
            <SelectTrigger className="w-[200px]">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {DIMENSION_OPTIONS.map((d) => (
                <SelectItem key={d.value} value={d.value}>
                  {d.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        {/* T74 — cohort filter row. Device is a fixed enum; locale + timezone
            are free-text (exact match, e.g. "pt-BR", "America/Sao_Paulo"). */}
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-muted-foreground text-xs">Cohort:</span>
          <Select
            value={deviceFilter}
            onValueChange={(v) => setDeviceFilter(v as TurChatDeviceType | "all")}
          >
            <SelectTrigger className="h-8 w-[150px]">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {DEVICE_FILTER_OPTIONS.map((d) => (
                <SelectItem key={d.value} value={d.value}>
                  {d.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Input
            value={localeFilter}
            onChange={(e) => setLocaleFilter(e.target.value)}
            placeholder="Locale (e.g. pt-BR)"
            className="h-8 w-[160px]"
          />
          <Input
            value={timezoneFilter}
            onChange={(e) => setTimezoneFilter(e.target.value)}
            placeholder="Timezone (e.g. America/Sao_Paulo)"
            className="h-8 w-[220px]"
          />
          {hasCohortFilter && (
            <button
              type="button"
              className="text-muted-foreground hover:text-foreground text-xs underline"
              onClick={() => {
                setDeviceFilter("all");
                setLocaleFilter("");
                setTimezoneFilter("");
              }}
            >
              Clear cohort
            </button>
          )}
        </div>
      </CardHeader>
      <CardContent>
        {isLoading && (
          <p className="text-muted-foreground py-8 text-center text-sm">Loading…</p>
        )}
        {isError && (
          <p className="text-destructive py-8 text-center text-sm">Failed to load scorecard.</p>
        )}
        {!isLoading && !isError && rows.length === 0 && (
          <p className="text-muted-foreground py-8 text-center text-sm">
            {emptyStateMessage(isExperimentDim, isParentConvDim, hasCohortFilter)}
          </p>
        )}
        {rows.length > 0 && (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>
                  {DIMENSION_OPTIONS.find((d) => d.value === dimension)?.label ?? dimension}
                </TableHead>
                <TableHead className="text-right">Sessions</TableHead>
                <TableHead className="text-right">Avg duration</TableHead>
                <TableHead className="text-right">Goal ✓ rate</TableHead>
                <TableHead className="text-right">Negative rate</TableHead>
                <TableHead>Top intent</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((row) => (
                <TableRow key={row.bucket}>
                  <TableCell className="font-medium">
                    {row.bucketLabel ?? row.bucket ?? "unknown"}
                  </TableCell>
                  <TableCell className="text-right">{row.sessions ?? 0}</TableCell>
                  <TableCell className="text-right">
                    {formatDuration(row.avgDurationMs)}
                  </TableCell>
                  <TableCell className="text-right">
                    {formatRate(row.goalAchievedRate)}
                  </TableCell>
                  <TableCell className="text-right">
                    {formatRate(row.negativeRate)}
                  </TableCell>
                  <TableCell className="text-xs">
                    {row.topIntent ?? "—"}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </CardContent>
    </Card>
  );
}

function formatRate(value: number | null | undefined): string {
  if (value === null || value === undefined) return "—";
  return `${(value * 100).toFixed(1)}%`;
}

function emptyStateMessage(
  isExperimentDim: boolean,
  isParentConvDim: boolean,
  hasCohortFilter: boolean,
): string {
  if (hasCohortFilter) {
    return "No sessions match this cohort in the selected window — try a broader device/locale/timezone filter.";
  }
  if (isExperimentDim) {
    return "No experiments declared yet — set an experimentKey on at least two chat flows to enable A/B routing.";
  }
  if (isParentConvDim) {
    return "No orchestrator sessions yet — child sessions get this dimension populated only when a Custom Tool calls agent.invoke(...).";
  }
  return "No data for this dimension in the selected window.";
}

function SessionRow({
  row,
  onSelect,
}: Readonly<{ row: TurChatAnalyticsSession; onSelect: (id: string) => void }>) {
  return (
    <TableRow className="cursor-pointer" onClick={() => onSelect(row.conversationId)}>
      <TableCell className="font-mono text-xs whitespace-nowrap">
        {formatTimestamp(row.startedAt)}
      </TableCell>
      <TableCell>
        <Badge variant={outcomeVariant(row.outcome)}>{row.outcome ?? "—"}</Badge>
      </TableCell>
      <TableCell>{row.intentLabel ?? "—"}</TableCell>
      <TableCell>
        {row.goalAchieved ? (
          <Badge variant={goalVariant(row.goalAchieved)}>{row.goalAchieved}</Badge>
        ) : (
          "—"
        )}
      </TableCell>
      <TableCell>
        {row.sentiment ? (
          <Badge variant={sentimentVariant(row.sentiment)}>{row.sentiment}</Badge>
        ) : (
          "—"
        )}
      </TableCell>
      <TableCell
        className="text-xs"
        title={row.agentId ?? undefined}
      >
        {row.agentTitle ?? row.agentId ?? "—"}
      </TableCell>
      <TableCell
        className="text-xs"
        title={row.personaId ?? undefined}
      >
        {row.personaName ?? row.personaId ?? "—"}
      </TableCell>
      <TableCell className="text-right">{formatDuration(row.durationMs)}</TableCell>
    </TableRow>
  );
}

export default function ChatAnalyticsPage() {
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const { data: health } = useChatAnalyticsHealth();
  const { data: sessions, isLoading, isError } = useChatAnalyticsSessions({ limit: 100 });

  if (health && !health.enabled) {
    return (
      <div className="px-4 lg:px-6">
        <Card>
          <CardContent className="flex flex-col items-center gap-3 py-16 text-center">
            <IconMessageOff className="text-muted-foreground size-10" />
            <p className="text-base font-medium">Chat analytics is disabled</p>
            <p className="text-muted-foreground max-w-md text-sm">
              Set <span className="font-mono">turing.logging.engine</span> to{" "}
              <span className="font-mono">mongodb</span> or{" "}
              <span className="font-mono">redis</span> to start collecting chat session analytics.
            </p>
          </CardContent>
        </Card>
      </div>
    );
  }

  return (
    <div className="space-y-6 px-4 lg:px-6">
      <TimeseriesPanel />
      <ToolLatencyPanel />
      <SseChannelsPanel />
      <ScorecardPanel />
      <Card>
        <CardHeader>
          <CardTitle>Recent chat sessions</CardTitle>
          <CardDescription>
            Last 100 sessions over the past 7 days. Click any row to drill into the transcript and AI enrichment.
            {health && ` Engine: ${health.engine}.`}
          </CardDescription>
        </CardHeader>
        <CardContent>
          {isLoading && (
            <p className="text-muted-foreground py-12 text-center text-sm">Loading sessions…</p>
          )}
          {isError && (
            <p className="text-destructive py-12 text-center text-sm">Failed to load sessions.</p>
          )}
          {!isLoading && !isError && (!sessions || sessions.length === 0) && (
            <p className="text-muted-foreground py-12 text-center text-sm">
              No chat sessions recorded yet — start a conversation in the chat console.
            </p>
          )}
          {sessions && sessions.length > 0 && (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Started</TableHead>
                  <TableHead>Outcome</TableHead>
                  <TableHead>Intent</TableHead>
                  <TableHead>Goal</TableHead>
                  <TableHead>Sentiment</TableHead>
                  <TableHead>Agent</TableHead>
                  <TableHead>Persona</TableHead>
                  <TableHead className="text-right">Duration</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {sessions.map((row) => (
                  <SessionRow key={row.conversationId} row={row} onSelect={setSelectedId} />
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      <DetailPanel conversationId={selectedId} onClose={() => setSelectedId(null)} />
    </div>
  );
}
