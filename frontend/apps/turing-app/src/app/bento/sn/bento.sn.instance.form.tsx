"use client"
import { useCreateSnSite, useUpdateSnSite } from "@/api/queries/sn-site.queries"
import { ROUTES } from "@/app/routes.const"
import { BentoFormSection, BentoSaveBar, type BentoIdentity, type BentoShellFormState } from "@/components/bento"
import { Form, FormControl, FormField, FormItem, FormMessage } from "@/components/ui/form"
import { FormItemTwoColumns } from "@/components/ui/form-item-two-columns"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import type { TurPageSite } from "@/models/page/page-site.model"
import type { TurSEInstance } from "@/models/se/se-instance.model"
import type { TurSNSite } from "@/models/sn/sn-site.model.ts"
import { TurPageService } from "@/services/page/page.service"
import { TurSEInstanceService } from "@/services/se/se.service"
import { TurFeaturesService } from "@/services/system/features.service"
import { IconCloudOff, IconCpu2, IconSearch } from "@tabler/icons-react"
import { toast } from "@viglet/viglet-design-system"
import { useEffect, useState } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"

const turSEInstanceService = new TurSEInstanceService()
const turPageService = new TurPageService()
const turFeaturesService = new TurFeaturesService()
const urlBase = ROUTES.BENTO_SN_INSTANCE

interface Props {
  value: TurSNSite
  isNew: boolean
  /** Identity fields owned by the page hero (title↔name/description/icon). */
  staged?: BentoIdentity
  /** Reports dirty/submitting so the Shell can render the hero Save/Cancel. */
  onStateChange?: (state: BentoShellFormState) => void
}

/** DOM id used by hero-anchored submit buttons via the `form="..."` attribute. */
export const BENTO_SN_FORM_ID = "bento-sn-instance-form"

/**
 * Bento SN instance form body — T557. Holds the **config sections** (search
 * engine binding + optional search template). Identity (name/description/icon)
 * lives in the hero: `name` is aliased to the shell's `title`, so on submit the
 * staged title is written back to `name`. Name pattern/uniqueness is enforced
 * server-side (create/update surfaces a 409 as a toast) — the console's
 * client-side name field is intentionally lifted into the hero.
 */
export const BentoSNInstanceForm: React.FC<Props> = ({ value, isNew, staged, onStateChange }) => {
  const { t } = useTranslation()
  const form = useForm<TurSNSite>({ defaultValues: value })
  const [seInstances, setSeInstances] = useState<TurSEInstance[]>([])
  const [pages, setPages] = useState<TurPageSite[]>([])
  const [storageEnabled, setStorageEnabled] = useState(true)
  const selectedSeId = form.watch("turSEInstance.id")
  const navigate = useNavigate()

  const createMutation = useCreateSnSite()
  const updateMutation = useUpdateSnSite()

  useEffect(() => {
    turSEInstanceService.query().then(setSeInstances)
    turFeaturesService.getFeatures()
      .then((f) => {
        setStorageEnabled(f.storageEnabled)
        if (f.storageEnabled) {
          turPageService.query().then(setPages).catch(() => setPages([]))
        }
      })
      .catch(() => setStorageEnabled(false))
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

  async function onSubmit(snSite: TurSNSite) {
    const name = staged?.title?.trim()
    if (!name) {
      toast.error(t("forms.snSiteForm.nameRequired"))
      return
    }
    // Merge the hero identity (title↔name) into the config payload.
    const payload: TurSNSite = {
      ...snSite,
      name,
      description: staged?.description ?? snSite.description,
      icon: staged?.icon ?? snSite.icon,
    }
    try {
      if (isNew) {
        const result = await createMutation.mutateAsync(payload)
        if (result) {
          toast.success(t("sn.siteSaved", { name }))
          navigate(urlBase)
        } else {
          toast.error(t("sn.siteNotSaved", { name }))
        }
      } else {
        const result = await updateMutation.mutateAsync(payload)
        if (result) {
          toast.success(t("sn.siteUpdated", { name }))
        } else {
          toast.error(t("sn.siteNotUpdated", { name }))
        }
      }
    } catch (error: unknown) {
      console.error("Form submission error", error)
      if (error && typeof error === "object" && "response" in error) {
        const axiosError = error as { response?: { status?: number } }
        if (axiosError.response?.status === 409) {
          toast.error(t("forms.snSiteForm.nameDuplicate"))
          return
        }
      }
      toast.error(t("sn.formSubmitFailed"))
    }
  }

  const stagedTitle = staged?.title?.trim() ?? ""
  const titleMissing = !stagedTitle
  const seLabel = seInstances.find((s) => s.id === selectedSeId)?.title
  // Save bar is present on any editable detail page so it stays suspended on
  // scroll; Save is disabled until there is something to persist.
  const showSaveBar = true

  return (
    <Form {...form}>
      <form id={BENTO_SN_FORM_ID} onSubmit={form.handleSubmit(onSubmit)} className="flex flex-col gap-5" autoComplete="off">
        {showSaveBar && (
          <>
            <div aria-hidden className="bento-save-bar-spacer" />
            <div className="bento-fade-in fixed inset-x-0 top-20 z-20 mx-auto w-full max-w-7xl px-4 md:px-8">
              <BentoSaveBar
                title={stagedTitle || (isNew ? t("sn.newSite") : t("sn.title"))}
                disabled={titleMissing || (!isNew && !form.formState.isDirty)}
                titleMissing={titleMissing}
                dirty={isNew || form.formState.isDirty}
                loading={isSubmitting}
                badges={seLabel && <BentoSaveBar.Badge>{seLabel}</BentoSaveBar.Badge>}
                onCancel={() => navigate(urlBase)}
              />
            </div>
          </>
        )}

        {/* Search Engine binding. */}
        <BentoFormSection
          icon={IconSearch}
          tone="emerald"
          title={t("forms.snSiteForm.searchEngine")}
          description={t("forms.snSiteForm.searchEngineDesc")}
        >
          <FormField
            control={form.control}
            name="turSEInstance.id"
            rules={{ required: t("forms.snSiteForm.seInstanceRequired") }}
            render={({ field }) => (
              <FormItemTwoColumns>
                <FormItemTwoColumns.Left>
                  <FormItemTwoColumns.Label>{t("forms.snSiteForm.seInstanceLabel")}</FormItemTwoColumns.Label>
                  <FormItemTwoColumns.Description>{t("forms.snSiteForm.seInstanceDesc")}</FormItemTwoColumns.Description>
                </FormItemTwoColumns.Left>
                <FormItemTwoColumns.Right>
                  <FormControl>
                    <Select onValueChange={field.onChange} value={field.value}>
                      <SelectTrigger className="w-full">
                        <SelectValue placeholder={t("globalSettings.choose")} />
                      </SelectTrigger>
                      <SelectContent>
                        {seInstances.map((seInstance) => (
                          <SelectItem key={seInstance.id} value={seInstance.id}>{seInstance.title}</SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </FormControl>
                </FormItemTwoColumns.Right>
                <FormMessage />
              </FormItemTwoColumns>
            )}
          />
        </BentoFormSection>

        {/* Search template (storage-gated). */}
        <BentoFormSection
          icon={IconCpu2}
          tone="indigo"
          title={t("forms.snSiteForm.searchTemplateLabel")}
          description={t("forms.snSiteForm.searchTemplateDesc")}
        >
          {storageEnabled ? (
            pages.length > 0 ? (
              <FormField
                control={form.control}
                name="searchTemplate"
                render={({ field }) => (
                  <FormItem className="w-full">
                    <FormControl>
                      <Select onValueChange={(v) => field.onChange(v === "__none__" ? null : v)} value={field.value ?? "__none__"}>
                        <SelectTrigger className="w-full">
                          <SelectValue placeholder={t("forms.snSiteForm.searchTemplateNone")} />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="__none__">{t("forms.snSiteForm.searchTemplateNone")}</SelectItem>
                          {pages.map((page) => (
                            <SelectItem key={page.name} value={page.name}>{page.name}</SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            ) : (
              <p className="text-sm text-muted-foreground italic">{t("forms.snSiteForm.searchTemplateNoPages")}</p>
            )
          ) : (
            <div className="flex items-start gap-3 rounded-lg border border-amber-500/30 bg-amber-500/5 p-4">
              <IconCloudOff className="size-5 text-amber-500 shrink-0 mt-0.5" />
              <p className="text-sm text-muted-foreground">{t("forms.snSiteForm.searchTemplateStorageDisabled")}</p>
            </div>
          )}
        </BentoFormSection>
      </form>
    </Form>
  )
}
