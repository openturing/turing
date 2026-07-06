"use client"
import { useCreateChatWebhook, useUpdateChatWebhook } from "@/api/queries/webhook.queries"
import { ROUTES } from "@/app/routes.const"
import { BentoFormSection, BentoSaveBar, type BentoIdentity, type BentoShellFormState } from "@/components/bento"
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Textarea } from "@/components/ui/textarea"
import type { TurChatWebhook, TurChatWebhookMethod } from "@/models/genai/webhook.model"
import { IconBraces, IconKey, IconRouteAltLeft, IconWebhook } from "@tabler/icons-react"
import { toast } from "@viglet/viglet-design-system"
import { useEffect } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"

const METHODS: TurChatWebhookMethod[] = ["POST", "PUT", "PATCH", "GET", "DELETE"]
const urlBase = ROUTES.BENTO_CHAT_WEBHOOK_INSTANCE

interface Props {
  value: TurChatWebhook
  isNew: boolean
  /** Identity — `title` aliases `name`; `enabled` is 0/1. */
  staged?: BentoIdentity
  onStateChange?: (state: BentoShellFormState) => void
}

/** DOM id used by hero-anchored submit buttons via the `form="..."` attribute. */
export const BENTO_WEBHOOK_FORM_ID = "bento-webhook-form"

export const BentoWebhookForm: React.FC<Props> = ({ value, isNew, staged, onStateChange }) => {
  const { t } = useTranslation()
  const form = useForm<TurChatWebhook>({ defaultValues: value })
  const navigate = useNavigate()

  const createMutation = useCreateChatWebhook()
  const updateMutation = useUpdateChatWebhook()

  const isDirty = form.formState.isDirty
  const isSubmitting = form.formState.isSubmitting
  useEffect(() => {
    onStateChange?.({ isDirty, isSubmitting })
  }, [isDirty, isSubmitting, onStateChange])

  useEffect(() => {
    form.reset(value, { keepDirtyValues: true })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value])

  async function onSubmit(data: TurChatWebhook) {
    if (!staged?.title?.trim()) {
      toast.error(t("webhook.nameRequired"))
      return
    }
    const payload: TurChatWebhook = {
      ...data,
      name: staged.title,
      description: staged.description,
      enabled: staged.enabled === 1,
      // Blank secret on edit keeps the stored credential untouched server-side.
      authHeader: data.authHeader?.trim() ? data.authHeader.trim() : undefined,
      signingSecret: data.signingSecret?.trim() ? data.signingSecret.trim() : undefined,
    }
    try {
      const saved = isNew ? await createMutation.mutateAsync(payload) : await updateMutation.mutateAsync(payload)
      if (saved) {
        toast.success(isNew ? t("webhook.created", { name: saved.name }) : t("webhook.updated", { name: saved.name }))
        if (isNew) navigate(urlBase)
      } else {
        toast.error(t("webhook.saveFailed"))
      }
    } catch (err) {
      console.error("Webhook save failed", err)
      toast.error(t("webhook.saveFailed"))
    }
  }

  const stagedTitle = staged?.title?.trim() ?? ""
  const titleMissing = !stagedTitle
  // Save bar is present on any editable detail page so it stays suspended on
  // scroll; Save is disabled until there is something to persist.
  const showSaveBar = true

  return (
    <Form {...form}>
      <form id={BENTO_WEBHOOK_FORM_ID} onSubmit={form.handleSubmit(onSubmit)} className="flex flex-col gap-5" autoComplete="off">
        {showSaveBar && (
          <>
            <div aria-hidden className="bento-save-bar-spacer" />
            <div className="bento-fade-in fixed inset-x-0 top-20 z-20 mx-auto w-full max-w-7xl px-4 md:px-8">
              <BentoSaveBar
                title={stagedTitle || (isNew ? t("webhook.newWebhook") : t("webhook.title"))}
                disabled={titleMissing || (!isNew && !form.formState.isDirty)}
                titleMissing={titleMissing}
                dirty={isNew || form.formState.isDirty}
                loading={isSubmitting}
                onCancel={() => navigate(urlBase)}
              />
            </div>
          </>
        )}

        {/* Endpoint */}
        <BentoFormSection
          icon={IconWebhook}
          tone="rose"
          title={t("webhook.details")}
          description={t("webhook.detailsDesc")}
        >
          <div className="flex w-full gap-3">
            <FormField
              control={form.control}
              name="httpMethod"
              render={({ field }) => (
                <FormItem className="w-32 shrink-0">
                  <FormLabel>{t("webhook.httpMethod")}</FormLabel>
                  <FormControl>
                    <Select value={field.value ?? "POST"} onValueChange={(v) => field.onChange(v as TurChatWebhookMethod)}>
                      <SelectTrigger className="w-full">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        {METHODS.map((m) => (
                          <SelectItem key={m} value={m}>{m}</SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </FormControl>
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="targetUrl"
              rules={{ required: t("webhook.targetUrlRequired") }}
              render={({ field }) => (
                <FormItem className="flex-1">
                  <FormLabel>{t("webhook.targetUrl")}</FormLabel>
                  <FormControl>
                    <Input {...field} value={field.value ?? ""} placeholder="https://hooks.zapier.com/hooks/catch/..." className="w-full" />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </div>
          <p className="text-sm text-muted-foreground">{t("webhook.targetUrlDesc")}</p>
        </BentoFormSection>

        {/* Payload */}
        <BentoFormSection
          icon={IconBraces}
          tone="amber"
          title={t("webhook.payload")}
          description={t("webhook.payloadDesc")}
        >
          <FormField
            control={form.control}
            name="payloadTemplate"
            render={({ field }) => (
              <FormItem className="w-full">
                <FormLabel>{t("webhook.payloadTemplate")}</FormLabel>
                <p className="text-sm text-muted-foreground">{t("webhook.payloadTemplateDesc")}</p>
                <FormControl>
                  <Textarea {...field} value={field.value ?? ""} rows={8} spellCheck={false} className="w-full font-mono text-xs" placeholder={'{\n  "properties": {\n    "email": "{{email}}",\n    "firstname": "{{name}}"\n  }\n}'} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="headersJson"
            render={({ field }) => (
              <FormItem className="w-full">
                <FormLabel>{t("webhook.headersJson")}</FormLabel>
                <p className="text-sm text-muted-foreground">{t("webhook.headersJsonDesc")}</p>
                <FormControl>
                  <Textarea {...field} value={field.value ?? ""} rows={3} spellCheck={false} className="w-full font-mono text-xs" placeholder={'{ "X-Api-Key": "abc123" }'} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
        </BentoFormSection>

        {/* Trigger */}
        <BentoFormSection
          icon={IconRouteAltLeft}
          tone="emerald"
          title={t("webhook.trigger")}
          description={t("webhook.triggerDesc")}
        >
          <FormField
            control={form.control}
            name="slotTrigger"
            render={({ field }) => (
              <FormItem className="w-full">
                <FormLabel>{t("webhook.slotTrigger")}</FormLabel>
                <p className="text-sm text-muted-foreground">{t("webhook.slotTriggerDesc")}</p>
                <FormControl>
                  <Input {...field} value={field.value ?? ""} placeholder="email  ·  *" className="w-full" />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="includeSlots"
            render={({ field }) => (
              <FormItem className="w-full">
                <FormLabel>{t("webhook.includeSlots")}</FormLabel>
                <p className="text-sm text-muted-foreground">{t("webhook.includeSlotsDesc")}</p>
                <FormControl>
                  <Input {...field} value={field.value ?? ""} placeholder="name, email, phone" className="w-full" />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
        </BentoFormSection>

        {/* Security */}
        <BentoFormSection
          icon={IconKey}
          tone="slate"
          title={t("webhook.security")}
          description={t("webhook.securityDesc")}
        >
          <FormField
            control={form.control}
            name="authHeader"
            render={({ field }) => (
              <FormItem className="w-full">
                <FormLabel>{t("webhook.authHeader")}</FormLabel>
                <p className="text-sm text-muted-foreground">
                  {value.hasAuthHeader && !isNew ? t("webhook.authHeaderConfigured") : t("webhook.authHeaderDesc")}
                </p>
                <FormControl>
                  <Input {...field} type="password" autoComplete="new-password" value={field.value ?? ""} placeholder="Bearer xyz…" className="w-full" />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="signingSecret"
            render={({ field }) => (
              <FormItem className="w-full">
                <FormLabel>{t("webhook.signingSecret")}</FormLabel>
                <p className="text-sm text-muted-foreground">
                  {value.hasSigningSecret && !isNew ? t("webhook.signingSecretConfigured") : t("webhook.signingSecretDesc")}
                </p>
                <FormControl>
                  <Input {...field} type="password" autoComplete="new-password" value={field.value ?? ""} placeholder="whsec_…" className="w-full" />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="signatureHeader"
            render={({ field }) => (
              <FormItem className="w-full">
                <FormLabel>{t("webhook.signatureHeader")}</FormLabel>
                <p className="text-sm text-muted-foreground">{t("webhook.signatureHeaderDesc")}</p>
                <FormControl>
                  <Input {...field} value={field.value ?? ""} placeholder="X-Turing-Signature" className="w-full" />
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
