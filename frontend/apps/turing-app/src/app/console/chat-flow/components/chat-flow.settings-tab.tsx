import { Input } from "@/components/ui/input";
import { SectionCard } from "@/components/ui/section-card";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { TabsContent } from "@/components/ui/tabs";
import { Textarea } from "@/components/ui/textarea";
import { IconBolt, IconSettings } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

import {
  CHAT_FLOW_GUARDRAIL_METHODS,
  CHAT_FLOW_TRIGGER_MODES,
  type TurChatFlowGuardrailMethod,
  type TurChatFlowTriggerMode,
} from "@/models/agent/chat-flow.model";

import { FormRow } from "./form-row";

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
                <Textarea
                  id="chat-flow-trigger-description"
                  value={triggerDescription}
                  onChange={(event) => setTriggerDescription(event.target.value)}
                  placeholder={t("chatFlow.fields.triggerDescriptionPlaceholder")}
                  rows={4}
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
