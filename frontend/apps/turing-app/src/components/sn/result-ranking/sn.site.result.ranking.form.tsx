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
import type { TurSNRankingExpression } from "@/models/sn/sn-ranking-expression.model"
import { TurSNRankingExpressionService } from "@/services/sn/sn.site.result.ranking.service"
import { IconDeviceFloppy, IconFilter, IconInfoCircle, IconNumber123, IconScale, IconX } from "@tabler/icons-react"
import { GradientButton } from "@/components/ui/gradient-button"
import { DialogDelete } from "@/components/dialog.delete"
import React, { useEffect } from "react"
import {
  useForm
} from "react-hook-form"
import { useTranslation } from "react-i18next"
import { Link, useNavigate } from "react-router-dom"
import { toast } from "@viglet/viglet-design-system"
import { StickyPageHeader } from "../../sticky-page-header"
import { BentoHero, BentoScrollSaveBar } from "@/components/bento"
import { SNFormSection, type SNFormChrome } from "@/components/sn/sn-form-section"
import { Slider } from "../../ui/slider"
import { DynamicResultRankingFields } from "./dynamic-result-ranking-field"
const turSNRankingExpressionService = new TurSNRankingExpressionService();
interface Props {
  snSiteId: string
  value: TurSNRankingExpression;
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

export const SNSiteResultRankingForm: React.FC<Props> = ({ snSiteId, value, isNew, onDelete, open, setOpen, baseRoute = ROUTES.SN_INSTANCE, chrome = "console" }) => {
  const { t } = useTranslation();
  const form = useForm<TurSNRankingExpression>({
    defaultValues: value
  });
  const { control, register, formState: { errors } } = form;
  const [slideValue, setSlideValue] = React.useState([4]);
  const urlBase = `${baseRoute}/${snSiteId}/result-ranking`;;
  const navigate = useNavigate()
  useEffect(() => {
    const nextValue = isNew
      ? { ...value, weight: value.weight ?? 4 }
      : value;

    form.reset(nextValue);
    setSlideValue([nextValue.weight ?? 4]);
  }, [value, isNew]);


  async function onSubmit(snRankingExpression: TurSNRankingExpression) {
    try {
      if (isNew) {
        const result = await turSNRankingExpressionService.create(snSiteId, snRankingExpression);
        if (result) {
          toast.success(t("forms.snResultRanking.created", { name: snRankingExpression.name }));
          navigate(urlBase);
        } else {
          toast.error(t("forms.snResultRanking.createFailed"));
        }
      }
      else {
        const result = await turSNRankingExpressionService.update(snSiteId, snRankingExpression);
        if (result) {
          toast.success(t("forms.snResultRanking.updated", { name: snRankingExpression.name }));
        } else {
          toast.error(t("forms.snResultRanking.updateFailed"));
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
      {onDelete && open !== undefined && setOpen && <DialogDelete feature={t("sn.resultRanking.title")} name={value.name} onDelete={onDelete} open={open} setOpen={setOpen} />}
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
            eyebrow={<Link to={urlBase} className="hover:text-foreground">{t("sn.resultRanking.title")}</Link>}
            leading={
              <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-emerald-500 to-teal-600 text-white shadow-md">
                <IconNumber123 size={24} />
              </span>
            }
            title={value.name || t("sn.resultRanking.title")}
            subtitle={t("sn.resultRanking.description")}
            trailing={<div className="bento-fade-out flex shrink-0 items-center gap-2">{actions}</div>}
          />
          <BentoScrollSaveBar onCancel={() => navigate(urlBase)} />
          </>
        ) : (
          <StickyPageHeader>
            <StickyPageHeader.Title
              icon={IconNumber123}
              feature={t("sn.resultRanking.title")}
              description={t("sn.resultRanking.description")}
            />
            <StickyPageHeader.Actions>{actions}</StickyPageHeader.Actions>
          </StickyPageHeader>
        )}
            {/* General Information Section */}
            <SNFormSection chrome={chrome} icon={IconInfoCircle} tone="blue" title={t("forms.common.generalInfo")} description={t("forms.snResultRanking.generalDesc")}>
                <FormField
                  control={form.control}
                  name="name"
                  rules={{ required: t("forms.snResultRanking.nameRequired") }}
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("forms.common.name")}</FormLabel>
                      <FormDescription>
                        {t("forms.snResultRanking.nameDesc")}
                      </FormDescription>
                      <FormControl>
                        <Input
                          {...field}
                          placeholder={t("forms.snResultRanking.namePlaceholder")}
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
                      <FormControl>
                        <SmartDescription
                          value={field.value}
                          onChange={field.onChange}
                          placeholder={t("forms.snResultRanking.descPlaceholder")}
                          maxLength={500}
                          title={form.watch("name")}
                          entityType="Ranking Expression"
                          enableMetaPrompt
                        >
                          <SmartDescription.Label>{t("forms.common.description")}</SmartDescription.Label>
                          <SmartDescription.Description>
                            {t("forms.snResultRanking.descDesc")}
                          </SmartDescription.Description>
                        </SmartDescription>
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
            </SNFormSection>

            {/* Ranking Conditions Section */}
            <SNFormSection chrome={chrome} icon={IconFilter} tone="violet" title={t("forms.snResultRanking.conditions")} description={t("forms.snResultRanking.conditionsDesc")}>
                <FormItem>
                  <FormLabel>
                    {t("forms.snResultRanking.contentFilter")} <span className="text-destructive">*</span>
                  </FormLabel>
                  <FormDescription>
                    {t("forms.snResultRanking.contentFilterDesc")}
                  </FormDescription>
                  <FormControl>
                    <DynamicResultRankingFields
                      fieldName="turSNRankingConditions"
                      control={control}
                      register={register}
                      snSiteId={snSiteId}
                      errors={errors}
                    />
                  </FormControl>
                </FormItem>
            </SNFormSection>

            {/* Ranking Weight Section */}
            <SNFormSection chrome={chrome} icon={IconScale} tone="emerald" title={t("forms.snResultRanking.weight")} description={t("forms.snResultRanking.weightDesc")}>
                <FormField
                  control={form.control}
                  name="weight"
                  render={({ field }) => (
                    <FormItem>
                      <div className="flex flex-row justify-between items-center w-full">
                        <div className="flex flex-col">
                          <FormLabel>{t("forms.snResultRanking.weightAdjustment")}</FormLabel>
                          <FormDescription>
                            {t("forms.snResultRanking.weightAdjustmentDesc")}
                          </FormDescription>
                        </div>
                        <div className="flex items-center gap-4 min-w-45">
                          <Slider
                            id="weight-slider"
                            value={[field.value ?? slideValue[0]]}
                            onValueChange={(value) => {
                              setSlideValue(value)
                              field.onChange(value[0])
                            }}
                            max={10}
                            min={0}
                            step={1}
                          />
                          <span className="w-10 text-right font-medium">
                            +{slideValue[0]}
                          </span>
                        </div>
                      </div>
                      <FormMessage />
                    </FormItem>
                  )}
                />
            </SNFormSection>

          {/* Action Footer */}
        </form>
    </Form>
  )
}
