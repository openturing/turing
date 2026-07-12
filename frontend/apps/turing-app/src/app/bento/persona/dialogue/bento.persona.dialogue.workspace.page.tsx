import {
  dialogueProjectService,
  invalidateDialogueProject,
  useCreateDialogueProject,
  useDeleteDialogueProject,
  useDialogueProject,
  useUpdateDialogueProject,
} from "@/api/queries/persona-dialogue.queries";
import { useLlmInstances } from "@/api/queries/llm-instance.queries";
import { usePersonas } from "@/api/queries/persona.queries";
import { ROUTES } from "@/app/routes.const";
import { BentoFormSection, BentoHero } from "@/components/bento";
import { PersonaSelectGrid } from "@/components/persona/persona-select-grid";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type {
  DialogueProjectRequest,
  DialogueTurn,
} from "@/models/persona/persona-dialogue.model";
import { useQueryClient } from "@tanstack/react-query";
import {
  IconArrowLeft,
  IconCalendarQuestion,
  IconLoader2,
  IconMasksTheater,
  IconMessages,
  IconPlayerPlayFilled,
  IconPlus,
  IconSettings,
  IconTrash,
  IconUserCircle,
} from "@tabler/icons-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useNavigate, useParams } from "react-router-dom";

/** Per-speaker bubble colours, cycled by the speaker's position in the roster. */
const BUBBLE_PALETTE = [
  { bg: "bg-blue-500/10", border: "border-blue-500/30", text: "text-blue-700 dark:text-blue-300" },
  { bg: "bg-fuchsia-500/10", border: "border-fuchsia-500/30", text: "text-fuchsia-700 dark:text-fuchsia-300" },
  { bg: "bg-emerald-500/10", border: "border-emerald-500/30", text: "text-emerald-700 dark:text-emerald-300" },
  { bg: "bg-amber-500/10", border: "border-amber-500/30", text: "text-amber-700 dark:text-amber-300" },
  { bg: "bg-violet-500/10", border: "border-violet-500/30", text: "text-violet-700 dark:text-violet-300" },
  { bg: "bg-rose-500/10", border: "border-rose-500/30", text: "text-rose-700 dark:text-rose-300" },
];

/**
 * Persona Dialogue — project workspace (Block AU / §XLIV, T706). A saved topic +
 * ordered speaker roster (via the shared {@link PersonaSelectGrid}) + model +
 * turn budget. "Run" streams the round-robin dialogue live over SSE and persists
 * the transcript, so re-opening the project shows the last conversation.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
export default function BentoPersonaDialogueWorkspacePage() {
  const { projectId } = useParams();
  if (projectId === "new") {
    return <CreateProjectCard />;
  }
  return <ProjectWorkspace projectId={projectId as string} />;
}

// ---------------------------------------------------------------------------

function CreateProjectCard() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const create = useCreateDialogueProject();
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");

  const submit = () => {
    const trimmed = name.trim();
    if (!trimmed) return;
    create.mutate(
      { name: trimmed, description, turns: 10 },
      { onSuccess: (project) => navigate(`${ROUTES.BENTO_PERSONA_DIALOGUE}/${project.id}`) },
    );
  };

  return (
    <>
      <BentoHero
        eyebrow={
          <Link to={ROUTES.BENTO_PERSONA_DIALOGUE} className="inline-flex items-center gap-1 hover:text-foreground">
            <IconArrowLeft size={12} /> {t("persona.dialogueProject.back")}
          </Link>
        }
        leading={
          <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-fuchsia-500 to-pink-600 text-white shadow-md">
            <IconMasksTheater size={24} />
          </span>
        }
        title={t("persona.dialogueProject.newTitle")}
        subtitle={t("persona.dialogueProject.newHeroSubtitle")}
      />
      <BentoFormSection
        icon={IconSettings}
        tone="rose"
        title={t("persona.dialogueProject.projectSection")}
        description={t("persona.dialogueProject.projectSectionDesc")}
      >
        <div className="flex flex-col gap-3">
          <label className="flex flex-col gap-1.5">
            <span className="text-sm font-medium">{t("persona.dialogueProject.nameLabel")}</span>
            <Input
              value={name}
              onChange={(e) => setName(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && submit()}
              placeholder={t("persona.dialogueProject.namePlaceholder")}
              autoFocus
            />
          </label>
          <label className="flex flex-col gap-1.5">
            <span className="text-sm font-medium">{t("persona.dialogueProject.descriptionLabel")}</span>
            <Input
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder={t("persona.dialogueProject.descriptionPlaceholder")}
            />
          </label>
          <div>
            <Button onClick={submit} disabled={!name.trim() || create.isPending} className="gap-2">
              {create.isPending ? <IconLoader2 className="size-4 animate-spin" /> : <IconPlus className="size-4" />}
              {t("persona.dialogueProject.createButton")}
            </Button>
          </div>
        </div>
      </BentoFormSection>
    </>
  );
}

// ---------------------------------------------------------------------------

interface DisplayTurn {
  personaId: string | null;
  personaName: string | null;
  content: string | null;
}

function ProjectWorkspace({ projectId }: Readonly<{ projectId: string }>) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const { data: project } = useDialogueProject(projectId);
  const { data: personaPool } = usePersonas();
  const { data: llmInstances } = useLlmInstances();

  const updateProject = useUpdateDialogueProject(projectId);
  const deleteProject = useDeleteDialogueProject();

  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [topic, setTopic] = useState("");
  const [turns, setTurns] = useState(10);

  const [turnsList, setTurnsList] = useState<DisplayTurn[]>([]);
  const [running, setRunning] = useState(false);
  const [runError, setRunError] = useState<string | null>(null);
  const abortRef = useRef<AbortController | null>(null);

  const speakers = useMemo(() => project?.speakers ?? [], [project]);
  const selectedSpeakerIds = useMemo(() => speakers.map((s) => s.id), [speakers]);

  // Only speaker-capable personas (SPEAKER / BOTH, or legacy unset) can talk.
  const pool = useMemo(
    () =>
      (personaPool ?? [])
        .filter((p) => p.personaKind !== "AUDIENCE")
        .map((p) => ({ id: p.id, name: p.name })),
    [personaPool],
  );

  // Stable colour per speaker: position in the roster → palette slot.
  const colorIndexById = useMemo(() => {
    const map = new Map<string, number>();
    speakers.forEach((s, i) => map.set(s.id, i));
    return map;
  }, [speakers]);

  // Seed local mirrors + the persisted transcript when the project (re)loads.
  useEffect(() => {
    if (!project) return;
    setName(project.name);
    setDescription(project.description ?? "");
    setTopic(project.topic ?? "");
    setTurns(project.turns);
    if (!running) {
      setTurnsList(
        (project.transcript ?? []).map((tt: DialogueTurn) => ({
          personaId: tt.personaId,
          personaName: tt.personaName,
          content: tt.content,
        })),
      );
    }
  }, [project, running]);

  useEffect(() => () => abortRef.current?.abort(), []);

  const refresh = useCallback(
    () => invalidateDialogueProject(queryClient, projectId),
    [queryClient, projectId],
  );

  const saveProject = useCallback(
    (patch: Partial<DialogueProjectRequest>) => {
      if (!project) return;
      updateProject.mutate({
        name: patch.name ?? name,
        description: patch.description ?? description,
        topic: patch.topic ?? topic,
        llmInstanceId: patch.llmInstanceId ?? project.llmInstanceId,
        turns: patch.turns ?? turns,
      });
    },
    [project, updateProject, name, description, topic, turns],
  );

  const changeSpeakers = useCallback(
    async (ids: string[]) => {
      await dialogueProjectService.setSpeakers(projectId, ids);
      refresh();
    },
    [projectId, refresh],
  );

  const canRun =
    Boolean(topic.trim()) &&
    speakers.length >= 2 &&
    Boolean(project?.llmInstanceId) &&
    !running;

  const runDialogue = useCallback(() => {
    if (!canRun) return;
    abortRef.current?.abort();
    const ctrl = new AbortController();
    abortRef.current = ctrl;
    setRunning(true);
    setRunError(null);
    setTurnsList([]);
    dialogueProjectService.runStream(
      projectId,
      {
        onTurn: (event) =>
          setTurnsList((prev) => [
            ...prev,
            { personaId: event.personaId ?? null, personaName: event.personaName ?? null, content: event.content ?? null },
          ]),
        onDone: () => {
          setRunning(false);
          refresh();
        },
        onError: (err) => {
          setRunning(false);
          setRunError(err instanceof Error ? err.message : t("persona.dialogueProject.runError"));
        },
      },
      ctrl.signal,
    );
  }, [canRun, projectId, refresh, t]);

  const removeProject = useCallback(() => {
    deleteProject.mutate(projectId, { onSuccess: () => navigate(ROUTES.BENTO_PERSONA_DIALOGUE) });
  }, [deleteProject, projectId, navigate]);

  let runLabel = t("persona.dialogueProject.run");
  if (running) runLabel = t("persona.dialogueProject.running");
  else if (project?.lastRunAt) runLabel = t("persona.dialogueProject.rerun");

  return (
    <>
      <BentoHero
        eyebrow={
          <Link to={ROUTES.BENTO_PERSONA_DIALOGUE} className="inline-flex items-center gap-1 hover:text-foreground">
            <IconArrowLeft size={12} /> {t("persona.dialogueProject.back")}
          </Link>
        }
        leading={
          <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-fuchsia-500 to-pink-600 text-white shadow-md">
            <IconMasksTheater size={24} />
          </span>
        }
        title={name || t("persona.dialogueProject.defaultTitle")}
        subtitle={topic || description || t("persona.dialogueProject.defaultSubtitle")}
        trailing={
          <Button onClick={runDialogue} disabled={!canRun} className="gap-2">
            {running ? <IconLoader2 className="size-4 animate-spin" /> : <IconPlayerPlayFilled className="size-4" />}
            {runLabel}
          </Button>
        }
      />

      <div className="flex flex-col gap-5">
        {/* --- Speakers --- */}
        <BentoFormSection
          icon={IconUserCircle}
          tone="rose"
          title={t("persona.dialogueProject.speakersTitle")}
          description={t("persona.dialogueProject.speakersDesc")}
        >
          <PersonaSelectGrid
            ordered
            personas={pool}
            selectedIds={selectedSpeakerIds}
            onChange={changeSpeakers}
            emptyText={t("persona.dialogueProject.noSpeakers")}
          />
        </BentoFormSection>

        {/* --- Topic, turns, model --- */}
        <BentoFormSection
          icon={IconCalendarQuestion}
          tone="amber"
          title={t("persona.dialogueProject.settingsTitle")}
          description={t("persona.dialogueProject.settingsDesc")}
        >
          <div className="flex flex-col gap-4">
            <label className="flex flex-col gap-1.5">
              <span className="text-sm font-medium">{t("persona.dialogueProject.topicLabel")}</span>
              <Input
                value={topic}
                onChange={(e) => setTopic(e.target.value)}
                onBlur={() => topic !== (project?.topic ?? "") && saveProject({ topic })}
                placeholder={t("persona.dialogueProject.topicPlaceholder")}
              />
            </label>
            <div className="grid gap-4 sm:grid-cols-2">
              <label className="flex flex-col gap-1.5">
                <span className="text-sm font-medium">{t("persona.dialogueProject.nameLabel")}</span>
                <Input value={name} onChange={(e) => setName(e.target.value)} onBlur={() => name.trim() && name !== project?.name && saveProject({ name })} />
              </label>
              <label className="flex flex-col gap-1.5">
                <span className="text-sm font-medium">{t("persona.dialogueProject.turnsLabel")}</span>
                <Input
                  type="number"
                  min={2}
                  max={40}
                  value={turns}
                  onChange={(e) => setTurns(Number(e.target.value) || 10)}
                  onBlur={() => turns !== project?.turns && saveProject({ turns })}
                />
              </label>
              <label className="flex flex-col gap-1.5 sm:col-span-2">
                <span className="text-sm font-medium">{t("persona.dialogueProject.modelLabel")}</span>
                <Select
                  value={project?.llmInstanceId ?? ""}
                  onValueChange={(v) => saveProject({ llmInstanceId: v })}
                >
                  <SelectTrigger>
                    <SelectValue placeholder={t("persona.dialogueProject.defaultLlm")} />
                  </SelectTrigger>
                  <SelectContent>
                    {(llmInstances ?? []).map((llm) => (
                      <SelectItem key={llm.id} value={llm.id}>
                        {llm.title}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </label>
            </div>
            <div>
              <Button type="button" variant="ghost" size="sm" className="gap-1.5 text-red-600" onClick={removeProject}>
                <IconTrash className="size-4" />
                {t("persona.dialogueProject.deleteProject")}
              </Button>
            </div>
          </div>
        </BentoFormSection>

        {/* --- Transcript --- */}
        <BentoFormSection
          icon={IconMessages}
          tone="violet"
          title={t("persona.dialogueProject.transcriptTitle")}
          description={t("persona.dialogueProject.transcriptDesc")}
        >
          {runError && <p className="mb-3 text-sm text-red-600">{runError}</p>}
          {turnsList.length === 0 && !running ? (
            <p className="text-sm text-muted-foreground">{t("persona.dialogueProject.transcriptEmpty")}</p>
          ) : (
            <div className="flex flex-col gap-3">
              {turnsList.map((turn, i) => (
                <DialogueBubble
                  key={`${turn.personaId}-${i}`}
                  name={turn.personaName ?? ""}
                  content={turn.content ?? ""}
                  colorIndex={colorIndexById.get(turn.personaId ?? "") ?? 0}
                />
              ))}
              {running && (
                <p className="flex items-center gap-2 text-sm text-muted-foreground">
                  <IconLoader2 className="size-4 animate-spin" />
                  {t("persona.dialogueProject.thinking")}
                </p>
              )}
            </div>
          )}
        </BentoFormSection>
      </div>
    </>
  );
}

function DialogueBubble({
  name,
  content,
  colorIndex,
}: Readonly<{ name: string; content: string; colorIndex: number }>) {
  const palette = BUBBLE_PALETTE[colorIndex % BUBBLE_PALETTE.length];
  const left = colorIndex % 2 === 0;
  return (
    <div className={`flex ${left ? "justify-start" : "justify-end"}`}>
      <div className={`max-w-[85%] rounded-2xl border px-4 py-3 ${palette.border} ${palette.bg}`}>
        <div className={`mb-1 text-xs font-semibold ${palette.text}`}>{name}</div>
        <p className="whitespace-pre-wrap text-sm">{content}</p>
      </div>
    </div>
  );
}
