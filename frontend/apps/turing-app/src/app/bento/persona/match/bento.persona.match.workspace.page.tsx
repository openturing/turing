import {
  invalidateMatchProject,
  personaMatchService,
  useCreateMatchProject,
  useDeleteMatchProject,
  useMatchMatrix,
  useMatchProject,
  useUpdateMatchProject,
} from "@/api/queries/persona-match.queries";
import { useLlmInstances } from "@/api/queries/llm-instance.queries";
import { usePersonas } from "@/api/queries/persona.queries";
import { ROUTES } from "@/app/routes.const";
import { BentoActionsMenu, BentoFormSection, BentoHero } from "@/components/bento";
import { PersonaSelectGrid } from "@/components/persona/persona-select-grid";
import { SnDocumentPicker, type PickedSnDocument } from "@/components/sn/sn-document-picker";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Progress } from "@/components/ui/progress";
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
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  buildMatrixMap,
  cellKey,
  fitTone,
  projectAvgFit,
  rankByContent,
  rankByPersona,
  SCHEDULES,
  type MatchCell,
  type MatchMisfit,
  type MatchPersonaKind,
  type MatchPersonaRef,
  type MatchProjectRequest,
  type MatchSchedule,
  type MatchSource,
  type MatchSourceType,
} from "@/models/persona/persona-match.model";
import { useQueryClient } from "@tanstack/react-query";
import {
  IconBulb,
  IconCalendarRepeat,
  IconCheck,
  IconFile,
  IconFileTypePdf,
  IconLink,
  IconLoader2,
  IconPlayerPlayFilled,
  IconPlus,
  IconServerBolt,
  IconSparkles,
  IconTargetArrow,
  IconTrash,
  IconUpload,
  IconUserCircle,
  IconWand,
  IconWorldSearch,
} from "@tabler/icons-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";

const SOURCE_TYPE_ICON: Record<MatchSourceType, typeof IconLink> = {
  URL: IconLink,
  SN_DOC: IconWorldSearch,
  ASSET: IconFile,
};

// --- PDF export (print-to-PDF) ---------------------------------------------

interface ReportRow {
  label: string;
  score: number;
  /** Pre-resolved "Readability NN% · mode" line (i18n done at call site). */
  meta: string;
  summary?: string | null;
  fits: string[];
  misfits: MatchMisfit[];
}
interface ReportGroup {
  head: string;
  sub: string;
  rows: ReportRow[];
}
/** Section labels resolved once at the call site (buildReportHtml is pure). */
interface ReportLabels {
  fits: string;
  adjust: string;
  nothingToAdjust: string;
}

function escapeHtml(value: string): string {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function fitHex(score: number): string {
  if (score >= 75) return "#10b981";
  if (score >= 50) return "#f59e0b";
  return "#f43f5e";
}

function buildRowHtml(r: ReportRow, labels: ReportLabels): string {
  const color = fitHex(r.score);
  const summaryHtml = r.summary
    ? `<p class="summary">${escapeHtml(r.summary)}</p>`
    : "";
  const fitsHtml = r.fits.length
    ? `<div class="subhead fit">${escapeHtml(labels.fits)}</div>
       <ul class="fits">${r.fits.map((f) => `<li>${escapeHtml(f)}</li>`).join("")}</ul>`
    : "";
  const misfitsHtml = r.misfits.length
    ? `<div class="subhead adjust">${escapeHtml(labels.adjust)}</div>
       ${r.misfits
         .map(
           (m) => `<div class="misfit">
             <div class="span">${escapeHtml(m.span)}</div>
             <div class="reason">${escapeHtml(m.reason)}</div>
             <div class="sugg">→ ${escapeHtml(m.suggestion)}</div>
           </div>`,
         )
         .join("")}`
    : `<p class="none">${escapeHtml(labels.nothingToAdjust)}</p>`;

  return `<article class="item">
    <div class="item-head">
      <span class="item-label">${escapeHtml(r.label)}</span>
      <span class="item-score" style="color:${color}">${Math.round(r.score)}%</span>
    </div>
    <span class="bar"><span class="fill" style="width:${r.score}%;background:${color}"></span></span>
    <p class="meta">${escapeHtml(r.meta)}</p>
    ${summaryHtml}
    ${fitsHtml}
    ${misfitsHtml}
  </article>`;
}

function buildReportHtml(
  title: string,
  subtitle: string,
  groups: ReportGroup[],
  brand: string,
  footer: string,
  labels: ReportLabels,
): string {
  const groupsHtml = groups
    .map((g) => {
      const rowsHtml = g.rows.map((r) => buildRowHtml(r, labels)).join("");
      return `<section class="group">
        <h2>${escapeHtml(g.head)}</h2>
        <p class="sub">${escapeHtml(g.sub)}</p>
        ${rowsHtml}
      </section>`;
    })
    .join("");

  return `<!doctype html><html><head><meta charset="utf-8"><title>${escapeHtml(title)}</title>
  <style>
    @page { margin: 20mm 16mm; }
    * { box-sizing: border-box; }
    body { font-family: -apple-system, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; color: #0f172a; margin: 0; }
    header { border-bottom: 2px solid #4f46e5; padding-bottom: 12px; margin-bottom: 20px; }
    .brand { font-size: 11px; letter-spacing: .12em; text-transform: uppercase; color: #6366f1; font-weight: 600; }
    h1 { font-size: 22px; margin: 4px 0 2px; }
    .subtitle { color: #64748b; font-size: 13px; margin: 0; }
    .group { margin-bottom: 22px; page-break-inside: avoid; }
    .group h2 { font-size: 16px; margin: 0 0 2px; padding-bottom: 4px; border-bottom: 1px solid #e2e8f0; }
    .group .sub { color: #64748b; font-size: 12px; margin: 6px 0 10px; }
    .item { border: 1px solid #e2e8f0; border-radius: 12px; padding: 12px 14px; margin: 8px 0; page-break-inside: avoid; }
    .item-head { display: flex; align-items: baseline; gap: 10px; }
    .item-label { flex: 1; font-size: 13px; font-weight: 600; }
    .item-score { font-size: 16px; font-weight: 800; }
    .bar { display: block; height: 7px; background: #e2e8f0; border-radius: 999px; overflow: hidden; margin: 6px 0 0; }
    .fill { display: block; height: 100%; border-radius: 999px; }
    .meta { color: #64748b; font-size: 11px; margin: 6px 0 0; }
    .summary { font-size: 12px; color: #334155; margin: 8px 0 0; line-height: 1.45; }
    .subhead { font-size: 10px; font-weight: 700; text-transform: uppercase; letter-spacing: .06em; margin: 10px 0 4px; }
    .subhead.fit { color: #059669; }
    .subhead.adjust { color: #b45309; }
    .fits { margin: 0; padding-left: 16px; }
    .fits li { font-size: 12px; margin: 2px 0; line-height: 1.4; }
    .misfit { border-left: 3px solid #f59e0b; background: #fffbeb; padding: 6px 10px; border-radius: 0 6px 6px 0; margin: 5px 0; }
    .misfit .span { font-size: 12px; font-weight: 600; }
    .misfit .reason { font-size: 11px; color: #64748b; margin-top: 2px; line-height: 1.4; }
    .misfit .sugg { font-size: 11px; color: #059669; margin-top: 4px; line-height: 1.4; }
    .none { color: #94a3b8; font-size: 12px; margin: 8px 0 0; }
    footer { margin-top: 24px; border-top: 1px solid #e2e8f0; padding-top: 8px; color: #94a3b8; font-size: 10px; }
  </style></head>
  <body>
    <header>
      <div class="brand">${escapeHtml(brand)}</div>
      <h1>${escapeHtml(title)}</h1>
      <p class="subtitle">${escapeHtml(subtitle)}</p>
    </header>
    ${groupsHtml}
    <footer>${escapeHtml(footer)}</footer>
  </body></html>`;
}

function openPrintWindow(html: string): void {
  // NOTE: do NOT pass `noopener` here — with that feature `window.open`
  // returns null by spec, so we'd never get the handle needed to write the
  // report into the child document (the window would stay blank).
  const win = window.open("", "_blank", "width=900,height=1100");
  if (!win) return;
  win.document.open();
  win.document.write(html);
  win.document.close();
  win.focus();
  win.setTimeout(() => win.print(), 350);
}

interface ActiveCell {
  source: MatchSource;
  persona: MatchPersonaRef;
  cell: MatchCell;
}

/**
 * Persona Match — project workspace (Block AT / §XLIII, T701–T702). Contents
 * (URL / Indexed / File) + personas → an N×N fit matrix with three lenses
 * (Matrix / By content / By persona) and a per-cell drill-in. "Run analysis"
 * streams the real runner (T698) over SSE, filling the heatmap cell-by-cell.
 * Fully localized via the `persona.match.*` i18n namespace.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
export default function BentoPersonaMatchWorkspacePage() {
  const { projectId } = useParams();
  if (projectId === "new") {
    return <CreateProjectCard />;
  }
  return <ProjectWorkspace projectId={projectId as string} />;
}

// ---------------------------------------------------------------------------
// New-project create card.
// ---------------------------------------------------------------------------

function CreateProjectCard() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const create = useCreateMatchProject();
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");

  const submit = () => {
    const trimmed = name.trim();
    if (!trimmed) return;
    create.mutate(
      { name: trimmed, description, enabled: true, schedule: "MANUAL" },
      { onSuccess: (project) => navigate(`${ROUTES.BENTO_PERSONA_MATCH}/${project.id}`) },
    );
  };

  return (
    <>
      <BentoHero
        backTo={ROUTES.BENTO_PERSONA_MATCH}
        backLabel={t("persona.match.back")}
        leading={
          <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-indigo-600 to-fuchsia-600 text-white shadow-md">
            <IconTargetArrow size={24} />
          </span>
        }
        title={t("persona.match.newTitle")}
        subtitle={t("persona.match.newHeroSubtitle")}
      />
      <BentoFormSection
        icon={IconSparkles}
        tone="indigo"
        title={t("persona.match.projectSection")}
        description={t("persona.match.projectSectionDesc")}
      >
        <div className="flex flex-col gap-3">
          <label className="flex flex-col gap-1.5">
            <span className="text-sm font-medium">{t("persona.match.nameLabel")}</span>
            <Input
              value={name}
              onChange={(e) => setName(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && submit()}
              placeholder={t("persona.match.namePlaceholder")}
              autoFocus
            />
          </label>
          <label className="flex flex-col gap-1.5">
            <span className="text-sm font-medium">{t("persona.match.descriptionLabel")}</span>
            <Input
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder={t("persona.match.descriptionPlaceholder")}
            />
          </label>
          <div>
            <Button onClick={submit} disabled={!name.trim() || create.isPending} className="gap-2">
              {create.isPending ? <IconLoader2 className="size-4 animate-spin" /> : <IconPlus className="size-4" />}
              {t("persona.match.createButton")}
            </Button>
          </div>
        </div>
      </BentoFormSection>
    </>
  );
}

// ---------------------------------------------------------------------------
// Existing-project workspace.
// ---------------------------------------------------------------------------

function toPersonaRef(p: { id: string; name: string; personaKind?: string | null }): MatchPersonaRef {
  return { id: p.id, name: p.name, kind: (p.personaKind ?? "SPEAKER") as MatchPersonaKind };
}

function ProjectWorkspace({ projectId }: Readonly<{ projectId: string }>) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const { data: project } = useMatchProject(projectId);
  const { data: matrixData } = useMatchMatrix(projectId);
  const { data: personaPool } = usePersonas();
  const { data: llmInstances } = useLlmInstances();

  const updateProject = useUpdateMatchProject(projectId);
  const deleteProject = useDeleteMatchProject();

  const [cells, setCells] = useState<Map<string, MatchCell>>(new Map());
  const [revealed, setRevealed] = useState<Set<string>>(new Set());
  const [running, setRunning] = useState(false);
  const [runError, setRunError] = useState<string | null>(null);
  const [runProgress, setRunProgress] = useState<{ done: number; total: number }>({ done: 0, total: 0 });
  const [activeCell, setActiveCell] = useState<ActiveCell | null>(null);

  const [addType, setAddType] = useState<MatchSourceType>("URL");
  const [addValue, setAddValue] = useState("");
  const [pickerOpen, setPickerOpen] = useState(false);
  const [busy, setBusy] = useState(false);

  const fileRef = useRef<HTMLInputElement | null>(null);
  const abortRef = useRef<AbortController | null>(null);

  const [name, setName] = useState("");
  const [description, setDescription] = useState("");

  const sources = useMemo(() => project?.sources ?? [], [project]);
  const personas = useMemo(() => project?.personas ?? [], [project]);
  const everRun = Boolean(project?.lastRunAt);

  useEffect(() => {
    if (!project) return;
    setName(project.name);
    setDescription(project.description ?? "");
  }, [project]);

  useEffect(() => {
    if (!matrixData) return;
    const map = buildMatrixMap(matrixData.cells);
    setCells(map);
    if (!running) setRevealed(new Set(map.keys()));
  }, [matrixData, running]);

  const matrix = cells;
  const total = sources.length * personas.length;

  const avgFit = useMemo(
    () => (everRun && total > 0 ? projectAvgFit(sources, personas, matrix) : null),
    [everRun, total, sources, personas, matrix],
  );
  const byContent = useMemo(() => rankByContent(sources, personas, matrix), [sources, personas, matrix]);
  const byPersona = useMemo(() => rankByPersona(sources, personas, matrix), [sources, personas, matrix]);
  const selectedPersonaIds = useMemo(() => personas.map((p) => p.id), [personas]);

  const refresh = useCallback(
    () => invalidateMatchProject(queryClient, projectId),
    [queryClient, projectId],
  );

  const saveProject = useCallback(
    (patch: Partial<MatchProjectRequest>) => {
      if (!project) return;
      updateProject.mutate({
        name: patch.name ?? name,
        description: patch.description ?? description,
        enabled: project.enabled,
        schedule: patch.schedule ?? project.schedule,
        llmInstanceId: patch.llmInstanceId ?? project.llmInstanceId,
      });
    },
    [project, updateProject, name, description],
  );

  const runAnalysis = useCallback(() => {
    if (total === 0 || running) return;
    abortRef.current?.abort();
    const ctrl = new AbortController();
    abortRef.current = ctrl;
    setRunning(true);
    setRunError(null);
    setRevealed(new Set());
    setRunProgress({ done: 0, total });

    personaMatchService.runStream(
      projectId,
      false,
      {
        onEvent: (event) => {
          if (event.type === "STARTED") {
            setRunProgress({ done: 0, total: event.total });
          } else if (event.type === "CELL" && event.cell) {
            const c = event.cell;
            const key = cellKey(c.sourceId, c.personaId);
            setCells((prev) => new Map(prev).set(key, c));
            setRevealed((prev) => new Set(prev).add(key));
            setRunProgress({ done: event.completed, total: event.total });
          }
        },
        onDone: () => {
          setRunning(false);
          refresh();
        },
        onError: (err) => {
          setRunning(false);
          setRunError(err instanceof Error ? err.message : t("persona.match.runError"));
        },
      },
      ctrl.signal,
    );
  }, [projectId, total, running, refresh, t]);

  useEffect(() => () => abortRef.current?.abort(), []);

  const addUrlSource = useCallback(async () => {
    const value = addValue.trim();
    if (!value || busy) return;
    setBusy(true);
    try {
      await personaMatchService.addSource(projectId, { type: addType, ref: value, sourceName: value });
      setAddValue("");
      refresh();
    } finally {
      setBusy(false);
    }
  }, [addValue, addType, busy, projectId, refresh]);

  const uploadFile = useCallback(
    async (file: File) => {
      setBusy(true);
      try {
        await personaMatchService.uploadSource(projectId, file);
        refresh();
      } finally {
        setBusy(false);
      }
    },
    [projectId, refresh],
  );

  const pickDoc = useCallback(
    async (doc: PickedSnDocument) => {
      setBusy(true);
      try {
        await personaMatchService.addSource(projectId, {
          type: "SN_DOC",
          ref: doc.referenceId,
          sourceName: doc.title,
          siteName: doc.siteName,
        });
        refresh();
      } finally {
        setBusy(false);
      }
    },
    [projectId, refresh],
  );

  const removeSource = useCallback(
    async (sourceId: string) => {
      await personaMatchService.deleteSource(projectId, sourceId);
      refresh();
    },
    [projectId, refresh],
  );

  const changePersonas = useCallback(
    async (ids: string[]) => {
      await personaMatchService.setPersonas(projectId, ids);
      refresh();
    },
    [projectId, refresh],
  );

  const handleExport = useCallback(
    (kind: "content" | "persona") => {
      const brand = t("persona.match.reportBrand");
      const footer = t("persona.match.reportFooter");
      const labels: ReportLabels = {
        fits: t("persona.match.whatFits"),
        adjust: t("persona.match.whatToAdjust"),
        nothingToAdjust: t("persona.match.nothingToAdjust"),
      };
      // Pre-resolve the per-cell "Readability NN% · mode" line so buildReportHtml
      // can stay pure (no `t`). Mirrors the CellSheet drill-in exactly.
      const cellMeta = (cell: MatchCell): string =>
        t("persona.match.readabilityLine", {
          score: Math.round(cell.readability),
          mode: cell.llmUsed
            ? t("persona.match.evaluatedWithLlm")
            : t("persona.match.heuristicOnly"),
        });
      if (kind === "content") {
        const groups: ReportGroup[] = byContent.map((g) => ({
          head: g.source.name,
          sub: `${t(`persona.match.sourceType.${g.source.type}`)} · ${t("persona.match.reportAvgFit", { avg: g.avg })}`,
          rows: g.ranked.map((r) => ({
            label: r.persona.name,
            score: r.cell.fitScore,
            meta: cellMeta(r.cell),
            summary: r.cell.summary,
            fits: r.cell.fits,
            misfits: r.cell.misfits,
          })),
        }));
        openPrintWindow(
          buildReportHtml(
            t("persona.match.reportByContentTitle", { name }),
            t("persona.match.reportByContentSub"),
            groups,
            brand,
            footer,
            labels,
          ),
        );
      } else {
        const groups: ReportGroup[] = byPersona.map((g) => ({
          head: g.persona.name,
          sub: `${t(`persona.match.kind.${g.persona.kind}`)} · ${t("persona.match.reportAvgFit", { avg: g.avg })}`,
          rows: g.ranked.map((r) => ({
            label: r.source.name,
            score: r.cell.fitScore,
            meta: cellMeta(r.cell),
            summary: r.cell.summary,
            fits: r.cell.fits,
            misfits: r.cell.misfits,
          })),
        }));
        openPrintWindow(
          buildReportHtml(
            t("persona.match.reportByPersonaTitle", { name }),
            t("persona.match.reportByPersonaSub"),
            groups,
            brand,
            footer,
            labels,
          ),
        );
      }
    },
    [byContent, byPersona, name, t],
  );

  const removeProject = useCallback(() => {
    deleteProject.mutate(projectId, { onSuccess: () => navigate(ROUTES.BENTO_PERSONA_MATCH) });
  }, [deleteProject, projectId, navigate]);

  const canRun = total > 0 && !running;
  const pool = useMemo(() => (personaPool ?? []).map(toPersonaRef), [personaPool]);

  let runLabel = t("persona.match.run");
  if (running) runLabel = t("persona.match.running");
  else if (everRun) runLabel = t("persona.match.rerun");

  return (
    <>
      <BentoHero
        backTo={ROUTES.BENTO_PERSONA_MATCH}
        backLabel={t("persona.match.back")}
        leading={
          <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-indigo-600 to-fuchsia-600 text-white shadow-md">
            <IconTargetArrow size={24} />
          </span>
        }
        title={name || t("persona.match.defaultTitle")}
        subtitle={description || t("persona.match.defaultSubtitle")}
        trailing={
          <div className="flex items-center gap-2">
            {avgFit !== null && <FitBadge score={avgFit} prefix={t("persona.match.avgFit")} />}
            <Button onClick={runAnalysis} disabled={!canRun} className="gap-2">
              {running ? <IconLoader2 className="size-4 animate-spin" /> : <IconPlayerPlayFilled className="size-4" />}
              {runLabel}
            </Button>
            <BentoActionsMenu
              actions={[{
                label: t("persona.match.deleteProject"),
                icon: IconTrash,
                tone: "destructive",
                onSelect: removeProject,
              }]}
            />
          </div>
        }
      />

      <div className="flex flex-col gap-5">
        {/* --- Contents --- */}
        <BentoFormSection
          icon={IconServerBolt}
          tone="blue"
          title={t("persona.match.contentsTitle")}
          description={t("persona.match.contentsDesc")}
          trailing={<Badge variant="secondary">{sources.length}</Badge>}
        >
          <div className="flex flex-col gap-2">
            {sources.map((source) => {
              const Icon = SOURCE_TYPE_ICON[source.type];
              const failed = source.status === "FAILED";
              return (
                <div
                  key={source.id}
                  className="bento-tile flex items-center gap-3 rounded-2xl border border-border/60 bg-card/40 px-3 py-2.5 backdrop-blur"
                >
                  <span className="grid size-9 shrink-0 place-items-center rounded-xl bg-blue-500/10 text-blue-600 dark:text-blue-300">
                    <Icon size={18} />
                  </span>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <span className="truncate text-sm font-medium">{source.name}</span>
                      <Badge variant="outline" className="shrink-0 text-[10px]">
                        {t(`persona.match.sourceType.${source.type}`)}
                      </Badge>
                      {failed && (
                        <Badge variant="destructive" className="shrink-0 text-[10px]">
                          {t("persona.match.failedBadge")}
                        </Badge>
                      )}
                    </div>
                    <p className="truncate text-xs text-muted-foreground">{failed ? source.error : source.ref}</p>
                  </div>
                  {source.charCount > 0 && (
                    <span className="hidden shrink-0 text-xs text-muted-foreground sm:block">
                      {t("persona.match.charCount", { count: source.charCount })}
                    </span>
                  )}
                  <Button
                    type="button"
                    size="icon"
                    variant="ghost"
                    aria-label={t("persona.match.removeContent")}
                    onClick={() => removeSource(source.id)}
                  >
                    <IconTrash className="size-4 text-red-600" />
                  </Button>
                </div>
              );
            })}
            {sources.length === 0 && (
              <p className="text-sm text-muted-foreground">{t("persona.match.noContents")}</p>
            )}
          </div>

          <div className="flex flex-col gap-2 sm:flex-row">
            <Select value={addType} onValueChange={(v) => setAddType(v as MatchSourceType)}>
              <SelectTrigger className="sm:w-40">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="URL">{t("persona.match.sourceType.URL")}</SelectItem>
                <SelectItem value="SN_DOC">{t("persona.match.sourceType.SN_DOC")}</SelectItem>
                <SelectItem value="ASSET">{t("persona.match.sourceType.ASSET")}</SelectItem>
              </SelectContent>
            </Select>
            {addType === "SN_DOC" && (
              <Button type="button" variant="outline" onClick={() => setPickerOpen(true)} className="flex-1 justify-center gap-1.5" disabled={busy}>
                <IconWorldSearch className="size-4" />
                {t("persona.match.searchIndexed")}
              </Button>
            )}
            {addType === "ASSET" && (
              <>
                <input
                  ref={fileRef}
                  type="file"
                  className="hidden"
                  onChange={(e) => {
                    const f = e.target.files?.[0];
                    if (f) uploadFile(f);
                    e.target.value = "";
                  }}
                />
                <Button type="button" variant="outline" onClick={() => fileRef.current?.click()} className="flex-1 justify-center gap-1.5" disabled={busy}>
                  {busy ? <IconLoader2 className="size-4 animate-spin" /> : <IconUpload className="size-4" />}
                  {t("persona.match.uploadFile")}
                </Button>
              </>
            )}
            {addType === "URL" && (
              <>
                <Input
                  value={addValue}
                  onChange={(e) => setAddValue(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter") {
                      e.preventDefault();
                      addUrlSource();
                    }
                  }}
                  placeholder={t("persona.match.urlPlaceholder")}
                  className="flex-1"
                />
                <Button type="button" variant="outline" onClick={addUrlSource} className="gap-1.5" disabled={busy}>
                  {busy ? <IconLoader2 className="size-4 animate-spin" /> : <IconPlus className="size-4" />}
                  {t("persona.match.addButton")}
                </Button>
              </>
            )}
          </div>
        </BentoFormSection>

        {/* --- Personas --- */}
        <BentoFormSection
          icon={IconUserCircle}
          tone="rose"
          title={t("persona.match.personas")}
          description={t("persona.match.personasDesc")}
          trailing={<Badge variant="secondary">{personas.length}</Badge>}
        >
          <PersonaSelectGrid
            personas={pool.map((p) => ({
              id: p.id,
              name: p.name,
              subtitle: t(`persona.match.kind.${p.kind}`),
            }))}
            selectedIds={selectedPersonaIds}
            onChange={changePersonas}
            emptyText={t("persona.match.noPersonas")}
          />
        </BentoFormSection>

        {/* --- Project settings --- */}
        <BentoFormSection
          icon={IconCalendarRepeat}
          tone="amber"
          title={t("persona.match.settingsTitle")}
          description={t("persona.match.settingsDesc")}
        >
          <div className="grid gap-4 sm:grid-cols-2">
            <label className="flex flex-col gap-1.5">
              <span className="text-sm font-medium">{t("persona.match.nameLabel")}</span>
              <Input value={name} onChange={(e) => setName(e.target.value)} onBlur={() => name.trim() && name !== project?.name && saveProject({ name })} />
            </label>
            <label className="flex flex-col gap-1.5">
              <span className="text-sm font-medium">{t("persona.match.descriptionLabel")}</span>
              <Input value={description} onChange={(e) => setDescription(e.target.value)} onBlur={() => description !== (project?.description ?? "") && saveProject({ description })} />
            </label>
            <label className="flex flex-col gap-1.5">
              <span className="text-sm font-medium">{t("persona.match.frequencyLabel")}</span>
              <Select value={project?.schedule ?? "MANUAL"} onValueChange={(v) => saveProject({ schedule: v as MatchSchedule })}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {SCHEDULES.map((s) => (
                    <SelectItem key={s} value={s}>
                      {t(`persona.match.schedule.${s}`)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </label>
            <label className="flex flex-col gap-1.5">
              <span className="text-sm font-medium">{t("persona.match.modelLabel")}</span>
              <Select
                value={project?.llmInstanceId ?? "__default"}
                onValueChange={(v) => saveProject({ llmInstanceId: v === "__default" ? null : v })}
              >
                <SelectTrigger>
                  <SelectValue placeholder={t("persona.match.defaultLlm")} />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="__default">{t("persona.match.defaultLlm")}</SelectItem>
                  {(llmInstances ?? []).map((llm) => (
                    <SelectItem key={llm.id} value={llm.id}>
                      {llm.title}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </label>
          </div>
        </BentoFormSection>

        {/* --- Results --- */}
        <BentoFormSection
          icon={IconSparkles}
          tone="violet"
          title={t("persona.match.resultsTitle")}
          description={t("persona.match.resultsDesc")}
          trailing={
            running ? (
              <span className="flex items-center gap-2 text-xs text-muted-foreground">
                <IconLoader2 className="size-3.5 animate-spin" />
                {runProgress.done}/{runProgress.total}
              </span>
            ) : undefined
          }
        >
          {runError && <p className="mb-3 text-sm text-red-600">{runError}</p>}
          {total === 0 ? (
            <div className="rounded-2xl border border-dashed border-border/60 p-8 text-center">
              <IconTargetArrow className="mx-auto mb-2 size-8 text-muted-foreground/60" />
              <p className="text-sm text-muted-foreground">{t("persona.match.emptyPrompt")}</p>
            </div>
          ) : !everRun && !running ? (
            <div className="rounded-2xl border border-dashed border-border/60 p-8 text-center">
              <IconWand className="mx-auto mb-2 size-8 text-violet-500/70" />
              <p className="text-sm text-muted-foreground">
                {t("persona.match.readyPrompt", {
                  count: total,
                  sources: sources.length,
                  personas: personas.length,
                })}
              </p>
            </div>
          ) : (
            <div className="flex flex-col gap-4">
              {running && <Progress value={runProgress.total ? (runProgress.done / runProgress.total) * 100 : 0} />}
              {!running && (
                <div className="flex flex-wrap justify-end gap-2">
                  <Button type="button" variant="outline" size="sm" className="gap-1.5" onClick={() => handleExport("content")}>
                    <IconFileTypePdf className="size-4 text-rose-600" />
                    {t("persona.match.pdfByContent")}
                  </Button>
                  <Button type="button" variant="outline" size="sm" className="gap-1.5" onClick={() => handleExport("persona")}>
                    <IconFileTypePdf className="size-4 text-rose-600" />
                    {t("persona.match.pdfByPersona")}
                  </Button>
                </div>
              )}
              <Tabs defaultValue="matrix">
                <TabsList>
                  <TabsTrigger value="matrix">{t("persona.match.tabMatrix")}</TabsTrigger>
                  <TabsTrigger value="byContent">{t("persona.match.tabByContent")}</TabsTrigger>
                  <TabsTrigger value="byPersona">{t("persona.match.tabByPersona")}</TabsTrigger>
                </TabsList>

                <TabsContent value="matrix" className="mt-4">
                  <Heatmap sources={sources} personas={personas} matrix={matrix} revealed={revealed} onPick={setActiveCell} />
                  <FitLegend />
                </TabsContent>

                <TabsContent value="byContent" className="mt-4">
                  <div className="grid gap-3 lg:grid-cols-2">
                    {byContent.map(({ source, ranked, avg }) => (
                      <RankPanel
                        key={source.id}
                        title={source.name}
                        subtitle={`${t(`persona.match.sourceType.${source.type}`)} · ${t("persona.match.avgFitInline", { avg })}`}
                        icon={SOURCE_TYPE_ICON[source.type]}
                        rows={ranked.map((r) => ({
                          label: r.persona.name,
                          score: r.cell.fitScore,
                          onClick: () => setActiveCell({ source, persona: r.persona, cell: r.cell }),
                        }))}
                      />
                    ))}
                  </div>
                </TabsContent>

                <TabsContent value="byPersona" className="mt-4">
                  <div className="grid gap-3 lg:grid-cols-2">
                    {byPersona.map(({ persona, ranked, avg }) => (
                      <RankPanel
                        key={persona.id}
                        title={persona.name}
                        subtitle={`${t(`persona.match.kind.${persona.kind}`)} · ${t("persona.match.avgFitInline", { avg })}`}
                        icon={IconUserCircle}
                        rows={ranked.map((r) => ({
                          label: r.source.name,
                          score: r.cell.fitScore,
                          onClick: () => setActiveCell({ source: r.source, persona, cell: r.cell }),
                        }))}
                      />
                    ))}
                  </div>
                </TabsContent>
              </Tabs>
            </div>
          )}
        </BentoFormSection>
      </div>

      <CellSheet active={activeCell} onClose={() => setActiveCell(null)} />
      <SnDocumentPicker open={pickerOpen} onOpenChange={setPickerOpen} onPick={pickDoc} />
    </>
  );
}

// ---------------------------------------------------------------------------

function FitBadge({ score, prefix }: Readonly<{ score: number; prefix?: string }>) {
  const tone = fitTone(score);
  return (
    <span className={`rounded-full border px-2.5 py-1 text-sm font-medium ${tone.border} ${tone.soft} ${tone.text}`}>
      {prefix ? `${prefix} ` : ""}
      {Math.round(score)}%
    </span>
  );
}

function FitLegend() {
  const { t } = useTranslation();
  return (
    <div className="mt-3 flex flex-wrap items-center gap-4 text-xs text-muted-foreground">
      <span className="inline-flex items-center gap-1.5">
        <span className="size-3 rounded bg-emerald-500" /> {t("persona.match.legendHigh")}
      </span>
      <span className="inline-flex items-center gap-1.5">
        <span className="size-3 rounded bg-amber-500" /> {t("persona.match.legendMedium")}
      </span>
      <span className="inline-flex items-center gap-1.5">
        <span className="size-3 rounded bg-rose-500" /> {t("persona.match.legendLow")}
      </span>
    </div>
  );
}

interface HeatmapProps {
  sources: MatchSource[];
  personas: MatchPersonaRef[];
  matrix: Map<string, MatchCell>;
  revealed: Set<string>;
  onPick: (active: ActiveCell) => void;
}

function Heatmap({ sources, personas, matrix, revealed, onPick }: Readonly<HeatmapProps>) {
  const gridTemplate = {
    gridTemplateColumns: `minmax(150px, 1.4fr) repeat(${personas.length}, minmax(56px, 1fr))`,
  };
  return (
    <div className="overflow-x-auto">
      <div className="min-w-max">
        <div className="grid gap-1.5" style={gridTemplate}>
          <div />
          {personas.map((p) => (
            <div key={p.id} className="px-1 pb-1 text-center">
              <div className="truncate text-xs font-medium" title={p.name}>
                {p.name}
              </div>
            </div>
          ))}
        </div>
        <div className="flex flex-col gap-1.5">
          {sources.map((source) => (
            <div key={source.id} className="grid items-center gap-1.5" style={gridTemplate}>
              <div className="truncate pr-2 text-sm" title={source.name}>
                {source.name}
              </div>
              {personas.map((persona) => {
                const key = cellKey(source.id, persona.id);
                const cell = matrix.get(key);
                if (!revealed.has(key) || !cell) {
                  return <div key={persona.id} className="h-12 animate-pulse rounded-xl bg-muted/60" />;
                }
                const tone = fitTone(cell.fitScore);
                return (
                  <button
                    key={persona.id}
                    type="button"
                    onClick={() => onPick({ source, persona, cell })}
                    title={`${source.name} × ${persona.name}: ${Math.round(cell.fitScore)}%`}
                    className={`grid h-12 place-items-center rounded-xl text-sm font-semibold text-white shadow-sm transition-transform hover:scale-[1.06] focus:outline-none focus-visible:ring-2 focus-visible:ring-offset-1 ${tone.fill}`}
                  >
                    {Math.round(cell.fitScore)}
                  </button>
                );
              })}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

interface RankRow {
  label: string;
  score: number;
  onClick: () => void;
}

function RankPanel({
  title,
  subtitle,
  icon: Icon,
  rows,
}: Readonly<{ title: string; subtitle: string; icon: typeof IconLink; rows: RankRow[] }>) {
  return (
    <div className="bento-tile bento-glass flex flex-col gap-3 rounded-2xl p-4">
      <div className="flex items-center gap-2">
        <span className="grid size-8 shrink-0 place-items-center rounded-lg bg-violet-500/10 text-violet-600 dark:text-violet-300">
          <Icon size={16} />
        </span>
        <div className="min-w-0">
          <div className="truncate text-sm font-semibold" title={title}>
            {title}
          </div>
          <div className="text-xs text-muted-foreground">{subtitle}</div>
        </div>
      </div>
      <div className="flex flex-col gap-1.5">
        {rows.map((row) => {
          const tone = fitTone(row.score);
          return (
            <button
              key={row.label}
              type="button"
              onClick={row.onClick}
              className="group flex items-center gap-2 rounded-lg px-1 py-1 text-left hover:bg-muted/50"
            >
              <span className="w-36 shrink-0 truncate text-xs" title={row.label}>
                {row.label}
              </span>
              <span className="relative h-2 flex-1 overflow-hidden rounded-full bg-muted">
                <span className={`absolute inset-y-0 left-0 rounded-full ${tone.fill}`} style={{ width: `${row.score}%` }} />
              </span>
              <span className={`w-10 shrink-0 text-right text-xs font-semibold ${tone.text}`}>{Math.round(row.score)}%</span>
            </button>
          );
        })}
      </div>
    </div>
  );
}

function CellSheet({ active, onClose }: Readonly<{ active: ActiveCell | null; onClose: () => void }>) {
  const { t } = useTranslation();
  const cell = active?.cell;
  const tone = cell ? fitTone(cell.fitScore) : null;
  return (
    <Sheet open={active !== null} onOpenChange={(open) => !open && onClose()}>
      <SheetContent side="right" className="flex w-full flex-col gap-0 overflow-y-auto sm:max-w-md">
        {active && cell && tone && (
          <>
            <SheetHeader>
              <SheetTitle className="text-base">
                {active.source.name} <span className="text-muted-foreground">×</span> {active.persona.name}
              </SheetTitle>
              <SheetDescription>
                {t(`persona.match.sourceType.${active.source.type}`)} × {t(`persona.match.kind.${active.persona.kind}`)}
              </SheetDescription>
            </SheetHeader>

            <div className="flex flex-col gap-5 px-4 pb-6">
              <div className={`flex items-center gap-4 rounded-2xl border p-4 ${tone.border} ${tone.soft}`}>
                <div className={`text-4xl font-bold ${tone.text}`}>{Math.round(cell.fitScore)}%</div>
                <div className="text-sm">
                  <div className={`font-medium ${tone.text}`}>
                    {t("persona.match.fitLevelLine", {
                      level: t(`persona.match.fitTone.${tone.level}`).toLowerCase(),
                    })}
                  </div>
                  <div className="text-muted-foreground">
                    {t("persona.match.readabilityLine", {
                      score: Math.round(cell.readability),
                      mode: cell.llmUsed
                        ? t("persona.match.evaluatedWithLlm")
                        : t("persona.match.heuristicOnly"),
                    })}
                  </div>
                </div>
              </div>

              {cell.summary && <p className="text-sm text-muted-foreground">{cell.summary}</p>}

              <div>
                <h4 className="mb-2 flex items-center gap-1.5 text-sm font-semibold text-emerald-700 dark:text-emerald-300">
                  <IconCheck size={16} /> {t("persona.match.whatFits")}
                </h4>
                {cell.fits.length ? (
                  <ul className="flex flex-col gap-1.5">
                    {cell.fits.map((fit) => (
                      <li key={fit} className="flex items-start gap-2 text-sm">
                        <IconCheck size={15} className="mt-0.5 shrink-0 text-emerald-500" />
                        <span>{fit}</span>
                      </li>
                    ))}
                  </ul>
                ) : (
                  <p className="text-sm text-muted-foreground">—</p>
                )}
              </div>

              <div>
                <h4 className="mb-2 flex items-center gap-1.5 text-sm font-semibold text-amber-700 dark:text-amber-300">
                  <IconBulb size={16} /> {t("persona.match.whatToAdjust")}
                </h4>
                {cell.misfits.length ? (
                  <ul className="flex flex-col gap-2.5">
                    {cell.misfits.map((m) => (
                      <li key={m.span} className="rounded-xl border border-amber-500/30 bg-amber-500/5 p-3">
                        <p className="text-sm font-medium">{m.span}</p>
                        <p className="mt-0.5 text-xs text-muted-foreground">{m.reason}</p>
                        <p className="mt-1.5 flex items-start gap-1.5 text-xs text-emerald-700 dark:text-emerald-300">
                          <IconWand size={13} className="mt-0.5 shrink-0" />
                          {m.suggestion}
                        </p>
                      </li>
                    ))}
                  </ul>
                ) : (
                  <p className="text-sm text-muted-foreground">{t("persona.match.nothingToAdjust")}</p>
                )}
              </div>
            </div>
          </>
        )}
      </SheetContent>
    </Sheet>
  );
}
