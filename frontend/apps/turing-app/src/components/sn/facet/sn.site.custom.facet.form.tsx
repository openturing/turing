"use client"
import { ROUTES } from "@/app/routes.const"
import { BadgeFieldType } from "@/components/badge-field-type"
import { SNSiteLabelTranslations } from "@/components/sn/facet/sn.site.label.translations"
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
import { FormItemTwoColumns } from "@/components/ui/form-item-two-columns"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import {
  Table,
  TableBody,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import type {
  TurSNSiteCustomFacet,
  TurSNSiteCustomFacetFieldOption,
} from "@/models/sn/sn-site-custom-facet.model"
import { useSnSiteAvailableLocales } from "@/api/queries/locale.queries"
import { TurSNSiteCustomFacetService } from "@/services/sn/sn.site.custom.facet.service"
import {
  closestCenter,
  DndContext,
  type DragEndEvent,
  PointerSensor,
  useSensor,
  useSensors,
} from "@dnd-kit/core"
import {
  arrayMove,
  SortableContext,
  verticalListSortingStrategy,
} from "@dnd-kit/sortable"
import {
  IconAlertTriangle,
  IconCirclePlus,
  IconDeviceFloppy,
  IconFilter,
  IconInfoCircle,
  IconLanguage,
  IconListDetails,
  IconX,
} from "@tabler/icons-react"
import { GradientButton } from "@/components/ui/gradient-button"
import { DialogDelete } from "@/components/dialog.delete"
import { useEffect, useState } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"
import { toast } from "@viglet/viglet-design-system"
import { StickyPageHeader } from "../../sticky-page-header"
import { SectionCard } from "../../ui/section-card"
import { CustomFacetFieldChangeDialog } from "./sn.site.custom.facet.field-change-dialog"
import { CustomFacetItemRow } from "./sn.site.custom.facet.item-row"
import {
  DEFAULT_OPERATOR,
  detectOverlaps,
  FACET_NAME_PATTERN,
  facetTypes,
  formatItemSummary,
  hasFilledRangeValues,
  isBetweenOperator,
  isDateFieldType,
  normalizeItems,
  parseIsoDateValue,
  parseRangeValue,
} from "./sn.site.custom.facet.form.utils"

const turSNSiteCustomFacetService = new TurSNSiteCustomFacetService();

interface Props {
  snSiteId: string;
  value: TurSNSiteCustomFacet;
  isNew: boolean;
  onDelete?: () => void;
  open?: boolean;
  setOpen?: React.Dispatch<React.SetStateAction<boolean>>;
}

export const SNSiteCustomFacetForm: React.FC<Props> = ({ snSiteId, value, isNew, onDelete, open, setOpen }) => {
  const { t } = useTranslation();
  const form = useForm<TurSNSiteCustomFacet>({
    defaultValues: {
      ...value,
      facetType: value.facetType ?? "DEFAULT",
      facetItemType: value.facetItemType ?? "DEFAULT",
    },
  });
  const { control, watch, setValue } = form;
  const [fieldOptions, setFieldOptions] = useState<TurSNSiteCustomFacetFieldOption[]>([]);
  const { data: availableLocales } = useSnSiteAvailableLocales(snSiteId);
  const [labelEntries, setLabelEntries] = useState<Array<{ locale: string; label: string }>>([]);
  const [openFieldChangeDialog, setOpenFieldChangeDialog] = useState(false);
  const [pendingFieldExtId, setPendingFieldExtId] = useState<string | null>(null);
  const navigate = useNavigate();
  const items = watch("items") ?? [];
  const selectedFieldExtId = watch("fieldExtId") ?? value.fieldExtId;
  const selectedFieldOption = fieldOptions.find((field) => field.id === selectedFieldExtId);
  const selectedFieldType = selectedFieldOption?.type ?? value.fieldExtType;
  const selectedFieldName = selectedFieldOption?.name ?? value.fieldExtName ?? "";
  const dateField = isDateFieldType(selectedFieldType);
  const overlapPairs = detectOverlaps(items, dateField);
  const overlappingIndices = new Set<number>(
    overlapPairs.flatMap((pair) => [pair.indexA, pair.indexB]),
  );
  const sensors = useSensors(useSensor(PointerSensor));

  useEffect(() => {
    form.reset({
      ...value,
      facetType: value.facetType ?? "DEFAULT",
      facetItemType: value.facetItemType ?? "DEFAULT",
    });
    const entries = Object.entries(value.label ?? {}).map(([locale, label]) => ({
      locale,
      label,
    }));
    setLabelEntries(entries);
  }, [value, form]);

  useEffect(() => {
    turSNSiteCustomFacetService.getFieldOptions(snSiteId).then(setFieldOptions);
  }, [snSiteId]);

  function onOpenAddItem() {
    if (isNew) {
      toast.info(t("forms.snCustomFacet.saveFirstToAddItems"));
      return;
    }
    navigate(`${ROUTES.SN_INSTANCE}/${snSiteId}/facet/custom/${value.id}/item/new`);
  }

  function onOpenEditItem(index: number) {
    if (isNew || !value.id) {
      toast.info(t("forms.snCustomFacet.saveFirstToAddItems"));
      return;
    }
    navigate(`${ROUTES.SN_INSTANCE}/${snSiteId}/facet/custom/${value.id}/item/${index}`);
  }

  function onRemoveItem(index: number) {
    const updatedItems = items.filter((_, currentIndex) => currentIndex !== index);
    setValue("items", normalizeItems(updatedItems, selectedFieldType), { shouldDirty: true });
  }

  function onDragEnd(event: DragEndEvent) {
    const { active, over } = event;
    if (!over || active.id === over.id) return;
    const oldIndex = items.findIndex((item, index) => (item.id || `item-${index}`) === active.id);
    const newIndex = items.findIndex((item, index) => (item.id || `item-${index}`) === over.id);
    if (oldIndex < 0 || newIndex < 0) return;
    const reorderedItems = arrayMove(items, oldIndex, newIndex);
    setValue("items", normalizeItems(reorderedItems, selectedFieldType), { shouldDirty: true });
  }

  function applyFieldChange(newFieldExtId: string) {
    const newFieldType = fieldOptions.find((field) => field.id === newFieldExtId)?.type;
    setValue("fieldExtId", newFieldExtId, { shouldDirty: true });
    setValue("items", normalizeItems(items, newFieldType), { shouldDirty: true });
  }

  function onFieldChange(newFieldExtId: string) {
    if (newFieldExtId === selectedFieldExtId) return;
    if (hasFilledRangeValues(items)) {
      setPendingFieldExtId(newFieldExtId);
      setOpenFieldChangeDialog(true);
      return;
    }
    applyFieldChange(newFieldExtId);
  }

  function onConfirmFieldChange() {
    if (pendingFieldExtId) {
      applyFieldChange(pendingFieldExtId);
    }
    setPendingFieldExtId(null);
    setOpenFieldChangeDialog(false);
  }

  function onCancelFieldChange() {
    setPendingFieldExtId(null);
    setOpenFieldChangeDialog(false);
  }

  function addLabelEntry() {
    setLabelEntries((currentEntries) => [...currentEntries, { locale: "", label: "" }]);
  }

  function removeLabelEntry(index: number) {
    setLabelEntries((currentEntries) => currentEntries.filter((_, currentIndex) => currentIndex !== index));
  }

  function updateLabelEntry(index: number, key: "locale" | "label", inputValue: string) {
    setLabelEntries((currentEntries) =>
      currentEntries.map((entry, currentIndex) =>
        currentIndex === index ? { ...entry, [key]: inputValue } : entry,
      ),
    );
  }

  async function onSubmit(customFacet: TurSNSiteCustomFacet) {
    if (!FACET_NAME_PATTERN.test(customFacet.name ?? "")) {
      toast.error(t("forms.snCustomFacet.facetNameInvalid"));
      return;
    }

    const invalidRange = (customFacet.items ?? []).find((item) => {
      if (!isBetweenOperator(item.operator ?? DEFAULT_OPERATOR)) return false;
      if (dateField) {
        const start = parseIsoDateValue(item.rangeStartDate);
        const end = parseIsoDateValue(item.rangeEndDate);
        if (!start || !end) return false;
        return new Date(end).getTime() < new Date(start).getTime();
      }
      const start = parseRangeValue(item.rangeStart);
      const end = parseRangeValue(item.rangeEnd);
      return start !== null && end !== null && end < start;
    });

    if (invalidRange) {
      toast.error(
        t("forms.snCustomFacet.invalidRange", {
          label: invalidRange.label || "(without label)",
        }),
      );
      return;
    }

    const payload: TurSNSiteCustomFacet = {
      ...customFacet,
      facetType: customFacet.facetType ?? "DEFAULT",
      facetItemType: customFacet.facetItemType ?? "DEFAULT",
      label: labelEntries
        .filter((entry) => entry.locale.trim() && entry.label.trim())
        .reduce<Record<string, string>>((labelsMap, entry) => {
          labelsMap[entry.locale.trim()] = entry.label;
          return labelsMap;
        }, {}),
      items: normalizeItems(customFacet.items ?? [], selectedFieldType),
    };

    try {
      if (isNew) {
        const result = await turSNSiteCustomFacetService.create(snSiteId, payload);
        if (result) {
          toast.success(t("forms.common.saved", { name: payload.name, feature: "Custom Facet" }));
          navigate(`${ROUTES.SN_INSTANCE}/${snSiteId}/facet`);
        } else {
          toast.error(t("forms.common.notSaved", { name: payload.name, feature: "Custom Facet" }));
        }
      } else {
        const result = await turSNSiteCustomFacetService.update(snSiteId, payload);
        if (result) {
          toast.success(t("forms.common.updated", { name: payload.name, feature: "Custom Facet" }));
        } else {
          toast.error(t("forms.common.notUpdated", { name: payload.name, feature: "Custom Facet" }));
        }
      }
    } catch (error) {
      console.error("Form submission error", error);
      const errorMessage = error instanceof Error ? error.message : "Unknown error occurred";
      toast.error(`Failed to submit the form: ${errorMessage}`);
    }
  }

  return (
    <>
      <CustomFacetFieldChangeDialog
        open={openFieldChangeDialog}
        onOpenChange={setOpenFieldChangeDialog}
        onConfirm={onConfirmFieldChange}
        onCancel={onCancelFieldChange}
      />
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 px-4 lg:px-6 pb-8">
          <StickyPageHeader>
            <StickyPageHeader.Title
              icon={IconFilter}
              feature={t("sn.facets.customField")}
              description={value.defaultLabel ?? ""}
            />
            <StickyPageHeader.Actions>
              {onDelete && open !== undefined && setOpen && (
                <DialogDelete
                  feature={t("sn.facets.customField")}
                  name={value.name}
                  onDelete={onDelete}
                  open={open}
                  setOpen={setOpen}
                />
              )}
              <GradientButton type="submit" size="sm">
                <IconDeviceFloppy className="size-4" />
                {t("forms.formActions.saveChanges")}
              </GradientButton>
              <GradientButton
                type="button"
                variant="outline"
                size="sm"
                onClick={() => navigate(`${ROUTES.SN_INSTANCE}/${snSiteId}/facet`)}
              >
                <IconX className="size-4" />
                {t("forms.formActions.cancel")}
              </GradientButton>
            </StickyPageHeader.Actions>
          </StickyPageHeader>

          {/* Basic Information Section */}
          <SectionCard variant="blue">
            <SectionCard.Header
              icon={IconInfoCircle}
              title={t("forms.snCustomFacet.basicInfo")}
              description={t("forms.snCustomFacet.basicInfoDesc")}
            />
            <SectionCard.Content>
              <FormField
                control={control}
                name="name"
                rules={{
                  required: t("forms.snCustomFacet.identifierRequired"),
                  pattern: {
                    value: FACET_NAME_PATTERN,
                    message: t("forms.snCustomFacet.identifierFormat"),
                  },
                }}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{t("forms.snCustomFacet.basicInfo")}</FormLabel>
                    <FormDescription>{t("forms.snCustomFacet.identifierDesc")}</FormDescription>
                    <FormControl>
                      <Input
                        {...field}
                        placeholder={t("forms.snCustomFacet.identifierPlaceholder")}
                        type="text"
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
            <SectionCard.Header
              icon={IconLanguage}
              title={t("forms.snCustomFacet.displayLabels")}
              description={t("forms.snCustomFacet.displayLabelsDesc")}
            />
            <SectionCard.Content>
              <div>
                <FormField
                  control={control}
                  name="defaultLabel"
                  rules={{ required: true }}
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("forms.snCustomFacet.default")}</FormLabel>
                      <FormDescription>{t("forms.snCustomFacet.defaultDesc")}</FormDescription>
                      <FormControl>
                        <Input
                          {...field}
                          value={field.value ?? ""}
                          placeholder={t("forms.snCustomFacet.defaultPlaceholder")}
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

          {/* Facet Configuration Section */}
          <SectionCard variant="emerald">
            <SectionCard.Header
              icon={IconFilter}
              title={t("forms.snCustomFacet.facetConfig")}
              description={t("forms.snCustomFacet.facetConfigDesc")}
            />
            <SectionCard.Content>
              <FormField
                control={control}
                name="fieldExtId"
                rules={{ required: true }}
                render={({ field }) => (
                  <FormItemTwoColumns>
                    <FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Label>
                        {t("forms.snCustomFacet.selectField")}
                      </FormItemTwoColumns.Label>
                      <FormItemTwoColumns.Description>
                        {t("forms.snCustomFacet.selectFieldDesc")}
                      </FormItemTwoColumns.Description>
                    </FormItemTwoColumns.Left>
                    <FormItemTwoColumns.Right>
                      <FormControl>
                        <Select onValueChange={onFieldChange} value={field.value || ""}>
                          <SelectTrigger className="w-full">
                            <SelectValue placeholder={t("forms.snCustomFacet.selectFieldPlaceholder")} />
                          </SelectTrigger>
                          <SelectContent>
                            {fieldOptions.map((fieldOption) => (
                              <SelectItem key={fieldOption.id} value={fieldOption.id}>
                                <div className="flex items-center justify-between gap-3">
                                  <BadgeFieldType type={fieldOption.type} variation="short" />
                                  <span>{fieldOption.name}</span>
                                </div>
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                      </FormControl>
                      <FormMessage />
                    </FormItemTwoColumns.Right>
                  </FormItemTwoColumns>
                )}
              />
              <div className="space-y-6">
                <FormField
                  control={control}
                  name="facetType"
                  render={({ field }) => (
                    <FormItemTwoColumns>
                      <FormItemTwoColumns.Left>
                        <FormItemTwoColumns.Label>
                          {t("forms.snFacetedField.operatorBetweenFacets")}
                        </FormItemTwoColumns.Label>
                        <FormItemTwoColumns.Description>
                          {t("forms.snFacetedField.operatorBetweenFacetsDesc")}
                        </FormItemTwoColumns.Description>
                      </FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Right>
                        <FormControl>
                          <Select onValueChange={field.onChange} value={field.value ?? "DEFAULT"}>
                            <SelectTrigger className="w-full">
                              <SelectValue placeholder="Select facet type" />
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
                        <FormMessage />
                      </FormItemTwoColumns.Right>
                    </FormItemTwoColumns>
                  )}
                />
                <FormField
                  control={control}
                  name="facetItemType"
                  render={({ field }) => (
                    <FormItemTwoColumns>
                      <FormItemTwoColumns.Left>
                        <FormItemTwoColumns.Label>
                          {t("forms.snFacetedField.operatorBetweenItems")}
                        </FormItemTwoColumns.Label>
                        <FormItemTwoColumns.Description>
                          {t("forms.snFacetedField.operatorBetweenItemsDesc")}
                        </FormItemTwoColumns.Description>
                      </FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Right>
                        <FormControl>
                          <Select onValueChange={field.onChange} value={field.value ?? "DEFAULT"}>
                            <SelectTrigger className="w-full">
                              <SelectValue placeholder="Select item type" />
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
                        <FormMessage />
                      </FormItemTwoColumns.Right>
                    </FormItemTwoColumns>
                  )}
                />
              </div>
            </SectionCard.Content>
          </SectionCard>

          {/* Facet Items Section */}
          <SectionCard variant="amber">
            <SectionCard.Header
              icon={IconListDetails}
              title={t("forms.snCustomFacet.facetItems")}
              description={t("forms.snCustomFacet.facetItemsDesc")}
            />
            <SectionCard.Content>
              <FormItem>
                <div className="flex items-center justify-between mb-4">
                  <div className="grid gap-2">
                    <FormLabel>{t("forms.snCustomFacet.defineItems")}</FormLabel>
                    <FormDescription>{t("forms.snCustomFacet.defineItemsDesc")}</FormDescription>
                  </div>
                  <Button type="button" variant="outline" onClick={onOpenAddItem}>
                    <IconCirclePlus className="h-4 w-4 mr-2" />
                    {t("forms.snCustomFacet.addItem")}
                  </Button>
                </div>
                {overlapPairs.length > 0 && (
                  <div
                    role="alert"
                    className="mb-4 rounded-md border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900 dark:border-amber-700 dark:bg-amber-950/40 dark:text-amber-200"
                  >
                    <div className="flex items-start gap-2">
                      <IconAlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
                      <div className="space-y-1">
                        <p className="font-medium">
                          {t("forms.snCustomFacet.overlapDetectedTitle", { count: overlapPairs.length })}
                        </p>
                        <p className="text-xs opacity-90">
                          {t("forms.snCustomFacet.overlapDetectedDesc")}
                        </p>
                        <ul className="mt-2 space-y-1 text-xs">
                          {overlapPairs.map((pair) => {
                            const a = items[pair.indexA];
                            const b = items[pair.indexB];
                            const labelA = a?.label || t("forms.snCustomFacet.noLabel");
                            const labelB = b?.label || t("forms.snCustomFacet.noLabel");
                            const summaryA = formatItemSummary(a, dateField, selectedFieldName || "x");
                            const summaryB = formatItemSummary(b, dateField, selectedFieldName || "x");
                            return (
                              <li key={`${pair.indexA}-${pair.indexB}`} className="font-mono">
                                <span className="font-semibold">#{pair.indexA + 1} {labelA}</span>
                                <span className="mx-1 opacity-70">({summaryA})</span>
                                <span className="opacity-70">{t("forms.snCustomFacet.overlapsWith")}</span>
                                <span className="font-semibold"> #{pair.indexB + 1} {labelB}</span>
                                <span className="mx-1 opacity-70">({summaryB})</span>
                              </li>
                            );
                          })}
                        </ul>
                      </div>
                    </div>
                  </div>
                )}
                <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={onDragEnd}>
                  <div className="rounded-md border overflow-hidden">
                    <Table>
                      <TableHeader>
                        <TableRow className="bg-muted/50">
                          <TableHead className="w-10" />
                          <TableHead className="w-16">#</TableHead>
                          <TableHead>{t("forms.snCustomFacet.label")}</TableHead>
                          <TableHead>{t("forms.snCustomFacet.operator")}</TableHead>
                          <TableHead>{t("forms.common.condition")}</TableHead>
                          <TableHead className="w-24 text-right" />
                        </TableRow>
                      </TableHeader>
                      <TableBody>
                        <SortableContext
                          items={items.map((item, index) => item.id || `item-${index}`)}
                          strategy={verticalListSortingStrategy}
                        >
                          {items.map((item, index) => (
                            <CustomFacetItemRow
                              key={item.id || `item-${index}`}
                              item={item}
                              index={index}
                              isDateField={dateField}
                              fieldName={selectedFieldName}
                              hasOverlap={overlappingIndices.has(index)}
                              t={t}
                              onEdit={onOpenEditItem}
                              onRemove={onRemoveItem}
                            />
                          ))}
                        </SortableContext>
                      </TableBody>
                    </Table>
                  </div>
                </DndContext>
              </FormItem>
            </SectionCard.Content>
          </SectionCard>
        </form>
      </Form>
    </>
  );
};
