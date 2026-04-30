"use client"
import { ROUTES } from "@/app/routes.const"
import { BadgeFieldType } from "@/components/badge-field-type"
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
import { FormItemTwoColumns } from "@/components/ui/form-item-two-columns"
import { GradientSwitch } from "@/components/ui/gradient-switch"
import {
  Input
} from "@/components/ui/input"
import { SectionCard } from "@/components/ui/section-card"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import { Skeleton } from "@/components/ui/skeleton"
import { SmartDescription } from "@/components/ui/smart-description"
import { useGlobalDecimalSeparator } from "@/hooks/use-global-decimal-separator"
import type { TurSNFieldType } from "@/models/sn/sn-field-type.model"
import type { TurSNSiteField } from "@/models/sn/sn-site-field.model"
import { TurSNFieldService } from "@/services/sn/sn.field.service"
import { TurSNFieldTypeService } from "@/services/sn/sn.field.type.service"
import { IconAlignBoxCenterStretch, IconDeviceFloppy, IconSettings, IconX } from "@tabler/icons-react"
import { GradientButton } from "@/components/ui/gradient-button"
import { DialogDelete } from "@/components/dialog.delete"
import axios from "axios"
import { useEffect, useMemo, useState } from "react"
import {
  useForm
} from "react-hook-form"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"
import { toast } from "@viglet/viglet-design-system"
const turSNFieldService = new TurSNFieldService();
const turSNFieldTypeService = new TurSNFieldTypeService();
interface Props {
  snSiteId: string;
  snField: TurSNSiteField;
  isNew: boolean;
  onDelete?: () => void;
  open?: boolean;
  setOpen?: React.Dispatch<React.SetStateAction<boolean>>;
}

export const SNSiteFieldForm: React.FC<Props> = ({ snSiteId, snField, isNew, onDelete, open, setOpen }) => {
  const { t } = useTranslation();
  const normalizedField = useMemo(() => ({
    ...snField,
    name: snField.name ?? "",
    description: snField.description ?? "",
    defaultValue: snField.defaultValue ?? "",
    facetName: snField.facetName ?? "",
    multiValued: snField.multiValued ?? 0,
    hl: snField.hl ?? 0,
    mlt: snField.mlt ?? 0,
    enabled: snField.enabled ?? 0,
    required: snField.required ?? 0,
    facet: snField.facet ?? 0,
    secondaryFacet: snField.secondaryFacet ?? 0,
    showAllFacetItems: snField.showAllFacetItems ?? 0,
    facetSort: snField.facetSort ?? "DEFAULT",
    facetType: snField.facetType ?? "DEFAULT",
    facetItemType: snField.facetItemType ?? "DEFAULT",
    facetRange: snField.facetRange ?? "DISABLED"
  } as TurSNSiteField), [snField]);

  const form = useForm<TurSNSiteField>({
    defaultValues: normalizedField
  });
  const [snFieldTypes, setSnFieldTypes] = useState<TurSNFieldType[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const {
    decimalSymbol,
    normalizeCurrencyString,
    normalizeDecimalString,
  } = useGlobalDecimalSeparator();
  const urlBase = `${ROUTES.SN_INSTANCE}/${snSiteId}/field`;
  const navigate = useNavigate()
  const selectedFieldType = form.watch("type");
  const isDecimalType = selectedFieldType === "FLOAT" || selectedFieldType === "DOUBLE";
  const isCurrencyType = selectedFieldType === "CURRENCY";

  const defaultValuePlaceholder = (() => {
    if (isCurrencyType) {
      return `e.g. 150${decimalSymbol}75,BRL`;
    }
    if (isDecimalType) {
      return `e.g. 150${decimalSymbol}75`;
    }
    return "Default value";
  })();
  useEffect(() => {
    turSNFieldTypeService.query().then((types) => {
      setSnFieldTypes(types);
      setIsLoading(false);
    });
  }, []);

  useEffect(() => {
    form.reset(normalizedField);
  }, [normalizedField, form]);


  async function onSubmit(snField: TurSNSiteField) {
    setIsSubmitting(true);
    try {
      // Normalize all fields: ensure strings are empty strings instead of null/undefined
      const normalizedData: TurSNSiteField = {
        ...snField,
        name: snField.name ?? "",
        description: snField.description ?? "",
        defaultValue: snField.defaultValue ?? "",
        facetName: snField.facetName ?? "",
        multiValued: snField.multiValued ?? 0,
        hl: snField.hl ?? 0,
        mlt: snField.mlt ?? 0,
        enabled: snField.enabled ?? 0,
        required: snField.required ?? 0,
        facet: snField.facet ?? 0,
      };

      if (isNew) {

        const result = await turSNFieldService.create(snSiteId, normalizedData);
        if (result) {
          toast.success(t("forms.common.saved", { name: normalizedData.name, feature: "SN Field" }));
          navigate(urlBase);
        }
        else {
          toast.error(t("forms.common.notSaved", { name: normalizedData.name, feature: "SN Field" }));
        }
      }
      else {
        const result = await turSNFieldService.update(snSiteId, normalizedData);
        if (result) {
          toast.success(t("forms.common.updated", { name: normalizedData.name, feature: "SN Field" }));
        } else {
          toast.error(t("forms.common.notUpdated", { name: normalizedData.name, feature: "SN Field" }));
        }
      }
    } catch (error) {
      console.error("Form submission error", error);
      if (axios.isAxiosError(error) && error.response?.status === 409) {
        toast.error(t("forms.snField.fieldExists"));
        return;
      }
      toast.error(t("forms.common.formSubmitFailed"));
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <>
      {isLoading ? (
        <div className="space-y-8 py-8 px-6">
          <div className="space-y-3">
            <Skeleton className="h-6 w-20" />
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-4 w-1/3" />
          </div>
          <div className="space-y-3">
            <Skeleton className="h-6 w-24" />
            <Skeleton className="h-20 w-full" />
            <Skeleton className="h-4 w-1/2" />
          </div>
          <div className="space-y-3">
            <Skeleton className="h-6 w-16" />
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-4 w-1/3" />
          </div>
          <div className="flex gap-4">
            <Skeleton className="h-6 w-24" />
            <Skeleton className="h-6 w-10" />
          </div>
          <Skeleton className="h-10 w-20" />
        </div>
      ) : (
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 px-4 lg:px-6 pb-8">
            <StickyPageHeader>
              <StickyPageHeader.Title
                icon={IconAlignBoxCenterStretch}
                feature={t("sn.fields.feature")}
                description={snField.description}
              />
              <StickyPageHeader.Actions>
                {onDelete && open !== undefined && setOpen && <DialogDelete feature={t("sn.fields.feature")} name={snField.name} onDelete={onDelete} open={open} setOpen={setOpen} />}
                <GradientButton type="submit" size="sm" loading={isSubmitting} disabled={isSubmitting}>
                  <IconDeviceFloppy className="size-4" />
                  {t("forms.formActions.saveChanges")}
                </GradientButton>
                <GradientButton type="button" variant="outline" size="sm" disabled={isSubmitting} onClick={() => navigate(urlBase)}>
                  <IconX className="size-4" />
                  {t("forms.formActions.cancel")}
                </GradientButton>
              </StickyPageHeader.Actions>
            </StickyPageHeader>
            {/* General Configuration */}
            <SectionCard variant="blue">
              <SectionCard.Header icon={IconSettings} title={t("forms.snField.generalConfig")} description={t("forms.snField.generalConfigDesc")} />
              <SectionCard.Content>
                {/* Name */}
                <FormField
                  control={form.control}
                  name="name"
                  rules={{ required: t("forms.snField.nameRequired") }}
                  render={({ field }) => (
                    <FormItem className="w-full">
                      <FormLabel>{t("forms.common.name")}</FormLabel>
                      <FormDescription>
                        {t("forms.snField.nameDesc")}
                      </FormDescription>
                      <FormControl>
                        <Input {...field} placeholder="Name" type="text" className="w-full" />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                {/* Type */}
                <FormField
                  control={form.control}
                  name="type"
                  rules={{ required: t("forms.snField.typeRequired") }}
                  render={({ field }) => (
                    <FormItemTwoColumns className="w-full">
                      <FormItemTwoColumns.Left>
                        <FormItemTwoColumns.Label>{t("forms.snField.type")}</FormItemTwoColumns.Label>
                        <FormItemTwoColumns.Description>
                          {t("forms.snField.typeDesc")}
                        </FormItemTwoColumns.Description>
                      </FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Right>
                        <FormControl>
                          <Select onValueChange={field.onChange} value={field.value}>
                            <SelectTrigger className="w-full min-w-45">
                              <SelectValue placeholder="Choose..." />
                            </SelectTrigger>
                            <SelectContent>
                              {snFieldTypes.map((snFieldType) => (
                                <SelectItem key={snFieldType.id} value={snFieldType.id}>
                                  <div className="flex w-full items-center justify-between gap-3">
                                    <BadgeFieldType type={snFieldType.id} variation="short" />
                                    <span>{snFieldType.name}</span>
                                  </div>
                                </SelectItem>
                              ))}
                            </SelectContent>
                          </Select>
                        </FormControl>
                      </FormItemTwoColumns.Right>
                      <FormMessage />
                    </FormItemTwoColumns>
                  )}
                />
                {/* Description */}
                <FormField
                  control={form.control}
                  name="description"
                  render={({ field }) => (
                    <FormItem className="w-full">
                      <FormControl>
                        <SmartDescription
                          value={field.value}
                          onChange={field.onChange}
                          placeholder="Description"
                          className="w-full"
                          maxLength={500}
                          title={form.watch("name")}
                          entityType="Search Field"
                          enableMetaPrompt
                        >
                          <SmartDescription.Label>Description</SmartDescription.Label>
                          <SmartDescription.Description>
                            Brief explanation of the field's purpose or usage.
                          </SmartDescription.Description>
                        </SmartDescription>
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                {/* Default Value */}
                <FormField
                  control={form.control}
                  name="defaultValue"
                  render={({ field }) => (
                    <FormItem className="w-full">
                      <FormLabel>{t("forms.snField.defaultValue")}</FormLabel>
                      <FormDescription>
                        {t("forms.snField.defaultValueDesc")}
                        {isDecimalType
                          ? ` Use ${decimalSymbol} as decimal separator (example: 150${decimalSymbol}75).`
                          : null}
                        {isCurrencyType
                          ? ` Currency format: amount,ISO-4217 (example: 150${decimalSymbol}75,BRL).`
                          : null}
                      </FormDescription>
                      <FormControl>
                        <Input
                          {...field}
                          placeholder={defaultValuePlaceholder}
                          type="text"
                          className="w-full"
                          onBlur={(event) => {
                            const rawValue = event.target.value ?? "";

                            if (isDecimalType) {
                              field.onChange(normalizeDecimalString(rawValue));
                              return;
                            }

                            if (isCurrencyType) {
                              field.onChange(normalizeCurrencyString(rawValue));
                            }
                          }}
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                {/* Multi Value Switch */}
                <FormField
                  control={form.control}
                  name="multiValued"
                  render={({ field }) => (
                    <FormItemTwoColumns className="w-full">
                      <FormItemTwoColumns.Left>
                        <FormItemTwoColumns.Label>{t("forms.snField.multiValue")}</FormItemTwoColumns.Label>
                        <FormItemTwoColumns.Description>
                          {t("forms.snField.multiValueDesc")}
                        </FormItemTwoColumns.Description>
                      </FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Right>
                        <GradientSwitch
                          checked={field.value === 1}
                          onCheckedChange={(checked) => field.onChange(checked ? 1 : 0)}
                        />
                      </FormItemTwoColumns.Right>
                      <FormMessage />
                    </FormItemTwoColumns>
                  )}
                />
                {/* Highlighting Switch */}
                <FormField
                  control={form.control}
                  name="hl"
                  render={({ field }) => (
                    <FormItemTwoColumns className="w-full">
                      <FormItemTwoColumns.Left>
                        <FormItemTwoColumns.Label>{t("forms.snField.highlightingLabel")}</FormItemTwoColumns.Label>
                        <FormItemTwoColumns.Description>
                          {t("forms.snField.highlightingDesc")}
                        </FormItemTwoColumns.Description>
                      </FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Right>
                        <GradientSwitch
                          checked={field.value === 1}
                          onCheckedChange={(checked) => field.onChange(checked ? 1 : 0)}
                        />
                      </FormItemTwoColumns.Right>
                      <FormMessage />
                    </FormItemTwoColumns>
                  )}
                />
                {/* MLT Switch */}
                <FormField
                  control={form.control}
                  name="mlt"
                  render={({ field }) => (
                    <FormItemTwoColumns className="w-full">
                      <FormItemTwoColumns.Left>
                        <FormItemTwoColumns.Label>{t("forms.snField.mlt")}</FormItemTwoColumns.Label>
                        <FormItemTwoColumns.Description>
                          {t("forms.snField.mltDesc")}
                        </FormItemTwoColumns.Description>
                      </FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Right>
                        <GradientSwitch
                          checked={field.value === 1}
                          onCheckedChange={(checked) => field.onChange(checked ? 1 : 0)}
                        />
                      </FormItemTwoColumns.Right>
                      <FormMessage />
                    </FormItemTwoColumns>
                  )}
                />
                {/* Enabled Switch */}
                <FormField
                  control={form.control}
                  name="enabled"
                  render={({ field }) => (
                    <FormItemTwoColumns className="w-full">
                      <FormItemTwoColumns.Left>
                        <FormItemTwoColumns.Label>{t("forms.common.enabled")}</FormItemTwoColumns.Label>
                        <FormItemTwoColumns.Description>
                          {t("forms.snField.enabledDesc")}
                        </FormItemTwoColumns.Description>
                      </FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Right>
                        <GradientSwitch
                          checked={field.value === 1}
                          onCheckedChange={(checked) => field.onChange(checked ? 1 : 0)}
                        />
                      </FormItemTwoColumns.Right>
                      <FormMessage />
                    </FormItemTwoColumns>
                  )}
                />
                {/* Required Switch */}
                <FormField
                  control={form.control}
                  name="required"
                  render={({ field }) => (
                    <FormItemTwoColumns className="w-full">
                      <FormItemTwoColumns.Left>
                        <FormItemTwoColumns.Label>{t("forms.snField.required")}</FormItemTwoColumns.Label>
                        <FormItemTwoColumns.Description>
                          {t("forms.snField.requiredDesc")}
                        </FormItemTwoColumns.Description>
                      </FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Right>
                        <GradientSwitch
                          checked={field.value === 1}
                          onCheckedChange={(checked) => field.onChange(checked ? 1 : 0)}
                        />
                      </FormItemTwoColumns.Right>
                      <FormMessage />
                    </FormItemTwoColumns>
                  )}
                />
                {/* Facet Switch */}
                <FormField
                  control={form.control}
                  name="facet"
                  render={({ field }) => (
                    <FormItemTwoColumns className="w-full">
                      <FormItemTwoColumns.Left>
                        <FormItemTwoColumns.Label>{t("forms.snField.facet")}</FormItemTwoColumns.Label>
                        <FormItemTwoColumns.Description>
                          {t("forms.snField.facetDesc")}
                        </FormItemTwoColumns.Description>
                      </FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Right>
                        <GradientSwitch
                          checked={field.value === 1}
                          onCheckedChange={(checked) => field.onChange(checked ? 1 : 0)}
                        />
                      </FormItemTwoColumns.Right>
                      <FormMessage />
                    </FormItemTwoColumns>
                  )}
                />
              </SectionCard.Content>
            </SectionCard>
            {/* Action Footer */}
          </form>
        </Form>
      )}
    </>
  )
}
