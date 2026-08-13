import {
  useApplyMatchAdjustment,
  useMatchAdjustments,
  useMatchDrift,
  useMatchTrend,
} from "@/api/queries/persona-match.queries";
import { BentoFormSection } from "@/components/bento";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import {
  confirmedFieldsOf,
  driftTone,
  formatDelta,
  isApplicable,
  type MatchAdjustmentItem,
  type MatchAdjustments,
  type MatchApplyResult,
  type MatchAudienceField,
  type MatchDriftEntry,
  type MatchPersonaRef,
  type MatchSource,
  type MatchTrendPoint,
} from "@/models/persona/persona-match.model";
import {
  IconAlertTriangle,
  IconArrowDownRight,
  IconArrowRight,
  IconArrowUpRight,
  IconCheck,
  IconFileText,
  IconLoader2,
  IconMinus,
  IconSparkles,
  IconTrendingUp,
  IconUserCircle,
  IconWand,
} from "@tabler/icons-react";
import type { TFunction } from "i18next";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

/**
 * Persona Match — the tuning loop (Block AT / §XLIII, T704). Reads the per-cell
 * fit history the runner now persists and turns it into the three surfaces the
 * bidirectional loop needs: the project's fit trend across runs, the drift list
 * ("this content fell below fit for persona X since last run"), and on-demand
 * LLM-suggested adjustments for both sides — edit the content, or correct the
 * persona. Everything here is advisory; nothing is applied automatically.
 *
 * Renders nothing until a project has at least one run on record.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
export function MatchTuningSection({
  projectId,
  sources,
  personas,
}: Readonly<{
  projectId: string;
  sources: MatchSource[];
  personas: MatchPersonaRef[];
}>) {
  const { t } = useTranslation();
  const { data: trend } = useMatchTrend(projectId);
  const { data: drift } = useMatchDrift(projectId);
  const adjustments = useMatchAdjustments(projectId);
  const [report, setReport] = useState<MatchAdjustments | null>(null);

  const sourceNames = useMemo(
    () => new Map(sources.map((s) => [s.id, s.name])),
    [sources],
  );
  const personaNames = useMemo(
    () => new Map(personas.map((p) => [p.id, p.name])),
    [personas],
  );

  const runCount = trend?.runCount ?? 0;
  if (runCount === 0) {
    return null;
  }

  const regressions = (drift?.entries ?? []).filter((e) => e.delta < 0);
  const improvements = (drift?.entries ?? []).filter((e) => e.delta > 0);

  return (
    <BentoFormSection
      icon={IconTrendingUp}
      tone="emerald"
      title={t("persona.match.tuningTitle")}
      description={t("persona.match.tuningDesc")}
      trailing={<Badge variant="secondary">{t("persona.match.runCount", { count: runCount })}</Badge>}
    >
      {runCount < 2 ? (
        <p className="text-sm text-muted-foreground">{t("persona.match.needsTwoRuns")}</p>
      ) : (
        <>
          <TrendChart points={trend?.average ?? []} />

          <div className="grid gap-4 lg:grid-cols-2">
            <DriftList
              title={t("persona.match.regressions")}
              emptyText={t("persona.match.noRegressions")}
              entries={regressions}
              sourceNames={sourceNames}
              personaNames={personaNames}
              threshold={drift?.fitThreshold}
            />
            <DriftList
              title={t("persona.match.improvements")}
              emptyText={t("persona.match.noImprovements")}
              entries={[...improvements].reverse()}
              sourceNames={sourceNames}
              personaNames={personaNames}
              threshold={drift?.fitThreshold}
            />
          </div>
        </>
      )}

      {/* --- adjustment suggestions (both sides of the loop) --- */}
      <div className="flex flex-col gap-3 border-t border-border/60 pt-4">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div>
            <h4 className="text-sm font-semibold">{t("persona.match.adjustmentsTitle")}</h4>
            <p className="text-xs text-muted-foreground">
              {t("persona.match.adjustmentsDesc")}
            </p>
          </div>
          <Button
            type="button"
            variant="outline"
            size="sm"
            className="gap-1.5"
            disabled={adjustments.isPending}
            onClick={() =>
              adjustments.mutate(Boolean(report), { onSuccess: (data) => setReport(data) })
            }
          >
            {adjustments.isPending ? (
              <IconLoader2 className="size-4 animate-spin" />
            ) : (
              <IconWand className="size-4 text-violet-600" />
            )}
            {report
              ? t("persona.match.regenerateAdjustments")
              : t("persona.match.suggestAdjustments")}
          </Button>
        </div>

        {adjustments.isError && (
          <p className="text-sm text-rose-600">{t("persona.match.adjustmentsError")}</p>
        )}

        {report && <AdjustmentReport report={report} projectId={projectId} />}
      </div>
    </BentoFormSection>
  );
}

// ---------------------------------------------------------------------------

/** Project-average fit per run, oldest → newest. */
function TrendChart({ points }: Readonly<{ points: MatchTrendPoint[] }>) {
  const { t } = useTranslation();
  const data = useMemo(
    () =>
      points.map((p, index) => ({
        index: index + 1,
        fit: Math.round(p.fitScore),
        label: formatRunDate(p.evaluatedAt),
      })),
    [points],
  );
  if (data.length < 2) {
    return null;
  }
  return (
    <div className="bento-tile rounded-2xl border border-border/60 bg-card/40 p-4 backdrop-blur">
      <div className="mb-2 flex items-center gap-1.5 text-sm font-medium">
        <IconTrendingUp size={16} className="text-emerald-600 dark:text-emerald-300" />
        {t("persona.match.trendTitle")}
      </div>
      <div className="h-44">
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={data} margin={{ top: 4, right: 8, bottom: 0, left: -18 }}>
            <CartesianGrid strokeDasharray="3 3" className="stroke-border/50" />
            <XAxis dataKey="label" tick={{ fontSize: 11 }} />
            <YAxis domain={[0, 100]} tick={{ fontSize: 11 }} />
            <Tooltip
              formatter={(value) => [`${value ?? 0}%`, t("persona.match.avgFit")]}
              contentStyle={{ fontSize: 12, borderRadius: 12 }}
            />
            <Line
              type="monotone"
              dataKey="fit"
              stroke="#10b981"
              strokeWidth={2}
              dot={{ r: 3 }}
              isAnimationActive={false}
            />
          </LineChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}

function DriftList({
  title,
  emptyText,
  entries,
  sourceNames,
  personaNames,
  threshold,
}: Readonly<{
  title: string;
  emptyText: string;
  entries: MatchDriftEntry[];
  sourceNames: Map<string, string>;
  personaNames: Map<string, string>;
  /** T826 — the project's good-fit bar the crossing flags were computed against. */
  threshold?: number;
}>) {
  const { t } = useTranslation();
  return (
    <div className="bento-tile bento-glass flex flex-col gap-2 rounded-2xl p-4">
      <div className="flex items-center justify-between gap-2">
        <span className="text-sm font-semibold">{title}</span>
        <Badge variant="outline" className="text-[10px]">
          {entries.length}
        </Badge>
      </div>
      {entries.length === 0 ? (
        <p className="text-sm text-muted-foreground">{emptyText}</p>
      ) : (
        <ul className="flex flex-col gap-1.5">
          {entries.map((entry) => {
            const tone = driftTone(entry.delta);
            const Arrow = arrowFor(tone.direction);
            return (
              <li
                key={`${entry.sourceId}:${entry.personaId}`}
                className={`flex items-center gap-2 rounded-xl border px-3 py-2 ${tone.border} ${tone.soft}`}
              >
                <Arrow size={15} className={`shrink-0 ${tone.text}`} />
                <div className="min-w-0 flex-1">
                  <p className="truncate text-xs font-medium">
                    {sourceNames.get(entry.sourceId) ?? entry.sourceId}
                    <span className="text-muted-foreground"> × </span>
                    {personaNames.get(entry.personaId) ?? entry.personaId}
                  </p>
                  <p className="text-[11px] text-muted-foreground">
                    {t("persona.match.driftLine", {
                      previous: Math.round(entry.previousFit),
                      current: Math.round(entry.currentFit),
                    })}
                    {entry.contentChanged && ` · ${t("persona.match.contentChanged")}`}
                  </p>
                </div>
                {entry.crossedBelowFit && (
                  <span
                    className="shrink-0 text-amber-600 dark:text-amber-300"
                    title={
                      threshold === undefined
                        ? t("persona.match.crossedBelowFit")
                        : t("persona.match.crossedBelowFitAt", {
                            threshold: Math.round(threshold),
                          })
                    }
                  >
                    <IconAlertTriangle size={15} />
                  </span>
                )}
                <span className={`shrink-0 text-xs font-semibold ${tone.text}`}>
                  {formatDelta(entry.delta)}
                </span>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}

function AdjustmentReport({
  report,
  projectId,
}: Readonly<{ report: MatchAdjustments; projectId: string }>) {
  const { t } = useTranslation();
  const contents = report.items.filter((i) => i.target === "CONTENT");
  const personas = report.items.filter((i) => i.target === "PERSONA");

  if (report.items.length === 0) {
    return (
      <p className="text-sm text-muted-foreground">
        {report.error ?? t("persona.match.noAdjustments")}
      </p>
    );
  }

  return (
    <div className="flex flex-col gap-3">
      <p className="flex items-center gap-1.5 text-xs text-muted-foreground">
        <IconSparkles size={13} className={report.llmUsed ? "text-violet-500" : undefined} />
        {report.llmUsed
          ? t("persona.match.adjustmentsFromLlm")
          : t("persona.match.adjustmentsDeterministic")}
        {!report.llmUsed && report.error ? ` · ${report.error}` : ""}
      </p>
      <div className="grid gap-3 lg:grid-cols-2">
        <AdjustmentColumn
          title={t("persona.match.adjustContents")}
          icon={IconFileText}
          items={contents}
          projectId={projectId}
          llmUsed={report.llmUsed}
        />
        <AdjustmentColumn
          title={t("persona.match.adjustPersonas")}
          icon={IconUserCircle}
          items={personas}
          projectId={projectId}
          llmUsed={report.llmUsed}
        />
      </div>
    </div>
  );
}

/**
 * T831 — the audience-field value as a reader sees it. An enumerated field has a
 * label; free text is shown verbatim.
 */
function fieldValueLabel(
  t: TFunction,
  field: MatchAudienceField,
  value: string | null,
): string {
  if (!value) {
    return t("persona.match.applyUnset");
  }
  if (field === "READING_LEVEL") {
    return t(`persona.readingLevels.${value}`, { defaultValue: value });
  }
  if (field === "DOMAIN_EXPERTISE") {
    return t(`persona.domainExpertise.${value}`, { defaultValue: value });
  }
  return value;
}

const FIELD_LABEL_KEY: Record<MatchAudienceField, string> = {
  READING_LEVEL: "forms.persona.readingLevel",
  DOMAIN_EXPERTISE: "forms.persona.domainExpertise",
  VOCABULARY_CEILING: "forms.persona.vocabularyCeiling",
  COMPREHENSION_NOTES: "forms.persona.comprehensionNotes",
};

/**
 * T831 — the Apply affordance, PERSONA items only. Rendered as a field-level
 * diff the operator ticks field by field: nothing is pre-ticked and there is no
 * select-all, because an LLM rewriting an audience definition unattended is the
 * failure mode the advisory-only rule exists to prevent.
 */
function ApplyPanel({
  projectId,
  item,
  llmUsed,
}: Readonly<{
  projectId: string;
  item: MatchAdjustmentItem;
  llmUsed: boolean;
}>) {
  const { t } = useTranslation();
  const [ticked, setTicked] = useState<Set<string>>(new Set());
  const apply = useApplyMatchAdjustment(projectId);
  const [result, setResult] = useState<MatchApplyResult | null>(null);

  const toggle = (field: string) => {
    setTicked((current) => {
      const next = new Set(current);
      if (next.has(field)) {
        next.delete(field);
      } else {
        next.add(field);
      }
      return next;
    });
  };

  const confirmed = confirmedFieldsOf(item, ticked);
  const remaining = item.fieldEdits.filter(
    (edit) => !result?.applied.some((a) => a.field === edit.field),
  );

  if (remaining.length === 0) {
    return (
      <p className="flex items-center gap-1.5 text-xs text-emerald-600 dark:text-emerald-400">
        <IconCheck size={13} className="shrink-0" />
        {t("persona.match.applyAllDone")}
      </p>
    );
  }

  return (
    <div className="flex flex-col gap-2 rounded-xl border border-violet-500/25 bg-violet-500/5 p-2.5">
      <p className="text-[0.7rem] font-semibold uppercase tracking-wide text-violet-600 dark:text-violet-300">
        {t("persona.match.applyTitle")}
      </p>
      <ul className="flex flex-col gap-2">
        {remaining.map((edit) => (
          <li key={edit.field} className="flex items-start gap-2">
            <Checkbox
              id={`${item.targetId}-${edit.field}`}
              checked={ticked.has(edit.field)}
              onCheckedChange={() => toggle(edit.field)}
              className="mt-0.5 shrink-0"
            />
            <label
              htmlFor={`${item.targetId}-${edit.field}`}
              className="min-w-0 flex-1 cursor-pointer text-xs"
            >
              <span className="font-medium">{t(FIELD_LABEL_KEY[edit.field])}</span>
              <span className="mt-0.5 flex flex-wrap items-center gap-1.5">
                <span className="text-muted-foreground line-through">
                  {fieldValueLabel(t, edit.field, edit.currentValue)}
                </span>
                <IconArrowRight size={12} className="shrink-0 text-muted-foreground" />
                <span className="font-semibold text-violet-600 dark:text-violet-300">
                  {fieldValueLabel(t, edit.field, edit.proposedValue)}
                </span>
              </span>
              {edit.reason && (
                <span className="mt-0.5 block text-muted-foreground">{edit.reason}</span>
              )}
            </label>
          </li>
        ))}
      </ul>
      <div className="flex flex-wrap items-center gap-2">
        <Button
          size="sm"
          variant="outline"
          disabled={confirmed.length === 0 || apply.isPending}
          onClick={() =>
            apply.mutate(
              {
                personaId: item.targetId,
                rationale: item.rationale ?? null,
                llmUsed,
                fields: confirmed,
              },
              {
                onSuccess: (data) => {
                  setResult(data);
                  setTicked(new Set());
                },
              },
            )
          }
        >
          {apply.isPending ? (
            <IconLoader2 size={14} className="mr-1 animate-spin" />
          ) : (
            <IconCheck size={14} className="mr-1" />
          )}
          {t("persona.match.applyConfirmed", { count: confirmed.length })}
        </Button>
        {apply.isError && (
          <span className="text-xs text-destructive">{t("persona.match.applyError")}</span>
        )}
      </div>
      {result && result.applied.length > 0 && (
        <p className="text-xs text-emerald-600 dark:text-emerald-400">
          {t("persona.match.applyApplied", { count: result.applied.length })}
        </p>
      )}
      {result?.skipped.map((skip) => (
        <p key={skip.field} className="text-xs text-amber-600 dark:text-amber-400">
          {t("persona.match.applySkipped", { field: skip.field, reason: skip.reason })}
        </p>
      ))}
    </div>
  );
}

function AdjustmentColumn({
  title,
  icon: Icon,
  items,
  projectId,
  llmUsed,
}: Readonly<{
  title: string;
  icon: typeof IconFileText;
  items: MatchAdjustmentItem[];
  projectId: string;
  llmUsed: boolean;
}>) {
  const { t } = useTranslation();
  if (items.length === 0) {
    return null;
  }
  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
        <Icon size={14} />
        {title}
      </div>
      {items.map((item) => (
        <div
          key={`${item.target}:${item.targetId}`}
          className="bento-tile flex flex-col gap-1.5 rounded-2xl border border-border/60 bg-card/40 p-3 backdrop-blur"
        >
          <div className="flex items-baseline gap-2">
            <span className="min-w-0 flex-1 truncate text-sm font-medium" title={item.targetName ?? item.targetId}>
              {item.targetName ?? item.targetId}
            </span>
            <span className="shrink-0 text-xs text-muted-foreground">
              {t("persona.match.avgFitInline", { avg: Math.round(item.avgFit) })}
            </span>
            {typeof item.delta === "number" && item.delta !== 0 && (
              <span className={`shrink-0 text-xs font-semibold ${driftTone(item.delta).text}`}>
                {formatDelta(item.delta)}
              </span>
            )}
          </div>
          {item.rationale && (
            <p className="text-xs text-muted-foreground">{item.rationale}</p>
          )}
          <ul className="flex flex-col gap-1">
            {item.actions.map((action) => (
              <li key={action} className="flex items-start gap-1.5 text-xs">
                <IconWand size={13} className="mt-0.5 shrink-0 text-violet-500" />
                <span>{action}</span>
              </li>
            ))}
          </ul>
          {isApplicable(item) && (
            <ApplyPanel projectId={projectId} item={item} llmUsed={llmUsed} />
          )}
        </div>
      ))}
    </div>
  );
}

// ---------------------------------------------------------------------------

function arrowFor(direction: "up" | "down" | "flat") {
  if (direction === "up") return IconArrowUpRight;
  if (direction === "down") return IconArrowDownRight;
  return IconMinus;
}

/** Short, locale-aware run label for the trend axis. */
function formatRunDate(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "—";
  return date.toLocaleDateString(undefined, { month: "short", day: "numeric" });
}
