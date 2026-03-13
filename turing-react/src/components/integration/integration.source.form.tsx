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
          toast.success(`The ${integrationAemSource.name} Integration Source was saved`);

          navigate(sourceInstanceRoute);
        }
        else {
          toast.error("Failed to create the integration source. Please try again.");
        }
      }
      else {
        const result = await turIntegrationAemSourceService.update(integrationAemSource);
        if (result) {
          toast.success(`The ${integrationAemSource.name} Integration Source was updated`);
        } else {
          toast.error("Failed to update the integration source. Please try again.");
        }
      }
    } catch (error) {
      console.error("Form submission error", error);
      toast.error("Failed to submit the form. Please try again.");
    }
  }

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 px-6">

        {/* General Configuration */}
        <SectionCard variant="blue">
          <SectionCard.Header icon={IconSettings} title="General Configuration" description="Basic information and connection settings for the AEM integration source." />
          <SectionCard.Content>
            <FormField
              control={form.control}
              name="name"
              rules={{ required: "Name is required." }}
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Name</FormLabel>
                  <FormControl>
                    <Input {...field} placeholder="My AEM Source" type="text" />
                  </FormControl>
                  <FormDescription>Unique identifier for this integration source</FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="endpoint"
              rules={{ required: "Endpoint is required." }}
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Endpoint</FormLabel>
                  <FormControl>
                    <Input {...field} placeholder="https://aem.example.com" type="url" />
                  </FormControl>
                  <FormDescription>AEM instance URL to connect</FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />

            <div className="grid grid-cols-2 gap-4">
              <FormField
                control={form.control}
                name="username"
                rules={{ required: "Username is required." }}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Username</FormLabel>
                    <FormControl>
                      <Input {...field} placeholder="admin" type="text" />
                    </FormControl>
                    <FormDescription>AEM authentication username</FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={form.control}
                name="password"
                rules={{ required: "Password is required." }}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Password</FormLabel>
                    <FormControl>
                      <Input {...field} placeholder="••••••••" type="password" />
                    </FormControl>
                    <FormDescription>AEM authentication password</FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>

            <FormField
              control={form.control}
              name="rootPath"
              rules={{ required: "Root Path is required." }}
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Root Path</FormLabel>
                  <FormControl>
                    <Input {...field} placeholder="/content/mysite" type="text" />
                  </FormControl>
                  <FormDescription>Root JCR path to start content extraction</FormDescription>
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
                    <FormLabel>Content Type</FormLabel>
                    <FormControl>
                      <Input {...field} placeholder="cq:Page" type="text" />
                    </FormControl>
                    <FormDescription>JCR content type to index</FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={form.control}
                name="subType"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Sub Type</FormLabel>
                    <FormControl>
                      <Input {...field} placeholder="cq:PageContent" type="text" />
                    </FormControl>
                    <FormDescription>JCR sub-type for content nodes</FormDescription>
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
                  <FormLabel>Once Pattern</FormLabel>
                  <FormControl>
                    <Input {...field} placeholder=".*" type="text" />
                  </FormControl>
                  <FormDescription>Regex pattern for one-time indexing filter</FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
          </SectionCard.Content>
        </SectionCard>

        {/* Author Configuration */}
        <SectionCard variant="violet">
          <SectionCard.Header icon={IconUser} title="Author Configuration" description="Settings for indexing content from AEM Author instance." />
          <SectionCard.Content>
            <FormField
              control={form.control}
              name="author"
              render={({ field }) => (
                <FormItemTwoColumns className="rounded-lg border p-4">
                  <FormItemTwoColumns.Left className="space-y-0.5">
                    <FormItemTwoColumns.Label className="text-base">Enable Author</FormItemTwoColumns.Label>
                    <FormItemTwoColumns.Description>
                      Index content from AEM Author instance
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
                  <FormLabel>Author SN Site</FormLabel>
                  <FormControl>
                    <Input {...field} placeholder="author-site" type="text" />
                  </FormControl>
                  <FormDescription>Semantic Navigation site ID for author content</FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="authorURLPrefix"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Author URL Prefix</FormLabel>
                  <FormControl>
                    <Input {...field} placeholder="https://author.example.com" type="url" />
                  </FormControl>
                  <FormDescription>URL prefix for author content links</FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
          </SectionCard.Content>
        </SectionCard>

        {/* Publish Configuration */}
        <SectionCard variant="emerald">
          <SectionCard.Header icon={IconSend} title="Publish Configuration" description="Settings for indexing content from AEM Publish instance." />
          <SectionCard.Content>
            <FormField
              control={form.control}
              name="publish"
              render={({ field }) => (
                <FormItemTwoColumns className="rounded-lg border p-4">
                  <FormItemTwoColumns.Left className="space-y-0.5">
                    <FormItemTwoColumns.Label className="text-base">Enable Publish</FormItemTwoColumns.Label>
                    <FormItemTwoColumns.Description>
                      Index content from AEM Publish instance
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
                  <FormLabel>Publish SN Site</FormLabel>
                  <FormControl>
                    <Input {...field} placeholder="publish-site" type="text" />
                  </FormControl>
                  <FormDescription>Semantic Navigation site ID for published content</FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="publishURLPrefix"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Publish URL Prefix</FormLabel>
                  <FormControl>
                    <Input {...field} placeholder="https://www.example.com" type="url" />
                  </FormControl>
                  <FormDescription>URL prefix for published content links</FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
          </SectionCard.Content>
        </SectionCard>

        {/* Locales Configuration */}
        <SectionCard variant="amber">
          <SectionCard.Header icon={IconLanguage} title="Locales Configuration" description="Locale detection and path mapping settings." />
          <SectionCard.Content>
            <FormField
              control={form.control}
              name="defaultLocale"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Default Locale</FormLabel>
                  <FormControl>
                    <LanguageSelect
                      value={field.value}
                      onValueChange={field.onChange}
                      locales={locales}
                      extraLocaleValues={field.value ? [field.value] : []}
                      className="w-full"
                    />
                  </FormControl>
                  <FormDescription>Default locale for content without explicit locale</FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="localeClass"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Locale Class</FormLabel>
                  <FormControl>
                    <Input {...field} placeholder="com.example.LocaleProvider" type="text" />
                  </FormControl>
                  <FormDescription>Java class for custom locale detection logic</FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="deltaClass"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Delta Class</FormLabel>
                  <FormControl>
                    <Input {...field} placeholder="com.example.DeltaProvider" type="text" />
                  </FormControl>
                  <FormDescription>Java class for incremental indexing logic</FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />

            <div className="rounded-lg border p-4 bg-muted/50">
              <h4 className="font-medium mb-2">Locale Paths</h4>
              <p className="text-sm text-muted-foreground mb-4">
                Define specific paths for different locales.
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
