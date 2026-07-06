"use client"
import { useCreateSeInstance, useUpdateSeInstance } from "@/api/queries/se-instance.queries"
import { ROUTES } from "@/app/routes.const"
import { BentoFormSection, BentoSaveBar, type BentoIdentity, type BentoShellFormState } from "@/components/bento"
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form"
import { FormItemTwoColumns } from "@/components/ui/form-item-two-columns"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import type { TurSEInstance } from "@/models/se/se-instance.model.ts"
import type { TurSEVendor } from "@/models/se/se-vendor.model.ts"
import { TurSEVendorService } from "@/services/se/se.service"
import { IconPlug, IconSettings } from "@tabler/icons-react"
import { toast } from "@viglet/viglet-design-system"
import { useEffect, useState } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"

const turSEVendorService = new TurSEVendorService()
const urlBase = ROUTES.BENTO_SE_INSTANCE

const VENDOR_DEFAULT_URLS: Record<string, string> = {
  SOLR: "http://localhost:8983/solr",
  LUCENE: "/data/turing/lucene",
  ES: "http://localhost:9200",
}

interface Props {
  value: TurSEInstance
  isNew: boolean
  /** GLOBAL pool / Solr-property catalog lock — disables the form fields. */
  readOnly?: boolean
  /** Identity fields owned by the page hero (title/description/status/icon). */
  staged?: BentoIdentity
  /** Reports dirty/submitting so the Shell can render the hero Save/Cancel. */
  onStateChange?: (state: BentoShellFormState) => void
}

/** DOM id used by hero-anchored submit buttons via the `form="..."` attribute. */
export const BENTO_SE_FORM_ID = "bento-se-instance-form"

export const BentoSEInstanceForm: React.FC<Props> = ({ value, isNew, readOnly = false, staged, onStateChange }) => {
  const { t } = useTranslation()
  const form = useForm<TurSEInstance>({ defaultValues: value })
  const [vendors, setVendors] = useState<TurSEVendor[]>([])
  const selectedVendorId = form.watch("turSEVendor.id")
  const navigate = useNavigate()

  const createMutation = useCreateSeInstance()
  const updateMutation = useUpdateSeInstance()

  useEffect(() => {
    turSEVendorService.query().then(setVendors)
  }, [])

  const isDirty = form.formState.isDirty
  const isSubmitting = form.formState.isSubmitting
  useEffect(() => {
    onStateChange?.({ isDirty, isSubmitting })
  }, [isDirty, isSubmitting, onStateChange])

  useEffect(() => {
    form.reset(value, { keepDirtyValues: true })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value])

  // Seed a sensible endpoint default when the vendor changes on a new instance.
  useEffect(() => {
    if (!isNew) return
    const defaultUrl = VENDOR_DEFAULT_URLS[selectedVendorId]
    if (defaultUrl) form.setValue("endpointUrl", defaultUrl, { shouldDirty: true })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedVendorId])

  async function onSubmit(seInstance: TurSEInstance) {
    if (!staged?.title?.trim()) {
      toast.error(t("forms.se.titleRequired"))
      return
    }
    const payload: TurSEInstance = { ...seInstance, ...staged }
    try {
      if (isNew) {
        const result = await createMutation.mutateAsync(payload)
        if (result) {
          toast.success(t("forms.common.saved", { name: payload.title, feature: t("se.title") }))
          navigate(urlBase)
        } else {
          toast.error(t("forms.common.notSaved", { name: payload.title, feature: t("se.title") }))
        }
      } else {
        const result = await updateMutation.mutateAsync(payload)
        if (result) {
          toast.success(t("forms.common.updated", { name: payload.title, feature: t("se.title") }))
        } else {
          toast.error(t("forms.common.notUpdated", { name: payload.title, feature: t("se.title") }))
        }
      }
    } catch (error) {
      console.error("Form submission error", error)
      toast.error(t("forms.common.formSubmitFailed"))
    }
  }

  const stagedTitle = staged?.title?.trim() ?? ""
  const titleMissing = !stagedTitle
  const vendorLabel = vendors.find((v) => v.id === selectedVendorId)?.title
  // Save bar is present on any editable detail page so it stays suspended on
  // scroll; Save is disabled until there is something to persist.
  const showSaveBar = !readOnly

  return (
    <Form {...form}>
      <form id={BENTO_SE_FORM_ID} onSubmit={form.handleSubmit(onSubmit)} className="flex flex-col gap-5" autoComplete="off">
        {showSaveBar && (
          <>
            <div aria-hidden className="bento-save-bar-spacer" />
            <div className="bento-fade-in fixed inset-x-0 top-20 z-20 mx-auto w-full max-w-7xl px-4 md:px-8">
              <BentoSaveBar
                title={stagedTitle || (isNew ? t("se.newSearchEngine") : t("se.title"))}
                disabled={titleMissing || (!isNew && !form.formState.isDirty)}
                titleMissing={titleMissing}
                dirty={isNew || form.formState.isDirty}
                loading={isSubmitting}
                badges={vendorLabel && <BentoSaveBar.Badge>{vendorLabel}</BentoSaveBar.Badge>}
                onCancel={() => navigate(urlBase)}
              />
            </div>
          </>
        )}

        {/* Connection Section — vendor + endpoint. Title/description/icon live in the hero. */}
        <BentoFormSection
          icon={IconSettings}
          tone="emerald"
          title={t("forms.se.connectionSettings")}
          description={t("forms.se.connectionSettingsDesc")}
        >
          <FormField
            control={form.control}
            name="turSEVendor.id"
            rules={{ required: t("forms.se.vendorRequired") }}
            render={({ field }) => (
              <FormItemTwoColumns>
                <FormItemTwoColumns.Left>
                  <FormItemTwoColumns.Label>{t("forms.common.vendor")}</FormItemTwoColumns.Label>
                  <FormItemTwoColumns.Description>{t("forms.se.vendorDesc")}</FormItemTwoColumns.Description>
                </FormItemTwoColumns.Left>
                <FormItemTwoColumns.Right>
                  <FormControl>
                    <Select onValueChange={field.onChange} value={field.value} disabled={readOnly}>
                      <SelectTrigger className="w-full">
                        <SelectValue placeholder={t("forms.common.selectVendor")} />
                      </SelectTrigger>
                      <SelectContent>
                        {vendors.map((vendor) => (
                          <SelectItem key={vendor.id} value={vendor.id}>{vendor.title}</SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </FormControl>
                </FormItemTwoColumns.Right>
                <FormMessage />
              </FormItemTwoColumns>
            )}
          />

          <FormField
            control={form.control}
            name="endpointUrl"
            rules={{ required: t("forms.se.endpointRequired") }}
            render={({ field }) => (
              <FormItem className="w-full">
                <FormLabel>
                  <IconPlug className="mr-1.5 inline size-4 text-muted-foreground" />
                  {t("forms.common.endpointUrl")}
                </FormLabel>
                <p className="text-sm text-muted-foreground">{t("forms.se.endpointDesc")}</p>
                <FormControl>
                  <Input placeholder={t("forms.se.endpointPlaceholder")} type="url" className="w-full" readOnly={readOnly} {...field} />
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
