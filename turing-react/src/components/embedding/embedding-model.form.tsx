"use client"
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
import type { TurLLMInstance } from "@/models/llm/llm-instance.model.ts"
import { TurEmbeddingModelService } from "@/services/embedding/embedding-model.service"
import { TurLLMInstanceService } from "@/services/llm/llm.service"
import { IconAdjustments, IconCube, IconPlug, IconSettings, IconToggleLeft } from "@tabler/icons-react"
import { useEffect, useState } from "react"
import { useForm } from "react-hook-form"
import { useNavigate } from "react-router-dom"
import { toast } from "sonner"
import { DialogDelete } from "../dialog.delete"
import { FormActions } from "../ui/form-actions"
import { FormItemTwoColumns } from "../ui/form-item-two-columns"
import { GradientSwitch } from "../ui/gradient-switch"
import { IconPicker } from "../ui/icon-picker"
import { SectionCard } from "../ui/section-card"
import { SmartDescription } from "../ui/smart-description"

const TRANSFORMERS_LOCAL = "TRANSFORMERS_LOCAL"

const turEmbeddingModelService = new TurEmbeddingModelService()
const turLLMInstanceService = new TurLLMInstanceService()
const urlBase = ROUTES.EMBEDDING_MODEL_INSTANCE

interface Props {
  value: TurEmbeddingModel;
  isNew: boolean;
}

export const EmbeddingModelForm: React.FC<Props> = ({ value, isNew }) => {
  const form = useForm<TurEmbeddingModel>({ defaultValues: value })
  const [open, setOpen] = useState(false)
  const [llmInstances, setLlmInstances] = useState<TurLLMInstance[]>([])
  const navigate = useNavigate()
  const selectedProviderType = form.watch("providerType")

  useEffect(() => {
    turLLMInstanceService.query().then(setLlmInstances)
  }, [])

  useEffect(() => {
    form.reset({
      ...value,
      providerType: value.providerType ?? "",
      modelName: value.modelName ?? "",
      modelReference: value.modelReference ?? "",
      modelPath: value.modelPath ?? "",
      tokenizerPath: value.tokenizerPath ?? "",
      description: value.description ?? "",
      enableGpu: value.enableGpu ?? false,
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
        const result = await turEmbeddingModelService.create(payload)
        if (result) {
          toast.success(`The ${payload.modelName} Embedding Model was saved`)
          navigate(urlBase)
        } else {
          toast.error(`The ${payload.modelName} Embedding Model was not saved`)
        }
      } else {
        const result = await turEmbeddingModelService.update(payload)
        if (result) {
          toast.success(`The ${payload.modelName} Embedding Model was updated`)
        } else {
          toast.error(`The ${payload.modelName} Embedding Model was not updated`)
        }
      }
    } catch (error) {
      console.error("Form submission error", error)
      toast.error("Failed to submit the form. Please try again.")
    }
  }

  async function onDelete() {
    try {
      if (await turEmbeddingModelService.delete(value)) {
        toast.success(`The ${value.modelName} Embedding Model was deleted`)
        navigate(urlBase)
      } else {
        toast.error(`The ${value.modelName} Embedding Model was not deleted`)
      }
    } catch (error) {
      console.error("Form submission error", error)
      toast.error(`The ${value.modelName} Embedding Model was not deleted`)
    }
    setOpen(false)
  }

  return (
    <div className="flex min-h-[60vh] h-full w-full items-center justify-center px-4">
      <Card className="mx-auto max-w-2xl">
        <CardHeader>
          <CardTitle className="text-2xl">{isNew && (<span>New</span>)} Embedding Model</CardTitle>
          <CardAction>
            {!isNew && <DialogDelete feature="embedding model" name={value.modelName} onDelete={onDelete} open={open} setOpen={setOpen} />}
          </CardAction>
          <CardDescription>
            Configure an embedding model for vector generation.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 max-w-3xl mx-auto py-8">
              {/* General Information */}
              <SectionCard variant="blue">
                <SectionCard.Header icon={IconSettings} title="General Information" description="Name and description for this embedding model." />
                <SectionCard.Content>
                  <div className="w-full">
                    <FormField
                      control={form.control}
                      name="modelName"
                      rules={{ required: "Model name is required." }}
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>Model Name</FormLabel>
                          <FormDescription>
                            A friendly name for the model (e.g., "Stella V5 High Precision").
                          </FormDescription>
                          <FormControl>
                            <Input {...field} placeholder="e.g., Stella V5 High Precision" type="text" />
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
                              placeholder="Enter a brief description"
                              maxLength={500}
                              title={form.watch("modelName")}
                              entityType="Embedding Model"
                            >
                              <SmartDescription.Label>Description</SmartDescription.Label>
                              <SmartDescription.Description>
                                Summarize the purpose or intended use of this embedding model.
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
                              <IconPicker.Label>Icon</IconPicker.Label>
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
                <SectionCard.Header icon={IconPlug} title="Provider" description="Select the LLM instance or local transformer." />
                <SectionCard.Content>
                  <div className="w-full">
                    <FormField
                      control={form.control}
                      name="providerType"
                      rules={{ required: "Please select a provider." }}
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>Provider Type</FormLabel>
                          <FormDescription>
                            Choose an LLM instance that supports embeddings, or select "Transformers (Local)" for ONNX models.
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
                                <SelectValue placeholder="Select provider..." />
                              </SelectTrigger>
                            </FormControl>
                            <SelectContent>
                              {llmInstances.map((instance) => (
                                <SelectItem key={instance.id} value={instance.id}>
                                  {instance.title}
                                </SelectItem>
                              ))}
                              <SelectItem value={TRANSFORMERS_LOCAL}>
                                Transformers (Local)
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
                <SectionCard.Header icon={IconCube} title="Model Configuration" description="Model identification and reference." />
                <SectionCard.Content>
                  <div className="w-full">
                    <FormField
                      control={form.control}
                      name="modelReference"
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>Model ID / Reference</FormLabel>
                          <FormDescription>
                            The technical identifier of the model (e.g., "text-embedding-3-large" or "stella-v5").
                          </FormDescription>
                          <FormControl>
                            <Input {...field} placeholder="e.g., text-embedding-3-large" type="text" />
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
                            required: selectedProviderType === TRANSFORMERS_LOCAL ? "Model path is required for local transformers." : false,
                            pattern: {
                              value: /\.onnx$/i,
                              message: "Only .onnx files are supported."
                            }
                          }}
                          render={({ field }) => (
                            <FormItem>
                              <FormLabel>Model Path</FormLabel>
                              <FormDescription>
                                Path to the ONNX model file on the filesystem (e.g., /models/stella-v5/model.onnx).
                              </FormDescription>
                              <FormControl>
                                <Input {...field} value={field.value ?? ""} placeholder="/path/to/model.onnx" type="text" />
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
                            required: selectedProviderType === TRANSFORMERS_LOCAL ? "Tokenizer path is required for local transformers." : false,
                            pattern: {
                              value: /\.json$/i,
                              message: "Only .json tokenizer files are supported."
                            }
                          }}
                          render={({ field }) => (
                            <FormItem>
                              <FormLabel>Tokenizer Path</FormLabel>
                              <FormDescription>
                                Path to the tokenizer.json file on the filesystem.
                              </FormDescription>
                              <FormControl>
                                <Input {...field} value={field.value ?? ""} placeholder="/path/to/tokenizer.json" type="text" />
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
                <SectionCard.Header icon={IconAdjustments} title="Advanced Options" description="Batch processing and hardware acceleration." />
                <SectionCard.Content>
                  <div className="w-full">
                    <FormField
                      control={form.control}
                      name="batchSize"
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>Batch Size</FormLabel>
                          <FormDescription>
                            Number of texts the model processes per batch. Useful for bulk imports.
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
                  <FormField
                    control={form.control}
                    name="enableGpu"
                    render={({ field }) => (
                      <FormItemTwoColumns>
                        <FormItemTwoColumns.Left>
                          <FormItemTwoColumns.Label>Enable GPU</FormItemTwoColumns.Label>
                          <FormItemTwoColumns.Description>
                            Enable hardware acceleration for local models that support GPU processing.
                          </FormItemTwoColumns.Description>
                        </FormItemTwoColumns.Left>
                        <FormItemTwoColumns.Right>
                          <FormControl>
                            <GradientSwitch
                              checked={field.value === true}
                              onCheckedChange={field.onChange}
                            />
                          </FormControl>
                        </FormItemTwoColumns.Right>
                      </FormItemTwoColumns>
                    )}
                  />
                </SectionCard.Content>
              </SectionCard>

              {/* Status */}
              <SectionCard variant="slate">
                <SectionCard.Header icon={IconToggleLeft} title="Status" description="Enable or disable this embedding model." />
                <SectionCard.Content>
                  <FormField
                    control={form.control}
                    name="enabled"
                    render={({ field }) => (
                      <FormItemTwoColumns>
                        <FormItemTwoColumns.Left>
                          <FormItemTwoColumns.Label>Enabled</FormItemTwoColumns.Label>
                          <FormItemTwoColumns.Description>
                            Toggle to activate or deactivate this embedding model.
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

              <FormActions>
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
