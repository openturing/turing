import { Input } from "@/components/ui/input";
import { PromptEditor } from "@/components/ui/prompt-editor";
import { SectionCard } from "@/components/ui/section-card";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { TabsContent } from "@/components/ui/tabs";
import { IconArrowsRightLeft, IconBolt, IconSettings } from "@tabler/icons-react";
import { Switch } from "@/components/ui/switch";
import { Textarea } from "@/components/ui/textarea";
import { useTranslation } from "react-i18next";

import {
  CHAT_FLOW_CAPTURE_MODES,
  CHAT_FLOW_GUARDRAIL_METHODS,
  CHAT_FLOW_TRIGGER_LANGUAGES,
  CHAT_FLOW_TRIGGER_MODES,
  type TurChatFlowCaptureMode,
  type TurChatFlowGuardrailMethod,
  type TurChatFlowTriggerLanguage,
  type TurChatFlowTriggerMode,
} from "@/models/agent/chat-flow.model";

import { ChatFlowEvalGatePanel } from "./chat-flow.eval-gate-panel";
import { FormRow } from "./form-row";

/**
 * System message used by the trigger-description "Help me write" sheet.
 * Tells the LLM that the field is a natural-language hint for the router
 * (not a user-facing reply), so the generated text is shaped accordingly.
 */
function buildTriggerDescriptionFieldInstruction(spec: {
  name?: string;
  description?: string;
}): string {
  return [
    "You are helping a user write the TRIGGER DESCRIPTION of a chat flow inside the Turing Enterprise Search platform.",
    "",
    "FIELD SEMANTICS:",
    "- This text is used by an LLM router to decide whether an incoming user message should activate this chat flow.",
    "- It is NOT shown to the end user — it is a contract written FOR the router LLM.",
    "- It should describe when the flow SHOULD fire and (importantly) when it should NOT.",
    "",
    "FLOW CONTEXT (current form state):",
    `- Flow name: ${spec.name?.trim() || "(unset)"}`,
    `- Flow description: ${spec.description?.trim() || "(unset)"}`,
    "",
    "WRITING RULES:",
    "- One short paragraph or a small bullet list. Routers read this on every turn — keep it tight.",
    "- Mention example user intents/phrasings that should activate the flow.",
    "- Mention near-miss cases that look related but should NOT activate the flow.",
    "- Match the language the user wrote the brief in (Portuguese brief → Portuguese; English brief → English).",
    "",
    "OUTPUT RULES:",
    "- Return ONLY the resulting trigger description.",
    "- Do NOT wrap in quotes, code fences, or markdown.",
    "- Do NOT include a preamble, headers, commentary, or examples of conversations.",
    "- If a current trigger description exists, prefer the smallest change that satisfies the user's instructions.",
  ].join("\n");
}

interface SettingsTabProps {
  agentId: string;
  name: string;
  setName: (value: string) => void;
  description: string;
  setDescription: (value: string) => void;
  guardrailMethod: TurChatFlowGuardrailMethod;
  setGuardrailMethod: (value: TurChatFlowGuardrailMethod) => void;
  captureMode: TurChatFlowCaptureMode;
  setCaptureMode: (value: TurChatFlowCaptureMode) => void;
  abandonHandoffMessage: string;
  setAbandonHandoffMessage: (value: string) => void;
  triggerDescription: string;
  setTriggerDescription: (value: string) => void;
  triggerMode: TurChatFlowTriggerMode;
  setTriggerMode: (value: TurChatFlowTriggerMode) => void;
  triggerLanguage: TurChatFlowTriggerLanguage;
  setTriggerLanguage: (value: TurChatFlowTriggerLanguage) => void;
  slotInheritanceJson: string;
  setSlotInheritanceJson: (value: string) => void;
}

export function ChatFlowSettingsTab({
  agentId,
  name,
  setName,
  description,
  setDescription,
  guardrailMethod,
  setGuardrailMethod,
  captureMode,
  setCaptureMode,
  abandonHandoffMessage,
  setAbandonHandoffMessage,
  triggerDescription,
  setTriggerDescription,
  triggerMode,
  setTriggerMode,
  triggerLanguage,
  setTriggerLanguage,
  slotInheritanceJson,
  setSlotInheritanceJson,
}: Readonly<SettingsTabProps>) {
  const { t } = useTranslation();
  return (
    <TabsContent value="settings" className="flex-1 min-h-0 overflow-y-auto">
      <div className="space-y-4 px-4 lg:px-6 py-2 pb-8">
        {/* T287 — Agent-CI eval gate: replay the agent's golden set and block
            (or warn) on regression before publish. Hidden when no golden set
            is configured. The passive authoring-insight panels (lint, funnel,
            trigger ambiguity) live on the dedicated "Analysis" tab. */}
        <ChatFlowEvalGatePanel agentId={agentId} />

        {/* ── Card: General ── */}
        <SectionCard variant="blue">
          <SectionCard.Header
            icon={IconSettings}
            title={t("chatFlow.sections.general.title")}
            description={t("chatFlow.sections.general.description")}
          />
          <SectionCard.Content>
            <div className="space-y-6">
              <FormRow
                htmlFor="chat-flow-name"
                label={t("chatFlow.fields.name")}
                description={t("chatFlow.fields.namePlaceholder")}
              >
                <Input
                  id="chat-flow-name"
                  value={name}
                  onChange={(event) => setName(event.target.value)}
                  placeholder={t("chatFlow.fields.namePlaceholder")}
                />
              </FormRow>

              <FormRow
                htmlFor="chat-flow-description"
                label={t("chatFlow.fields.description")}
                description={t("chatFlow.fields.descriptionPlaceholder")}
              >
                <Input
                  id="chat-flow-description"
                  value={description}
                  onChange={(event) => setDescription(event.target.value)}
                  placeholder={t("chatFlow.fields.descriptionPlaceholder")}
                />
              </FormRow>

              <FormRow
                htmlFor="chat-flow-guardrail"
                label={t("chatFlow.fields.guardrailMethod")}
                description={t(`chatFlow.guardrailMethods.${guardrailMethod}.description`)}
              >
                <Select
                  value={guardrailMethod}
                  onValueChange={(value) => setGuardrailMethod(value as TurChatFlowGuardrailMethod)}
                >
                  <SelectTrigger id="chat-flow-guardrail" className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {CHAT_FLOW_GUARDRAIL_METHODS.map((method) => (
                      <SelectItem key={method} value={method}>
                        {t(`chatFlow.guardrailMethods.${method}.label`)}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </FormRow>

              {/* T51 / §VII.4.e — capture-first inversion. Only the LLM_JUDGE
                  guardrail has a judge to invert, so the picker is disabled
                  (and explained) for the other methods. */}
              <FormRow
                htmlFor="chat-flow-capture-mode"
                label={t("chatFlow.fields.captureMode")}
                description={
                  guardrailMethod === "LLM_JUDGE"
                    ? t(`chatFlow.captureModes.${captureMode}.description`)
                    : t("chatFlow.fields.captureModeJudgeOnly")
                }
              >
                <Select
                  value={captureMode}
                  onValueChange={(value) => setCaptureMode(value as TurChatFlowCaptureMode)}
                  disabled={guardrailMethod !== "LLM_JUDGE"}
                >
                  <SelectTrigger id="chat-flow-capture-mode" className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {CHAT_FLOW_CAPTURE_MODES.map((cmode) => (
                      <SelectItem key={cmode} value={cmode}>
                        {t(`chatFlow.captureModes.${cmode}.label`)}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </FormRow>

              {/* T53 / §VII.4.g — abandonment auto-escalation. When the judge
                  detects the visitor wants to quit, offer a human consultant
                  instead of just closing. Non-blank text = opt-in; only the
                  LLM_JUDGE guardrail has an abandonment signal. */}
              <FormRow
                htmlFor="chat-flow-abandon-handoff"
                label={t("chatFlow.fields.abandonHandoffMessage")}
                description={
                  guardrailMethod === "LLM_JUDGE"
                    ? t("chatFlow.fields.abandonHandoffMessageHint")
                    : t("chatFlow.fields.abandonHandoffMessageJudgeOnly")
                }
              >
                <Textarea
                  id="chat-flow-abandon-handoff"
                  value={abandonHandoffMessage}
                  onChange={(event) => setAbandonHandoffMessage(event.target.value)}
                  disabled={guardrailMethod !== "LLM_JUDGE"}
                  rows={2}
                  placeholder={t("chatFlow.fields.abandonHandoffMessagePlaceholder")}
                />
              </FormRow>
            </div>
          </SectionCard.Content>
        </SectionCard>

        {/* ── Card: Auto-trigger ── */}
        <SectionCard variant="violet">
          <SectionCard.Header
            icon={IconBolt}
            title={t("chatFlow.sections.trigger.title")}
            description={t("chatFlow.sections.trigger.description")}
          />
          <SectionCard.Content>
            <div className="space-y-6">
              <FormRow
                htmlFor="chat-flow-trigger-description"
                label={t("chatFlow.fields.triggerDescription")}
                description={t("chatFlow.fields.triggerDescriptionHint")}
              >
                <PromptEditor
                  value={triggerDescription}
                  onChange={setTriggerDescription}
                  placeholder={t("chatFlow.fields.triggerDescriptionPlaceholder")}
                  rows={4}
                  metaPrompt={{
                    fieldInstruction: buildTriggerDescriptionFieldInstruction({
                      name,
                      description,
                    }),
                    tone: "violet",
                    triggerLabel: t("chatFlow.fields.helpWriteTrigger"),
                    title: t("chatFlow.fields.helpWriteTriggerTitle"),
                    description: t("chatFlow.fields.helpWriteTriggerDescription"),
                    placeholder: t("chatFlow.fields.helpWriteTriggerPlaceholder"),
                    hint: t("chatFlow.fields.helpWriteTriggerHint"),
                    generateLabel: t("chatFlow.fields.helpWriteTriggerGenerate"),
                  }}
                />
              </FormRow>

              <FormRow
                htmlFor="chat-flow-trigger-mode"
                label={t("chatFlow.fields.triggerMode")}
                description={t(`chatFlow.triggerModes.${triggerMode}.description`)}
              >
                <Select
                  value={triggerMode}
                  onValueChange={(value) => setTriggerMode(value as TurChatFlowTriggerMode)}
                >
                  <SelectTrigger id="chat-flow-trigger-mode" className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {CHAT_FLOW_TRIGGER_MODES.map((mode) => (
                      <SelectItem key={mode} value={mode}>
                        {t(`chatFlow.triggerModes.${mode}.label`)}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </FormRow>

              {/* T25 / §II.2.1 — language of triggerDescription. Picks the
                  matching Lucene analyzer for the procedural router so PT
                  stems collapse correctly for PT flows and Porter does the
                  same job for EN flows. AUTO (default) lets the backend
                  detect from the description content. */}
              <FormRow
                htmlFor="chat-flow-trigger-language"
                label={t("chatFlow.fields.triggerLanguage")}
                description={t(`chatFlow.triggerLanguages.${triggerLanguage}.description`)}
              >
                <Select
                  value={triggerLanguage}
                  onValueChange={(value) => setTriggerLanguage(value as TurChatFlowTriggerLanguage)}
                >
                  <SelectTrigger id="chat-flow-trigger-language" className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {CHAT_FLOW_TRIGGER_LANGUAGES.map((lang) => (
                      <SelectItem key={lang} value={lang}>
                        {t(`chatFlow.triggerLanguages.${lang}.label`)}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </FormRow>
            </div>
          </SectionCard.Content>
        </SectionCard>

        {/* T93 / §VII.11.c — Inherit slots from earlier flows on the same
            conversation. Two affordances:
              • a "Preserve all session slots" toggle (writes the wildcard
                {"*":"*"} and hides the advanced JSON), for the common case
                of "just bring everything along".
              • an advanced JSON textarea for renaming / picking specific
                slots — same shape as the backend persists. */}
        <SlotInheritanceCard
          slotInheritanceJson={slotInheritanceJson}
          setSlotInheritanceJson={setSlotInheritanceJson}
        />
      </div>
    </TabsContent>
  );
}

/**
 * Card that maps the {@code slotInheritanceJson} field onto a
 * Preserve-all toggle + advanced JSON textarea. Lives at the bottom of
 * the editor since most flows don't need inheritance and the card
 * collapses to a single switch row when off.
 */
function SlotInheritanceCard({
  slotInheritanceJson,
  setSlotInheritanceJson,
}: Readonly<{
  slotInheritanceJson: string;
  setSlotInheritanceJson: (value: string) => void;
}>) {
  const { t } = useTranslation();
  const trimmed = slotInheritanceJson.trim();
  // The wildcard mapping is the "Preserve everything" semantics. Anything
  // else (including empty) is either "no inheritance" or a custom mapping
  // the operator typed by hand in the advanced textarea.
  const isWildcard = trimmed === '{"*":"*"}' || trimmed === '{"*": "*"}';
  const hasCustomMapping = trimmed.length > 0 && !isWildcard;
  return (
    <SectionCard variant="cyan">
      <SectionCard.Header
        icon={IconArrowsRightLeft}
        title={t("chatFlow.sections.slotInheritance.title", {
          defaultValue: "Slot inheritance",
        })}
        description={t("chatFlow.sections.slotInheritance.description", {
          defaultValue:
            "When the engine routes a visitor onto this flow from a different flow on the same conversation, decide which already-captured slots carry over. Empty = no inheritance.",
        })}
      />
      <SectionCard.Content>
        <div className="space-y-6">
          <FormRow
            htmlFor="chat-flow-preserve-all-slots"
            label={t("chatFlow.fields.preserveAllSlots", {
              defaultValue: "Preserve all session slots",
            })}
            description={t("chatFlow.fields.preserveAllSlotsHint", {
              defaultValue:
                "Bring every slot captured in earlier flows on this conversation through to this flow. Writes the wildcard mapping {\"*\":\"*\"}. Toggle off to author a specific slot map below.",
            })}
          >
            <Switch
              id="chat-flow-preserve-all-slots"
              checked={isWildcard}
              onCheckedChange={(checked) =>
                setSlotInheritanceJson(checked ? '{"*":"*"}' : "")
              }
              disabled={hasCustomMapping}
            />
          </FormRow>

          {!isWildcard && (
            <FormRow
              htmlFor="chat-flow-slot-inheritance-json"
              label={t("chatFlow.fields.slotInheritanceJson", {
                defaultValue: "Custom mapping (advanced)",
              })}
              description={t("chatFlow.fields.slotInheritanceJsonHint", {
                defaultValue:
                  'JSON object {"receivingSlot":"sourceSlot"}. Use the same name on both sides for a straight pass-through; use different names to rename, e.g. {"position":"cargo_atual"}. Slots not in the map are NOT inherited.',
              })}
            >
              <Textarea
                id="chat-flow-slot-inheritance-json"
                value={slotInheritanceJson}
                onChange={(event) => setSlotInheritanceJson(event.target.value)}
                placeholder={'{"name":"name","position":"cargo_atual"}'}
                rows={4}
                className="font-mono text-xs"
              />
            </FormRow>
          )}
        </div>
      </SectionCard.Content>
    </SectionCard>
  );
}
