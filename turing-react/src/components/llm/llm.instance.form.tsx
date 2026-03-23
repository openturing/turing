"use client"
import { ROUTES } from "@/app/routes.const"
import { SectionCard } from "../ui/section-card"
import { FormActions } from "../ui/form-actions"
import { IconSettings, IconBrain, IconSparkles, IconAdjustments, IconToggleLeft } from "@tabler/icons-react"
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage
} from "@/components/ui/form"
import {
  Input
} from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import {
  Textarea
} from "@/components/ui/textarea"
import type { TurLLMInstance } from "@/models/llm/llm-instance.model.ts"
import type { TurLLMVendor } from "@/models/llm/llm-vendor.model"
import { TurLLMVendorService } from "@/services/llm/llm-vendor.service"
import { TurLLMInstanceService } from "@/services/llm/llm.service"
import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import {
  useForm
} from "react-hook-form"
import { useNavigate } from "react-router-dom"
import { toast } from "sonner"
import { DialogDelete } from "../dialog.delete"
import { FormItemTwoColumns } from "../ui/form-item-two-columns"
import { GradientSwitch } from "../ui/gradient-switch"
import { IconPicker } from "../ui/icon-picker"
import { SmartDescription } from "../ui/smart-description"
const turLLMInstanceService = new TurLLMInstanceService();
const turLLMVendorService = new TurLLMVendorService();
const urlBase = ROUTES.LLM_INSTANCE
interface Props {
  value: TurLLMInstance;
  isNew: boolean;
}

type LlmProviderOptionsDraft = {
  embeddingModel: string;
  topK: string;
  repeatPenalty: string;
  numPredict: string;
  maxTokens: string;
  stop: string;
  deploymentName: string;
  embeddingDeploymentName: string;
}

const emptyLlmProviderOptionsDraft = (): LlmProviderOptionsDraft => ({
  embeddingModel: "",
  topK: "",
  repeatPenalty: "",
  numPredict: "",
  maxTokens: "",
  stop: "",
  deploymentName: "",
  embeddingDeploymentName: ""
})

const toText = (value: unknown) => value == null ? "" : String(value)

const parseJsonObject = (jsonValue?: string) => {
  if (!jsonValue?.trim()) {
    return undefined
  }
  try {
    const parsed = JSON.parse(jsonValue) as unknown
    if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
      return parsed as Record<string, unknown>
    }
  } catch {
    // Keep the draft empty for invalid JSON; validation occurs on submit.
  }
  return undefined
}

const parseLlmProviderOptionsDraft = (_vendorId: string | undefined, jsonValue?: string): LlmProviderOptionsDraft => {
  const parsed = parseJsonObject(jsonValue)
  if (!parsed) {
    return emptyLlmProviderOptionsDraft()
  }

  return {
    embeddingModel: toText(parsed.embeddingModel),
    topK: toText(parsed.topK),
    repeatPenalty: toText(parsed.repeatPenalty),
    numPredict: toText(parsed.numPredict),
    maxTokens: toText(parsed.maxTokens),
    stop: Array.isArray(parsed.stop) ? parsed.stop.map((item) => String(item)).join(",") : toText(parsed.stop),
    deploymentName: toText(parsed.deploymentName),
    embeddingDeploymentName: toText(parsed.embeddingDeploymentName)
  }
}

const parseNumber = (value: string) => {
  const normalized = value.trim()
  if (!normalized) {
    return undefined
  }
  const parsed = Number(normalized)
  return Number.isFinite(parsed) ? parsed : undefined
}

const buildLlmProviderOptionsFromDraft = (vendorId: string | undefined, draft: LlmProviderOptionsDraft) => {
  const options: Record<string, unknown> = {}
  const putText = (key: string, value: string) => {
    const normalized = value.trim()
    if (normalized) {
      options[key] = normalized
    }
  }
  const putNumber = (key: string, value: string) => {
    const parsed = parseNumber(value)
    if (parsed !== undefined) {
      options[key] = parsed
    }
  }

  if (vendorId === "OLLAMA") {
    putText("embeddingModel", draft.embeddingModel)
    putNumber("topK", draft.topK)
    putNumber("repeatPenalty", draft.repeatPenalty)
    putNumber("numPredict", draft.numPredict)
    const stopItems = draft.stop
      .split(",")
      .map((item) => item.trim())
      .filter((item) => item.length > 0)
    if (stopItems.length > 0) {
      options.stop = stopItems
    }
  }

  if (vendorId === "OPENAI") {
    putText("embeddingModel", draft.embeddingModel)
    putNumber("maxTokens", draft.maxTokens)
  }

  if (vendorId === "ANTHROPIC") {
    putNumber("topK", draft.topK)
    putNumber("maxTokens", draft.maxTokens)
  }

  if (vendorId === "GEMINI") {
    putNumber("topK", draft.topK)
    putNumber("maxTokens", draft.maxTokens)
  }

  if (vendorId === "GEMINI_OPENAI") {
    putNumber("maxTokens", draft.maxTokens)
  }

  if (vendorId === "AZURE_OPENAI") {
    putText("deploymentName", draft.deploymentName)
    putText("embeddingDeploymentName", draft.embeddingDeploymentName)
    putNumber("maxTokens", draft.maxTokens)
  }

  return options
}

export const LLMInstanceForm: React.FC<Props> = ({ value, isNew }) => {
  const { t } = useTranslation();
  const form = useForm<TurLLMInstance>({
    defaultValues: value
  });

  const [open, setOpen] = useState(false);
  const [vendors, setVendors] = useState<TurLLMVendor[]>([]);
  const [llmProviderOptionsDraft, setLlmProviderOptionsDraft] = useState<LlmProviderOptionsDraft>(
    emptyLlmProviderOptionsDraft()
  )
  const selectedVendorId = form.watch("turLLMVendor.id");
  const navigate = useNavigate()

  useEffect(() => {
    turLLMVendorService.query().then(setVendors);
  }, [])

  useEffect(() => {
    form.reset({
      ...value,
      apiKey: "",
      providerOptionsJson: value.providerOptionsJson ?? ""
    });
    setLlmProviderOptionsDraft(
      parseLlmProviderOptionsDraft(value.turLLMVendor?.id, value.providerOptionsJson)
    )
  }, [value])

  const applyVendorDefaults = (vendorId: string) => {
    const draft = emptyLlmProviderOptionsDraft()
    if (vendorId === "OLLAMA") {
      form.setValue("url", "http://localhost:11434", { shouldDirty: true });
      form.setValue("modelName", "mistral", { shouldDirty: true });
      form.setValue("temperature", 0.8, { shouldDirty: true });
      form.setValue("topP", 0.9, { shouldDirty: true });
      form.setValue("seed", 42, { shouldDirty: true });
      form.setValue("supportedCapabilities", "RESPONSE_FORMAT_JSON_SCHEMA", { shouldDirty: true });
      form.setValue("timeout", "PT60S", { shouldDirty: true });
      draft.embeddingModel = "nomic-embed-text"
      draft.topK = "6"
      draft.repeatPenalty = "1.1"
      draft.numPredict = "256"
    }
    if (vendorId === "OPENAI") {
      form.setValue("url", "https://api.openai.com", { shouldDirty: true });
      form.setValue("modelName", "gpt-4o-mini", { shouldDirty: true });
      form.setValue("temperature", 0.7, { shouldDirty: true });
      form.setValue("topP", 0.9, { shouldDirty: true });
      form.setValue("seed", 42, { shouldDirty: true });
      form.setValue("timeout", "PT60S", { shouldDirty: true });
      draft.embeddingModel = "text-embedding-3-small"
      draft.maxTokens = "1024"
    }
    if (vendorId === "ANTHROPIC") {
      form.setValue("url", "https://api.anthropic.com", { shouldDirty: true });
      form.setValue("modelName", "claude-sonnet-4-20250514", { shouldDirty: true });
      form.setValue("temperature", 0.7, { shouldDirty: true });
      form.setValue("topP", 0.9, { shouldDirty: true });
      form.setValue("timeout", "PT60S", { shouldDirty: true });
      draft.topK = "40"
      draft.maxTokens = "1024"
    }
    if (vendorId === "GEMINI") {
      form.setValue("url", "", { shouldDirty: true });
      form.setValue("modelName", "gemini-2.0-flash", { shouldDirty: true });
      form.setValue("temperature", 0.7, { shouldDirty: true });
      form.setValue("topP", 0.9, { shouldDirty: true });
      form.setValue("timeout", "PT60S", { shouldDirty: true });
      draft.topK = "40"
      draft.maxTokens = "8192"
    }
    if (vendorId === "GEMINI_OPENAI") {
      form.setValue("url", "https://generativelanguage.googleapis.com/v1beta/openai", { shouldDirty: true });
      form.setValue("modelName", "gemini-2.0-flash", { shouldDirty: true });
      form.setValue("temperature", 0.7, { shouldDirty: true });
      form.setValue("topP", 0.9, { shouldDirty: true });
      form.setValue("timeout", "PT60S", { shouldDirty: true });
      draft.maxTokens = "8192"
    }
    if (vendorId === "AZURE_OPENAI") {
      form.setValue("url", "", { shouldDirty: true });
      form.setValue("modelName", "gpt-4o", { shouldDirty: true });
      form.setValue("temperature", 0.7, { shouldDirty: true });
      form.setValue("topP", 0.9, { shouldDirty: true });
      form.setValue("seed", 42, { shouldDirty: true });
      form.setValue("timeout", "PT60S", { shouldDirty: true });
      draft.deploymentName = "gpt-4o"
      draft.embeddingDeploymentName = "text-embedding-ada-002"
      draft.maxTokens = "1024"
    }
    setLlmProviderOptionsDraft(draft)
  }

  const setLlmDraftValue = (key: keyof LlmProviderOptionsDraft, fieldValue: string) => {
    setLlmProviderOptionsDraft((prev) => ({ ...prev, [key]: fieldValue }))
  }

  const getProviderOptionsPlaceholder = (vendorId?: string) => {
    if (vendorId === "OLLAMA") {
      return '{\n  "embeddingModel": "nomic-embed-text",\n  "topK": 6,\n  "repeatPenalty": 1.1,\n  "numPredict": 256,\n  "stop": ["END", "STOP"]\n}'
    }
    if (vendorId === "OPENAI") {
      return '{\n  "embeddingModel": "text-embedding-3-small",\n  "maxTokens": 1024\n}'
    }
    if (vendorId === "ANTHROPIC") {
      return '{\n  "topK": 40,\n  "maxTokens": 1024\n}'
    }
    if (vendorId === "GEMINI") {
      return '{\n  "topK": 40,\n  "maxTokens": 8192\n}'
    }
    if (vendorId === "GEMINI_OPENAI") {
      return '{\n  "maxTokens": 8192\n}'
    }
    if (vendorId === "AZURE_OPENAI") {
      return '{\n  "deploymentName": "gpt-4o",\n  "embeddingDeploymentName": "text-embedding-ada-002",\n  "maxTokens": 1024\n}'
    }
    return '{}'
  }

  const normalizeJson = (jsonValue?: string) => {
    const normalized = jsonValue?.trim();
    if (!normalized) {
      return undefined;
    }
    try {
      JSON.parse(normalized);
      return normalized;
    } catch {
      toast.error(t("forms.common.invalidJson"));
      return null;
    }
  }

  async function onSubmit(llmInstance: TurLLMInstance) {
    const rawProviderOptionsJson = normalizeJson(llmInstance.providerOptionsJson)
    if (rawProviderOptionsJson === null) {
      return;
    }

    const visualProviderOptions = buildLlmProviderOptionsFromDraft(selectedVendorId, llmProviderOptionsDraft)
    const rawProviderOptions = parseJsonObject(rawProviderOptionsJson)
    const mergedProviderOptions = {
      ...visualProviderOptions,
      ...(rawProviderOptions)
    }
    const providerOptionsJson = Object.keys(mergedProviderOptions).length > 0
      ? JSON.stringify(mergedProviderOptions, null, 2)
      : undefined

    const payload: TurLLMInstance = {
      ...llmInstance,
      apiKey: llmInstance.apiKey?.trim() || undefined,
      providerOptionsJson
    }

    try {
      if (isNew) {
        const result = await turLLMInstanceService.create(payload);
        if (result) {
          toast.success(t("forms.common.saved", { name: payload.title, feature: "Language Model" }));
          navigate(urlBase);
        } else {
          toast.error(t("forms.common.notSaved", { name: payload.title, feature: "Language Model" }));
        }
      }
      else {
        const result = await turLLMInstanceService.update(payload);
        if (result) {
          toast.success(t("forms.common.updated", { name: payload.title, feature: "Language Model" }));
        } else {
          toast.error(t("forms.common.notUpdated", { name: payload.title, feature: "Language Model" }));
        }
      }
    } catch (error) {
      console.error("Form submission error", error);
      toast.error(t("forms.common.formSubmitFailed"));
    }
  }

  async function onDelete() {
    console.log("delete");
    try {
      if (await turLLMInstanceService.delete(value)) {
        toast.success(t("forms.common.deleted", { name: value.title, feature: "Language Model" }));
        navigate(urlBase);
      }
      else {
        toast.error(t("forms.common.notDeleted", { name: value.title, feature: "Language Model" }));
      }

    } catch (error) {
      console.error("Form submission error", error);
      toast.error(t("forms.common.notDeleted", { name: value.title, feature: "Language Model" }));
    }
    setOpen(false);
  }
  return (
    <div className="w-full px-0 md:px-4 py-4">
      <Card className="mx-auto border-0 shadow-none md:border md:shadow-sm rounded-none md:rounded-xl max-md:**:data-[slot=card-header]:px-2 max-md:**:data-[slot=card-content]:px-2 max-md:**:data-[slot=card-content]:pt-0">
        <CardHeader className="hidden md:grid">
          <CardTitle className="text-2xl">{isNew && (<span>New</span>)} Language Model</CardTitle>
          <CardAction>
            {!isNew && <DialogDelete feature="Language Model" name={value.title} onDelete={onDelete} open={open} setOpen={setOpen} />}
          </CardAction>
          <CardDescription>
            {t("forms.llm.settings")}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Form {...form}>
            <form
              onSubmit={form.handleSubmit(onSubmit)}
              className="w-full max-w-2xl mx-auto py-2 md:py-8 flex flex-col gap-4"
              autoComplete="off"
            >
                {/* General Section */}
                <SectionCard variant="blue">
                  <SectionCard.Header icon={IconSettings} title={t("forms.common.generalInfo")} description={t("forms.llm.titleAndVendor")} />
                  <SectionCard.Content>
                    {/* Title */}
                    <div className="w-full">
                      <FormField
                        control={form.control}
                        name="title"
                        rules={{ required: t("forms.llm.titleRequired") }}
                        render={({ field }) => (
                          <FormItem className="w-full">
                            <FormLabel>{t("forms.common.title")}</FormLabel>
                            <div className="text-muted-foreground text-sm font-normal mt-1">
                              {t("forms.llm.titleDesc")}
                            </div>
                            <FormControl>
                              <Input {...field} placeholder={t("forms.common.title")} type="text" className="w-full" />
                            </FormControl>
                            <FormMessage />
                          </FormItem>
                        )}
                      />
                    </div>
                    {/* Vendor (inline row) */}
                    <div className="w-full flex flex-row justify-between items-center">
                      <div className="flex flex-col">
                        <FormLabel>{t("forms.common.vendor")}</FormLabel>
                        <div className="text-muted-foreground text-sm font-normal mt-1">
                          {t("forms.llm.vendorDesc")}
                        </div>
                      </div>
                      <div className="flex-1 ml-8">
                        <FormField
                          control={form.control}
                          name="turLLMVendor.id"
                          rules={{ required: t("forms.llm.vendorRequired") }}
                          render={({ field }) => (
                            <FormItem className="w-full">
                              <FormControl>
                                <Select
                                  onValueChange={(nextValue) => {
                                    field.onChange(nextValue);
                                    applyVendorDefaults(nextValue);
                                  }}
                                  value={field.value}
                                >
                                  <SelectTrigger className="w-full">
                                    <SelectValue placeholder="Choose..." />
                                  </SelectTrigger>
                                  <SelectContent>
                                    {vendors.map((vendor) => (
                                      <SelectItem key={vendor.id} value={vendor.id}>{vendor.title}</SelectItem>
                                    ))}
                                  </SelectContent>
                                </Select>
                              </FormControl>
                              <FormMessage />
                            </FormItem>
                          )}
                        />
                      </div>
                    </div>
                    {/* Description */}
                    <div className="w-full">
                      <FormField
                        control={form.control}
                        name="description"
                        render={({ field }) => (
                          <FormItem className="w-full">
                            <FormControl>
                              <SmartDescription
                                value={field.value}
                                onChange={field.onChange}
                                placeholder={t("forms.common.description")}
                                className="w-full"
                                rows={2}
                                maxLength={500}
                                title={form.watch("title")}
                                entityType="Language Model"
                              >
                                <SmartDescription.Label>{t("forms.common.description")}</SmartDescription.Label>
                                <SmartDescription.Description>
                                  {t("forms.llm.descriptionDesc")}
                                </SmartDescription.Description>
                              </SmartDescription>
                            </FormControl>
                            <FormMessage />
                          </FormItem>
                        )}
                      />
                    </div>
                    <div className="w-full">
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
                    </div>
                  </SectionCard.Content>
                </SectionCard>

                {/* Model Section */}
                <SectionCard variant="violet">
                  <SectionCard.Header icon={IconBrain} title={t("forms.llm.modelSettings")} description={t("forms.llm.modelSettingsDesc")} />
                  <SectionCard.Content>
                    {/* URL */}
                    <div className="w-full">
                      <FormField
                        control={form.control}
                        name="url"
                        rules={{ required: t("forms.se.endpointRequired") }}
                        render={({ field }) => (
                          <FormItem className="w-full">
                            <FormLabel>{t("forms.common.endpointUrl")}</FormLabel>
                            <div className="text-muted-foreground text-sm font-normal mt-1">
                              {t("forms.llm.endpointDesc")}
                            </div>
                            <FormControl>
                              <Input placeholder={t("forms.llm.endpointPlaceholder")} type="text" className="w-full" {...field} />
                            </FormControl>
                            <FormMessage />
                          </FormItem>
                        )}
                      />
                    </div>
                    {/* Model Name */}
                    <div className="w-full">
                      <FormField
                        control={form.control}
                        name="modelName"
                        rules={{ required: t("forms.llm.modelName") }}
                        render={({ field }) => (
                          <FormItem className="w-full">
                            <FormLabel>{t("forms.llm.modelName")}</FormLabel>
                            <div className="text-muted-foreground text-sm font-normal mt-1">
                              {t("forms.llm.modelNameDesc")}
                            </div>
                            <FormControl>
                              <Input placeholder={t("forms.llm.modelNamePlaceholder")} type="text" autoComplete="one-time-code" className="w-full" {...field} />
                            </FormControl>
                            <FormMessage />
                          </FormItem>
                        )}
                      />
                    </div>
                    {/* API Key */}
                    <div className="w-full">
                      <FormField
                        control={form.control}
                        name="apiKey"
                        render={({ field }) => (
                          <FormItem className="w-full">
                            <FormLabel>{t("forms.common.apiKey")}</FormLabel>
                            <div className="text-muted-foreground text-sm font-normal mt-1">
                              {t("forms.llm.apiKeyDesc")}
                            </div>
                            <FormControl>
                              <Input
                                placeholder={t("forms.llm.apiKeyPlaceholder")}
                                type="text"
                                className="w-full [-webkit-text-security:disc]"
                                autoComplete="one-time-code"
                                {...field}
                                value={field.value ?? ""}
                              />
                            </FormControl>
                            <FormMessage />
                          </FormItem>
                        )}
                      />
                    </div>
                  </SectionCard.Content>
                </SectionCard>

                {/* Generation Section */}
                <SectionCard variant="emerald">
                  <SectionCard.Header icon={IconSparkles} title={t("forms.llm.genParams")} description={t("forms.llm.genParamsDesc")} />
                  <SectionCard.Content>
                    {/* Temperature — all vendors */}
                    <div className="w-full">
                      <FormField
                        control={form.control}
                        name="temperature"
                        render={({ field }) => (
                          <FormItem className="w-full">
                            <FormLabel>{t("forms.llm.temperature")}</FormLabel>
                            <div className="text-muted-foreground text-sm font-normal mt-1">
                              {t("forms.llm.temperatureDesc")}
                            </div>
                            <FormControl>
                              <Input placeholder="e.g., 0.8" type="number" step="0.01" className="w-full" {...field} />
                            </FormControl>
                            <FormMessage />
                          </FormItem>
                        )}
                      />
                    </div>
                    {/* Top P — all vendors */}
                    <div className="w-full">
                      <FormField
                        control={form.control}
                        name="topP"
                        render={({ field }) => (
                          <FormItem className="w-full">
                            <FormLabel>{t("forms.llm.topP")}</FormLabel>
                            <div className="text-muted-foreground text-sm font-normal mt-1">
                              {t("forms.llm.topPDesc")}
                            </div>
                            <FormControl>
                              <Input placeholder="e.g., 0.9" type="number" step="0.01" className="w-full" {...field} />
                            </FormControl>
                            <FormMessage />
                          </FormItem>
                        )}
                      />
                    </div>
                    {/* Seed — OLLAMA, OPENAI, AZURE_OPENAI */}
                    {(selectedVendorId === "OLLAMA" || selectedVendorId === "OPENAI" || selectedVendorId === "AZURE_OPENAI") && (
                      <div className="w-full">
                        <FormField
                          control={form.control}
                          name="seed"
                          render={({ field }) => (
                            <FormItem className="w-full">
                              <FormLabel>{t("forms.llm.seed")}</FormLabel>
                              <div className="text-muted-foreground text-sm font-normal mt-1">
                                {t("forms.llm.seedDesc")}
                              </div>
                              <FormControl>
                                <Input placeholder="e.g., 42" type="number" className="w-full" {...field} />
                              </FormControl>
                              <FormMessage />
                            </FormItem>
                          )}
                        />
                      </div>
                    )}
                  </SectionCard.Content>
                </SectionCard>

                {/* Advanced Section */}
                <SectionCard variant="amber">
                  <SectionCard.Header icon={IconAdjustments} title={t("forms.common.advancedOptions")} description={t("forms.llm.advancedDesc")} />
                  <SectionCard.Content>
                    {/* Response Format */}
                    <div className="w-full">
                      <FormField
                        control={form.control}
                        name="responseFormat"
                        render={({ field }) => (
                          <FormItem className="w-full">
                            <FormLabel>{t("forms.llm.responseFormat")}</FormLabel>
                            <div className="text-muted-foreground text-sm font-normal mt-1">
                              {t("forms.llm.responseFormatDesc")}
                            </div>
                            <FormControl>
                              <Input placeholder="e.g., JSON" type="text" className="w-full" {...field} />
                            </FormControl>
                            <FormMessage />
                          </FormItem>
                        )}
                      />
                    </div>
                    {/* Supported Capabilities */}
                    <div className="w-full">
                      <FormField
                        control={form.control}
                        name="supportedCapabilities"
                        render={({ field }) => (
                          <FormItem className="w-full">
                            <FormLabel>{t("forms.llm.capabilities")}</FormLabel>
                            <div className="text-muted-foreground text-sm font-normal mt-1">
                              {t("forms.llm.capabilitiesDesc")}
                            </div>
                            <FormControl>
                              <Input placeholder="e.g., RESPONSE_FORMAT_JSON_SCHEMA" type="text" className="w-full" {...field} />
                            </FormControl>
                            <FormMessage />
                          </FormItem>
                        )}
                      />
                    </div>
                    {/* Timeout */}
                    <div className="w-full">
                      <FormField
                        control={form.control}
                        name="timeout"
                        render={({ field }) => (
                          <FormItem className="w-full">
                            <FormLabel>{t("forms.llm.timeout")}</FormLabel>
                            <div className="text-muted-foreground text-sm font-normal mt-1">
                              {t("forms.llm.timeoutDesc")}
                            </div>
                            <FormControl>
                              <Input placeholder="e.g., PT60S" type="text" className="w-full" {...field} />
                            </FormControl>
                            <FormMessage />
                          </FormItem>
                        )}
                      />
                    </div>
                    {/* Max Retries */}
                    <div className="w-full">
                      <FormField
                        control={form.control}
                        name="maxRetries"
                        render={({ field }) => (
                          <FormItem className="w-full">
                            <FormLabel>{t("forms.llm.maxRetries")}</FormLabel>
                            <div className="text-muted-foreground text-sm font-normal mt-1">
                              {t("forms.llm.maxRetriesDesc")}
                            </div>
                            <FormControl>
                              <Input placeholder="e.g., 3" type="number" className="w-full" {...field} />
                            </FormControl>
                            <FormMessage />
                          </FormItem>
                        )}
                      />
                    </div>
                    {/* Provider Options JSON */}
                    <div className="w-full rounded-md border p-4 space-y-4">
                      <div className="text-sm font-medium">{t("forms.llm.visualOptions")}</div>
                      <div className="text-muted-foreground text-sm">
                        {t("forms.llm.visualOptionsDesc")}
                      </div>
                      {selectedVendorId === "OLLAMA" && (
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                          <Input placeholder="Embedding Model (e.g., nomic-embed-text)" value={llmProviderOptionsDraft.embeddingModel} onChange={(event) => setLlmDraftValue("embeddingModel", event.target.value)} />
                          <Input placeholder="Top K (e.g., 6)" type="number" value={llmProviderOptionsDraft.topK} onChange={(event) => setLlmDraftValue("topK", event.target.value)} />
                          <Input placeholder="Repeat Penalty (e.g., 1.1)" type="number" step="0.01" value={llmProviderOptionsDraft.repeatPenalty} onChange={(event) => setLlmDraftValue("repeatPenalty", event.target.value)} />
                          <Input placeholder="Num Predict (e.g., 256)" type="number" value={llmProviderOptionsDraft.numPredict} onChange={(event) => setLlmDraftValue("numPredict", event.target.value)} />
                          <Input placeholder="Stop (comma-separated, e.g., END,STOP)" value={llmProviderOptionsDraft.stop} onChange={(event) => setLlmDraftValue("stop", event.target.value)} />
                        </div>
                      )}
                      {selectedVendorId === "OPENAI" && (
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                          <Input placeholder="Embedding Model (e.g., text-embedding-3-small)" value={llmProviderOptionsDraft.embeddingModel} onChange={(event) => setLlmDraftValue("embeddingModel", event.target.value)} />
                          <Input placeholder="Max Tokens (e.g., 1024)" type="number" value={llmProviderOptionsDraft.maxTokens} onChange={(event) => setLlmDraftValue("maxTokens", event.target.value)} />
                        </div>
                      )}
                      {selectedVendorId === "ANTHROPIC" && (
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                          <Input placeholder="Top K (e.g., 40)" type="number" value={llmProviderOptionsDraft.topK} onChange={(event) => setLlmDraftValue("topK", event.target.value)} />
                          <Input placeholder="Max Tokens (e.g., 1024)" type="number" value={llmProviderOptionsDraft.maxTokens} onChange={(event) => setLlmDraftValue("maxTokens", event.target.value)} />
                        </div>
                      )}
                      {selectedVendorId === "GEMINI" && (
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                          <Input placeholder="Top K (e.g., 40)" type="number" value={llmProviderOptionsDraft.topK} onChange={(event) => setLlmDraftValue("topK", event.target.value)} />
                          <Input placeholder="Max Tokens (e.g., 8192)" type="number" value={llmProviderOptionsDraft.maxTokens} onChange={(event) => setLlmDraftValue("maxTokens", event.target.value)} />
                        </div>
                      )}
                      {selectedVendorId === "GEMINI_OPENAI" && (
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                          <Input placeholder="Max Tokens (e.g., 8192)" type="number" value={llmProviderOptionsDraft.maxTokens} onChange={(event) => setLlmDraftValue("maxTokens", event.target.value)} />
                        </div>
                      )}
                      {selectedVendorId === "AZURE_OPENAI" && (
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                          <Input placeholder="Chat Deployment Name (e.g., gpt-4o)" value={llmProviderOptionsDraft.deploymentName} onChange={(event) => setLlmDraftValue("deploymentName", event.target.value)} />
                          <Input placeholder="Embedding Deployment Name (e.g., text-embedding-ada-002)" value={llmProviderOptionsDraft.embeddingDeploymentName} onChange={(event) => setLlmDraftValue("embeddingDeploymentName", event.target.value)} />
                          <Input placeholder="Max Tokens (e.g., 1024)" type="number" value={llmProviderOptionsDraft.maxTokens} onChange={(event) => setLlmDraftValue("maxTokens", event.target.value)} />
                        </div>
                      )}
                    </div>

                    <div className="w-full">
                      <FormField
                        control={form.control}
                        name="providerOptionsJson"
                        render={({ field }) => (
                          <FormItem className="w-full">
                            <FormLabel>{t("forms.llm.providerOptionsJson")}</FormLabel>
                            <div className="text-muted-foreground text-sm font-normal mt-1">
                              {t("forms.llm.providerOptionsJsonDesc")}
                            </div>
                            <FormControl>
                              <Textarea
                                placeholder={getProviderOptionsPlaceholder(selectedVendorId)}
                                className="resize-y w-full min-h-40"
                                rows={10}
                                {...field}
                                value={field.value ?? ""}
                              />
                            </FormControl>
                            <FormMessage />
                          </FormItem>
                        )}
                      />
                    </div>
                  </SectionCard.Content>
                </SectionCard>

                {/* Status Section */}
                <SectionCard variant="slate">
                  <SectionCard.Header icon={IconToggleLeft} title={t("forms.common.status")} description={t("forms.llm.statusDesc")} />
                  <SectionCard.Content>
                    <FormField
                      control={form.control}
                      name="enabled"
                      render={({ field }) => (
                        <FormItemTwoColumns>
                          <FormItemTwoColumns.Left>
                            <FormItemTwoColumns.Label>{t("forms.common.enabled")}</FormItemTwoColumns.Label>
                            <FormItemTwoColumns.Description className="text-sm font-normal mt-1">
                              {t("forms.llm.enabledDesc")}
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
                    <FormField
                      control={form.control}
                      name="toolsEnabled"
                      render={({ field }) => (
                        <FormItemTwoColumns>
                          <FormItemTwoColumns.Left>
                            <FormItemTwoColumns.Label>{t("forms.llm.toolsEnabled")}</FormItemTwoColumns.Label>
                            <FormItemTwoColumns.Description className="text-sm font-normal mt-1">
                              {t("forms.llm.toolsEnabledDesc")}
                            </FormItemTwoColumns.Description>
                          </FormItemTwoColumns.Left>
                          <FormItemTwoColumns.Right>
                            <FormControl>
                              <GradientSwitch
                                checked={field.value === true}
                                onCheckedChange={(checked) => field.onChange(checked)}
                              />
                            </FormControl>
                          </FormItemTwoColumns.Right>
                          <FormMessage />
                        </FormItemTwoColumns>
                      )}
                    />
                  </SectionCard.Content>
                </SectionCard>

              {/* Action Footer */}
              <FormActions>
                {!isNew && <FormActions.Delete onClick={() => setOpen(true)} />}
                <FormActions.Cancel onClick={() => navigate(urlBase)} />
                <FormActions.Submit />
              </FormActions>
            </form>
          </Form>
        </CardContent>
      </Card>
    </div>
  )
}

