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
import { IconBolt, IconSettings } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

import {
  CHAT_FLOW_GUARDRAIL_METHODS,
  CHAT_FLOW_TRIGGER_MODES,
  type TurChatFlowGuardrailMethod,
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
            </div>
          </SectionCard.Content>
        </SectionCard>
      </div>
    </TabsContent>
  );
}
