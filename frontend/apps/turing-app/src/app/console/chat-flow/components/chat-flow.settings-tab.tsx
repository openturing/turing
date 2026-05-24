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
import { IconBolt, IconFlask, IconSettings } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

import {
  CHAT_FLOW_GUARDRAIL_METHODS,
  CHAT_FLOW_TRIGGER_LANGUAGES,
  CHAT_FLOW_TRIGGER_MODES,
  type TurChatFlowGuardrailMethod,
  type TurChatFlowTriggerLanguage,
  type TurChatFlowTriggerMode,
} from "@/models/agent/chat-flow.model";

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
  name: string;
  setName: (value: string) => void;
  description: string;
  setDescription: (value: string) => void;
  guardrailMethod: TurChatFlowGuardrailMethod;
  setGuardrailMethod: (value: TurChatFlowGuardrailMethod) => void;
  triggerDescription: string;
  setTriggerDescription: (value: string) => void;
  triggerMode: TurChatFlowTriggerMode;
  setTriggerMode: (value: TurChatFlowTriggerMode) => void;
  triggerLanguage: TurChatFlowTriggerLanguage;
  setTriggerLanguage: (value: TurChatFlowTriggerLanguage) => void;
  experimentKey: string;
  setExperimentKey: (value: string) => void;
  variantLabel: string;
  setVariantLabel: (value: string) => void;
  trafficWeight: number | null;
  setTrafficWeight: (value: number | null) => void;
}

export function ChatFlowSettingsTab({
  name,
  setName,
  description,
  setDescription,
  guardrailMethod,
  setGuardrailMethod,
  triggerDescription,
  setTriggerDescription,
  triggerMode,
  setTriggerMode,
  triggerLanguage,
  setTriggerLanguage,
  experimentKey,
  setExperimentKey,
  variantLabel,
  setVariantLabel,
  trafficWeight,
  setTrafficWeight,
}: Readonly<SettingsTabProps>) {
  const { t } = useTranslation();
  return (
    <TabsContent value="settings" className="flex-1 min-h-0 overflow-y-auto">
      <div className="space-y-4 px-4 lg:px-6 py-2 pb-8">
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

        {/* ── Card: A/B experiment ── */}
        <SectionCard variant="amber">
          <SectionCard.Header
            icon={IconFlask}
            title={t("chatFlow.sections.experiment.title", {
              defaultValue: "A/B experiment",
            })}
            description={t("chatFlow.sections.experiment.description", {
              defaultValue:
                "Group this flow with sibling flows under the same experiment key — the router will reassign conversations across variants by traffic weight. Leave the key empty to disable A/B routing.",
            })}
          />
          <SectionCard.Content>
            <div className="space-y-6">
              <FormRow
                htmlFor="chat-flow-experiment-key"
                label={t("chatFlow.fields.experimentKey", { defaultValue: "Experiment key" })}
                description={t("chatFlow.fields.experimentKeyHint", {
                  defaultValue:
                    "Identifier shared across all variants of one A/B test (e.g. 'pricing-page-tone'). Stable: changing it ends the experiment and starts a new one.",
                })}
              >
                <Input
                  id="chat-flow-experiment-key"
                  value={experimentKey}
                  onChange={(event) => setExperimentKey(event.target.value)}
                  placeholder="e.g. programa-match-headline-2026q1"
                  maxLength={64}
                />
              </FormRow>

              <FormRow
                htmlFor="chat-flow-variant-label"
                label={t("chatFlow.fields.variantLabel", { defaultValue: "Variant label" })}
                description={t("chatFlow.fields.variantLabelHint", {
                  defaultValue:
                    "This flow's arm inside the experiment ('control', 'treatment_v2', 'warmer_tone'). Surfaces as the grouping dimension in the analytics dashboard.",
                })}
              >
                <Input
                  id="chat-flow-variant-label"
                  value={variantLabel}
                  onChange={(event) => setVariantLabel(event.target.value)}
                  placeholder="control"
                  maxLength={64}
                  disabled={!experimentKey.trim()}
                />
              </FormRow>

              <FormRow
                htmlFor="chat-flow-traffic-weight"
                label={t("chatFlow.fields.trafficWeight", { defaultValue: "Traffic weight" })}
                description={t("chatFlow.fields.trafficWeightHint", {
                  defaultValue:
                    "Relative weight (0-100) across variants. (50, 50) splits evenly; (90, 10) routes 90% to the first. Sums do not have to equal 100 — only ratios matter. 0 pauses this arm without deleting it.",
                })}
              >
                <Input
                  id="chat-flow-traffic-weight"
                  type="number"
                  min={0}
                  max={100}
                  value={trafficWeight ?? ""}
                  onChange={(event) => {
                    const raw = event.target.value;
                    if (raw === "") {
                      setTrafficWeight(null);
                      return;
                    }
                    const parsed = Number.parseInt(raw, 10);
                    if (!Number.isNaN(parsed)) {
                      setTrafficWeight(Math.max(0, Math.min(100, parsed)));
                    }
                  }}
                  placeholder="50"
                  disabled={!experimentKey.trim()}
                />
              </FormRow>
            </div>
          </SectionCard.Content>
        </SectionCard>
      </div>
    </TabsContent>
  );
}
