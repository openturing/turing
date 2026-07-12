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
import {
  BentoEntityShell,
  BentoFormSection,
  BentoSaveBar,
  type BentoEntityShellRenderArgs,
} from "@/components/bento";
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
import type { DialogueProject } from "@/models/persona/persona-dialogue.model";
import { toast } from "@viglet/viglet-design-system";
import { useQueryClient } from "@tanstack/react-query";
import {
  IconCalendarQuestion,
  IconLoader2,
  IconMasksTheater,
  IconMessages,
  IconPlayerPlayFilled,
  IconUserCircle,
} from "@tabler/icons-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";

/** DOM id the hero-anchored Save button submits via `form="..."`. */
const FORM_ID = "bento-dialogue-project-form";

/**
 * Per-speaker colour, cycled by the speaker's position in the roster. Every
 * message is left-aligned (nobody is "me" in a persona↔persona dialogue) —
 * identity comes from a solid coloured avatar + the name + a tinted bubble, a
 * group-chat / channel look rather than a me-vs-them left/right split.
 */
const SPEAKER_PALETTE = [
  { avatar: "bg-blue-500", bg: "bg-blue-500/10", border: "border-blue-500/25", text: "text-blue-700 dark:text-blue-300" },
  { avatar: "bg-fuchsia-500", bg: "bg-fuchsia-500/10", border: "border-fuchsia-500/25", text: "text-fuchsia-700 dark:text-fuchsia-300" },
  { avatar: "bg-emerald-500", bg: "bg-emerald-500/10", border: "border-emerald-500/25", text: "text-emerald-700 dark:text-emerald-300" },
  { avatar: "bg-amber-500", bg: "bg-amber-500/10", border: "border-amber-500/25", text: "text-amber-700 dark:text-amber-300" },
  { avatar: "bg-violet-500", bg: "bg-violet-500/10", border: "border-violet-500/25", text: "text-violet-700 dark:text-violet-300" },
  { avatar: "bg-rose-500", bg: "bg-rose-500/10", border: "border-rose-500/25", text: "text-rose-700 dark:text-rose-300" },
];

/** Up to two initials from a persona name (first letters of its first words). */
function speakerInitials(name: string): string {
  const words = name.split(/[^\p{L}\p{N}]+/u).filter(Boolean);
  if (words.length === 0) return "?";
  return (words[0][0] + (words[1]?.[0] ?? "")).toUpperCase();
}

/**
 * Persona Dialogue — project workspace (Block AU / §XLIV, T706). Built on the
 * gold-standard {@link BentoEntityShell}: name + description are edited inline in
 * the hero, a scroll-linked Save bar commits the topic / turns / model / ordered
 * speaker roster, and Delete lives in the hero `⋮` menu (only for a saved
 * project). Running the dialogue + the live transcript are a separate block shown
 * only once the project exists and its edits are saved.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
export default function BentoPersonaDialogueWorkspacePage() {
  const { t } = useTranslation();
  const { projectId } = useParams();
  const isNew = projectId === "new";

  const { data: project } = useDialogueProject(isNew ? undefined : projectId);
  const updateMutation = useUpdateDialogueProject(projectId ?? "");
  const deleteMutation = useDeleteDialogueProject();

  const entity = {
    id: project?.id,
    title: project?.name ?? "",
    description: project?.description ?? "",
  };

  return (
    <BentoEntityShell
      entity={entity}
      isNew={isNew}
      headlineFallback={t("persona.dialogueProject.newTitle")}
      eyebrow={t("persona.dialogueProject.title")}
      listRoute={ROUTES.BENTO_PERSONA_DIALOGUE}
      icon={IconMasksTheater}
      tone="rose"
      hideIcon
      formId={FORM_ID}
      feature={t("persona.dialogueProject.feature")}
      onUpdate={
        isNew
          ? undefined
          : (next) =>
              updateMutation.mutateAsync({
                name: next.title,
                description: next.description,
                topic: project?.topic ?? null,
                turns: project?.turns ?? 10,
                llmInstanceId: project?.llmInstanceId ?? null,
              })
      }
      onDelete={isNew ? undefined : () => deleteMutation.mutateAsync(projectId ?? "")}
    >
      {(args) => (
        <DialogueEditor projectId={projectId} isNew={isNew} project={project} {...args} />
      )}
    </BentoEntityShell>
  );
}

// ---------------------------------------------------------------------------

interface DisplayTurn {
  personaId: string | null;
  personaName: string | null;
  content: string | null;
}

interface EditorProps extends BentoEntityShellRenderArgs {
  projectId?: string;
  isNew: boolean;
  project?: DialogueProject;
}

function DialogueEditor({ projectId, isNew, project, staged, onStateChange }: Readonly<EditorProps>) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { data: personaPool } = usePersonas();
  const { data: llmInstances } = useLlmInstances();
  const createMutation = useCreateDialogueProject();
  const updateMutation = useUpdateDialogueProject(projectId ?? "");

  // Local, immediately-reflected edit state (persisted only on Save).
  const [topic, setTopic] = useState("");
  // `turns` = number of rounds (each round = every persona speaks once).
  const [turns, setTurns] = useState(5);
  const [llmInstanceId, setLlmInstanceId] = useState("");
  const [speakerIds, setSpeakerIds] = useState<string[]>([]);
  const [saving, setSaving] = useState(false);

  // Run + transcript (only shown once the user runs, on a saved project).
  const [turnsList, setTurnsList] = useState<DisplayTurn[]>([]);
  const [running, setRunning] = useState(false);
  const [started, setStarted] = useState(false);
  const [runError, setRunError] = useState<string | null>(null);
  const abortRef = useRef<AbortController | null>(null);
  const bottomRef = useRef<HTMLDivElement | null>(null);

  // Keep the newest utterance in view so the conversation follows itself.
  useEffect(() => {
    if (started) {
      bottomRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
    }
  }, [turnsList.length, running, started]);

  // Seed local state from the loaded project.
  useEffect(() => {
    if (!project) return;
    setTopic(project.topic ?? "");
    setTurns(project.turns);
    setLlmInstanceId(project.llmInstanceId ?? "");
    setSpeakerIds(project.speakers.map((s) => s.id));
  }, [project]);

  useEffect(() => () => abortRef.current?.abort(), []);

  // Only speaker-capable personas (SPEAKER / BOTH, or legacy unset) can talk.
  const pool = useMemo(
    () =>
      (personaPool ?? [])
        .filter((p) => p.personaKind !== "AUDIENCE")
        .map((p) => ({ id: p.id, name: p.name })),
    [personaPool],
  );

  const savedSpeakerIds = useMemo(() => project?.speakers.map((s) => s.id) ?? [], [project]);
  const dirty =
    isNew ||
    topic !== (project?.topic ?? "") ||
    turns !== (project?.turns ?? 10) ||
    llmInstanceId !== (project?.llmInstanceId ?? "") ||
    JSON.stringify(speakerIds) !== JSON.stringify(savedSpeakerIds);

  useEffect(() => {
    onStateChange({ isDirty: dirty, isSubmitting: saving });
  }, [dirty, saving, onStateChange]);

  const handleSubmit = useCallback(
    async (e: React.FormEvent) => {
      e.preventDefault();
      if (!staged.title.trim()) {
        toast.error(t("forms.common.title"));
        return;
      }
      setSaving(true);
      try {
        const body = {
          name: staged.title,
          description: staged.description,
          topic,
          turns,
          llmInstanceId: llmInstanceId || null,
        };
        if (isNew) {
          const created = await createMutation.mutateAsync(body);
          if (speakerIds.length) {
            await dialogueProjectService.setSpeakers(created.id, speakerIds);
          }
          navigate(`${ROUTES.BENTO_PERSONA_DIALOGUE}/${created.id}`);
        } else if (projectId) {
          await updateMutation.mutateAsync(body);
          await dialogueProjectService.setSpeakers(projectId, speakerIds);
          invalidateDialogueProject(queryClient, projectId);
        }
      } catch {
        toast.error(t("persona.dialogueProject.runError"));
      } finally {
        setSaving(false);
      }
    },
    [staged, topic, turns, llmInstanceId, speakerIds, isNew, projectId, createMutation, updateMutation, navigate, queryClient, t],
  );

  // --- run ---
  const colorIndexById = useMemo(() => {
    const map = new Map<string, number>();
    speakerIds.forEach((id, i) => map.set(id, i));
    return map;
  }, [speakerIds]);

  const canRun =
    !isNew &&
    !dirty &&
    Boolean(topic.trim()) &&
    speakerIds.length >= 2 &&
    Boolean(llmInstanceId) &&
    !running;

  const runDialogue = useCallback(() => {
    if (!projectId || !canRun) return;
    abortRef.current?.abort();
    const ctrl = new AbortController();
    abortRef.current = ctrl;
    setRunning(true);
    setStarted(true);
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
        onDone: () => setRunning(false),
        onError: (err) => {
          setRunning(false);
          setRunError(err instanceof Error ? err.message : t("persona.dialogueProject.runError"));
        },
      },
      ctrl.signal,
    );
  }, [projectId, canRun, t]);

  const showSaveBar = true;

  return (
    <>
      <form id={FORM_ID} onSubmit={handleSubmit} className="flex flex-col gap-5">
        {showSaveBar && (
          <>
            <div aria-hidden className="bento-save-bar-spacer" />
            <div className="bento-fade-in fixed inset-x-0 top-20 z-20 mx-auto w-full max-w-7xl px-4 md:px-8">
              <BentoSaveBar
                title={staged.title || t("persona.dialogueProject.newTitle")}
                disabled={!staged.title.trim() || (!isNew && !dirty)}
                titleMissing={!staged.title.trim()}
                dirty={dirty}
                loading={saving}
                onCancel={() => navigate(ROUTES.BENTO_PERSONA_DIALOGUE)}
              />
            </div>
          </>
        )}

        {/* --- Topic, turns & model (first) --- */}
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
                placeholder={t("persona.dialogueProject.topicPlaceholder")}
              />
            </label>
            <div className="grid gap-4 sm:grid-cols-2">
              <label className="flex flex-col gap-1.5">
                <span className="text-sm font-medium">{t("persona.dialogueProject.turnsLabel")}</span>
                <Input
                  type="number"
                  min={1}
                  max={20}
                  value={turns}
                  onChange={(e) => setTurns(Number(e.target.value) || 5)}
                />
                <span className="text-xs text-muted-foreground">
                  {t("persona.dialogueProject.turnsDesc")}
                  {speakerIds.length >= 2 && (
                    <>
                      {" "}
                      <span className="font-medium">
                        {t("persona.dialogueProject.turnsPerPersona", {
                          rounds: turns,
                          personas: speakerIds.length,
                          total: turns * speakerIds.length,
                        })}
                      </span>
                    </>
                  )}
                </span>
              </label>
              <label className="flex flex-col gap-1.5">
                <span className="text-sm font-medium">{t("persona.dialogueProject.modelLabel")}</span>
                <Select value={llmInstanceId} onValueChange={setLlmInstanceId}>
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
          </div>
        </BentoFormSection>

        {/* --- Speakers (second) --- */}
        <BentoFormSection
          icon={IconUserCircle}
          tone="rose"
          title={t("persona.dialogueProject.speakersTitle")}
          description={t("persona.dialogueProject.speakersDesc")}
        >
          <PersonaSelectGrid
            ordered
            personas={pool}
            selectedIds={speakerIds}
            onChange={setSpeakerIds}
            emptyText={t("persona.dialogueProject.noSpeakers")}
          />
        </BentoFormSection>
      </form>

      {/* --- Run + transcript (saved projects only) --- */}
      {!isNew && (
        <div className="mt-5">
          <BentoFormSection
            icon={IconMessages}
            tone="violet"
            title={t("persona.dialogueProject.runTitle")}
            description={t("persona.dialogueProject.transcriptDesc")}
            trailing={
              <Button onClick={runDialogue} disabled={!canRun} size="sm" className="gap-2">
                {running ? <IconLoader2 className="size-4 animate-spin" /> : <IconPlayerPlayFilled className="size-4" />}
                {running
                  ? t("persona.dialogueProject.running")
                  : started
                    ? t("persona.dialogueProject.rerun")
                    : t("persona.dialogueProject.run")}
              </Button>
            }
          >
            {dirty && (
              <p className="mb-2 text-sm text-amber-600 dark:text-amber-400">
                {t("persona.dialogueProject.saveToRun")}
              </p>
            )}
            {!dirty && speakerIds.length < 2 && (
              <p className="mb-2 text-sm text-muted-foreground">{t("persona.dialogueProject.needTwo")}</p>
            )}
            {runError && <p className="mb-2 text-sm text-red-600">{runError}</p>}

            {!started ? (
              <p className="text-sm text-muted-foreground">{t("persona.dialogueProject.transcriptEmpty")}</p>
            ) : (
              <div className="flex flex-col gap-4">
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
                <div ref={bottomRef} aria-hidden />
              </div>
            )}
          </BentoFormSection>
        </div>
      )}
    </>
  );
}

function DialogueBubble({
  name,
  content,
  colorIndex,
}: Readonly<{ name: string; content: string; colorIndex: number }>) {
  const palette = SPEAKER_PALETTE[colorIndex % SPEAKER_PALETTE.length];
  return (
    <div className="flex gap-3">
      <span
        className={`grid size-9 shrink-0 place-items-center rounded-xl text-xs font-bold text-white shadow-sm ${palette.avatar}`}
        aria-hidden
      >
        {speakerInitials(name)}
      </span>
      <div className="min-w-0 flex-1">
        <div className={`mb-1 text-sm font-semibold ${palette.text}`}>{name}</div>
        <div
          className={`inline-block max-w-full rounded-2xl rounded-tl-md border px-4 py-2.5 ${palette.border} ${palette.bg}`}
        >
          <p className="whitespace-pre-wrap text-sm">{content}</p>
        </div>
      </div>
    </div>
  );
}
