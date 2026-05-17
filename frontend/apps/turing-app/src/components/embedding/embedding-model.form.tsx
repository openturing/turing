"use client"
import {
  useCreateEmbeddingModel,
  useDeleteEmbeddingModel,
  useUpdateEmbeddingModel,
} from "@/api/queries/embedding-model.queries"
import { useLlmInstances } from "@/api/queries/llm-instance.queries"
import { ROUTES } from "@/app/routes.const"
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
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
  SelectValue
} from "@/components/ui/select"
import type { TurEmbeddingModel } from "@/models/embedding/embedding-model.model.ts"
import { IconAdjustments, IconAlertTriangle, IconCube, IconPlug, IconSettings, IconToggleLeft, IconTrash } from "@tabler/icons-react"
import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { useForm } from "react-hook-form"
import { useNavigate } from "react-router-dom"
import { toast } from "@viglet/viglet-design-system"
import { DialogDelete } from "../dialog.delete"
import { Button } from "../ui/button"
import { StickySaveBar } from "../ui/sticky-save-bar"
import { FormItemTwoColumns } from "../ui/form-item-two-columns"
import { GradientSwitch } from "../ui/gradient-switch"
import { IconPicker } from "../ui/icon-picker"
import { SectionCard } from "../ui/section-card"
import { SmartDescription } from "../ui/smart-description"

const TRANSFORMERS_LOCAL = "TRANSFORMERS_LOCAL"

const urlBase = ROUTES.EMBEDDING_MODEL_INSTANCE

interface Props {
  value: TurEmbeddingModel;
  isNew: boolean;
}

export const EmbeddingModelForm: React.FC<Props> = ({ value, isNew }) => {
  const { t } = useTranslation();
  const form = useForm<TurEmbeddingModel>({ defaultValues: value })
  const [open, setOpen] = useState(false)
  const { data: llmInstances = [] } = useLlmInstances()
  const navigate = useNavigate()
  const selectedProviderType = form.watch("providerType")

  // Mutations: cache invalidation happens inside each hook so the list page
  // refreshes automatically after navigate(urlBase).
  const createMutation = useCreateEmbeddingModel()
  const updateMutation = useUpdateEmbeddingModel()
  const deleteMutation = useDeleteEmbeddingModel()

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
    })
  }, [value])

  async function onSubmit(model: TurEmbeddingModel) {
    const payload: TurEmbeddingModel = {
      ...model,
      turLLMInstance: model.providerType === TRANSFORMERS_LOCAL
        ? undefined
        : model.turLLMInstance,
      modelPath: model.providerType === TRANSFORMERS_LOCAL ? model.modelPath : undefined,
      tokenizerPath: model.providerType === TRANSFORMERS_LOCAL ? model.tokenizerPath : undefined,
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

  async function onDelete() {
    try {
      if (await deleteMutation.mutateAsync(value)) {
        toast.success(t("forms.common.deleted", { name: value.modelName, feature: t("embeddingModel.title") }))
        navigate(urlBase)
      } else {
        toast.error(t("forms.common.notDeleted", { name: value.modelName, feature: t("embeddingModel.title") }))
      }
    } catch (error) {
      console.error("Form submission error", error)
      toast.error(t("forms.common.notDeleted", { name: value.modelName, feature: t("embeddingModel.title") }))
    }
    setOpen(false)
  }

  return (
    <div className="w-full px-0 md:px-4 py-4">
      <Card className="mx-auto md:max-w-2xl border-0 shadow-none md:border md:shadow-sm rounded-none md:rounded-xl max-md:**:data-[slot=card-header]:px-2 max-md:**:data-[slot=card-content]:px-2 max-md:**:data-[slot=card-content]:pt-0">
        <CardHeader className="hidden md:grid">
          <CardTitle className="text-2xl">{isNew ? t("embeddingModel.newInstance") : t("embeddingModel.title")}</CardTitle>
          <CardAction>
            {!isNew && <DialogDelete feature="embedding model" name={value.modelName} onDelete={onDelete} open={open} setOpen={setOpen} />}
          </CardAction>
          <CardDescription>
            {t("forms.embedding.settings")}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 max-w-3xl mx-auto py-2 md:py-8">
              <StickySaveBar
                title={form.watch("modelName") || (isNew ? t("embeddingModel.newEmbeddingModel") : t("embeddingModel.title"))}
                onCancel={() => navigate(urlBase)}
              />
              {/* General Information */}
              <SectionCard variant="blue">
                <SectionCard.Header icon={IconSettings} title={t("forms.common.generalInfo")} description={t("forms.embedding.generalDesc")} />
                <SectionCard.Content>
                  <div className="w-full">
                    <FormField
                      control={form.control}
                      name="modelName"
                      rules={{ required: t("forms.embedding.nameRequired") }}
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>{t("forms.embedding.modelName")}</FormLabel>
                          <FormDescription>
                            {t("forms.embedding.modelNameDesc")}
                          </FormDescription>
                          <FormControl>
                            <Input {...field} placeholder={t("forms.embedding.modelNamePlaceholder")} type="text" />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                  </div>
                  <div className="w-full">
                    <FormField
                      control={form.control}
                      name="description"
                      render={({ field }) => (
                        <FormItem>
                          <FormControl>
                            <SmartDescription
                              value={field.value}
                              onChange={field.onChange}
                              placeholder={t("forms.embedding.descriptionPlaceholder")}
                              maxLength={500}
                              title={form.watch("modelName")}
                              entityType={t("embeddingModel.title")}
                              enableMetaPrompt
                            >
                              <SmartDescription.Label>{t("forms.common.description")}</SmartDescription.Label>
                              <SmartDescription.Description>
                                {t("forms.embedding.descriptionDesc")}
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
                              title={form.watch("modelName")}
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

              {/* Provider */}
              <SectionCard variant="cyan">
                <SectionCard.Header icon={IconPlug} title={t("forms.embedding.provider")} description={t("forms.embedding.providerDesc")} />
                <SectionCard.Content>
                  <div className="w-full">
                    <FormField
                      control={form.control}
                      name="providerType"
                      rules={{ required: t("forms.embedding.providerRequired") }}
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>{t("forms.embedding.providerType")}</FormLabel>
                          <FormDescription>
                            {t("forms.embedding.providerTypeDesc")}
                          </FormDescription>
                          <Select
                            onValueChange={(val) => {
                              field.onChange(val)
                              if (val !== TRANSFORMERS_LOCAL) {
                                const llm = llmInstances.find(i => i.id === val)
                                if (llm) {
                                  form.setValue("turLLMInstance", llm)
                                }
                              } else {
                                form.setValue("turLLMInstance", undefined)
                              }
                            }}
                            value={field.value}
                          >
                            <FormControl>
                              <SelectTrigger className="w-full">
                                <SelectValue placeholder={t("forms.embedding.selectProvider")} />
                              </SelectTrigger>
                            </FormControl>
                            <SelectContent>
                              {llmInstances.map((instance) => (
                                <SelectItem key={instance.id} value={instance.id}>
                                  {instance.title}
                                </SelectItem>
                              ))}
                              <SelectItem value={TRANSFORMERS_LOCAL}>
                                {t("forms.embedding.transformersLocal")}
                              </SelectItem>
                            </SelectContent>
                          </Select>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                  </div>
                </SectionCard.Content>
              </SectionCard>

              {/* Model Configuration */}
              <SectionCard variant="violet">
                <SectionCard.Header icon={IconCube} title={t("forms.embedding.modelConfig")} description={t("forms.embedding.modelConfigDesc")} />
                <SectionCard.Content>
                  <div className="w-full">
                    <FormField
                      control={form.control}
                      name="modelReference"
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>{t("forms.embedding.modelId")}</FormLabel>
                          <FormDescription>
                            {t("forms.embedding.modelIdDesc")}
                          </FormDescription>
                          <FormControl>
                            <Input {...field} placeholder={t("forms.embedding.modelIdPlaceholder")} type="text" />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                  </div>

                  {/* Local Transformer fields */}
                  {selectedProviderType === TRANSFORMERS_LOCAL && (
                    <>
                      <div className="w-full">
                        <FormField
                          control={form.control}
                          name="modelPath"
                          rules={{
                            required: selectedProviderType === TRANSFORMERS_LOCAL ? t("forms.embedding.modelPathRequired") : false,
                            pattern: {
                              value: /\.onnx$/i,
                              message: t("forms.embedding.onlyOnnx")
                            }
                          }}
                          render={({ field }) => (
                            <FormItem>
                              <FormLabel>{t("forms.embedding.modelPath")}</FormLabel>
                              <FormDescription>
                                {t("forms.embedding.modelPathDesc")}
                              </FormDescription>
                              <FormControl>
                                <Input {...field} value={field.value ?? ""} placeholder={t("forms.embedding.modelPathPlaceholder")} type="text" />
                              </FormControl>
                              <FormMessage />
                            </FormItem>
                          )}
                        />
                      </div>
                      <div className="w-full">
                        <FormField
                          control={form.control}
                          name="tokenizerPath"
                          rules={{
                            required: selectedProviderType === TRANSFORMERS_LOCAL ? t("forms.embedding.tokenizerRequired") : false,
                            pattern: {
                              value: /\.json$/i,
                              message: t("forms.embedding.onlyJsonTokenizer")
                            }
                          }}
                          render={({ field }) => (
                            <FormItem>
                              <FormLabel>{t("forms.embedding.tokenizerPath")}</FormLabel>
                              <FormDescription>
                                {t("forms.embedding.tokenizerPathDesc")}
                              </FormDescription>
                              <FormControl>
                                <Input {...field} value={field.value ?? ""} placeholder={t("forms.embedding.tokenizerPathPlaceholder")} type="text" />
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

              {/* Advanced Options */}
              <SectionCard variant="amber">
                <SectionCard.Header icon={IconAdjustments} title={t("forms.common.advancedOptions")} description={t("forms.embedding.advancedDesc")} />
                <SectionCard.Content>
                  <div className="w-full">
                    <FormField
                      control={form.control}
                      name="batchSize"
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>{t("forms.embedding.batchSize")}</FormLabel>
                          <FormDescription>
                            {t("forms.embedding.batchSizeDesc")}
                          </FormDescription>
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
                            />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                  </div>
                </SectionCard.Content>
              </SectionCard>

              {/* Status */}
              <SectionCard variant="slate">
                <SectionCard.Header icon={IconToggleLeft} title={t("forms.common.status")} description={t("forms.embedding.statusDesc")} />
                <SectionCard.Content>
                  <FormField
                    control={form.control}
                    name="enabled"
                    render={({ field }) => (
                      <FormItemTwoColumns>
                        <FormItemTwoColumns.Left>
                          <FormItemTwoColumns.Label>{t("forms.common.enabled")}</FormItemTwoColumns.Label>
                          <FormItemTwoColumns.Description>
                            {t("forms.embedding.enabledDesc")}
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
                      </FormItemTwoColumns>
                    )}
                  />
                </SectionCard.Content>
              </SectionCard>

              {/* Danger zone — duplicate of the desktop-only CardHeader
                  trash icon as an always-visible labelled button.
                  Shares the same `open` state so only one dialog renders. */}
              {!isNew && (
                <SectionCard variant="rose">
                  <SectionCard.Header
                    icon={IconAlertTriangle}
                    title={t("forms.embedding.dangerZoneTitle")}
                    description={t("forms.embedding.dangerZoneDesc")}
                  />
                  <SectionCard.Content>
                    <Button
                      type="button"
                      variant="destructive"
                      onClick={() => setOpen(true)}
                      className="w-full sm:w-auto"
                    >
                      <IconTrash className="size-4" />
                      {t("forms.embedding.deleteAction")}
                    </Button>
                  </SectionCard.Content>
                </SectionCard>
              )}
            </form>
          </Form>
        </CardContent>
      </Card>
    </div>
  )
}
