"use client"
import { ROUTES } from "@/app/routes.const"
import { SNSiteLabelTranslations } from "@/components/sn/facet/sn.site.label.translations"
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
import type { TurLocale } from "@/models/locale/locale.model"
import type { TurSNSiteField } from "@/models/sn/sn-site-field.model"
import { TurLocaleService } from "@/services/locale/locale.service"
import { TurSNFieldService } from "@/services/sn/sn.field.service"
import { TurSNFieldTypeService } from "@/services/sn/sn.field.type.service"
import { IconAdjustmentsHorizontal, IconDeviceFloppy, IconFilter, IconId, IconLanguage, IconX } from "@tabler/icons-react"
import { GradientButton } from "@/components/ui/gradient-button"
import axios from "axios"
import { useEffect, useMemo, useState } from "react"
import {
  useForm
} from "react-hook-form"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"
import { toast } from "@viglet/viglet-design-system"

const turLocaleService = new TurLocaleService();
const turSNFieldService = new TurSNFieldService();
const turSNFieldTypeService = new TurSNFieldTypeService();
interface Props {
  snSiteId: string;
  snField: TurSNSiteField;
  isNew: boolean;
  open?: boolean;
  setOpen?: React.Dispatch<React.SetStateAction<boolean>>;
}

export const SNSiteFacetedFieldForm: React.FC<Props> = ({ snSiteId, snField, isNew }) => {
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
  const { control } = form;
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [availableLocales, setAvailableLocales] = useState<TurLocale[]>([]);
  const [labelEntries, setLabelEntries] = useState<Array<{ locale: string; label: string }>>([]);
  const urlBase = `${ROUTES.SN_INSTANCE}/${snSiteId}/field`;
  const navigate = useNavigate()

  const facetRanges = [
    { value: "DISABLED", name: t("forms.snFacetedField.disabled") },
    { value: "DAY", name: t("forms.snFacetedField.day") },
    { value: "MONTH", name: t("forms.snFacetedField.month") },
    { value: "YEAR", name: t("forms.snFacetedField.year") }
  ];
  const facetTypes = [
    { value: "DEFAULT", name: t("forms.snFacetedField.default") },
    { value: "AND", name: t("forms.snFacetedField.and") },
    { value: "OR", name: t("forms.snFacetedField.or") }
  ];
  const facetSorts = [
    { value: "DEFAULT", name: t("forms.snFacetedField.default") },
    { value: "ALPHABETICAL", name: t("forms.snFacetedField.alphabetical") },
    { value: "COUNT", name: t("forms.snFacetedField.count") }
  ];
  useEffect(() => {
    turSNFieldTypeService.query().then(() => {
      setIsLoading(false);
    });
  }, []);

  useEffect(() => {
    form.reset(normalizedField);
    const entries = (normalizedField.facetLocales ?? []).map((facetLocale) => ({
      locale: facetLocale.locale ?? "",
      label: facetLocale.label ?? "",
    }));
    setLabelEntries(entries);
  }, [normalizedField, form]);

  useEffect(() => {
    turLocaleService.query().then(setAvailableLocales);
  }, []);

  function addLabelEntry() {
    setLabelEntries((currentEntries) => [...currentEntries, { locale: "", label: "" }]);
  }

  function removeLabelEntry(index: number) {
    setLabelEntries((currentEntries) => currentEntries.filter((_, currentIndex) => currentIndex !== index));
  }

  function updateLabelEntry(index: number, key: "locale" | "label", inputValue: string) {
    setLabelEntries((currentEntries) => currentEntries.map((entry, currentIndex) => (
      currentIndex === index
        ? { ...entry, [key]: inputValue }
        : entry
    )));
  }


  async function onSubmit(snField: TurSNSiteField) {
    try {
      // Normalize all fields: ensure strings are empty strings instead of null/undefined
      const normalizedData: TurSNSiteField = {
        ...snField,
        name: snField.name ?? "",
        description: snField.description ?? "",
        defaultValue: snField.defaultValue ?? "",
        facetName: snField.facetName ?? "",
        facetLocales: labelEntries
          .filter((entry) => entry.locale.trim() && entry.label.trim())
          .map((entry) => ({
            id: "",
            locale: entry.locale.trim(),
            label: entry.label.trim(),
          })),
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
                icon={IconFilter}
                feature={t("sn.facets.facetedField")}
                description={snField.description}
              />
              <StickyPageHeader.Actions>
                <GradientButton type="submit" size="sm" loading={isLoading}>
                  <IconDeviceFloppy className="size-4" />
                  {t("forms.formActions.saveChanges")}
                </GradientButton>
                <GradientButton type="button" variant="outline" size="sm" onClick={() => navigate(urlBase)}>
                  <IconX className="size-4" />
                  {t("forms.formActions.cancel")}
                </GradientButton>
              </StickyPageHeader.Actions>
            </StickyPageHeader>
            {/* Basic Information */}
            <SectionCard variant="blue">
              <SectionCard.Header icon={IconId} title={t("forms.snFacetedField.basicInfo")} description={t("forms.snFacetedField.basicInfoDesc")} />
              <SectionCard.Content>
                {/* Identifier */}
                <FormField
                  control={form.control}
                  name="name"
                  rules={{ required: t("forms.snFacetedField.identifierRequired") }}
                  render={({ field }) => (
                    <FormItem className="w-full">
                      <FormLabel>{t("forms.snFacetedField.identifier")}</FormLabel>
                      <FormDescription>
                        {t("forms.snFacetedField.identifierDesc")}
                      </FormDescription>
                      <FormControl>
                        <Input
                          {...field}
                          readOnly
                          aria-readonly="true"
                          placeholder="Identifier"
                          type="text"
                          className="w-full cursor-not-allowed opacity-70"
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </SectionCard.Content>
            </SectionCard>

            {/* Labels Section */}
            <SectionCard variant="violet">
              <SectionCard.Header icon={IconLanguage} title={t("forms.snFacetedField.displayLabels")} description={t("forms.snFacetedField.displayLabelsDesc")} />
              <SectionCard.Content>
                <div>
                  <FormField
                    control={control}
                    name="facetName"
                    rules={{ required: true }}
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>{t("forms.snFacetedField.default")}</FormLabel>
                        <FormDescription>
                          {t("forms.snFacetedField.defaultDesc")}
                        </FormDescription>
                        <FormControl>
                          <Input
                            {...field}
                            value={field.value ?? ""}
                            placeholder="e.g., Price Range"
                            type="text"
                          />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  <SNSiteLabelTranslations
                    entries={labelEntries}
                    locales={availableLocales}
                    onAdd={addLabelEntry}
                    onUpdate={updateLabelEntry}
                    onRemove={removeLabelEntry}
                  />
                </div>
              </SectionCard.Content>
            </SectionCard>

            {/* Facet Configuration */}
            <SectionCard variant="emerald">
              <SectionCard.Header icon={IconAdjustmentsHorizontal} title={t("forms.snFacetedField.facetConfig")} description={t("forms.snFacetedField.facetConfigDesc")} />
              <SectionCard.Content>
                {/* Secondary Facet Switch */}
                <FormField
                  control={form.control}
                  name="secondaryFacet"
                  render={({ field }) => (
                    <FormItemTwoColumns className="w-full">
                      <FormItemTwoColumns.Left>
                        <FormItemTwoColumns.Label>{t("forms.snFacetedField.secondaryFacet")}</FormItemTwoColumns.Label>
                        <FormItemTwoColumns.Description>
                          {t("forms.snFacetedField.secondaryFacetDesc")}
                        </FormItemTwoColumns.Description>
                      </FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Right>
                        <GradientSwitch
                          checked={!!field.value}
                          onCheckedChange={(checked) => field.onChange(checked)}
                        />
                      </FormItemTwoColumns.Right>
                      <FormMessage />
                    </FormItemTwoColumns>
                  )}
                />
                {/* Show All Facet Items Switch */}
                <FormField
                  control={form.control}
                  name="showAllFacetItems"
                  render={({ field }) => (
                    <FormItemTwoColumns className="w-full">
                      <FormItemTwoColumns.Left>
                        <FormItemTwoColumns.Label>{t("forms.snFacetedField.showAllItems")}</FormItemTwoColumns.Label>
                        <FormItemTwoColumns.Description>
                          {t("forms.snFacetedField.showAllItemsDesc")}
                        </FormItemTwoColumns.Description>
                      </FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Right>
                        <GradientSwitch
                          checked={!!field.value}
                          onCheckedChange={(checked) => field.onChange(checked)}
                        />
                      </FormItemTwoColumns.Right>
                      <FormMessage />
                    </FormItemTwoColumns>
                  )}
                />
                {/* Facet Sort Select */}
                <FormField
                  control={form.control}
                  name="facetSort"
                  render={({ field }) => (
                    <FormItemTwoColumns className="w-full">
                      <FormItemTwoColumns.Left>
                        <FormItemTwoColumns.Label>{t("forms.snFacetedField.facetSort")}</FormItemTwoColumns.Label>
                        <FormItemTwoColumns.Description>
                          {t("forms.snFacetedField.facetSortDesc")}
                        </FormItemTwoColumns.Description>
                      </FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Right>
                        <FormControl>
                          <Select onValueChange={field.onChange} value={field.value}>
                            <SelectTrigger className="w-full min-w-45">
                              <SelectValue placeholder="Choose..." />
                            </SelectTrigger>
                            <SelectContent>
                              {facetSorts.map((facetSort) => (
                                <SelectItem key={facetSort.value} value={facetSort.value}>
                                  {facetSort.name}
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
                {/* Facet Type Select */}
                <FormField
                  control={form.control}
                  name="facetType"
                  render={({ field }) => (
                    <FormItemTwoColumns className="w-full">
                      <FormItemTwoColumns.Left>
                        <FormItemTwoColumns.Label>{t("forms.snFacetedField.operatorBetweenFacets")}</FormItemTwoColumns.Label>
                        <FormItemTwoColumns.Description>
                          {t("forms.snFacetedField.operatorBetweenFacetsDesc")}
                        </FormItemTwoColumns.Description>
                      </FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Right>
                        <FormControl>
                          <Select onValueChange={field.onChange} value={field.value}>
                            <SelectTrigger className="w-full min-w-45">
                              <SelectValue placeholder="Choose..." />
                            </SelectTrigger>
                            <SelectContent>
                              {facetTypes.map((facetType) => (
                                <SelectItem key={facetType.value} value={facetType.value}>
                                  {facetType.name}
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
                {/* Facet Item Type Select */}
                <FormField
                  control={form.control}
                  name="facetItemType"
                  render={({ field }) => (
                    <FormItemTwoColumns className="w-full">
                      <FormItemTwoColumns.Left>
                        <FormItemTwoColumns.Label>{t("forms.snFacetedField.operatorBetweenItems")}</FormItemTwoColumns.Label>
                        <FormItemTwoColumns.Description>
                          {t("forms.snFacetedField.operatorBetweenItemsDesc")}
                        </FormItemTwoColumns.Description>
                      </FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Right>
                        <FormControl>
                          <Select onValueChange={field.onChange} value={field.value}>
                            <SelectTrigger className="w-full min-w-45">
                              <SelectValue placeholder="Choose..." />
                            </SelectTrigger>
                            <SelectContent>
                              {facetTypes.map((facetType) => (
                                <SelectItem key={facetType.value} value={facetType.value}>
                                  {facetType.name}
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
                {/* Facet Range Select */}
                <FormField
                  control={form.control}
                  name="facetRange"
                  render={({ field }) => (
                    <FormItemTwoColumns className="w-full">
                      <FormItemTwoColumns.Left>
                        <FormItemTwoColumns.Label>{t("forms.snFacetedField.range")}</FormItemTwoColumns.Label>
                        <FormItemTwoColumns.Description>
                          {t("forms.snFacetedField.rangeDesc")}
                        </FormItemTwoColumns.Description>
                      </FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Right>
                        <FormControl>
                          <Select onValueChange={field.onChange} value={field.value}>
                            <SelectTrigger className="w-full min-w-45">
                              <SelectValue placeholder="Choose..." />
                            </SelectTrigger>
                            <SelectContent>
                              {facetRanges.map((facetRange) => (
                                <SelectItem key={facetRange.value} value={facetRange.value}>
                                  {facetRange.name}
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
              </SectionCard.Content>
            </SectionCard>

            {/* Action Footer */}
          </form>
        </Form>
      )}
    </>
  )
}
