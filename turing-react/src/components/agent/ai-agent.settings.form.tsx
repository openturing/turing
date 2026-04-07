"use client"
import { ROUTES } from "@/app/routes.const"
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form"
import { Input } from "@/components/ui/input"
import { SystemPromptEditor } from "@/components/ui/system-prompt-editor"
import type { TurAIAgent } from "@/models/agent/ai-agent.model.ts"
import { TurAIAgentService } from "@/services/agent/ai-agent.service"
import { IconFileText, IconInfoCircle, IconToggleLeft } from "@tabler/icons-react"
import { useEffect } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"
import { toast } from "@openviglet/viglet-design-system"
import { FormActions } from "../ui/form-actions"
import { FormItemTwoColumns } from "../ui/form-item-two-columns"
import { GradientSwitch } from "../ui/gradient-switch"
import { IconPicker } from "../ui/icon-picker"
import { SectionCard } from "../ui/section-card"
import { SmartDescription } from "../ui/smart-description"

const turAIAgentService = new TurAIAgentService();
const urlBase = ROUTES.AI_AGENT_INSTANCE

interface Props {
  value: TurAIAgent;
  isNew: boolean;
}

export const AIAgentSettingsForm: React.FC<Props> = ({ value, isNew }) => {
  const { t } = useTranslation();
  const form = useForm<TurAIAgent>({
    defaultValues: value
  });
  const navigate = useNavigate()

  useEffect(() => {
    form.reset({
      ...value,
      description: value.description ?? "",
      systemPrompt: value.systemPrompt ?? "",
      nativeTools: value.nativeTools ?? null,
      llmInstances: value.llmInstances ?? [],
      mcpServers: value.mcpServers ?? [],
    });
  }, [value])

  async function onSubmit(agent: TurAIAgent) {
    const payload: TurAIAgent = { ...agent }
    try {
      if (isNew) {
        const result = await turAIAgentService.create(payload);
        if (result) {
          toast.success(t("forms.common.saved", { name: payload.title, feature: t("aiAgent.title") }));
          navigate(`${urlBase}/${result.id}/settings`);
        } else {
          toast.error(t("forms.common.notSaved", { name: payload.title, feature: t("aiAgent.title") }));
        }
      } else {
        const result = await turAIAgentService.update(payload);
        if (result) {
          toast.success(t("forms.common.updated", { name: payload.title, feature: t("aiAgent.title") }));
        } else {
          toast.error(t("forms.common.notUpdated", { name: payload.title, feature: t("aiAgent.title") }));
        }
      }
    } catch (error) {
      console.error("Form submission error", error);
      toast.error(t("forms.common.formSubmitFailed"));
    }
  }

  return (
    <div className="px-6">
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 py-8">
          <SectionCard variant="blue">
            <SectionCard.Header icon={IconInfoCircle} title={t("forms.common.generalInfo")} description={t("forms.agentSettings.generalDesc")} />
            <SectionCard.Content>
              <FormField
                control={form.control}
                name="title"
                rules={{ required: t("forms.agentSettings.titleRequired", "A title is required for this AI agent.") }}
                render={({ field }) => (
                  <FormItemTwoColumns>
                    <FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Label>{t("forms.common.title")}</FormItemTwoColumns.Label>
                      <FormItemTwoColumns.Description>
                        {t("forms.agentSettings.titleDesc")}
                      </FormItemTwoColumns.Description>
                    </FormItemTwoColumns.Left>
                    <FormItemTwoColumns.Right>
                      <FormControl>
                        <Input {...field} placeholder={t("forms.agentSettings.titlePlaceholder")} type="text" />
                      </FormControl>
                      <FormMessage />
                    </FormItemTwoColumns.Right>
                  </FormItemTwoColumns>
                )}
              />
              <FormField
                control={form.control}
                name="description"
                render={({ field }) => (
                  <FormItem>
                    <FormControl>
                      <SmartDescription
                        value={field.value}
                        onChange={field.onChange}
                        placeholder={t("forms.agentSettings.descPlaceholder")}
                        maxLength={500}
                        title={form.watch("title")}
                        entityType={t("aiAgent.title")}
                      >
                        <SmartDescription.Label>{t("forms.common.description")}</SmartDescription.Label>
                        <SmartDescription.Description>
                          {t("forms.agentSettings.descDesc")}
                        </SmartDescription.Description>
                      </SmartDescription>
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="icon"
                render={({ field }) => (
                  <FormItem>
                    <FormControl>
                      <IconPicker
                        value={field.value}
                        onChange={(icon) => field.onChange(icon)}
                        onRemove={() => field.onChange(null)}
                        title={form.watch("title")}
                        description={form.watch("description")}
                      >
                        <IconPicker.Label>{t("forms.common.icon")}</IconPicker.Label>
                      </IconPicker>
                    </FormControl>
                  </FormItem>
                )}
              />
            </SectionCard.Content>
          </SectionCard>

          <SectionCard variant="violet">
            <SectionCard.Header icon={IconFileText} title={t("forms.agentSettings.systemPrompt")} description={t("forms.agentSettings.systemPromptDesc")} />
            <SectionCard.Content>
              <FormField
                control={form.control}
                name="systemPrompt"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{t("forms.agentSettings.instruction")}</FormLabel>
                    <FormDescription>
                      {t("forms.agentSettings.instructionDesc")}
                    </FormDescription>
                    <FormControl>
                      <SystemPromptEditor
                        value={field.value ?? ""}
                        onChange={field.onChange}
                        fieldRef={field.ref}
                        placeholder={t("forms.agentSettings.instructionPlaceholder")}
                        hint={t("forms.agentSettings.instructionHint")}
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </SectionCard.Content>
          </SectionCard>

          <SectionCard variant="slate">
            <SectionCard.Header icon={IconToggleLeft} title={t("forms.common.status")} description={t("forms.agentSettings.statusDesc")} />
            <SectionCard.Content>
              <FormField
                control={form.control}
                name="enabled"
                render={({ field }) => (
                  <FormItemTwoColumns>
                    <FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Label>{t("forms.common.enabled")}</FormItemTwoColumns.Label>
                      <FormItemTwoColumns.Description>
                        {t("forms.agentSettings.enabledDesc")}
                      </FormItemTwoColumns.Description>
                    </FormItemTwoColumns.Left>
                    <FormItemTwoColumns.Right>
                      <FormControl>
                        <GradientSwitch
                          checked={field.value === 1}
                          onCheckedChange={(checked) => {
                            field.onChange(checked ? 1 : 0);
                          }}
                        />
                      </FormControl>
                    </FormItemTwoColumns.Right>
                  </FormItemTwoColumns>
                )}
              />
            </SectionCard.Content>
          </SectionCard>

          <FormActions>
            <FormActions.Cancel onClick={() => navigate(urlBase)} />
            <FormActions.Submit />
          </FormActions>
        </form>
      </Form>
    </div>
  )
}
