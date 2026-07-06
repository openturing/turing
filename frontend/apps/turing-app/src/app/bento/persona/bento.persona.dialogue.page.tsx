import { usePersonas, useRunPersonaDialogue } from "@/api/queries/persona.queries";
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
import type { TurPersona } from "@/models/persona/persona.model.ts";
import { useTuringLlmInstances } from "@viglet/turing-react-sdk";
import {
  IconAlertTriangle,
  IconMasksTheater,
  IconMessages,
  IconPlayerPlayFilled,
  IconSettings,
} from "@tabler/icons-react";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useSearchParams } from "react-router-dom";

const LLM_STORAGE_KEY = "turing.persona.dialogue.llm";

/**
 * Global persona↔persona dialogue — Block AI / §XXXII.8 (T585). A dialogue is
 * inherently about **two** peer personas plus a topic (neither "owns" it), so —
 * unlike content-fit (T584) — this is a **global** surface reached from the
 * persona list, not scoped to one persona instance. Pick a topic, two speaker
 * personas, an LLM and a turn budget (default 10); the backend
 * ({@code TurPersonaDialogueService}) drives the shared chat executor turn by
 * turn — each speaker must end with a question so the conversation self-sustains
 * — and returns the whole transcript, rendered here as a two-sided thread.
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
  const runDialogue = useRunPersonaDialogue();

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

  const [topic, setTopic] = useState("");
  const [turns, setTurns] = useState(10);
  const [personaAId, setPersonaAId] = useState("");
  const [personaBId, setPersonaBId] = useState("");

  // Default the two speakers once personas load: A honours a `?a=` deep link
  // (from a persona's launchpad), then falls back to the first two distinct
  // speakers.
  useEffect(() => {
    if (speakers.length === 0) return;
    const preferredA = searchParams.get("a");
    setPersonaAId((prev) => prev || (preferredA && speakers.some((p) => p.id === preferredA) ? preferredA : speakers[0].id));
    setPersonaBId((prev) => prev || speakers.find((p) => p.id !== (preferredA ?? speakers[0].id))?.id || "");
  }, [speakers, searchParams]);

  const canRun =
    Boolean(topic.trim()) &&
    Boolean(personaAId) &&
    Boolean(personaBId) &&
    Boolean(effectiveLlmId) &&
    !runDialogue.isPending;

  function run() {
    runDialogue.mutate({
      topic: topic.trim(),
      personaAId,
      personaBId,
      llmInstanceId: effectiveLlmId,
      turns,
    });
  }

  const transcript = runDialogue.data;
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
          defaultValue: "Pick a topic and two personas and watch them talk it through, turn by turn.",
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

              <div className="grid gap-4 sm:grid-cols-2">
                <PersonaSelect
                  label={t("persona.dialogue.personaA", { defaultValue: "First speaker" })}
                  value={personaAId}
                  onChange={setPersonaAId}
                  speakers={speakers}
                />
                <PersonaSelect
                  label={t("persona.dialogue.personaB", { defaultValue: "Second speaker" })}
                  value={personaBId}
                  onChange={setPersonaBId}
                  speakers={speakers}
                />
              </div>

              <div className="grid gap-4 sm:grid-cols-2">
                <label className="flex flex-col gap-1.5">
                  <span className="text-sm font-medium">{t("persona.dialogue.turns", { defaultValue: "Turns" })}</span>
                  <Input
                    type="number"
                    min={2}
                    max={20}
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
                  <IconPlayerPlayFilled className="size-4" />
                  {runDialogue.isPending
                    ? t("persona.dialogue.running", { defaultValue: "Talking…" })
                    : t("persona.dialogue.run", { defaultValue: "Start dialogue" })}
                </Button>
              </div>
            </div>
          )}
        </BentoFormSection>

        {transcript && (
          <BentoFormSection
            icon={IconMessages}
            tone="violet"
            title={t("persona.dialogue.transcriptTitle", { defaultValue: "Transcript" })}
            description={transcript.topic}
          >
            {transcript.transcript.length === 0 ? (
              <p className="text-sm text-muted-foreground">
                {t("persona.dialogue.empty", { defaultValue: "No turns were produced." })}
              </p>
            ) : (
              <div className="flex flex-col gap-3">
                {transcript.transcript.map((turn, i) => (
                  <DialogueBubble
                    key={`${turn.personaId}-${i}`}
                    turn={turn}
                    left={turn.personaId === transcript.personaA.id}
                  />
                ))}
              </div>
            )}
            {transcript.llmError && (
              <p className="mt-3 flex items-center gap-1 text-xs text-amber-600 dark:text-amber-400">
                <IconAlertTriangle className="size-3.5" />
                {t("persona.dialogue.partial", {
                  defaultValue: "The dialogue stopped early: {{error}}",
                  error: transcript.error ?? "",
                })}
              </p>
            )}
          </BentoFormSection>
        )}
      </div>
    </>
  );
}

function PersonaSelect({
  label,
  value,
  onChange,
  speakers,
}: Readonly<{
  label: string;
  value: string;
  onChange: (id: string) => void;
  speakers: TurPersona[];
}>) {
  return (
    <label className="flex flex-col gap-1.5">
      <span className="text-sm font-medium">{label}</span>
      <Select value={value} onValueChange={onChange}>
        <SelectTrigger>
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          {speakers.map((persona) => (
            <SelectItem key={persona.id} value={persona.id}>
              {persona.name}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </label>
  );
}

function DialogueBubble({
  turn,
  left,
}: Readonly<{ turn: { personaName: string; content: string }; left: boolean }>) {
  return (
    <div className={`flex ${left ? "justify-start" : "justify-end"}`}>
      <div
        className={`max-w-[85%] rounded-2xl border px-4 py-3 ${
          left
            ? "border-blue-500/30 bg-blue-500/10"
            : "border-fuchsia-500/30 bg-fuchsia-500/10"
        }`}
      >
        <div
          className={`mb-1 text-xs font-semibold ${
            left ? "text-blue-700 dark:text-blue-300" : "text-fuchsia-700 dark:text-fuchsia-300"
          }`}
        >
          {turn.personaName}
        </div>
        <p className="whitespace-pre-wrap text-sm">{turn.content}</p>
      </div>
    </div>
  );
}
