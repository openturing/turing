"use client"
import { useCreateRoutine, useUpdateRoutine } from "@/api/queries/routine.queries"
import { ROUTES } from "@/app/routes.const"
import { BentoFormSection, BentoSaveBar, type BentoIdentity, type BentoShellFormState } from "@/components/bento"
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Textarea } from "@/components/ui/textarea"
import type { TurRoutine, TurRoutineKind } from "@/models/genai/routine.model"
import { IconCode, IconSettings } from "@tabler/icons-react"
import { toast } from "@viglet/viglet-design-system"
import { useEffect } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"

const KINDS: TurRoutineKind[] = ["NATIVE", "GROOVY"]
const urlBase = ROUTES.BENTO_ROUTINE_INSTANCE

interface Props {
  value: TurRoutine
  isNew: boolean
  /** Identity — `title` aliases the routine `name`; `enabled` is 0/1. */
  staged?: BentoIdentity
  onStateChange?: (state: BentoShellFormState) => void
}

/** DOM id used by hero-anchored submit buttons via the `form="..."` attribute. */
export const BENTO_ROUTINE_FORM_ID = "bento-routine-form"

export const BentoRoutineForm: React.FC<Props> = ({ value, isNew, staged, onStateChange }) => {
  const { t } = useTranslation()
  const form = useForm<TurRoutine>({ defaultValues: value })
  const navigate = useNavigate()
  const watchedKind = form.watch("kind")

  const createMutation = useCreateRoutine()
  const updateMutation = useUpdateRoutine()

  const isDirty = form.formState.isDirty
  const isSubmitting = form.formState.isSubmitting
  useEffect(() => {
    onStateChange?.({ isDirty, isSubmitting })
  }, [isDirty, isSubmitting, onStateChange])

  useEffect(() => {
    form.reset(value, { keepDirtyValues: true })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value])

  async function onSubmit(data: TurRoutine) {
    if (!staged?.title?.trim()) {
      toast.error(t("routine.nameRequired"))
      return
    }
    const payload: TurRoutine = {
      ...data,
      name: staged.title,
      description: staged.description,
      enabled: staged.enabled === 1,
      nativeToolName: data.kind === "NATIVE" ? data.nativeToolName : undefined,
      groovyScript: data.kind === "GROOVY" ? data.groovyScript : undefined,
    }
    try {
      const saved = isNew ? await createMutation.mutateAsync(payload) : await updateMutation.mutateAsync(payload)
      if (saved) {
        toast.success(isNew ? t("routine.created", { name: saved.name }) : t("routine.updated", { name: saved.name }))
        if (isNew) navigate(urlBase)
      } else {
        toast.error(t("routine.saveFailed"))
      }
    } catch (err) {
      console.error("Routine save failed", err)
      toast.error(t("routine.saveFailed"))
    }
  }

  const stagedTitle = staged?.title?.trim() ?? ""
  const titleMissing = !stagedTitle
  // Save bar is present on any editable detail page so it stays suspended on
  // scroll; Save is disabled until there is something to persist.
  const showSaveBar = true

  return (
    <Form {...form}>
      <form id={BENTO_ROUTINE_FORM_ID} onSubmit={form.handleSubmit(onSubmit)} className="flex flex-col gap-5" autoComplete="off">
        {showSaveBar && (
          <>
            <div aria-hidden className="bento-save-bar-spacer" />
            <div className="bento-fade-in fixed inset-x-0 top-20 z-20 mx-auto w-full max-w-7xl px-4 md:px-8">
              <BentoSaveBar
                title={stagedTitle || (isNew ? t("routine.newRoutine") : t("routine.title"))}
                disabled={titleMissing || (!isNew && !form.formState.isDirty)}
                titleMissing={titleMissing}
                dirty={isNew || form.formState.isDirty}
                loading={isSubmitting}
                badges={<BentoSaveBar.Badge>{watchedKind ?? "NATIVE"}</BentoSaveBar.Badge>}
                onCancel={() => navigate(urlBase)}
              />
            </div>
          </>
        )}

        <BentoFormSection
          icon={IconSettings}
          tone="emerald"
          title={t("routine.execution")}
          description={t("routine.executionDesc")}
        >
          <FormField
            control={form.control}
            name="kind"
            render={({ field }) => (
              <FormItem className="w-full">
                <FormLabel>{t("routine.kind")}</FormLabel>
                <p className="text-sm text-muted-foreground">{t("routine.kindDesc")}</p>
                <FormControl>
                  <Select value={field.value ?? "NATIVE"} onValueChange={(v) => field.onChange(v as TurRoutineKind)}>
                    <SelectTrigger className="w-full">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {KINDS.map((k) => (
                        <SelectItem key={k} value={k}>{t(`routine.kindOptions.${k}`)}</SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

          {watchedKind === "NATIVE" && (
            <FormField
              control={form.control}
              name="nativeToolName"
              rules={{ required: t("routine.nativeToolNameRequired") }}
              render={({ field }) => (
                <FormItem className="w-full">
                  <FormLabel>{t("routine.nativeToolName")}</FormLabel>
                  <p className="text-sm text-muted-foreground">{t("routine.nativeToolNameDesc")}</p>
                  <FormControl>
                    <Input {...field} value={field.value ?? ""} placeholder="get_current_time" className="w-full" />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          )}

          {watchedKind === "GROOVY" && (
            <FormField
              control={form.control}
              name="groovyScript"
              rules={{ required: t("routine.groovyScriptRequired") }}
              render={({ field }) => (
                <FormItem className="w-full">
                  <FormLabel className="flex items-center gap-2">
                    <IconCode className="size-4" />
                    {t("routine.groovyScript")}
                  </FormLabel>
                  <p className="text-sm text-muted-foreground">{t("routine.groovyScriptDesc")}</p>
                  <FormControl>
                    <Textarea
                      {...field}
                      value={field.value ?? ""}
                      rows={14}
                      spellCheck={false}
                      className="w-full font-mono text-xs"
                      placeholder={`// Groovy. args, conversationId, routineName are bound.\n// Last expression is written to the scheduleAgent node's outputVariable.\nreturn "hello \${args.name ?: 'world'}"\n`}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          )}

          <FormField
            control={form.control}
            name="defaultTimeoutMs"
            render={({ field }) => (
              <FormItem className="w-full">
                <FormLabel>{t("routine.defaultTimeoutMs")}</FormLabel>
                <p className="text-sm text-muted-foreground">{t("routine.defaultTimeoutMsDesc")}</p>
                <FormControl>
                  <Input
                    type="number"
                    min={1000}
                    step={1000}
                    className="w-full"
                    value={typeof field.value === "number" ? field.value : ""}
                    onChange={(e) => {
                      const v = e.target.value
                      field.onChange(v === "" ? undefined : Number(v))
                    }}
                    placeholder="60000"
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
