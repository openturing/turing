"use client"
import { ROUTES } from "@/app/routes.const"
import { SNSiteLabelTranslations } from "@/components/sn/facet/sn.site.label.translations"
import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from "@/components/ui/accordion"
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form"
import { FormItemTwoColumns } from "@/components/ui/form-item-two-columns"
import { GradientButton } from "@/components/ui/gradient-button"
import { GradientSwitch } from "@/components/ui/gradient-switch"
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
import { Skeleton } from "@/components/ui/skeleton"
import type { TurLocale } from "@/models/locale/locale.model"
import type { TurSNSiteField } from "@/models/sn/sn-site-field.model"
import { TurLocaleService } from "@/services/locale/locale.service"
import { TurSNFieldService } from "@/services/sn/sn.field.service"
import { TurSNFieldTypeService } from "@/services/sn/sn.field.type.service"
import axios from "axios"
import { useEffect, useMemo, useState } from "react"
import {
  useForm
} from "react-hook-form"
import { useNavigate } from "react-router-dom"
import { toast } from "sonner"

const turLocaleService = new TurLocaleService();
const turSNFieldService = new TurSNFieldService();
const turSNFieldTypeService = new TurSNFieldTypeService();
interface Props {
  snSiteId: string;
  snField: TurSNSiteField;
  isNew: boolean;
}

export const SNSiteFacetedFieldForm: React.FC<Props> = ({ snSiteId, snField, isNew }) => {
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
    { value: "DISABLED", name: "Disabled" },
    { value: "DAY", name: "Day" },
    { value: "MONTH", name: "Month" },
    { value: "YEAR", name: "Year" }
  ];
  const facetTypes = [
    { value: "DEFAULT", name: "Default" },
    { value: "AND", name: "And" },
    { value: "OR", name: "Or" }
  ];
  const facetSorts = [
    { value: "DEFAULT", name: "Default" },
    { value: "ALPHABETICAL", name: "Alphabetical" },
    { value: "COUNT", name: "Count" }
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
          toast.success(`The ${normalizedData.name} SN Field was saved`);
          navigate(urlBase);
        }
        else {
          toast.error(`The ${normalizedData.name} SN Field was not saved`);
        }
      }
      else {
        const result = await turSNFieldService.update(snSiteId, normalizedData);
        if (result) {
          toast.success(`The ${normalizedData.name} SN Field was updated`);
        } else {
          toast.error(`The ${normalizedData.name} SN Field was not updated`);
        }
      }
    } catch (error) {
      console.error("Form submission error", error);
      if (axios.isAxiosError(error) && error.response?.status === 409) {
        toast.error("A field with this name already exists.");
        return;
      }
      toast.error("Failed to submit the form. Please try again.");
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
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-10 py-10 px-8">
            <Accordion
              type="multiple"
              defaultValue={["general", "labels", "facet"]}
              className="w-full space-y-4"
            >
              {/* Basic Information */}
              <AccordionItem value="general" className="border rounded-lg px-6">
                <AccordionTrigger className="hover:no-underline">
                  <div className="flex items-center gap-2">
                    <span className="text-lg font-semibold">Basic Information</span>
                  </div>
                </AccordionTrigger>
                <AccordionContent className="flex flex-col gap-6 pt-4">
                  {/* Identifier */}
                  <FormField
                    control={form.control}
                    name="name"
                    rules={{ required: "Identifier is required." }}
                    render={({ field }) => (
                      <FormItem className="w-full">
                        <FormLabel>Identifier</FormLabel>
                        <FormDescription>
                          Unique identifier for this field. Used for search and indexing.
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
                </AccordionContent>

              </AccordionItem>
              {/* Labels Section */}
              <AccordionItem value="labels" className="border rounded-lg px-6">
                <AccordionTrigger className="text-lg font-semibold">Display Labels</AccordionTrigger>
                <AccordionContent className="space-y-6 pt-4">
                  <div>
                    <FormField
                      control={control}
                      name="facetName"
                      rules={{ required: true }}
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>Default</FormLabel>
                          <FormDescription>
                            The name shown to users when browsing filters. Used when no language-specific label is available.
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
                </AccordionContent>
              </AccordionItem>
              {/* Facet Configuration */}
              <AccordionItem value="facet" className="border rounded-lg px-6">
                <AccordionTrigger className="hover:no-underline">
                  <div className="flex items-center gap-2">
                    <span className="text-lg font-semibold">Facet Configuration</span>
                  </div>
                </AccordionTrigger>
                <AccordionContent className="flex flex-col gap-6 pt-4">
                  {/* Secondary Facet Switch */}
                  <FormField
                    control={form.control}
                    name="secondaryFacet"
                    render={({ field }) => (
                      <FormItemTwoColumns className="w-full">
                        <FormItemTwoColumns.Left>
                          <FormItemTwoColumns.Label>Secondary Facet</FormItemTwoColumns.Label>
                          <FormItemTwoColumns.Description>
                            Classifies this field as a secondary facet.
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
                          <FormItemTwoColumns.Label>Show All Facet Items</FormItemTwoColumns.Label>
                          <FormItemTwoColumns.Description>
                            Displays all possible facet values, even those with zero results.
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
                          <FormItemTwoColumns.Label>Facet Sort</FormItemTwoColumns.Label>
                          <FormItemTwoColumns.Description>
                            Determines how facet values are ordered in the filter box.
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
                          <FormItemTwoColumns.Label>Operator between Facets</FormItemTwoColumns.Label>
                          <FormItemTwoColumns.Description>
                            Specifies how multiple facets are combined in search.
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
                          <FormItemTwoColumns.Label>Operator between Facet Items</FormItemTwoColumns.Label>
                          <FormItemTwoColumns.Description>
                            Defines how multiple selected facet values are joined.
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
                          <FormItemTwoColumns.Label>Range</FormItemTwoColumns.Label>
                          <FormItemTwoColumns.Description>
                            Enables range-based facet filtering.
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
                </AccordionContent>
              </AccordionItem>
            </Accordion>
            {/* Action Footer */}
            <div className="flex items-center justify-end gap-3 pt-6 border-t">
              <GradientButton
                type="button"
                variant="outline"
                className="w-full md:w-auto"
                onClick={() => navigate(urlBase)}
              >
                Cancel
              </GradientButton>
              <GradientButton
                type="submit"
                disabled={isLoading}
                className="w-full md:w-auto"
              >
                Save Changes
              </GradientButton>
            </div>
          </form>
        </Form>
      )}
    </>
  )
}

