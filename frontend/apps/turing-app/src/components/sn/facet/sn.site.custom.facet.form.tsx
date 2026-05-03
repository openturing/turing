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
import { IconAlertTriangle, IconCirclePlus, IconDeviceFloppy, IconFilter, IconGripVertical, IconInfoCircle, IconLanguage, IconListDetails, IconPencil, IconTrash, IconX } from '@tabler/icons-react'
import { GradientButton } from "@/components/ui/gradient-button"
import { DialogDelete } from "@/components/dialog.delete"
import { useEffect, useState } from "react"
import {
  useForm
} from "react-hook-form"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"
import { toast } from "@viglet/viglet-design-system"
import { StickyPageHeader } from "../../sticky-page-header"
import { SectionCard } from "../../ui/section-card"

const turSNSiteCustomFacetService = new TurSNSiteCustomFacetService();
const turLocaleService = new TurLocaleService();

interface Props {
  snSiteId: string;
  value: TurSNSiteCustomFacet;
  isNew: boolean;
  onDelete?: () => void;
  open?: boolean;
  setOpen?: React.Dispatch<React.SetStateAction<boolean>>;
}

const FACET_NAME_PATTERN = /^[a-zA-Z0-9_-]+$/;

const DATE_FIELD_TYPE = "DATE";

const facetTypes: { name: string; value: TurSNSiteFacetFieldTypes }[] = [
  { name: "Default", value: "DEFAULT" },
  { name: "And", value: "AND" },
  { name: "Or", value: "OR" },
];

const OPERATOR_VALUES: TurSNSiteCustomFacetOperator[] = [
  "EQUAL",
  "GREATER_THAN",
  "GREATER_THAN_OR_EQUAL",
  "LESS_THAN",
  "LESS_THAN_OR_EQUAL",
  "BETWEEN_INCLUSIVE",
  "BETWEEN_EXCLUSIVE",
];

const OPERATOR_TRANSLATION_KEYS: Record<TurSNSiteCustomFacetOperator, { label: string; description: string }> = {
  EQUAL: { label: "forms.snCustomFacet.equal", description: "forms.snCustomFacet.equalDesc" },
  GREATER_THAN: { label: "forms.snCustomFacet.greaterThan", description: "forms.snCustomFacet.greaterThanDesc" },
  GREATER_THAN_OR_EQUAL: { label: "forms.snCustomFacet.greaterThanOrEqual", description: "forms.snCustomFacet.greaterThanOrEqualDesc" },
  LESS_THAN: { label: "forms.snCustomFacet.lessThan", description: "forms.snCustomFacet.lessThanDesc" },
  LESS_THAN_OR_EQUAL: { label: "forms.snCustomFacet.lessThanOrEqual", description: "forms.snCustomFacet.lessThanOrEqualDesc" },
  BETWEEN_INCLUSIVE: { label: "forms.snCustomFacet.betweenInclusive", description: "forms.snCustomFacet.betweenInclusiveDesc" },
  BETWEEN_EXCLUSIVE: { label: "forms.snCustomFacet.betweenExclusive", description: "forms.snCustomFacet.betweenExclusiveDesc" },
};

const DEFAULT_OPERATOR: TurSNSiteCustomFacetOperator = "BETWEEN_INCLUSIVE";

function isBetweenOperator(operator?: TurSNSiteCustomFacetOperator): boolean {
  return operator === "BETWEEN_INCLUSIVE" || operator === "BETWEEN_EXCLUSIVE";
}

function usesOnlyStartValue(operator?: TurSNSiteCustomFacetOperator): boolean {
  return operator === "EQUAL"
    || operator === "GREATER_THAN"
    || operator === "GREATER_THAN_OR_EQUAL";
}

function usesOnlyEndValue(operator?: TurSNSiteCustomFacetOperator): boolean {
  return operator === "LESS_THAN" || operator === "LESS_THAN_OR_EQUAL";
}

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
    const op = item.operator ?? DEFAULT_OPERATOR;
    let rangeStart = isDate ? null : parseRangeValue(item.rangeStart);
    let rangeEnd = isDate ? null : parseRangeValue(item.rangeEnd);
    let rangeStartDate = isDate ? parseIsoDateValue(item.rangeStartDate) : null;
    let rangeEndDate = isDate ? parseIsoDateValue(item.rangeEndDate) : null;

    if (usesOnlyEndValue(op)) {
      rangeStart = null;
      rangeStartDate = null;
    } else if (usesOnlyStartValue(op)) {
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

function operatorLabel(t: (key: string) => string, operator?: TurSNSiteCustomFacetOperator): string {
  const op = operator ?? DEFAULT_OPERATOR;
  return t(OPERATOR_TRANSLATION_KEYS[op].label);
}

function formatItemSummary(item: TurSNSiteCustomFacetItem, isDateField: boolean, variable: string): string {
  const op = item.operator ?? DEFAULT_OPERATOR;
  const startVal = isDateField
    ? (item.rangeStartDate ? new Date(item.rangeStartDate).toLocaleString() : "—")
    : (item.rangeStart ?? "—");
  const endVal = isDateField
    ? (item.rangeEndDate ? new Date(item.rangeEndDate).toLocaleString() : "—")
    : (item.rangeEnd ?? "—");

  switch (op) {
    case "EQUAL": return `${variable} = ${startVal}`;
    case "GREATER_THAN": return `${variable} > ${startVal}`;
    case "GREATER_THAN_OR_EQUAL": return `${variable} \u2265 ${startVal}`;
    case "LESS_THAN": return `${variable} < ${endVal}`;
    case "LESS_THAN_OR_EQUAL": return `${variable} \u2264 ${endVal}`;
    case "BETWEEN_EXCLUSIVE": return `${startVal} < ${variable} < ${endVal}`;
    case "BETWEEN_INCLUSIVE":
    default: return `${startVal} \u2264 ${variable} \u2264 ${endVal}`;
  }
}

type Interval = {
  min: number | null;
  max: number | null;
  minInclusive: boolean;
  maxInclusive: boolean;
};

function dateIsoToNumber(value: string | null | undefined): number | null {
  if (!value) return null;
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? null : parsed.getTime();
}

function itemToInterval(item: TurSNSiteCustomFacetItem, isDateField: boolean): Interval | null {
  const op = item.operator ?? DEFAULT_OPERATOR;
  const start = isDateField ? dateIsoToNumber(item.rangeStartDate) : parseRangeValue(item.rangeStart);
  const end = isDateField ? dateIsoToNumber(item.rangeEndDate) : parseRangeValue(item.rangeEnd);

  switch (op) {
    case "EQUAL":
      if (start === null) return null;
      return { min: start, max: start, minInclusive: true, maxInclusive: true };
    case "GREATER_THAN":
      if (start === null) return null;
      return { min: start, max: null, minInclusive: false, maxInclusive: false };
    case "GREATER_THAN_OR_EQUAL":
      if (start === null) return null;
      return { min: start, max: null, minInclusive: true, maxInclusive: false };
    case "LESS_THAN":
      if (end === null) return null;
      return { min: null, max: end, minInclusive: false, maxInclusive: false };
    case "LESS_THAN_OR_EQUAL":
      if (end === null) return null;
      return { min: null, max: end, minInclusive: false, maxInclusive: true };
    case "BETWEEN_INCLUSIVE":
      if (start === null || end === null) return null;
      return { min: start, max: end, minInclusive: true, maxInclusive: true };
    case "BETWEEN_EXCLUSIVE":
      if (start === null || end === null) return null;
      return { min: start, max: end, minInclusive: false, maxInclusive: false };
  }
}

function intervalsOverlap(a: Interval, b: Interval): boolean {
  if (a.max !== null && b.min !== null) {
    if (a.max < b.min) return false;
    if (a.max === b.min && (!a.maxInclusive || !b.minInclusive)) return false;
  }
  if (b.max !== null && a.min !== null) {
    if (b.max < a.min) return false;
    if (b.max === a.min && (!b.maxInclusive || !a.minInclusive)) return false;
  }
  return true;
}

type OverlapPair = { indexA: number; indexB: number };

function detectOverlaps(items: TurSNSiteCustomFacetItem[], isDateField: boolean): OverlapPair[] {
  const intervals = items.map((item) => itemToInterval(item, isDateField));
  const pairs: OverlapPair[] = [];
  for (let i = 0; i < intervals.length; i += 1) {
    const a = intervals[i];
    if (!a) continue;
    for (let j = i + 1; j < intervals.length; j += 1) {
      const b = intervals[j];
      if (!b) continue;
      if (intervalsOverlap(a, b)) {
        pairs.push({ indexA: i, indexB: j });
      }
    }
  }
  return pairs;
}

type ItemRowProps = {
  item: TurSNSiteCustomFacetItem;
  index: number;
  isDateField: boolean;
  fieldName: string;
  hasOverlap: boolean;
  t: (key: string) => string;
  onEdit: (index: number) => void;
  onRemove: (index: number) => void;
};

const ItemRow: React.FC<ItemRowProps> = ({ item, index, isDateField, fieldName, hasOverlap, t, onEdit, onRemove }) => {
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

  const overlapClass = hasOverlap
    ? "bg-amber-50 dark:bg-amber-950/30 border-l-4 border-l-amber-500"
    : "";

  return (
    <TableRow ref={setNodeRef} style={style} className={overlapClass}>
      <TableCell className="w-10">
        <button {...attributes} {...listeners} className="p-2 cursor-grab active:cursor-grabbing">
          <IconGripVertical className="h-4 w-4 text-muted-foreground" />
        </button>
      </TableCell>
      <TableCell className="w-16">
        <div className="flex items-center gap-2">
          <span>{index + 1}</span>
          {hasOverlap && (
            <IconAlertTriangle
              className="h-4 w-4 text-amber-600 dark:text-amber-400"
              aria-label={t("forms.snCustomFacet.overlapIconLabel")}
            />
          )}
        </div>
      </TableCell>
      <TableCell className="font-medium">{item.label || <span className="text-muted-foreground italic">{t("forms.snCustomFacet.noLabel")}</span>}</TableCell>
      <TableCell>
        <span className="inline-flex items-center rounded-md bg-muted px-2 py-1 text-xs font-medium">
          {operatorLabel(t, item.operator)}
        </span>
      </TableCell>
      <TableCell className="text-muted-foreground text-sm font-mono">{formatItemSummary(item, isDateField, fieldName || "x")}</TableCell>
      <TableCell className="w-24 text-right">
        <div className="flex justify-end gap-1">
          <Button type="button" variant="ghost" size="icon" onClick={() => onEdit(index)} title="Edit">
            <IconPencil className="h-4 w-4" />
          </Button>
          <Button type="button" variant="ghost" size="icon" onClick={() => onRemove(index)} title="Remove">
            <IconTrash className="h-4 w-4 text-destructive" />
          </Button>
        </div>
      </TableCell>
    </TableRow>
  );
};

export const SNSiteCustomFacetForm: React.FC<Props> = ({ snSiteId, value, isNew, onDelete, open, setOpen }) => {
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
    label: "", operator: DEFAULT_OPERATOR, rangeStart: null, rangeEnd: null, rangeStartDate: null, rangeEndDate: null,
  });
  const navigate = useNavigate()
  const items = watch("items") ?? [];
  const selectedFieldExtId = watch("fieldExtId") ?? value.fieldExtId;
  const selectedFieldOption = fieldOptions.find((field) => field.id === selectedFieldExtId);
  const selectedFieldType = selectedFieldOption?.type ?? value.fieldExtType;
  const selectedFieldName = selectedFieldOption?.name ?? value.fieldExtName ?? "";
  const dateField = isDateFieldType(selectedFieldType);
  const overlapPairs = detectOverlaps(items, dateField);
  const overlappingIndices = new Set<number>(
    overlapPairs.flatMap((pair) => [pair.indexA, pair.indexB])
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
      label: "", operator: DEFAULT_OPERATOR, rangeStart: null, rangeEnd: null, rangeStartDate: null, rangeEndDate: null,
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
                value={editingItem.operator ?? DEFAULT_OPERATOR}
                onValueChange={(v) => onUpdateEditingItem("operator", v)}
              >
                <SelectTrigger className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {OPERATOR_VALUES.map((op) => (
                    <SelectItem key={op} value={op}>
                      <div>
                        <span className="font-medium">{t(OPERATOR_TRANSLATION_KEYS[op].label)}</span>
                        <span className="ml-2 text-xs text-muted-foreground">{t(OPERATOR_TRANSLATION_KEYS[op].description)}</span>
                      </div>
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <p className="text-xs text-muted-foreground font-mono">
                {formatItemSummary(editingItem, dateField, selectedFieldName || "x")}
              </p>
            </div>
            {isBetweenOperator(editingItem.operator ?? DEFAULT_OPERATOR) && (
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <label className="text-sm font-medium">{t("forms.snCustomFacet.start")}</label>
                  <Input
                    type={dateField ? "datetime-local" : "number"}
                    step={dateField ? undefined : "any"}
                    value={dateField ? toDateTimeLocalValue(editingItem.rangeStartDate) : (editingItem.rangeStart ?? "")}
                    onChange={(e) => onUpdateEditingItem(dateField ? "rangeStartDate" : "rangeStart", e.target.value)}
                    placeholder={t("forms.snCustomFacet.startPlaceholder")}
                  />
                </div>
                <div className="space-y-2">
                  <label className="text-sm font-medium">{t("forms.snCustomFacet.end")}</label>
                  <Input
                    type={dateField ? "datetime-local" : "number"}
                    step={dateField ? undefined : "any"}
                    value={dateField ? toDateTimeLocalValue(editingItem.rangeEndDate) : (editingItem.rangeEnd ?? "")}
                    onChange={(e) => onUpdateEditingItem(dateField ? "rangeEndDate" : "rangeEnd", e.target.value)}
                    placeholder={t("forms.snCustomFacet.endPlaceholder")}
                  />
                </div>
              </div>
            )}
            {usesOnlyStartValue(editingItem.operator) && (
              <div className="space-y-2">
                <label className="text-sm font-medium">{t("forms.common.value")}</label>
                <Input
                  type={dateField ? "datetime-local" : "number"}
                  step={dateField ? undefined : "any"}
                  value={dateField ? toDateTimeLocalValue(editingItem.rangeStartDate) : (editingItem.rangeStart ?? "")}
                  onChange={(e) => onUpdateEditingItem(dateField ? "rangeStartDate" : "rangeStart", e.target.value)}
                  placeholder={editingItem.operator === "EQUAL"
                    ? t("forms.snCustomFacet.exactValuePlaceholder")
                    : t("forms.snCustomFacet.minimumValuePlaceholder")}
                />
              </div>
            )}
            {usesOnlyEndValue(editingItem.operator) && (
              <div className="space-y-2">
                <label className="text-sm font-medium">{t("forms.common.value")}</label>
                <Input
                  type={dateField ? "datetime-local" : "number"}
                  step={dateField ? undefined : "any"}
                  value={dateField ? toDateTimeLocalValue(editingItem.rangeEndDate) : (editingItem.rangeEnd ?? "")}
                  onChange={(e) => onUpdateEditingItem(dateField ? "rangeEndDate" : "rangeEnd", e.target.value)}
                  placeholder={t("forms.snCustomFacet.maximumValuePlaceholder")}
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
        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 px-4 lg:px-6 pb-8">
          <StickyPageHeader>
            <StickyPageHeader.Title
              icon={IconFilter}
              feature={t("sn.facets.customField")}
              description={value.defaultLabel ?? ""}
            />
            <StickyPageHeader.Actions>
              {onDelete && open !== undefined && setOpen && <DialogDelete feature={t("sn.facets.customField")} name={value.name} onDelete={onDelete} open={open} setOpen={setOpen} />}
              <GradientButton type="submit" size="sm">
                <IconDeviceFloppy className="size-4" />
                {t("forms.formActions.saveChanges")}
              </GradientButton>
              <GradientButton type="button" variant="outline" size="sm" onClick={() => navigate(`${ROUTES.SN_INSTANCE}/${snSiteId}/facet`)}>
                <IconX className="size-4" />
                {t("forms.formActions.cancel")}
              </GradientButton>
            </StickyPageHeader.Actions>
          </StickyPageHeader>
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
                            <ItemRow
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
  )
}
