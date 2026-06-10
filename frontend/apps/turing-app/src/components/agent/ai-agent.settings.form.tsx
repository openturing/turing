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
import { Textarea } from "@/components/ui/textarea"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { useCreateAiAgent, useUpdateAiAgent } from "@/api/queries/ai-agent.queries"
import type { TurAIAgent } from "@/models/agent/ai-agent.model.ts"
import type { TurEmbeddingModel } from "@/models/embedding/embedding-model.model"
import type { TurSEInstance } from "@/models/se/se-instance.model"
import type { TurStoreInstance } from "@/models/store/store-instance.model"
import { TurEmbeddingModelService } from "@/services/embedding/embedding-model.service"
import { TurSEInstanceService } from "@/services/se/se.service"
import { TurStoreInstanceService } from "@/services/store/store.service"
import { TurFeaturesService, type LoggingEngine } from "@/services/system/features.service"
import { TurGlobalSettingsService } from "@/services/system/global-settings.service"
import { IconBolt, IconBrain, IconBrandPython, IconDatabase, IconDeviceFloppy, IconInfoCircle, IconPower, IconSettings, IconShieldLock, IconSparkles, IconX } from "@tabler/icons-react"
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
import { SmartDescription } from "../ui/smart-description"

const turEmbeddingModelService = new TurEmbeddingModelService();
const turStoreInstanceService = new TurStoreInstanceService();
const turSEInstanceService = new TurSEInstanceService();
const turGlobalSettingsService = new TurGlobalSettingsService();
const turFeaturesService = new TurFeaturesService();
const urlBase = ROUTES.AI_AGENT_INSTANCE

const DEFAULT_VALUE = "__default__";
const NONE_VALUE = "__none__";

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
  const [embeddingModels, setEmbeddingModels] = useState<TurEmbeddingModel[]>([]);
  const [storeInstances, setStoreInstances] = useState<TurStoreInstance[]>([]);
  const [seInstances, setSeInstances] = useState<TurSEInstance[]>([]);
  const [globalRagEnabled, setGlobalRagEnabled] = useState(false);
  const [globalDefaultEmbeddingModelId, setGlobalDefaultEmbeddingModelId] = useState<string>("");
  const [globalDefaultEmbeddingStoreId, setGlobalDefaultEmbeddingStoreId] = useState<string>("");
  const [loggingEngine, setLoggingEngine] = useState<LoggingEngine>("none");
  // T323 — whether the skills feature is usable on this deployment. The
  // backend flag tracks object storage; the runtime also needs Code Interpreter
  // DOCKER mode + a Default LLM, so when those are missing the toggle still
  // saves but the backend simply offers no skills (graceful no-op).
  const [skillsFeatureAvailable, setSkillsFeatureAvailable] = useState(false);

  const createMutation = useCreateAiAgent();
  const updateMutation = useUpdateAiAgent();

  const ragEnabled = form.watch("ragEnabled");
  const embeddingModelValue = form.watch("turEmbeddingModelInstance");
  const storeInstanceValue = form.watch("turStoreInstance");
  const chatMemoryEnabled = form.watch("chatMemoryEnabled");
  // T30 — gates the topK + recentN inputs; only meaningful when the base
  // chat memory is also active, hence the && composition below.
  const chatMemoryRelevanceEnabled = form.watch("chatMemoryRelevanceEnabled");
  // T115 — gates the compression threshold input; only meaningful when the
  // base chat memory is also active.
  const chatMemoryCompressionEnabled = form.watch("chatMemoryCompressionEnabled");
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
      // Status toggle lives in the bento dashboard hero, not in this form.
      // Default new agents to enabled so they appear in pickers (e.g. the
      // Global Settings "Default AI Agent" select, which filters by enabled).
      // `?? 1` preserves an explicit 0 on existing agents.
      enabled: value.enabled ?? 1,
      ragEnabled: value.ragEnabled ?? false,
      richContentEnabled: value.richContentEnabled ?? false,
      skillsEnabled: value.skillsEnabled ?? false,
      chatMemoryEnabled: value.chatMemoryEnabled ?? false,
      chatMemoryFlushIntervalMinutes: value.chatMemoryFlushIntervalMinutes ?? 5,
      chatMemoryMaxMessages: value.chatMemoryMaxMessages ?? 100,
      // T30 / §IV.4 — relevance retrieval defaults match the backend
      // entity defaults so existing agents keep the prior recent-N FIFO
      // behavior until the operator opts in.
      chatMemoryRelevanceEnabled: value.chatMemoryRelevanceEnabled ?? false,
      chatMemoryRelevanceTopK: value.chatMemoryRelevanceTopK ?? 5,
      chatMemoryRecentN: value.chatMemoryRecentN ?? 10,
      // T115 / §IX.3.e — compression defaults match the backend entity so
      // existing agents keep the prior behavior until the operator opts in.
      chatMemoryCompressionEnabled: value.chatMemoryCompressionEnabled ?? false,
      chatMemoryCompressionThresholdTokens: value.chatMemoryCompressionThresholdTokens ?? 8192,
      chatMemoryCompressionInterval: value.chatMemoryCompressionInterval ?? "PT1H",
      // T309 — async (off the hot path) is the default once compression is on.
      chatMemoryCompressionAsync: value.chatMemoryCompressionAsync ?? true,
      // T123 / §IX.7 — defaults: 0 disables the check; WARN is the
      // backend enum default. Existing agents keep their pre-T123
      // behaviour (no enforcement) until the operator opts in.
      maxPromptTokens: value.maxPromptTokens ?? 0,
      overBudgetBehavior: value.overBudgetBehavior ?? "WARN",
      // T66 / §VII.6.g — RETAIN_FOREVER matches the backend enum default so
      // existing agents keep every submission until the operator opts in.
      submissionRetention: value.submissionRetention ?? "RETAIN_FOREVER",
      submissionRetentionDays: value.submissionRetentionDays ?? 30,
      pythonRequirements: value.pythonRequirements ?? "",
      // CALL is the safe default — matches the backend enum default and
      // the pre-2026.2.7 behavior (single SSE event after the LLM finishes).
      chatMode: value.chatMode ?? "CALL",
      // T19/T24 reposition (2026.2.7): ragBm25Fallback and ragHybridSearch
      // moved from agent to TurSNSiteGenAi (RAG only runs in SN site
      // context; locale-aware retrieval needs SN site's locale config).
      // The toggles now live in sn.site.genai.form.tsx.
      turEmbeddingModelInstance: value.turEmbeddingModelInstance,
      turStoreInstance: value.turStoreInstance,
      // T28 / §III.5 Phase C — null clears the per-agent SE binding so
      // TurAnalyticsIntentIndexer.resolveSeInstance falls back to the
      // global default (property / first registered SE).
      analyticsSeInstance: value.analyticsSeInstance ?? null,
    });
  }, [value])

  useEffect(() => {
    turEmbeddingModelService.query().then(setEmbeddingModels);
    turStoreInstanceService.query().then(setStoreInstances);
    turSEInstanceService.query().then(setSeInstances);
    turGlobalSettingsService.query().then((settings) => {
      setGlobalRagEnabled(settings.ragEnabled ?? false);
      setGlobalDefaultEmbeddingModelId(settings.defaultEmbeddingModelId ?? "");
      setGlobalDefaultEmbeddingStoreId(settings.defaultEmbeddingStoreId ?? "");
    });
    turFeaturesService.getFeatures()
      .then((features) => {
        setLoggingEngine(features.loggingEngine ?? "none");
        setSkillsFeatureAvailable(features.skillsEnabled ?? false);
      })
      .catch(() => {
        setLoggingEngine("none");
        setSkillsFeatureAvailable(false);
      });
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
              <FormField
                control={form.control}
                name="analyticsSeInstance"
                render={({ field }) => (
                  <FormItemTwoColumns>
                    <FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Label>{t("forms.agentSettings.analyticsSeInstance")}</FormItemTwoColumns.Label>
                      <FormItemTwoColumns.Description>
                        {t("forms.agentSettings.analyticsSeInstanceDesc")}
                      </FormItemTwoColumns.Description>
                    </FormItemTwoColumns.Left>
                    <FormItemTwoColumns.Right>
                      <FormControl>
                        <Select
                          onValueChange={(value: string) => {
                            if (value === NONE_VALUE) {
                              field.onChange(null);
                            } else {
                              const instance = seInstances.find((i) => i.id === value);
                              field.onChange(instance ?? null);
                            }
                          }}
                          value={field.value?.id ?? NONE_VALUE}
                        >
                          <SelectTrigger className="w-full">
                            <SelectValue placeholder={t("forms.agentSettings.analyticsSeInstanceNone")} />
                          </SelectTrigger>
                          <SelectContent>
                            <SelectItem value={NONE_VALUE}>
                              {t("forms.agentSettings.analyticsSeInstanceNone")}
                            </SelectItem>
                            {seInstances.map((instance) => (
                              <SelectItem key={instance.id} value={instance.id!}>
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
              {/* T19/T24 reposition (2026.2.7): ragBm25Fallback and
                  ragHybridSearch toggles moved to the SN site GenAi form
                  because RAG only runs in SN site context (the SN site
                  chat API is the only invocation path that exercises
                  RAG) and locale-aware retrieval depends on
                  TurSNSiteLocale, which the SN site owns. */}
            </SectionCard.Content>
          </SectionCard>

          <SectionCard variant="blue">
            <SectionCard.Header
              icon={IconSparkles}
              title={t("forms.agentSettings.richContent")}
              description={t("forms.agentSettings.richContentDesc")}
            />
            <SectionCard.Content>
              <FormField
                control={form.control}
                name="richContentEnabled"
                render={({ field }) => (
                  <FormItemTwoColumns>
                    <FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Label>{t("forms.agentSettings.enableRichContent")}</FormItemTwoColumns.Label>
                      <FormItemTwoColumns.Description>
                        {t("forms.agentSettings.enableRichContentDesc")}
                      </FormItemTwoColumns.Description>
                    </FormItemTwoColumns.Left>
                    <FormItemTwoColumns.Right>
                      <FormControl>
                        <GradientSwitch
                          checked={!!field.value}
                          onCheckedChange={(checked) => field.onChange(checked)}
                        />
                      </FormControl>
                    </FormItemTwoColumns.Right>
                  </FormItemTwoColumns>
                )}
              />
            </SectionCard.Content>
          </SectionCard>

          <SectionCard variant="blue">
            <SectionCard.Header
              icon={IconBolt}
              title={t("forms.agentSettings.skills")}
              description={t("forms.agentSettings.skillsDesc")}
            />
            <SectionCard.Content>
              <FormField
                control={form.control}
                name="skillsEnabled"
                render={({ field }) => (
                  <FormItemTwoColumns>
                    <FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Label>{t("forms.agentSettings.enableSkills")}</FormItemTwoColumns.Label>
                      <FormItemTwoColumns.Description>
                        {skillsFeatureAvailable
                          ? t("forms.agentSettings.enableSkillsDesc")
                          : t("forms.agentSettings.enableSkillsBlocked")}
                      </FormItemTwoColumns.Description>
                    </FormItemTwoColumns.Left>
                    <FormItemTwoColumns.Right>
                      <FormControl>
                        <GradientSwitch
                          checked={!!field.value}
                          disabled={!skillsFeatureAvailable}
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
              {/*
                T30 / §IV.4 — conversation-relevance retrieval. Only useful
                when the base chat memory is on (otherwise there's nothing
                persisted to retrieve from), so all three controls gate on
                loggingActive && chatMemoryEnabled. The topK+recentN inputs
                additionally gate on chatMemoryRelevanceEnabled so the
                operator sees them grayed out until they flip the switch.
              */}
              <FormField
                control={form.control}
                name="chatMemoryRelevanceEnabled"
                render={({ field }) => (
                  <FormItemTwoColumns>
                    <FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Label>{t("forms.agentSettings.chatMemoryRelevance")}</FormItemTwoColumns.Label>
                      <FormItemTwoColumns.Description>
                        {t("forms.agentSettings.chatMemoryRelevanceDesc")}
                      </FormItemTwoColumns.Description>
                    </FormItemTwoColumns.Left>
                    <FormItemTwoColumns.Right>
                      <FormControl>
                        <GradientSwitch
                          checked={!!field.value}
                          disabled={!loggingActive || !chatMemoryEnabled}
                          onCheckedChange={(checked) => field.onChange(checked)}
                        />
                      </FormControl>
                    </FormItemTwoColumns.Right>
                  </FormItemTwoColumns>
                )}
              />
              <FormField
                control={form.control}
                name="chatMemoryRelevanceTopK"
                rules={{
                  min: { value: 1, message: t("forms.agentSettings.chatMemoryMinValue") },
                  max: { value: 20, message: t("forms.agentSettings.chatMemoryRelevanceTopKMaxValue") },
                }}
                render={({ field }) => (
                  <FormItemTwoColumns>
                    <FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Label>{t("forms.agentSettings.chatMemoryRelevanceTopK")}</FormItemTwoColumns.Label>
                      <FormItemTwoColumns.Description>
                        {t("forms.agentSettings.chatMemoryRelevanceTopKDesc")}
                      </FormItemTwoColumns.Description>
                    </FormItemTwoColumns.Left>
                    <FormItemTwoColumns.Right>
                      <FormControl>
                        <Input
                          type="number"
                          min={1}
                          max={20}
                          value={field.value ?? 5}
                          onChange={(e) => field.onChange(Number(e.target.value))}
                          onBlur={field.onBlur}
                          ref={field.ref}
                          name={field.name}
                          disabled={!loggingActive || !chatMemoryEnabled || !chatMemoryRelevanceEnabled}
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItemTwoColumns.Right>
                  </FormItemTwoColumns>
                )}
              />
              <FormField
                control={form.control}
                name="chatMemoryRecentN"
                rules={{
                  min: { value: 1, message: t("forms.agentSettings.chatMemoryMinValue") },
                }}
                render={({ field }) => (
                  <FormItemTwoColumns>
                    <FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Label>{t("forms.agentSettings.chatMemoryRecentN")}</FormItemTwoColumns.Label>
                      <FormItemTwoColumns.Description>
                        {t("forms.agentSettings.chatMemoryRecentNDesc")}
                      </FormItemTwoColumns.Description>
                    </FormItemTwoColumns.Left>
                    <FormItemTwoColumns.Right>
                      <FormControl>
                        <Input
                          type="number"
                          min={1}
                          value={field.value ?? 10}
                          onChange={(e) => field.onChange(Number(e.target.value))}
                          onBlur={field.onBlur}
                          ref={field.ref}
                          name={field.name}
                          disabled={!loggingActive || !chatMemoryEnabled || !chatMemoryRelevanceEnabled}
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItemTwoColumns.Right>
                  </FormItemTwoColumns>
                )}
              />
              {/*
                T115 / §IX.3.e — workspace-backed memory compression. When the
                older pool exceeds the token threshold, those turns are
                summarized once per interval and replaced by a compact summary
                block in the prompt. Gates on loggingActive && chatMemoryEnabled
                (nothing to compress without persisted memory); the threshold
                input additionally gates on chatMemoryCompressionEnabled.
              */}
              <FormField
                control={form.control}
                name="chatMemoryCompressionEnabled"
                render={({ field }) => (
                  <FormItemTwoColumns>
                    <FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Label>{t("forms.agentSettings.chatMemoryCompression")}</FormItemTwoColumns.Label>
                      <FormItemTwoColumns.Description>
                        {t("forms.agentSettings.chatMemoryCompressionDesc")}
                      </FormItemTwoColumns.Description>
                    </FormItemTwoColumns.Left>
                    <FormItemTwoColumns.Right>
                      <FormControl>
                        <GradientSwitch
                          checked={!!field.value}
                          disabled={!loggingActive || !chatMemoryEnabled}
                          onCheckedChange={(checked) => field.onChange(checked)}
                        />
                      </FormControl>
                    </FormItemTwoColumns.Right>
                  </FormItemTwoColumns>
                )}
              />
              <FormField
                control={form.control}
                name="chatMemoryCompressionThresholdTokens"
                rules={{
                  min: { value: 1, message: t("forms.agentSettings.chatMemoryMinValue") },
                }}
                render={({ field }) => (
                  <FormItemTwoColumns>
                    <FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Label>{t("forms.agentSettings.chatMemoryCompressionThreshold")}</FormItemTwoColumns.Label>
                      <FormItemTwoColumns.Description>
                        {t("forms.agentSettings.chatMemoryCompressionThresholdDesc")}
                      </FormItemTwoColumns.Description>
                    </FormItemTwoColumns.Left>
                    <FormItemTwoColumns.Right>
                      <FormControl>
                        <Input
                          type="number"
                          min={1}
                          value={field.value ?? 8192}
                          onChange={(e) => field.onChange(Number(e.target.value))}
                          onBlur={field.onBlur}
                          ref={field.ref}
                          name={field.name}
                          disabled={!loggingActive || !chatMemoryEnabled || !chatMemoryCompressionEnabled}
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItemTwoColumns.Right>
                  </FormItemTwoColumns>
                )}
              />
              <FormField
                control={form.control}
                name="chatMemoryCompressionAsync"
                render={({ field }) => (
                  <FormItemTwoColumns>
                    <FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Label>{t("forms.agentSettings.chatMemoryCompressionAsync")}</FormItemTwoColumns.Label>
                      <FormItemTwoColumns.Description>
                        {t("forms.agentSettings.chatMemoryCompressionAsyncDesc")}
                      </FormItemTwoColumns.Description>
                    </FormItemTwoColumns.Left>
                    <FormItemTwoColumns.Right>
                      <FormControl>
                        <GradientSwitch
                          checked={field.value !== false}
                          disabled={!loggingActive || !chatMemoryEnabled || !chatMemoryCompressionEnabled}
                          onCheckedChange={(checked) => field.onChange(checked)}
                        />
                      </FormControl>
                    </FormItemTwoColumns.Right>
                  </FormItemTwoColumns>
                )}
              />
              {/* T123 / §IX.7 — enforceable per-turn token budget. The
                  executor estimates the prompt size before calling the
                  LLM and applies the configured behaviour when the
                  estimate exceeds maxPromptTokens. 0 disables the check. */}
              <FormField
                control={form.control}
                name="maxPromptTokens"
                rules={{
                  min: { value: 0, message: t("forms.agentSettings.maxPromptTokensMinValue", { defaultValue: "Must be ≥ 0." }) },
                  max: { value: 1_000_000, message: t("forms.agentSettings.maxPromptTokensMaxValue", { defaultValue: "Must be ≤ 1,000,000." }) },
                }}
                render={({ field }) => (
                  <FormItemTwoColumns>
                    <FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Label>
                        {t("forms.agentSettings.maxPromptTokens", { defaultValue: "Max prompt tokens" })}
                      </FormItemTwoColumns.Label>
                      <FormItemTwoColumns.Description>
                        {t("forms.agentSettings.maxPromptTokensDesc", {
                          defaultValue:
                            "Estimated upper bound on the per-turn prompt size (chars/4 heuristic). 0 disables the check. Pair with the over-budget behaviour below to decide what happens when the estimate exceeds the budget.",
                        })}
                      </FormItemTwoColumns.Description>
                    </FormItemTwoColumns.Left>
                    <FormItemTwoColumns.Right>
                      <FormControl>
                        <Input
                          type="number"
                          min={0}
                          max={1_000_000}
                          step={500}
                          placeholder="0 (disabled)"
                          value={field.value ?? 0}
                          onChange={(e) => field.onChange(Number(e.target.value))}
                          onBlur={field.onBlur}
                          ref={field.ref}
                          name={field.name}
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItemTwoColumns.Right>
                  </FormItemTwoColumns>
                )}
              />
              <FormField
                control={form.control}
                name="overBudgetBehavior"
                render={({ field }) => (
                  <FormItemTwoColumns>
                    <FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Label>
                        {t("forms.agentSettings.overBudgetBehavior", { defaultValue: "Over-budget behaviour" })}
                      </FormItemTwoColumns.Label>
                      <FormItemTwoColumns.Description>
                        {t("forms.agentSettings.overBudgetBehaviorDesc", {
                          defaultValue:
                            "WARN: log and proceed. ERROR: short-circuit the LLM call with an error reply. COMPACT: reserved for future workspace-backed compression (V1 degrades to WARN).",
                        })}
                      </FormItemTwoColumns.Description>
                    </FormItemTwoColumns.Left>
                    <FormItemTwoColumns.Right>
                      <FormControl>
                        <select
                          aria-label={t("forms.agentSettings.overBudgetBehavior", { defaultValue: "Over-budget behaviour" })}
                          className="border rounded-md px-3 py-2 text-sm bg-background"
                          value={field.value ?? "WARN"}
                          onChange={(e) => field.onChange(e.target.value)}
                          onBlur={field.onBlur}
                          ref={field.ref}
                          name={field.name}
                          disabled={(form.watch("maxPromptTokens") ?? 0) === 0}
                        >
                          <option value="WARN">WARN</option>
                          <option value="ERROR">ERROR</option>
                          <option value="COMPACT">COMPACT (V1: degrades to WARN)</option>
                        </select>
                      </FormControl>
                      <FormMessage />
                    </FormItemTwoColumns.Right>
                  </FormItemTwoColumns>
                )}
              />
            </SectionCard.Content>
          </SectionCard>

          {/* T66 / §VII.6.g — submission retention (LGPD/GDPR compliance hook). */}
          <SectionCard variant="amber">
            <SectionCard.Header
              icon={IconShieldLock}
              title={t("forms.agentSettings.submissionRetention", { defaultValue: "Submission retention" })}
              description={t("forms.agentSettings.submissionRetentionSectionDesc", {
                defaultValue:
                  "Compliance policy (LGPD / GDPR) for how long this agent's completed chat-flow submissions are kept. Applies to the submission archive only — live PII slots expire via the global PII retention TTL.",
              })}
            />
            <SectionCard.Content>
              <FormField
                control={form.control}
                name="submissionRetention"
                render={({ field }) => (
                  <FormItemTwoColumns>
                    <FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Label>
                        {t("forms.agentSettings.submissionRetentionMode", { defaultValue: "Retention policy" })}
                      </FormItemTwoColumns.Label>
                      <FormItemTwoColumns.Description>
                        {t("forms.agentSettings.submissionRetentionModeDesc", {
                          defaultValue:
                            "Retain forever: keep every submission. Retain N days: a daily job deletes submissions older than the threshold. Delete after export: purge a conversation's submissions as soon as it is exported.",
                        })}
                      </FormItemTwoColumns.Description>
                    </FormItemTwoColumns.Left>
                    <FormItemTwoColumns.Right>
                      <FormControl>
                        <select
                          aria-label={t("forms.agentSettings.submissionRetentionMode", { defaultValue: "Retention policy" })}
                          className="border rounded-md px-3 py-2 text-sm bg-background"
                          value={field.value ?? "RETAIN_FOREVER"}
                          onChange={(e) => field.onChange(e.target.value)}
                          onBlur={field.onBlur}
                          ref={field.ref}
                          name={field.name}
                        >
                          <option value="RETAIN_FOREVER">
                            {t("forms.agentSettings.submissionRetentionForever", { defaultValue: "Retain forever" })}
                          </option>
                          <option value="RETAIN_DAYS">
                            {t("forms.agentSettings.submissionRetentionDaysOption", { defaultValue: "Retain for N days" })}
                          </option>
                          <option value="DELETE_AFTER_EXPORT">
                            {t("forms.agentSettings.submissionRetentionAfterExport", { defaultValue: "Delete after export" })}
                          </option>
                        </select>
                      </FormControl>
                      <FormMessage />
                    </FormItemTwoColumns.Right>
                  </FormItemTwoColumns>
                )}
              />
              {form.watch("submissionRetention") === "RETAIN_DAYS" && (
                <FormField
                  control={form.control}
                  name="submissionRetentionDays"
                  rules={{
                    min: { value: 1, message: t("forms.agentSettings.submissionRetentionDaysMin", { defaultValue: "Must be ≥ 1." }) },
                    max: { value: 3650, message: t("forms.agentSettings.submissionRetentionDaysMax", { defaultValue: "Must be ≤ 3650 (10 years)." }) },
                  }}
                  render={({ field }) => (
                    <FormItemTwoColumns>
                      <FormItemTwoColumns.Left>
                        <FormItemTwoColumns.Label>
                          {t("forms.agentSettings.submissionRetentionDaysLabel", { defaultValue: "Retention period (days)" })}
                        </FormItemTwoColumns.Label>
                        <FormItemTwoColumns.Description>
                          {t("forms.agentSettings.submissionRetentionDaysDesc", {
                            defaultValue:
                              "Submissions completed more than this many days ago are deleted by the daily cleanup job. Leave at 0 to disable the time-based purge.",
                          })}
                        </FormItemTwoColumns.Description>
                      </FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Right>
                        <FormControl>
                          <Input
                            type="number"
                            min={1}
                            max={3650}
                            step={1}
                            placeholder="30"
                            value={field.value ?? 30}
                            onChange={(e) => field.onChange(Number(e.target.value))}
                            onBlur={field.onBlur}
                            ref={field.ref}
                            name={field.name}
                          />
                        </FormControl>
                        <FormMessage />
                      </FormItemTwoColumns.Right>
                    </FormItemTwoColumns>
                  )}
                />
              )}
            </SectionCard.Content>
          </SectionCard>

          <SectionCard>
            <SectionCard.Header
              icon={IconBrandPython}
              title={t("forms.agentSettings.codeInterpreter")}
              description={t("forms.agentSettings.codeInterpreterDesc")}
            />
            <SectionCard.Content>
              <FormField
                control={form.control}
                name="pythonRequirements"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{t("forms.agentSettings.pythonRequirements")}</FormLabel>
                    <FormDescription>
                      {t("forms.agentSettings.pythonRequirementsDesc")}
                    </FormDescription>
                    <FormControl>
                      <Textarea
                        className="font-mono text-sm"
                        rows={5}
                        placeholder={"pandas==2.0\nopenpyxl>=3.1"}
                        spellCheck={false}
                        autoComplete="off"
                        {...field}
                        value={field.value ?? ""}
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </SectionCard.Content>
          </SectionCard>

          <SectionCard variant="blue">
            <SectionCard.Header
              icon={IconBolt}
              title={t("forms.agentSettings.chatMode")}
              description={t("forms.agentSettings.chatModeDesc")}
            />
            <SectionCard.Content>
              <FormField
                control={form.control}
                name="chatMode"
                render={({ field }) => (
                  <FormItemTwoColumns>
                    <FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Label>{t("forms.agentSettings.chatMode")}</FormItemTwoColumns.Label>
                      <FormItemTwoColumns.Description>
                        {field.value === "STREAM"
                          ? t("forms.agentSettings.chatModeStreamDesc")
                          : t("forms.agentSettings.chatModeCallDesc")}
                      </FormItemTwoColumns.Description>
                    </FormItemTwoColumns.Left>
                    <FormItemTwoColumns.Right>
                      <FormControl>
                        <Select
                          onValueChange={(v) => field.onChange(v)}
                          value={field.value ?? "CALL"}
                        >
                          <SelectTrigger className="w-full">
                            <SelectValue />
                          </SelectTrigger>
                          <SelectContent>
                            <SelectItem value="CALL">{t("forms.agentSettings.chatModeCall")}</SelectItem>
                            <SelectItem value="STREAM">{t("forms.agentSettings.chatModeStream")}</SelectItem>
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

          <SectionCard variant="emerald">
            <SectionCard.Header
              icon={IconPower}
              title={t("forms.common.status")}
              description={t("forms.agentSettings.statusDesc")}
            />
            <SectionCard.Content>
              <FormField
                control={form.control}
                name="enabled"
                render={({ field }) => (
                  <FormItemTwoColumns>
                    <FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Label>{t("forms.common.enabled")}</FormItemTwoColumns.Label>
                      <FormItemTwoColumns.Description className="text-sm font-normal mt-1">
                        {t("forms.agentSettings.enabledDesc")}
                      </FormItemTwoColumns.Description>
                    </FormItemTwoColumns.Left>
                    <FormItemTwoColumns.Right>
                      <FormControl>
                        <GradientSwitch
                          checked={field.value === 1}
                          onCheckedChange={(checked) => field.onChange(checked ? 1 : 0)}
                        />
                      </FormControl>
                    </FormItemTwoColumns.Right>
                    <FormMessage />
                  </FormItemTwoColumns>
                )}
              />
            </SectionCard.Content>
          </SectionCard>

        </form>
    </Form>
  )
}
