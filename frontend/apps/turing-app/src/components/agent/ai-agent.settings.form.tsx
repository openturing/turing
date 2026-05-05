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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { SystemPromptEditor } from "@/components/ui/system-prompt-editor"
import { useCreateAiAgent, useUpdateAiAgent } from "@/api/queries/ai-agent.queries"
import type { TurAIAgent } from "@/models/agent/ai-agent.model.ts"
import type { TurEmbeddingModel } from "@/models/embedding/embedding-model.model"
import type { TurStoreInstance } from "@/models/store/store-instance.model"
import { TurEmbeddingModelService } from "@/services/embedding/embedding-model.service"
import { TurStoreInstanceService } from "@/services/store/store.service"
import { TurFeaturesService, type LoggingEngine } from "@/services/system/features.service"
import { TurGlobalSettingsService } from "@/services/system/global-settings.service"
import { IconBrain, IconDatabase, IconDeviceFloppy, IconFileText, IconInfoCircle, IconPower, IconSettings, IconToggleLeft, IconX } from "@tabler/icons-react"
import { GradientButton } from "../ui/gradient-button"
import { useEffect, useState } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"
import { toast } from "@viglet/viglet-design-system"
import { StickyPageHeader } from "../sticky-page-header"
import { FormItemTwoColumns } from "../ui/form-item-two-columns"
import { GradientSwitch } from "../ui/gradient-switch"
import { IconPicker } from "../ui/icon-picker"
import { SectionCard } from "../ui/section-card"
import { MetaPromptSheet } from "../ui/meta-prompt-sheet"
import { SmartDescription } from "../ui/smart-description"

const turEmbeddingModelService = new TurEmbeddingModelService();
const turStoreInstanceService = new TurStoreInstanceService();
const turGlobalSettingsService = new TurGlobalSettingsService();
const turFeaturesService = new TurFeaturesService();
const urlBase = ROUTES.AI_AGENT_INSTANCE

const DEFAULT_VALUE = "__default__";
const NONE_VALUE = "__none__";

interface Props {
  value: TurAIAgent;
  isNew: boolean;
}

/**
 * System message used by the SystemPromptEditor "Help me write" sheet.
 * Tells the LLM that the field being edited is the agent's system prompt
 * (i.e. the persona / behavioral contract sent on every conversation),
 * so generated text is shaped accordingly. Uses live form state for the
 * agent name and admin-facing description.
 */
function buildSystemPromptFieldInstruction(spec: {
  title?: string;
  agentDescription?: string;
}): string {
  return [
    "You are helping a user write the SYSTEM PROMPT of an AI agent inside the Turing Enterprise Search platform.",
    "",
    "FIELD SEMANTICS:",
    "- This text becomes the system message prepended to every conversation the agent has.",
    "- It defines the agent's persona, scope, behavior boundaries, tone, and any operating instructions.",
    "- It is NOT shown to the end user — it talks TO the LLM about how to behave.",
    "",
    "AGENT CONTEXT (current form state):",
    `- Agent name: ${spec.title?.trim() || "(unset)"}`,
    `- Agent description: ${spec.agentDescription?.trim() || "(unset)"}`,
    "",
    "WRITING RULES:",
    "- Write in second person ('You are...', 'You should...').",
    "- Be specific about scope (what the agent does AND what it refuses).",
    "- Prefer imperative bullet lists or short paragraphs over flowery prose.",
    "- Match the language the user wrote the brief in (Portuguese brief → Portuguese system prompt; English brief → English).",
    "",
    "OUTPUT RULES:",
    "- Return ONLY the resulting system prompt text.",
    "- Do NOT wrap in quotes, code fences, or markdown.",
    "- Do NOT include a preamble, headers, commentary, or examples of conversations.",
    "- If a current system prompt exists, prefer the smallest change that satisfies the user's instructions.",
  ].join("\n");
}

export const AIAgentSettingsForm: React.FC<Props> = ({ value, isNew }) => {
  const { t } = useTranslation();
  const form = useForm<TurAIAgent>({
    defaultValues: value
  });
  const navigate = useNavigate()
  const [embeddingModels, setEmbeddingModels] = useState<TurEmbeddingModel[]>([]);
  const [storeInstances, setStoreInstances] = useState<TurStoreInstance[]>([]);
  const [globalRagEnabled, setGlobalRagEnabled] = useState(false);
  const [globalDefaultEmbeddingModelId, setGlobalDefaultEmbeddingModelId] = useState<string>("");
  const [globalDefaultEmbeddingStoreId, setGlobalDefaultEmbeddingStoreId] = useState<string>("");
  const [loggingEngine, setLoggingEngine] = useState<LoggingEngine>("none");

  const createMutation = useCreateAiAgent();
  const updateMutation = useUpdateAiAgent();

  const ragEnabled = form.watch("ragEnabled");
  const embeddingModelValue = form.watch("turEmbeddingModelInstance");
  const storeInstanceValue = form.watch("turStoreInstance");
  const chatMemoryEnabled = form.watch("chatMemoryEnabled");
  const loggingActive = loggingEngine !== "none";

  // RAG can only be ON if both an embedding model AND a store are resolvable
  // (either explicitly set on the agent, or via global RAG defaults).
  const hasResolvableEmbeddingModel =
      embeddingModelValue !== undefined && embeddingModelValue !== null
          ? true
          : embeddingModelValue === null
              ? !!globalDefaultEmbeddingModelId
              : false;
  const hasResolvableStore =
      storeInstanceValue !== undefined && storeInstanceValue !== null
          ? true
          : storeInstanceValue === null
              ? !!globalDefaultEmbeddingStoreId
              : false;
  const canEnableRag = hasResolvableEmbeddingModel && hasResolvableStore;

  useEffect(() => {
    form.reset({
      ...value,
      description: value.description ?? "",
      systemPrompt: value.systemPrompt ?? "",
      nativeTools: value.nativeTools ?? null,
      llmInstances: value.llmInstances ?? [],
      mcpServers: value.mcpServers ?? [],
      ragEnabled: value.ragEnabled ?? false,
      chatMemoryEnabled: value.chatMemoryEnabled ?? false,
      chatMemoryFlushIntervalMinutes: value.chatMemoryFlushIntervalMinutes ?? 5,
      chatMemoryMaxMessages: value.chatMemoryMaxMessages ?? 100,
      turEmbeddingModelInstance: value.turEmbeddingModelInstance,
      turStoreInstance: value.turStoreInstance,
    });
  }, [value])

  useEffect(() => {
    turEmbeddingModelService.query().then(setEmbeddingModels);
    turStoreInstanceService.query().then(setStoreInstances);
    turGlobalSettingsService.query().then((settings) => {
      setGlobalRagEnabled(settings.ragEnabled ?? false);
      setGlobalDefaultEmbeddingModelId(settings.defaultEmbeddingModelId ?? "");
      setGlobalDefaultEmbeddingStoreId(settings.defaultEmbeddingStoreId ?? "");
    });
    turFeaturesService.getFeatures()
      .then((features) => setLoggingEngine(features.loggingEngine ?? "none"))
      .catch(() => setLoggingEngine("none"));
  }, []);

  // If the user changes embedding model/store and RAG is no longer resolvable,
  // force ragEnabled = false so the saved state is consistent.
  useEffect(() => {
    if (ragEnabled && !canEnableRag) {
      form.setValue("ragEnabled", false);
    }
  }, [ragEnabled, canEnableRag, form]);

  function getSelectValue(instance: { id: string } | null | undefined, hasGlobalDefault: boolean): string {
    if (instance === undefined) return NONE_VALUE;
    if (instance === null) return hasGlobalDefault ? DEFAULT_VALUE : NONE_VALUE;
    return instance.id;
  }

  function handleEmbeddingModelChange(val: string) {
    if (val === DEFAULT_VALUE) {
      form.setValue("turEmbeddingModelInstance", null);
    } else if (val === NONE_VALUE) {
      form.setValue("turEmbeddingModelInstance", undefined);
    } else {
      const instance = embeddingModels.find((i) => i.id === val);
      form.setValue("turEmbeddingModelInstance", instance ?? undefined);
    }
  }

  function handleStoreChange(val: string) {
    if (val === DEFAULT_VALUE) {
      form.setValue("turStoreInstance", null);
    } else if (val === NONE_VALUE) {
      form.setValue("turStoreInstance", undefined);
    } else {
      const instance = storeInstances.find((i) => i.id === val);
      form.setValue("turStoreInstance", instance ?? undefined);
    }
  }

  async function onSubmit(agent: TurAIAgent) {
    const payload: TurAIAgent = { ...agent }
    try {
      if (isNew) {
        const result = await createMutation.mutateAsync(payload);
        if (result) {
          toast.success(t("forms.common.saved", { name: payload.title, feature: t("aiAgent.title") }));
          navigate(`${urlBase}/${result.id}/settings`);
        } else {
          toast.error(t("forms.common.notSaved", { name: payload.title, feature: t("aiAgent.title") }));
        }
      } else {
        const result = await updateMutation.mutateAsync(payload);
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
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 px-4 lg:px-6 pb-8">
        <StickyPageHeader>
          <StickyPageHeader.Title
            icon={IconSettings}
            feature={t("aiAgent.settings.title")}
            description={t("aiAgent.settings.description")}
          />
          <StickyPageHeader.Actions>
            <GradientButton type="submit" size="sm">
              <IconDeviceFloppy className="size-4" />
              {t("forms.formActions.saveChanges")}
            </GradientButton>
            <GradientButton type="button" variant="outline" size="sm" onClick={() => navigate(urlBase)}>
              <IconX className="size-4" />
              {t("forms.formActions.cancel")}
            </GradientButton>
          </StickyPageHeader.Actions>
        </StickyPageHeader>
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
                        enableMetaPrompt
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
                    <div className="flex flex-wrap items-center justify-between gap-2 mb-1.5">
                      <div>
                        <FormLabel>{t("forms.agentSettings.instruction")}</FormLabel>
                        <FormDescription>
                          {t("forms.agentSettings.instructionDesc")}
                        </FormDescription>
                      </div>
                      <MetaPromptSheet
                        value={field.value ?? ""}
                        onChange={field.onChange}
                        metaPrompt={form.watch("systemPromptMetaPrompt") ?? ""}
                        onMetaPromptChange={(v) =>
                          form.setValue("systemPromptMetaPrompt", v, { shouldDirty: true })
                        }
                        fieldInstruction={buildSystemPromptFieldInstruction({
                          title: form.watch("title"),
                          agentDescription: form.watch("description"),
                        })}
                        triggerVariant="button"
                        tone="violet"
                        triggerLabel={t("forms.agentSettings.helpWriteSystemPrompt")}
                        title={t("forms.agentSettings.helpWriteSystemPromptTitle")}
                        description={t("forms.agentSettings.helpWriteSystemPromptDescription")}
                        placeholder={t("forms.agentSettings.helpWriteSystemPromptPlaceholder")}
                        hint={t("forms.agentSettings.helpWriteSystemPromptHint")}
                        generateLabel={t("forms.agentSettings.helpWriteSystemPromptGenerate")}
                      />
                    </div>
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

          <SectionCard variant="violet">
            <SectionCard.Header icon={IconBrain} title={t("forms.agentSettings.ragModels")} description={t("forms.agentSettings.ragModelsDesc")} />
            <SectionCard.Content>
              <FormField
                control={form.control}
                name="turEmbeddingModelInstance"
                render={({ field }) => (
                  <FormItemTwoColumns>
                    <FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Label>{t("forms.agentSettings.embeddingModel")}</FormItemTwoColumns.Label>
                      <FormItemTwoColumns.Description>
                        {t("forms.agentSettings.embeddingModelDesc")}
                      </FormItemTwoColumns.Description>
                    </FormItemTwoColumns.Left>
                    <FormItemTwoColumns.Right>
                      <FormControl>
                        <Select
                          onValueChange={handleEmbeddingModelChange}
                          value={getSelectValue(field.value, !!globalDefaultEmbeddingModelId)}
                        >
                          <SelectTrigger className="w-full">
                            <SelectValue placeholder={t("forms.agentSettings.noEmbeddingModel")} />
                          </SelectTrigger>
                          <SelectContent>
                            {globalRagEnabled && globalDefaultEmbeddingModelId && (
                              <SelectItem value={DEFAULT_VALUE}>{t("forms.agentSettings.defaultGlobal")}</SelectItem>
                            )}
                            <SelectItem value={NONE_VALUE}>{t("forms.agentSettings.noEmbeddingModel")}</SelectItem>
                            {embeddingModels.map((model) => (
                              <SelectItem key={model.id} value={model.id}>
                                {model.modelName}
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                      </FormControl>
                      <FormMessage />
                    </FormItemTwoColumns.Right>
                  </FormItemTwoColumns>
                )}
              />
              <FormField
                control={form.control}
                name="turStoreInstance"
                render={({ field }) => (
                  <FormItemTwoColumns>
                    <FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Label>{t("forms.agentSettings.embeddingStore")}</FormItemTwoColumns.Label>
                      <FormItemTwoColumns.Description>
                        {t("forms.agentSettings.embeddingStoreDesc")}
                      </FormItemTwoColumns.Description>
                    </FormItemTwoColumns.Left>
                    <FormItemTwoColumns.Right>
                      <FormControl>
                        <Select
                          onValueChange={handleStoreChange}
                          value={getSelectValue(field.value, !!globalDefaultEmbeddingStoreId)}
                        >
                          <SelectTrigger className="w-full">
                            <SelectValue placeholder={t("forms.agentSettings.noEmbeddingStore")} />
                          </SelectTrigger>
                          <SelectContent>
                            {globalRagEnabled && globalDefaultEmbeddingStoreId && (
                              <SelectItem value={DEFAULT_VALUE}>{t("forms.agentSettings.defaultGlobal")}</SelectItem>
                            )}
                            <SelectItem value={NONE_VALUE}>{t("forms.agentSettings.noEmbeddingStore")}</SelectItem>
                            {storeInstances.map((instance) => (
                              <SelectItem key={instance.id} value={instance.id}>
                                {instance.title}
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                      </FormControl>
                      <FormMessage />
                    </FormItemTwoColumns.Right>
                  </FormItemTwoColumns>
                )}
              />
            </SectionCard.Content>
          </SectionCard>

          <SectionCard variant="blue">
            <SectionCard.Header icon={IconPower} title={t("forms.agentSettings.rag")} description={t("forms.agentSettings.ragDesc")} />
            <SectionCard.Content>
              <FormField
                control={form.control}
                name="ragEnabled"
                render={({ field }) => (
                  <FormItemTwoColumns>
                    <FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Label>{t("forms.agentSettings.enableRag")}</FormItemTwoColumns.Label>
                      <FormItemTwoColumns.Description>
                        {canEnableRag
                          ? t("forms.agentSettings.enableRagDesc")
                          : t("forms.agentSettings.enableRagBlocked")}
                      </FormItemTwoColumns.Description>
                    </FormItemTwoColumns.Left>
                    <FormItemTwoColumns.Right>
                      <FormControl>
                        <GradientSwitch
                          checked={!!field.value}
                          disabled={!canEnableRag}
                          onCheckedChange={(checked) => field.onChange(checked)}
                        />
                      </FormControl>
                    </FormItemTwoColumns.Right>
                  </FormItemTwoColumns>
                )}
              />
            </SectionCard.Content>
          </SectionCard>

          <SectionCard variant="violet">
            <SectionCard.Header
              icon={IconDatabase}
              title={t("forms.agentSettings.chatMemory")}
              description={t("forms.agentSettings.chatMemoryDesc")}
            />
            <SectionCard.Content>
              <FormField
                control={form.control}
                name="chatMemoryEnabled"
                render={({ field }) => (
                  <FormItemTwoColumns>
                    <FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Label>{t("forms.agentSettings.chatMemoryEnable")}</FormItemTwoColumns.Label>
                      <FormItemTwoColumns.Description>
                        {loggingActive
                          ? t("forms.agentSettings.chatMemoryEnableDesc")
                          : t("forms.agentSettings.chatMemoryEnableBlocked")}
                      </FormItemTwoColumns.Description>
                    </FormItemTwoColumns.Left>
                    <FormItemTwoColumns.Right>
                      <FormControl>
                        <GradientSwitch
                          checked={!!field.value}
                          disabled={!loggingActive}
                          onCheckedChange={(checked) => field.onChange(checked)}
                        />
                      </FormControl>
                    </FormItemTwoColumns.Right>
                  </FormItemTwoColumns>
                )}
              />
              <FormField
                control={form.control}
                name="chatMemoryFlushIntervalMinutes"
                rules={{
                  min: { value: 1, message: t("forms.agentSettings.chatMemoryMinValue") },
                }}
                render={({ field }) => (
                  <FormItemTwoColumns>
                    <FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Label>{t("forms.agentSettings.chatMemoryFlushInterval")}</FormItemTwoColumns.Label>
                      <FormItemTwoColumns.Description>
                        {t("forms.agentSettings.chatMemoryFlushIntervalDesc")}
                      </FormItemTwoColumns.Description>
                    </FormItemTwoColumns.Left>
                    <FormItemTwoColumns.Right>
                      <FormControl>
                        <Input
                          type="number"
                          min={1}
                          value={field.value ?? 5}
                          onChange={(e) => field.onChange(Number(e.target.value))}
                          onBlur={field.onBlur}
                          ref={field.ref}
                          name={field.name}
                          disabled={!loggingActive || !chatMemoryEnabled}
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItemTwoColumns.Right>
                  </FormItemTwoColumns>
                )}
              />
              <FormField
                control={form.control}
                name="chatMemoryMaxMessages"
                rules={{
                  min: { value: 1, message: t("forms.agentSettings.chatMemoryMinValue") },
                }}
                render={({ field }) => (
                  <FormItemTwoColumns>
                    <FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Label>{t("forms.agentSettings.chatMemoryMaxMessages")}</FormItemTwoColumns.Label>
                      <FormItemTwoColumns.Description>
                        {t("forms.agentSettings.chatMemoryMaxMessagesDesc")}
                      </FormItemTwoColumns.Description>
                    </FormItemTwoColumns.Left>
                    <FormItemTwoColumns.Right>
                      <FormControl>
                        <Input
                          type="number"
                          min={1}
                          value={field.value ?? 100}
                          onChange={(e) => field.onChange(Number(e.target.value))}
                          onBlur={field.onBlur}
                          ref={field.ref}
                          name={field.name}
                          disabled={!loggingActive || !chatMemoryEnabled}
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItemTwoColumns.Right>
                  </FormItemTwoColumns>
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

        </form>
    </Form>
  )
}
