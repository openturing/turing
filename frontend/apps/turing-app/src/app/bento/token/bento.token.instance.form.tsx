"use client"
import { useCreateTokenInstance, useUpdateTokenInstance } from "@/api/queries/token-instance.queries"
import { ROUTES } from "@/app/routes.const"
import { BentoFormSection, BentoSaveBar, type BentoIdentity, type BentoShellFormState } from "@/components/bento"
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form"
import { GradientButton } from "@/components/ui/gradient-button"
import { Input } from "@/components/ui/input"
import type { TurTokenInstance } from "@/models/token/token-instance.model.ts"
import { IconKey } from "@tabler/icons-react"
import { toast } from "@viglet/viglet-design-system"
import { useEffect } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"

const urlBase = ROUTES.BENTO_TOKEN_INSTANCE

interface Props {
  value: TurTokenInstance
  isNew: boolean
  staged?: BentoIdentity
  onStateChange?: (state: BentoShellFormState) => void
}

/** DOM id used by hero-anchored submit buttons via the `form="..."` attribute. */
export const BENTO_TOKEN_FORM_ID = "bento-token-instance-form"

export const BentoTokenInstanceForm: React.FC<Props> = ({ value, isNew, staged, onStateChange }) => {
  const { t } = useTranslation()
  const form = useForm<TurTokenInstance>({ defaultValues: value })
  const navigate = useNavigate()

  const createMutation = useCreateTokenInstance()
  const updateMutation = useUpdateTokenInstance()

  const isDirty = form.formState.isDirty
  const isSubmitting = form.formState.isSubmitting
  useEffect(() => {
    onStateChange?.({ isDirty, isSubmitting })
  }, [isDirty, isSubmitting, onStateChange])

  useEffect(() => {
    form.reset(value, { keepDirtyValues: true })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value])

  async function onSubmit(token: TurTokenInstance) {
    if (!staged?.title?.trim()) {
      toast.error(t("forms.token.titleRequired"))
      return
    }
    const payload: TurTokenInstance = {
      ...token,
      title: staged.title,
      description: staged.description,
    }
    try {
      if (isNew) {
        const result = await createMutation.mutateAsync(payload)
        if (result) {
          toast.success(t("forms.common.saved", { name: payload.title, feature: t("apiToken.title") }))
          navigate(urlBase)
        } else {
          toast.error(t("forms.common.notSaved", { name: payload.title, feature: t("apiToken.title") }))
        }
      } else {
        const result = await updateMutation.mutateAsync(payload)
        if (result) {
          toast.success(t("forms.common.updated", { name: payload.title, feature: t("apiToken.title") }))
        } else {
          toast.error(t("forms.common.notUpdated", { name: payload.title, feature: t("apiToken.title") }))
        }
      }
    } catch (error) {
      console.error("Form submission error", error)
      toast.error(t("forms.common.formSubmitFailed"))
    }
  }

  async function handleCopy() {
    try {
      await navigator.clipboard.writeText(value.token)
      toast.success(t("forms.common.copied"))
    } catch (err) {
      toast.error(t("forms.common.copyFailed"))
      console.error("Failed to copy text: ", err)
    }
  }

  const stagedTitle = staged?.title?.trim() ?? ""
  const titleMissing = !stagedTitle
  // Save bar is present on any editable detail page so it stays suspended on
  // scroll; Save is disabled until there is something to persist.
  const showSaveBar = true

  return (
    <Form {...form}>
      <form id={BENTO_TOKEN_FORM_ID} onSubmit={form.handleSubmit(onSubmit)} className="flex flex-col gap-5" autoComplete="off">
        {showSaveBar && (
          <>
            <div aria-hidden className="bento-save-bar-spacer" />
            <div className="bento-fade-in fixed inset-x-0 top-20 z-20 mx-auto w-full max-w-7xl px-4 md:px-8">
              <BentoSaveBar
                title={stagedTitle || (isNew ? t("apiToken.newInstance") : t("apiToken.title"))}
                disabled={titleMissing || (!isNew && !form.formState.isDirty)}
                titleMissing={titleMissing}
                dirty={isNew || form.formState.isDirty}
                loading={isSubmitting}
                onCancel={() => navigate(urlBase)}
              />
            </div>
          </>
        )}

        {/* Token secret — read-only, only for an existing token. */}
        {!isNew && (
          <BentoFormSection
            icon={IconKey}
            tone="violet"
            title={t("forms.token.tokenLabel")}
            description={t("forms.token.tokenDesc")}
          >
            <FormField
              control={form.control}
              name="token"
              render={({ field }) => (
                <FormItem className="w-full">
                  <FormLabel>{t("forms.common.apiKey")}</FormLabel>
                  <p className="text-sm text-muted-foreground">{t("forms.token.tokenHint")}</p>
                  <FormControl>
                    <div className="flex items-center gap-2">
                      <Input placeholder={t("forms.common.apiKey")} type="text" readOnly className="w-full font-mono text-sm" {...field} />
                      <GradientButton type="button" onClick={handleCopy}>{t("forms.common.copy")}</GradientButton>
                    </div>
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </BentoFormSection>
        )}
      </form>
    </Form>
  )
}
