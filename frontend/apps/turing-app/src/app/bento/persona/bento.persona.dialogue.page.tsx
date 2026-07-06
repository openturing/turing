import { usePersonas } from "@/api/queries/persona.queries";
import { DEFAULT_CONTEXT_WINDOW } from "@/app/console/chat/chat.types";
import { ROUTES } from "@/app/routes.const";
import { BentoFormSection, BentoHero } from "@/components/bento";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type { TurDialogueEvent } from "@/models/persona/persona-dialogue.model";
import type { TurPersona } from "@/models/persona/persona.model.ts";
import { TurPersonaDialogueService } from "@/services/persona/persona-dialogue.service";
import { useTuringLlmInstances } from "@viglet/turing-react-sdk";
import {
  IconAlertTriangle,
  IconLoader2,
  IconMasksTheater,
  IconMessages,
  IconPlayerPlayFilled,
  IconPlus,
  IconSettings,
  IconTrash,
} from "@tabler/icons-react";
import { useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useSearchParams } from "react-router-dom";

const LLM_STORAGE_KEY = "turing.persona.dialogue.llm";
const dialogueService = new TurPersonaDialogueService();

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
 * Global persona↔persona dialogue — Block AI / §XXXII.8 (T585; N-persona + live
 * streaming in T604). A dialogue is about **peer** personas plus a topic (none
 * "owns" it), so this is a **global** surface reached from the persona list, not
 * scoped to one persona. Pick a topic, **two or more** speaker personas, an LLM
 * and a turn budget (default 10); the backend drives the shared chat executor
 * round-robin — each speaker must end with a question so the conversation
 * self-sustains — and **streams** each turn as it lands (SSE), so the transcript
 * builds live rather than arriving all at once.
 *
 * A persona's own launchpad can deep-link here with `?a=<personaId>` to
 * pre-select the first speaker.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
export default function BentoPersonaDialoguePage() {
  const { t } = useTranslation();
  const [searchParams] = useSearchParams();
  const { data: personas } = usePersonas();

  const {
    instances: llmInstances,
    loaded: llmLoaded,
    selectedId: selectedLlmId,
    select: selectLlm,
  } = useTuringLlmInstances({
    persistKey: LLM_STORAGE_KEY,
    defaultContextWindow: DEFAULT_CONTEXT_WINDOW,
  });
  const effectiveLlmId = selectedLlmId || llmInstances[0]?.id || "";

  // Only speaker-capable personas (SPEAKER/BOTH, or legacy unset) can talk.
  const speakers = useMemo<TurPersona[]>(
    () => (personas ?? []).filter((p) => p.personaKind !== "AUDIENCE"),
    [personas],
  );
  const nameById = useMemo(() => {
    const map = new Map<string, string>();
    speakers.forEach((p) => map.set(p.id, p.name));
    return map;
  }, [speakers]);

  const [topic, setTopic] = useState("");
  const [turns, setTurns] = useState(10);
  // The ordered roster of speakers — two or more slots (ids may be empty until
  // picked). The user can add/remove slots.
  const [personaIds, setPersonaIds] = useState<string[]>(["", ""]);

  const [liveTurns, setLiveTurns] = useState<TurDialogueEvent[]>([]);
  const [running, setRunning] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  // Snapshot of the roster used for the active run — drives the bubble colours
  // so they stay stable even if the form is edited mid/after a run.
  const [runRoster, setRunRoster] = useState<string[]>([]);
  const abortRef = useRef<AbortController | null>(null);

  // Default the first two slots once personas load: slot 0 honours a `?a=` deep
  // link (from a persona's launchpad), slot 1 the next distinct speaker.
  useEffect(() => {
    if (speakers.length === 0) return;
    setPersonaIds((prev) => {
      if (prev.some(Boolean)) return prev;
      const preferredA = searchParams.get("a");
      const a = preferredA && speakers.some((p) => p.id === preferredA) ? preferredA : speakers[0].id;
      const b = speakers.find((p) => p.id !== a)?.id ?? "";
      return [a, b];
    });
  }, [speakers, searchParams]);

  // Abort an in-flight stream on unmount.
  useEffect(() => () => abortRef.current?.abort(), []);

  const filledIds = personaIds.filter(Boolean);
  const canRun =
    Boolean(topic.trim()) &&
    filledIds.length >= 2 &&
    Boolean(effectiveLlmId) &&
    !running;

  function setSlot(index: number, id: string) {
    setPersonaIds((prev) => prev.map((v, i) => (i === index ? id : v)));
  }
  function addSlot() {
    setPersonaIds((prev) => [...prev, ""]);
  }
  function removeSlot(index: number) {
    setPersonaIds((prev) => (prev.length <= 2 ? prev : prev.filter((_, i) => i !== index)));
  }

  function run() {
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;
    setLiveTurns([]);
    setErrorMsg(null);
    setRunning(true);
    setRunRoster(filledIds);
    dialogueService.stream(
      { topic: topic.trim(), personaIds: filledIds, llmInstanceId: effectiveLlmId, turns },
      {
        onTurn: (event) => setLiveTurns((prev) => [...prev, event]),
        onDone: () => setRunning(false),
        onError: (_err, event) => {
          setErrorMsg(event?.error ?? t("persona.dialogue.failed", { defaultValue: "The dialogue failed." }));
          setRunning(false);
        },
      },
      controller.signal,
    );
  }

  // Stable colour per speaker: index in the unique run roster → palette slot.
  const colorIndexById = useMemo(() => {
    const unique = Array.from(new Set(runRoster));
    const map = new Map<string, number>();
    unique.forEach((id, i) => map.set(id, i));
    return map;
  }, [runRoster]);

  const noSpeakers = personas !== undefined && speakers.length < 2;
  const noLlm = llmLoaded && llmInstances.length === 0;

  return (
    <>
      <BentoHero
        eyebrow={
          <Link to={ROUTES.BENTO_PERSONA_INSTANCE} className="hover:text-foreground">
            {t("persona.title")}
          </Link>
        }
        leading={
          <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-fuchsia-500 to-pink-600 text-white shadow-md">
            <IconMasksTheater size={24} />
          </span>
        }
        title={t("persona.dialogue.title", { defaultValue: "Persona dialogue" })}
        subtitle={t("persona.dialogue.subtitle", {
          defaultValue: "Pick a topic and two or more personas and watch them talk it through, turn by turn.",
        })}
      />

      <div className="mt-6 flex flex-col gap-5">
        <BentoFormSection
          icon={IconSettings}
          tone="rose"
          title={t("persona.dialogue.setupTitle", { defaultValue: "Set up the conversation" })}
          description={t("persona.dialogue.setupDesc", {
            defaultValue: "Each persona must end every reply with a question, so the dialogue keeps flowing.",
          })}
        >
          {noSpeakers ? (
            <p className="text-sm text-muted-foreground">
              {t("persona.dialogue.needTwo", {
                defaultValue: "You need at least two speaker personas (SPEAKER or BOTH) to run a dialogue.",
              })}
            </p>
          ) : (
            <div className="flex flex-col gap-4">
              <label className="flex flex-col gap-1.5">
                <span className="text-sm font-medium">{t("persona.dialogue.topic", { defaultValue: "Topic" })}</span>
                <Input
                  value={topic}
                  onChange={(e) => setTopic(e.target.value)}
                  placeholder={t("persona.dialogue.topicPlaceholder", {
                    defaultValue: "e.g. Should a startup adopt a four-day work week?",
                  })}
                />
              </label>

              <div className="flex flex-col gap-2">
                <span className="text-sm font-medium">
                  {t("persona.dialogue.speakers", { defaultValue: "Speakers" })}
                </span>
                {personaIds.map((id, index) => (
                  // eslint-disable-next-line react/no-array-index-key -- roster slots are positional
                  <div key={index} className="flex items-center gap-2">
                    <span className="w-6 shrink-0 text-center text-xs text-muted-foreground">{index + 1}</span>
                    <div className="flex-1">
                      <Select value={id} onValueChange={(v) => setSlot(index, v)}>
                        <SelectTrigger>
                          <SelectValue placeholder={t("persona.dialogue.selectPersona", { defaultValue: "Select a persona" })} />
                        </SelectTrigger>
                        <SelectContent>
                          {speakers.map((persona) => (
                            <SelectItem key={persona.id} value={persona.id}>
                              {persona.name}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </div>
                    <Button
                      type="button"
                      size="icon"
                      variant="ghost"
                      disabled={personaIds.length <= 2}
                      title={t("persona.dialogue.removeSpeaker", { defaultValue: "Remove speaker" })}
                      onClick={() => removeSlot(index)}
                    >
                      <IconTrash className="size-4 text-red-600" />
                    </Button>
                  </div>
                ))}
                <div>
                  <Button type="button" variant="outline" size="sm" onClick={addSlot}>
                    <IconPlus className="size-4" />
                    {t("persona.dialogue.addSpeaker", { defaultValue: "Add persona" })}
                  </Button>
                </div>
              </div>

              <div className="grid gap-4 sm:grid-cols-2">
                <label className="flex flex-col gap-1.5">
                  <span className="text-sm font-medium">{t("persona.dialogue.turns", { defaultValue: "Turns" })}</span>
                  <Input
                    type="number"
                    min={2}
                    max={40}
                    value={turns}
                    onChange={(e) => setTurns(Number(e.target.value) || 10)}
                  />
                </label>
                <label className="flex flex-col gap-1.5">
                  <span className="text-sm font-medium">{t("persona.dialogue.model", { defaultValue: "Model" })}</span>
                  <Select value={effectiveLlmId} onValueChange={selectLlm} disabled={noLlm}>
                    <SelectTrigger>
                      <SelectValue placeholder={t("persona.dialogue.selectModel", { defaultValue: "Select a model" })} />
                    </SelectTrigger>
                    <SelectContent>
                      {llmInstances.map((inst) => (
                        <SelectItem key={inst.id} value={inst.id}>
                          {inst.title}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </label>
              </div>

              {noLlm && (
                <p className="flex items-center gap-1 text-xs text-muted-foreground">
                  <IconAlertTriangle className="size-3.5" />
                  {t("persona.dialogue.noLlm", { defaultValue: "No enabled LLM instance is available." })}
                </p>
              )}

              <div>
                <Button type="button" disabled={!canRun} onClick={run}>
                  {running ? <IconLoader2 className="size-4 animate-spin" /> : <IconPlayerPlayFilled className="size-4" />}
                  {running
                    ? t("persona.dialogue.running", { defaultValue: "Talking…" })
                    : t("persona.dialogue.run", { defaultValue: "Start dialogue" })}
                </Button>
              </div>
            </div>
          )}
        </BentoFormSection>

        {(liveTurns.length > 0 || running || errorMsg) && (
          <BentoFormSection
            icon={IconMessages}
            tone="violet"
            title={t("persona.dialogue.transcriptTitle", { defaultValue: "Transcript" })}
            description={topic}
          >
            <div className="flex flex-col gap-3">
              {liveTurns.map((turn, i) => (
                <DialogueBubble
                  key={`${turn.personaId}-${turn.index ?? i}`}
                  name={turn.personaName ?? nameById.get(turn.personaId ?? "") ?? ""}
                  content={turn.content ?? ""}
                  colorIndex={colorIndexById.get(turn.personaId ?? "") ?? 0}
                />
              ))}
              {running && (
                <p className="flex items-center gap-2 text-sm text-muted-foreground">
                  <IconLoader2 className="size-4 animate-spin" />
                  {t("persona.dialogue.thinking", { defaultValue: "The next persona is thinking…" })}
                </p>
              )}
            </div>
            {errorMsg && (
              <p className="mt-3 flex items-center gap-1 text-xs text-amber-600 dark:text-amber-400">
                <IconAlertTriangle className="size-3.5" />
                {t("persona.dialogue.partial", {
                  defaultValue: "The dialogue stopped early: {{error}}",
                  error: errorMsg,
                })}
              </p>
            )}
          </BentoFormSection>
        )}
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
