import { personaResearchService, useResearchStudies } from "@/api/queries/persona-research.queries";
import { ROUTES } from "@/app/routes.const";
import { BentoFormSection, BentoHero } from "@/components/bento";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import type { ResearchProgramRollup } from "@/models/persona/persona-research.model";
import {
  IconChartBar,
  IconCheck,
  IconLoader2,
  IconPlayerPlayFilled,
  IconStack2,
  IconUsersGroup,
} from "@tabler/icons-react";
import { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";

/**
 * Multi-study program planner + cross-study rollup (Block AW / §XLVI.5, T733 —
 * PRISMA). One surface to plan several studies across different audiences and roll
 * their insights up into a program-level view: pick studies, optionally run them
 * all, then see program totals, each study's sufficiency, and the themes that
 * recur ACROSS audiences (shared themes highlighted). Purely deterministic — it
 * reuses each study's cached report + saturation, adding no new inference.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
export default function BentoPersonaResearchProgramPage() {
  const { t } = useTranslation();
  const { data: studies } = useResearchStudies();

  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [rollup, setRollup] = useState<ResearchProgramRollup | null>(null);
  const [loading, setLoading] = useState(false);
  const [running, setRunning] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const selectedIds = useMemo(() => Array.from(selected), [selected]);

  const toggle = useCallback((id: string) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }, []);

  const viewRollup = useCallback(async () => {
    if (selectedIds.length === 0 || loading) return;
    setLoading(true);
    setError(null);
    try {
      const result = await personaResearchService.rollup(selectedIds);
      setRollup(result);
      if (!result.available) setError(result.message ?? t("persona.research.program.unavailable"));
    } catch {
      setError(t("persona.research.program.failed"));
    } finally {
      setLoading(false);
    }
  }, [selectedIds, loading, t]);

  const runAll = useCallback(async () => {
    if (selectedIds.length === 0 || running) return;
    setRunning(true);
    setError(null);
    try {
      for (const id of selectedIds) {
        await personaResearchService.run(id, false);
      }
      const result = await personaResearchService.rollup(selectedIds);
      setRollup(result);
    } catch {
      setError(t("persona.research.program.runFailed"));
    } finally {
      setRunning(false);
    }
  }, [selectedIds, running, t]);

  return (
    <>
      <BentoHero
        backTo={ROUTES.BENTO_PERSONA_RESEARCH}
        backLabel={t("persona.research.program.back")}
        leading={
          <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-violet-600 to-fuchsia-600 text-white shadow-md">
            <IconStack2 size={24} />
          </span>
        }
        title={t("persona.research.program.title")}
        subtitle={t("persona.research.program.subtitle")}
      />

      <div className="flex flex-col gap-5">
        {/* --- Planner: select studies --- */}
        <BentoFormSection
          icon={IconStack2}
          tone="violet"
          title={t("persona.research.program.planTitle")}
          description={t("persona.research.program.planDesc")}
          trailing={<Badge variant="secondary">{selected.size}</Badge>}
        >
          {(studies ?? []).length === 0 ? (
            <p className="text-sm text-muted-foreground">{t("persona.research.program.noStudies")}</p>
          ) : (
            <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
              {(studies ?? []).map((study) => {
                const isSelected = selected.has(study.id);
                return (
                  <button
                    key={study.id}
                    type="button"
                    onClick={() => toggle(study.id)}
                    aria-pressed={isSelected}
                    className={`bento-tile bento-tile-clickable flex items-center gap-3 rounded-2xl border px-3 py-2.5 text-left backdrop-blur transition-colors ${
                      isSelected ? "border-violet-500/40 bg-violet-500/10" : "border-border/60 bg-card/40 hover:border-violet-500/30"
                    }`}
                  >
                    <span
                      className={`grid size-8 shrink-0 place-items-center rounded-lg text-sm ${
                        isSelected ? "bg-linear-to-br from-violet-600 to-fuchsia-600 text-white" : "bg-muted text-muted-foreground"
                      }`}
                    >
                      {isSelected ? <IconCheck size={16} /> : <IconUsersGroup size={16} />}
                    </span>
                    <div className="min-w-0 flex-1">
                      <div className="truncate text-sm font-medium">{study.name}</div>
                      <div className="text-xs text-muted-foreground">
                        {t("persona.research.participantsCount", { count: study.personaCount })}
                        {study.lastRunAt ? ` · ${t("persona.research.program.run")}` : ` · ${t("persona.research.program.notRun")}`}
                      </div>
                    </div>
                  </button>
                );
              })}
            </div>
          )}
          <div className="mt-3 flex flex-wrap gap-2">
            <Button onClick={viewRollup} disabled={selected.size === 0 || loading} className="gap-2">
              {loading ? <IconLoader2 className="size-4 animate-spin" /> : <IconChartBar className="size-4" />}
              {t("persona.research.program.viewRollup")}
            </Button>
            <Button variant="outline" onClick={runAll} disabled={selected.size === 0 || running} className="gap-2">
              {running ? <IconLoader2 className="size-4 animate-spin" /> : <IconPlayerPlayFilled className="size-4" />}
              {t("persona.research.program.runAll")}
            </Button>
          </div>
          {error && <p className="mt-2 text-sm text-red-600">{error}</p>}
        </BentoFormSection>

        {/* --- Program rollup --- */}
        {rollup?.available && (
          <>
            <BentoFormSection
              icon={IconChartBar}
              tone="indigo"
              title={t("persona.research.program.rollupTitle")}
              description={t("persona.research.program.rollupDesc")}
            >
              <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
                <Stat label={t("persona.research.program.statStudies")} value={rollup.studyCount} />
                <Stat label={t("persona.research.program.statParticipants")} value={rollup.totalParticipants} />
                <Stat label={t("persona.research.program.statInterviews")} value={rollup.totalInterviews} />
                <Stat label={t("persona.research.program.statThemes")} value={rollup.totalDistinctThemes} />
                <Stat label={t("persona.research.program.statShared")} value={rollup.sharedThemeCount} accent />
              </div>

              <div className="mt-4 grid gap-2 lg:grid-cols-2">
                {rollup.studies.map((s) => (
                  <Link
                    key={s.studyId}
                    to={`${ROUTES.BENTO_PERSONA_RESEARCH}/${s.studyId}`}
                    className="bento-tile bento-tile-clickable flex items-center gap-3 rounded-2xl border border-border/60 bg-card/40 px-3 py-2.5 backdrop-blur"
                  >
                    <div className="min-w-0 flex-1">
                      <div className="truncate text-sm font-medium">{s.name}</div>
                      <div className="text-xs text-muted-foreground">
                        {t(`persona.research.protocol.${s.protocol}`)} · {t("persona.research.interviewsCount", { count: s.interviews })}
                      </div>
                    </div>
                    {s.reportAvailable ? (
                      <span
                        className={`rounded-full border px-2 py-0.5 text-[10px] ${
                          s.saturated
                            ? "border-emerald-500/30 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300"
                            : "border-amber-500/30 bg-amber-500/10 text-amber-700 dark:text-amber-300"
                        }`}
                      >
                        {s.saturated
                          ? t("persona.research.adequateAt", { n: s.adequateAtN })
                          : t("persona.research.program.themesN", { count: s.themeCount })}
                      </span>
                    ) : (
                      <span className="rounded-full border border-border/60 bg-card/40 px-2 py-0.5 text-[10px] text-muted-foreground">
                        {t("persona.research.program.notRun")}
                      </span>
                    )}
                  </Link>
                ))}
              </div>
            </BentoFormSection>

            <BentoFormSection
              icon={IconChartBar}
              tone="blue"
              title={t("persona.research.program.themesTitle")}
              description={t("persona.research.program.themesDesc")}
            >
              {rollup.themes.length === 0 ? (
                <p className="text-sm text-muted-foreground">{t("persona.research.program.noThemes")}</p>
              ) : (
                <div className="flex flex-col gap-2">
                  {rollup.themes.map((theme) => {
                    const shared = theme.studyCount >= 2;
                    return (
                      <div
                        key={theme.title}
                        className={`flex items-center gap-3 rounded-2xl border px-3 py-2 backdrop-blur ${
                          shared ? "border-blue-500/30 bg-blue-500/5" : "border-border/60 bg-card/40"
                        }`}
                      >
                        <div className="min-w-0 flex-1">
                          <div className="truncate text-sm font-medium">{theme.title}</div>
                          <div className="truncate text-xs text-muted-foreground">{theme.studyNames.join(" · ")}</div>
                        </div>
                        {shared && (
                          <Badge variant="secondary" className="shrink-0 text-[10px]">
                            {t("persona.research.program.inStudies", { count: theme.studyCount })}
                          </Badge>
                        )}
                      </div>
                    );
                  })}
                </div>
              )}
            </BentoFormSection>
          </>
        )}
      </div>
    </>
  );
}

function Stat({ label, value, accent }: Readonly<{ label: string; value: number; accent?: boolean }>) {
  return (
    <div className={`bento-tile rounded-2xl border p-3 text-center backdrop-blur ${accent ? "border-blue-500/30 bg-blue-500/5" : "border-border/60 bg-card/40"}`}>
      <div className={`text-2xl font-bold ${accent ? "text-blue-600 dark:text-blue-400" : ""}`}>{value}</div>
      <div className="text-xs text-muted-foreground">{label}</div>
    </div>
  );
}
