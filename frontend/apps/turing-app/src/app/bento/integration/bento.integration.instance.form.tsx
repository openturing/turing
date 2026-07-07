"use client"
import { useCreateIntegrationInstance, useUpdateIntegrationInstance } from "@/api/queries/integration-instance.queries"
import { useTokenInstances } from "@/api/queries/token-instance.queries"
import { ROUTES } from "@/app/routes.const"
import { BentoFormSection, BentoSaveBar, type BentoIdentity, type BentoShellFormState } from "@/components/bento"
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import type { TurIntegrationInstance } from "@/models/integration/integration-instance.model.ts"
import { IconPlug } from "@tabler/icons-react"
import { toast } from "@viglet/viglet-design-system"
import { useEffect } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"

const urlBase = ROUTES.BENTO_INTEGRATION_INSTANCE
const DEFAULT_ENDPOINT = "http://localhost:30130/"

interface Props {
  value: TurIntegrationInstance
  isNew: boolean
  readOnly?: boolean
  staged?: BentoIdentity
  onStateChange?: (state: BentoShellFormState) => void
}

/** DOM id used by hero-anchored submit buttons via the `form="..."` attribute. */
export const BENTO_INTEGRATION_FORM_ID = "bento-integration-instance-form"

export const BentoIntegrationInstanceForm: React.FC<Props> = ({ value, isNew, readOnly = false, staged, onStateChange }) => {
  const { t } = useTranslation()
  const { data: tokens = [] } = useTokenInstances()
  const form = useForm<TurIntegrationInstance>({
    defaultValues: isNew ? { ...value, endpoint: value.endpoint || DEFAULT_ENDPOINT } : value,
  })
  const navigate = useNavigate()

  const createMutation = useCreateIntegrationInstance()
  const updateMutation = useUpdateIntegrationInstance()

  const isDirty = form.formState.isDirty
  const isSubmitting = form.formState.isSubmitting
  useEffect(() => {
    onStateChange?.({ isDirty, isSubmitting })
  }, [isDirty, isSubmitting, onStateChange])

  useEffect(() => {
    form.reset(value, { keepDirtyValues: true })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value])

  async function onSubmit(integrationInstance: TurIntegrationInstance) {
    if (!staged?.title?.trim()) {
      toast.error(t("forms.integration.nameRequired"))
      return
    }
    const payload: TurIntegrationInstance = { ...integrationInstance, ...staged }
    try {
      if (isNew) {
        const result = await createMutation.mutateAsync(payload)
        if (result) {
          toast.success(t("forms.common.saved", { name: payload.title, feature: t("integration.title") }))
          navigate(urlBase)
        } else {
          toast.error(t("forms.common.notSaved", { name: payload.title, feature: t("integration.title") }))
        }
      } else {
        const result = await updateMutation.mutateAsync(payload)
        if (result) {
          toast.success(t("forms.common.updated", { name: payload.title, feature: t("integration.title") }))
        } else {
          toast.error(t("forms.common.notUpdated", { name: payload.title, feature: t("integration.title") }))
        }
      }
    } catch (error) {
      console.error("Form submission error", error)
      toast.error(t("forms.common.formSubmitFailed"))
    }
  }

  const stagedTitle = staged?.title?.trim() ?? ""
  const titleMissing = !stagedTitle
  // Save bar is present on any editable detail page so it stays suspended on
  // scroll; Save is disabled until there is something to persist.
  const showSaveBar = !readOnly

  return (
    <Form {...form}>
      <form id={BENTO_INTEGRATION_FORM_ID} onSubmit={form.handleSubmit(onSubmit)} className="flex flex-col gap-5" autoComplete="off">
        {showSaveBar && (
          <>
            <div aria-hidden className="bento-save-bar-spacer" />
            <div className="bento-fade-in fixed inset-x-0 top-20 z-20 mx-auto w-full max-w-7xl px-4 md:px-8">
              <BentoSaveBar
                title={stagedTitle || (isNew ? t("integration.newInstance") : t("integration.title"))}
                disabled={titleMissing || (!isNew && !form.formState.isDirty)}
                titleMissing={titleMissing}
                dirty={isNew || form.formState.isDirty}
                loading={isSubmitting}
                onCancel={() => navigate(urlBase)}
              />
            </div>
          </>
        )}

        {/* Dumont DEP Connection */}
        <BentoFormSection
          icon={IconPlug}
          tone="violet"
          title={t("forms.integration.connectionDetails")}
          description={t("forms.integration.dumontDepDesc")}
        >
          <FormField
            control={form.control}
            name="endpoint"
            rules={{ required: t("forms.integration.endpointRequired") }}
            render={({ field }) => (
              <FormItem className="w-full">
                <FormLabel>{t("forms.integration.dumontEndpointLabel")}</FormLabel>
                <p className="text-sm text-muted-foreground">{t("forms.integration.dumontEndpointDesc")}</p>
                <FormControl>
                  <Input placeholder={DEFAULT_ENDPOINT} type="url" className="w-full" readOnly={readOnly} {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="apiToken"
            render={({ field }) => (
              <FormItem className="w-full">
                <FormLabel>{t("forms.integration.apiTokenLabel")}</FormLabel>
                <p className="text-sm text-muted-foreground">{t("forms.integration.apiTokenDesc")}</p>
                <FormControl>
                  <Select
                    value={field.value?.id ?? ""}
                    disabled={readOnly}
                    onValueChange={(val) => {
                      if (val === "__none__") {
                        field.onChange(null)
                      } else {
                        const selected = tokens.find((tk) => tk.id === val)
                        field.onChange(selected ?? null)
                      }
                    }}
                  >
                    <SelectTrigger className="w-full">
                      <SelectValue placeholder={t("forms.integration.apiTokenPlaceholder")} />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="__none__">{t("forms.integration.apiTokenNone")}</SelectItem>
                      {tokens.map((tk) => (
                        <SelectItem key={tk.id} value={tk.id}>{tk.title}</SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
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
