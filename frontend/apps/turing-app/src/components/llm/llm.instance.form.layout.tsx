"use client"
import { ROUTES } from "@/app/routes.const"
import {
  useCreateLlmInstance,
  useDeleteLlmInstance,
  useUpdateLlmInstance,
} from "@/api/queries/llm-instance.queries"
import type { TurLLMInstance } from "@/models/llm/llm-instance.model.ts"
import type { TurLLMVendor } from "@/models/llm/llm-vendor.model"
import { TurLLMVendorService } from "@/services/llm/llm-vendor.service"
import { useInfraReadOnly } from "@/hooks/use-infra-read-only"
import {
  IconAdjustments,
  IconBrain,
  IconCpu2,
  IconSettings,
  IconSparkles,
  IconX,
} from "@tabler/icons-react"
import { useEffect, useMemo, useState } from "react"
import { useTranslation } from "react-i18next"
import { useForm, type UseFormReturn } from "react-hook-form"
import { useNavigate } from "react-router-dom"
import { toast } from "@viglet/viglet-design-system"
import { Form } from "@/components/ui/form"
import { GlobalBadge, GlobalReadOnlyNotice } from "../infra-global-notice"
import { GradientButton } from "../ui/gradient-button"
import { StickySaveBar } from "../ui/sticky-save-bar"
import { SubPage } from "../sub.page"

const turLLMVendorService = new TurLLMVendorService();
const urlBase = ROUTES.LLM_INSTANCE

interface Props {
  value: TurLLMInstance;
  isNew: boolean;
}

/**
 * Context shared by the LLM instance editor's section sub-pages. A single
 * `useForm` instance lives in this layout and is threaded to each section
 * through the router `<Outlet context={...}>`, so the sticky save bar persists
 * one payload regardless of which section is on screen.
 *
 * @since 2026.3.4
 */
export interface LLMFormContext {
  form: UseFormReturn<TurLLMInstance>;
  value: TurLLMInstance;
  isNew: boolean;
  readOnly: boolean;
  vendors: TurLLMVendor[];
  selectedVendorId: string;
  maxTokensInput: string;
  setMaxTokensInput: (value: string) => void;
  applyVendorDefaults: (vendorId: string) => void;
  onDelete: () => void;
  deleteOpen: boolean;
  setDeleteOpen: (open: boolean) => void;
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

export const LLMInstanceFormLayout: React.FC<Props> = ({ value, isNew }) => {
  const { t } = useTranslation();
  const form = useForm<TurLLMInstance>({
    defaultValues: value
  });

  const [deleteOpen, setDeleteOpen] = useState(false);
  const [vendors, setVendors] = useState<TurLLMVendor[]>([]);
  const [maxTokensInput, setMaxTokensInput] = useState<string>("")
  const [preservedOptions, setPreservedOptions] = useState<Record<string, unknown>>({})
  const selectedVendorId = form.watch("turLLMVendor.id");
  const { isGlobal, readOnly } = useInfraReadOnly(value?.tenantId);
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
    if (vendorId === "OPENAI_COMPAT") {
      // No default URL — the base URL identifies the actual vendor (DeepSeek,
      // Groq, OpenRouter, local vLLM, …). The placeholder below shows an example.
      form.setValue("url", "", { shouldDirty: true });
      form.setValue("modelName", "", { shouldDirty: true });
      form.setValue("temperature", 0.7, { shouldDirty: true });
      form.setValue("topP", 0.9, { shouldDirty: true });
      form.setValue("supportedCapabilities", "", { shouldDirty: true });
      setMaxTokensInput("1024")
    }
    if (vendorId === "BEDROCK") {
      // Bedrock is IAM-authenticated (region + credentials), not URL-based: the
      // region + optional accessKeyId/secretAccessKey go in provider options; the
      // model name is the Bedrock id or inference-profile ARN.
      form.setValue("url", "", { shouldDirty: true });
      form.setValue("modelName", "anthropic.claude-3-5-sonnet-20241022-v2:0", { shouldDirty: true });
      form.setValue("temperature", 0.7, { shouldDirty: true });
      form.setValue("topP", 0.9, { shouldDirty: true });
      form.setValue("supportedCapabilities", "", { shouldDirty: true });
      setMaxTokensInput("1024")
    }
    if (vendorId === "VOYAGE") {
      // Voyage is an embeddings/rerank specialist with no chat API — only the
      // API key + embedding model name matter.
      form.setValue("url", "https://api.voyageai.com/v1", { shouldDirty: true });
      form.setValue("modelName", "voyage-3", { shouldDirty: true });
      form.setValue("supportedCapabilities", "", { shouldDirty: true });
      setMaxTokensInput("")
    }
    if (vendorId === "COHERE") {
      // Cohere via its OpenAI-compatible endpoint: Command chat + Embed v4.
      form.setValue("url", "https://api.cohere.ai/compatibility/v1", { shouldDirty: true });
      form.setValue("modelName", "command-r-plus", { shouldDirty: true });
      form.setValue("temperature", 0.7, { shouldDirty: true });
      form.setValue("topP", 0.9, { shouldDirty: true });
      form.setValue("supportedCapabilities", "", { shouldDirty: true });
      setMaxTokensInput("1024")
    }
    if (vendorId === "MISTRAL") {
      // Mistral via its OpenAI-compatible endpoint: chat + mistral-embed.
      form.setValue("url", "https://api.mistral.ai/v1", { shouldDirty: true });
      form.setValue("modelName", "mistral-large-latest", { shouldDirty: true });
      form.setValue("temperature", 0.7, { shouldDirty: true });
      form.setValue("topP", 0.9, { shouldDirty: true });
      form.setValue("supportedCapabilities", "", { shouldDirty: true });
      setMaxTokensInput("1024")
    }
    if (vendorId === "VERTEX_AI") {
      // Vertex AI is IAM-authenticated (project + location + credentials), not
      // URL-based: set 'project'/'location' (and optional 'credentialsJson') in
      // provider options; the model is a Vertex Gemini id.
      form.setValue("url", "", { shouldDirty: true });
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
    setDeleteOpen(false);
  }

  const watchedTitle = form.watch("title")
  const vendorLabel = vendors.find((v) => v.id === selectedVendorId)?.title
  const instanceUrlBase = `${urlBase}/${isNew ? "new" : value.id}`;

  const navData = useMemo(() => {
    const navMain = [
      {
        title: t("llm.sidebar.general"),
        url: "/general",
        icon: IconSettings,
        showOnNew: true,
      },
      {
        title: t("llm.sidebar.model"),
        url: "/model",
        icon: IconBrain,
        showOnNew: true,
      },
      {
        title: t("llm.sidebar.generation"),
        url: "/generation",
        icon: IconSparkles,
        showOnNew: true,
      },
    ];
    if (!isNew) {
      navMain.push({
        title: t("llm.sidebar.capabilities"),
        url: "/capabilities",
        icon: IconAdjustments,
        showOnNew: false,
      });
    }
    return { navMain };
  }, [isNew, t]);

  const outletContext: LLMFormContext = {
    form,
    value,
    isNew,
    readOnly,
    vendors,
    selectedVendorId,
    maxTokensInput,
    setMaxTokensInput,
    applyVendorDefaults,
    onDelete,
    deleteOpen,
    setDeleteOpen,
  };

  return (
    <Form {...form}>
      <form
        onSubmit={form.handleSubmit(onSubmit)}
        className="space-y-4 md:space-y-6"
        autoComplete="off"
      >
        <StickySaveBar
          title={watchedTitle || (isNew ? t("llm.newLanguageModel") : t("llm.title"))}
          badges={
            <>
              {vendorLabel && <StickySaveBar.Badge>{vendorLabel}</StickySaveBar.Badge>}
              {isGlobal && <GlobalBadge />}
            </>
          }
          onCancel={() => navigate(urlBase)}
          actions={
            readOnly ? (
              <GradientButton type="button" variant="outline" size="sm" onClick={() => navigate(urlBase)}>
                <IconX className="size-4" />
                {t("forms.formActions.back", { defaultValue: "Back" })}
              </GradientButton>
            ) : undefined
          }
        />
        {readOnly && <GlobalReadOnlyNotice />}

        <SubPage
          icon={IconCpu2}
          feature={t("llm.title")}
          name={watchedTitle || value.title || t("llm.title")}
          urlBase={instanceUrlBase}
          isNew={isNew}
          data={navData}
          open={deleteOpen}
          setOpen={setDeleteOpen}
          onDelete={readOnly ? undefined : onDelete}
          outletContext={outletContext}
        />
      </form>
    </Form>
  )
}
