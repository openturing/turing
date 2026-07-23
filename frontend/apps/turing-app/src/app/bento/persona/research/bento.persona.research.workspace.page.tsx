import {
  invalidateResearchStudy,
  personaResearchService,
  useCreateResearchStudy,
  useDeleteResearchStudy,
  useResearchStudy,
  useUpdateResearchStudy,
} from "@/api/queries/persona-research.queries";
import { useAiAgents } from "@/api/queries/ai-agent.queries";
import { useLlmInstances } from "@/api/queries/llm-instance.queries";
import { BatchCostConfirmDialog } from "@/components/llm/batch-cost-confirm-dialog";
import type { TurBatchCostRequest } from "@/models/cost-governance/cost-governance.model";
import { usePersonas } from "@/api/queries/persona.queries";
import { ROUTES } from "@/app/routes.const";
import { BentoActionsMenu, BentoFormSection, BentoHero } from "@/components/bento";
import { PersonaSelectGrid } from "@/components/persona/persona-select-grid";
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
import { Textarea } from "@/components/ui/textarea";
import {
  interviewStatusTone,
  RESEARCH_PROTOCOLS,
  RESEARCH_SCHEDULES,
  type ResearchInterview,
  type ResearchProtocol,
  type ResearchSchedule,
  type ResearchStudyRequest,
} from "@/models/persona/persona-research.model";
import { useQueryClient } from "@tanstack/react-query";
import {
  IconChevronDown,
  IconDeviceFloppy,
  IconLoader2,
  IconMessageQuestion,
  IconMessages,
  IconPlayerPlayFilled,
  IconPlus,
  IconRobot,
  IconSettings,
  IconSparkles,
  IconTrash,
  IconUserCircle,
  IconUsersGroup,
} from "@tabler/icons-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ResearchInsightsSection } from "./research-insights.section";

export default function BentoPersonaResearchWorkspacePage() {
  const { studyId } = useParams();
  if (studyId === "new") {
    return <CreateStudyCard />;
  }
  return <StudyWorkspace studyId={studyId as string} />;
}

// ---------------------------------------------------------------------------
// New-study create card.
// ---------------------------------------------------------------------------

function CreateStudyCard() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const create = useCreateResearchStudy();
  const [name, setName] = useState("");
  const [goal, setGoal] = useState("");
  const [hypothesis, setHypothesis] = useState("");
  const [protocol, setProtocol] = useState<ResearchProtocol>("DYNAMIC_SCRIPT");
  const [conceptText, setConceptText] = useState("");
  const [questionsText, setQuestionsText] = useState("");
  const [maxQuestions, setMaxQuestions] = useState(6);

  // Research Assistant (T732) — a skippable guided-authoring drawer.
  const [assistantBrief, setAssistantBrief] = useState("");
  const [proposing, setProposing] = useState(false);
  const [assistantMessage, setAssistantMessage] = useState<string | null>(null);
  const [assistantError, setAssistantError] = useState<string | null>(null);

  const questions = useMemo(
    () => questionsText.split("\n").map((q) => q.trim()).filter(Boolean),
    [questionsText],
  );

  const draftWithAssistant = useCallback(
    async (regenerate: boolean) => {
      if (!assistantBrief.trim() || proposing) return;
      setProposing(true);
      setAssistantError(null);
      try {
        const result = await personaResearchService.propose(assistantBrief.trim(), regenerate);
        if (result.success && result.proposal) {
          const p = result.proposal;
          setName(p.name);
          setGoal(p.goal);
          setHypothesis(p.hypothesis);
          setProtocol(p.protocol);
          setConceptText(p.conceptText);
          setQuestionsText((p.questions ?? []).join("\n"));
          setMaxQuestions(p.maxQuestions || 6);
          setAssistantMessage(p.assistantMessage || t("persona.research.assistant.drafted"));
        } else {
          setAssistantError(result.error ?? t("persona.research.assistant.failed"));
        }
      } catch {
        setAssistantError(t("persona.research.assistant.failed"));
      } finally {
        setProposing(false);
      }
    },
    [assistantBrief, proposing, t],
  );

  const submit = () => {
    const trimmed = name.trim();
    if (!trimmed) return;
    create.mutate(
      {
        name: trimmed,
        goal,
        hypothesis,
        enabled: true,
        protocol,
        conceptText,
        questions,
        maxQuestions,
        schedule: "MANUAL",
      },
      { onSuccess: (study) => navigate(`${ROUTES.BENTO_PERSONA_RESEARCH}/${study.id}`) },
    );
  };

  return (
    <>
      <BentoHero
        backTo={ROUTES.BENTO_PERSONA_RESEARCH}
        backLabel={t("persona.research.back")}
        leading={
          <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-violet-600 to-fuchsia-600 text-white shadow-md">
            <IconUsersGroup size={24} />
          </span>
        }
        title={t("persona.research.newTitle")}
        subtitle={t("persona.research.newHeroSubtitle")}
      />
      <div className="flex flex-col gap-5">
        {/* --- Research Assistant (skippable guided authoring) --- */}
        <BentoFormSection
          icon={IconSparkles}
          tone="indigo"
          title={t("persona.research.assistant.title")}
          description={t("persona.research.assistant.desc")}
        >
          <div className="flex flex-col gap-3">
            <Textarea
              value={assistantBrief}
              onChange={(e) => setAssistantBrief(e.target.value)}
              placeholder={t("persona.research.assistant.placeholder")}
              rows={3}
            />
            <div className="flex flex-wrap items-center gap-2">
              <Button
                type="button"
                onClick={() => draftWithAssistant(false)}
                disabled={!assistantBrief.trim() || proposing}
                className="gap-2"
              >
                {proposing ? <IconLoader2 className="size-4 animate-spin" /> : <IconSparkles className="size-4" />}
                {t("persona.research.assistant.draft")}
              </Button>
              {assistantMessage && !proposing && (
                <Button type="button" variant="outline" className="gap-2" onClick={() => draftWithAssistant(true)}>
                  <IconSparkles className="size-4" />
                  {t("persona.research.assistant.redraft")}
                </Button>
              )}
            </div>
            {assistantError && <p className="text-sm text-red-600">{assistantError}</p>}
            {assistantMessage && (
              <div className="rounded-2xl border border-indigo-500/25 bg-indigo-500/5 p-3 text-sm">
                {assistantMessage}
              </div>
            )}
            <p className="text-xs text-muted-foreground">{t("persona.research.assistant.skippable")}</p>
          </div>
        </BentoFormSection>

        {/* --- Study fields (edited directly or pre-filled by the assistant) --- */}
        <BentoFormSection
          icon={IconSettings}
          tone="violet"
          title={t("persona.research.studySection")}
          description={t("persona.research.studySectionDesc")}
        >
          <div className="flex flex-col gap-3">
            <label className="flex flex-col gap-1.5">
              <span className="text-sm font-medium">{t("persona.research.nameLabel")}</span>
              <Input
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder={t("persona.research.namePlaceholder")}
              />
            </label>
            <label className="flex flex-col gap-1.5">
              <span className="text-sm font-medium">{t("persona.research.goalLabel")}</span>
              <Textarea
                value={goal}
                onChange={(e) => setGoal(e.target.value)}
                placeholder={t("persona.research.goalPlaceholder")}
                rows={2}
              />
            </label>
            <label className="flex flex-col gap-1.5">
              <span className="text-sm font-medium">{t("persona.research.protocolLabel")}</span>
              <Select value={protocol} onValueChange={(v) => setProtocol(v as ResearchProtocol)}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {RESEARCH_PROTOCOLS.map((p) => (
                    <SelectItem key={p} value={p}>
                      {t(`persona.research.protocol.${p}`)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </label>
            {protocol === "CONCEPT_TEST" && (
              <label className="flex flex-col gap-1.5">
                <span className="text-sm font-medium">{t("persona.research.conceptLabel")}</span>
                <Textarea value={conceptText} onChange={(e) => setConceptText(e.target.value)} rows={3} placeholder={t("persona.research.conceptPlaceholder")} />
              </label>
            )}
            {protocol === "CUSTOM_SCRIPT" && (
              <label className="flex flex-col gap-1.5">
                <span className="text-sm font-medium">{t("persona.research.questionsLabel")}</span>
                <Textarea value={questionsText} onChange={(e) => setQuestionsText(e.target.value)} rows={5} placeholder={t("persona.research.questionsPlaceholder")} />
                <span className="text-xs text-muted-foreground">{t("persona.research.questionsHint", { count: questions.length })}</span>
              </label>
            )}
            <div>
              <Button onClick={submit} disabled={!name.trim() || create.isPending} className="gap-2">
                {create.isPending ? <IconLoader2 className="size-4 animate-spin" /> : <IconPlus className="size-4" />}
                {t("persona.research.createButton")}
              </Button>
            </div>
          </div>
        </BentoFormSection>
      </div>
    </>
  );
}

// ---------------------------------------------------------------------------
// Existing-study workspace.
// ---------------------------------------------------------------------------

function StudyWorkspace({ studyId }: Readonly<{ studyId: string }>) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const { data: study } = useResearchStudy(studyId);
  const { data: personaPool } = usePersonas();
  const { data: llmInstances } = useLlmInstances();
  const { data: agents } = useAiAgents();

  const updateStudy = useUpdateResearchStudy(studyId);
  const deleteStudy = useDeleteResearchStudy();

  // Local edit state (persisted on Save).
  const [name, setName] = useState("");
  const [goal, setGoal] = useState("");
  const [hypothesis, setHypothesis] = useState("");
  const [description, setDescription] = useState("");
  const [protocol, setProtocol] = useState<ResearchProtocol>("DYNAMIC_SCRIPT");
  const [conceptText, setConceptText] = useState("");
  const [questionsText, setQuestionsText] = useState("");
  const [maxQuestions, setMaxQuestions] = useState(6);
  const [targetAgentId, setTargetAgentId] = useState("");
  const [interviewLlm, setInterviewLlm] = useState("");
  const [synthesisLlm, setSynthesisLlm] = useState("");
  const [defaultLlm, setDefaultLlm] = useState("");
  const [schedule, setSchedule] = useState<ResearchSchedule>("MANUAL");
  const [saving, setSaving] = useState(false);

  // Run + live interview feed.
  const [feed, setFeed] = useState<Map<string, ResearchInterview>>(new Map());
  const [running, setRunning] = useState(false);
  const [runError, setRunError] = useState<string | null>(null);
  const [progress, setProgress] = useState<{ done: number; total: number }>({ done: 0, total: 0 });
  const [expanded, setExpanded] = useState<Set<string>>(new Set());
  const abortRef = useRef<AbortController | null>(null);
  const bottomRef = useRef<HTMLDivElement | null>(null);

  const personas = useMemo(() => study?.personas ?? [], [study]);
  const everRun = Boolean(study?.lastRunAt);

  // Seed local state from the loaded study.
  useEffect(() => {
    if (!study) return;
    setName(study.name);
    setGoal(study.goal ?? "");
    setHypothesis(study.hypothesis ?? "");
    setDescription(study.description ?? "");
    setProtocol(study.protocol);
    setConceptText(study.conceptText ?? "");
    setQuestionsText((study.questions ?? []).join("\n"));
    setMaxQuestions(study.maxQuestions || 6);
    setTargetAgentId(study.targetAgentId ?? "");
    setInterviewLlm(study.interviewLlmInstanceId ?? "");
    setSynthesisLlm(study.synthesisLlmInstanceId ?? "");
    setDefaultLlm(study.llmInstanceId ?? "");
    setSchedule(study.schedule);
  }, [study]);

  // Seed the feed from persisted interviews (unless a run is in flight).
  useEffect(() => {
    if (!study || running) return;
    const map = new Map<string, ResearchInterview>();
    (study.interviews ?? []).forEach((i) => map.set(i.id || i.personaId, i));
    setFeed(map);
  }, [study, running]);

  useEffect(() => () => abortRef.current?.abort(), []);

  useEffect(() => {
    if (running) bottomRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
  }, [feed, running]);

  const questions = useMemo(
    () => questionsText.split("\n").map((q) => q.trim()).filter(Boolean),
    [questionsText],
  );

  const dirty = useMemo(() => {
    if (!study) return false;
    return (
      name !== study.name ||
      goal !== (study.goal ?? "") ||
      hypothesis !== (study.hypothesis ?? "") ||
      description !== (study.description ?? "") ||
      protocol !== study.protocol ||
      conceptText !== (study.conceptText ?? "") ||
      JSON.stringify(questions) !== JSON.stringify(study.questions ?? []) ||
      maxQuestions !== (study.maxQuestions || 6) ||
      targetAgentId !== (study.targetAgentId ?? "") ||
      interviewLlm !== (study.interviewLlmInstanceId ?? "") ||
      synthesisLlm !== (study.synthesisLlmInstanceId ?? "") ||
      defaultLlm !== (study.llmInstanceId ?? "") ||
      schedule !== study.schedule
    );
  }, [study, name, goal, hypothesis, description, protocol, conceptText, questions, maxQuestions, targetAgentId, interviewLlm, synthesisLlm, defaultLlm, schedule]);

  const refresh = useCallback(
    () => invalidateResearchStudy(queryClient, studyId),
    [queryClient, studyId],
  );

  const saveStudy = useCallback(async () => {
    if (!study || !name.trim()) return;
    setSaving(true);
    try {
      const body: ResearchStudyRequest = {
        name: name.trim(),
        goal,
        hypothesis,
        description,
        enabled: study.enabled,
        protocol,
        conceptText,
        questions,
        maxQuestions,
        llmInstanceId: defaultLlm || null,
        targetAgentId: targetAgentId || null,
        interviewLlmInstanceId: interviewLlm || null,
        synthesisLlmInstanceId: synthesisLlm || null,
        schedule,
      };
      await updateStudy.mutateAsync(body);
      refresh();
    } finally {
      setSaving(false);
    }
  }, [study, name, goal, hypothesis, description, protocol, conceptText, questions, maxQuestions, defaultLlm, targetAgentId, interviewLlm, synthesisLlm, schedule, updateStudy, refresh]);

  const changePersonas = useCallback(
    async (ids: string[]) => {
      await personaResearchService.setPersonas(studyId, ids);
      refresh();
    },
    [studyId, refresh],
  );

  const runStudy = useCallback(() => {
    if (running || personas.length === 0 || dirty) return;
    abortRef.current?.abort();
    const ctrl = new AbortController();
    abortRef.current = ctrl;
    setRunning(true);
    setRunError(null);
    setFeed(new Map());
    setProgress({ done: 0, total: personas.length });

    personaResearchService.runStream(
      studyId,
      false,
      {
        onEvent: (event) => {
          if (event.type === "STARTED") {
            setProgress({ done: 0, total: event.total });
          } else if (event.type === "INTERVIEW" && event.interview) {
            const interview = event.interview;
            setFeed((prev) => new Map(prev).set(interview.id || interview.personaId, interview));
            setProgress({ done: event.completed, total: event.total });
          }
        },
        onDone: () => {
          setRunning(false);
          refresh();
        },
        onError: (err) => {
          setRunning(false);
          setRunError(err instanceof Error ? err.message : t("persona.research.runError"));
        },
      },
      ctrl.signal,
    );
  }, [studyId, running, personas.length, dirty, refresh, t]);

  const removeStudy = useCallback(() => {
    deleteStudy.mutate(studyId, { onSuccess: () => navigate(ROUTES.BENTO_PERSONA_RESEARCH) });
  }, [deleteStudy, studyId, navigate]);

  const toggleExpanded = useCallback((id: string) => {
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }, []);

  const pool = useMemo(
    () =>
      (personaPool ?? []).map((p) => ({
        id: p.id,
        name: p.name,
        subtitle: t(`persona.research.kind.${p.personaKind ?? "SPEAKER"}`),
      })),
    [personaPool, t],
  );
  const selectedPersonaIds = useMemo(() => personas.map((p) => p.id), [personas]);
  const feedList = useMemo(() => Array.from(feed.values()), [feed]);

  let runLabel = t("persona.research.run");
  if (running) runLabel = t("persona.research.running");
  else if (everRun) runLabel = t("persona.research.rerun");

  const canRun = personas.length > 0 && !running && !dirty;

  // T783 — pre-flight cost gate before an expensive study run. Estimate itemCount
  // as personas × the max interview turns, priced against the interview lane's
  // (or default) model. Vendor id lower-cased to match the catalog price table.
  const [costConfirmOpen, setCostConfirmOpen] = useState(false);
  const batchParams = useMemo<TurBatchCostRequest | null>(() => {
    const runId = interviewLlm || defaultLlm;
    const inst = (llmInstances ?? []).find((i) => i.id === runId);
    const vendorId = inst?.turLLMVendor?.id;
    if (!inst || !vendorId || !inst.modelName || personas.length === 0) return null;
    return {
      vendorId: vendorId.toLowerCase(),
      modelName: inst.modelName,
      itemCount: personas.length * Math.max(1, maxQuestions),
      inputTokensPerItem: 1500,
      outputTokensPerItem: 400,
    };
  }, [llmInstances, interviewLlm, defaultLlm, personas.length, maxQuestions]);

  return (
    <>
      <BentoHero
        backTo={ROUTES.BENTO_PERSONA_RESEARCH}
        backLabel={t("persona.research.back")}
        leading={
          <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-violet-600 to-fuchsia-600 text-white shadow-md">
            <IconUsersGroup size={24} />
          </span>
        }
        title={name || t("persona.research.defaultTitle")}
        subtitle={goal || description || t("persona.research.defaultSubtitle")}
        trailing={
          <div className="flex items-center gap-2">
            <Button
              onClick={() => (batchParams ? setCostConfirmOpen(true) : runStudy())}
              disabled={!canRun}
              className="gap-2"
            >
              {running ? <IconLoader2 className="size-4 animate-spin" /> : <IconPlayerPlayFilled className="size-4" />}
              {runLabel}
            </Button>
            <BentoActionsMenu
              actions={[{
                label: t("persona.research.deleteStudy"),
                icon: IconTrash,
                tone: "destructive",
                onSelect: removeStudy,
              }]}
            />
          </div>
        }
      />

      <div className="flex flex-col gap-5">
        {/* --- Protocol, goal & model --- */}
        <BentoFormSection
          icon={IconMessageQuestion}
          tone="violet"
          title={t("persona.research.protocolTitle")}
          description={t("persona.research.protocolSectionDesc")}
          trailing={
            <Button type="button" size="sm" className="gap-1.5" onClick={saveStudy} disabled={!dirty || !name.trim() || saving}>
              {saving ? <IconLoader2 className="size-4 animate-spin" /> : <IconDeviceFloppy className="size-4" />}
              {t("persona.research.save")}
            </Button>
          }
        >
          <div className="flex flex-col gap-4">
            <div className="grid gap-4 sm:grid-cols-2">
              <label className="flex flex-col gap-1.5">
                <span className="text-sm font-medium">{t("persona.research.nameLabel")}</span>
                <Input value={name} onChange={(e) => setName(e.target.value)} />
              </label>
              <label className="flex flex-col gap-1.5">
                <span className="text-sm font-medium">{t("persona.research.protocolLabel")}</span>
                <Select value={protocol} onValueChange={(v) => setProtocol(v as ResearchProtocol)}>
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {RESEARCH_PROTOCOLS.map((p) => (
                      <SelectItem key={p} value={p}>
                        {t(`persona.research.protocol.${p}`)}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </label>
            </div>

            <label className="flex flex-col gap-1.5">
              <span className="text-sm font-medium">{t("persona.research.goalLabel")}</span>
              <Textarea value={goal} onChange={(e) => setGoal(e.target.value)} rows={2} placeholder={t("persona.research.goalPlaceholder")} />
            </label>
            <label className="flex flex-col gap-1.5">
              <span className="text-sm font-medium">{t("persona.research.hypothesisLabel")}</span>
              <Textarea value={hypothesis} onChange={(e) => setHypothesis(e.target.value)} rows={2} placeholder={t("persona.research.hypothesisPlaceholder")} />
            </label>

            {protocol === "CONCEPT_TEST" && (
              <label className="flex flex-col gap-1.5">
                <span className="text-sm font-medium">{t("persona.research.conceptLabel")}</span>
                <Textarea value={conceptText} onChange={(e) => setConceptText(e.target.value)} rows={3} placeholder={t("persona.research.conceptPlaceholder")} />
              </label>
            )}

            {protocol === "CUSTOM_SCRIPT" && (
              <label className="flex flex-col gap-1.5">
                <span className="text-sm font-medium">{t("persona.research.questionsLabel")}</span>
                <Textarea value={questionsText} onChange={(e) => setQuestionsText(e.target.value)} rows={5} placeholder={t("persona.research.questionsPlaceholder")} />
                <span className="text-xs text-muted-foreground">{t("persona.research.questionsHint", { count: questions.length })}</span>
              </label>
            )}

            {protocol !== "CUSTOM_SCRIPT" && (
              <label className="flex max-w-xs flex-col gap-1.5">
                <span className="text-sm font-medium">{t("persona.research.maxQuestionsLabel")}</span>
                <Input type="number" min={1} max={30} value={maxQuestions} onChange={(e) => setMaxQuestions(Number(e.target.value) || 6)} />
                <span className="text-xs text-muted-foreground">{t("persona.research.maxQuestionsHint")}</span>
              </label>
            )}

            <div className="grid gap-4 sm:grid-cols-2">
              <label className="flex flex-col gap-1.5">
                <span className="flex items-center gap-1.5 text-sm font-medium">
                  <IconRobot size={15} /> {t("persona.research.targetLabel")}
                </span>
                <Select value={targetAgentId || "__llm"} onValueChange={(v) => setTargetAgentId(v === "__llm" ? "" : v)}>
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="__llm">{t("persona.research.targetDefaultLlm")}</SelectItem>
                    {(agents ?? []).map((agent) => (
                      <SelectItem key={agent.id} value={agent.id}>
                        {agent.title}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <span className="text-xs text-muted-foreground">{t("persona.research.targetHint")}</span>
              </label>
              <label className="flex flex-col gap-1.5">
                <span className="text-sm font-medium">{t("persona.research.scheduleLabel")}</span>
                <Select value={schedule} onValueChange={(v) => setSchedule(v as ResearchSchedule)}>
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {RESEARCH_SCHEDULES.map((s) => (
                      <SelectItem key={s} value={s}>
                        {t(`persona.research.schedule.${s}`)}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </label>
            </div>

            {/* Big Shuffle — per-stage model lanes (T728). */}
            <div className="grid gap-4 sm:grid-cols-3">
              <label className="flex flex-col gap-1.5">
                <span className="text-sm font-medium">{t("persona.research.interviewLlmLabel")}</span>
                <LlmSelect value={interviewLlm} onChange={setInterviewLlm} instances={llmInstances} defaultLabel={t("persona.research.laneDefault")} />
              </label>
              <label className="flex flex-col gap-1.5">
                <span className="text-sm font-medium">{t("persona.research.synthesisLlmLabel")}</span>
                <LlmSelect value={synthesisLlm} onChange={setSynthesisLlm} instances={llmInstances} defaultLabel={t("persona.research.laneDefault")} />
              </label>
              <label className="flex flex-col gap-1.5">
                <span className="text-sm font-medium">{t("persona.research.modelLabel")}</span>
                <LlmSelect value={defaultLlm} onChange={setDefaultLlm} instances={llmInstances} defaultLabel={t("persona.research.defaultLlm")} />
              </label>
            </div>
          </div>
        </BentoFormSection>

        {/* --- Audience roster --- */}
        <BentoFormSection
          icon={IconUserCircle}
          tone="rose"
          title={t("persona.research.audienceTitle")}
          description={t("persona.research.audienceDesc")}
          trailing={
            <div className="flex items-center gap-2">
              <Button asChild variant="outline" size="sm" className="gap-1.5">
                <Link to={ROUTES.BENTO_PERSONA_COHORT}>
                  <IconSparkles className="size-4" />
                  {t("persona.research.synthesizeCohort")}
                </Link>
              </Button>
              <Badge variant="secondary">{personas.length}</Badge>
            </div>
          }
        >
          <PersonaSelectGrid
            ordered
            personas={pool}
            selectedIds={selectedPersonaIds}
            onChange={changePersonas}
            emptyText={t("persona.research.noPersonas")}
          />
        </BentoFormSection>

        {/* --- Run + live interview feed --- */}
        <BentoFormSection
          icon={IconMessages}
          tone="blue"
          title={t("persona.research.feedTitle")}
          description={t("persona.research.feedDesc")}
          trailing={
            running ? (
              <span className="flex items-center gap-2 text-xs text-muted-foreground">
                <IconLoader2 className="size-3.5 animate-spin" />
                {progress.done}/{progress.total}
              </span>
            ) : undefined
          }
        >
          {dirty && (
            <p className="mb-2 text-sm text-amber-600 dark:text-amber-400">{t("persona.research.saveToRun")}</p>
          )}
          {!dirty && personas.length === 0 && (
            <p className="mb-2 text-sm text-muted-foreground">{t("persona.research.needAudience")}</p>
          )}
          {runError && <p className="mb-2 text-sm text-red-600">{runError}</p>}

          {running && <Progress className="mb-3" value={progress.total ? (progress.done / progress.total) * 100 : 0} />}

          {feedList.length === 0 && !running ? (
            <p className="text-sm text-muted-foreground">{t("persona.research.feedEmpty")}</p>
          ) : (
            <div className="flex flex-col gap-2.5">
              {feedList.map((interview) => {
                const id = interview.id || interview.personaId;
                const isOpen = expanded.has(id);
                return (
                  <div key={id} className="bento-tile rounded-2xl border border-border/60 bg-card/40 backdrop-blur">
                    <button
                      type="button"
                      onClick={() => toggleExpanded(id)}
                      className="flex w-full items-center gap-3 px-3 py-2.5 text-left"
                    >
                      <span className="grid size-9 shrink-0 place-items-center rounded-xl bg-linear-to-br from-blue-600 to-indigo-600 text-xs font-bold text-white">
                        {(interview.personaName || "?").slice(0, 2).toUpperCase()}
                      </span>
                      <div className="min-w-0 flex-1">
                        <div className="flex items-center gap-2">
                          <span className="truncate text-sm font-medium">{interview.personaName}</span>
                          <span className={`rounded-full border px-2 py-0.5 text-[10px] ${interviewStatusTone(interview.status)}`}>
                            {t(`persona.research.status.${interview.status}`)}
                          </span>
                        </div>
                        <p className="text-xs text-muted-foreground">
                          {interview.error
                            ? interview.error
                            : t("persona.research.turnCount", { count: interview.turnCount })}
                        </p>
                      </div>
                      {interview.turns.length > 0 && (
                        <IconChevronDown
                          size={16}
                          className={`shrink-0 text-muted-foreground transition-transform ${isOpen ? "rotate-180" : ""}`}
                        />
                      )}
                    </button>
                    {isOpen && interview.turns.length > 0 && (
                      <div className="flex flex-col gap-3 border-t border-border/50 px-4 py-3">
                        {interview.turns.map((turn) => (
                          <div key={turn.index} className="flex flex-col gap-1">
                            <p className="text-sm font-medium text-blue-700 dark:text-blue-300">{turn.question}</p>
                            <p className="whitespace-pre-wrap text-sm text-muted-foreground">{turn.answer}</p>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                );
              })}
              {running && (
                <p className="flex items-center gap-2 text-sm text-muted-foreground">
                  <IconLoader2 className="size-4 animate-spin" />
                  {t("persona.research.interviewing")}
                </p>
              )}
              <div ref={bottomRef} aria-hidden />
            </div>
          )}
        </BentoFormSection>

        {/* --- Synthesized read surfaces (report, saturation, graph, drift, concept-fit, eval bridge) --- */}
        <ResearchInsightsSection
          studyId={studyId}
          studyName={name}
          isConceptTest={protocol === "CONCEPT_TEST"}
          everRun={everRun}
        />
      </div>

      <BatchCostConfirmDialog
        open={costConfirmOpen}
        params={batchParams}
        onProceed={() => { setCostConfirmOpen(false); runStudy(); }}
        onCancel={() => setCostConfirmOpen(false)}
      />
    </>
  );
}

// ---------------------------------------------------------------------------

function LlmSelect({
  value,
  onChange,
  instances,
  defaultLabel,
}: Readonly<{
  value: string;
  onChange: (v: string) => void;
  instances?: { id: string; title: string }[];
  defaultLabel: string;
}>) {
  return (
    <Select value={value || "__default"} onValueChange={(v) => onChange(v === "__default" ? "" : v)}>
      <SelectTrigger>
        <SelectValue placeholder={defaultLabel} />
      </SelectTrigger>
      <SelectContent>
        <SelectItem value="__default">{defaultLabel}</SelectItem>
        {(instances ?? []).map((llm) => (
          <SelectItem key={llm.id} value={llm.id}>
            {llm.title}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}
