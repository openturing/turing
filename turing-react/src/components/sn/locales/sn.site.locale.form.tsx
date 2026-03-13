"use client"
import { ROUTES } from "@/app/routes.const"
import { LanguageSelect } from "@/components/language-select"
import { Button } from "@/components/ui/button"
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form"
import { FormActions } from "@/components/ui/form-actions"
import { FormItemTwoColumns } from "@/components/ui/form-item-two-columns"
import { GradientSwitch } from "@/components/ui/gradient-switch"
import {
  Input
} from "@/components/ui/input"
import { SectionCard } from "@/components/ui/section-card"
import type { TurLocale } from "@/models/locale/locale.model"
import type { TurSNSiteLocale } from "@/models/sn/sn-site-locale.model"
import { TurLocaleService } from "@/services/locale/locale.service"
import { TurSNSiteLocaleService } from "@/services/sn/sn.site.locale.service"
import { IconDatabase, IconExternalLink, IconLanguage } from "@tabler/icons-react"
import { useEffect, useState } from "react"
import {
  useForm
} from "react-hook-form"
import { useNavigate } from "react-router-dom"
import { toast } from "sonner"


interface Props {
  snSiteId: string;
  snLocale: TurSNSiteLocale;
  isNew: boolean;
}
const turSNSiteLocaleService = new TurSNSiteLocaleService();

export const SNSiteLocaleForm: React.FC<Props> = ({ snSiteId, snLocale, isNew }) => {
  const [locales, setLocales] = useState<TurLocale[]>([]);
  const [useCustomCore, setUseCustomCore] = useState(isNew && !!snLocale.core);
  const form = useForm<TurSNSiteLocale>({
    defaultValues: snLocale
  });
  const urlBase = `/admin/sn/instance/${snSiteId}/locale`;
  const navigate = useNavigate()

  useEffect(() => {
    form.reset(snLocale);
    setUseCustomCore(isNew && !!snLocale.core);
  }, [form, isNew, snLocale]);

  useEffect(() => {
    const fetchLocales = async () => {
      const result = await new TurLocaleService().query();
      setLocales(result);
    };
    fetchLocales();
  }, []);

  async function onSubmit(snLocale: TurSNSiteLocale) {
    const payload = {
      ...snLocale,
      core: isNew && !useCustomCore ? "" : snLocale.core
    };
    try {
      if (isNew) {
        const result = await turSNSiteLocaleService.create(snSiteId, payload);
        if (result) {
          toast.success(`The ${snLocale.language} SN Locale was created`);
          navigate(urlBase);
        }
        else {
          toast.error("Failed to create the SN Locale. Please try again.");
        }
      }
      else {
        const result = await turSNSiteLocaleService.update(snSiteId, payload);
        if (result) {
          toast.success(`The ${snLocale.language} SN Locale was updated`);
        }
        else {
          toast.error("Failed to update the SN Locale. Please try again.");
        }
      }
    } catch (error) {
      console.error("Form submission error", error);
      toast.error("Failed to submit the form. Please try again.");
    }
  }

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 py-8 px-6">
        {/* Language Section */}
        <SectionCard variant="blue">
          <SectionCard.Header icon={IconLanguage} title="Language Settings" description="Configure the language for semantic search and content indexing." />
          <SectionCard.Content>
            <FormField
              control={form.control}
              name="language"
              rules={{ required: "Language is required." }}
              render={({ field }) => (
                <FormItem>
                  <div className="flex flex-row justify-between items-center w-full">
                    <div className="flex flex-col">
                      <FormLabel>Language</FormLabel>
                      <FormDescription>
                        Select the language code for semantic search and content indexing.
                      </FormDescription>
                    </div>
                    <div className="flex-1 max-w-xs">
                      <FormControl>
                        <LanguageSelect
                          value={field.value}
                          onValueChange={field.onChange}
                          locales={locales}
                          extraLocaleValues={field.value ? [field.value] : []}
                          placeholder="Choose..."
                          className="w-full"
                        />
                      </FormControl>
                    </div>
                  </div>
                  <FormMessage />
                </FormItem>
              )}
            />
          </SectionCard.Content>
        </SectionCard>

        {/* Core Section */}
        <SectionCard variant="violet">
          <SectionCard.Header icon={IconDatabase} title="Core Configuration" description="Manage the search engine core for this locale." />
          <SectionCard.Content>
            {isNew && (
              <FormItemTwoColumns>
                <FormItemTwoColumns.Left>
                  <FormItemTwoColumns.Label>Define Core Manually</FormItemTwoColumns.Label>
                  <FormItemTwoColumns.Description>
                    Enable this option to manually specify the core. If disabled, the core will be created automatically.
                  </FormItemTwoColumns.Description>
                </FormItemTwoColumns.Left>
                <FormItemTwoColumns.Right>
                  <GradientSwitch
                    checked={useCustomCore}
                    onCheckedChange={(value) => {
                      const nextValue = !!value;
                      setUseCustomCore(nextValue);
                      if (!nextValue) {
                        form.setValue("core", "", { shouldDirty: true });
                      }
                    }}
                  />
                </FormItemTwoColumns.Right>
              </FormItemTwoColumns>
            )}

            {(!isNew || useCustomCore) && (
              <FormField
                control={form.control}
                name="core"
                render={({ field }) => {
                  const seId = snLocale.turSNSite?.turSEInstance?.id;
                  const searchSuffix = field.value ? `?search=${encodeURIComponent(field.value)}` : "";
                  const coresUrl = seId
                    ? `${ROUTES.SE_INSTANCE}/${seId}/cores${searchSuffix}`
                    : undefined;
                  return (
                    <FormItem>
                      <FormLabel>Core</FormLabel>
                      <FormDescription>
                        The core name will appear in the semantic navigation site locale list.
                      </FormDescription>
                      <FormControl>
                        <div className="flex gap-2">
                          <Input
                            {...field}
                            placeholder="Enter core name"
                            type="text"
                          />
                          {coresUrl && (
                            <Button
                              type="button"
                              variant="outline"
                              size="icon"
                              title="View in Search Engine cores"
                              onClick={() => navigate(coresUrl)}
                            >
                              <IconExternalLink className="size-4" />
                            </Button>
                          )}
                        </div>
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  );
                }}
              />
            )}
          </SectionCard.Content>
        </SectionCard>

        {/* Action Footer */}
        <FormActions>
          <FormActions.Cancel onClick={() => navigate(urlBase)} />
          <FormActions.Submit />
        </FormActions>
      </form>
    </Form>
  )
}
