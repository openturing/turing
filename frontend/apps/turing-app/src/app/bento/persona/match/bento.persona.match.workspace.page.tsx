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
import { BentoFormSection, BentoHero } from "@/components/bento";
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
  PERSONA_KIND_LABEL,
  projectAvgFit,
  rankByContent,
  rankByPersona,
  SCHEDULE_LABEL,
  SOURCE_TYPE_LABEL,
  type MatchCell,
  type MatchPersonaKind,
  type MatchPersonaRef,
  type MatchProjectRequest,
  type MatchSchedule,
  type MatchSource,
  type MatchSourceType,
} from "@/models/persona/persona-match.model";
import { useQueryClient } from "@tanstack/react-query";
import {
  IconArrowLeft,
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
import { Link, useNavigate, useParams } from "react-router-dom";

const SOURCE_TYPE_ICON: Record<MatchSourceType, typeof IconLink> = {
  URL: IconLink,
  SN_DOC: IconWorldSearch,
  ASSET: IconFile,
};

// --- PDF export (print-to-PDF) ---------------------------------------------

interface ReportRow {
  label: string;
  score: number;
}
interface ReportGroup {
  head: string;
  sub: string;
  rows: ReportRow[];
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function fitHex(score: number): string {
  if (score >= 75) return "#10b981";
  if (score >= 50) return "#f59e0b";
  return "#f43f5e";
}

function buildReportHtml(title: string, subtitle: string, groups: ReportGroup[]): string {
  const groupsHtml = groups
    .map((g) => {
      const rowsHtml = g.rows
        .map((r) => {
          const color = fitHex(r.score);
          return `<div class="row">
            <span class="rlabel">${escapeHtml(r.label)}</span>
            <span class="bar"><span class="fill" style="width:${r.score}%;background:${color}"></span></span>
            <span class="score" style="color:${color}">${Math.round(r.score)}%</span>
          </div>`;
        })
        .join("");
      return `<section class="group">
        <h2>${escapeHtml(g.head)}</h2>
        <p class="sub">${escapeHtml(g.sub)}</p>
        ${rowsHtml}
      </section>`;
    })
    .join("");

  return `<!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><title>${escapeHtml(title)}</title>
  <style>
    @page { margin: 20mm 16mm; }
    * { box-sizing: border-box; }
    body { font-family: -apple-system, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; color: #0f172a; margin: 0; }
    header { border-bottom: 2px solid #4f46e5; padding-bottom: 12px; margin-bottom: 20px; }
    .brand { font-size: 11px; letter-spacing: .12em; text-transform: uppercase; color: #6366f1; font-weight: 600; }
    h1 { font-size: 22px; margin: 4px 0 2px; }
    .subtitle { color: #64748b; font-size: 13px; margin: 0; }
    .group { margin-bottom: 18px; page-break-inside: avoid; }
    .group h2 { font-size: 15px; margin: 0 0 2px; }
    .group .sub { color: #64748b; font-size: 12px; margin: 0 0 8px; }
    .row { display: flex; align-items: center; gap: 10px; margin: 4px 0; }
    .rlabel { width: 200px; font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .bar { flex: 1; height: 8px; background: #e2e8f0; border-radius: 999px; overflow: hidden; }
    .fill { display: block; height: 100%; border-radius: 999px; }
    .score { width: 44px; text-align: right; font-size: 12px; font-weight: 700; }
    footer { margin-top: 24px; border-top: 1px solid #e2e8f0; padding-top: 8px; color: #94a3b8; font-size: 10px; }
  </style></head>
  <body>
    <header>
      <div class="brand">Viglet Turing ES &middot; Persona Match</div>
      <h1>${escapeHtml(title)}</h1>
      <p class="subtitle">${escapeHtml(subtitle)}</p>
    </header>
    ${groupsHtml}
    <footer>Relatorio gerado a partir da matriz de fit N&times;N. Os percentuais combinam legibilidade deterministica e avaliacao por LLM.</footer>
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

interface ActiveCell {
  source: MatchSource;
  persona: MatchPersonaRef;
  cell: MatchCell;
}

/**
 * Persona Match — project workspace (Block AT / §XLIII, T701–T702). Contents
 * (URL / Indexed / File) + personas → an N×N fit matrix with three lenses
 * (Matrix / By content / By persona) and a per-cell drill-in. "Rodar análise"
 * streams the real runner (T698) over SSE, filling the heatmap cell-by-cell.
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
        eyebrow={
          <Link to={ROUTES.BENTO_PERSONA_MATCH} className="inline-flex items-center gap-1 hover:text-foreground">
            <IconArrowLeft size={12} /> Persona Match
          </Link>
        }
        leading={
          <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-indigo-600 to-fuchsia-600 text-white shadow-md">
            <IconTargetArrow size={24} />
          </span>
        }
        title="Novo projeto de análise"
        subtitle="Dê um nome ao projeto. Depois adicione conteúdos e personas para gerar a matriz N×N."
      />
      <BentoFormSection icon={IconSparkles} tone="indigo" title="Projeto" description="Nome e descrição do projeto de análise.">
        <div className="flex flex-col gap-3">
          <label className="flex flex-col gap-1.5">
            <span className="text-sm font-medium">Nome</span>
            <Input
              value={name}
              onChange={(e) => setName(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && submit()}
              placeholder="Campanha de captação 2026"
              autoFocus
            />
          </label>
          <label className="flex flex-col gap-1.5">
            <span className="text-sm font-medium">Descrição</span>
            <Input
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="O que este projeto avalia"
            />
          </label>
          <div>
            <Button onClick={submit} disabled={!name.trim() || create.isPending} className="gap-2">
              {create.isPending ? <IconLoader2 className="size-4 animate-spin" /> : <IconPlus className="size-4" />}
              Criar projeto
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

  // Editable identity/settings, mirrored from the server project.
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");

  const sources = useMemo(() => project?.sources ?? [], [project]);
  const personas = useMemo(() => project?.personas ?? [], [project]);
  const everRun = Boolean(project?.lastRunAt);

  // Sync local mirrors + persisted matrix when the project (re)loads.
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
  const selectedIds = useMemo(() => new Set(personas.map((p) => p.id)), [personas]);

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

  // --- run (SSE) ---
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
          setRunError(err instanceof Error ? err.message : "Falha ao rodar a análise");
        },
      },
      ctrl.signal,
    );
  }, [projectId, total, running, refresh]);

  useEffect(() => () => abortRef.current?.abort(), []);

  // --- source mutations ---
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

  const togglePersona = useCallback(
    async (personaId: string) => {
      const next = selectedIds.has(personaId)
        ? [...selectedIds].filter((id) => id !== personaId)
        : [...selectedIds, personaId];
      await personaMatchService.setPersonas(projectId, next);
      refresh();
    },
    [selectedIds, projectId, refresh],
  );

  const handleExport = useCallback(
    (kind: "content" | "persona") => {
      if (kind === "content") {
        const groups: ReportGroup[] = byContent.map((g) => ({
          head: g.source.name,
          sub: `${SOURCE_TYPE_LABEL[g.source.type]} · fit médio ${g.avg}%`,
          rows: g.ranked.map((r) => ({ label: r.persona.name, score: r.cell.fitScore })),
        }));
        openPrintWindow(buildReportHtml(`${name} — Por conteúdo`, "O que cada conteúdo serve para cada persona.", groups));
      } else {
        const groups: ReportGroup[] = byPersona.map((g) => ({
          head: g.persona.name,
          sub: `${PERSONA_KIND_LABEL[g.persona.kind]} · fit médio ${g.avg}%`,
          rows: g.ranked.map((r) => ({ label: r.source.name, score: r.cell.fitScore })),
        }));
        openPrintWindow(buildReportHtml(`${name} — Por persona`, "O que cada persona serve para cada conteúdo.", groups));
      }
    },
    [byContent, byPersona, name],
  );

  const removeProject = useCallback(() => {
    deleteProject.mutate(projectId, { onSuccess: () => navigate(ROUTES.BENTO_PERSONA_MATCH) });
  }, [deleteProject, projectId, navigate]);

  const canRun = total > 0 && !running;
  const pool = useMemo(() => (personaPool ?? []).map(toPersonaRef), [personaPool]);

  return (
    <>
      <BentoHero
        eyebrow={
          <Link to={ROUTES.BENTO_PERSONA_MATCH} className="inline-flex items-center gap-1 hover:text-foreground">
            <IconArrowLeft size={12} /> Persona Match
          </Link>
        }
        leading={
          <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-indigo-600 to-fuchsia-600 text-white shadow-md">
            <IconTargetArrow size={24} />
          </span>
        }
        title={name || "Projeto de análise"}
        subtitle={description || "Selecione conteúdos e personas, e gere a matriz de fit N×N."}
        trailing={
          <div className="flex items-center gap-2">
            {avgFit !== null && <FitBadge score={avgFit} prefix="Fit médio" />}
            <Button onClick={runAnalysis} disabled={!canRun} className="gap-2">
              {running ? <IconLoader2 className="size-4 animate-spin" /> : <IconPlayerPlayFilled className="size-4" />}
              {running ? "Analisando…" : everRun ? "Reanalisar" : "Rodar análise"}
            </Button>
          </div>
        }
      />

      <div className="flex flex-col gap-5">
        {/* --- Contents --- */}
        <BentoFormSection
          icon={IconServerBolt}
          tone="blue"
          title="Conteúdos"
          description="URL, conteúdo indexado ou arquivo. Adicionados uma vez por projeto — não por persona."
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
                        {SOURCE_TYPE_LABEL[source.type]}
                      </Badge>
                      {failed && (
                        <Badge variant="destructive" className="shrink-0 text-[10px]">
                          Falha
                        </Badge>
                      )}
                    </div>
                    <p className="truncate text-xs text-muted-foreground">{failed ? source.error : source.ref}</p>
                  </div>
                  {source.charCount > 0 && (
                    <span className="hidden shrink-0 text-xs text-muted-foreground sm:block">
                      {source.charCount.toLocaleString("pt-BR")} car.
                    </span>
                  )}
                  <Button
                    type="button"
                    size="icon"
                    variant="ghost"
                    aria-label="Remover conteúdo"
                    onClick={() => removeSource(source.id)}
                  >
                    <IconTrash className="size-4 text-red-600" />
                  </Button>
                </div>
              );
            })}
            {sources.length === 0 && (
              <p className="text-sm text-muted-foreground">Nenhum conteúdo ainda. Adicione o primeiro abaixo.</p>
            )}
          </div>

          <div className="flex flex-col gap-2 sm:flex-row">
            <Select value={addType} onValueChange={(v) => setAddType(v as MatchSourceType)}>
              <SelectTrigger className="sm:w-40">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="URL">URL</SelectItem>
                <SelectItem value="SN_DOC">Indexado</SelectItem>
                <SelectItem value="ASSET">Arquivo</SelectItem>
              </SelectContent>
            </Select>
            {addType === "SN_DOC" && (
              <Button type="button" variant="outline" onClick={() => setPickerOpen(true)} className="flex-1 justify-center gap-1.5" disabled={busy}>
                <IconWorldSearch className="size-4" />
                Buscar documento indexado…
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
                  Enviar arquivo…
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
                  placeholder="https://exemplo.com/pagina"
                  className="flex-1"
                />
                <Button type="button" variant="outline" onClick={addUrlSource} className="gap-1.5" disabled={busy}>
                  {busy ? <IconLoader2 className="size-4 animate-spin" /> : <IconPlus className="size-4" />}
                  Adicionar
                </Button>
              </>
            )}
          </div>
        </BentoFormSection>

        {/* --- Personas --- */}
        <BentoFormSection
          icon={IconUserCircle}
          tone="rose"
          title="Personas"
          description="Selecione as personas que este projeto avalia. A matriz é o produto conteúdos × personas."
          trailing={<Badge variant="secondary">{personas.length}</Badge>}
        >
          <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
            {pool.map((persona) => {
              const selected = selectedIds.has(persona.id);
              return (
                <button
                  key={persona.id}
                  type="button"
                  onClick={() => togglePersona(persona.id)}
                  aria-pressed={selected}
                  className={`bento-tile bento-tile-clickable flex items-center gap-3 rounded-2xl border px-3 py-2.5 text-left backdrop-blur transition-colors ${
                    selected ? "border-rose-500/40 bg-rose-500/10" : "border-border/60 bg-card/40 hover:border-rose-500/30"
                  }`}
                >
                  <span
                    className={`grid size-9 shrink-0 place-items-center rounded-xl ${
                      selected ? "bg-linear-to-br from-rose-600 to-pink-600 text-white" : "bg-muted text-muted-foreground"
                    }`}
                  >
                    {selected ? <IconCheck size={18} /> : <IconUserCircle size={18} />}
                  </span>
                  <div className="min-w-0 flex-1">
                    <div className="truncate text-sm font-medium">{persona.name}</div>
                    <div className="text-xs text-muted-foreground">{PERSONA_KIND_LABEL[persona.kind]}</div>
                  </div>
                </button>
              );
            })}
            {pool.length === 0 && <p className="text-sm text-muted-foreground">Nenhuma persona cadastrada ainda.</p>}
          </div>
        </BentoFormSection>

        {/* --- Project settings --- */}
        <BentoFormSection
          icon={IconCalendarRepeat}
          tone="amber"
          title="Projeto, agenda & modelo"
          description="Nome, descrição e a frequência com que a análise reextrai os conteúdos e recalcula as células que mudaram."
        >
          <div className="grid gap-4 sm:grid-cols-2">
            <label className="flex flex-col gap-1.5">
              <span className="text-sm font-medium">Nome</span>
              <Input value={name} onChange={(e) => setName(e.target.value)} onBlur={() => name.trim() && name !== project?.name && saveProject({ name })} />
            </label>
            <label className="flex flex-col gap-1.5">
              <span className="text-sm font-medium">Descrição</span>
              <Input value={description} onChange={(e) => setDescription(e.target.value)} onBlur={() => description !== (project?.description ?? "") && saveProject({ description })} />
            </label>
            <label className="flex flex-col gap-1.5">
              <span className="text-sm font-medium">Frequência</span>
              <Select value={project?.schedule ?? "MANUAL"} onValueChange={(v) => saveProject({ schedule: v as MatchSchedule })}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {(Object.keys(SCHEDULE_LABEL) as MatchSchedule[]).map((s) => (
                    <SelectItem key={s} value={s}>
                      {SCHEDULE_LABEL[s]}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </label>
            <label className="flex flex-col gap-1.5">
              <span className="text-sm font-medium">Modelo (LLM)</span>
              <Select
                value={project?.llmInstanceId ?? "__default"}
                onValueChange={(v) => saveProject({ llmInstanceId: v === "__default" ? null : v })}
              >
                <SelectTrigger>
                  <SelectValue placeholder="LLM padrão" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="__default">LLM padrão (Global)</SelectItem>
                  {(llmInstances ?? []).map((llm) => (
                    <SelectItem key={llm.id} value={llm.id}>
                      {llm.title}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </label>
          </div>
          <div className="mt-2">
            <Button type="button" variant="ghost" size="sm" className="gap-1.5 text-red-600" onClick={removeProject}>
              <IconTrash className="size-4" />
              Excluir projeto
            </Button>
          </div>
        </BentoFormSection>

        {/* --- Results --- */}
        <BentoFormSection
          icon={IconSparkles}
          tone="violet"
          title="Relatório N×N"
          description="O que cada conteúdo serve para cada persona — e o que cada persona serve para cada conteúdo."
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
              <p className="text-sm text-muted-foreground">
                Adicione ao menos um conteúdo e uma persona, depois clique em <strong>Rodar análise</strong>.
              </p>
            </div>
          ) : !everRun && !running ? (
            <div className="rounded-2xl border border-dashed border-border/60 p-8 text-center">
              <IconWand className="mx-auto mb-2 size-8 text-violet-500/70" />
              <p className="text-sm text-muted-foreground">
                {total} células prontas para avaliar ({sources.length} × {personas.length}). Clique em <strong>Rodar análise</strong>.
              </p>
            </div>
          ) : (
            <div className="flex flex-col gap-4">
              {running && <Progress value={runProgress.total ? (runProgress.done / runProgress.total) * 100 : 0} />}
              {!running && (
                <div className="flex flex-wrap justify-end gap-2">
                  <Button type="button" variant="outline" size="sm" className="gap-1.5" onClick={() => handleExport("content")}>
                    <IconFileTypePdf className="size-4 text-rose-600" />
                    PDF · por conteúdo
                  </Button>
                  <Button type="button" variant="outline" size="sm" className="gap-1.5" onClick={() => handleExport("persona")}>
                    <IconFileTypePdf className="size-4 text-rose-600" />
                    PDF · por persona
                  </Button>
                </div>
              )}
              <Tabs defaultValue="matrix">
                <TabsList>
                  <TabsTrigger value="matrix">Matriz</TabsTrigger>
                  <TabsTrigger value="byContent">Por conteúdo</TabsTrigger>
                  <TabsTrigger value="byPersona">Por persona</TabsTrigger>
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
                        subtitle={`${SOURCE_TYPE_LABEL[source.type]} · fit médio ${avg}%`}
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
                        subtitle={`${PERSONA_KIND_LABEL[persona.kind]} · fit médio ${avg}%`}
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
  return (
    <div className="mt-3 flex flex-wrap items-center gap-4 text-xs text-muted-foreground">
      <span className="inline-flex items-center gap-1.5">
        <span className="size-3 rounded bg-emerald-500" /> Alto (≥ 75%)
      </span>
      <span className="inline-flex items-center gap-1.5">
        <span className="size-3 rounded bg-amber-500" /> Médio (50–74%)
      </span>
      <span className="inline-flex items-center gap-1.5">
        <span className="size-3 rounded bg-rose-500" /> Baixo (&lt; 50%)
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
                {SOURCE_TYPE_LABEL[active.source.type]} × {PERSONA_KIND_LABEL[active.persona.kind]}
              </SheetDescription>
            </SheetHeader>

            <div className="flex flex-col gap-5 px-4 pb-6">
              <div className={`flex items-center gap-4 rounded-2xl border p-4 ${tone.border} ${tone.soft}`}>
                <div className={`text-4xl font-bold ${tone.text}`}>{Math.round(cell.fitScore)}%</div>
                <div className="text-sm">
                  <div className={`font-medium ${tone.text}`}>Fit {tone.label.toLowerCase()}</div>
                  <div className="text-muted-foreground">
                    Legibilidade {Math.round(cell.readability)}% · {cell.llmUsed ? "avaliado com LLM" : "só heurística"}
                  </div>
                </div>
              </div>

              {cell.summary && <p className="text-sm text-muted-foreground">{cell.summary}</p>}

              <div>
                <h4 className="mb-2 flex items-center gap-1.5 text-sm font-semibold text-emerald-700 dark:text-emerald-300">
                  <IconCheck size={16} /> O que condiz
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
                  <IconBulb size={16} /> O que ajustar
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
                  <p className="text-sm text-muted-foreground">Nada a ajustar — conteúdo bem alinhado a esta persona.</p>
                )}
              </div>
            </div>
          </>
        )}
      </SheetContent>
    </Sheet>
  );
}
