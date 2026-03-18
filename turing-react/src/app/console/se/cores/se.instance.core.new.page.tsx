import { ROUTES } from "@/app/routes.const";
import { LanguageSelect } from "@/components/language-select";
import { SubPageHeader } from "@/components/sub.page.header";
import { FormActions } from "@/components/ui/form-actions";
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { SectionCard } from "@/components/ui/section-card";
import { Switch } from "@/components/ui/switch";
import type { TurLocale } from "@/models/locale/locale.model";
import { TurLocaleService } from "@/services/locale/locale.service";
import { TurSEInstanceService } from "@/services/se/se.service";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconDatabase, IconInfoCircle } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "sonner";

const turSEInstanceService = new TurSEInstanceService();
const turLocaleService = new TurLocaleService();

const CORE_NAME_PATTERN = /^[a-zA-Z0-9_-]+$/;

interface CreateCoreForm {
  name: string;
  locale: string;
}

function buildCoreName(name: string, locale: string, appendLocale: boolean): string {
  const trimmed = name.trim();
  if (!trimmed) return "";
  if (!appendLocale || !locale) return trimmed;
  return `${trimmed}_${locale}`;
}

export default function SEInstanceCoreNewPage() {
  const { id } = useParams() as { id: string };
  const navigate = useNavigate();
  const [locales, setLocales] = useState<TurLocale[]>([]);
  const [appendLocale, setAppendLocale] = useState(true);
  useSubPageBreadcrumb([
    { label: "Cores", href: `${ROUTES.SE_INSTANCE}/${id}/cores` },
    { label: "New" },
  ]);

  const form = useForm<CreateCoreForm>({
    defaultValues: { name: "", locale: "" },
  });

  const watchName = form.watch("name");
  const watchLocale = form.watch("locale");
  const finalCoreName = buildCoreName(watchName, watchLocale, appendLocale);

  useEffect(() => {
    turLocaleService.query().then(setLocales);
  }, []);

  async function onSubmit(data: CreateCoreForm) {
    const coreName = buildCoreName(data.name.trim(), data.locale, appendLocale);
    if (!CORE_NAME_PATTERN.test(coreName)) {
      toast.error("Core name must contain only letters, numbers, underscores (_), or hyphens (-).");
      return;
    }
    try {
      await turSEInstanceService.createCore(id, coreName, data.locale);
      toast.success(`Core "${coreName}" created successfully.`);
      navigate(`${ROUTES.SE_INSTANCE}/${id}/cores`);
    } catch (error) {
      console.error("Failed to create core", error);
      const msg = error instanceof Error ? error.message : "Unknown error";
      toast.error(`Failed to create core: ${msg}`);
    }
  }

  return (
    <>
      <SubPageHeader
        icon={IconDatabase}
        name="New Core"
        feature="Cores"
        description="Create a new core or collection in this Search Engine."
      />
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 py-8 px-6">
          <SectionCard variant="blue">
            <SectionCard.Header
              icon={IconInfoCircle}
              title="Core Details"
              description="Name and locale for the new core."
            />
            <SectionCard.Content>
              <FormField
                control={form.control}
                name="name"
                rules={{
                  required: "Core name is required.",
                  pattern: {
                    value: CORE_NAME_PATTERN,
                    message: "Use only letters, numbers, underscores (_), or hyphens (-).",
                  },
                }}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{appendLocale ? "Name Prefix" : "Full Core Name"}</FormLabel>
                    <FormDescription>
                      {appendLocale
                        ? "Enter a prefix. The locale will be appended automatically (e.g., my-site + en_US = my-site_en_US)."
                        : "Enter the full core name exactly as it should be created."}
                    </FormDescription>
                    <FormControl>
                      <Input
                        {...field}
                        onChange={(e) => field.onChange(e.target.value.replace(/[^a-zA-Z0-9_-]/g, ""))}
                        placeholder={appendLocale ? "e.g., my-site" : "e.g., my-site_en_US"}
                        type="text"
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="locale"
                rules={{ required: "Please select a locale." }}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Locale</FormLabel>
                    <FormDescription>
                      The language/locale determines the configSet used for text analysis (stemming, stopwords, etc.).
                    </FormDescription>
                    <FormControl>
                      <LanguageSelect
                        value={field.value}
                        onValueChange={field.onChange}
                        locales={locales}
                        extraLocaleValues={field.value ? [field.value] : []}
                        placeholder="Choose the language"
                        className="w-full"
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <div className="flex items-center gap-3 pt-2">
                <Switch
                  id="append-locale"
                  checked={appendLocale}
                  onCheckedChange={setAppendLocale}
                />
                <Label htmlFor="append-locale" className="text-sm">
                  Append locale to name
                </Label>
              </div>
              {finalCoreName && (
                <div className="rounded-md border bg-muted/50 px-4 py-3 mt-2">
                  <p className="text-xs text-muted-foreground mb-1">Core name preview</p>
                  <p className="text-sm font-mono font-semibold">{finalCoreName}</p>
                </div>
              )}
            </SectionCard.Content>
          </SectionCard>

          <FormActions>
            <FormActions.Cancel onClick={() => navigate(`${ROUTES.SE_INSTANCE}/${id}/cores`)} />
            <FormActions.Submit>Create Core</FormActions.Submit>
          </FormActions>
        </form>
      </Form>
    </>
  );
}
