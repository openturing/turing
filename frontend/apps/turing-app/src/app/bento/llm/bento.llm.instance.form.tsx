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
import { LLMInstanceCapabilitiesForm } from "@/components/llm/llm.instance.capabilities.form"
import { useLlmInstanceCapabilities } from "@/api/queries/llm-capability.queries"
import { Input } from "@/components/ui/input"
import { LlmModelMultiSelect } from "@/components/ui/llm-model-multi-select"
import { ModelCombobox } from "@/components/ui/model-combobox"
import { ModelVerifyButton } from "@/components/ui/model-verify-button"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Textarea } from "@/components/ui/textarea"
import { useLlmModels } from "@/api/queries/llm-model.queries"
import type { TurLLMInstance } from "@/models/llm/llm-instance.model.ts"
import type { TurLlmModelMetadata } from "@/models/llm/llm-model-option.model.ts"
import { catalogSupportsFileInput, catalogSupportsTools } from "@/components/ui/model-meta"
import type { TurLLMVendor } from "@/models/llm/llm-vendor.model"
import { TurLLMInstanceService } from "@/services/llm/llm.service"
import { TurLLMVendorService } from "@/services/llm/llm-vendor.service"
import { IconAdjustments, IconBrain, IconSettings, IconSparkles, IconStack2, IconToggleLeft, IconVector } from "@tabler/icons-react"
import { useEffect, useMemo, useState } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"
import { toast } from "@viglet/viglet-design-system"

const turLLMVendorService = new TurLLMVendorService()
const turLLMInstanceService = new TurLLMInstanceService()
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

// Vendors whose backend provider IGNORES the instance `url` field: Vertex AI
// (project/location + IAM), Bedrock (region + IAM) and Voyage (fixed hardcoded
// endpoints). For these the Endpoint URL input is hidden so it doesn't confuse
// — the field stays blank and its required-rule never applies.
const VENDORS_WITHOUT_ENDPOINT = new Set(["VERTEX_AI", "BEDROCK", "VOYAGE"])
const VENDOR_NEEDS_ENDPOINT = (vendorId?: string) =>
  Boolean(vendorId) && !VENDORS_WITHOUT_ENDPOINT.has(vendorId as string)

/**
 * T781 — amber warning under a capability toggle: shown only when the toggle is
 * ON and the catalog has positive evidence the selected model does NOT support
 * it (would fail at runtime). Silent when unknown or supported, so it never
 * second-guesses a model the catalog doesn't describe.
 */
const CatalogCapabilityWarning: React.FC<{ active: boolean; supported?: boolean; messageKey: string }> = ({
  active,
  supported,
  messageKey,
}) => {
  const { t } = useTranslation()
  if (!active || supported !== false) return null
  return <p className="mt-1 text-xs text-amber-600 dark:text-amber-400">{t(messageKey)}</p>
}

/**
 * T780 — inline hint under a numeric limit field: shows the catalog's value for
 * the selected model, and warns (amber) when the hand-entered value exceeds that
 * catalog maximum (a likely mis-entry that would fail at runtime). Renders nothing
 * when the catalog has no value for this model.
 */
const CatalogLimitHint: React.FC<{ entered?: number | null; catalogValue?: number | null }> = ({
  entered,
  catalogValue,
}) => {
  const { t } = useTranslation()
  if (catalogValue == null) return null
  const exceeds = entered != null && entered > catalogValue
  return (
    <p className={`mt-1 text-xs ${exceeds ? "text-amber-600 dark:text-amber-400" : "text-muted-foreground"}`}>
      {exceeds
        ? t("forms.llm.catalogExceeds", { value: catalogValue.toLocaleString() })
        : t("forms.llm.catalogHint", { value: catalogValue.toLocaleString() })}
    </p>
  )
}

export const BentoLLMInstanceForm: React.FC<Props> = ({ value, isNew, staged, onStateChange }) => {
  const { t } = useTranslation()
  const form = useForm<TurLLMInstance>({ defaultValues: value })

  const [vendors, setVendors] = useState<TurLLMVendor[]>([])
  const [maxTokensInput, setMaxTokensInput] = useState<string>("")
  const [preservedOptions, setPreservedOptions] = useState<Record<string, unknown>>({})
  /*
   * `preservedOptions` and `maxTokensInput` live outside react-hook-form, so
   * editing only those (e.g. the Vertex project) would not flip
   * `form.formState.isDirty` and the Save bar would stay disabled. This flag
   * mirrors dirtiness for the option-backed inputs.
   */
  const [optionsDirty, setOptionsDirty] = useState(false)
  const selectedVendorId = form.watch("turLLMVendor.id")
  const watchedUrl = form.watch("url")
  const watchedApiKey = form.watch("apiKey")
  const navigate = useNavigate()

  // Multi-model selection: `modelNames` is the comma-separated superset, while
  // `modelName` stays the single default consumed by the rest of the platform.
  // Legacy instances have only `modelName` — seed the selection from it.
  const watchedModelName = form.watch("modelName")
  const watchedModelNames = form.watch("modelNames")
  const watchedEmbeddingModelName = form.watch("embeddingModelName")
  // T773 — in-process ONNX embedding vendors (T754). When one is selected the form
  // shows ONNX embedding fields instead of the chat-model + cloud-embedding sections.
  const isLocalOnnxVendor = selectedVendorId === "TRANSFORMERS_LOCAL"
  const isHfOnnxVendor = selectedVendorId === "HUGGINGFACE"
  const isOnnxVendor = isLocalOnnxVendor || isHfOnnxVendor
  const selectedModels = useMemo(() => {
    const list = (watchedModelNames ?? "")
      .split(",")
      .map((s) => s.trim())
      .filter(Boolean)
    const def = (watchedModelName ?? "").trim()
    if (list.length === 0) return def ? [def] : []
    // Keep the default present even if it isn't in the stored list yet.
    return def && !list.includes(def) ? [def, ...list] : list
  }, [watchedModelNames, watchedModelName])
  const defaultModel = (watchedModelName ?? "").trim() || selectedModels[0] || ""

  const handleModelsChange = (next: string[], nextDefault: string) => {
    form.setValue("modelNames", next.join(","), { shouldDirty: true })
    form.setValue("modelName", nextDefault, { shouldDirty: true })
    autoFillFromCatalog(nextDefault)
  }

  const createMutation = useCreateLlmInstance()
  const updateMutation = useUpdateLlmInstance()

  // Native Capabilities are surfaced as a second tab (instead of a card at the
  // bottom of the CRUD form). The tab only exists for a saved instance whose
  // vendor actually exposes native capabilities — mirrors the filter inside
  // LLMInstanceCapabilitiesForm so we never show an empty tab.
  const [activeTab, setActiveTab] = useState<"config" | "capabilities">("config")
  const { data: instanceCapabilities } = useLlmInstanceCapabilities(isNew ? undefined : value.id)
  const hasCapabilitiesTab = useMemo(
    () =>
      !isNew &&
      Boolean(value.id) &&
      (instanceCapabilities ?? []).some((c) => c.pluginType === selectedVendorId?.toLowerCase()),
    [isNew, value.id, instanceCapabilities, selectedVendorId],
  )
  // If the capabilities tab disappears (vendor changed to one without native
  // capabilities), fall back to the config tab so we never render nothing.
  useEffect(() => {
    if (!hasCapabilitiesTab && activeTab === "capabilities") {
      setActiveTab("config")
    }
  }, [hasCapabilitiesTab, activeTab])

  // T577 — models for the picker: live from the vendor when a key is available
  // (typed here, or reused from the saved instance), else the static catalog.
  const {
    data: modelList,
    isFetching: modelsLoading,
    refetch: refetchModels,
  } = useLlmModels({
    vendorId: selectedVendorId,
    instanceId: isNew ? undefined : value.id,
    apiKey: watchedApiKey,
    url: watchedUrl,
    providerOptionsJson: value.providerOptionsJson,
    enabled: Boolean(selectedVendorId),
  })

  // T780 — id → catalog metadata for the models currently offered by the picker,
  // so selecting a model can pre-fill its known limits and the form can validate
  // hand-entered values against the catalog.
  const modelMetaById = useMemo(() => {
    const map = new Map<string, TurLlmModelMetadata>()
    for (const option of modelList?.models ?? []) {
      if (option.metadata) map.set(option.id, option.metadata)
    }
    return map
  }, [modelList])

  const defaultModelMeta = modelMetaById.get(defaultModel)

  /**
   * T780 — on model selection, pre-fill the instance's context window and max
   * output tokens from the catalog, but only when the operator hasn't set them
   * (never clobber an explicit override). The operator can still edit or clear
   * the pre-filled value afterwards.
   */
  const autoFillFromCatalog = (modelId: string) => {
    const meta = modelMetaById.get(modelId)
    if (!meta) return
    if (meta.contextWindow != null && form.getValues("contextWindow") == null) {
      form.setValue("contextWindow", meta.contextWindow, { shouldDirty: true })
    }
    if (meta.maxOutputTokens != null && !maxTokensInput.trim()) {
      setMaxTokensInput(String(meta.maxOutputTokens))
      setOptionsDirty(true)
    }
  }

  /** T780 — pre-fill embedding dimensions from the catalog on embedding-model pick. */
  const handleEmbeddingModelChange = (modelId: string) => {
    form.setValue("embeddingModelName", modelId, { shouldDirty: true })
    const meta = modelMetaById.get(modelId)
    if (meta?.embeddingDimensions != null && form.getValues("embeddingDimensions") == null) {
      form.setValue("embeddingDimensions", meta.embeddingDimensions, { shouldDirty: true })
    }
  }

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
    setOptionsDirty(false)
    // form is stable across renders and including it triggers a needless re-reset.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value])

  /**
   * Updates a `providerOptionsJson`-backed key held in `preservedOptions`.
   * A blank value removes the key so we never persist e.g. `"project": ""`.
   */
  const setPreservedOption = (key: string, rawValue: string) => {
    setPreservedOptions((prev) => {
      const next = { ...prev }
      if (rawValue.trim()) {
        next[key] = rawValue
      } else {
        delete next[key]
      }
      return next
    })
    setOptionsDirty(true)
  }

  const isVertexAi = selectedVendorId === "VERTEX_AI"
  const vertexProject = toText(preservedOptions.project)
  const vertexLocation = toText(preservedOptions.location)
  const vertexProjectMissing = isVertexAi && !vertexProject.trim()

  const applyVendorDefaults = (vendorId: string) => {
    form.setValue("timeout", "PT60S", { shouldDirty: true })
    form.setValue("maxRetries", 3, { shouldDirty: true })
    form.setValue("responseFormat", "", { shouldDirty: true })
    // Reset the multi-model list so it reseeds from the new vendor's preset
    // model below (never mixing models across vendors).
    form.setValue("modelNames", "", { shouldDirty: true })
    form.setValue("embeddingModelName", "", { shouldDirty: true })

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
      form.setValue("url", "https://api.openai.com/v1", { shouldDirty: true })
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
      // Google AI Studio (Gemini Developer API) default endpoint — mirrors
      // GEMINI_DEFAULT_BASE_URL in TurNativeProviderClientImpl.
      form.setValue("url", "https://generativelanguage.googleapis.com", { shouldDirty: true })
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
    if (vendorId === "MISTRAL") {
      form.setValue("url", "https://api.mistral.ai/v1", { shouldDirty: true })
      form.setValue("modelName", "mistral-large-latest", { shouldDirty: true })
      form.setValue("temperature", 0.7, { shouldDirty: true })
      form.setValue("topP", 0.9, { shouldDirty: true })
      form.setValue("supportedCapabilities", "", { shouldDirty: true })
      setMaxTokensInput("8192")
    }
    if (vendorId === "COHERE") {
      // Cohere's OpenAI-compatibility gateway (chat + Embed v4).
      form.setValue("url", "https://api.cohere.ai/compatibility/v1", { shouldDirty: true })
      form.setValue("modelName", "command-r-plus", { shouldDirty: true })
      form.setValue("temperature", 0.7, { shouldDirty: true })
      form.setValue("topP", 0.9, { shouldDirty: true })
      form.setValue("supportedCapabilities", "", { shouldDirty: true })
      setMaxTokensInput("4096")
    }
    if (vendorId === "VOYAGE") {
      // Retrieval-specialist embeddings + rerank (no chat API). The provider
      // uses fixed hardcoded endpoints and ignores `url`, so it stays blank
      // and the Endpoint URL input is hidden.
      form.setValue("url", "", { shouldDirty: true })
      form.setValue("modelName", "voyage-3", { shouldDirty: true })
      form.setValue("temperature", 0.7, { shouldDirty: true })
      form.setValue("topP", 0.9, { shouldDirty: true })
      form.setValue("supportedCapabilities", "", { shouldDirty: true })
      setMaxTokensInput("")
    }
    if (vendorId === "OPENAI_COMPAT") {
      // Base URL is provider-specific (DeepSeek, Groq, xAI, OpenRouter,
      // local vLLM/LM-Studio…), so it can't be auto-filled — the user
      // must supply it. Everything else gets sensible defaults.
      form.setValue("url", "", { shouldDirty: true })
      form.setValue("modelName", "", { shouldDirty: true })
      form.setValue("temperature", 0.7, { shouldDirty: true })
      form.setValue("topP", 0.9, { shouldDirty: true })
      form.setValue("supportedCapabilities", "", { shouldDirty: true })
      setMaxTokensInput("1024")
    }
    if (vendorId === "BEDROCK") {
      // AWS Bedrock is IAM-authenticated and region-scoped — no base URL;
      // credentials/region are resolved from the AWS SDK provider chain.
      form.setValue("url", "", { shouldDirty: true })
      form.setValue("modelName", "anthropic.claude-3-5-sonnet-20241022-v2:0", { shouldDirty: true })
      form.setValue("temperature", 0.7, { shouldDirty: true })
      form.setValue("topP", 0.9, { shouldDirty: true })
      form.setValue("supportedCapabilities", "", { shouldDirty: true })
      setMaxTokensInput("4096")
    }
    if (vendorId === "VERTEX_AI") {
      // Regional endpoint + GCP project/location — no single universal URL;
      // resolved from provider options / ADC, so it can't be auto-filled.
      // `project` is surfaced as a required field below; `location` defaults
      // to us-central1 (mirrors DEFAULT_LOCATION in TurVertexAiLlmProvider).
      form.setValue("url", "", { shouldDirty: true })
      form.setValue("modelName", "gemini-2.0-flash", { shouldDirty: true })
      form.setValue("temperature", 0.7, { shouldDirty: true })
      form.setValue("topP", 0.9, { shouldDirty: true })
      form.setValue("supportedCapabilities", "", { shouldDirty: true })
      setMaxTokensInput("8192")
      setPreservedOptions((prev) => (prev.location ? prev : { ...prev, location: "us-central1" }))
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

    // T773 — an in-process ONNX embedding instance has no chat model; it needs an
    // embedding source instead (HF repo id, or local .onnx + tokenizer paths).
    if (isOnnxVendor) {
      const hasOnnxEmbedding = isHfOnnxVendor
        ? Boolean(llmInstance.embeddingModelName?.trim())
        : Boolean(llmInstance.embeddingModelPath?.trim()) &&
          Boolean(llmInstance.embeddingTokenizerPath?.trim())
      if (!hasOnnxEmbedding) {
        toast.error(
          t("forms.llm.onnxEmbeddingRequired", {
            defaultValue: "Provide the embedding source (a Hugging Face repo id, or the ONNX model + tokenizer paths).",
          }),
        )
        return
      }
    } else if (!llmInstance.modelName?.trim()) {
      // At least one chat model with a default is required (the default → modelName
      // is the single model the rest of the platform consumes).
      toast.error(t("forms.llm.modelsRequired", { defaultValue: "Select at least one model." }))
      return
    }

    // Vertex AI cannot be created without a GCP project — the backend
    // provider throws at model-build time otherwise.
    if (selectedVendorId === "VERTEX_AI" && !toText(preservedOptions.project).trim()) {
      toast.error(t("forms.llm.gcpProjectRequired"))
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
  // Save bar is present on any editable detail page so it stays suspended on
  // scroll; Save is disabled until there is something to persist.
  const showSaveBar = true

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
                disabled={titleMissing || vertexProjectMissing || (!isNew && !form.formState.isDirty && !optionsDirty)}
                titleMissing={titleMissing}
                dirty={isNew || form.formState.isDirty || optionsDirty}
                loading={isSubmitting}
                badges={vendorLabel && <BentoSaveBar.Badge>{vendorLabel}</BentoSaveBar.Badge>}
                onCancel={() => navigate(urlBase)}
              />
            </div>
          </>
        )}

        {/* Native Capabilities are shown as a second tab rather than a card at
            the bottom, keeping the CRUD form and the per-instance capability
            matrix on distinct surfaces of the same screen. */}
        {hasCapabilitiesTab && (
          <nav className="flex flex-wrap gap-2">
            {([
              { key: "config", label: t("forms.llm.tabConfiguration"), icon: IconSettings },
              { key: "capabilities", label: t("forms.llm.nativeCapabilities"), icon: IconSparkles },
            ] as const).map((tab) => {
              const isActive = activeTab === tab.key
              return (
                <button
                  key={tab.key}
                  type="button"
                  onClick={() => setActiveTab(tab.key)}
                  className={`bento-tile bento-tile-clickable inline-flex items-center gap-1.5 rounded-full border px-3.5 py-1.5 text-sm backdrop-blur transition-colors ${
                    isActive
                      ? "border-primary/40 bg-primary text-primary-foreground"
                      : "border-border/60 bg-card/60 text-muted-foreground hover:text-foreground"
                  }`}
                  aria-pressed={isActive}
                >
                  <tab.icon size={16} aria-hidden />
                  {tab.label}
                </button>
              )
            })}
          </nav>
        )}

        {/* Configuration tab — the CRUD form sections. Kept mounted (hidden via
            class) so form state and dirty tracking survive tab switches. */}
        <div className={`flex flex-col gap-5 ${activeTab === "config" ? "" : "hidden"}`}>
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
                          <SelectValue placeholder={t("forms.common.choose")} />
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
          {VENDOR_NEEDS_ENDPOINT(selectedVendorId) && (
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
          )}

          {isVertexAi && (
            <>
              <div className="w-full">
                <FormLabel className={vertexProjectMissing ? "text-destructive" : undefined}>
                  {t("forms.llm.gcpProject")}
                </FormLabel>
                <p className="text-sm text-muted-foreground">{t("forms.llm.gcpProjectDesc")}</p>
                <Input
                  placeholder={t("forms.llm.gcpProjectPlaceholder")}
                  type="text"
                  className="mt-2 w-full"
                  value={vertexProject}
                  onChange={(event) => setPreservedOption("project", event.target.value)}
                />
                {vertexProjectMissing && (
                  <p className="mt-1 text-sm font-medium text-destructive">
                    {t("forms.llm.gcpProjectRequired")}
                  </p>
                )}
              </div>
              <div className="w-full">
                <FormLabel>{t("forms.llm.gcpLocation")}</FormLabel>
                <p className="text-sm text-muted-foreground">{t("forms.llm.gcpLocationDesc")}</p>
                <Input
                  placeholder={t("forms.llm.gcpLocationPlaceholder")}
                  type="text"
                  className="mt-2 w-full"
                  value={vertexLocation}
                  onChange={(event) => setPreservedOption("location", event.target.value)}
                />
              </div>
            </>
          )}

          <FormField
            control={form.control}
            name="apiKey"
            render={({ field }) => (
              <FormItem className="w-full">
                <FormLabel>{isVertexAi ? t("forms.llm.vertexCredentials") : t("forms.common.apiKey")}</FormLabel>
                <p className="text-sm text-muted-foreground">
                  {isVertexAi ? t("forms.llm.vertexCredentialsDesc") : t("forms.llm.apiKeyDesc")}
                </p>
                <FormControl>
                  {isVertexAi ? (
                    // Vertex AI credentials are a multi-line service-account
                    // JSON. Rendered as a textarea (not masked) but still saved
                    // to the encrypted `apiKey` field.
                    <Textarea
                      placeholder={t("forms.llm.vertexCredentialsPlaceholder")}
                      rows={6}
                      className="w-full font-mono text-xs"
                      autoComplete="off"
                      spellCheck={false}
                      {...field}
                      value={field.value ?? ""}
                    />
                  ) : (
                    <Input
                      placeholder={t("forms.llm.apiKeyPlaceholder")}
                      type="text"
                      className="w-full [-webkit-text-security:disc]"
                      autoComplete="one-time-code"
                      {...field}
                      value={field.value ?? ""}
                    />
                  )}
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

          {/* Verify — fires a live probe against the SAVED instance so the
              operator can confirm the vendor/URL/model/key work. Saved-by-id
              only: disabled for new or unsaved-edit state (save first). */}
          <div className="w-full border-t pt-4">
            <FormLabel>{t("forms.common.verifyTitle")}</FormLabel>
            <p className="mb-3 text-sm text-muted-foreground">{t("forms.llm.verifyDesc")}</p>
            <ModelVerifyButton
              onVerify={() => turLLMInstanceService.verify(value.id)}
              disabled={isNew || form.formState.isDirty || optionsDirty}
              disabledHint={t("forms.common.verifySaveFirst")}
            />
          </div>
        </BentoFormSection>

        {/* Cloud/chat sections — hidden for in-process ONNX embedding vendors (T773). */}
        {!isOnnxVendor && (
          <>
            {/* Models Section — one or more models with a single default. */}
            <BentoFormSection
              icon={IconStack2}
              tone="violet"
              title={t("forms.llm.modelsTitle", { defaultValue: "Models" })}
              description={t("forms.llm.modelsDesc", {
                defaultValue:
                  "Select one or more models for this instance and mark one as the default — the default is the model used across the platform.",
              })}
            >
              <div className="w-full">
                <FormLabel>{t("forms.llm.modelsLabel", { defaultValue: "Available models" })}</FormLabel>
                <p className="mb-2 text-sm text-muted-foreground">
                  {t("forms.llm.modelsHelp", {
                    defaultValue: "Star a model to make it the default. The default must be one of the selected models.",
                  })}
                </p>
                <LlmModelMultiSelect
                  selected={selectedModels}
                  defaultModel={defaultModel}
                  onChange={handleModelsChange}
                  options={modelList?.models ?? []}
                  isLoading={modelsLoading}
                  source={modelList?.source}
                  onOpen={() => refetchModels()}
                  expectedKind="CHAT"
                />
                {selectedModels.length === 0 && (
                  <p className="mt-2 text-sm font-medium text-destructive">
                    {t("forms.llm.modelsRequired", { defaultValue: "Select at least one model." })}
                  </p>
                )}
              </div>
            </BentoFormSection>

            {/* Embedding default (T757 / ADR 0004) — the embedding model this instance
                serves for RAG / vector search. Optional: a chat-only instance leaves it
                blank. Unifies the retired standalone embedding-model surface. */}
            <BentoFormSection
              icon={IconVector}
              tone="emerald"
              title={t("forms.llm.embeddingDefaultTitle", { defaultValue: "Embedding default" })}
              description={t("forms.llm.embeddingDefaultDesc", {
                defaultValue:
                  "Optionally set the embedding model this instance serves — used by RAG and vector search. Leave blank for a chat-only instance.",
              })}
            >
              <div className="w-full">
                <FormLabel>{t("forms.llm.embeddingModelName", { defaultValue: "Embedding model" })}</FormLabel>
                <ModelCombobox
                  value={watchedEmbeddingModelName ?? ""}
                  onChange={handleEmbeddingModelChange}
                  options={modelList?.models ?? []}
                  isLoading={modelsLoading}
                  source={modelList?.source}
                  onOpen={() => refetchModels()}
                  expectedKind="EMBEDDING"
                  placeholder={t("forms.llm.embeddingModelPlaceholder", {
                    defaultValue: "Select an embedding model (optional)",
                  })}
                />
              </div>
            </BentoFormSection>
          </>
        )}

        {/* In-process ONNX embedding creation (T773) — local .onnx + tokenizer, or a
            Hugging Face repo id. Shown only for the TRANSFORMERS_LOCAL / HUGGINGFACE
            vendors; this is the unified home for what /bento/embedding used to create. */}
        {isOnnxVendor && (
          <BentoFormSection
            icon={IconVector}
            tone="emerald"
            title={t("forms.llm.onnxEmbeddingTitle", { defaultValue: "ONNX embedding model" })}
            description={t("forms.llm.onnxEmbeddingDesc", {
              defaultValue:
                "This in-process vendor serves a single local embedding model (no cloud, no chat). Point it at a Hugging Face repo id, or the ONNX model + tokenizer files.",
            })}
          >
            <div className="grid w-full gap-4">
              {isHfOnnxVendor && (
                <FormField
                  control={form.control}
                  name="embeddingModelName"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("forms.llm.hfRepoId", { defaultValue: "Hugging Face repo id" })}</FormLabel>
                      <FormControl>
                        <Input
                          {...field}
                          value={field.value ?? ""}
                          placeholder="e.g. sentence-transformers/all-MiniLM-L6-v2"
                          type="text"
                          className="w-full"
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              )}
              {isLocalOnnxVendor && (
                <>
                  <FormField
                    control={form.control}
                    name="embeddingModelPath"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>{t("forms.llm.onnxModelPath", { defaultValue: "ONNX model path (.onnx)" })}</FormLabel>
                        <FormControl>
                          <Input {...field} value={field.value ?? ""} placeholder="/models/model.onnx" type="text" className="w-full" />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  <FormField
                    control={form.control}
                    name="embeddingTokenizerPath"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>{t("forms.llm.onnxTokenizerPath", { defaultValue: "Tokenizer path (tokenizer.json)" })}</FormLabel>
                        <FormControl>
                          <Input {...field} value={field.value ?? ""} placeholder="/models/tokenizer.json" type="text" className="w-full" />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </>
              )}
              <FormField
                control={form.control}
                name="embeddingBatchSize"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{t("forms.llm.onnxBatchSize", { defaultValue: "Batch size (optional)" })}</FormLabel>
                    <FormControl>
                      <Input
                        type="number"
                        className="w-full"
                        value={field.value ?? ""}
                        onChange={(e) => field.onChange(e.target.value === "" ? undefined : Number(e.target.value))}
                        placeholder="16"
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>
          </BentoFormSection>
        )}

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
              <CatalogLimitHint
                entered={parseNumber(maxTokensInput)}
                catalogValue={defaultModelMeta?.maxOutputTokens}
              />
            </div>
          )}

          {selectedVendorId && !isOnnxVendor && (
            <FormField
              control={form.control}
              name="contextWindow"
              render={({ field }) => (
                <FormItem className="w-full">
                  <FormLabel>{t("forms.llm.contextWindow", { defaultValue: "Context window" })}</FormLabel>
                  <p className="text-sm text-muted-foreground">
                    {t("forms.llm.contextWindowDesc", {
                      defaultValue:
                        "Max prompt tokens the model accepts. Auto-filled from the catalog when you pick a model; override if needed.",
                    })}
                  </p>
                  <FormControl>
                    <Input
                      placeholder="e.g., 128000"
                      type="number"
                      className="w-full"
                      value={field.value ?? ""}
                      onChange={(e) => field.onChange(e.target.value === "" ? undefined : Number(e.target.value))}
                    />
                  </FormControl>
                  <CatalogLimitHint
                    entered={field.value ?? undefined}
                    catalogValue={defaultModelMeta?.contextWindow}
                  />
                  <FormMessage />
                </FormItem>
              )}
            />
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
                <CatalogCapabilityWarning
                  active={field.value === true}
                  supported={catalogSupportsTools(defaultModelMeta)}
                  messageKey="forms.llm.toolsUnsupportedWarning"
                />
                <FormMessage />
              </FormItemTwoColumns>
            )}
          />
          <FormField
            control={form.control}
            name="fileUploadEnabled"
            render={({ field }) => (
              <FormItemTwoColumns>
                <FormItemTwoColumns.Left>
                  <FormItemTwoColumns.Label>{t("forms.llm.fileUploadEnabled")}</FormItemTwoColumns.Label>
                  <FormItemTwoColumns.Description className="mt-1 text-sm font-normal">
                    {t("forms.llm.fileUploadEnabledDesc")}
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
                <CatalogCapabilityWarning
                  active={field.value === true}
                  supported={catalogSupportsFileInput(defaultModelMeta)}
                  messageKey="forms.llm.fileUploadUnsupportedWarning"
                />
                <FormMessage />
              </FormItemTwoColumns>
            )}
          />
        </BentoFormSection>
        </div>

        {/* Native Capabilities tab — the per-instance capability matrix (F.15 /
            T132). Self-saves per toggle through the capability API, so it's
            independent of the SaveBar and only shown for a saved instance whose
            vendor exposes native capabilities. */}
        {hasCapabilitiesTab && activeTab === "capabilities" && value.id && (
          <LLMInstanceCapabilitiesForm instanceId={value.id} vendorId={selectedVendorId} />
        )}
      </form>
    </Form>
  )
}
