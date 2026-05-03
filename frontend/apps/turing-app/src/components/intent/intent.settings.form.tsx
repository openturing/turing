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
import {
  useCreateIntent,
  useDeleteIntent,
  useUpdateIntent,
} from "@/api/queries/intent.queries"
import type { TurIntent, TurIntentAction } from "@/models/intent/intent.model"
import { IconPicker } from "@/components/ui/icon-picker"
import { SmartDescription } from "@/components/ui/smart-description"
import {
  IconDeviceFloppy,
  IconGripVertical,
  IconInfoCircle,
  IconList,
  IconPlus,
  IconSettings,
  IconToggleLeft,
  IconTrash,
  IconX,
} from "@tabler/icons-react"
import { useEffect, useState } from "react"
import { useFieldArray, useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"
import { toast } from "@viglet/viglet-design-system"
import { DialogDelete } from "../dialog.delete"
import { StickyPageHeader } from "../sticky-page-header"
import { FormItemTwoColumns } from "../ui/form-item-two-columns"
import { GradientButton } from "../ui/gradient-button"
import { GradientSwitch } from "../ui/gradient-switch"
import { SectionCard } from "../ui/section-card"

interface Props {
  value: TurIntent
  isNew: boolean
  agentId: string
}

export const IntentSettingsForm: React.FC<Props> = ({ value, isNew, agentId }) => {
  const urlBase = `${ROUTES.AI_AGENT_INSTANCE}/${agentId}/intent`
  const { t } = useTranslation();
  const form = useForm<TurIntent>({
    defaultValues: value,
  })
  const navigate = useNavigate()
  const [deleteOpen, setDeleteOpen] = useState(false)

  const createMutation = useCreateIntent();
  const updateMutation = useUpdateIntent();
  const deleteMutation = useDeleteIntent();

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
    /* Leave the id undefined so {@code TurAssignableUuidGenerator} on the
       backend assigns a fresh UUID. Sending "" instead would be preserved
       verbatim and the second new action collides with the first. */
    append({
      id: undefined as unknown as string,
      label: "",
      prompt: "",
      sortOrder: fields.length,
    } as TurIntentAction)
  }

  async function onDelete() {
    try {
      if (await deleteMutation.mutateAsync({ agentId, intent: value })) {
        toast.success(t("intent.deleted", { name: value.title }))
        navigate(urlBase)
      } else {
        toast.error(t("intent.notDeleted", { name: value.title }))
      }
    } catch (error) {
      console.error("Delete error", error)
      toast.error(t("intent.notDeleted", { name: value.title }))
    }
    setDeleteOpen(false)
  }

  async function onSubmit(intent: TurIntent) {
    /* Strip empty/missing ids so the backend UUID generator kicks in.
       Otherwise Hibernate persists `""` literally — second new action
       collides on the PK, and the intent itself ends up with id=""
       which makes its list-page link point back to the list. */
    const { id: intentId, ...intentRest } = intent
    const cleanIntent: TurIntent = (isNew && !intentId?.trim())
      ? (intentRest as TurIntent)
      : intent
    const payload: TurIntent = {
      ...cleanIntent,
      actions: intent.actions.map((a, i) => {
        const { id, ...rest } = a
        return id?.trim()
          ? { ...a, sortOrder: i }
          : { ...rest, sortOrder: i } as TurIntentAction
      }),
    }
    try {
      if (isNew) {
        const result = await createMutation.mutateAsync({ agentId, intent: payload })
        if (result) {
          toast.success(t("forms.common.saved", { name: `"${payload.title}"`, feature: t("intent.title") }))
          navigate(`${urlBase}/${result.id}/settings`)
        } else {
          toast.error(t("forms.common.notSaved", { name: `"${payload.title}"`, feature: t("intent.title") }))
        }
      } else {
        const result = await updateMutation.mutateAsync({ agentId, intent: payload })
        if (result) {
          toast.success(t("forms.common.updated", { name: `"${payload.title}"`, feature: t("intent.title") }))
        } else {
          toast.error(t("forms.common.notUpdated", { name: `"${payload.title}"`, feature: t("intent.title") }))
        }
      }
    } catch (error) {
      console.error("Form submission error", error)
      toast.error(t("forms.common.formSubmitFailed"))
    }
  }

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 px-4 lg:px-6 pb-8">
        <StickyPageHeader>
          <StickyPageHeader.Title
            icon={IconSettings}
            feature={t("intent.settings.title")}
            description={t("intent.settings.description")}
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
            {!isNew && (
              <DialogDelete
                feature={t("intent.title")}
                name={value.title}
                onDelete={onDelete}
                open={deleteOpen}
                setOpen={setDeleteOpen}
              />
            )}
          </StickyPageHeader.Actions>
        </StickyPageHeader>
          <SectionCard variant="blue">
            <SectionCard.Header
              icon={IconInfoCircle}
              title={t("forms.common.generalInfo")}
              description={t("forms.intentSettings.generalDesc")}
            />
            <SectionCard.Content>
              <FormField
                control={form.control}
                name="title"
                rules={{ required: t("forms.intentSettings.titleRequired") }}
                render={({ field }) => (
                  <FormItemTwoColumns>
                    <FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Label>{t("forms.common.title")}</FormItemTwoColumns.Label>
                      <FormItemTwoColumns.Description>
                        {t("forms.intentSettings.titleDesc")}
                      </FormItemTwoColumns.Description>
                    </FormItemTwoColumns.Left>
                    <FormItemTwoColumns.Right>
                      <FormControl>
                        <Input
                          {...field}
                          placeholder={t("forms.intentSettings.titlePlaceholder")}
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
                        placeholder={t("forms.intentSettings.descPlaceholder")}
                        maxLength={500}
                        title={form.watch("title")}
                        entityType={t("intent.title")}
                        enableMetaPrompt
                      >
                        <SmartDescription.Label>{t("forms.common.description")}</SmartDescription.Label>
                        <SmartDescription.Description>
                          {t("forms.intentSettings.descDesc")}
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
                        <IconPicker.Description>
                          {t("forms.intentSettings.iconDesc")}
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
              title={t("forms.intentSettings.actions")}
              description={t("forms.intentSettings.actionsDesc")}
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
                        rules={{ required: t("forms.intentSettings.labelRequired") }}
                        render={({ field: f }) => (
                          <FormItem>
                            <FormControl>
                              <Input
                                {...f}
                                placeholder={t("forms.intentSettings.actionLabel")}
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
                        rules={{ required: t("forms.intentSettings.promptRequired") }}
                        render={({ field: f }) => (
                          <FormItem>
                            <FormControl>
                              <Textarea
                                {...f}
                                placeholder={t("forms.intentSettings.actionPrompt")}
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
                      title={t("forms.intentSettings.removeAction")}
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
                  {t("forms.intentSettings.addAction")}
                </GradientButton>
              </div>
            </SectionCard.Content>
          </SectionCard>

          <SectionCard variant="slate">
            <SectionCard.Header
              icon={IconToggleLeft}
              title={t("forms.common.status")}
              description={t("forms.intentSettings.statusDesc")}
            />
            <SectionCard.Content>
              <FormField
                control={form.control}
                name="enabled"
                render={({ field }) => (
                  <FormItemTwoColumns>
                    <FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Label>{t("forms.common.enabled")}</FormItemTwoColumns.Label>
                      <FormItemTwoColumns.Description>
                        {t("forms.intentSettings.enabledDesc")}
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

        </form>
    </Form>
  )
}
