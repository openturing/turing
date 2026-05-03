import { ROUTES } from "@/app/routes.const";
import { LanguageSelect } from "@/components/language-select";
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
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { SectionCard } from "@/components/ui/section-card";
import { Switch } from "@/components/ui/switch";
import type { TurLocale } from "@/models/locale/locale.model";
import { TurLocaleService } from "@/services/locale/locale.service";
import { useCreateSeCore } from "@/api/queries/se-core.queries";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconDatabase, IconDeviceFloppy, IconInfoCircle, IconX } from "@tabler/icons-react";
import { GradientButton } from "@/components/ui/gradient-button";

import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "@viglet/viglet-design-system";

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
  const { t } = useTranslation();
  const { id } = useParams() as { id: string };
  const navigate = useNavigate();
  const [locales, setLocales] = useState<TurLocale[]>([]);
  const [appendLocale, setAppendLocale] = useState(true);
  const createCoreMutation = useCreateSeCore();
  useSubPageBreadcrumb([
    { label: t("se.cores.title"), href: `${ROUTES.SE_INSTANCE}/${id}/cores` },
    { label: t("se.newCore.title") },
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
      toast.error(t("se.newCore.nameFormatError"));
      return;
    }
    try {
      await createCoreMutation.mutateAsync({ seId: id, name: coreName, locale: data.locale });
      toast.success(t("se.newCore.createSuccess", { name: coreName }));
      navigate(`${ROUTES.SE_INSTANCE}/${id}/cores`);
    } catch (error) {
      console.error("Failed to create core", error);
      const msg = error instanceof Error ? error.message : "Unknown error";
      toast.error(t("se.newCore.createFailed", { message: msg }));
    }
  }

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 px-4 lg:px-6 pb-8">
        <StickyPageHeader>
          <StickyPageHeader.Title
            icon={IconDatabase}
            feature={t("se.cores.title")}
            description={t("se.newCore.description")}
          />
          <StickyPageHeader.Actions>
            <GradientButton type="submit" size="sm">
              <IconDeviceFloppy className="size-4" />
              {t("forms.formActions.saveChanges")}
            </GradientButton>
            <GradientButton type="button" variant="outline" size="sm" onClick={() => navigate(`${ROUTES.SE_INSTANCE}/${id}/cores`)}>
              <IconX className="size-4" />
              {t("forms.formActions.cancel")}
            </GradientButton>
          </StickyPageHeader.Actions>
        </StickyPageHeader>
          <SectionCard variant="blue">
            <SectionCard.Header
              icon={IconInfoCircle}
              title={t("se.newCore.coreDetails")}
              description={t("se.newCore.coreDetailsDesc")}
            />
            <SectionCard.Content>
              <FormField
                control={form.control}
                name="name"
                rules={{
                  required: t("se.newCore.nameRequired"),
                  pattern: {
                    value: CORE_NAME_PATTERN,
                    message: t("se.newCore.nameFormat"),
                  },
                }}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{appendLocale ? t("se.newCore.namePrefix") : t("se.newCore.fullCoreName")}</FormLabel>
                    <FormDescription>
                      {appendLocale
                        ? t("se.newCore.namePrefixDesc")
                        : t("se.newCore.fullCoreNameDesc")}
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
                rules={{ required: t("se.newCore.localeRequired") }}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{t("se.newCore.locale")}</FormLabel>
                    <FormDescription>
                      {t("se.newCore.localeDesc")}
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
                  {t("se.newCore.appendLocale")}
                </Label>
              </div>
              {finalCoreName && (
                <div className="rounded-md border bg-muted/50 px-4 py-3 mt-2">
                  <p className="text-xs text-muted-foreground mb-1">{t("se.newCore.coreNamePreview")}</p>
                  <p className="text-sm font-mono font-semibold">{finalCoreName}</p>
                </div>
              )}
            </SectionCard.Content>
          </SectionCard>

      </form>
    </Form>
  );
}
