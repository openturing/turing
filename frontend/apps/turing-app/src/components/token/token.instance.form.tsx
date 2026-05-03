"use client"
import { ROUTES } from "@/app/routes.const"
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form"
import {
  Input
} from "@/components/ui/input"
import { SmartDescription } from "@/components/ui/smart-description"
import {
  useCreateTokenInstance,
  useUpdateTokenInstance,
} from "@/api/queries/token-instance.queries"
import type { TurTokenInstance } from "@/models/token/token-instance.model.ts"
import { IconCode, IconDeviceFloppy, IconKey, IconNotes, IconX } from "@tabler/icons-react"
import { DialogDelete } from "@/components/dialog.delete"
import { useEffect } from "react"
import { useTranslation } from "react-i18next"
import {
  useForm
} from "react-hook-form"
import { useNavigate } from "react-router-dom"
import { toast } from "@viglet/viglet-design-system"
import { StickyPageHeader } from "../sticky-page-header"
import { GradientButton } from "../ui/gradient-button"
import { SectionCard } from "../ui/section-card"
interface Props {
  value: TurTokenInstance;
  isNew: boolean;
  onDelete?: () => void;
  open?: boolean;
  setOpen?: React.Dispatch<React.SetStateAction<boolean>>;
}

export const TokenInstanceForm: React.FC<Props> = ({ value, isNew, onDelete, open, setOpen }) => {
  const { t } = useTranslation();
  const form = useForm<TurTokenInstance>({
    defaultValues: value
  });
  const navigate = useNavigate()
  const createMutation = useCreateTokenInstance();
  const updateMutation = useUpdateTokenInstance();

  useEffect(() => {
    form.reset(value);
  }, [value]);

  async function onSubmit(tokenInstance: TurTokenInstance) {
    try {
      if (isNew) {
        const result = await createMutation.mutateAsync(tokenInstance);
        if (result) {
          toast.success(t("forms.common.saved", { name: tokenInstance.title, feature: t("apiToken.title") }));
          navigate(ROUTES.ADMIN_TOKENS);
        } else {
          toast.error(t("forms.common.notSaved", { name: tokenInstance.title, feature: t("apiToken.title") }));
        }
      }
      else {
        const result = await updateMutation.mutateAsync(tokenInstance);
        if (result) {
          toast.success(t("forms.common.updated", { name: tokenInstance.title, feature: t("apiToken.title") }));
        } else {
          toast.error(t("forms.common.notUpdated", { name: tokenInstance.title, feature: t("apiToken.title") }));
        }
      }
    } catch (error) {
      console.error("Form submission error", error);
      toast.error(t("forms.common.formSubmitFailed"));
    }
  }

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(value.token);
      toast.success(t("forms.common.copied"));
    } catch (err) {
      toast.error(t("forms.common.copyFailed"));
      console.error("Failed to copy text: ", err);
    }
  };

  return (
    <Form {...form}>
      <form
        onSubmit={form.handleSubmit(onSubmit)}
        className="space-y-4 px-4 lg:px-6 pb-8"
      >
        <StickyPageHeader>
          <StickyPageHeader.Title
            icon={IconCode}
            feature="API Token"
            description={isNew ? "Create a new API token." : "Manage API token details and credentials."}
          />
          <StickyPageHeader.Actions>
            {onDelete && open !== undefined && setOpen && <DialogDelete feature="API Token" name={value.title ?? "API Token"} onDelete={onDelete} open={open} setOpen={setOpen} />}
            <GradientButton type="submit" size="sm">
              <IconDeviceFloppy className="size-4" />
              {t("forms.formActions.saveChanges")}
            </GradientButton>
            <GradientButton type="button" variant="outline" size="sm" onClick={() => navigate(ROUTES.ADMIN_TOKENS)}>
              <IconX className="size-4" />
              {t("forms.formActions.cancel")}
            </GradientButton>
          </StickyPageHeader.Actions>
        </StickyPageHeader>

        {/* General */}
        <SectionCard variant="blue">
          <SectionCard.Header icon={IconNotes} title={t("forms.token.general")} description={t("forms.token.generalDesc")} />
          <SectionCard.Content>
            <FormField
              control={form.control}
              name="title"
              rules={{ required: t("forms.token.titleRequired") }}
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("forms.common.title")}</FormLabel>
                  <FormDescription>{t("forms.token.titleDesc")}</FormDescription>
                  <FormControl>
                    <Input
                      {...field}
                      placeholder={t("forms.token.titlePlaceholder")}
                      type="text"
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="description"
              render={({ field }) => (
                <FormItem>
                  <SmartDescription
                    value={field.value}
                    onChange={field.onChange}
                    placeholder={t("forms.token.descriptionPlaceholder")}
                    title={form.watch("title")}
                    entityType={t("apiToken.title")}
                    enableMetaPrompt
                  >
                    <SmartDescription.Label>{t("forms.common.description")}</SmartDescription.Label>
                    <SmartDescription.Description>{t("forms.token.descriptionDesc")}</SmartDescription.Description>
                  </SmartDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
          </SectionCard.Content>
        </SectionCard>

        {/* Token */}
        {!isNew && <SectionCard variant="violet">
          <SectionCard.Header icon={IconKey} title={t("forms.token.tokenLabel")} description={t("forms.token.tokenDesc")} />
          <SectionCard.Content>
            <FormField
              control={form.control}
              name="token"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("forms.common.apiKey")}</FormLabel>
                  <FormDescription>{t("forms.token.tokenHint")}</FormDescription>
                  <FormControl>
                    <div className="flex items-center space-x-2">
                      <Input
                        placeholder={t("forms.common.apiKey")}
                        type="text"
                        readOnly
                        className="font-mono text-sm"
                        {...field} />
                      <GradientButton type="button" onClick={handleCopy}>{t("forms.common.copy")}</GradientButton>
                    </div>
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </SectionCard.Content>
        </SectionCard>}

        {/* Footer */}
      </form>
    </Form>
  )
}
