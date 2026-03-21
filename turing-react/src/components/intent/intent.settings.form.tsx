"use client"
import { ROUTES } from "@/app/routes.const"
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormMessage,
} from "@/components/ui/form"
import { Input } from "@/components/ui/input"
import { Textarea } from "@/components/ui/textarea"
import type { TurIntent, TurIntentAction } from "@/models/intent/intent.model"
import { TurIntentService } from "@/services/intent/intent.service"
import { IconPicker } from "@/components/ui/icon-picker"
import { SmartDescription } from "@/components/ui/smart-description"
import {
  IconGripVertical,
  IconInfoCircle,
  IconList,
  IconPlus,
  IconToggleLeft,
  IconTrash,
} from "@tabler/icons-react"
import { useEffect, useState } from "react"
import { useFieldArray, useForm } from "react-hook-form"
import { useNavigate } from "react-router-dom"
import { toast } from "sonner"
import { FormActions } from "../ui/form-actions"
import { FormItemTwoColumns } from "../ui/form-item-two-columns"
import { GradientButton } from "../ui/gradient-button"
import { GradientSwitch } from "../ui/gradient-switch"
import { SectionCard } from "../ui/section-card"

const turIntentService = new TurIntentService()
const urlBase = ROUTES.INTENT_INSTANCE

interface Props {
  value: TurIntent
  isNew: boolean
}

export const IntentSettingsForm: React.FC<Props> = ({ value, isNew }) => {
  const form = useForm<TurIntent>({
    defaultValues: value,
  })
  const navigate = useNavigate()

  const { fields, append, remove, move } = useFieldArray({
    control: form.control,
    name: "actions",
    keyName: "_fieldId",
  })

  useEffect(() => {
    form.reset({
      ...value,
      description: value.description ?? "",
      icon: value.icon ?? null,
      actions: (value.actions ?? []).map((a, i) => ({
        ...a,
        sortOrder: a.sortOrder ?? i,
      })),
    })
  }, [value])

  const [dragIndex, setDragIndex] = useState<number | null>(null)

  function handleDragStart(index: number) {
    setDragIndex(index)
  }

  function handleDragOver(e: React.DragEvent, index: number) {
    e.preventDefault()
    if (dragIndex !== null && dragIndex !== index) {
      move(dragIndex, index)
      setDragIndex(index)
    }
  }

  function handleDragEnd() {
    setDragIndex(null)
  }

  function addAction() {
    append({
      id: "",
      label: "",
      prompt: "",
      sortOrder: fields.length,
    } as TurIntentAction)
  }

  async function onSubmit(intent: TurIntent) {
    const payload: TurIntent = {
      ...intent,
      actions: intent.actions.map((a, i) => ({ ...a, sortOrder: i })),
    }
    try {
      if (isNew) {
        const result = await turIntentService.create(payload)
        if (result) {
          toast.success(`The "${payload.title}" intent was saved`)
          navigate(`${urlBase}/${result.id}/settings`)
        } else {
          toast.error(`The "${payload.title}" intent was not saved`)
        }
      } else {
        const result = await turIntentService.update(payload)
        if (result) {
          toast.success(`The "${payload.title}" intent was updated`)
        } else {
          toast.error(`The "${payload.title}" intent was not updated`)
        }
      }
    } catch (error) {
      console.error("Form submission error", error)
      toast.error("Failed to submit the form. Please try again.")
    }
  }

  return (
    <div className="px-6">
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 py-8">
          <SectionCard variant="blue">
            <SectionCard.Header
              icon={IconInfoCircle}
              title="General Information"
              description="Name and description for this intent category."
            />
            <SectionCard.Content>
              <FormField
                control={form.control}
                name="title"
                rules={{ required: "A title is required." }}
                render={({ field }) => (
                  <FormItemTwoColumns>
                    <FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Label>Title</FormItemTwoColumns.Label>
                      <FormItemTwoColumns.Description>
                        The category label shown in the chat suggestions.
                      </FormItemTwoColumns.Description>
                    </FormItemTwoColumns.Left>
                    <FormItemTwoColumns.Right>
                      <FormControl>
                        <Input
                          {...field}
                          placeholder="e.g., Boost my work productivity"
                          type="text"
                        />
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
                        placeholder="Describe the intent category..."
                        maxLength={500}
                        title={form.watch("title")}
                        entityType="Intent"
                      >
                        <SmartDescription.Label>Description</SmartDescription.Label>
                        <SmartDescription.Description>
                          A brief summary of what this category covers.
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
                        <IconPicker.Label>Icon</IconPicker.Label>
                        <IconPicker.Description>
                          An icon displayed next to the intent in the chat.
                        </IconPicker.Description>
                      </IconPicker>
                    </FormControl>
                  </FormItem>
                )}
              />
            </SectionCard.Content>
          </SectionCard>

          <SectionCard variant="violet">
            <SectionCard.Header
              icon={IconList}
              title="Actions"
              description="The prompt suggestions within this intent. Drag to reorder."
            />
            <SectionCard.Content>
              <div className="space-y-3">
                {fields.map((field, index) => (
                  <div
                    key={field._fieldId}
                    draggable
                    onDragStart={() => handleDragStart(index)}
                    onDragOver={(e) => handleDragOver(e, index)}
                    onDragEnd={handleDragEnd}
                    className={`flex items-start gap-2 rounded-lg border bg-background p-3 transition-all ${
                      dragIndex === index
                        ? "opacity-50 border-violet-500"
                        : "hover:border-violet-500/40"
                    }`}
                  >
                    <div className="pt-2 cursor-grab text-muted-foreground hover:text-foreground">
                      <IconGripVertical className="size-4" />
                    </div>
                    <div className="flex-1 space-y-2">
                      <FormField
                        control={form.control}
                        name={`actions.${index}.label`}
                        rules={{ required: "Label is required." }}
                        render={({ field: f }) => (
                          <FormItem>
                            <FormControl>
                              <Input
                                {...f}
                                placeholder="Action label (e.g., Create daily schedule)"
                                className="text-sm font-medium"
                              />
                            </FormControl>
                            <FormMessage />
                          </FormItem>
                        )}
                      />
                      <FormField
                        control={form.control}
                        name={`actions.${index}.prompt`}
                        rules={{ required: "Prompt is required." }}
                        render={({ field: f }) => (
                          <FormItem>
                            <FormControl>
                              <Textarea
                                {...f}
                                placeholder="The prompt that will be sent to the chat..."
                                className="resize-none text-sm min-h-15"
                              />
                            </FormControl>
                            <FormMessage />
                          </FormItem>
                        )}
                      />
                    </div>
                    <button
                      type="button"
                      onClick={() => remove(index)}
                      className="pt-2 text-muted-foreground hover:text-destructive transition-colors"
                      title="Remove action"
                    >
                      <IconTrash className="size-4" />
                    </button>
                  </div>
                ))}
              </div>
              <div className="mt-3">
                <GradientButton
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={addAction}
                >
                  <IconPlus className="size-4 mr-1" />
                  Add Action
                </GradientButton>
              </div>
            </SectionCard.Content>
          </SectionCard>

          <SectionCard variant="slate">
            <SectionCard.Header
              icon={IconToggleLeft}
              title="Status"
              description="Control whether this intent is shown in the chat."
            />
            <SectionCard.Content>
              <FormField
                control={form.control}
                name="enabled"
                render={({ field }) => (
                  <FormItemTwoColumns>
                    <FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Label>Enabled</FormItemTwoColumns.Label>
                      <FormItemTwoColumns.Description>
                        When disabled, this intent will not appear in chat suggestions.
                      </FormItemTwoColumns.Description>
                    </FormItemTwoColumns.Left>
                    <FormItemTwoColumns.Right>
                      <FormControl>
                        <GradientSwitch
                          checked={field.value === 1}
                          onCheckedChange={(checked) => {
                            field.onChange(checked ? 1 : 0)
                          }}
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
    </div>
  )
}
