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
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import type { TurIntegrationInstance } from "@/models/integration/integration-instance.model.ts"
import { TurIntegrationInstanceService } from "@/services/integration/integration.service"
import { useEffect } from "react"
import { useTranslation } from "react-i18next"
import {
  useForm
} from "react-hook-form"
import { useNavigate } from "react-router-dom"
import { toast } from "sonner"
import { FormItemTwoColumns } from "../ui/form-item-two-columns"
import { IconPicker } from "../ui/icon-picker"
import { SmartDescription } from "../ui/smart-description"
import { SectionCard } from "../ui/section-card"
import { FormActions } from "../ui/form-actions"
import { IconSettings, IconPlug } from "@tabler/icons-react"

const turIntegrationInstanceService = new TurIntegrationInstanceService();
interface Props {
  value: TurIntegrationInstance;
  isNew: boolean;
}

export const IntegrationInstanceForm: React.FC<Props> = ({ value, isNew }) => {
  const { t } = useTranslation();
  const form = useForm<TurIntegrationInstance>({
    defaultValues: value
  });
  const navigate = useNavigate()
  useEffect(() => {
    form.reset(value);
  }, [value]);


  function onSubmit(integrationInstance: TurIntegrationInstance) {
    try {
      if (isNew) {
        turIntegrationInstanceService.create(integrationInstance);
        toast.success(t("forms.common.saved", { name: integrationInstance.title, feature: t("integration.title") }));
        navigate(ROUTES.INTEGRATION_INSTANCE);
      }
      else {
        turIntegrationInstanceService.update(integrationInstance);
        toast.success(t("forms.common.updated", { name: integrationInstance.title, feature: t("integration.title") }));
      }
    } catch (error) {
      console.error("Form submission error", error);
      toast.error(t("forms.common.formSubmitFailed"));
    }
  }

  return (
    <div className="px-6 py-8">
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
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

            {/* Connection Details Section */}
            <SectionCard variant="violet">
              <SectionCard.Header icon={IconPlug} title={t("forms.integration.connectionDetails")} description={t("forms.integration.connectionDetailsDesc")} />
              <SectionCard.Content>
                {/* Vendor (50/50 row) */}
                <FormField
                  control={form.control}
                  name="vendor"
                  rules={{ required: t("forms.integration.vendorRequired") }}
                  render={({ field }) => (
                    <FormItemTwoColumns>
                      <FormItemTwoColumns.Left>
                        <FormItemTwoColumns.Label>{t("forms.integration.typeLabel")}</FormItemTwoColumns.Label>
                        <FormItemTwoColumns.Description>
                          {t("forms.integration.typeDesc")}
                        </FormItemTwoColumns.Description>
                      </FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Right>
                        <FormControl>
                          <Select onValueChange={field.onChange} value={field.value}>
                            <SelectTrigger className="w-full">
                              <SelectValue placeholder={t("forms.integration.selectType")} />
                            </SelectTrigger>
                            <SelectContent>
                              <SelectItem key="AEM" value="AEM">{t("forms.integration.adobeAem")}</SelectItem>
                              <SelectItem key="ASSETS" value="ASSETS">{t("forms.integration.assets")}</SelectItem>
                              <SelectItem key="JDBC_DATABASE" value="JDBC_DATABASE">{t("forms.integration.jdbcDatabase")}</SelectItem>
                              <SelectItem key="WEB_CRAWLER" value="WEB_CRAWLER">{t("forms.integration.webCrawler")}</SelectItem>
                            </SelectContent>
                          </Select>
                        </FormControl>
                      </FormItemTwoColumns.Right>
                      <FormMessage />
                    </FormItemTwoColumns>
                  )}
                />
                {/* Endpoint */}
                <FormField
                  control={form.control}
                  name="endpoint"
                  rules={{ required: t("forms.integration.endpointRequired") }}
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("forms.common.endpointUrl")}</FormLabel>
                      <FormDescription>
                        {t("forms.integration.endpointDesc")}
                      </FormDescription>
                      <FormControl>
                        <Input
                          placeholder={t("forms.integration.endpointPlaceholder")}
                          type="url"
                          {...field}
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </SectionCard.Content>
            </SectionCard>

          <FormActions>
            <FormActions.Cancel onClick={() => navigate(ROUTES.INTEGRATION_INSTANCE)} />
            <FormActions.Submit />
          </FormActions>
        </form>
      </Form >
    </div>
  )
}

