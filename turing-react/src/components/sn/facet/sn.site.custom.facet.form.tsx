"use client"
import { ROUTES } from "@/app/routes.const"
import { BadgeFieldType } from "@/components/badge-field-type"
import { SNSiteLabelTranslations } from "@/components/sn/facet/sn.site.label.translations"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
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
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import type { TurLocale } from "@/models/locale/locale.model"
import type {
  TurSNSiteCustomFacet,
  TurSNSiteCustomFacetFieldOption,
  TurSNSiteCustomFacetItem,
  TurSNSiteCustomFacetOperator,
} from "@/models/sn/sn-site-custom-facet.model"
import type { TurSNSiteFacetFieldTypes } from "@/models/sn/sn-site-facet.field.type"
import { TurLocaleService } from "@/services/locale/locale.service"
import { TurSNSiteCustomFacetService } from "@/services/sn/sn.site.custom.facet.service"
import {
  closestCenter,
  DndContext,
  type DragEndEvent,
  PointerSensor,
  useSensor,
  useSensors,
} from '@dnd-kit/core'
import {
  arrayMove,
  SortableContext,
  useSortable,
  verticalListSortingStrategy,
} from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import { IconFilter, IconInfoCircle, IconLanguage, IconListDetails } from '@tabler/icons-react'
import { GripVertical, Pencil, PlusCircle, Trash2 } from 'lucide-react'
import { useEffect, useState } from "react"
import {
  useForm
} from "react-hook-form"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"
import { toast } from "sonner"
import { FormActions } from "../../ui/form-actions"
import { SectionCard } from "../../ui/section-card"

const turSNSiteCustomFacetService = new TurSNSiteCustomFacetService();
const turLocaleService = new TurLocaleService();

interface Props {
  snSiteId: string;
  value: TurSNSiteCustomFacet;
  isNew: boolean;
}

const FACET_NAME_PATTERN = /^[a-zA-Z0-9_-]+$/;

const DATE_FIELD_TYPE = "DATE";

const facetTypes: { name: string; value: TurSNSiteFacetFieldTypes }[] = [
  { name: "Default", value: "DEFAULT" },
  { name: "And", value: "AND" },
  { name: "Or", value: "OR" },
];

const operatorOptions: { name: string; value: TurSNSiteCustomFacetOperator; description: string }[] = [
  { name: "Between", value: "BETWEEN", description: "Value is within a range (start to end)" },
  { name: "Equal", value: "EQUAL", description: "Value is exactly equal to" },
  { name: "Greater than", value: "GREATER_THAN", description: "Value is greater than" },
  { name: "Less than", value: "LESS_THAN", description: "Value is less than" },
];

function isDateFieldType(fieldType?: string): boolean {
  return (fieldType ?? "").toUpperCase() === DATE_FIELD_TYPE;
}

function parseRangeValue(value: unknown): number | null {
  if (value === null || value === undefined || value === "") return null;
  const numericValue = Number.parseFloat(String(value).replace(",", "."));
  return Number.isNaN(numericValue) ? null : numericValue;
}

function parseIsoDateValue(value: unknown): string | null {
  if (value === null || value === undefined || value === "") return null;
  const parsedDate = new Date(String(value));
  return Number.isNaN(parsedDate.getTime()) ? null : parsedDate.toISOString();
}

function toDateTimeLocalValue(value: string | null | undefined): string {
  if (!value) return "";
  const parsedDate = new Date(value);
  if (Number.isNaN(parsedDate.getTime())) return "";
  const timezoneOffsetMs = parsedDate.getTimezoneOffset() * 60 * 1000;
  return new Date(parsedDate.getTime() - timezoneOffsetMs).toISOString().slice(0, 16);
}

function normalizeItems(itemsToNormalize: TurSNSiteCustomFacetItem[], fieldType?: string): TurSNSiteCustomFacetItem[] {
  const isDate = isDateFieldType(fieldType);
  return itemsToNormalize.map((item, index) => {
    const op = item.operator ?? "BETWEEN";
    let rangeStart = isDate ? null : parseRangeValue(item.rangeStart);
    let rangeEnd = isDate ? null : parseRangeValue(item.rangeEnd);
    let rangeStartDate = isDate ? parseIsoDateValue(item.rangeStartDate) : null;
    let rangeEndDate = isDate ? parseIsoDateValue(item.rangeEndDate) : null;

    if (op === "LESS_THAN") {
      rangeStart = null;
      rangeStartDate = null;
    } else if (op === "EQUAL" || op === "GREATER_THAN") {
      rangeEnd = null;
      rangeEndDate = null;
    }

    return { ...item, position: index + 1, operator: op, rangeStart, rangeEnd, rangeStartDate, rangeEndDate };
  });
}

function hasFilledRangeValues(itemsToCheck: TurSNSiteCustomFacetItem[]): boolean {
  return itemsToCheck.some((item) => {
    const hasNumberRange = item.rangeStart !== null && item.rangeStart !== undefined
      || item.rangeEnd !== null && item.rangeEnd !== undefined;
    const hasDateRange = Boolean(item.rangeStartDate) || Boolean(item.rangeEndDate);
    return hasNumberRange || hasDateRange;
  });
}

function operatorLabel(operator?: TurSNSiteCustomFacetOperator): string {
  return operatorOptions.find((o) => o.value === operator)?.name ?? "Between";
}

function formatItemSummary(item: TurSNSiteCustomFacetItem, isDateField: boolean): string {
  const op = item.operator ?? "BETWEEN";
  const startVal = isDateField
    ? (item.rangeStartDate ? new Date(item.rangeStartDate).toLocaleString() : "—")
    : (item.rangeStart ?? "—");
  const endVal = isDateField
    ? (item.rangeEndDate ? new Date(item.rangeEndDate).toLocaleString() : "—")
    : (item.rangeEnd ?? "—");

  switch (op) {
    case "EQUAL": return `= ${startVal}`;
    case "GREATER_THAN": return `> ${startVal}`;
    case "LESS_THAN": return `< ${endVal}`;
    case "BETWEEN":
    default: return `${startVal} — ${endVal}`;
  }
}

type ItemRowProps = {
  item: TurSNSiteCustomFacetItem;
  index: number;
  isDateField: boolean;
  onEdit: (index: number) => void;
  onRemove: (index: number) => void;
};

const ItemRow: React.FC<ItemRowProps> = ({ item, index, isDateField, onEdit, onRemove }) => {
  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({ id: item.id || `item-${index}` });

  const style: React.CSSProperties = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.8 : 1,
  };

  return (
    <TableRow ref={setNodeRef} style={style}>
      <TableCell className="w-10">
        <button {...attributes} {...listeners} className="p-2 cursor-grab active:cursor-grabbing">
          <GripVertical className="h-4 w-4 text-muted-foreground" />
        </button>
      </TableCell>
      <TableCell className="w-16">{index + 1}</TableCell>
      <TableCell className="font-medium">{item.label || <span className="text-muted-foreground italic">No label</span>}</TableCell>
      <TableCell>
        <span className="inline-flex items-center rounded-md bg-muted px-2 py-1 text-xs font-medium">
          {operatorLabel(item.operator)}
        </span>
      </TableCell>
      <TableCell className="text-muted-foreground text-sm">{formatItemSummary(item, isDateField)}</TableCell>
      <TableCell className="w-24 text-right">
        <div className="flex justify-end gap-1">
          <Button type="button" variant="ghost" size="icon" onClick={() => onEdit(index)} title="Edit">
            <Pencil className="h-4 w-4" />
          </Button>
          <Button type="button" variant="ghost" size="icon" onClick={() => onRemove(index)} title="Remove">
            <Trash2 className="h-4 w-4 text-destructive" />
          </Button>
        </div>
      </TableCell>
    </TableRow>
  );
};

export const SNSiteCustomFacetForm: React.FC<Props> = ({ snSiteId, value, isNew }) => {
  const { t } = useTranslation();
  const form = useForm<TurSNSiteCustomFacet>({
    defaultValues: {
      ...value,
      facetType: value.facetType ?? "DEFAULT",
      facetItemType: value.facetItemType ?? "DEFAULT",
    }
  });
  const { control, watch, setValue } = form;
  const [fieldOptions, setFieldOptions] = useState<TurSNSiteCustomFacetFieldOption[]>([]);
  const [availableLocales, setAvailableLocales] = useState<TurLocale[]>([]);
  const [labelEntries, setLabelEntries] = useState<Array<{ locale: string; label: string }>>([]);
  const [openFieldChangeDialog, setOpenFieldChangeDialog] = useState(false);
  const [pendingFieldExtId, setPendingFieldExtId] = useState<string | null>(null);
  const [itemModalOpen, setItemModalOpen] = useState(false);
  const [editingItemIndex, setEditingItemIndex] = useState<number | null>(null);
  const [editingItem, setEditingItem] = useState<TurSNSiteCustomFacetItem>({
    label: "", operator: "BETWEEN", rangeStart: null, rangeEnd: null, rangeStartDate: null, rangeEndDate: null,
  });
  const navigate = useNavigate()
  const items = watch("items") ?? [];
  const selectedFieldExtId = watch("fieldExtId") ?? value.fieldExtId;
  const selectedFieldType = fieldOptions.find((field) => field.id === selectedFieldExtId)?.type ?? value.fieldExtType;
  const dateField = isDateFieldType(selectedFieldType);
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
  }, [value, form])

  useEffect(() => {
    turSNSiteCustomFacetService.getFieldOptions(snSiteId).then(setFieldOptions);
  }, [snSiteId])

  useEffect(() => {
    turLocaleService.query().then(setAvailableLocales);
  }, [])

  function onOpenAddItem() {
    setEditingItemIndex(null);
    setEditingItem({
      label: "", operator: "BETWEEN", rangeStart: null, rangeEnd: null, rangeStartDate: null, rangeEndDate: null,
    });
    setItemModalOpen(true);
  }

  function onOpenEditItem(index: number) {
    setEditingItemIndex(index);
    setEditingItem({ ...items[index] });
    setItemModalOpen(true);
  }

  function onSaveItem() {
    const updatedItems = [...items];
    if (editingItemIndex !== null) {
      updatedItems[editingItemIndex] = editingItem;
    } else {
      updatedItems.push(editingItem);
    }
    setValue("items", normalizeItems(updatedItems, selectedFieldType), { shouldDirty: true });
    setItemModalOpen(false);
  }

  function onUpdateEditingItem(key: keyof TurSNSiteCustomFacetItem, rawValue: string) {
    setEditingItem((prev) => {
      if (key === "rangeStart" || key === "rangeEnd") {
        return { ...prev, [key]: parseRangeValue(rawValue) };
      }
      if (key === "rangeStartDate" || key === "rangeEndDate") {
        return { ...prev, [key]: parseIsoDateValue(rawValue) };
      }
      if (key === "operator") {
        return { ...prev, operator: rawValue as TurSNSiteCustomFacetOperator };
      }
      return { ...prev, [key]: rawValue };
    });
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
    if (newFieldExtId === selectedFieldExtId) {
      return;
    }

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
    setLabelEntries((currentEntries) => currentEntries.map((entry, currentIndex) => (
      currentIndex === index
        ? { ...entry, [key]: inputValue }
        : entry
    )));
  }

  async function onSubmit(customFacet: TurSNSiteCustomFacet) {
    if (!FACET_NAME_PATTERN.test(customFacet.name ?? "")) {
      toast.error(t("forms.snCustomFacet.facetNameInvalid"));
      return;
    }

    const invalidRange = (customFacet.items ?? []).find((item) => {
      if ((item.operator ?? "BETWEEN") !== "BETWEEN") return false;
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
      toast.error(t("forms.snCustomFacet.invalidRange", { label: invalidRange.label || "(without label)" }));
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
      }
      else {
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
      <Dialog open={openFieldChangeDialog} onOpenChange={setOpenFieldChangeDialog}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("forms.snCustomFacet.changeField")}</DialogTitle>
            <DialogDescription>
              {t("forms.snCustomFacet.changeFieldDesc")}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onCancelFieldChange}>
              Cancel
            </Button>
            <Button type="button" onClick={onConfirmFieldChange}>
              {t("forms.snCustomFacet.continue")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
      <Dialog open={itemModalOpen} onOpenChange={setItemModalOpen}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>{editingItemIndex !== null ? t("forms.snCustomFacet.editItem") : t("forms.snCustomFacet.addItemTitle")}</DialogTitle>
            <DialogDescription>
              {t("forms.snCustomFacet.itemDesc")}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-2">
            <div className="space-y-2">
              <label className="text-sm font-medium">{t("forms.snCustomFacet.label")}</label>
              <Input
                value={editingItem.label ?? ""}
                onChange={(e) => onUpdateEditingItem("label", e.target.value)}
                placeholder="e.g., Under $100"
              />
            </div>
            <div className="space-y-2">
              <label className="text-sm font-medium">{t("forms.snCustomFacet.operator")}</label>
              <Select
                value={editingItem.operator ?? "BETWEEN"}
                onValueChange={(v) => onUpdateEditingItem("operator", v)}
              >
                <SelectTrigger className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {operatorOptions.map((op) => (
                    <SelectItem key={op.value} value={op.value}>
                      <div>
                        <span className="font-medium">{op.name}</span>
                        <span className="ml-2 text-xs text-muted-foreground">{op.description}</span>
                      </div>
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            {(editingItem.operator ?? "BETWEEN") === "BETWEEN" && (
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <label className="text-sm font-medium">{t("forms.snCustomFacet.start")}</label>
                  <Input
                    type={dateField ? "datetime-local" : "number"}
                    step={dateField ? undefined : "any"}
                    value={dateField ? toDateTimeLocalValue(editingItem.rangeStartDate) : (editingItem.rangeStart ?? "")}
                    onChange={(e) => onUpdateEditingItem(dateField ? "rangeStartDate" : "rangeStart", e.target.value)}
                    placeholder="Start value"
                  />
                </div>
                <div className="space-y-2">
                  <label className="text-sm font-medium">{t("forms.snCustomFacet.end")}</label>
                  <Input
                    type={dateField ? "datetime-local" : "number"}
                    step={dateField ? undefined : "any"}
                    value={dateField ? toDateTimeLocalValue(editingItem.rangeEndDate) : (editingItem.rangeEnd ?? "")}
                    onChange={(e) => onUpdateEditingItem(dateField ? "rangeEndDate" : "rangeEnd", e.target.value)}
                    placeholder="End value"
                  />
                </div>
              </div>
            )}
            {(editingItem.operator === "EQUAL" || editingItem.operator === "GREATER_THAN") && (
              <div className="space-y-2">
                <label className="text-sm font-medium">{t("forms.common.value")}</label>
                <Input
                  type={dateField ? "datetime-local" : "number"}
                  step={dateField ? undefined : "any"}
                  value={dateField ? toDateTimeLocalValue(editingItem.rangeStartDate) : (editingItem.rangeStart ?? "")}
                  onChange={(e) => onUpdateEditingItem(dateField ? "rangeStartDate" : "rangeStart", e.target.value)}
                  placeholder={editingItem.operator === "EQUAL" ? "Exact value" : "Minimum value"}
                />
              </div>
            )}
            {editingItem.operator === "LESS_THAN" && (
              <div className="space-y-2">
                <label className="text-sm font-medium">{t("forms.common.value")}</label>
                <Input
                  type={dateField ? "datetime-local" : "number"}
                  step={dateField ? undefined : "any"}
                  value={dateField ? toDateTimeLocalValue(editingItem.rangeEndDate) : (editingItem.rangeEnd ?? "")}
                  onChange={(e) => onUpdateEditingItem(dateField ? "rangeEndDate" : "rangeEnd", e.target.value)}
                  placeholder="Maximum value"
                />
              </div>
            )}
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setItemModalOpen(false)}>
              Cancel
            </Button>
            <Button type="button" onClick={onSaveItem}>
              {editingItemIndex !== null ? t("forms.snCustomFacet.save") : t("forms.common.add")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 py-8 px-6">
          {/* Basic Information Section */}
          <SectionCard variant="blue">
            <SectionCard.Header icon={IconInfoCircle} title={t("forms.snCustomFacet.basicInfo")} description={t("forms.snCustomFacet.basicInfoDesc")} />
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
                    <FormDescription>
                      {t("forms.snCustomFacet.identifierDesc")}
                    </FormDescription>
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
            <SectionCard.Header icon={IconLanguage} title={t("forms.snCustomFacet.displayLabels")} description={t("forms.snCustomFacet.displayLabelsDesc")} />
            <SectionCard.Content>
              <div>
                <FormField
                  control={control}
                  name="defaultLabel"
                  rules={{ required: true }}
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("forms.snCustomFacet.default")}</FormLabel>
                      <FormDescription>
                        {t("forms.snCustomFacet.defaultDesc")}
                      </FormDescription>
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
            <SectionCard.Header icon={IconFilter} title={t("forms.snCustomFacet.facetConfig")} description={t("forms.snCustomFacet.facetConfigDesc")} />
            <SectionCard.Content>
              <FormField
                control={control}
                name="fieldExtId"
                rules={{ required: true }}
                render={({ field }) => (
                  <FormItemTwoColumns>
                    <FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Label>{t("forms.snCustomFacet.selectField")}</FormItemTwoColumns.Label>
                      <FormItemTwoColumns.Description>{t("forms.snCustomFacet.selectFieldDesc")}</FormItemTwoColumns.Description>
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
                {/* Facet Type Row */}
                <FormField
                  control={control}
                  name="facetType"
                  render={({ field }) => (
                    <FormItemTwoColumns>
                      <FormItemTwoColumns.Left>
                        <FormItemTwoColumns.Label>{t("forms.snFacetedField.operatorBetweenFacets")}</FormItemTwoColumns.Label>
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

                {/* Facet Item Type Row */}
                <FormField
                  control={control}
                  name="facetItemType"
                  render={({ field }) => (
                    <FormItemTwoColumns>
                      <FormItemTwoColumns.Left>
                        <FormItemTwoColumns.Label>{t("forms.snFacetedField.operatorBetweenItems")}</FormItemTwoColumns.Label>
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
            <SectionCard.Header icon={IconListDetails} title={t("forms.snCustomFacet.facetItems")} description={t("forms.snCustomFacet.facetItemsDesc")} />
            <SectionCard.Content>
              <FormItem>
                <div className="flex items-center justify-between mb-4">
                  <div className="grid gap-2">
                    <FormLabel>{t("forms.snCustomFacet.defineItems")}</FormLabel>
                    <FormDescription>{t("forms.snCustomFacet.defineItemsDesc")}</FormDescription>
                  </div>
                  <Button type="button" variant="outline" onClick={onOpenAddItem}>
                    <PlusCircle className="h-4 w-4 mr-2" />
                    {t("forms.snCustomFacet.addItem")}
                  </Button>
                </div>
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
                            <ItemRow
                              key={item.id || `item-${index}`}
                              item={item}
                              index={index}
                              isDateField={dateField}
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

          <FormActions>
            <FormActions.Cancel onClick={() => navigate(`${ROUTES.SN_INSTANCE}/${snSiteId}/facet`)} />
            <FormActions.Submit>{isNew ? t("forms.snCustomFacet.createFacet") : t("forms.formActions.saveChanges")}</FormActions.Submit>
          </FormActions>
        </form>
      </Form>
    </>
  )
}
