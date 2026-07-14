import {
  personaResearchService,
  useResearchConceptFit,
  useResearchDrift,
  useResearchGraph,
  useResearchInsights,
  useResearchSaturation,
} from "@/api/queries/persona-research.queries";
import { queryKeys } from "@/api/queries/keys";
import { ROUTES } from "@/app/routes.const";
import { BentoFormSection } from "@/components/bento";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  fitTone,
  type ResearchGraphNode,
  type ResearchReport,
} from "@/models/persona/persona-research.model";
import { useQueryClient } from "@tanstack/react-query";
import {
  IconArrowRight,
  IconChartDots3,
  IconChartLine,
  IconFileTypePdf,
  IconFlask2,
  IconGauge,
  IconLoader2,
  IconQuote,
  IconRefresh,
  IconSparkles,
  IconTargetArrow,
} from "@tabler/icons-react";
import { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";
import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

// --- PDF export (print-to-PDF) ---------------------------------------------

function escapeHtml(value: string): string {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function buildInsightsHtml(
  studyName: string,
  report: ResearchReport,
  labels: { brand: string; footer: string; summary: string; themes: string; recs: string; supported: string },
): string {
  const summaryHtml = report.executiveSummary
    ? `<section class="group"><h2>${escapeHtml(labels.summary)}</h2><p class="body">${escapeHtml(report.executiveSummary)}</p></section>`
    : "";
  const themesHtml = report.themes
    .map((theme) => {
      const quotes = theme.quotes
        .map((q) => `<li><span class="q">“${escapeHtml(q.quote)}”</span> <span class="attr">— ${escapeHtml(q.personaName)}</span></li>`)
        .join("");
      return `<section class="group">
        <h3>${escapeHtml(theme.title)} <span class="prev">${labels.supported}: ${theme.prevalence}</span></h3>
        <p class="body">${escapeHtml(theme.summary)}</p>
        <ul>${quotes}</ul>
      </section>`;
    })
    .join("");
  const recsHtml = report.recommendations.length
    ? `<section class="group"><h2>${escapeHtml(labels.recs)}</h2><ol>${report.recommendations
        .map((r) => `<li>${escapeHtml(r)}</li>`)
        .join("")}</ol></section>`
    : "";

  return `<!doctype html><html><head><meta charset="utf-8"><title>${escapeHtml(studyName)}</title>
  <style>
    @page { margin: 20mm 16mm; }
    * { box-sizing: border-box; }
    body { font-family: -apple-system, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; color: #0f172a; margin: 0; line-height: 1.5; }
    header { border-bottom: 2px solid #7c3aed; padding-bottom: 12px; margin-bottom: 20px; }
    .brand { font-size: 11px; letter-spacing: .12em; text-transform: uppercase; color: #7c3aed; font-weight: 600; }
    h1 { font-size: 22px; margin: 4px 0 2px; }
    h2 { font-size: 15px; margin: 0 0 6px; }
    h3 { font-size: 14px; margin: 0 0 2px; }
    .prev { font-size: 11px; font-weight: 500; color: #7c3aed; }
    .group { margin-bottom: 16px; page-break-inside: avoid; }
    .body { font-size: 12px; color: #334155; margin: 0 0 6px; }
    ul, ol { margin: 4px 0; padding-left: 18px; }
    li { font-size: 12px; margin: 2px 0; }
    .q { font-style: italic; }
    .attr { color: #64748b; }
    footer { margin-top: 24px; border-top: 1px solid #e2e8f0; padding-top: 8px; color: #94a3b8; font-size: 10px; }
  </style></head>
  <body>
    <header>
      <div class="brand">${escapeHtml(labels.brand)}</div>
      <h1>${escapeHtml(studyName)}</h1>
    </header>
    ${summaryHtml}
    <h2>${escapeHtml(labels.themes)}</h2>
    ${themesHtml}
    ${recsHtml}
    <footer>${escapeHtml(labels.footer)}</footer>
  </body></html>`;
}

function openPrintWindow(html: string): void {
  const win = window.open("", "_blank", "noopener,width=900,height=1100");
  if (!win) return;
  win.document.open();
  win.document.write(html);
  win.document.close();
  win.focus();
  win.setTimeout(() => win.print(), 350);
}

// ---------------------------------------------------------------------------

interface Props {
  studyId: string;
  studyName: string;
  isConceptTest: boolean;
  everRun: boolean;
}

/**
 * The Synthetic User Research read surfaces (Block AW / §XLVI.5, T730): the
 * synthesized insights report (by-theme / by-persona lenses + regenerate + PDF),
 * the deterministic saturation gauge, the theme/affinity graph, the continuous-
 * insight drift chart, the concept-fit report (concept-test studies), and the
 * one-click promote-to-eval-dataset bridge. All fail-open: each backend returns
 * an `available` flag with a friendly message when there is nothing to render yet.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
export function ResearchInsightsSection({ studyId, studyName, isConceptTest, everRun }: Readonly<Props>) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();

  const { data: report } = useResearchInsights(studyId, everRun);
  const { data: saturation } = useResearchSaturation(studyId, everRun);
  const { data: graph } = useResearchGraph(studyId, everRun);
  const { data: drift } = useResearchDrift(studyId, everRun);
  const { data: conceptFit } = useResearchConceptFit(studyId, everRun && isConceptTest);

  const [regenerating, setRegenerating] = useState(false);
  const [promoting, setPromoting] = useState(false);
  const [promotedId, setPromotedId] = useState<string | null>(null);

  const regenerate = useCallback(async () => {
    setRegenerating(true);
    try {
      const fresh = await personaResearchService.insights(studyId, true);
      queryClient.setQueryData(queryKeys.personaResearch.insights(studyId), fresh);
    } finally {
      setRegenerating(false);
    }
  }, [studyId, queryClient]);

  const promote = useCallback(async () => {
    setPromoting(true);
    try {
      const dataset = await personaResearchService.promoteToDataset(studyId);
      setPromotedId(dataset.id);
    } finally {
      setPromoting(false);
    }
  }, [studyId]);

  const exportPdf = useCallback(() => {
    if (!report?.available) return;
    openPrintWindow(
      buildInsightsHtml(studyName, report, {
        brand: t("persona.research.reportBrand"),
        footer: t("persona.research.reportFooter"),
        summary: t("persona.research.executiveSummary"),
        themes: t("persona.research.themes"),
        recs: t("persona.research.recommendations"),
        supported: t("persona.research.supportedBy"),
      }),
    );
  }, [report, studyName, t]);

  // Persona → themes edges, for the affinity graph lens.
  const themesByPersona = useMemo(() => {
    if (!graph?.available) return [];
    const nodeById = new Map<string, ResearchGraphNode>(graph.nodes.map((n) => [n.id, n]));
    const personas = graph.nodes.filter((n) => n.type === "PERSONA");
    return personas
      .map((persona) => ({
        persona,
        themes: graph.edges
          .filter((e) => e.source === persona.id)
          .map((e) => ({ node: nodeById.get(e.target), weight: e.weight }))
          .filter((x): x is { node: ResearchGraphNode; weight: number } => Boolean(x.node))
          .sort((a, b) => b.weight - a.weight),
      }))
      .filter((x) => x.themes.length > 0);
  }, [graph]);

  const driftData = useMemo(
    () =>
      (drift?.points ?? []).map((p, i) => ({
        run: i + 1,
        themes: p.totalUniqueThemes,
        interviews: p.interviewCount,
      })),
    [drift],
  );

  if (!everRun) return null;

  return (
    <>
      {/* --- Insights report --- */}
      <BentoFormSection
        icon={IconSparkles}
        tone="violet"
        title={t("persona.research.reportTitle")}
        description={t("persona.research.reportDesc")}
        trailing={
          report?.available ? (
            <div className="flex items-center gap-2">
              {report.canRegenerate && (
                <Button type="button" variant="outline" size="sm" className="gap-1.5" onClick={regenerate} disabled={regenerating}>
                  {regenerating ? <IconLoader2 className="size-4 animate-spin" /> : <IconRefresh className="size-4" />}
                  {t("persona.research.regenerate")}
                </Button>
              )}
              <Button type="button" variant="outline" size="sm" className="gap-1.5" onClick={exportPdf}>
                <IconFileTypePdf className="size-4 text-rose-600" />
                {t("persona.research.exportPdf")}
              </Button>
            </div>
          ) : undefined
        }
      >
        {!report?.available ? (
          <p className="text-sm text-muted-foreground">
            {report?.error ?? t("persona.research.reportUnavailable")}
          </p>
        ) : (
          <div className="flex flex-col gap-4">
            {report.executiveSummary && (
              <div className="rounded-2xl border border-violet-500/25 bg-violet-500/5 p-4">
                <h4 className="mb-1 text-xs font-semibold uppercase tracking-wide text-violet-700 dark:text-violet-300">
                  {t("persona.research.executiveSummary")}
                </h4>
                <p className="whitespace-pre-wrap text-sm">{report.executiveSummary}</p>
              </div>
            )}

            <Tabs defaultValue="byTheme">
              <TabsList>
                <TabsTrigger value="byTheme">{t("persona.research.tabByTheme")}</TabsTrigger>
                <TabsTrigger value="byPersona">{t("persona.research.tabByPersona")}</TabsTrigger>
              </TabsList>

              <TabsContent value="byTheme" className="mt-4">
                <div className="flex flex-col gap-3">
                  {report.themes.map((theme) => (
                    <div key={theme.title} className="bento-tile bento-glass rounded-2xl p-4">
                      <div className="mb-1 flex items-center gap-2">
                        <h4 className="text-sm font-semibold">{theme.title}</h4>
                        <Badge variant="secondary" className="text-[10px]">
                          {t("persona.research.supportedByN", { count: theme.prevalence })}
                        </Badge>
                      </div>
                      <p className="mb-2 text-sm text-muted-foreground">{theme.summary}</p>
                      <ul className="flex flex-col gap-1.5">
                        {theme.quotes.map((q, i) => (
                          <li key={`${q.personaId}-${i}`} className="flex items-start gap-2 text-sm">
                            <IconQuote size={14} className="mt-0.5 shrink-0 text-violet-500" />
                            <span>
                              <span className="italic">“{q.quote}”</span>{" "}
                              <span className={q.resolved ? "text-muted-foreground" : "text-amber-600 dark:text-amber-400"}>
                                — {q.personaName}
                                {!q.resolved && ` (${t("persona.research.unverified")})`}
                              </span>
                            </span>
                          </li>
                        ))}
                      </ul>
                    </div>
                  ))}
                  {report.themes.length === 0 && (
                    <p className="text-sm text-muted-foreground">{t("persona.research.noThemes")}</p>
                  )}
                </div>
              </TabsContent>

              <TabsContent value="byPersona" className="mt-4">
                <div className="grid gap-3 lg:grid-cols-2">
                  {report.byPersona.map((lens) => (
                    <div key={lens.personaId} className="bento-tile bento-glass rounded-2xl p-4">
                      <h4 className="mb-2 text-sm font-semibold">{lens.personaName}</h4>
                      <ul className="flex flex-col gap-2">
                        {lens.quotes.map((q, i) => (
                          <li key={i} className="text-sm">
                            <Badge variant="outline" className="mb-1 text-[10px]">{q.theme}</Badge>
                            <p className="italic text-muted-foreground">“{q.quote}”</p>
                          </li>
                        ))}
                      </ul>
                    </div>
                  ))}
                  {report.byPersona.length === 0 && (
                    <p className="text-sm text-muted-foreground">{t("persona.research.noThemes")}</p>
                  )}
                </div>
              </TabsContent>
            </Tabs>

            {report.recommendations.length > 0 && (
              <div>
                <h4 className="mb-2 text-sm font-semibold">{t("persona.research.recommendations")}</h4>
                <ol className="flex list-decimal flex-col gap-1.5 pl-5">
                  {report.recommendations.map((rec, i) => (
                    <li key={i} className="text-sm">{rec}</li>
                  ))}
                </ol>
              </div>
            )}
          </div>
        )}
      </BentoFormSection>

      {/* --- Saturation --- */}
      <BentoFormSection
        icon={IconGauge}
        tone="emerald"
        title={t("persona.research.saturationTitle")}
        description={t("persona.research.saturationDesc")}
      >
        {!saturation?.available ? (
          <p className="text-sm text-muted-foreground">
            {saturation?.message ?? t("persona.research.saturationUnavailable")}
          </p>
        ) : (
          <div className="flex flex-col gap-4">
            <div
              className={`flex items-center gap-4 rounded-2xl border p-4 ${
                saturation.saturated
                  ? "border-emerald-500/30 bg-emerald-500/10"
                  : "border-amber-500/30 bg-amber-500/10"
              }`}
            >
              <IconGauge
                size={28}
                className={saturation.saturated ? "text-emerald-600" : "text-amber-600"}
              />
              <div className="text-sm">
                <div className="font-medium">
                  {saturation.saturated
                    ? t("persona.research.adequateAt", { n: saturation.adequateAtN })
                    : t("persona.research.notSaturated")}
                </div>
                <div className="text-muted-foreground">
                  {t("persona.research.uniqueThemes", {
                    themes: saturation.totalUniqueThemes,
                    personas: saturation.personaCount,
                  })}
                </div>
              </div>
            </div>
            <div className="flex flex-col gap-1.5">
              {saturation.steps.map((step) => (
                <div key={step.personaId} className="flex items-center gap-3">
                  <span className="w-40 shrink-0 truncate text-xs" title={step.personaName}>
                    {step.index}. {step.personaName}
                  </span>
                  <span className="relative h-2 flex-1 overflow-hidden rounded-full bg-muted">
                    <span
                      className="absolute inset-y-0 left-0 rounded-full bg-emerald-500"
                      style={{ width: `${Math.min(100, Math.round(step.noveltyRatio * 100))}%` }}
                    />
                  </span>
                  <span className="w-24 shrink-0 text-right text-xs text-muted-foreground">
                    {t("persona.research.newThemes", { count: step.newThemes })}
                  </span>
                </div>
              ))}
            </div>
          </div>
        )}
      </BentoFormSection>

      {/* --- Concept fit (concept-test studies only) --- */}
      {isConceptTest && (
        <BentoFormSection
          icon={IconTargetArrow}
          tone="rose"
          title={t("persona.research.conceptFitTitle")}
          description={t("persona.research.conceptFitDesc")}
        >
          {!conceptFit?.available ? (
            <p className="text-sm text-muted-foreground">
              {conceptFit?.error ?? t("persona.research.conceptFitUnavailable")}
            </p>
          ) : (
            <div className="flex flex-col gap-3">
              <div className="text-sm">
                {t("persona.research.averageFit")}{" "}
                <span className={`font-semibold ${fitTone(conceptFit.averageFitScore).text}`}>
                  {Math.round(conceptFit.averageFitScore)}%
                </span>
              </div>
              {conceptFit.personas.map((entry) => {
                const tone = fitTone(entry.result.fitScore);
                return (
                  <div key={entry.personaId} className="flex items-center gap-3">
                    <span className="w-40 shrink-0 truncate text-xs" title={entry.personaName}>
                      {entry.personaName}
                    </span>
                    <span className="relative h-2 flex-1 overflow-hidden rounded-full bg-muted">
                      <span className={`absolute inset-y-0 left-0 rounded-full ${tone.fill}`} style={{ width: `${entry.result.fitScore}%` }} />
                    </span>
                    <span className={`w-10 shrink-0 text-right text-xs font-semibold ${tone.text}`}>
                      {Math.round(entry.result.fitScore)}%
                    </span>
                  </div>
                );
              })}
            </div>
          )}
        </BentoFormSection>
      )}

      {/* --- Theme / affinity graph --- */}
      <BentoFormSection
        icon={IconChartDots3}
        tone="blue"
        title={t("persona.research.graphTitle")}
        description={t("persona.research.graphDesc")}
      >
        {!graph?.available ? (
          <p className="text-sm text-muted-foreground">
            {graph?.message ?? t("persona.research.graphUnavailable")}
          </p>
        ) : (
          <div className="grid gap-3 lg:grid-cols-2">
            {themesByPersona.map(({ persona, themes }) => (
              <div key={persona.id} className="bento-tile bento-glass rounded-2xl p-4">
                <div className="mb-2 text-sm font-semibold">{persona.label}</div>
                <div className="flex flex-wrap gap-1.5">
                  {themes.map(({ node, weight }) => (
                    <span
                      key={node.id}
                      className="inline-flex items-center gap-1 rounded-full border border-blue-500/25 bg-blue-500/10 px-2 py-0.5 text-xs text-blue-700 dark:text-blue-300"
                    >
                      {node.label}
                      <span className="text-blue-500/70">×{weight}</span>
                    </span>
                  ))}
                </div>
              </div>
            ))}
          </div>
        )}
      </BentoFormSection>

      {/* --- Continuous-insight drift --- */}
      <BentoFormSection
        icon={IconChartLine}
        tone="amber"
        title={t("persona.research.driftTitle")}
        description={t("persona.research.driftDesc")}
      >
        {driftData.length < 2 ? (
          <p className="text-sm text-muted-foreground">{t("persona.research.driftUnavailable")}</p>
        ) : (
          <div className="h-56 w-full">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={driftData} margin={{ top: 8, right: 12, bottom: 4, left: -12 }}>
                <CartesianGrid strokeDasharray="3 3" className="stroke-border/40" />
                <XAxis dataKey="run" fontSize={11} tickLine={false} />
                <YAxis fontSize={11} tickLine={false} allowDecimals={false} />
                <Tooltip />
                <Line type="monotone" dataKey="themes" stroke="#f59e0b" strokeWidth={2} dot />
                <Line type="monotone" dataKey="interviews" stroke="#6366f1" strokeWidth={2} dot />
              </LineChart>
            </ResponsiveContainer>
          </div>
        )}
      </BentoFormSection>

      {/* --- Eval-dataset bridge (T725) --- */}
      <BentoFormSection
        icon={IconFlask2}
        tone="indigo"
        title={t("persona.research.evalBridgeTitle")}
        description={t("persona.research.evalBridgeDesc")}
      >
        {promotedId ? (
          <div className="flex flex-wrap items-center gap-3">
            <p className="text-sm text-emerald-700 dark:text-emerald-300">
              {t("persona.research.promoted")}
            </p>
            <Button asChild variant="outline" size="sm" className="gap-1.5">
              <Link to={ROUTES.BENTO_EVAL}>
                {t("persona.research.openEvalStudio")}
                <IconArrowRight className="size-4" />
              </Link>
            </Button>
          </div>
        ) : (
          <Button type="button" variant="outline" className="gap-2" onClick={promote} disabled={promoting}>
            {promoting ? <IconLoader2 className="size-4 animate-spin" /> : <IconFlask2 className="size-4" />}
            {t("persona.research.promoteToDataset")}
          </Button>
        )}
      </BentoFormSection>
    </>
  );
}
