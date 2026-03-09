"use client"
import { ROUTES } from "@/app/routes.const"
import { BadgeFieldType } from "@/components/badge-field-type"
import { SNSiteLabelTranslations } from "@/components/sn/facet/sn.site.label.translations"
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from "@/components/ui/accordion"
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
import { GripVertical, PlusCircle } from 'lucide-react'
import { useEffect, useState } from "react"
import {
  useForm
} from "react-hook-form"
import { useNavigate } from "react-router-dom"
import { toast } from "sonner"
import { GradientButton } from "../../ui/gradient-button"

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
  const dateField = isDateFieldType(fieldType);
  return itemsToNormalize.map((item, index) => ({
    ...item,
    position: index + 1,
    rangeStart: dateField ? null : parseRangeValue(item.rangeStart),
    rangeEnd: dateField ? null : parseRangeValue(item.rangeEnd),
    rangeStartDate: dateField ? parseIsoDateValue(item.rangeStartDate) : null,
    rangeEndDate: dateField ? parseIsoDateValue(item.rangeEndDate) : null,
  }));
}

function hasFilledRangeValues(itemsToCheck: TurSNSiteCustomFacetItem[]): boolean {
  return itemsToCheck.some((item) => {
    const hasNumberRange = item.rangeStart !== null && item.rangeStart !== undefined
      || item.rangeEnd !== null && item.rangeEnd !== undefined;
    const hasDateRange = Boolean(item.rangeStartDate) || Boolean(item.rangeEndDate);
    return hasNumberRange || hasDateRange;
  });
}

type ItemRowProps = {
  item: TurSNSiteCustomFacetItem;
  index: number;
  isDateField: boolean;
  onUpdate: (index: number, key: keyof TurSNSiteCustomFacetItem, value: string) => void;
  onRemove: (index: number) => void;
};

const ItemRow: React.FC<ItemRowProps> = ({ item, index, isDateField, onUpdate, onRemove }) => {
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
      <TableCell>
        <Input
          value={item.label ?? ""}
          onChange={(event) => onUpdate(index, "label", event.target.value)}
          placeholder="Item label"
        />
      </TableCell>
      <TableCell>
        <Input
          type={isDateField ? "datetime-local" : "number"}
          step={isDateField ? undefined : "any"}
          value={isDateField ? toDateTimeLocalValue(item.rangeStartDate) : (item.rangeStart ?? "")}
          onChange={(event) => onUpdate(index, isDateField ? "rangeStartDate" : "rangeStart", event.target.value)}
          placeholder="Start"
        />
      </TableCell>
      <TableCell>
        <Input
          type={isDateField ? "datetime-local" : "number"}
          step={isDateField ? undefined : "any"}
          value={isDateField ? toDateTimeLocalValue(item.rangeEndDate) : (item.rangeEnd ?? "")}
          onChange={(event) => onUpdate(index, isDateField ? "rangeEndDate" : "rangeEnd", event.target.value)}
          placeholder="End"
        />
      </TableCell>
      <TableCell className="w-24 text-right">
        <Button type="button" variant="outline" onClick={() => onRemove(index)}>Remove</Button>
      </TableCell>
    </TableRow>
  );
};

export const SNSiteCustomFacetForm: React.FC<Props> = ({ snSiteId, value, isNew }) => {
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

  function onItemUpdate(index: number, key: keyof TurSNSiteCustomFacetItem, rawValue: string) {
    const updatedItems = [...items];
    if (key === "rangeStart" || key === "rangeEnd") {
      updatedItems[index] = {
        ...updatedItems[index],
        [key]: parseRangeValue(rawValue),
      };
    } else if (key === "rangeStartDate" || key === "rangeEndDate") {
      updatedItems[index] = {
        ...updatedItems[index],
        [key]: parseIsoDateValue(rawValue),
      };
    } else {
      updatedItems[index] = {
        ...updatedItems[index],
        [key]: rawValue,
      };
    }
    setValue("items", normalizeItems(updatedItems, selectedFieldType), { shouldDirty: true });
  }

  function onAddItem() {
    const updatedItems = [...items, { label: "", rangeStart: null, rangeEnd: null, rangeStartDate: null, rangeEndDate: null }];
    setValue("items", normalizeItems(updatedItems, selectedFieldType), { shouldDirty: true });
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
      toast.error("Facet Name must contain only letters, numbers, underscore (_) or hyphen (-).");
      return;
    }

    const invalidRange = (customFacet.items ?? []).find((item) => {
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
      toast.error(`Invalid range on item "${invalidRange.label || "(without label)"}": Range End must be greater than or equal to Range Start.`);
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
          toast.success(`The ${payload.name} Custom Facet was saved`);
          navigate(`${ROUTES.SN_INSTANCE}/${snSiteId}/custom-facet`);
        } else {
          toast.error(`The ${payload.name} Custom Facet was not saved`);
        }
      }
      else {
        const result = await turSNSiteCustomFacetService.update(snSiteId, payload);
        if (result) {
          toast.success(`The ${payload.name} Custom Facet was updated`);
        } else {
          toast.error(`The ${payload.name} Custom Facet was not updated`);
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
            <DialogTitle>Change field?</DialogTitle>
            <DialogDescription>
              There are range values already filled in this custom facet. If you continue, current range values will be lost.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onCancelFieldChange}>
              Cancel
            </Button>
            <Button type="button" onClick={onConfirmFieldChange}>
              Continue
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-10 py-10 px-8">
          <Accordion type="multiple" defaultValue={["basic-info", "labels", "facet-config", "facet-items"]} className="space-y-4">
            {/* Basic Information Section */}
            <AccordionItem value="basic-info" className="border rounded-lg px-6">
              <AccordionTrigger className="text-lg font-semibold">Basic Information</AccordionTrigger>
              <AccordionContent className="space-y-6 pt-4">
                <FormField
                  control={control}
                  name="name"
                  rules={{
                    required: "Please enter an identifier.",
                    pattern: {
                      value: FACET_NAME_PATTERN,
                      message: "Use only letters, numbers, underscores (_), or hyphens (-).",
                    },
                  }}
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Identifier</FormLabel>
                      <FormDescription>
                        Give your facet a unique identifier using lowercase letters, numbers, underscores, or hyphens. This helps organize your search filters.
                      </FormDescription>
                      <FormControl>
                        <Input
                          {...field}
                          placeholder="e.g., price_range"
                          type="text"
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
                    name="defaultLabel"
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

            {/* Facet Configuration Section */}
            <AccordionItem value="facet-config" className="border rounded-lg px-6">
              <AccordionTrigger className="text-lg font-semibold">Facet Configuration</AccordionTrigger>
              <AccordionContent className="space-y-6 pt-4">
                <FormField
                  control={control}
                  name="fieldExtId"
                  rules={{ required: true }}
                  render={({ field }) => (
                    <FormItemTwoColumns>
                      <FormItemTwoColumns.Left>
                        <FormItemTwoColumns.Label>Select a Field</FormItemTwoColumns.Label>
                        <FormItemTwoColumns.Description >Choose the field that will serve as the foundation for this custom facet.</FormItemTwoColumns.Description>
                      </FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Right>
                        <FormControl>
                          <Select onValueChange={onFieldChange} value={field.value || ""}>
                            <SelectTrigger className="w-full">
                              <SelectValue placeholder="Select a field" />
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
                          <FormItemTwoColumns.Label>Operator between Facets</FormItemTwoColumns.Label>
                          <FormItemTwoColumns.Description >
                            Specifies how multiple facets are combined in search.
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
                          <FormItemTwoColumns.Label>Operator between Facet Items</FormItemTwoColumns.Label>
                          <FormItemTwoColumns.Description >
                            Defines how multiple selected facet values are joined.
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
              </AccordionContent>
            </AccordionItem>

            {/* Facet Items Section */}
            <AccordionItem value="facet-items" className="border rounded-lg px-6">
              <AccordionTrigger className="text-lg font-semibold">Facet Range Items</AccordionTrigger>
              <AccordionContent className="space-y-6 pt-4">
                <FormItem>
                  <div className="flex items-center justify-between mb-4">
                    <div className="grid gap-2">
                      <FormLabel>Define Your Ranges</FormLabel>
                      <FormDescription>Create custom range buckets for this filter. Drag items to reorder them—they'll appear in this order to users.</FormDescription>
                    </div>
                    <Button type="button" variant="outline" onClick={onAddItem}>
                      <PlusCircle className="h-4 w-4 mr-2" />
                      Add Item
                    </Button>
                  </div>
                  <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={onDragEnd}>
                    <div className="rounded-md border overflow-hidden">
                      <Table>
                        <TableHeader>
                          <TableRow className="bg-muted/50">
                            <TableHead className="w-10" />
                            <TableHead className="w-16">Position</TableHead>
                            <TableHead>Label</TableHead>
                            <TableHead>Range Start</TableHead>
                            <TableHead>Range End</TableHead>
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
                                onUpdate={onItemUpdate}
                                onRemove={onRemoveItem}
                              />
                            ))}
                          </SortableContext>
                        </TableBody>
                      </Table>
                    </div>
                  </DndContext>
                </FormItem>
              </AccordionContent>
            </AccordionItem>
          </Accordion>

          {/* Action Footer */}
          <div className="flex items-center justify-end gap-3 pt-6 border-t">
            <GradientButton
              type="button"
              variant="outline"
              className="w-full md:w-auto"
              onClick={() => navigate(`${ROUTES.SN_INSTANCE}/${snSiteId}/custom-facet`)}
            >
              Cancel
            </GradientButton>
            <GradientButton type="submit">
              {isNew ? "Create Facet" : "Save Changes"}
            </GradientButton>
          </div>
        </form>
      </Form>
    </>
  )
}
