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
import {
  useCreateIntegrationInstance,
  useUpdateIntegrationInstance,
} from "@/api/queries/integration-instance.queries"
import { useTokenInstances } from "@/api/queries/token-instance.queries"
import type { TurIntegrationInstance } from "@/models/integration/integration-instance.model.ts"
import { useEffect } from "react"
import { useTranslation } from "react-i18next"
import {
  useForm
} from "react-hook-form"
import { useNavigate } from "react-router-dom"
import { toast } from "@viglet/viglet-design-system"
import { IconPicker } from "../ui/icon-picker"
import { SmartDescription } from "../ui/smart-description"
import { SectionCard } from "../ui/section-card"
import { StickyPageHeader } from "../sticky-page-header"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "../ui/select"
import { IconDeviceFloppy, IconSettings, IconPlug, IconX } from "@tabler/icons-react"
import { GradientButton } from "../ui/gradient-button"

interface Props {
  value: TurIntegrationInstance;
  isNew: boolean;
}

const DEFAULT_ENDPOINT = "http://localhost:30130/";

export const IntegrationInstanceForm: React.FC<Props> = ({ value, isNew }) => {
  const { t } = useTranslation();
  const { data: tokens = [] } = useTokenInstances();
  const form = useForm<TurIntegrationInstance>({
    defaultValues: isNew
      ? { ...value, endpoint: value.endpoint || DEFAULT_ENDPOINT }
      : value,
  });
  const navigate = useNavigate()
  const createMutation = useCreateIntegrationInstance();
  const updateMutation = useUpdateIntegrationInstance();

  useEffect(() => {
    form.reset(value);
  }, [value]);

  function onSubmit(integrationInstance: TurIntegrationInstance) {
    try {
      if (isNew) {
        createMutation.mutate(integrationInstance);
        toast.success(t("forms.common.saved", { name: integrationInstance.title, feature: t("integration.title") }));
        navigate(ROUTES.INTEGRATION_INSTANCE);
      }
      else {
        updateMutation.mutate(integrationInstance);
        toast.success(t("forms.common.updated", { name: integrationInstance.title, feature: t("integration.title") }));
      }
    } catch (error) {
      console.error("Form submission error", error);
      toast.error(t("forms.common.formSubmitFailed"));
    }
  }

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 px-4 lg:px-6 pb-8">
        <StickyPageHeader>
          <StickyPageHeader.Title
            icon={IconSettings}
            feature={t("integration.detail.title")}
            description={t("integration.detail.description")}
          />
          <StickyPageHeader.Actions>
            <GradientButton type="submit" size="sm">
              <IconDeviceFloppy className="size-4" />
              {t("forms.formActions.saveChanges")}
            </GradientButton>
            <GradientButton type="button" variant="outline" size="sm" onClick={() => navigate(ROUTES.INTEGRATION_INSTANCE)}>
              <IconX className="size-4" />
              {t("forms.formActions.cancel")}
            </GradientButton>
          </StickyPageHeader.Actions>
        </StickyPageHeader>
            {/* General Information Section */}
            <SectionCard variant="blue">
              <SectionCard.Header icon={IconSettings} title={t("forms.common.generalInfo")} description={t("forms.integration.generalDesc")} />
              <SectionCard.Content>
                {/* Title */}
                <FormField
                  control={form.control}
                  name="title"
                  rules={{ required: t("forms.integration.nameRequired") }}
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("forms.integration.nameLabel")}</FormLabel>
                      <FormDescription>
                        {t("forms.integration.nameDesc")}
                      </FormDescription>
                      <FormControl>
                        <Input
                          {...field}
                          placeholder={t("forms.integration.namePlaceholder")}
                          type="text"
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                {/* Description */}
                <FormField
                  control={form.control}
                  name="description"
                  render={({ field }) => (
                    <FormItem>
                      <FormControl>
                        <SmartDescription
                          value={field.value}
                          onChange={field.onChange}
                          placeholder={t("forms.integration.descPlaceholder")}
                          maxLength={500}
                          title={form.watch("title")}
                          entityType={t("integration.title")}
                          enableMetaPrompt
                        >
                          <SmartDescription.Label>{t("forms.integration.descLabel")}</SmartDescription.Label>
                          <SmartDescription.Description>
                            {t("forms.integration.descDesc")}
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
                        </IconPicker>
                      </FormControl>
                    </FormItem>
                  )}
                />
              </SectionCard.Content>
            </SectionCard>

            {/* Dumont DEP Connection Section */}
            <SectionCard variant="violet">
              <SectionCard.Header icon={IconPlug} title={t("forms.integration.connectionDetails")} description={t("forms.integration.dumontDepDesc")} />
              <SectionCard.Content>
                {/* Endpoint */}
                <FormField
                  control={form.control}
                  name="endpoint"
                  rules={{ required: t("forms.integration.endpointRequired") }}
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("forms.integration.dumontEndpointLabel")}</FormLabel>
                      <FormDescription>
                        {t("forms.integration.dumontEndpointDesc")}
                      </FormDescription>
                      <FormControl>
                        <Input
                          placeholder={DEFAULT_ENDPOINT}
                          type="url"
                          {...field}
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                {/* API Token */}
                <FormField
                  control={form.control}
                  name="apiToken"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("forms.integration.apiTokenLabel")}</FormLabel>
                      <FormDescription>
                        {t("forms.integration.apiTokenDesc")}
                      </FormDescription>
                      <FormControl>
                        <Select
                          value={field.value?.id ?? ""}
                          onValueChange={(val) => {
                            if (val === "__none__") {
                              field.onChange(null);
                            } else {
                              const selected = tokens.find((tk) => tk.id === val);
                              field.onChange(selected ?? null);
                            }
                          }}
                        >
                          <SelectTrigger>
                            <SelectValue placeholder={t("forms.integration.apiTokenPlaceholder")} />
                          </SelectTrigger>
                          <SelectContent>
                            <SelectItem value="__none__">{t("forms.integration.apiTokenNone")}</SelectItem>
                            {tokens.map((tk) => (
                              <SelectItem key={tk.id} value={tk.id}>
                                {tk.title}
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </SectionCard.Content>
            </SectionCard>

      </form>
    </Form>
  )
}

