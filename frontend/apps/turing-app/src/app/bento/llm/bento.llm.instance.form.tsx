"use client"
import {
  useCreateLlmInstance,
  useUpdateLlmInstance,
} from "@/api/queries/llm-instance.queries"
import { ROUTES } from "@/app/routes.const"
import { BentoFormSection, BentoSaveBar } from "@/components/bento"
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form"
import { FormItemTwoColumns } from "@/components/ui/form-item-two-columns"
import { GradientSwitch } from "@/components/ui/gradient-switch"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import type { TurLLMInstance } from "@/models/llm/llm-instance.model.ts"
import type { TurLLMVendor } from "@/models/llm/llm-vendor.model"
import { TurLLMVendorService } from "@/services/llm/llm-vendor.service"
import { IconAdjustments, IconBrain, IconSettings, IconSparkles, IconToggleLeft } from "@tabler/icons-react"
import { useEffect, useState } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"
import { toast } from "@viglet/viglet-design-system"

const turLLMVendorService = new TurLLMVendorService()
const urlBase = ROUTES.BENTO_LLM_INSTANCE

interface StagedHeroFields {
  title: string
  description: string
  enabled: number
  icon: string | null
}

interface Props {
  value: TurLLMInstance
  isNew: boolean
  /**
   * Identity fields owned by the page hero (title, description, status,
   * icon). For existing instances they're already persisted via
   * immediate-save mutations, so this prop is essentially "the truth".
   * For new instances they're staged here only — the form's submit
   * merges them into the create payload. The corresponding FormFields
   * were lifted out of the form sections into the hero.
   */
  staged?: StagedHeroFields
  /**
   * Reports form state changes upward so the Shell can render the
   * hero-anchored Save/Cancel buttons with the right enabled/loading
   * state. Called every time `isDirty` or `isSubmitting` changes.
   */
  onStateChange?: (state: { isDirty: boolean; isSubmitting: boolean }) => void
}

/** DOM id used by hero-anchored submit buttons via the `form="..."` attribute. */
export const BENTO_LLM_FORM_ID = "bento-llm-instance-form"

// `maxTokens` is rendered as a top-level UI field and routed per vendor:
//   - Ollama stores it on the `numPredict` entity column (Ollama's native "num_predict" param).
//   - All other vendors store it inside providerOptionsJson.maxTokens.
// Any other keys in providerOptionsJson are preserved as-is on save so
// legacy / custom options are never silently dropped.

const toText = (value: unknown) => value == null ? "" : String(value)

const parseJsonObject = (jsonValue?: string) => {
  if (!jsonValue?.trim()) return undefined
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
  if (!parsed) return { maxTokens: "", unknown }
  const maxTokens = toText(parsed.maxTokens)
  for (const [key, value] of Object.entries(parsed)) {
    if (key !== "maxTokens") unknown[key] = value
  }
  return { maxTokens, unknown }
}

const parseNumber = (value: string) => {
  const normalized = value.trim()
  if (!normalized) return undefined
  const parsed = Number(normalized)
  return Number.isFinite(parsed) ? parsed : undefined
}

const USES_NUM_PREDICT = (vendorId?: string) => vendorId === "OLLAMA"

export const BentoLLMInstanceForm: React.FC<Props> = ({ value, isNew, staged, onStateChange }) => {
  const { t } = useTranslation()
  const form = useForm<TurLLMInstance>({ defaultValues: value })

  const [vendors, setVendors] = useState<TurLLMVendor[]>([])
  const [maxTokensInput, setMaxTokensInput] = useState<string>("")
  const [preservedOptions, setPreservedOptions] = useState<Record<string, unknown>>({})
  const selectedVendorId = form.watch("turLLMVendor.id")
  const navigate = useNavigate()

  const createMutation = useCreateLlmInstance()
  const updateMutation = useUpdateLlmInstance()

  useEffect(() => {
    turLLMVendorService.query().then(setVendors)
  }, [])

  /*
   * Report form state changes upward so the Shell can mirror them
   * in the hero-anchored Save/Cancel buttons (enable/disable,
   * loading spinner). The Shell owns "should the buttons be visible
   * at the hero position" decision.
   */
  const isDirty = form.formState.isDirty
  const isSubmitting = form.formState.isSubmitting
  useEffect(() => {
    onStateChange?.({ isDirty, isSubmitting })
  }, [isDirty, isSubmitting, onStateChange])

  useEffect(() => {
    /*
     * `keepDirtyValues: true` preserves user edits to URL / model /
     * API key / generation params during a re-prop. Necessary because
     * the hero's immediate-save mutations (title / description /
     * status / icon) update the React Query cache, which propagates a
     * fresh `value` prop here — without this option, in-progress
     * unsaved edits to other fields would be wiped each time the user
     * blurs an inline-edit in the hero.
     */
    form.reset({
      ...value,
      apiKey: "",
      providerOptionsJson: value.providerOptionsJson ?? "",
    }, { keepDirtyValues: true })
    const { maxTokens: optionMaxTokens, unknown } = parseLlmProviderOptions(value.providerOptionsJson)
    if (USES_NUM_PREDICT(value.turLLMVendor?.id)) {
      setMaxTokensInput(value.numPredict == null ? "" : String(value.numPredict))
    } else {
      setMaxTokensInput(optionMaxTokens)
    }
    setPreservedOptions(unknown)
    // form is stable across renders and including it triggers a needless re-reset.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value])

  const applyVendorDefaults = (vendorId: string) => {
    form.setValue("timeout", "PT60S", { shouldDirty: true })
    form.setValue("maxRetries", 3, { shouldDirty: true })
    form.setValue("responseFormat", "", { shouldDirty: true })

    if (vendorId === "OLLAMA") {
      form.setValue("url", "http://localhost:11434", { shouldDirty: true })
      form.setValue("modelName", "mistral", { shouldDirty: true })
      form.setValue("temperature", 0.8, { shouldDirty: true })
      form.setValue("topP", 0.9, { shouldDirty: true })
      form.setValue("topK", 6, { shouldDirty: true })
      form.setValue("repeatPenalty", 1.1, { shouldDirty: true })
      form.setValue("seed", 42, { shouldDirty: true })
      form.setValue("stop", "", { shouldDirty: true })
      form.setValue("supportedCapabilities", "RESPONSE_FORMAT_JSON_SCHEMA", { shouldDirty: true })
      setMaxTokensInput("256")
    }
    if (vendorId === "OPENAI") {
      form.setValue("url", "https://api.openai.com", { shouldDirty: true })
      form.setValue("modelName", "gpt-4o-mini", { shouldDirty: true })
      form.setValue("temperature", 0.7, { shouldDirty: true })
      form.setValue("topP", 0.9, { shouldDirty: true })
      form.setValue("seed", 42, { shouldDirty: true })
      form.setValue("supportedCapabilities", "", { shouldDirty: true })
      setMaxTokensInput("1024")
    }
    if (vendorId === "ANTHROPIC") {
      form.setValue("url", "https://api.anthropic.com", { shouldDirty: true })
      form.setValue("modelName", "claude-sonnet-4-20250514", { shouldDirty: true })
      form.setValue("temperature", 0.7, { shouldDirty: true })
      form.setValue("topP", 0.9, { shouldDirty: true })
      form.setValue("topK", 40, { shouldDirty: true })
      form.setValue("supportedCapabilities", "", { shouldDirty: true })
      setMaxTokensInput("1024")
    }
    if (vendorId === "GEMINI") {
      form.setValue("url", "", { shouldDirty: true })
      form.setValue("modelName", "gemini-2.0-flash", { shouldDirty: true })
      form.setValue("temperature", 0.7, { shouldDirty: true })
      form.setValue("topP", 0.9, { shouldDirty: true })
      form.setValue("topK", 40, { shouldDirty: true })
      form.setValue("supportedCapabilities", "", { shouldDirty: true })
      setMaxTokensInput("8192")
    }
    if (vendorId === "GEMINI_OPENAI") {
      form.setValue("url", "https://generativelanguage.googleapis.com/v1beta/openai", { shouldDirty: true })
      form.setValue("modelName", "gemini-2.0-flash", { shouldDirty: true })
      form.setValue("temperature", 0.7, { shouldDirty: true })
      form.setValue("topP", 0.9, { shouldDirty: true })
      form.setValue("supportedCapabilities", "", { shouldDirty: true })
      setMaxTokensInput("8192")
    }
  }

  async function onSubmit(llmInstance: TurLLMInstance) {
    // Hard gate: title is required, and it now lives in the hero only.
    // The SaveBar's `disabled` prop already prevents this from reaching
    // here in the happy path; the toast is a fallback for edge cases
    // (form submitted via Enter, programmatic submit, etc).
    if (!staged?.title?.trim()) {
      toast.error(t("forms.llm.titleRequired"))
      return
    }

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

    /*
     * Hero-managed identity fields (title/description/enabled/icon)
     * override the form state when provided. For existing instances
     * these are already persisted via immediate-save and `staged`
     * matches the cache; for new instances `staged` is the only
     * source — the form has no inputs for them.
     */
    const payload: TurLLMInstance = {
      ...llmInstance,
      ...staged,
      apiKey: llmInstance.apiKey?.trim() || undefined,
      numPredict: usesNumPredict ? (maxTokensParsed ?? null) as unknown as number : llmInstance.numPredict,
      providerOptionsJson,
    }

    try {
      if (isNew) {
        const result = await createMutation.mutateAsync(payload)
        if (result) {
          toast.success(t("forms.common.saved", { name: payload.title, feature: t("llm.title") }))
          navigate(urlBase)
        } else {
          toast.error(t("forms.common.notSaved", { name: payload.title, feature: t("llm.title") }))
        }
      } else {
        const result = await updateMutation.mutateAsync(payload)
        if (result) {
          toast.success(t("forms.common.updated", { name: payload.title, feature: t("llm.title") }))
        } else {
          toast.error(t("forms.common.notUpdated", { name: payload.title, feature: t("llm.title") }))
        }
      }
    } catch (error) {
      console.error("Form submission error", error)
      toast.error(t("forms.common.formSubmitFailed"))
    }
  }

  /*
   * Title source-of-truth shifted to the hero (BentoInlineEdit) when
   * we lifted identity fields out of the form. Read from `staged` so
   * the SaveBar headline mirrors what the user sees up top, and so
   * the required-field check matches the same value.
   */
  const stagedTitle = staged?.title?.trim() ?? ""
  const titleMissing = !stagedTitle
  const vendorLabel = vendors.find((v) => v.id === selectedVendorId)?.title
  /*
   * Save bar is rendered while there's something to save (dirty or
   * new). All visual interpolation is driven by `--bento-fade` on
   * `<html>` (set by the page Shell's scroll-linked rAF loop):
   *   - `bento-save-bar-spacer` reserves form-flow space proportional
   *     to `--bento-fade` (0px → 64px), pushing form content down
   *     gradually as scroll advances.
   *   - `bento-fade-in` fades + slides the fixed bar into place at
   *     top-20.
   *   - Pointer-events flip via `bento-fade-half` on `<html>` past
   *     the 0.5 midpoint.
   *
   * No React state for the animation — CSS does the work.
   */
  const showSaveBar = isNew || form.formState.isDirty

  return (
    <Form {...form}>
      <form id={BENTO_LLM_FORM_ID} onSubmit={form.handleSubmit(onSubmit)} className="flex flex-col gap-5" autoComplete="off">
        {showSaveBar && (
          <>
            {/* Flow spacer — height grows with --bento-fade. */}
            <div aria-hidden className="bento-save-bar-spacer" />
            {/* Fixed bar wrapper — viewport-anchored, width matches
                main's max-w-7xl container, opacity/translate driven
                by --bento-fade via `.bento-fade-in`. */}
            <div className="bento-fade-in fixed inset-x-0 top-20 z-20 mx-auto w-full max-w-7xl px-4 md:px-8">
              <BentoSaveBar
                title={stagedTitle || (isNew ? t("llm.newLanguageModel") : t("llm.title"))}
                disabled={titleMissing}
                loading={isSubmitting}
                badges={vendorLabel && <BentoSaveBar.Badge>{vendorLabel}</BentoSaveBar.Badge>}
                onCancel={() => navigate(urlBase)}
              />
            </div>
          </>
        )}

        {/* General Section */}
        <BentoFormSection
          icon={IconSettings}
          tone="blue"
          title={t("forms.common.generalInfo")}
          description={t("forms.llm.titleAndVendor")}
        >
          {/* Title is now edited inline in the hero (BentoInlineEdit). */}

          <div className="flex flex-row items-center justify-between gap-4">
            <div className="flex flex-col">
              <FormLabel>{t("forms.common.vendor")}</FormLabel>
              <p className="text-sm text-muted-foreground">{t("forms.llm.vendorDesc")}</p>
            </div>
            <div className="ml-8 flex-1">
              <FormField
                control={form.control}
                name="turLLMVendor.id"
                rules={{ required: t("forms.llm.vendorRequired") }}
                render={({ field }) => (
                  <FormItem className="w-full">
                    <FormControl>
                      <Select
                        onValueChange={(nextValue) => {
                          field.onChange(nextValue)
                          applyVendorDefaults(nextValue)
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

          {/*
           * Description is now edited inline in the hero (BentoInlineEdit
           * multiline). Icon picker is also in the hero
           * (BentoHeroIconPicker). See Shell in bento.llm.instance.page.tsx.
           */}
        </BentoFormSection>

        {/* Model Section */}
        <BentoFormSection
          icon={IconBrain}
          tone="violet"
          title={t("forms.llm.modelSettings")}
          description={t("forms.llm.modelSettingsDesc")}
        >
          <FormField
            control={form.control}
            name="url"
            rules={{ required: t("forms.se.endpointRequired") }}
            render={({ field }) => (
              <FormItem className="w-full">
                <FormLabel>{t("forms.common.endpointUrl")}</FormLabel>
                <p className="text-sm text-muted-foreground">{t("forms.llm.endpointDesc")}</p>
                <FormControl>
                  <Input placeholder={t("forms.llm.endpointPlaceholder")} type="text" className="w-full" {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

          <FormField
            control={form.control}
            name="modelName"
            rules={{ required: t("forms.llm.modelName") }}
            render={({ field }) => (
              <FormItem className="w-full">
                <FormLabel>{t("forms.llm.modelName")}</FormLabel>
                <p className="text-sm text-muted-foreground">{t("forms.llm.modelNameDesc")}</p>
                <FormControl>
                  <Input placeholder={t("forms.llm.modelNamePlaceholder")} type="text" autoComplete="one-time-code" className="w-full" {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

          <FormField
            control={form.control}
            name="apiKey"
            render={({ field }) => (
              <FormItem className="w-full">
                <FormLabel>{t("forms.common.apiKey")}</FormLabel>
                <p className="text-sm text-muted-foreground">{t("forms.llm.apiKeyDesc")}</p>
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
        </BentoFormSection>

        {/* Generation Section */}
        <BentoFormSection
          icon={IconSparkles}
          tone="emerald"
          title={t("forms.llm.genParams")}
          description={t("forms.llm.genParamsDesc")}
        >
          <FormField
            control={form.control}
            name="temperature"
            render={({ field }) => (
              <FormItem className="w-full">
                <FormLabel>{t("forms.llm.temperature")}</FormLabel>
                <p className="text-sm text-muted-foreground">{t("forms.llm.temperatureDesc")}</p>
                <FormControl>
                  <Input placeholder="e.g., 0.8" type="number" step="0.01" className="w-full" {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

          <FormField
            control={form.control}
            name="topP"
            render={({ field }) => (
              <FormItem className="w-full">
                <FormLabel>{t("forms.llm.topP")}</FormLabel>
                <p className="text-sm text-muted-foreground">{t("forms.llm.topPDesc")}</p>
                <FormControl>
                  <Input placeholder="e.g., 0.9" type="number" step="0.01" className="w-full" {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

          {(selectedVendorId === "OLLAMA" || selectedVendorId === "OPENAI") && (
            <FormField
              control={form.control}
              name="seed"
              render={({ field }) => (
                <FormItem className="w-full">
                  <FormLabel>{t("forms.llm.seed")}</FormLabel>
                  <p className="text-sm text-muted-foreground">{t("forms.llm.seedDesc")}</p>
                  <FormControl>
                    <Input placeholder="e.g., 42" type="number" className="w-full" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          )}

          {(selectedVendorId === "OLLAMA" || selectedVendorId === "ANTHROPIC" || selectedVendorId === "GEMINI") && (
            <FormField
              control={form.control}
              name="topK"
              render={({ field }) => (
                <FormItem className="w-full">
                  <FormLabel>{t("forms.llm.topK")}</FormLabel>
                  <p className="text-sm text-muted-foreground">{t("forms.llm.topKDesc")}</p>
                  <FormControl>
                    <Input placeholder="e.g., 40" type="number" className="w-full" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          )}

          {selectedVendorId && (
            <div className="w-full">
              <FormLabel>{t("forms.llm.maxTokens")}</FormLabel>
              <p className="text-sm text-muted-foreground">{t("forms.llm.maxTokensDesc")}</p>
              <Input
                placeholder="e.g., 1024"
                type="number"
                className="mt-2 w-full"
                value={maxTokensInput}
                onChange={(event) => setMaxTokensInput(event.target.value)}
              />
            </div>
          )}

          {selectedVendorId === "OLLAMA" && (
            <>
              <FormField
                control={form.control}
                name="repeatPenalty"
                render={({ field }) => (
                  <FormItem className="w-full">
                    <FormLabel>{t("forms.llm.repeatPenalty")}</FormLabel>
                    <p className="text-sm text-muted-foreground">{t("forms.llm.repeatPenaltyDesc")}</p>
                    <FormControl>
                      <Input placeholder="e.g., 1.1" type="number" step="0.01" className="w-full" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="stop"
                render={({ field }) => (
                  <FormItem className="w-full">
                    <FormLabel>{t("forms.llm.stop")}</FormLabel>
                    <p className="text-sm text-muted-foreground">{t("forms.llm.stopDesc")}</p>
                    <FormControl>
                      <Input placeholder="e.g., END,STOP" type="text" className="w-full" {...field} value={field.value ?? ""} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </>
          )}
        </BentoFormSection>

        {/* Advanced Section */}
        <BentoFormSection
          icon={IconAdjustments}
          tone="amber"
          title={t("forms.common.advancedOptions")}
          description={t("forms.llm.advancedDesc")}
        >
          <FormField
            control={form.control}
            name="responseFormat"
            render={({ field }) => (
              <FormItem className="w-full">
                <FormLabel>{t("forms.llm.responseFormat")}</FormLabel>
                <p className="text-sm text-muted-foreground">{t("forms.llm.responseFormatDesc")}</p>
                <FormControl>
                  <Input placeholder="e.g., JSON" type="text" className="w-full" {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="supportedCapabilities"
            render={({ field }) => (
              <FormItem className="w-full">
                <FormLabel>{t("forms.llm.capabilities")}</FormLabel>
                <p className="text-sm text-muted-foreground">{t("forms.llm.capabilitiesDesc")}</p>
                <FormControl>
                  <Input placeholder="e.g., RESPONSE_FORMAT_JSON_SCHEMA" type="text" className="w-full" {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="timeout"
            render={({ field }) => (
              <FormItem className="w-full">
                <FormLabel>{t("forms.llm.timeout")}</FormLabel>
                <p className="text-sm text-muted-foreground">{t("forms.llm.timeoutDesc")}</p>
                <FormControl>
                  <Input placeholder="e.g., PT60S" type="text" className="w-full" {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="maxRetries"
            render={({ field }) => (
              <FormItem className="w-full">
                <FormLabel>{t("forms.llm.maxRetries")}</FormLabel>
                <p className="text-sm text-muted-foreground">{t("forms.llm.maxRetriesDesc")}</p>
                <FormControl>
                  <Input placeholder="e.g., 3" type="number" className="w-full" {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
        </BentoFormSection>

        {/* Status Section */}
        <BentoFormSection
          icon={IconToggleLeft}
          tone="slate"
          title={t("forms.common.status")}
          description={t("forms.llm.statusDesc")}
        >
          {/*
           * Status (enabled/disabled) lives in the page hero — the
           * clickable Active/Idle pill in the trailing slot. Removed
           * here to keep one canonical place to edit identity fields.
           */}
          <FormField
            control={form.control}
            name="toolsEnabled"
            render={({ field }) => (
              <FormItemTwoColumns>
                <FormItemTwoColumns.Left>
                  <FormItemTwoColumns.Label>{t("forms.llm.toolsEnabled")}</FormItemTwoColumns.Label>
                  <FormItemTwoColumns.Description className="mt-1 text-sm font-normal">
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
        </BentoFormSection>
      </form>
    </Form>
  )
}
