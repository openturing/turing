"use client"
import { useCreateEmbeddingModel, useUpdateEmbeddingModel } from "@/api/queries/embedding-model.queries"
import { useHuggingFaceModels, useLlmEmbeddingModels } from "@/api/queries/embedding-model-discovery.queries"
import { useLlmInstances } from "@/api/queries/llm-instance.queries"
import { ROUTES } from "@/app/routes.const"
import { BentoFormSection, BentoSaveBar, type BentoIdentity, type BentoShellFormState } from "@/components/bento"
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form"
import { HuggingFaceModelCombobox } from "@/components/ui/huggingface-model-combobox"
import { Input } from "@/components/ui/input"
import { ModelCombobox } from "@/components/ui/model-combobox"
import { Select, SelectContent, SelectItem, SelectSeparator, SelectTrigger, SelectValue } from "@/components/ui/select"
import type { TurEmbeddingModel } from "@/models/embedding/embedding-model.model.ts"
import { IconAdjustments, IconCube, IconPlug } from "@tabler/icons-react"
import { toast } from "@viglet/viglet-design-system"
import { useEffect, useState } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"

const TRANSFORMERS_LOCAL = "TRANSFORMERS_LOCAL"
const HUGGINGFACE = "HUGGINGFACE"
const urlBase = ROUTES.BENTO_EMBEDDING_MODEL_INSTANCE

interface Props {
  value: TurEmbeddingModel
  isNew: boolean
  readOnly?: boolean
  /** Identity — `title` here is aliased to the entity's `modelName`. */
  staged?: BentoIdentity
  onStateChange?: (state: BentoShellFormState) => void
}

/** DOM id used by hero-anchored submit buttons via the `form="..."` attribute. */
export const BENTO_EMBEDDING_FORM_ID = "bento-embedding-model-form"

export const BentoEmbeddingModelForm: React.FC<Props> = ({ value, isNew, readOnly = false, staged, onStateChange }) => {
  const { t } = useTranslation()
  const form = useForm<TurEmbeddingModel>({ defaultValues: value })
  const { data: llmInstances = [] } = useLlmInstances()
  const selectedProviderType = form.watch("providerType")
  const navigate = useNavigate()

  const createMutation = useCreateEmbeddingModel()
  const updateMutation = useUpdateEmbeddingModel()

  // Provider mode derived from providerType: TRANSFORMERS_LOCAL / HUGGINGFACE
  // sentinels, else an LLM-instance id (T626, §XXXVI).
  const isLocal = selectedProviderType === TRANSFORMERS_LOCAL
  const isHuggingFace = selectedProviderType === HUGGINGFACE
  const isLlmInstance = Boolean(selectedProviderType) && !isLocal && !isHuggingFace
  const selectedInstance = isLlmInstance
    ? llmInstances.find((i) => i.id === selectedProviderType)
    : undefined

  // HuggingFace picker: server-side search (T624).
  const [hfQuery, setHfQuery] = useState("")
  const { data: hfList, isFetching: hfLoading } = useHuggingFaceModels(hfQuery, isHuggingFace)

  // LLM embedding-model picker for the selected instance's vendor (T625).
  const {
    data: llmEmbeddingList,
    isFetching: llmEmbeddingLoading,
    refetch: refetchLlmEmbedding,
  } = useLlmEmbeddingModels({
    vendorId: selectedInstance?.turLLMVendor?.id,
    instanceId: selectedInstance?.id,
    enabled: isLlmInstance && Boolean(selectedInstance?.turLLMVendor?.id),
  })

  const isDirty = form.formState.isDirty
  const isSubmitting = form.formState.isSubmitting
  useEffect(() => {
    onStateChange?.({ isDirty, isSubmitting })
  }, [isDirty, isSubmitting, onStateChange])

  useEffect(() => {
    form.reset({
      ...value,
      providerType: value.providerType ?? "",
      modelName: value.modelName ?? "",
      modelReference: value.modelReference ?? "",
      modelPath: value.modelPath ?? "",
      tokenizerPath: value.tokenizerPath ?? "",
      description: value.description ?? "",
      enabled: value.enabled ?? 0,
    }, { keepDirtyValues: true })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value])

  async function onSubmit(model: TurEmbeddingModel) {
    // `modelName` is edited in the hero (aliased as staged.title).
    const modelName = staged?.title?.trim() ?? ""
    if (!modelName) {
      toast.error(t("forms.embedding.nameRequired"))
      return
    }
    const submittingLocal = model.providerType === TRANSFORMERS_LOCAL
    const submittingHf = model.providerType === HUGGINGFACE
    const payload: TurEmbeddingModel = {
      ...model,
      modelName,
      description: staged?.description ?? model.description,
      icon: staged?.icon ?? model.icon,
      enabled: staged?.enabled ?? model.enabled,
      // Only an LLM-instance provider carries an instance; the local + HF ONNX
      // paths run in-process with no instance.
      turLLMInstance: (submittingLocal || submittingHf) ? undefined : model.turLLMInstance,
      // Local uses hand-typed paths; HF + LLM use modelReference (repo id / model id).
      modelPath: submittingLocal ? model.modelPath : undefined,
      tokenizerPath: submittingLocal ? model.tokenizerPath : undefined,
    }

    try {
      if (isNew) {
        const result = await createMutation.mutateAsync(payload)
        if (result) {
          toast.success(t("forms.common.saved", { name: payload.modelName, feature: t("embeddingModel.title") }))
          navigate(urlBase)
        } else {
          toast.error(t("forms.common.notSaved", { name: payload.modelName, feature: t("embeddingModel.title") }))
        }
      } else {
        const result = await updateMutation.mutateAsync(payload)
        if (result) {
          toast.success(t("forms.common.updated", { name: payload.modelName, feature: t("embeddingModel.title") }))
        } else {
          toast.error(t("forms.common.notUpdated", { name: payload.modelName, feature: t("embeddingModel.title") }))
        }
      }
    } catch (error) {
      console.error("Form submission error", error)
      toast.error(t("forms.common.formSubmitFailed"))
    }
  }

  const stagedName = staged?.title?.trim() ?? ""
  const nameMissing = !stagedName
  let providerLabel: string | undefined
  if (isLocal) providerLabel = t("forms.embedding.transformersLocal")
  else if (isHuggingFace) providerLabel = t("forms.embedding.huggingFace")
  else providerLabel = selectedInstance?.title
  // Save bar is present on any editable detail page so it stays suspended on
  // scroll; Save is disabled until there is something to persist.
  const showSaveBar = !readOnly

  return (
    <Form {...form}>
      <form id={BENTO_EMBEDDING_FORM_ID} onSubmit={form.handleSubmit(onSubmit)} className="flex flex-col gap-5" autoComplete="off">
        {showSaveBar && (
          <>
            <div aria-hidden className="bento-save-bar-spacer" />
            <div className="bento-fade-in fixed inset-x-0 top-20 z-20 mx-auto w-full max-w-7xl px-4 md:px-8">
              <BentoSaveBar
                title={stagedName || (isNew ? t("embeddingModel.newEmbeddingModel") : t("embeddingModel.title"))}
                disabled={nameMissing || (!isNew && !form.formState.isDirty)}
                titleMissing={nameMissing}
                dirty={isNew || form.formState.isDirty}
                loading={isSubmitting}
                badges={providerLabel && <BentoSaveBar.Badge>{providerLabel}</BentoSaveBar.Badge>}
                onCancel={() => navigate(urlBase)}
              />
            </div>
          </>
        )}

        {/* Provider Section */}
        <BentoFormSection
          icon={IconPlug}
          tone="blue"
          title={t("forms.embedding.provider")}
          description={t("forms.embedding.providerDesc")}
        >
          <FormField
            control={form.control}
            name="providerType"
            rules={{ required: t("forms.embedding.providerRequired") }}
            render={({ field }) => (
              <FormItem className="w-full">
                <FormLabel>{t("forms.embedding.providerType")}</FormLabel>
                <p className="text-sm text-muted-foreground">{t("forms.embedding.providerTypeDesc")}</p>
                <Select
                  onValueChange={(val) => {
                    field.onChange(val)
                    if (val !== TRANSFORMERS_LOCAL && val !== HUGGINGFACE) {
                      const llm = llmInstances.find((i) => i.id === val)
                      if (llm) form.setValue("turLLMInstance", llm, { shouldDirty: true })
                    } else {
                      form.setValue("turLLMInstance", undefined, { shouldDirty: true })
                    }
                  }}
                  value={field.value}
                  disabled={readOnly}
                >
                  <FormControl>
                    <SelectTrigger className="w-full">
                      <SelectValue placeholder={t("forms.embedding.selectProvider")} />
                    </SelectTrigger>
                  </FormControl>
                  <SelectContent>
                    {llmInstances.map((instance) => (
                      <SelectItem key={instance.id} value={instance.id}>{instance.title}</SelectItem>
                    ))}
                    <SelectSeparator />
                    <SelectItem value={HUGGINGFACE}>{t("forms.embedding.huggingFace")}</SelectItem>
                    <SelectItem value={TRANSFORMERS_LOCAL}>{t("forms.embedding.transformersLocal")}</SelectItem>
                  </SelectContent>
                </Select>
                <FormMessage />
              </FormItem>
            )}
          />
        </BentoFormSection>

        {/* Model Configuration */}
        <BentoFormSection
          icon={IconCube}
          tone="violet"
          title={t("forms.embedding.modelConfig")}
          description={
            isHuggingFace
              ? t("forms.embedding.hfModelConfigDesc")
              : isLocal
                ? t("forms.embedding.localModelConfigDesc")
                : t("forms.embedding.modelConfigDesc")
          }
        >
          {/* HuggingFace repo picker (T626) */}
          {isHuggingFace && (
            <FormField
              control={form.control}
              name="modelReference"
              rules={{ required: t("forms.embedding.hfModelRequired") }}
              render={({ field }) => (
                <FormItem className="w-full">
                  <FormLabel>{t("forms.embedding.hfModel")}</FormLabel>
                  <p className="text-sm text-muted-foreground">{t("forms.embedding.hfModelDesc")}</p>
                  <FormControl>
                    <HuggingFaceModelCombobox
                      value={field.value ?? ""}
                      onChange={(v) => field.onChange(v)}
                      options={hfList?.models ?? []}
                      isLoading={hfLoading}
                      source={hfList?.source}
                      onQueryChange={setHfQuery}
                      disabled={readOnly}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          )}

          {/* LLM-instance embedding-model picker (T625/T626) */}
          {isLlmInstance && (
            <FormField
              control={form.control}
              name="modelReference"
              render={({ field }) => (
                <FormItem className="w-full">
                  <FormLabel>{t("forms.embedding.modelId")}</FormLabel>
                  <p className="text-sm text-muted-foreground">{t("forms.embedding.modelIdDesc")}</p>
                  <FormControl>
                    <ModelCombobox
                      value={field.value ?? ""}
                      onChange={(v) => field.onChange(v)}
                      options={llmEmbeddingList?.models ?? []}
                      isLoading={llmEmbeddingLoading}
                      source={llmEmbeddingList?.source}
                      onOpen={() => refetchLlmEmbedding()}
                      placeholder={t("forms.embedding.modelIdPlaceholder")}
                      disabled={readOnly}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          )}

          {/* Manual local ONNX paths (air-gapped / private / fine-tuned) */}
          {isLocal && (
            <>
              <FormField
                control={form.control}
                name="modelPath"
                rules={{
                  required: t("forms.embedding.modelPathRequired"),
                  pattern: { value: /\.onnx$/i, message: t("forms.embedding.onlyOnnx") },
                }}
                render={({ field }) => (
                  <FormItem className="w-full">
                    <FormLabel>{t("forms.embedding.modelPath")}</FormLabel>
                    <p className="text-sm text-muted-foreground">{t("forms.embedding.modelPathDesc")}</p>
                    <FormControl>
                      <Input {...field} value={field.value ?? ""} placeholder={t("forms.embedding.modelPathPlaceholder")} type="text" className="w-full" readOnly={readOnly} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="tokenizerPath"
                rules={{
                  required: t("forms.embedding.tokenizerRequired"),
                  pattern: { value: /\.json$/i, message: t("forms.embedding.onlyJsonTokenizer") },
                }}
                render={({ field }) => (
                  <FormItem className="w-full">
                    <FormLabel>{t("forms.embedding.tokenizerPath")}</FormLabel>
                    <p className="text-sm text-muted-foreground">{t("forms.embedding.tokenizerPathDesc")}</p>
                    <FormControl>
                      <Input {...field} value={field.value ?? ""} placeholder={t("forms.embedding.tokenizerPathPlaceholder")} type="text" className="w-full" readOnly={readOnly} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </>
          )}

          {/* No provider chosen yet */}
          {!selectedProviderType && (
            <p className="text-sm text-muted-foreground">{t("forms.embedding.selectProviderFirst")}</p>
          )}
        </BentoFormSection>

        {/* Advanced Options */}
        <BentoFormSection
          icon={IconAdjustments}
          tone="amber"
          title={t("forms.common.advancedOptions")}
          description={t("forms.embedding.advancedDesc")}
        >
          <FormField
            control={form.control}
            name="batchSize"
            render={({ field }) => (
              <FormItem className="w-full">
                <FormLabel>{t("forms.embedding.batchSize")}</FormLabel>
                <p className="text-sm text-muted-foreground">{t("forms.embedding.batchSizeDesc")}</p>
                <FormControl>
                  <Input
                    {...field}
                    value={field.value ?? ""}
                    onChange={(e) => {
                      const val = e.target.value
                      field.onChange(val === "" ? undefined : Number(val))
                    }}
                    placeholder="e.g., 32"
                    type="number"
                    min={1}
                    className="w-full"
                    readOnly={readOnly}
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
        </BentoFormSection>
      </form>
    </Form>
  )
}
