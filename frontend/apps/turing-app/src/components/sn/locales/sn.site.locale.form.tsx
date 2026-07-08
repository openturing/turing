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
import { StickyPageHeader } from "@/components/sticky-page-header"
import { BentoHero, BentoScrollSaveBar } from "@/components/bento"
import { SNFormSection, type SNFormChrome } from "@/components/sn/sn-form-section"
import { FormItemTwoColumns } from "@/components/ui/form-item-two-columns"
import { GradientSwitch } from "@/components/ui/gradient-switch"
import {
  Input
} from "@/components/ui/input"
import type { TurLocale } from "@/models/locale/locale.model"
import type { TurSNSiteLocale } from "@/models/sn/sn-site-locale.model"
import { TurLocaleService } from "@/services/locale/locale.service"
import { TurSNSiteLocaleService } from "@/services/sn/sn.site.locale.service"
import { IconDatabase, IconDeviceFloppy, IconExternalLink, IconLanguage, IconX } from "@tabler/icons-react"
import { GradientButton } from "@/components/ui/gradient-button"
import { DialogDelete } from "@/components/dialog.delete"
import { useEffect, useState } from "react"
import {
  useForm
} from "react-hook-form"
import { useTranslation } from "react-i18next"
import { Link, useNavigate } from "react-router-dom"
import { toast } from "@viglet/viglet-design-system"


interface Props {
  snSiteId: string;
  snLocale: TurSNSiteLocale;
  isNew: boolean;
  onDelete?: () => void;
  open?: boolean;
  setOpen?: React.Dispatch<React.SetStateAction<boolean>>;
  /** SN instance base route for save/cancel navigation. Defaults to the
   *  console; the Bento surface passes `ROUTES.BENTO_SN_INSTANCE` (T576). */
  baseRoute?: string;
  /** Render chrome. console = StickyPageHeader + SectionCards; bento = BentoHero + frosted BentoFormSection cards (T576). Defaults to console. */
  chrome?: SNFormChrome;
}
const turSNSiteLocaleService = new TurSNSiteLocaleService();

export const SNSiteLocaleForm: React.FC<Props> = ({ snSiteId, snLocale, isNew, onDelete, open, setOpen, baseRoute = ROUTES.SN_INSTANCE, chrome = "console" }) => {
  const { t } = useTranslation();
  const [locales, setLocales] = useState<TurLocale[]>([]);
  const [useCustomCore, setUseCustomCore] = useState(isNew && !!snLocale.core);
  const form = useForm<TurSNSiteLocale>({
    defaultValues: snLocale
  });
  const urlBase = `${baseRoute}/${snSiteId}/locale`;
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
          toast.success(t("forms.snLocale.created", { name: snLocale.language }));
          navigate(urlBase);
        }
        else {
          toast.error(t("forms.snLocale.createFailed"));
        }
      }
      else {
        const result = await turSNSiteLocaleService.update(snSiteId, payload);
        if (result) {
          toast.success(t("forms.snLocale.updated", { name: snLocale.language }));
        }
        else {
          toast.error(t("forms.snLocale.updateFailed"));
        }
      }
    } catch (error) {
      console.error("Form submission error", error);
      toast.error(t("forms.common.formSubmitFailed"));
    }
  }

  const isBento = chrome === "bento";

  const actions = (
    <>
      {onDelete && open !== undefined && setOpen && <DialogDelete feature={t("sn.multiLanguage.feature")} name={isNew ? t("sn.multiLanguage.feature") : snLocale.language} onDelete={onDelete} open={open} setOpen={setOpen} />}
      <GradientButton type="submit" size="sm">
        <IconDeviceFloppy className="size-4" />
        {t("forms.formActions.saveChanges")}
      </GradientButton>
      <GradientButton type="button" variant="outline" size="sm" onClick={() => navigate(urlBase)}>
        <IconX className="size-4" />
        {t("forms.formActions.cancel")}
      </GradientButton>
    </>
  );

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} className={isBento ? "space-y-5 pb-8" : "space-y-4 px-4 lg:px-6 pb-8"}>
        {isBento ? (
          <>
          <BentoHero
            eyebrow={<Link to={urlBase} className="hover:text-foreground">{t("sn.multiLanguage.feature")}</Link>}
            leading={
              <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-emerald-500 to-teal-600 text-white shadow-md">
                <IconLanguage size={24} />
              </span>
            }
            title={isNew ? t("sn.multiLanguage.feature") : (snLocale.language || snLocale.core)}
            subtitle={isNew ? t("sn.multiLanguage.description") : snLocale.core}
            trailing={<div className="bento-fade-out flex shrink-0 items-center gap-2">{actions}</div>}
          />
          <BentoScrollSaveBar onCancel={() => navigate(urlBase)} />
          </>
        ) : (
          <StickyPageHeader>
            <StickyPageHeader.Title
              icon={IconLanguage}
              feature={t("sn.multiLanguage.feature")}
              description={isNew ? t("sn.multiLanguage.description") : snLocale.core}
            />
            <StickyPageHeader.Actions>{actions}</StickyPageHeader.Actions>
          </StickyPageHeader>
        )}
        {/* Language Section */}
        <SNFormSection chrome={chrome} icon={IconLanguage} tone="blue" title={t("forms.snLocale.languageSettings")} description={t("forms.snLocale.languageSettingsDesc")}>
            <FormField
              control={form.control}
              name="language"
              rules={{ required: t("forms.snLocale.languageRequired") }}
              render={({ field }) => (
                <FormItem>
                  <div className="flex flex-row justify-between items-center w-full">
                    <div className="flex flex-col">
                      <FormLabel>{t("forms.snLocale.language")}</FormLabel>
                      <FormDescription>
                        {t("forms.snLocale.languageDesc")}
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
        </SNFormSection>

        {/* Core Section */}
        <SNFormSection chrome={chrome} icon={IconDatabase} tone="violet" title={t("forms.snLocale.coreConfig")} description={t("forms.snLocale.coreConfigDesc")}>
            {isNew && (
              <FormItemTwoColumns>
                <FormItemTwoColumns.Left>
                  <FormItemTwoColumns.Label>{t("forms.snLocale.defineManually")}</FormItemTwoColumns.Label>
                  <FormItemTwoColumns.Description>
                    {t("forms.snLocale.defineManuallyDesc")}
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
                      <FormLabel>{t("forms.snLocale.core")}</FormLabel>
                      <FormDescription>
                        {t("forms.snLocale.coreDesc")}
                      </FormDescription>
                      <FormControl>
                        <div className="flex gap-2">
                          <Input
                            {...field}
                            placeholder={t("forms.snLocale.corePlaceholder")}
                            type="text"
                          />
                          {coresUrl && (
                            <Button
                              type="button"
                              variant="outline"
                              size="icon"
                              title={t("forms.snLocale.viewInSE")}
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
        </SNFormSection>

        {/* Action Footer */}
      </form>
    </Form>
  )
}
