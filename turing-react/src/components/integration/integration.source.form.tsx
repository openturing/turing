"use client"
import { ROUTES } from "@/app/routes.const"
import { LanguageSelect } from "@/components/language-select"
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
import { useAemSourceService } from "@/contexts/TuringServiceContext"
import type { TurIntegrationAemSource } from "@/models/integration/integration-aem-source.model"
import type { TurLocale } from "@/models/locale/locale.model"
import { TurLocaleService } from "@/services/locale/locale.service"
import { IconLanguage, IconSend, IconSettings, IconUser } from "@tabler/icons-react"
import { useEffect, useState } from "react"
import {
  useForm
} from "react-hook-form"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"
import { toast } from "sonner"
import { FormActions } from "../ui/form-actions"
import { FormItemTwoColumns } from "../ui/form-item-two-columns"
import { GradientSwitch } from "../ui/gradient-switch"
import { SectionCard } from "../ui/section-card"
import { DynamicSourceLocales } from "./dynamic.source.locale"

interface Props {
  value: TurIntegrationAemSource;
  isNew: boolean;
  integrationId: string;
}
const turLocaleService = new TurLocaleService();
export const IntegrationSourceForm: React.FC<Props> = ({ value, isNew, integrationId }) => {
  const { t } = useTranslation();
  const turIntegrationAemSourceService = useAemSourceService(integrationId);
  const form = useForm<TurIntegrationAemSource>({
    defaultValues: value
  });
  const { control, register } = form;
  const navigate = useNavigate()
  const [locales, setLocales] = useState<TurLocale[]>([]);
  useEffect(() => {
    form.reset(value);
    turLocaleService.query().then(setLocales)
  }, [form, value]);

  const sourceInstanceRoute = `${ROUTES.INTEGRATION_INSTANCE}/${integrationId}/source`
  async function onSubmit(integrationAemSource: TurIntegrationAemSource) {
    try {
      if (isNew) {
        const result = await turIntegrationAemSourceService.create(integrationAemSource);
        if (result) {
          toast.success(t("forms.common.saved", { name: integrationAemSource.name, feature: t("integration.sources.feature") }));

          navigate(sourceInstanceRoute);
        }
        else {
          toast.error(t("forms.common.formSubmitFailed"));
        }
      }
      else {
        const result = await turIntegrationAemSourceService.update(integrationAemSource);
        if (result) {
          toast.success(t("forms.common.updated", { name: integrationAemSource.name, feature: t("integration.sources.feature") }));
        } else {
          toast.error(t("forms.common.formSubmitFailed"));
        }
      }
    } catch (error) {
      console.error("Form submission error", error);
      toast.error(t("forms.common.formSubmitFailed"));
    }
  }

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 px-6">

        {/* General Configuration */}
        <SectionCard variant="blue">
          <SectionCard.Header icon={IconSettings} title={t("forms.integrationSource.general")} description={t("forms.integrationSource.generalDesc")} />
          <SectionCard.Content>
            <FormField
              control={form.control}
              name="name"
              rules={{ required: t("forms.integration.nameRequired") }}
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("forms.common.name")}</FormLabel>
                  <FormControl>
                    <Input {...field} placeholder={t("forms.integration.namePlaceholder")} type="text" />
                  </FormControl>
                  <FormDescription>{t("forms.integration.nameDesc")}</FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="endpoint"
              rules={{ required: t("forms.integration.endpointRequired") }}
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("forms.integrationSource.endpoint")}</FormLabel>
                  <FormControl>
                    <Input {...field} placeholder={t("forms.integration.endpointPlaceholder")} type="url" />
                  </FormControl>
                  <FormDescription>{t("forms.integration.endpointDesc")}</FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />

            <div className="grid grid-cols-2 gap-4">
              <FormField
                control={form.control}
                name="username"
                rules={{ required: t("forms.integrationSource.username") + " is required." }}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{t("forms.integrationSource.username")}</FormLabel>
                    <FormControl>
                      <Input {...field} placeholder="admin" type="text" />
                    </FormControl>
                    <FormDescription>{t("forms.integrationSource.username")}</FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={form.control}
                name="password"
                rules={{ required: t("forms.integrationSource.password") + " is required." }}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{t("forms.integrationSource.password")}</FormLabel>
                    <FormControl>
                      <Input {...field} placeholder="••••••••" type="password" />
                    </FormControl>
                    <FormDescription>{t("forms.integrationSource.password")}</FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>

            <FormField
              control={form.control}
              name="rootPath"
              rules={{ required: t("forms.integrationSource.rootPath") + " is required." }}
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("forms.integrationSource.rootPath")}</FormLabel>
                  <FormControl>
                    <Input {...field} placeholder="/content/mysite" type="text" />
                  </FormControl>
                  <FormDescription>{t("forms.integrationSource.rootPath")}</FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />

            <div className="grid grid-cols-2 gap-4">
              <FormField
                control={form.control}
                name="contentType"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{t("forms.integrationSource.contentType")}</FormLabel>
                    <FormControl>
                      <Input {...field} placeholder="cq:Page" type="text" />
                    </FormControl>
                    <FormDescription>{t("forms.integrationSource.contentType")}</FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={form.control}
                name="subType"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{t("forms.integrationSource.subType")}</FormLabel>
                    <FormControl>
                      <Input {...field} placeholder="cq:PageContent" type="text" />
                    </FormControl>
                    <FormDescription>{t("forms.integrationSource.subType")}</FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>

            <FormField
              control={form.control}
              name="oncePattern"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("forms.integrationSource.oncePattern")}</FormLabel>
                  <FormControl>
                    <Input {...field} placeholder=".*" type="text" />
                  </FormControl>
                  <FormDescription>{t("forms.integrationSource.oncePattern")}</FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
          </SectionCard.Content>
        </SectionCard>

        {/* Author Configuration */}
        <SectionCard variant="violet">
          <SectionCard.Header icon={IconUser} title={t("forms.integrationSource.authorConfig")} description={t("forms.integrationSource.authorConfigDesc")} />
          <SectionCard.Content>
            <FormField
              control={form.control}
              name="author"
              render={({ field }) => (
                <FormItemTwoColumns className="rounded-lg border p-4">
                  <FormItemTwoColumns.Left className="space-y-0.5">
                    <FormItemTwoColumns.Label className="text-base">{t("forms.integrationSource.enableAuthor")}</FormItemTwoColumns.Label>
                    <FormItemTwoColumns.Description>
                      {t("forms.integrationSource.authorConfigDesc")}
                    </FormItemTwoColumns.Description>
                  </FormItemTwoColumns.Left>
                  <FormItemTwoColumns.Right>
                    <FormControl>
                      <GradientSwitch
                        checked={field.value}
                        onCheckedChange={field.onChange}
                      />
                    </FormControl>
                  </FormItemTwoColumns.Right>
                </FormItemTwoColumns>
              )}
            />

            <FormField
              control={form.control}
              name="authorSNSite"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("forms.integrationSource.authorSnSite")}</FormLabel>
                  <FormControl>
                    <Input {...field} placeholder="author-site" type="text" />
                  </FormControl>
                  <FormDescription>{t("forms.integrationSource.authorSnSite")}</FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="authorURLPrefix"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("forms.integrationSource.authorUrlPrefix")}</FormLabel>
                  <FormControl>
                    <Input {...field} placeholder="https://author.example.com" type="url" />
                  </FormControl>
                  <FormDescription>{t("forms.integrationSource.authorUrlPrefix")}</FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
          </SectionCard.Content>
        </SectionCard>

        {/* Publish Configuration */}
        <SectionCard variant="emerald">
          <SectionCard.Header icon={IconSend} title={t("forms.integrationSource.publishConfig")} description={t("forms.integrationSource.publishConfigDesc")} />
          <SectionCard.Content>
            <FormField
              control={form.control}
              name="publish"
              render={({ field }) => (
                <FormItemTwoColumns className="rounded-lg border p-4">
                  <FormItemTwoColumns.Left className="space-y-0.5">
                    <FormItemTwoColumns.Label className="text-base">{t("forms.integrationSource.enablePublish")}</FormItemTwoColumns.Label>
                    <FormItemTwoColumns.Description>
                      {t("forms.integrationSource.publishConfigDesc")}
                    </FormItemTwoColumns.Description>
                  </FormItemTwoColumns.Left>
                  <FormItemTwoColumns.Right>
                    <FormControl>
                      <GradientSwitch
                        checked={field.value}
                        onCheckedChange={field.onChange}
                      />
                    </FormControl>
                  </FormItemTwoColumns.Right>
                </FormItemTwoColumns>
              )}
            />

            <FormField
              control={form.control}
              name="publishSNSite"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("forms.integrationSource.publishSnSite")}</FormLabel>
                  <FormControl>
                    <Input {...field} placeholder="publish-site" type="text" />
                  </FormControl>
                  <FormDescription>{t("forms.integrationSource.publishSnSite")}</FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="publishURLPrefix"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("forms.integrationSource.publishUrlPrefix")}</FormLabel>
                  <FormControl>
                    <Input {...field} placeholder="https://www.example.com" type="url" />
                  </FormControl>
                  <FormDescription>{t("forms.integrationSource.publishUrlPrefix")}</FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
          </SectionCard.Content>
        </SectionCard>

        {/* Locales Configuration */}
        <SectionCard variant="amber">
          <SectionCard.Header icon={IconLanguage} title={t("forms.integrationSource.localesConfig")} description={t("forms.integrationSource.localesConfigDesc")} />
          <SectionCard.Content>
            <FormField
              control={form.control}
              name="defaultLocale"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("forms.integrationSource.defaultLocale")}</FormLabel>
                  <FormControl>
                    <LanguageSelect
                      value={field.value}
                      onValueChange={field.onChange}
                      locales={locales}
                      extraLocaleValues={field.value ? [field.value] : []}
                      className="w-full"
                    />
                  </FormControl>
                  <FormDescription>{t("forms.integrationSource.defaultLocale")}</FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="localeClass"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("forms.integrationSource.localeClass")}</FormLabel>
                  <FormControl>
                    <Input {...field} placeholder="com.example.LocaleProvider" type="text" />
                  </FormControl>
                  <FormDescription>{t("forms.integrationSource.localeClass")}</FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="deltaClass"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("forms.integrationSource.deltaClass")}</FormLabel>
                  <FormControl>
                    <Input {...field} placeholder="com.example.DeltaProvider" type="text" />
                  </FormControl>
                  <FormDescription>{t("forms.integrationSource.deltaClass")}</FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />

            <div className="rounded-lg border p-4 bg-muted/50">
              <h4 className="font-medium mb-2">{t("forms.integrationSource.localePaths")}</h4>
              <p className="text-sm text-muted-foreground mb-4">
                {t("forms.integrationSource.localePathsDesc")}
              </p>
              <DynamicSourceLocales
                fieldName="localePaths"
                control={control}
                register={register}
              />
            </div>
          </SectionCard.Content>
        </SectionCard>

        <FormActions>
          <FormActions.Cancel onClick={() => navigate(sourceInstanceRoute)} />
          <FormActions.Submit />
        </FormActions>
      </form>
    </Form>
  )
}
