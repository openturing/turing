"use client";
import { ROUTES } from "@/app/routes.const";
import {
  useCreateChatWebhook,
  useDeleteChatWebhook,
  useUpdateChatWebhook,
} from "@/api/queries/webhook.queries";
import { StickyPageHeader } from "@/components/sticky-page-header";
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { GradientButton } from "@/components/ui/gradient-button";
import { GradientSwitch } from "@/components/ui/gradient-switch";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { SectionCard } from "@/components/ui/section-card";
import { Textarea } from "@/components/ui/textarea";
import type { TurChatWebhook, TurChatWebhookMethod } from "@/models/genai/webhook.model";
import {
  IconBraces,
  IconDeviceFloppy,
  IconKey,
  IconRouteAltLeft,
  IconTrash,
  IconWebhook,
  IconX,
} from "@tabler/icons-react";
import { toast } from "@viglet/viglet-design-system";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";

import { DialogDelete } from "@/components/dialog.delete";

const METHODS: TurChatWebhookMethod[] = ["POST", "PUT", "PATCH", "GET", "DELETE"];

interface Props {
  readonly value: TurChatWebhook;
  readonly isNew: boolean;
}

/**
 * T62 — edit/create form for a {@code TurChatWebhook}. The auth header is
 * write-only: existing webhooks show a "configured" hint and an empty field
 * preserves the stored credential on save.
 *
 * @since 2026.3.1
 */
export const WebhookForm: React.FC<Props> = ({ value, isNew }) => {
  const { t } = useTranslation();
  const form = useForm<TurChatWebhook>({ defaultValues: value });
  const navigate = useNavigate();
  const [deleteOpen, setDeleteOpen] = useState(false);

  const createMutation = useCreateChatWebhook();
  const updateMutation = useUpdateChatWebhook();
  const deleteMutation = useDeleteChatWebhook();

  useEffect(() => {
    form.reset(value);
  }, [value, form]);

  async function onSubmit(data: TurChatWebhook) {
    const payload: TurChatWebhook = {
      ...data,
      // Blank auth header on edit keeps the stored secret untouched server-side.
      authHeader: data.authHeader?.trim() ? data.authHeader.trim() : undefined,
    };
    try {
      const saved = isNew
        ? await createMutation.mutateAsync(payload)
        : await updateMutation.mutateAsync(payload);
      if (saved) {
        toast.success(
          isNew
            ? t("webhook.created", { name: saved.name })
            : t("webhook.updated", { name: saved.name }),
        );
        navigate(ROUTES.CHAT_WEBHOOK_INSTANCE);
      } else {
        toast.error(t("webhook.saveFailed"));
      }
    } catch (err) {
      console.error("Webhook save failed", err);
      toast.error(t("webhook.saveFailed"));
    }
  }

  async function onDelete() {
    if (!value.id) return;
    try {
      const ok = await deleteMutation.mutateAsync(value);
      if (ok) {
        toast.success(t("webhook.deleted", { name: value.name }));
        navigate(ROUTES.CHAT_WEBHOOK_INSTANCE);
      } else {
        toast.error(t("webhook.deleteFailed"));
      }
    } catch (err) {
      console.error("Webhook delete failed", err);
      toast.error(t("webhook.deleteFailed"));
    }
  }

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 px-4 lg:px-6 pb-8">
        <StickyPageHeader>
          <StickyPageHeader.Title
            icon={IconWebhook}
            feature={isNew ? t("webhook.newWebhook") : value.name}
            description={isNew ? t("webhook.createDescription") : t("webhook.editDescription")}
          />
          <StickyPageHeader.Actions>
            <GradientButton type="submit" size="sm">
              <IconDeviceFloppy className="size-4" />
              {t("forms.formActions.saveChanges")}
            </GradientButton>
            <GradientButton
              type="button"
              variant="outline"
              size="sm"
              onClick={() => navigate(ROUTES.CHAT_WEBHOOK_INSTANCE)}
            >
              <IconX className="size-4" />
              {t("forms.formActions.cancel")}
            </GradientButton>
            {!isNew && (
              <GradientButton
                type="button"
                variant="destructive"
                size="sm"
                onClick={() => setDeleteOpen(true)}
              >
                <IconTrash className="size-4" />
                {t("forms.formActions.delete")}
              </GradientButton>
            )}
          </StickyPageHeader.Actions>
        </StickyPageHeader>

        <SectionCard variant="blue">
          <SectionCard.Header
            icon={IconWebhook}
            title={t("webhook.details")}
            description={t("webhook.detailsDesc")}
          />
          <SectionCard.Content>
            <FormField
              control={form.control}
              name="name"
              rules={{ required: t("webhook.nameRequired") }}
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("webhook.name")}</FormLabel>
                  <FormDescription>{t("webhook.nameDesc")}</FormDescription>
                  <FormControl>
                    <Input {...field} placeholder="push_to_salesforce" />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="description"
              render={({ field }) => (
                <FormItem className="mt-4">
                  <FormLabel>{t("webhook.description")}</FormLabel>
                  <FormControl>
                    <Input
                      {...field}
                      value={field.value ?? ""}
                      placeholder={t("webhook.descriptionPlaceholder")}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <div className="mt-4 flex gap-3">
              <FormField
                control={form.control}
                name="httpMethod"
                render={({ field }) => (
                  <FormItem className="w-32 shrink-0">
                    <FormLabel>{t("webhook.httpMethod")}</FormLabel>
                    <FormControl>
                      <Select
                        value={field.value ?? "POST"}
                        onValueChange={(v) => field.onChange(v as TurChatWebhookMethod)}
                      >
                        <SelectTrigger>
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          {METHODS.map((m) => (
                            <SelectItem key={m} value={m}>
                              {m}
                            </SelectItem>
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
                      <Input
                        {...field}
                        value={field.value ?? ""}
                        placeholder="https://hooks.zapier.com/hooks/catch/..."
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>
            <FormDescription className="mt-1">{t("webhook.targetUrlDesc")}</FormDescription>
          </SectionCard.Content>
        </SectionCard>

        <SectionCard variant="amber">
          <SectionCard.Header
            icon={IconBraces}
            title={t("webhook.payload")}
            description={t("webhook.payloadDesc")}
          />
          <SectionCard.Content>
            <FormField
              control={form.control}
              name="payloadTemplate"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("webhook.payloadTemplate")}</FormLabel>
                  <FormDescription>{t("webhook.payloadTemplateDesc")}</FormDescription>
                  <FormControl>
                    <Textarea
                      {...field}
                      value={field.value ?? ""}
                      rows={8}
                      spellCheck={false}
                      className="font-mono text-xs"
                      placeholder={'{\n  "properties": {\n    "email": "{{email}}",\n    "firstname": "{{name}}"\n  }\n}'}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="headersJson"
              render={({ field }) => (
                <FormItem className="mt-4">
                  <FormLabel>{t("webhook.headersJson")}</FormLabel>
                  <FormDescription>{t("webhook.headersJsonDesc")}</FormDescription>
                  <FormControl>
                    <Textarea
                      {...field}
                      value={field.value ?? ""}
                      rows={3}
                      spellCheck={false}
                      className="font-mono text-xs"
                      placeholder={'{ "X-Api-Key": "abc123" }'}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </SectionCard.Content>
        </SectionCard>

        <SectionCard variant="emerald">
          <SectionCard.Header
            icon={IconRouteAltLeft}
            title={t("webhook.trigger")}
            description={t("webhook.triggerDesc")}
          />
          <SectionCard.Content>
            <FormField
              control={form.control}
              name="slotTrigger"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("webhook.slotTrigger")}</FormLabel>
                  <FormDescription>{t("webhook.slotTriggerDesc")}</FormDescription>
                  <FormControl>
                    <Input {...field} value={field.value ?? ""} placeholder="email  ·  *" />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="includeSlots"
              render={({ field }) => (
                <FormItem className="mt-4">
                  <FormLabel>{t("webhook.includeSlots")}</FormLabel>
                  <FormDescription>{t("webhook.includeSlotsDesc")}</FormDescription>
                  <FormControl>
                    <Input
                      {...field}
                      value={field.value ?? ""}
                      placeholder="name, email, phone"
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </SectionCard.Content>
        </SectionCard>

        <SectionCard variant="slate">
          <SectionCard.Header
            icon={IconKey}
            title={t("webhook.security")}
            description={t("webhook.securityDesc")}
          />
          <SectionCard.Content>
            <FormField
              control={form.control}
              name="authHeader"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("webhook.authHeader")}</FormLabel>
                  <FormDescription>
                    {value.hasAuthHeader && !isNew
                      ? t("webhook.authHeaderConfigured")
                      : t("webhook.authHeaderDesc")}
                  </FormDescription>
                  <FormControl>
                    <Input
                      {...field}
                      type="password"
                      autoComplete="new-password"
                      value={field.value ?? ""}
                      placeholder="Bearer xyz…"
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="enabled"
              render={({ field }) => (
                <FormItem className="mt-4 flex items-center justify-between rounded-lg border p-3">
                  <div>
                    <FormLabel className="text-sm">{t("webhook.enabled")}</FormLabel>
                    <FormDescription>{t("webhook.enabledDesc")}</FormDescription>
                  </div>
                  <FormControl>
                    <GradientSwitch
                      checked={field.value ?? true}
                      onCheckedChange={field.onChange}
                    />
                  </FormControl>
                </FormItem>
              )}
            />
          </SectionCard.Content>
        </SectionCard>

        {!isNew && (
          <DialogDelete
            open={deleteOpen}
            setOpen={setDeleteOpen}
            feature={t("webhook.title")}
            name={value.name}
            onDelete={onDelete}
          />
        )}
      </form>
    </Form>
  );
};
