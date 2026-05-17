"use client"
import { ROUTES } from "@/app/routes.const"
import { SectionCard } from "../ui/section-card"
import { IconAdjustments, IconAlertTriangle, IconBrain, IconSettings, IconSparkles, IconToggleLeft, IconTrash } from "@tabler/icons-react"
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
  useCreateLlmInstance,
  useDeleteLlmInstance,
  useUpdateLlmInstance,
} from "@/api/queries/llm-instance.queries"
import type { TurLLMInstance } from "@/models/llm/llm-instance.model.ts"
import type { TurLLMVendor } from "@/models/llm/llm-vendor.model"
import { TurLLMVendorService } from "@/services/llm/llm-vendor.service"
import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import {
  useForm
} from "react-hook-form"
import { useNavigate } from "react-router-dom"
import { toast } from "@viglet/viglet-design-system"
import { DialogDelete } from "../dialog.delete"
import { Button } from "../ui/button"
import { FormItemTwoColumns } from "../ui/form-item-two-columns"
import { GradientSwitch } from "../ui/gradient-switch"
import { StickySaveBar } from "../ui/sticky-save-bar"
import { IconPicker } from "../ui/icon-picker"
import { SmartDescription } from "../ui/smart-description"
const turLLMVendorService = new TurLLMVendorService();
const urlBase = ROUTES.LLM_INSTANCE
interface Props {
  value: TurLLMInstance;
  isNew: boolean;
}

// `maxTokens` is rendered as a top-level UI field and routed per vendor:
//   - Ollama stores it on the `numPredict` entity column (Ollama's native "num_predict" param).
//   - All other vendors store it inside providerOptionsJson.maxTokens.
// Any other keys in providerOptionsJson are preserved as-is on save so
// legacy / custom options are never silently dropped.

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

const parseLlmProviderOptions = (jsonValue?: string): { maxTokens: string; unknown: Record<string, unknown> } => {
  const parsed = parseJsonObject(jsonValue)
  const unknown: Record<string, unknown> = {}
  if (!parsed) {
    return { maxTokens: "", unknown }
  }
  const maxTokens = toText(parsed.maxTokens)
  for (const [key, value] of Object.entries(parsed)) {
    if (key !== "maxTokens") {
      unknown[key] = value
    }
  }
  return { maxTokens, unknown }
}

const parseNumber = (value: string) => {
  const normalized = value.trim()
  if (!normalized) {
    return undefined
  }
  const parsed = Number(normalized)
  return Number.isFinite(parsed) ? parsed : undefined
}

const USES_NUM_PREDICT = (vendorId?: string) => vendorId === "OLLAMA"

export const LLMInstanceForm: React.FC<Props> = ({ value, isNew }) => {
  const { t } = useTranslation();
  const form = useForm<TurLLMInstance>({
    defaultValues: value
  });

  const [open, setOpen] = useState(false);
  const [vendors, setVendors] = useState<TurLLMVendor[]>([]);
  const [maxTokensInput, setMaxTokensInput] = useState<string>("")
  const [preservedOptions, setPreservedOptions] = useState<Record<string, unknown>>({})
  const selectedVendorId = form.watch("turLLMVendor.id");
  const navigate = useNavigate()

  const createMutation = useCreateLlmInstance()
  const updateMutation = useUpdateLlmInstance()
  const deleteMutation = useDeleteLlmInstance()

  useEffect(() => {
    turLLMVendorService.query().then(setVendors);
  }, [])

  useEffect(() => {
    form.reset({
      ...value,
      apiKey: "",
      providerOptionsJson: value.providerOptionsJson ?? ""
    });
    const { maxTokens: optionMaxTokens, unknown } = parseLlmProviderOptions(value.providerOptionsJson)
    // Ollama stores its "max tokens" as numPredict on the entity; others as providerOptions.maxTokens.
    if (USES_NUM_PREDICT(value.turLLMVendor?.id)) {
      setMaxTokensInput(value.numPredict != null ? String(value.numPredict) : "")
    } else {
      setMaxTokensInput(optionMaxTokens)
    }
    setPreservedOptions(unknown)
  }, [value])

  const applyVendorDefaults = (vendorId: string) => {
    // Defaults shared across every vendor — only applied on vendor change.
    form.setValue("timeout", "PT60S", { shouldDirty: true });
    form.setValue("maxRetries", 3, { shouldDirty: true });
    form.setValue("responseFormat", "", { shouldDirty: true });

    if (vendorId === "OLLAMA") {
      form.setValue("url", "http://localhost:11434", { shouldDirty: true });
      form.setValue("modelName", "mistral", { shouldDirty: true });
      form.setValue("temperature", 0.8, { shouldDirty: true });
      form.setValue("topP", 0.9, { shouldDirty: true });
      form.setValue("topK", 6, { shouldDirty: true });
      form.setValue("repeatPenalty", 1.1, { shouldDirty: true });
      form.setValue("seed", 42, { shouldDirty: true });
      form.setValue("stop", "", { shouldDirty: true });
      form.setValue("supportedCapabilities", "RESPONSE_FORMAT_JSON_SCHEMA", { shouldDirty: true });
      setMaxTokensInput("256")
    }
    if (vendorId === "OPENAI") {
      // The OpenAI Java SDK appends `/chat/completions` to the base URL, so the
      // version segment must be part of what we store. `https://api.openai.com`
      // alone yields a 404 because the SDK never inserts `/v1` itself.
      form.setValue("url", "https://api.openai.com/v1", { shouldDirty: true });
      form.setValue("modelName", "gpt-4o-mini", { shouldDirty: true });
      form.setValue("temperature", 0.7, { shouldDirty: true });
      form.setValue("topP", 0.9, { shouldDirty: true });
      form.setValue("seed", 42, { shouldDirty: true });
      form.setValue("supportedCapabilities", "", { shouldDirty: true });
      setMaxTokensInput("1024")
    }
    if (vendorId === "ANTHROPIC") {
      form.setValue("url", "https://api.anthropic.com", { shouldDirty: true });
      form.setValue("modelName", "claude-sonnet-4-20250514", { shouldDirty: true });
      form.setValue("temperature", 0.7, { shouldDirty: true });
      form.setValue("topP", 0.9, { shouldDirty: true });
      form.setValue("topK", 40, { shouldDirty: true });
      form.setValue("supportedCapabilities", "", { shouldDirty: true });
      setMaxTokensInput("1024")
    }
    if (vendorId === "GEMINI") {
      form.setValue("url", "", { shouldDirty: true });
      form.setValue("modelName", "gemini-2.0-flash", { shouldDirty: true });
      form.setValue("temperature", 0.7, { shouldDirty: true });
      form.setValue("topP", 0.9, { shouldDirty: true });
      form.setValue("topK", 40, { shouldDirty: true });
      form.setValue("supportedCapabilities", "", { shouldDirty: true });
      setMaxTokensInput("8192")
    }
    if (vendorId === "GEMINI_OPENAI") {
      form.setValue("url", "https://generativelanguage.googleapis.com/v1beta/openai", { shouldDirty: true });
      form.setValue("modelName", "gemini-2.0-flash", { shouldDirty: true });
      form.setValue("temperature", 0.7, { shouldDirty: true });
      form.setValue("topP", 0.9, { shouldDirty: true });
      form.setValue("supportedCapabilities", "", { shouldDirty: true });
      setMaxTokensInput("8192")
    }
  }

  async function onSubmit(llmInstance: TurLLMInstance) {
    const maxTokensParsed = parseNumber(maxTokensInput)
    const usesNumPredict = USES_NUM_PREDICT(selectedVendorId)

    const visualProviderOptions: Record<string, unknown> = {}
    if (!usesNumPredict && maxTokensParsed !== undefined) {
      visualProviderOptions.maxTokens = maxTokensParsed
    }
    const mergedProviderOptions = { ...preservedOptions, ...visualProviderOptions }
    const providerOptionsJson = Object.keys(mergedProviderOptions).length > 0
      ? JSON.stringify(mergedProviderOptions, null, 2)
      : undefined

    const payload: TurLLMInstance = {
      ...llmInstance,
      apiKey: llmInstance.apiKey?.trim() || undefined,
      // Ollama's "max tokens" lives on numPredict; everyone else uses providerOptions.maxTokens.
      numPredict: usesNumPredict ? (maxTokensParsed ?? null) as unknown as number : llmInstance.numPredict,
      providerOptionsJson
    }

    try {
      if (isNew) {
        const result = await createMutation.mutateAsync(payload);
        if (result) {
          toast.success(t("forms.common.saved", { name: payload.title, feature: t("llm.title") }));
          navigate(urlBase);
        } else {
          toast.error(t("forms.common.notSaved", { name: payload.title, feature: t("llm.title") }));
        }
      }
      else {
        const result = await updateMutation.mutateAsync(payload);
        if (result) {
          toast.success(t("forms.common.updated", { name: payload.title, feature: t("llm.title") }));
        } else {
          toast.error(t("forms.common.notUpdated", { name: payload.title, feature: t("llm.title") }));
        }
      }
    } catch (error) {
      console.error("Form submission error", error);
      toast.error(t("forms.common.formSubmitFailed"));
    }
  }

  async function onDelete() {
    try {
      if (await deleteMutation.mutateAsync(value)) {
        toast.success(t("forms.common.deleted", { name: value.title, feature: t("llm.title") }));
        navigate(urlBase);
      }
      else {
        toast.error(t("forms.common.notDeleted", { name: value.title, feature: t("llm.title") }));
      }

    } catch (error) {
      console.error("Form submission error", error);
      toast.error(t("forms.common.notDeleted", { name: value.title, feature: t("llm.title") }));
    }
    setOpen(false);
  }
  const watchedTitle = form.watch("title")
  const vendorLabel = vendors.find((v) => v.id === selectedVendorId)?.title

  return (
    <div className="w-full px-0 md:px-4 py-4">
      <Form {...form}>
        <form
          onSubmit={form.handleSubmit(onSubmit)}
          className="space-y-4 md:space-y-6"
          autoComplete="off"
        >
          <StickySaveBar
            title={watchedTitle || (isNew ? t("llm.newLanguageModel") : t("llm.title"))}
            badges={vendorLabel && <StickySaveBar.Badge>{vendorLabel}</StickySaveBar.Badge>}
            onCancel={() => navigate(urlBase)}
          />

          <Card className="mx-auto border-0 shadow-none md:border md:shadow-sm rounded-none md:rounded-xl max-md:**:data-[slot=card-header]:px-2 max-md:**:data-[slot=card-content]:px-2 max-md:**:data-[slot=card-content]:pt-0">
            <CardHeader className="hidden md:grid">
              <CardTitle className="text-2xl">{isNew ? t("llm.newLanguageModel") : t("llm.title")}</CardTitle>
              <CardAction>
                {!isNew && <DialogDelete feature="Language Model" name={value.title} onDelete={onDelete} open={open} setOpen={setOpen} />}
              </CardAction>
              <CardDescription>
                {t("forms.llm.settings")}
              </CardDescription>
            </CardHeader>
            <CardContent>
              <div className="w-full max-w-2xl mx-auto py-2 md:py-8 flex flex-col gap-4">
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
                                entityType={t("llm.title")}
                                enableMetaPrompt
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
                    {/* Seed — OLLAMA, OPENAI */}
                    {(selectedVendorId === "OLLAMA" || selectedVendorId === "OPENAI") && (
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
                    {/* Top K — OLLAMA, ANTHROPIC, GEMINI */}
                    {(selectedVendorId === "OLLAMA" || selectedVendorId === "ANTHROPIC" || selectedVendorId === "GEMINI") && (
                      <div className="w-full">
                        <FormField
                          control={form.control}
                          name="topK"
                          render={({ field }) => (
                            <FormItem className="w-full">
                              <FormLabel>{t("forms.llm.topK")}</FormLabel>
                              <div className="text-muted-foreground text-sm font-normal mt-1">
                                {t("forms.llm.topKDesc")}
                              </div>
                              <FormControl>
                                <Input placeholder="e.g., 40" type="number" className="w-full" {...field} />
                              </FormControl>
                              <FormMessage />
                            </FormItem>
                          )}
                        />
                      </div>
                    )}
                    {/* Max Tokens — all vendors. Routed to numPredict for Ollama, providerOptions.maxTokens otherwise. */}
                    {selectedVendorId && (
                      <div className="w-full">
                        <FormLabel>{t("forms.llm.maxTokens")}</FormLabel>
                        <div className="text-muted-foreground text-sm font-normal mt-1">
                          {t("forms.llm.maxTokensDesc")}
                        </div>
                        <Input
                          placeholder="e.g., 1024"
                          type="number"
                          className="w-full mt-2"
                          value={maxTokensInput}
                          onChange={(event) => setMaxTokensInput(event.target.value)}
                        />
                      </div>
                    )}
                    {/* Repeat Penalty, Stop — OLLAMA only */}
                    {selectedVendorId === "OLLAMA" && (
                      <>
                        <div className="w-full">
                          <FormField
                            control={form.control}
                            name="repeatPenalty"
                            render={({ field }) => (
                              <FormItem className="w-full">
                                <FormLabel>{t("forms.llm.repeatPenalty")}</FormLabel>
                                <div className="text-muted-foreground text-sm font-normal mt-1">
                                  {t("forms.llm.repeatPenaltyDesc")}
                                </div>
                                <FormControl>
                                  <Input placeholder="e.g., 1.1" type="number" step="0.01" className="w-full" {...field} />
                                </FormControl>
                                <FormMessage />
                              </FormItem>
                            )}
                          />
                        </div>
                        <div className="w-full">
                          <FormField
                            control={form.control}
                            name="stop"
                            render={({ field }) => (
                              <FormItem className="w-full">
                                <FormLabel>{t("forms.llm.stop")}</FormLabel>
                                <div className="text-muted-foreground text-sm font-normal mt-1">
                                  {t("forms.llm.stopDesc")}
                                </div>
                                <FormControl>
                                  <Input placeholder="e.g., END,STOP" type="text" className="w-full" {...field} value={field.value ?? ""} />
                                </FormControl>
                                <FormMessage />
                              </FormItem>
                            )}
                          />
                        </div>
                      </>
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

                {/* Danger zone — labelled, always-visible counterpart to the
                    desktop-only CardHeader trash icon. Shares the same
                    `open`/`setOpen` state so only one DialogDelete renders. */}
                {!isNew && (
                  <SectionCard variant="rose">
                    <SectionCard.Header
                      icon={IconAlertTriangle}
                      title={t("forms.llm.dangerZoneTitle")}
                      description={t("forms.llm.dangerZoneDesc")}
                    />
                    <SectionCard.Content>
                      <Button
                        type="button"
                        variant="destructive"
                        onClick={() => setOpen(true)}
                        className="w-full sm:w-auto"
                      >
                        <IconTrash className="size-4" />
                        {t("forms.llm.deleteAction")}
                      </Button>
                    </SectionCard.Content>
                  </SectionCard>
                )}
              </div>
            </CardContent>
          </Card>
        </form>
      </Form>
    </div>
  )
}

